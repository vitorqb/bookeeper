(ns bookeeper.cli-parser-test
  (:require [clojure.test :refer :all]
            [bookeeper.cli-parser :refer :all]))

(deftest test-parse-args-global

  (testing "Command no opts"
    (is (= (parse-args-global ["cmd1"] [])
           {:global-opts {} :cmd-name "cmd1" :cmd-args []})))

  (testing "Command two opts"
    (let [args ["cmd1" "--option1" "-p" "123"]]
      (is (= {:global-opts {} :cmd-name "cmd1" :cmd-args (rest args)}
             (parse-args-global args [])))))

  (testing "With global option"
    (let [args ["--option1" "value1" "cmd2" "--some-arg"]
          option1-spec ["-o" "--option1 OPTION1" "Option one"]]
      (is (= (parse-args-global args [option1-spec])
             {:global-opts {:option1 "value1"}
              :cmd-name    "cmd2"
              :cmd-args    ["--some-arg"]}))))

  (testing "With unknown global option"
    (is (= (parse-args-global ["-p"] [])
           {:err-msg "Unknown option: \"-p\""}))))

(deftest test-parse-args

  (testing "Unkown global options"
    (is (= {:ok false :exit-message "Unknown option: \"--opt\""}
           (parse-args ["--opt"] [] []))))

  (testing "No args"
    (is (= {:ok false :exit-message exit-message-no-command}
           (parse-args [] [] []))))

  (testing "Missing arg"
    (is (= {:ok false :exit-message "Missing options: a"}
           (parse-args ["cmd"]
                       []
                       [{:cmd-name "cmd"
                         :cmd-spec [["-a" "--arg1 ARG"]]
                         :required-keys [:a]}]))))

  (testing "Unkown command"
    (is (= {:ok false :exit-message (format-unknown-cmd "unkown")}
           (parse-args ["unkown"] [] []))))

  (testing "Positional args not supported"
    (is (= {:ok false :exit-message exit-message-positional-arguments-not-supported}
           (parse-args ["cmd" "pos"] [] [{:cmd-name "cmd" :cmd-spec []}]))))

  (let [name "do-this"]

    (testing "Known command"
      (is (= {:cmd-name name :cmd-opts {} :global-opts {}}
             (parse-args [name] [] [{:cmd-name name :cmd-spec []}]))))

    (testing "Known command with options"
      (is (= {:cmd-name name :cmd-opts {:opt1 "value1"} :global-opts {}}
             (parse-args [name "--opt1" "value1"]
                         []
                         [{:cmd-name name :cmd-spec [[nil "--opt1 VALUE"]]}]))))

    (testing "Known command invalid option"
      (is (= {:ok false :exit-message "Unknown option: \"-a\""}
             (parse-args [name "-a"] [] [{:cmd-name name :cmd-spec []}]))))))
