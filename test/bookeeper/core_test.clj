(ns bookeeper.core-test
  (:require [clojure.test :refer :all]
            [bookeeper.core :refer :all]
            [clojure.string :as str]))

;;
;; Helpers
;;
(defmacro with-capture-doprint
  "Evaluates body, captures all calls to doprint and sets it to atom."
  [capture-atom & body]
  `(with-redefs [doprint #(swap! ~capture-atom conj %)]
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

(deftest test-parse-args-global

  (testing "Command no opts"
    (is (= (parse-args-global ["cmd1"] [])
           {:global-opts {} :cmd-name "cmd1" :cmd-args []})))

  (testing "Command two opts"
    (let [args ["cmd1" "--option1" "-p" "123"]]
      (is (= {:global-opts {} :cmd-name "cmd1" :cmd-args (rest args)}
             (parse-args-global args [])))))

  (testing "With global option"
    (let [args ["--option1" "value1" "cmd2" "--some-arg"]
          option1-spec ["-o" "--option1 OPTION1" "Option one"]]
      (is (= (parse-args-global args [option1-spec])
             {:global-opts {:option1 "value1"}
              :cmd-name    "cmd2"
              :cmd-args    ["--some-arg"]}))))

  (testing "With unknown global option"
    (is (= (parse-args-global ["-p"] [])
           {:err-msg "Unknown option: \"-p\""}))))

(deftest test-parse-args

  (testing "Unkown global options"
    (is (= {:ok false :exit-message "Unknown option: \"--opt\""}
           (parse-args ["--opt"] [] []))))

  (testing "No args"
    (is (= {:ok false :exit-message exit-message-no-command}
           (parse-args [] [] []))))

  (testing "Unkown command"
    (is (= {:ok false :exit-message (format-unknown-cmd "unkown")}
           (parse-args ["unkown"] [] []))))

  (let [name "do-this"]

    (testing "Known command"
      (is (= {:cmd-name name :cmd-opts {} :global-opts {}}
             (parse-args [name] [] [{:cmd-name name :cmd-spec []}]))))

    (testing "Known command with options"
      (is (= {:cmd-name name :cmd-opts {:opt1 "value1"} :global-opts {}}
             (parse-args [name "--opt1" "value1"]
                         []
                         [{:cmd-name name :cmd-spec [[nil "--opt1 VALUE"]]}]))))

    (testing "Known command invalid option"
      (is (= {:ok false :exit-message "Unknown option: \"-a\""}
             (parse-args [name "-a"] [] [{:cmd-name name :cmd-spec []}]))))))

(deftest test-functional-query-all-books
  ;; Clears books from db
  (->> (query-all-books) (run! delete-book))

  ;; Insert three books
  (->> ["Book 1" "Book 2" "Three"]
       (map #(assoc {} :title %))
       (run! create-book))

  (testing "Calling query books from main"
    (let [result (extract-doprint-from (-main "query-books"))
          expect (->> (query-all-books) (map book-to-repr))]
      (is (= (sort result) (sort expect))))))

(deftest test-functional-read-book
  ;; Clears books from db
  (->> (query-all-books) (run! delete-book))

  (testing "Adds a new book"
    ;; Adds a new book
    (-main "add-book" "--title" "How To Solve It")

    ;; And sees it in the output when querying for books
    (let [query-books-prints (extract-doprint-from (-main "query-books"))]
      (is (some #(.contains % "How To Solve It") query-books-prints)))))


(deftest test-functional-unkown-command
  (testing "Calling unkown command"
    (let [printted (extract-doprint-from (-main "unkown__command"))]
      (is (str/starts-with? (first printted) "Unkown command 'unkown__command'")))))
