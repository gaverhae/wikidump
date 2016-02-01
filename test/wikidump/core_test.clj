(ns wikidump.core-test
  (:require [expectations :refer [expect in from-each]]
            [wikidump.core :as wiki]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            (ragtime [jdbc :as ragsql]
                     [core :as ragtime])
            [clojure.test.check :as tc]
            (clojure.test.check [generators :as gen]
                                [properties :as prop])))

(def test-data
  "
  <feed>
    <doc>
      <title>Test title</title>
      <url>http://example.com/test</url>
      <abstract>Test abstract.</abstract>
      <links>
        <sublink linktype=\"ignored\">
          <anchor>Garbage</anchor>
          <link>http://example.com/link</link>
        </sublink>
      </links>
    </doc>
    <doc>
      <abstract>Something else entirely.</abstract>
      <title>Test too</title>
      <url>http://example.com/other</url>
    </doc>
  </feed>
  ")

(expect [{:title "Test title"
          :abstract "Test abstract."
          :url "http://example.com/test"}
         {:title "Test too"
          :abstract "Something else entirely."
          :url "http://example.com/other"}]
        (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
          (doall (wiki/parse-xml rdr))))

(expect #{"title" "test" "abstract"}
        (wiki/extract-words {:title "Test title"
                             :abstract "Test abstract."
                             :url "http://example.com/test"}))

(expect [{:title "Test too"
          :abstract "Something else entirely."
          :url "http://example.com/other"}]
        (let [store (wiki/in-memory-map-store)]
          (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
            (wiki/add-xml-feed! store rdr))
          (wiki/search store "too")))

(expect [{:title "Test too"
          :abstract "Something else entirely."
          :url "http://example.com/other"}
         {:title "Test title"
          :abstract "Test abstract."
          :url "http://example.com/test"}]
        (let [store (wiki/in-memory-map-store)]
          (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
            (wiki/add-xml-feed! store rdr))
          (wiki/search store "test")))

(defn feed-string-data
  [string store]
  (with-open [rdr (clojure.java.io/reader (.getBytes string))]
    (wiki/add-xml-feed! store rdr))
  store)

(defn reset-postgres!
  "Resets the PostgreSQL testing database by running all migrations in both
  directions.

  The metadata makes sure expectations runs this once before a full test suite."
  []
  (let [rdb (ragsql/sql-database (env :postgres-url))
        migrations (ragsql/load-resources "migrations")
        idx (ragtime/into-index migrations)]
    ;; To reinitialize the database, we rollback each migration in reverse
    ;; order. We use mapv here instead of map because mapv is not lazy (and as
    ;; we do nothing with the result, map would not run at all).
    (->> (ragtime/applied-migrations rdb idx)
         reverse
         (mapv (partial ragtime/rollback rdb)))
    ;; We then reapply the migrations in order to get the DB to a known state.
    (wiki/run-migrations (env :postgres-url))))

(def test-stores
  [(feed-string-data test-data (wiki/in-memory-map-store))
   (feed-string-data test-data (wiki/in-memory-set-store))
   (do
     (reset-postgres!)
     (feed-string-data test-data (wiki/postgres-store (env :postgres-url))))])

(expect {:status 200}
        (from-each [test-store test-stores]
          (in ((wiki/make-handler test-store)
               {:uri "/search"
                :query-string "q=Test"
                :request-method :get}))))

(expect {:status 404}
        (from-each [test-store test-stores]
          (in ((wiki/make-handler test-store)
               {:uri "/anything-else"
                :request-method :get}))))

(expect {:status 200
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body {:q "Test"
                :results [{:title "Test too"
                           :abstract "Something else entirely."
                           :url "http://example.com/other"}
                          {:title "Test title"
                           :abstract "Test abstract."
                           :url "http://example.com/test"}]}}
        (from-each [test-store test-stores]
          (in (-> ((wiki/make-handler test-store)
                   {:uri "/search"
                    :query-string "q=Test"
                    :request-method :get})
                  (update :body json/parse-string true)))))

(expect {:status 200
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body {:q "missing"
                :results []}}
        (from-each [test-store test-stores]
          (in (-> ((wiki/make-handler test-store)
                   {:uri "/search"
                    :query-string "q=missing"
                    :request-method :get})
                  (update :body json/parse-string true)))))

(def gen-feed
  (-> (fn [[url title abstract]]
        (str "<doc><title>" title "</title><url>" url "</url>"
             "<abstract>" abstract "</abstract></doc>"))
      (gen/fmap (gen/vector gen/string-alphanumeric 3 3))
      (gen/vector 0 100)
      (->> (gen/fmap (fn [docs]
                       (str "<feed>" (reduce str docs) "</feed>"))))))

(expect #(apply = %)
        (let [set-store (wiki/in-memory-set-store)
              map-store (wiki/in-memory-map-store)
              sql-store (wiki/postgres-store (env :postgres-url))
              data "<feed><doc><title>6</title><url>0</url><abstract>06</abstract></doc></feed>"
              search "6"]
          (reset-postgres!)
          (feed-string-data data set-store)
          (feed-string-data data map-store)
          (feed-string-data data sql-store)
          [(wiki/search set-store search)
           (wiki/search map-store search)
           (wiki/search sql-store search)]))

(def cnt (atom 0))

(expect
  {:result true}
  (in
    (tc/quick-check
      10
      (prop/for-all [feed gen-feed
                     words (gen/vector gen/string-alphanumeric 1 100)]
                    (let [stores [(wiki/in-memory-map-store)
                                  (wiki/in-memory-set-store)
                                  (wiki/postgres-store (env :postgres-url))]]
                      (reset-postgres!)
                      (mapv (partial feed-string-data feed) stores)
                      (println (swap! cnt inc))
                      (->> words
                           (remove empty?)
                           (map (fn [w] (map #(wiki/search % w) stores)))
                           (every? (partial apply =))))))))
