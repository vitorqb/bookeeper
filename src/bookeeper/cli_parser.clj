(ns bookeeper.cli-parser
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [clojure.core.match :refer [match]]))

(declare parse-args-global parse-args-cmd)

;; Error messages
(defn format-unknown-cmd [name] (format "Unkown command '%s'" name))
(def exit-message-multiple-commands "Multiple commands provided.")
(def exit-message-positional-arguments-not-supported
  "Positional arguments for commands are not (yet) supported.")

;; Parsers
(defn parse-args
  "Parses cli arguments. Either returns {:exit-message ..., :ok? ...}
  in case the program should return, or
  {:cmd-name ..., :cmd-opts ..., :global-opts ...}
  in case the program should dispatch to a command handler.
  args -> args to parse.
  global-args-spec -> spec (as of clojure.tools-cli) for global options params.
  commands-specs -> An array of {:cmd-name ... :args-specs ...} where args-specs
                    is a clojure.tools-cli(-like) spec for cli options."
  [args global-args-spec commands-specs]
  (let [{:keys [err-msg cmd-name global-opts cmd-args]}
        (parse-args-global args global-args-spec)]
    (cond
      err-msg
      {:ok false :exit-message err-msg}

      (= cmd-name nil)
      {:cmd-name nil :cmd-opts nil :global-opts global-opts}

      :else
      (let [args-specs (first (filter #(= (:name %) cmd-name) commands-specs))]
        (if (nil? args-specs)
          {:ok false :exit-message (format-unknown-cmd cmd-name)}
          (let [{:keys [err-msg cmd-opts]} (parse-args-cmd cmd-args args-specs)]
            (if err-msg
              {:ok false :exit-message err-msg}
              {:cmd-name cmd-name :cmd-opts cmd-opts :global-opts global-opts})))))))

(defn parse-args-global
  "Parses global (not command specific) arguments.
  On successfull parsing returns {:global-opts ... :cmd-name ... :cmd-args ...}
  On failure returns {:err-msg ...}
  args -> the array of arguments to parse.
  global-opts-spec -> an array with specs for the global options"
  [args global-opts-specs]
  (let [{:keys [options arguments errors]}
        (cli/parse-opts args global-opts-specs :in-order true)]
    (cond
      errors {:err-msg (str/join "\n" errors)}
      :else {:global-opts options
             :cmd-name (first arguments)
             :cmd-args (rest arguments)})))

(defn parse-args-cmd
  "Parses cli arguments for a command.
  Either returns {:err-msg ...} or {:cmd-opts ...}
  args -> array of strings to parse
  :args-specs -> a parse-opts spec for this command
  :required-keys -> an array of keys that must be in the parsed arguments list"
  [args {args-specs :args-specs required-keys :required-keys}]
  (let [args-specs-with-help (cons ["-h" "--help"] args-specs)
        {:keys [options arguments errors]} (cli/parse-opts args args-specs-with-help)
        missing-keys (filter #((comp not contains?) options %) required-keys)]

    (cond
      errors
      {:err-msg (str/join "\n" errors)}

      (= {:help true} options)
      {:cmd-opts options}

      (-> missing-keys count (> 0))
      {:err-msg (->> missing-keys (map name) str/join (format "Missing options: %s"))}

      (-> arguments count (> 0))
      {:err-msg exit-message-positional-arguments-not-supported}
      
      :else
      {:cmd-opts options})))

