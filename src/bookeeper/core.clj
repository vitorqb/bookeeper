(ns bookeeper.core
  (:gen-class)
  (:require [bookeeper.cli-parser :refer [parse-args]]
            [bookeeper.helpers :refer :all]
            [bookeeper.db :refer :all]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [honeysql.format]
            [java-time]
            [clojure.tools.cli :as cli])
  (:import [java.time LocalDate]))

(declare query-book query-all-books get-handler query-books-handler
         unkown-command-handler book-to-repr add-book-handler
         create-book get-time-spent time-spent-handler query-reading-sessions-handler
         reading-session-to-repr query-all-reading-sessions read-book-handler
         create-reading-session)

;;
;; Main
;;
;; !!!! TODO -> Add --help
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

(defmacro with-capturing-user-exceptions
  "Tries to execute body. If captures an ExceptionInfo that contains
  {... :capture-for-user true} in its data, exits with exit-fn parsing
  error-code 1 and the :user-err-msg from the exception data."
  [exit-fn & body]
  `(try ~@body
        (catch clojure.lang.ExceptionInfo e#
          (do
            (when-not (:capture-for-user (ex-data e#))
              (throw e#))
            (~exit-fn 1 (:user-err-msg (ex-data e#)))))))

(defn -main
  [& args]
  (let [{:keys [exit-message ok? cmd-name cmd-opts handler]}
        (parse-args args [] main-cmd-specs)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (with-capturing-user-exceptions exit
        ((get-handler cmd-name main-cmd-specs) cmd-opts)))))

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
  (-> book-title
      (->> (assoc {} :title))
      query-book
      get-time-spent
      (or 0)
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
  (-> {:select :* :from :books}
      (cond-> 
          id    (sqlhelpers/where [:= :id id])
          title (sqlhelpers/where [:= :title title]))
      sql/build
      sql/format))

(defn query-book
  [spec]
  (-> spec
      query-book-sql
      (query-returning-one (format "Book not found: %s" spec))))

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
   (let [bring-books-p (some #{:book} bring-related)]
     (-> {:from :reading-sessions
          :select [[:reading-sessions.id :id] :date :duration :book_id]}
         (cond-> bring-books-p
           (-> (update :select #(concat % [[:books.title :book-title]]))
               (sqlhelpers/join :books [:= :books.id :book_id])))
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
  (format "[%s] [%s]" id title))

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
      get-time-spent-query
      query
      first
      :sum_duration
      (or 0)))

;;
;; Honeysql extensions
;;
(extend-protocol honeysql.format/ToSql
  LocalDate
  (to-sql [v] (honeysql.format/to-sql (date-to-str v))))
