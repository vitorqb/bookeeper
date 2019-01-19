(ns bookeeper.helpers)

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

