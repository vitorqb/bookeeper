(ns bookeeper.serializers-test
  (:require [clojure.test :refer :all]
            [bookeeper.helpers :refer [date->str]]
            [bookeeper.serializers :refer :all]
            [java-time]))

(deftest test-book->edn
  (testing "Base"
    (let [book {:title "Hola"}]
      (is (= book (book->edn book))))
    (let [book {:title "Hola" :page-count 12}]
      (is (= book (book->edn book))))))


(deftest test-reading-session->edn
  (let [reading-session-base {:id 1 :date (java-time/local-date 2017 12 12)
                              :duration 50 :page_count 13}]
    (testing "Without book"
      (let [reading-session (assoc reading-session-base :book_id 2)]
        (is (= (update reading-session :date date->str)
               (reading-session->edn reading-session)))))

    (testing "With book"
      (let [reading-session (assoc reading-session-base :book
                                   {:id 3 :title "A" :page-count 12})]
        (is (= (-> reading-session
                   (update :date date->str)
                   (update :book book->edn))
               (reading-session->edn reading-session)))))))


(deftest test-edn->reading-session
  (testing "Without book"
    (let [reading-session {:id 1 :date "2018-01-01" :book_id 90}]
      (is (= {:id 1 :date (java-time/local-date 2018 1 1) :book_id 90}
             (edn->reading-session reading-session)))))

  (testing "With book"
    (let [reading-session {:date "1993-11-23" :book {:id 90 :title "A" :page-count 2}}]
      (is (= {:date (java-time/local-date 1993 11 23)
              :book (edn->book (:book reading-session))}
             (edn->reading-session reading-session))))))
             
