(ns wikidump.core
  (:require [org.httpkit.client :as client]
            [clojure.data.xml :as xml])
  (:gen-class))

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

  (let [entry (-> @data :content (nth 0) :content)
        title (->> entry
                   (filter #(= :title (:tag %)))
                   first :content first)]
    {:title title})

  )
