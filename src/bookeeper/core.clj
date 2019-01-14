(ns bookeeper.core
  (:gen-class)
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))


;;
;; Settings
;;
(defn getenv [x] "Wraps System/getenv for testing" (System/getenv x))


(defn getenv-or-error
  "Get from env or throws runtime error"
  [x]
  (or (getenv x)
      (throw (RuntimeException. (str "Env var " x " not found")))))


(def db-subname (getenv-or-error "BOOKEEPER_DB_FILE"))


;;
;; Main
;; 
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
