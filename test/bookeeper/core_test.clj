(ns bookeeper.core-test
  (:require [clojure.test :refer :all]
            [bookeeper.core :refer :all]
            [clojure.string :as str]))

(deftest test-getenv-or-error
  (testing "Returns when getenv returns"
    (with-redefs [getenv (constantly "hola")]
      (is (= (getenv-or-error "a")) "hola")))
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
    (let [print-args (atom ())]
      (with-redefs [doprint #(swap! print-args conj %)]
        (-main "query-books"))
      (is (= (sort @print-args) (->> (query-all-books) (map book-to-repr)))))))


(deftest test-functional-unkown-command
  (testing "Calling unkown command"
    (let [print-args (atom ())]
      (with-redefs [doprint #(swap! print-args conj %)]
        (-main "unkown__command"))
      (is (str/starts-with? (first @print-args) "Unkown command 'unkown__command'")))))
