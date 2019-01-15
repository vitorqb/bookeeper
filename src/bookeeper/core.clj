(ns bookeeper.core
  (:gen-class)
  (:require [ragtime.jdbc :as ragtime-jdcb]
            [ragtime.repl :as repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [clojure.java.jdbc :as jdbc]))

(declare query-all-books)

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
  "Returns the sql to insert a new book in the db"
  [{title :title}]
  (-> (sqlhelpers/insert-into :books)
      (sqlhelpers/values [{:title title}])
      sql/format))

(defn create-book
  "Creates a new book in the database!"
  [book-spec]
  (->> book-spec
       (create-book-sql)
       (jdbc/execute! db)))

(defn query-all-books
  "Returns all books from the db"
  []
  (-> (sqlhelpers/select :*)
      (sqlhelpers/from :books)
      (sql/format)
      (->> (jdbc/query db))))

(defn delete-book-sql
  [{pk :pk}]
  (-> (sqlhelpers/delete-from :books)
      (sqlhelpers/where [:= :pk pk])
      (sql/format)))

(defn delete-book
  [book]
  (->> book (delete-book-sql) (jdbc/execute! db)))

;; 
;; db-related stuff
;; 
(defn load-config []
  {:datastore  (ragtime-jdcb/sql-database db)
   :migrations (ragtime-jdcb/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))
