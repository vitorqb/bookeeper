(ns bookeeper.core
  (:gen-class)
  (:require [bookeeper.cli-parser :refer [parse-args]]
            [ragtime.jdbc]
            [ragtime.repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [clojure.tools.cli :as cli]
            [clojure.java.jdbc :as jdbc]))


(declare query-book query-all-books query execute! get-handler query-books-handler
         unkown-command-handler doprint book-to-repr add-book-handler
         create-book get-time-spent time-spent-handler)

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
    :required-keys []}
   {:cmd-name      "time-spent"
    :cmd-spec      [["-t" "--book-title BOOK_TITLE" "Book title"]]
    :required-keys [:book-title]}])

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
;; !!!! TODO -> Allow handlers to return nil (everything went fine) or
;; !!!!         {:ok? ... :exit-message ...}
(defn get-handler [cmd]
  "Returns a handler for a command"
  (case cmd
    "query-books" query-books-handler
    "add-book"    add-book-handler
    "time-spent"  time-spent-handler))

(defn query-books-handler [{}]
  (->> (query-all-books) (map book-to-repr) sort (run! doprint)))

(defn add-book-handler [{title :title}]
  (create-book {:title title}))

(defn time-spent-handler [{book-title :book-title}]
  (-> book-title
      #(query-book {:title %})
      get-time-spent
      (or 0)
      str
      doprint))

;;
;; Bussiness Logic
;;
(defn create-book-sql
  [{title :title}]
  (-> {:insert-into :books :values [{:title title}]}
      sql/build
      sql/format))

(def create-book #(-> % create-book-sql execute!))

(defn query-book-sql
  [{id :id title :title}]
  (cond-> {:select :* :from :books}
    id    (sqlhelpers/where [:= :id id])
    title (sqlhelpers/where [:= :title title])
    true  sql/build
    true  sql/format))

(def query-book #(-> % query-book-sql query))

(def query-all-books-sql #(-> {:select :* :from :books} sql/build sql/format))

(def query-all-books #(-> (query-all-books-sql) query))

(defn delete-book-sql
  [{id :id}]
  (-> {:delete-from :books :where [:= :id id]}
      sql/build
      sql/format))

(def delete-book #(-> % delete-book-sql execute!))

(defn book-to-repr
  [{id :id title :title}]
  (format "[%s] %s" id title))

(defn get-time-spent-query
  "Returns a query withthe time spent for a book"
  [{id :id}]
  (-> (sqlhelpers/select [:%sum.duration :sum-duration])
      (sqlhelpers/from   :reading-sessions)
      (sqlhelpers/join   :books [:= :books.id :reading-sessions.book_id])
      (sqlhelpers/where  [:= :books.id id])
      (sql/build)
      (sql/format)))

(defn get-time-spent
  "Returns the time spent reading a book."
  [book]
  (-> book get-time-spent-query query :sum-duration (or 0)))

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
