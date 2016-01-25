(ns wikidump.core-test
  (:require [expectations :refer [expect]]
            [wikidump.core :as wiki]))

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
                             :abstract "Test abstract"
                             :url "http://example.com/test"}))

#_(expect [{:title "Test too"
          :abstract "Something else entirely."
          :url "http://example.com/other"}]
        (let [store (wiki/in-memory-map-store)]
          (with-open [rdr (clojure.java.io/reader (.getBytes test-data))]
            (wiki/add-xml-feed! store rdr))
          (wiki/search store "too")))
