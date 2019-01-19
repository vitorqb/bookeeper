(ns bookeeper.helpers
  (:require [java-time]))

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
