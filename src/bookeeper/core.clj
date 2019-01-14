(ns bookeeper.core
  (:gen-class)
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


;; 
;; db-related stuff
;; 
(defn load-config []
  {:datastore  (jdbc/sql-database {:subprotocol "sqlite" :subname "db.sqlite3"})
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))
