(ns bookeeper.core-test
  (:require [clojure.test :refer :all]
            [bookeeper.core :refer :all]
            [clojure.string :as str]
            [java-time]))

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


(deftest test-book-to-repr
  (testing "Base"
    (is (= (book-to-repr {:id 12 :title "A book title"})
           "[12] A book title"))))

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
  (->> (query-all-books) (run! delete-book))
  (with-redefs [exit (constantly nil)]
    (let [title     "My Book"
          date      (java-time/local-date 2018 11 23)
          durations [(* 30 60) (* 30 30)]]
      (with-ignore-doprint
        ;; Adds a new book!
        (-main "add-book" "--title" title)
        ;; Then add two readings
        (run!
         #(-main "read-book" "--title" title "--date" date "--duration" %)
         durations))
      ;; Check it was read
      (let [read-output (with-capture-doprint (-main "time-spent" "--book-title" title))]
        (is (= (Integer/parseInt read-output)
               (reduce + durations)))))))


;; (deftest test-functional-unkown-command
;;   (testing "Calling unkown command"
;;     (let [printted (extract-doprint-from (-main "unkown__command"))]
;;       (is (str/starts-with? (first printted) "Unkown command 'unkown__command'")))))
