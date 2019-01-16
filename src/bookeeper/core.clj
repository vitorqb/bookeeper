(ns bookeeper.core
  (:gen-class)
  (:require [ragtime.jdbc]
            [ragtime.repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.cli :refer [parse-opts]]))

(declare query-all-books query execute! query-books-handler unkown-command-handler doprint book-to-repr)

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

(defn doprint
  [x]
  "Wrapper around print (mainly for test)"
  (println x))


;;
;; Main
;; 
(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (when (= args ())
    (throw (RuntimeException. "No command given!")))
  (let [[cmd cmd-args] args]
    (let [handler (case cmd
                    "query-books" query-books-handler
                    unkown-command-handler)]
      (handler cmd cmd-args))))


(defn unkown-command-handler [cmd _]
  (->> cmd (format "Unkown command '%s'") doprint))

(defn query-books-handler [_ args]
  (->> (query-all-books) (map book-to-repr) sort (run! doprint)))


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
  [{id :id}]
  (-> {:delete-from :books :where [:= :id id]}
      sql/build
      sql/format))

(defn delete-book
  [book]
  (-> book delete-book-sql execute!))

(defn book-to-repr
  [{id :id title :title}]
  (format "[%s] %s" id title))

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
