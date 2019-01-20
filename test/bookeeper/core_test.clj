(ns bookeeper.core-test
  (:require [clojure.test :refer :all]
            [bookeeper.core :refer :all]
            [bookeeper.helpers :refer :all]
            [clojure.string :as str]
            [java-time]
            [honeysql.core :as sql]))

;;
;; Helpers
;;
(defmacro with-capture-doprint
  "Evaluates body, captures all calls to doprint and sets it to atom."
  [capture-atom & body]
  `(with-redefs [doprint #(swap! ~capture-atom conj %)]
     ~@body))

(defmacro with-ignore-doprint
  "Evaluates body, ignore the sideeffect of all doprint calls."
  [& body]
  `(with-redefs [doprint (constantly nil)]
     ~@body))

(defmacro extract-doprint-from
  "Evaluates body, captures all calls to doprint, and returns."
  [& body]
  `(let [myatom# (atom ())]
     (with-capture-doprint myatom# ~@body)
     @myatom#))

;;
;; Tests
;; 
(deftest test-getenv-or-error
  (testing "Returns when getenv returns"
    (with-redefs [getenv (constantly "hola")]
      (is (= (getenv-or-error "a") "hola"))))
  (testing "Throws error if getenv returns nil"
    (with-redefs [getenv (constantly nil)]
      (is (thrown? RuntimeException (getenv-or-error "a"))))))

(deftest test-get-handler
  (testing "Base"
    (is (= (get-handler "cmd1" [{:name "cmd1" :handler :SENTINEL}])
           :SENTINEL)))
  (testing "Not found throws error"
    (is (thrown? RuntimeException (get-handler "cmd1" []))))
  (testing "Multiple throws error"
    (is (thrown? RuntimeException
                 (get-handler "cmd2" [{:name "cmd2" :handler 1}
                                      {:name "cmd2" :handler 2}])))))

(deftest test-create-book-sql
  (testing "Base"
    (is (= (create-book-sql {:title "Book One"})
           ["INSERT INTO books (title) VALUES (?)" "Book One"]))))

(deftest test-create-book
  (testing "Makes correct query"
    ;; !!!! TODO -> Abstract query-arg
    (let [query-arg (atom {})]
      (with-redefs [clojure.java.jdbc/execute! (fn [_ x] (reset! query-arg x))]
        (create-book {:title "Hola"}))
      (is (= @query-arg (create-book-sql {:title "Hola"}))))))
        

(deftest test-query-all-books-sql
  (testing "Base"
    (is (= (query-all-books-sql) ["SELECT * FROM books"]))))

  
(deftest test-query-all-books
  (testing "Makes correcy query"
    (let [args (atom [])]
      (with-redefs [clojure.java.jdbc/query (fn [x y] (reset! args [x y]))]
        (query-all-books))
      (is (= (first @args) db))
      (is (= (second @args) ["SELECT * FROM books"])))))

(deftest test-query-all-reading-sessions
  (testing "Makes right query"
    (let [query-call-arg (atom nil)]
      (with-redefs [query-all-reading-sessions-sql (constantly :sentinel)
                    query (fn [x]
                            (reset! query-call-arg x)
                            [{:date "2018-12-12"}])]
        (query-all-reading-sessions)
        (is (= @query-call-arg :sentinel)))))
  (testing "Parses date correctly"
    (with-redefs [query (constantly [{:date "1972-12-12"}])]
      (is (= (query-all-reading-sessions)
             [{:date (java-time/local-date 1972 12 12)}]))))
  (testing "Bring entire books if [:books] parsed."
    (with-redefs [query (constantly [{:book_id 1
                                      :book_title "Title"
                                      :date "1991-01-01"}])]
      (is (= [{:date (java-time/local-date 1991 1 1) :book {:id 1 :title "Title"}}]
             (query-all-reading-sessions [:book]))))))

(deftest test-query-all-reading-sessions-sql
  (testing "Query without :book"
    (is (= (query-all-reading-sessions-sql)
           [(str "SELECT reading_sessions.id AS id, date, duration, book_id FROM"
                 " reading_sessions")])))
  (testing "With :book"
    (is (= (query-all-reading-sessions-sql [:book])
           [(str "SELECT reading_sessions.id AS id, date, duration, book_id,"
                 " books.title AS book_title FROM reading_sessions"
                 " INNER JOIN books ON books.id = book_id")]))))

(deftest test-delete-book-sql
  (testing "Base"
    (is (= ["DELETE FROM books WHERE id = ?" 1] (delete-book-sql {:id 1})))))


(deftest test-delete-book
  (testing "Makes correct query"
    (let [args (atom [])
          book {:id 1 :title "A"}]
      (with-redefs [clojure.java.jdbc/execute! #(reset! args [%1 %2])]
        (delete-book book))
      (is (= @args [db (delete-book-sql book)])))))

(deftest test-query-book
  (testing "With id"
    (is (= ["SELECT * FROM books WHERE id = ?" 1]
           (query-book-sql {:id 1}))))
  (testing "With title"
    (is (= ["SELECT * FROM books WHERE title = ?" "mytitle"]
           (query-book-sql {:title "mytitle"})))))

(deftest test-get-time-spent
  (testing "Makes correct query"
    (let [query-calls (atom ())]
      (with-redefs [query (fn [& args] (swap! query-calls conj args))]
        (get-time-spent {:id 1}))
      (is (= 1 (count @query-calls)))
      (is (= (list (get-time-spent-query {:id 1})) (first @query-calls)))))
  (testing "The query"
    (is (= [(str "SELECT sum(duration) AS sum_duration"
                 " FROM reading_sessions"
                 " WHERE book_id = ?") 15]
           (get-time-spent-query {:id 15})))))

(deftest test-create-reading-session
  (testing "create-reading-session-sql"
    (is (= [(str "INSERT INTO reading_sessions (date, duration, book_id) VALUES (?, ?, ?)")
            "2018-02-02" 250 1]
           (create-reading-session-sql {:date (java-time/local-date 2018 2 2)
                                        :duration 250
                                        :book {:id 1}})))))

(deftest test-book-to-repr
  (testing "Base"
    (is (= (book-to-repr {:id 12 :title "A book title"})
           "[12] [A book title]"))))

(deftest test-reading-session-to-repr
  (testing "Base"
    (is (= "[1993-11-23] [2] [444]"
           (reading-session-to-repr {:date (java-time/local-date 1993 11 23)
                                     :book_id 2
                                     :duration 444}))))
  (testing "When book parsed"
    (is (= "[2017-01-02] [My Book] [120]"
           (reading-session-to-repr {:date (java-time/local-date 2017 1 2)
                                     :book {:title "My Book"}
                                     :duration 120})))))

(deftest test-functional-time-spent
  (testing "Calls time-spent-handler"
    (with-ignore-doprint
      (let [time-spent-handler-calls-args (atom ())]
        (with-redefs [time-spent-handler #(swap! time-spent-handler-calls-args conj %)]
          (-main "time-spent" "--book-title" "MyBook"))
        (is (= 1 (count @time-spent-handler-calls-args)))
        (is (= {:book-title "MyBook"} (first @time-spent-handler-calls-args)))))))

(deftest test-functional-query-all-books
  ;; Clears books from db
  (->> (query-all-books) (run! delete-book))

  ;; Insert three books
  (->> ["Book 1" "Book 2" "Three"]
       (map #(assoc {} :title %))
       (run! create-book))

  (testing "Calling query books from main"
    (with-redefs [exit (fn [_ msg] (doprint msg))]
      (let [result (-> (extract-doprint-from (-main "query-books")) sort)
            expect (->> (query-all-books) (map book-to-repr) sort)]
        (is (= result expect))))))

(defn clear-books-and-reading-sessions []
  (run! #(-> {:delete-from %} sql/format execute!) [:books :reading-sessions]))

(deftest test-functional-read-book
  (testing "Base"
    (clear-books-and-reading-sessions)
    (let [title "Book1"
          date (java-time/local-date 2018 1 1)
          duration 300]
      (create-book {:title title})
      (with-redefs [exit (fn [_ msg] (doprint msg))]
        ;; The user adds a read-book session
        (-main "read-book"
               "--book-title" title
               "--date" (date-to-str date)
               "--duration" (str duration))
        ;; And sees it when he queries
        (let [resp (extract-doprint-from (-main "query-reading-sessions"))]
          (is (= 1 (count resp)))
          (is (str/starts-with? (first resp) (str "[" (date-to-str date) "]"))))))))

(deftest test-functional-add-and-read-book
  (testing "Adds a new book"
    ;; Clears books from db
    (->> (query-all-books) (run! delete-book))
    ;; Adds a new book
    (let [title "How To Solve It"
          contains-title? #(.contains % title)]
      (-main "add-book" "--title" title)
      ;; And sees it in the output when querying for books
      (let [query-books-prints (extract-doprint-from (-main "query-books"))]
        (is (some contains-title? query-books-prints)))))

  (testing "Adds a new book")
  ;; Clear books from db
  (clear-books-and-reading-sessions)
  (with-redefs [exit (fn [_ msg] (doprint msg))]
    (let [title     "My Book"
          date      (java-time/local-date 2018 11 23)
          durations [(* 30 60) (* 30 30)]]
      (with-ignore-doprint
        ;; Adds a new book!
        (-main "add-book" "--title" title)
        ;; Then add two readings
        (run!
         #(-main "read-book" "--book-title" title
                 "--date" (date-to-str date)
                 "--duration" (str %))
         durations))
      ;; Check it was read
      (let [read-output (extract-doprint-from (-main "time-spent" "--book-title" title))]
        (is (= (-> read-output first Integer/parseInt)
               (reduce + durations)))))))
