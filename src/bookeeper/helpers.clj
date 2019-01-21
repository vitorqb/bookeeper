(ns bookeeper.helpers
  (:require [java-time]
            [clojure.core.incubator :refer [dissoc-in]]
            [clojure.string :as str]))

;;
;; System Helpers
;;
(defn doprint
  [x]
  "Wrapper around print (mainly for test)"
  (println x))

(defn getenv
  "Wraps System/getenv for testing"
  [x]
  (System/getenv x))

(defn getenv-or-error
  "Get from env or throws runtime error"
  [x]
  (or (getenv x)
      (throw (RuntimeException. (str "Env var " x " not found")))))

(defn exit [code msg]
  "Print the error message and exists with code"
  (doprint (format "ERROR: %s" msg))
  (System/exit code))

;;
;; Date Helpers
;;
(def default-date-format
  "The default date-format to use (as of java-time/format)"
  "yyyy-MM-dd")

(defn date-to-str
  "Converts a java-time/local-date to str."
  [x]
  (java-time/format default-date-format x))

(defn str-to-date
  "Converts a string in default-date-format to a date"
  [x]
  (java-time/local-date default-date-format x))

;;
;; Other
;;
(defn move-in
  "Moves a path in a map to another path"
  [m old new]
  (as-> m it
    (assoc-in it new (get-in it old))
    (dissoc-in it old)))

(defn keyword-replace
  "Replaces a keyword"
  [k match replacement]
  (-> k name (str/replace match replacement) keyword))

(defn replace-underscore-in-keys
  "Replaces all _ in keys of a map to -"
  [m]
  (into {} (map (fn [[k v]] [(keyword-replace k #"_" "-") v]) m)))
