(defproject bookeeper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/core.incubator "0.1.4"]
                 [org.xerial/sqlite-jdbc "3.23.1"]
                 [ragtime "0.7.2"]
                 [honeysql "0.9.4"]
                 [clojure.java-time "0.3.2"]]
  :aot [bookeeper.core]
  :main bookeeper.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :aliases {"start-repl-server" ["repl" ":headless" ":host" "127.0.0.1" ":port" "4123"]
            "migrate" ["run" "-m" "bookeeper.db/migrate"]
            "rollback" ["run" "-m" "bookeeper.db/rollback"]})
