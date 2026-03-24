(ns clj-figlet.font-test
  (:require [clojure.test :refer :all]
            [clj-figlet.core :as fig]
            [clojure.java.io :as io]))

(deftest test-all-bundled-fonts-valid
  (testing "Every bundled font passes spec validation"
    (doseq [font-name (fig/all-fonts)]
      (testing font-name
        (is (fig/valid-font? (fig/load-font (str "fonts/" font-name ".flf")))
            (str font-name " failed validation"))))))

(deftest test-write-font-round-trip
  (testing "A font survives write → load → render unchanged"
    (let [font     (fig/load-font "fonts/standard.flf")
          tmp      (java.io.File/createTempFile "clj-figlet-" ".flf")
          _        (fig/write-font font tmp
                     :comments ["Round-trip test" "clj-figlet"])
          reloaded (fig/load-font tmp)
          text     "Hello World!"]
      (try
        (is (fig/valid-font? reloaded))
        (is (= (fig/render font text)
               (fig/render reloaded text)))
        (finally
          (.delete tmp))))))

(deftest test-write-font-rejects-invalid
  (testing "write-font throws on an invalid font"
    (is (thrown? clojure.lang.ExceptionInfo
                 (fig/write-font {:not "a font"} "/dev/null")))))
