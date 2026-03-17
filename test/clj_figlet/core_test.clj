(ns clj-figlet.core-test
  (:require [clojure.test :refer :all]
            [clj-figlet.core :as figlet]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn- reference-figlet
  "Calls the system figlet binary and returns its output.
  Uses -w 10000 to prevent word wrapping."
  [font-name text]
  (let [result (sh "figlet" "-w" "10000" "-f" font-name text)]
    (when (zero? (:exit result))
      (:out result))))

(defn- normalize-output
  "Normalizes figlet output for comparison: trims trailing whitespace on each
  line and removes any trailing blank lines."
  [s]
  (let [lines (str/split s #"\n" -1)
        trimmed (mapv str/trimr lines)]
    ;; Remove trailing empty lines
    (loop [v trimmed]
      (if (and (seq v) (str/blank? (peek v)))
        (recur (pop v))
        (str/join "\n" v)))))

;; ---------------------------------------------------------------------------
;; Font loading tests
;; ---------------------------------------------------------------------------

(deftest test-load-font
  (testing "loads standard.flf from resources"
    (let [font (figlet/load-font "fonts/standard.flf")]
      (is (= 6 (:height font)))
      (is (= 5 (:baseline font)))
      (is (= \$ (:hardblank font)))
      (is (= :smushing (:h-layout font)))
      (is (contains? (:chars font) 65))   ; 'A'
      (is (contains? (:chars font) 32))   ; space
      (is (contains? (:chars font) 126))  ; '~'
      (is (= 6 (count (get (:chars font) 65)))))))

(deftest test-load-small-font
  (testing "loads small.flf from resources"
    (let [font (figlet/load-font "fonts/small.flf")]
      (is (= 5 (:height font)))
      (is (= 4 (:baseline font)))
      (is (= \$ (:hardblank font))))))

;; ---------------------------------------------------------------------------
;; Comparison tests against reference figlet
;; ---------------------------------------------------------------------------

(def test-strings
  ["Hello"
   "World"
   "Hello World"
   "FIG"
   "ABC"
   "123"
   "Test!"
   "ab"
   "Hi"
   "A"
   "Z"
   " "
   "  "
   "a b"
   "@#$%"
   "!@#"
   "({[<>]})"
   "X"
   "|||"
   "/\\"
   "mmm"
   "iii"
   "FIGLET"
   "abcdefghijklmnopqrstuvwxyz"])

(def test-fonts
  ["standard" "small" "big" "slant" "banner" "mini" "shadow" "lean" "block"
   "doom" "script" "smscript" "smslant" "smshadow" "graffiti" "starwars"])

(deftest test-vs-reference-figlet
  (doseq [font-name test-fonts]
    (let [font (figlet/load-font (str "fonts/" font-name ".flf"))]
      (doseq [text test-strings]
        (testing (str font-name ": \"" text "\"")
          (let [expected (reference-figlet font-name text)
                actual (figlet/render font text)]
            (when expected
              (is (= (normalize-output expected)
                     (normalize-output actual))
                  (str "Mismatch for font=" font-name " text=\"" text "\"\n"
                       "EXPECTED:\n" expected
                       "ACTUAL:\n" actual)))))))))

(deftest test-render-by-name
  (testing "render accepts a font name string"
    (let [expected (reference-figlet "standard" "OK")
          actual (figlet/render "standard" "OK")]
      (is (= (normalize-output expected)
             (normalize-output actual))))))

(deftest test-empty-input
  (testing "empty string renders without error"
    (let [font (figlet/load-font "fonts/standard.flf")
          result (figlet/render font "")]
      (is (string? result)))))
