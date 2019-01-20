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
