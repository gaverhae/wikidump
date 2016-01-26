(defproject wikidump "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.4.0"]
                 [http-kit "2.1.18"]
                 [org.clojure/data.xml "0.0.8"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [cheshire "5.5.0"]]
  :main ^:skip-aot wikidump.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[expectations "2.1.4"]]
                   :plugins [[lein-autoexpect "1.7.0"]]}})
