(ns bookeeper.core
  (:gen-class)
  (:require [clojure.string :as str]
            [bookeeper.cli-parser :refer [parse-args]]
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
(def main-cmd-specs
  "An array of specifications for commands.
  Every item in the array must have:
  :name -> the name of the command
  :args-specs -> an array of arguments specs, as of cli-parser.
  :handler  -> a handler (fn) to execute this command.
  :required-keys -> defines keys that must be parsed by the user."

  [{:name          "add-book"
    :args-specs    [["-t" "--title TITLE" "Title"]
                    ["-p" "--page-count PAGE_COUNT" "Page count"]]
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
                    ["-d" "--date DATE" "Date"
                     :parse-fn str-to-date]
                    ["-u" "--duration DURATION" "Duration"
                     :parse-fn #(Integer/parseInt %)]
                    ["-p" "--page-count PAGE_COUNT" "Page count"]]
    :handler       #'read-book-handler
    :required-keys [:book-title :date :duration]}])

(defn make-cmd-help-msg
  "Returns the help message for a single command.
  cmd-spec is assumed to have :name and :args-specs."
  [{:keys [name args-specs]}]
  (-> name
      (->> (format "Help for command '%s'"))
      (cons nil)
      (cond-> (= (count args-specs) 0)
        (->> (cons "Arguments: none")))
      (cond-> (> (count args-specs) 0)
        (->> (cons "Arguments:")
             (cons (->> args-specs (cli/parse-opts "") :summary))))
      (reverse)
      (->> (str/join "\n"))))
  

(defn make-help-msg
  "Returns a string that is a help message for the user.
  cmd-specs -> An array of command specs (like main-cmd-specs)"
  [cmd-specs]
  (->> cmd-specs
       (map :name)
       (map #(str "  - " %))
       (sort)
       (cons "Available commands:")
       (cons "Usage: bookeeper <command> [args]")
       (str/join "\n")))

(def main-global-opts
  [["-h" "--help"]])

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

(def exit-message-no-command "No command provided.")
(defn -main
  [& args]
  (let [{:keys [exit-message ok? cmd-name cmd-opts global-opts]}
        (parse-args args main-global-opts main-cmd-specs)]

    (cond
      (some #{[:help true]} global-opts) (doprint (make-help-msg main-cmd-specs))

      exit-message (exit (if ok? 0 1) exit-message)

      (= cmd-name nil) (exit 1 exit-message-no-command)

      (some #{[:help true]} cmd-opts)
      (let [cmd-spec (some #(-> % :name (= cmd-name) (and %)) main-cmd-specs)]
        (doprint (make-cmd-help-msg cmd-spec)))

      :else (with-capturing-user-exceptions exit
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

(def add-book-handler #(create-book %))

(defn time-spent-handler [{book-title :book-title}]
  (-> book-title
      (->> (hash-map :title))
      query-book
      get-time-spent
      (or 0)
      str
      doprint))

(defn read-book-handler [{:keys [book-title date duration page-count]}]
  (->> book-title
       (assoc {} :title)
       query-book
       (assoc {:date date :duration duration :page-count page-count} :book)
       create-reading-session))

;;
;; Bussiness Logic
;;
(defn create-book-sql
  [{title :title page-count :page-count}]
  (assert title "Title should never be null")
  (-> {:insert-into :books :values [{:title title}]}
      (cond-> page-count
        (assoc-in [:values 0 :page-count] page-count))
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
(defn query-all-books
  []
  (-> (query-all-books-sql)
      (query)
      (#(map replace-underscore-in-keys %))))

(defn query-all-reading-sessions-sql
  "Prepares a query for reading-sessions.
  If bring-related contains :book, join information for the book as
  book_id and book_title."
  ([] (query-all-reading-sessions-sql []))
  ([bring-related]
   (let [bring-books-p (some #{:book} bring-related)]
     (-> {:from :reading-sessions
          :select [[:reading-sessions.id :id] :date :duration :book_id
                   [:reading-sessions.page_count :page_count]]}
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
   (let [bring-books-p (some #{:book} bring-related)]
     (-> (query-all-reading-sessions-sql bring-related)
         query
         (->> (map #(update % :date str-to-date)))
         (cond-> bring-books-p
           (->> (map #(move-in % [:book_id] [:book :id]))
                (map #(move-in % [:book_title] [:book :title]))))
         (#(map replace-underscore-in-keys %))))))

(defn create-reading-session-sql
  [{:keys [date duration book page-count]}]
  (-> {:insert-into :reading-sessions
       :values [{:date date :duration duration :book-id (:id book)}]}
      (cond-> page-count
        (assoc-in [:values 0 :page-count] page-count))
      sql/format))

(def create-reading-session #(-> % create-reading-session-sql execute!))

;; !!!! TODO -> Returns as clojure map
(defn book-to-repr
  [{:keys [id title page-count]}]
  (->> [id title page-count]
       (map #(or % ""))
       (apply format "[%s] [%s] [%s]")))

;; !!!! TODO -> Returns as clojure map
(defn reading-session-to-repr
  "Prints a reading-session.
  If the entire book is parsed, print the book title.
  If only the id is parsed, prints the id only"
  [{:keys [date book book_id duration page-count]}]
  (let [book-title (and book (:title book))]
    (format "[%s] [%s] [%s] [%s]"
            (date-to-str date)
            (or book-title book_id)
            duration
            (or page-count ""))))

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
