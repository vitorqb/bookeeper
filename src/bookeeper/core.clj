(ns bookeeper.core
  (:gen-class)
  (:require [ragtime.jdbc]
            [ragtime.repl]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlhelpers]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]))

(declare query-all-books query execute! get-handler query-books-handler
         unkown-command-handler doprint book-to-repr add-book-handler
         create-book)

;;
;; Settings
;;
(defn getenv [x] "Wraps System/getenv for testing" (System/getenv x))


(defn getenv-or-error
  "Get from env or throws runtime error"
  [x]
  (or (getenv x)
      (throw (RuntimeException. (str "Env var " x " not found")))))


(def db-subname (getenv-or-error "BOOKEEPER_DB_FILE"))


;;
;; Globals
;;
(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     db-subname})

(defn doprint
  [x]
  "Wrapper around print (mainly for test)"
  (println x))


;;
;; Main
;; 
(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (when (= args ())
    (throw (RuntimeException. "No command given!")))
  (let [[cmd & cmd-args] args
        handler (get-handler cmd)]
    (handler cmd cmd-args)))

;;
;; Cli parser helpers
;;

;; Error messages
(defn format-unknown-cmd [cmd-name] (format "Unkown command '%s'" cmd-name))
(def exit-message-multiple-commands "Multiple commands provided.")
(def exit-message-no-command "No command provided.")
(def exit-message-positional-arguments-not-supported
  "Positional arguments for commands are not (yet) supported.")

;; Parsers
(defn parse-args-global
  "Parses global (not command specific) arguments.
  On successfull parsing returns {:global-opts ... :cmd-name ... :cmd-args ...}
  On failure returns {:err-msg ...}
  args -> the array of arguments to parse.
  global-opts-spec -> an array with specs for the global options"
  [args global-opts-specs]
  (let [{:keys [options arguments errors]} (cli/parse-opts args
                                                           global-opts-specs
                                                           :in-order true)]
    (cond
      errors {:err-msg (str/join "\n" errors)}
      (= (count arguments) 0) {:err-msg exit-message-no-command}
      :else {:global-opts options
             :cmd-name (first arguments)
             :cmd-args (rest arguments)})))

(defn parse-args-cmd
  "Parses cli arguments for a command.
  Either returns {:err-msg ...} or {:cmd-opts ...}"
  [args {cmd-spec :cmd-spec}]
  (let [{:keys [options arguments errors]} (cli/parse-opts args cmd-spec)]
    (cond
      errors {:err-msg (str/join "\n" errors)}
      (> (count arguments) 1) {:err-msg exit-message-positional-arguments-not-supported}
      :else {:cmd-opts options})))

(defn parse-args
  "Parses cli arguments. Either returns {:exit-message ..., :ok? ...}
  in case the program should return, or
  {:cmd-name ..., :cmd-opts ..., :global-opts ...}
  in case the program should dispatch to a command handler.
  args -> args to parse.
  global-args-spec -> spec (as of clojure.tools-cli) for global options params.
  commands-specs -> An array of {:cmd-name ... :cmd-spec ...} where cmd-spec
                    is a clojure.tools-cli(-like) spec for cli options."
  [args global-args-spec commands-specs]
  (let [{:keys [err-msg global-opts cmd-name cmd-args]}
        (parse-args-global args global-args-spec)]
    (if err-msg
      {:ok false :exit-message err-msg}
      (let [[cmd-spec] (filter #(= (:cmd-name %) cmd-name) commands-specs)]
        (if (nil? cmd-spec)
          {:ok false :exit-message (format-unknown-cmd cmd-name)}
          (let [{:keys [err-msg cmd-opts]} (parse-args-cmd cmd-args cmd-spec)]
            (if err-msg
              {:ok false :exit-message err-msg}
              {:cmd-name cmd-name :cmd-opts cmd-opts :global-opts global-opts})))))))

(defn get-handler [cmd]
  "Returns a handler for a command"
  (case cmd
    "query-books" query-books-handler
    "add-book"    add-book-handler
    unkown-command-handler))

(defn unkown-command-handler [cmd _]
  (->> cmd (format "Unkown command '%s'") doprint))

(defn query-books-handler [_ args]
  (->> (query-all-books) (map book-to-repr) sort (run! doprint)))

(defn add-book-handler [_ args]
  (let [parsed-args (cli/parse-opts args [["-t" "--title" "Title"]])]
    (-> parsed-args
        (get :options)
        (get :title)
        (#(do (println %) %))
        (->> (assoc {} :title))
        (create-book))))

;;
;; Bussiness Logic
;;
(defn create-book-sql
  [{title :title}]
  (-> {:insert-into :books :values [{:title title}]}
      sql/build
      sql/format))

(defn create-book
  [book-spec]
  (-> book-spec create-book-sql execute!))

(defn query-all-books-sql
  []
  (-> {:select :* :from :books} sql/build sql/format))

(defn query-all-books
  []
  (-> (query-all-books-sql) query))

(defn delete-book-sql
  [{id :id}]
  (-> {:delete-from :books :where [:= :id id]}
      sql/build
      sql/format))

(defn delete-book
  [book]
  (-> book delete-book-sql execute!))

(defn book-to-repr
  [{id :id title :title}]
  (format "[%s] %s" id title))

;; 
;; db-related stuff
;; 
(defn load-config []
  {:datastore  (ragtime.jdbc/sql-database db)
   :migrations (ragtime.jdbc/load-resources "migrations")})

(defn migrate []
  (ragtime.repl/migrate (load-config)))

(defn rollback []
  (ragtime.repl/rollback (load-config)))

(defn query [x] (jdbc/query db x))
(defn execute! [x] (jdbc/execute! db x))
