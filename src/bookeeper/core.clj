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

;;
;; Globals
;;
(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     (getenv-or-error "BOOKEEPER_DB_FILE")})

;;
;; Main
;; 
(def main-cmd-specs
  "An array of specifications for commands.
  Every item in the array must have:
  :name -> the name of the command
  :args-specs -> an array of arguments specs, as of cli-parser.
  :handler  -> a handler (fn) to execute this command.
  :required-keys -> defines keys that must be parsed by the user."

  [{:name          "add-book"
    :args-specs    [["-t" "--title TITLE" "Title"]]
    :handler       #'add-book-handler
    :required-keys [:title]}

   {:name         "query-books"
    :args-specs    []
    :handler       #'query-books-handler
    :required-keys []}

   {:name          "time-spent"
    :args-specs    [["-t" "--book-title BOOK_TITLE" "Book title"]]
    :handler       #'time-spent-handler
    :required-keys [:book-title]}

   {:name          "query-reading-sessions"
    :args-specs    []
    :handler       #'query-reading-sessions-handler
    :required-keys []}

   {:name          "read-book"
    :args-specs    [["-t" "--book-title BOOK_TITLE" "Book title"]
                    ;; !!!! TODO -> Warn if date is wrong!
                    ["-d" "--date DATE" "Date"
                     :parse-fn str-to-date]
                    ["-u" "--duration DURATION" "Duration"
                     :parse-fn #(Integer/parseInt %)]]
    :handler       #'read-book-handler
    :required-keys [:book-title :date :duration]}])

(defn -main
  [& args]
  (let [{:keys [exit-message ok? cmd-name cmd-opts handler]}
        (parse-args args [] main-cmd-specs)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      ((get-handler cmd-name main-cmd-specs) cmd-opts))))

;;
;; Handlers
;;
(defn get-handler
  "Given a collection of cmd-specs (like main-cmd-specs), returns the
  handler for a specific command with name `cmd-name`"
  [cmd-name cmd-specs]
  (letfn [(throw-err [msg] (throw (RuntimeException. (format msg cmd-name))))]
    (let [filtered (filter (fn [{nm :name}] (= cmd-name nm)) cmd-specs)]
      (case (min (count filtered) 2)
        0 (throw-err "Could not find handler for %s")
        2 (throw-err "Multiple handlers found for %s")
        1 (-> filtered first :handler)))))

(defn query-books-handler [{}]
  (->> (query-all-books) (map book-to-repr) sort (run! doprint)))

(defn query-reading-sessions-handler [{}]
  (->> (query-all-reading-sessions [:book])
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

(defn query-all-reading-sessions-sql
  "Prepares a query for reading-sessions.
  If bring-related contains :book, join information for the book as
  book_id and book_title."
  ([] (query-all-reading-sessions-sql []))
  ([bring-related]
   (let [bring-books-p (some #{:book} bring-related)
         select-fields (cond-> [[:reading-sessions.id :id] :date :duration :book_id]
                         bring-books-p (concat [[:books.title :book_title]]))]
     (-> {:select select-fields :from :reading-sessions}
         (cond-> bring-books-p (sqlhelpers/join :books [:= :books.id :book_id]))
         sql/build
         sql/format))))

(defn query-all-reading-sessions
  "Queries the db for all reading-sessions
  being-related defines which related info should be added to the result,
  currently either [] or [:books].
  If [] then returns {... :book_id ...}
  If [:books] then returns {... :book: {:id ... :title ...}}"
  ([] (query-all-reading-sessions []))
  ([bring-related]
   (let [bring-books-p (some #{:book} bring-related)
         move-books #(-> %
                         (move-in [:book_id] [:book :id])
                         (move-in [:book_title] [:book :title]))]
     (-> (query-all-reading-sessions-sql bring-related)
         query
         (->> (map #(update % :date str-to-date)))
         (cond->> bring-books-p (map move-books))))))

(defn create-reading-session-sql
  [{:keys [date duration book]}]
  (-> {:insert-into :reading-sessions
       :values [{:date date :duration duration :book-id (:id book)}]}
      sql/format))

(def create-reading-session #(-> % create-reading-session-sql execute!))

(defn book-to-repr
  [{id :id title :title}]
  (format "[%s] %s" id title))

(defn reading-session-to-repr
  "Prints a reading-session.
  If the entire book is parsed, print the book title.
  If only the id is parsed, prints the id only"
  [{:keys [date book book_id duration]}]
  (let [book-title (and book (:title book))]
    (format "[%s] [%s] [%s]" (date-to-str date) (or book-title book_id) duration)))

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
  (to-sql [v] (honeysql.format/to-sql (date-to-str v))))
