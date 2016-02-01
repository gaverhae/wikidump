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
  "Converts an XML stream to a seq of maps with :url, :title and :abstract.

  This function does not check the validity of the input schema and does not
  filter out invalid results."
  [s]
  (letfn [(extract-key [k d]
            (-> (:content d)
                (->> (filter #(= k (:tag %))))
                first :content first
                (or "")))]
    (->> (xml/parse s)
      :content
      (map (fn [doc]
             (into {} (for [k [:url :abstract :title]]
                        [k (extract-key k doc)]))))
      (remove (fn [{:keys [url title abstract]}]
                (or (= "" url)
                    (and (= "" title)
                         (= "" abstract))))))))

(defprotocol Store
  (add-xml-feed! [this stream]
    "Adds a stream of XML entries to the store. Mutates the store in place.
    Should return nil to make mutation explicit.")
  (search [this word]
    "Searches the store for all entries that contain a given word. Returns a
    vector of matching documents."))

(defn extract-words
  "Extracts all words from a given document, all lowercase in a set."
  [doc]
  (-> (select-keys doc [:abstract :title])
      vals
      (->> (clojure.string/join " ")
           (map #(if (Character/isLetterOrDigit %)
                   % \space))
           (apply str))
      .toLowerCase
      (clojure.string/split #" ")
      (->> (remove #{""})
           (into #{}))))

(defn in-memory-map-store
  "Creates a naive in-memory implementation of the Store interface. This should
  be very fast, but will not be able to store much. This will be used as the
  reference implementation for testing new stores.

  Note that this breaks down pretty badly if we ever need to search for
  multiple words at once (at least if we want to support this at the Store
  implementation level)."
  []
  (let [data (atom {})
        remove-existing (fn [store new-entry]
                          (into {} (for [[k v] store]
                                     [k (->> v
                                             (remove #(= (:url new-entry)
                                                         (:url %)))
                                             set)])))
        add-entry (fn [store new-entry]
                    (let [words (extract-words new-entry)]
                      (merge-with clojure.set/union
                                  store
                                  (into {} (map #(-> [% #{new-entry}])
                                                words)))))
        merge-new-entries (fn [stream]
                            (let [new-entries (->> stream parse-xml)]
                              (fn [store]
                                (reduce (fn [store new-entry]
                                          (-> store
                                              (remove-existing new-entry)
                                              (add-entry new-entry)))
                                        {} new-entries))))]
    (reify Store
      (add-xml-feed! [_ stream]
        (swap! data (merge-new-entries stream))
        nil)
      (search [_ word]
        (let [cur @data
              lw (.toLowerCase word)]
          (->> (keys cur)
               (filter #(.contains % lw))
               (map cur)
               (reduce concat)
               set
               (sort-by :url)))))))

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
        (let [new-entries (parse-xml stream)]
          (swap! data
                 (fn [store]
                   (reduce (fn [store new-entry]
                             (->> store
                                  (remove #(= (:url new-entry)
                                              (:url %)))
                                  (concat [new-entry])
                                  set))
                           store
                           new-entries))))
        nil)
      (search [_ word]
        (->> @data
             (filter (fn [doc]
                       (or (.contains (.toLowerCase (:title doc))
                                      (.toLowerCase word))
                           (.contains (.toLowerCase (:abstract doc))
                                      (.toLowerCase word)))))
             (sort-by :url))))))

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
      (or (->> (sql/query db-uri ["SELECT *
                                  FROM docs
                                  WHERE lower(abstract) LIKE '%' || lower(?) || '%'
                                  OR lower(title)    LIKE '%' || lower(?) || '%'"
                                  word word])
               (sort-by :url))
          []))))

;; TODO: Add env parsing to select where to load data from and
;;       which store to create.
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
