(ns clj-figlet.core-test
  (:require [clj-figlet.core    :as    fig]
            [clojure.java.shell :refer [sh]]
            [clojure.string     :as    str]
            [clojure.test       :refer :all]))

(defn- font-path
  "Returns the absolute filesystem path to a bundled font file."
  [font-name]
  (-> (clojure.java.io/resource (str "fonts/" font-name ".flf"))
      clojure.java.io/file
      str))

(defn- reference-figlet
  "Calls the system figlet binary and returns its output.  Passes the full
  path to the bundled font file so the comparison uses the exact same font
  we ship, regardless of what the system figlet has installed."
  [font-name text]
  (let [result (sh "figlet" "-w" "10000" "-f" (font-path font-name) "--" text)]
    (when (zero? (:exit result))
      (:out result))))

(defn- normalize-output
  "Normalizes figlet output for comparison: trims trailing whitespace on each
  line and removes any trailing blank lines."
  [s]
  (->> (str/split s #"\n" -1)
       (mapv str/trimr)
       (reverse)
       (drop-while str/blank?)
       (reverse)
       (str/join "\n")))

(defn- assert-matches-reference
  "Asserts that our render output matches the reference C figlet for the
  given font name and text.  Includes both outputs in the failure message."
  [font-name text]
  (let [expected (reference-figlet font-name text)
        actual   (fig/render font-name text)]
    (when expected
      (is (= (normalize-output expected)
             (normalize-output actual))
          (str "Mismatch for font=" font-name " text=" (pr-str text) "\n"
               "EXPECTED:\n" expected
               "ACTUAL:\n" actual)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Font Loading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-font-loading
  (testing "standard.flf"
    (let [font (fig/load-font "fonts/standard.flf")]
      (is (= 6 (:height font)))
      (is (= 5 (:baseline font)))
      (is (= \$ (:hardblank font)))
      (is (= :smushing (:h-layout font)))
      (is (contains? (:chars font) 65))
      (is (contains? (:chars font) 32))
      (is (contains? (:chars font) 126))
      (is (= 6 (count (get (:chars font) 65))))))

  (testing "small.flf"
    (let [font (fig/load-font "fonts/small.flf")]
      (is (= 5 (:height font)))
      (is (= 4 (:baseline font)))
      (is (= \$ (:hardblank font))))))

(deftest test-render-by-name
  (testing "render accepts a bare font name"
    (let [expected (reference-figlet "standard" "OK")
          actual   (fig/render "standard" "OK")]
      (is (= (normalize-output expected)
             (normalize-output actual))))))

(deftest test-empty-input
  (testing "empty string renders without error"
    (is (string? (fig/render "standard" "")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reference Comparison — Common Strings
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def common-strings
  "Recognizable words and phrases covering caps, lowercase, digits,
  punctuation, spaces, and the full alphabet."
  ["Hello"
   "World"
   "Hello World"
   "FIG"
   "Test!"
   "123"
   "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
   "abcdefghijklmnopqrstuvwxyz"
   "0123456789"])

(deftest test-common-strings
  (doseq [font-name (fig/all-fonts)
          text      common-strings]
    (testing (str font-name ": " (pr-str text))
      (assert-matches-reference font-name text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reference Comparison — Smushing Stress
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def smushing-strings
  "Strings targeting specific smushing rules and edge cases.

  Rule 1 — Equal character:     ||  //  \\\\
  Rule 2 — Underscore:          _|  _/  _\\
  Rule 3 — Hierarchy:           []{}()<>
  Rule 4 — Opposite pair:       ][  }{  )(
  Rule 5 — Big X:               /\\  \\/  ><
  Rule 6 — Hardblank:           (implicit in space handling)
  Width extremes:                W vs i adjacency
  Punctuation clusters:          dense narrow chars"
  [;; Rule 1: equal characters
   "||" "//" "\\\\"
   ;; Rule 2: underscore meets hierarchy chars
   "_|" "_/" "_\\"
   ;; Rule 3: all hierarchy classes in sequence
   "[]{}()<>"
   ;; Rule 4: reversed bracket pairs
   "][}{)("
   ;; Rule 5: Big X pairs
   "/\\" "\\/" "><"
   ;; Hardblank / space handling
   " A " "A B C" "  " " "
   ;; Nested brackets: multi-rule overlap
   "({[]})" "<[{(|)}]>"
   ;; Dense punctuation
   "!@#$%^&*"
   ;; Width extremes
   "W" "i" "Wi" "iW" "WiW" "iWi"
   ;; Small punctuation cluster
   ".,;:'\""
   ;; Mixed case + digits + symbols
   "Abc-123!" "x*y=z"])

(deftest test-smushing-stress
  (doseq [font-name (fig/all-fonts)
          text      smushing-strings]
    (testing (str font-name ": " (pr-str text))
      (assert-matches-reference font-name text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reference Comparison — Regression Cases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def problem-cases
  "Specific font+string pairs that triggered bugs during development.
  Each entry is [font-name text]."
  [;; Underscore-only chars with universal smushing (deep overlap bug)
   ["mini"   "_/"]
   ["mini"   "_\\"]
   ["shadow" "_\\"]
   ;; First-char with all-blank rows + smushing (linebd off-by-one)
   ["mini"   "_~"]
   ["shadow" ".W"]
   ;; Hardblank-only smushing without Full_Layout (old_layout & 31 masking)
   ["Colossal" "Hi"]
   ["Colossal" "AB"]
   ;; Narrow chars (width < 2 disables smushing)
   ["dancingfont" "[]{}"]
   ["dancingfont" "!@#$%^&*"]
   ;; Formerly malformed fonts (now fixed) — # and > chars
   ["Bear"        "#test"]
   ["Bear"        "a>b"]
   ["flowerpower" "#test"]
   ["dancingfont" "#test"]])

(deftest test-problem-cases
  (doseq [[font-name text] problem-cases]
    (testing (str font-name ": " (pr-str text))
      (assert-matches-reference font-name text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reference Comparison — Generative
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:const +generative-reps+
  "Number of random strings generated per font per category."
  50)

(def ^:const +min-length+
  "Minimum length of generated test strings."
  10)

(def ^:const +max-length+
  "Maximum length of generated test strings."
  32)

(def ^:private pangrams
  "Well-known pangrams and character-rich phrases for shuffled substrings."
  ["The Quick Brown Fox Jumps Over The Lazy Dog!"
   "Pack My Box With Five Dozen Liquor Jugs."
   "How Vexingly Quick Daft Zebras Jump!"
   "Sphinx of Black Quartz, Judge My Vow."
   "Mr. Jock, TV Quiz PhD, Bags Few Lynx."
   "0123456789 + @#$%^&*() = {[<>]}/|\\~"])

(def ^:private printable-ascii
  (vec (map char (range 32 127))))

(defn- random-text
  "Returns a string of `len` characters randomly drawn from `chars`."
  [chars len]
  (apply str (repeatedly len #(rand-nth chars))))

(defn- shuffled-text
  "Returns a string of `len` characters sampled without replacement from
  `source` (shuffled)."
  [source len]
  (apply str (take len (shuffle (seq source)))))

(deftest test-generative
  (doseq [font-name (fig/all-fonts)]

    (testing "Shuffled pangram substrings (mixed case, punctuation, spaces)"
      (doseq [pangram pangrams]
        (dotimes [_ +generative-reps+]
          (let [text (shuffled-text pangram (+ +min-length+ (rand-int (- +max-length+ +min-length+))))]
            (testing (str font-name ": pangram shuffle " (pr-str text))
              (assert-matches-reference font-name text))))))

    (testing "Random printable ASCII (full character set)"
      (dotimes [_ +generative-reps+]
        (let [text (random-text printable-ascii (+ +min-length+ (rand-int (- +max-length+ +min-length+))))]
          (testing (str font-name ": ascii random " (pr-str text))
            (assert-matches-reference font-name text)))))))
