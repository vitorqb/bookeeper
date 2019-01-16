(ns bookeeper.core
  (:gen-class)
  (:require [ragtime.jdbc]
            [ragtime.repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [clojure.java.jdbc :as jdbc]))

(declare query-all-books query execute!)

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
;; Globals
;;
(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     db-subname})


;;
;; Main
;; 
(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (query-all-books)))


;;
;; Bussiness Logic
;;
(defn create-book-sql
  [{title :title}]
  (-> {:insert-into :books :values [{:title title}]}
      sql/build
      sql/format))

(defn create-book
  [book-spec]
  (-> book-spec create-book-sql execute!))

(defn query-all-books-sql
  []
  (-> {:select :* :from :books} sql/build sql/format))

(defn query-all-books
  []
  (-> (query-all-books-sql) query))

(defn delete-book-sql
  [{pk :pk}]
  (-> {:delete-from :books :where [:= :pk pk]}
      sql/build
      sql/format))

(defn delete-book
  [book]
  (-> book delete-book-sql execute!))

;; 
;; db-related stuff
;; 
(defn load-config []
  {:datastore  (ragtime.jdbc/sql-database db)
   :migrations (ragtime.jdbc/load-resources "migrations")})

(defn migrate []
  (ragtime.repl/migrate (load-config)))

(defn rollback []
  (ragtime.repl/rollback (load-config)))

(defn query [x] (jdbc/query db x))
(defn execute! [x] (jdbc/execute! db x))
