(ns bookeeper.cli-parser
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]))

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
