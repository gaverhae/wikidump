(ns wikidump.core
  (:require [org.httpkit.client :as client]
            [clojure.data.xml :as xml])
  (:gen-class))

(defn parse-xml
  "Converts an XML string or stream to a seq of maps with :url, :title and
  :abstract.

  This function does not check the validity of the input schema and does not
  filter out invalid results."
  [s]
  (letfn [(extract-key [k e]
            (->> e :content (filter #(= k (:tag %))) first :content first))]
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

(defn in-memory-map-store
  "Creates a naive in-memory implementation of the Store interface. This should
  be very fast, but will not be able to store much. This will be used as the
  reference implementation for testing new stores."
  []
  (let [data (atom {})]
    (reify Store
      (add-xml-feed! [_ stream] nil)
      (search [_ word] []))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


(comment

  (def files-dir "resources/public/files")
  (.mkdirs (java.io.File. files-dir))
  (def url "http://dumps.wikimedia.org/enwiki/latest/enwiki-latest-abstract23.xml")
  (def file-path (str files-dir "/excerpt23.xml"))

  (def data (atom nil))

  (client/get
    url {:as :stream}
    (fn [{:keys [body]}]
      (reset! data (xml/parse body))))

  (let [entry (-> @data :content (nth 0) :content)]
    {:title (extract-key :title entry)
     :abstract (extract-key :abstract entry)
     :url (extract-key :url entry)})

  )
