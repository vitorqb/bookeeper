(ns bookeeper.core
  (:gen-class)
  (:require [bookeeper.cli-parser :refer [parse-args]]
            [ragtime.jdbc]
            [ragtime.repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [clojure.tools.cli :as cli]
            [clojure.java.jdbc :as jdbc]))


(declare query-all-books query execute! get-handler query-books-handler
         unkown-command-handler doprint book-to-repr add-book-handler
         create-book)

;;
;; System Helpers
;;
(defn getenv [x] "Wraps System/getenv for testing" (System/getenv x))

(defn getenv-or-error
  "Get from env or throws runtime error"
  [x]
  (or (getenv x)
      (throw (RuntimeException. (str "Env var " x " not found")))))

(defn exit [code msg]
  (doprint (format "ERROR: %s" msg))
  (System/exit code))

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
(def main-cmd-specs
  [{:cmd-name      "add-book"
    :cmd-spec      [["-t" "--title TITLE" "Title"]]
    :required-keys [:title]}
   {:cmd-name      "query-books"
    :cmd-spec      []
    :required-keys []}])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [exit-message ok? cmd-name cmd-opts]}
        (parse-args args [] main-cmd-specs)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      ((get-handler cmd-name) cmd-opts))))

;;
;; Cli parser helpers
;;
(defn get-handler [cmd]
  "Returns a handler for a command"
  (case cmd
    "query-books" query-books-handler
    "add-book"    add-book-handler))

(defn query-books-handler [{}]
  (->> (query-all-books) (map book-to-repr) sort (run! doprint)))

(defn add-book-handler [{title :title}]
  (create-book {:title title}))

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
