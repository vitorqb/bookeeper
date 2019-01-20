(ns bookeeper.db-test
  (:require [clojure.test :refer :all]
            [bookeeper.db :refer :all]))

(deftest test-query-returning-one
  (testing "Returning 0"
    (with-redefs [query (constantly [])]
      (try (query-returning-one [] :sentinel)
           (throw (RuntimeException. "ExceptionInfo not thrown!"))
           (catch clojure.lang.ExceptionInfo e
             (let [data (ex-data e)]
               (is (= (:capture-for-user data) true))
               (is (= (:user-err-msg :sentinel))))))))
  (testing "Returning 1"
    (with-redefs [query (constantly [:sentinel-value])]
      (is (= (query-returning-one "" "") :sentinel-value)))))
