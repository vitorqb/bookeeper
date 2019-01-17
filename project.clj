(defproject bookeeper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.xerial/sqlite-jdbc "3.23.1"]
                 [ragtime "0.7.2"]
                 [honeysql "0.9.4"]]
  :main ^:skip-aot bookeeper.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :aliases {"start-repl-server" ["repl" ":headless" ":host" "127.0.0.1" ":port" "4123"]})
