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

(defn query-returning-one
  "Performs a query, ensuring it returns exactly one element.
   If not, throws ex-info with err-msg and :cause :query-returned-multiple"
  [x err-msg]
  (let [query-result (first (query x))]
    (when-not query-result
      (throw (ex-info (format "No results returned for\n%s" x)
                      {:capture-for-user true
                       :user-err-msg err-msg})))
    query-result))
            
            
