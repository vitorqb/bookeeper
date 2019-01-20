(ns bookeeper.db
  (:require [bookeeper.helpers :refer [getenv-or-error]]
            [ragtime.jdbc]
            [ragtime.repl]
            [clojure.java.jdbc :as jdbc]))


;;
;; Globals
;;
(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     (getenv-or-error "BOOKEEPER_DB_FILE")})

(defn load-config []
  {:datastore  (ragtime.jdbc/sql-database db)
   :migrations (ragtime.jdbc/load-resources "migrations")})

(defn migrate []
  (ragtime.repl/migrate (load-config)))

(defn rollback []
  (ragtime.repl/rollback (load-config)))

(defn query [x] (jdbc/query db x))
(defn execute! [x] (jdbc/execute! db x))
