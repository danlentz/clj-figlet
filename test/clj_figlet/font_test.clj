(ns clj-figlet.font-test
  (:require [clojure.test :refer :all]
            [clj-figlet.core :as fig]))

(deftest test-all-bundled-fonts-valid
  (testing "Every bundled font passes spec validation"
    (doseq [font-name (fig/all-fonts)]
      (testing font-name
        (is (fig/valid-font? (fig/load-font (str "fonts/" font-name ".flf")))
            (str font-name " failed validation"))))))
