(ns bookeeper.helpers-test
  (:require [clojure.test :refer :all]
            [bookeeper.helpers :refer :all]
            [java-time]))


(deftest test-date-to-str
  (testing "Base"
    (is (= (date-to-str (java-time/local-date 2018 12 28)) "2018-12-28"))))

(deftest test-str-to-date
  (testing "Base"
    (is (= (str-to-date "2018-12-28") (java-time/local-date 2018 12 28)))))

(deftest test-keyword-replace
  (testing "Base"
    (is (= :book-id (keyword-replace :book_id #"_" "-")))))

(deftest test-replace-underscore-inkeys
  (testing "Base"
    (is (= {} (replace-underscore-in-keys {})))
    (is (= {:a 1} (replace-underscore-in-keys {:a 1})))
    (is (= {:a-b "a"} (replace-underscore-in-keys {:a_b "a"})))))
