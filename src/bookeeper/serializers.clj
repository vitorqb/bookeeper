(ns bookeeper.serializers
  (:require [bookeeper.helpers :refer [date->str str->date]]))

;; (de)serialization of book is straightforward
(def book->edn identity)
(def edn->book identity)

;; (de)serializer of reading session requires nested (de)serialization of
;; :date and :book, if present.
(defn edn->reading-session [x]
  (-> x
      (update :date str->date)
      (cond-> (:book x) (update :book edn->book))))

(defn reading-session->edn [x]
  (-> x
      (update :date date->str)
      (cond-> (:book x) (update :book book->edn))))
