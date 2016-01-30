(ns wikidump.core-test
  (:require [expectations :refer [expect in from-each]]
            [wikidump.core :as wiki]
            [cheshire.core :as json]))

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

(expect {:doc {:title "Test title"
               :abstract "Test abstract."
               :url "http://example.com/test"}
         :words #{"test" "title" "abstract"}}
        (wiki/extract-words {:title "Test title"
                             :abstract "Test abstract."
                             :url "http://example.com/test"}))

(expect #{{:title "Test too"
           :abstract "Something else entirely."
           :url "http://example.com/other"}}
        (let [store (wiki/in-memory-map-store)]
          (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
            (wiki/add-xml-feed! store rdr))
          (wiki/search store "too")))

(expect #{{:title "Test too"
           :abstract "Something else entirely."
           :url "http://example.com/other"}
          {:title "Test title"
               :abstract "Test abstract."
               :url "http://example.com/test"}}
        (let [store (wiki/in-memory-map-store)]
          (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
            (wiki/add-xml-feed! store rdr))
          (wiki/search store "test")))

(defn feed-test-data
  [store]
  (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
    (wiki/add-xml-feed! store rdr))
  store)

(def test-stores
  [(feed-test-data (wiki/in-memory-map-store))
   (feed-test-data (wiki/in-memory-set-store))])

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
                :results [{:title "Test title"
                           :abstract "Test abstract."
                           :url "http://example.com/test"}
                          {:title "Test too"
                           :abstract "Something else entirely."
                           :url "http://example.com/other"}]}}
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
