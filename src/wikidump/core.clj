(ns wikidump.core
  (:require [org.httpkit.client :as client])
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

  (client/get url {} (fn [{:keys [body]}] (spit file-path body)))

  )
