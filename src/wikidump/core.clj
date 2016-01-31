(ns wikidump.core
  (:require (org.httpkit [client :as client]
                         [server :as http])
            [clojure.data.xml :as xml]
            (ring.middleware [defaults :as rd]
                             [json :as rjson])
            (ragtime [jdbc :as ragsql]
                     [core :as rg])
            (compojure [core :as cj]
                       [route :as route])
            [environ.core :refer [env]]
            [clojure.java.jdbc :as sql])
  (:gen-class))

(defn parse-xml
  "Converts an XML string or stream to a seq of maps with :url, :title and
  :abstract.

  This function does not check the validity of the input schema and does not
  filter out invalid results."
  [s]
  (letfn [(extract-key [k d]
            (->> d :content (filter #(= k (:tag %))) first :content first))]
    (->> (xml/parse s)
      :content
      (map (fn [doc]
             (into {} (for [k [:url :abstract :title]]
                        [k (extract-key k doc)])))))))

(defprotocol Store
  (add-xml-feed! [this stream]
    "Adds a stream of XML entries to the store. Mutates the store in place.
    Should return nil to make mutation explicit.")
  (search [this word]
    "Searches the store for all entries that contain a given word. Returns a
    vector of matching documents."))

(defn extract-words
  "Extracts all words from a given document. Returns a map with the document
  at :doc and all of its words (lowercase, excluding punctuation) as a set at
  :words."
  [doc]
  {:doc doc
   :words (-> (select-keys doc [:abstract :title])
              vals
              (->> (clojure.string/join " ")
                   (map #(if (Character/isLetterOrDigit %)
                           % \space))
                   (apply str))
              .toLowerCase
              (clojure.string/split #" ")
              (->> (remove #{""})
                   (into #{})))})

(defn in-memory-map-store
  "Creates a naive in-memory implementation of the Store interface. This should
  be very fast, but will not be able to store much. This will be used as the
  reference implementation for testing new stores."
  []
  (let [data (atom {})
        merge-new-entries
        (fn [stream]
          (let [new-entries (->> stream
                                 parse-xml
                                 (map extract-words)
                                 (mapcat (fn [m]
                                           (map (fn [w] [w (:doc m)])
                                                (:words m))))
                                 (reduce (fn [m [k v]]
                                           (update m k (fnil conj #{}) v))
                                         {}))]
            (fn [old-store] (merge-with conj old-store new-entries))))]
    (reify Store
      (add-xml-feed! [_ stream]
        (swap! data (merge-new-entries stream))
        nil)
      (search [_ word] (@data word [])))))

(defn in-memory-set-store
  "Creates a naive in-memory implementation of the Store interface. This
  implementation does not have an index, so loading data is pretty fast, but
  searching for a word takes linear time.

  Internally uses a set rather than a vector to avoid duplicate documents if
  somehow the same data gets loaded twice."
  []
  (let [data (atom #{})]
    (reify Store
      (add-xml-feed! [_ stream]
        (->> stream parse-xml (swap! data (partial reduce conj)))
        nil)
      (search [_ word]
        (->> @data
             (filter (fn [doc]
                       (or (.contains (.toLowerCase (:title doc))
                                      (.toLowerCase word))
                           (.contains (.toLowerCase (:abstract doc))
                                      (.toLowerCase word))))))))))

(defn handle-search
  "Handles the search route; delegates to the store for actual search, but
  manages errors and HTTP wrapping."
  [store word]
  (if (empty? word)
    {:status 400
     :body {:error "Bad request."}}
    {:status 200
     :body {:q word
            :results (search store (.toLowerCase word))}}))

(defn make-handler
  "Turns a store into a Ring handler that returns JSON results."
  [store]
  (-> (cj/routes
        (cj/GET "/search" [q] (handle-search store q))
        (route/not-found {:status 404
                          :body {:error "Page not found."}}))
      rjson/wrap-json-response
      (rd/wrap-defaults rd/api-defaults)))

(defn run-migrations
  "Runs the migrations in resources/migrations on the given db-uri to bring it
  up-to-date."
  [db-uri]
  (let [store (ragsql/sql-database db-uri)
        migs (ragsql/load-resources "migrations")
        index (rg/into-index migs)]
    (rg/migrate-all store index migs)))

(defn postgres-store
  "Returns a Store backed by a PostgreSQL database at the given URI.

  URI must be of the form
  jdbc:postgresql://server-address/db-name?user=db-user&password=db-password"
  [db-uri]
  (run-migrations db-uri)
  (reify Store
    (add-xml-feed! [_ stream]
      (let [data (parse-xml stream)]
        (doseq [{:keys [url title abstract]} data]
          (sql/execute! db-uri ["INSERT INTO docs(url, title, abstract)
                                VALUES(?, ?, ?)
                                ON CONFLICT (url)
                                DO UPDATE SET abstract = ?,
                                              title = ?"
                                url title abstract abstract title])))
      nil)
    (search [_ word]
      (let [lword (.toLowerCase word)]
        (sql/query db-uri ["SELECT *
                           FROM docs
                           WHERE lower(abstract) LIKE '%' || ? || '%'
                              OR lower(title)    LIKE '%' || ? || '%'"
                           lword lword])))))

(defn -main
  "Starts the program. At this point, there are no options."
  [& args]
  (println "Starting the server on port 8080.")
  (let [store (in-memory-map-store)
        url  "http://dumps.wikimedia.org/enwiki/latest/enwiki-latest-abstract23.xml"]
    (println "Downloading data in the background. Please wait for the store to"
             "fill. Download url:")
    (println url)
    (client/get url {:as :stream}
      (fn [{:keys [body]}]
        (println "Finished download. Starting to index...")
        (add-xml-feed! store body)
        (println "Indexing finished.")))
    (http/run-server (make-handler store)
                     {:port 8080})))
