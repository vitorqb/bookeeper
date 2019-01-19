(ns bookeeper.core
  (:gen-class)
  (:require [bookeeper.cli-parser :refer [parse-args]]
            [bookeeper.helpers :refer :all]
            [ragtime.jdbc]
            [ragtime.repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [honeysql.format]
            [java-time]
            [clojure.tools.cli :as cli]
            [clojure.java.jdbc :as jdbc])
  (:import [java.time LocalDate]))


(declare query-book query-all-books query execute! get-handler query-books-handler
         unkown-command-handler book-to-repr add-book-handler
         create-book get-time-spent time-spent-handler query-reading-sessions-handler
         reading-session-to-repr query-all-reading-sessions read-book-handler
         create-reading-session)

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
(def main-cmd-specs
  [{:cmd-name      "add-book"
    :cmd-spec      [["-t" "--title TITLE" "Title"]]
    :required-keys [:title]}
   {:cmd-name      "query-books"
    :cmd-spec      []
    :required-keys []}
   {:cmd-name      "time-spent"
    :cmd-spec      [["-t" "--book-title BOOK_TITLE" "Book title"]]
    :required-keys [:book-title]}
   {:cmd-name      "query-reading-sessions"
    :cmd-spec      []
    :required-keys []}
   {:cmd-name      "read-book"
    :cmd-spec      [["-t" "--book-title BOOK_TITLE" "Book title"]
                    ;; !!!! TODO -> Parse date!
                    ["-d" "--date DATE" "Date"
                     :parse-fn (fn [x] (java-time/local-date "yyyy-MM-dd" x))]
                    ["-u" "--duration DURATION" "Duration"]]
    :required-keys [:book-title :date :duration]}])

(defn -main
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
    "query-books"            query-books-handler
    "add-book"               add-book-handler
    "time-spent"             time-spent-handler
    "query-reading-sessions" query-reading-sessions-handler
    "read-book"              read-book-handler))

;; !!!! TODO -> Generic query handler
(defn query-books-handler [{}]
  (->> (query-all-books) (map book-to-repr) sort (run! doprint)))

(defn query-reading-sessions-handler [{}]
  (->> (query-all-reading-sessions)
       (map reading-session-to-repr)
       sort
       (run! doprint)))

(defn add-book-handler [{title :title}]
  (create-book {:title title}))

(defn time-spent-handler [{book-title :book-title}]
  (->> book-title
       (assoc {} :title)
       query-book
       get-time-spent
       ((fn [x] (or x 0)))
       str
       doprint))

(defn read-book-handler [{:keys [book-title date duration]}]
  (->> book-title
       (assoc {} :title)
       query-book
       (assoc {:date date :duration duration} :book)
       create-reading-session))

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

(def query-book #(-> % query-book-sql query first))

(defn delete-book-sql
  [{id :id}]
  (-> {:delete-from :books :where [:= :id id]}
      sql/build
      sql/format))

(def delete-book #(-> % delete-book-sql execute!))

;; !!!! TODO -> generic-query-all?
(def query-all-books-sql #(-> {:select :* :from :books} sql/build sql/format))

(def query-all-books #(-> (query-all-books-sql) query))

(def query-all-reading-sessions-sql
  #(-> {:select :* :from :reading-sessions} sql/build sql/format))

;; !!!! TODO -> Transform date into java-time
(defn query-all-reading-sessions []
  (letfn [(str-to-date [x] (java-time/local-date "yyyy-MM-dd" x))]
    (->> (query-all-reading-sessions-sql)
         query
         (map #(update % :date str-to-date)))))

(defn create-reading-session-sql
  [{:keys [date duration book]}]
  (-> {:insert-into :reading-sessions
       :values [{:date date :duration duration :book-id (:id book)}]}
      sql/format))

(def create-reading-session #(-> % create-reading-session-sql execute!))

(defn book-to-repr
  [{id :id title :title}]
  (format "[%s] %s" id title))

;; !!!! TODO -> Print book title
(defn reading-session-to-repr
  [{:keys [date book_id duration]}]
  (format "[%s] [%s] [%s]"
          ;; !!!! -> standard format date function
          (java-time/format "yyyy-MM-dd" date)
          book_id
          duration))

(defn get-time-spent-query
  "Returns a query withthe time spent for a book"
  [{id :id}]
  (-> (sqlhelpers/select [:%sum.duration :sum-duration])
      (sqlhelpers/from   :reading-sessions)
      (sqlhelpers/where  [:= :book-id id])
      (sql/build)
      (sql/format)))

(defn get-time-spent
  "Returns the time spent reading a book."
  [book]
  (-> book
      (get-time-spent-query)
      (query)
      (first)
      (:sum_duration)
      ((fn [x] (or x 0)))))

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

;;
;; Honeysql extensions
;;
(extend-protocol honeysql.format/ToSql
  LocalDate
  (to-sql [v] (honeysql.format/to-sql (java-time/format "yyyy-MM-dd" v))))
