(ns clj-figlet.showcase-test
  "A creative showcase of clj-figlet capabilities.

  These tests double as living documentation: each one demonstrates a
  practical or playful way to put FIGlet rendering to work inside a
  Clojure application.  Run them, read the output, steal the patterns."
  (:require [clojure.test :refer :all]
            [clj-figlet.core :as fig]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- lines
  "Splits rendered output into non-trailing-blank lines."
  [s]
  (->> (str/split-lines s)
       (reverse)
       (drop-while str/blank?)
       (reverse)
       vec))

(defn- visible-width
  "The width of the widest line in a rendered FIGure."
  [s]
  (reduce max 0 (map count (str/split-lines s))))

(defn- box
  "Wraps a rendered FIGure in a Unicode box-drawing frame."
  [s]
  (let [ls   (lines s)
        w    (reduce max 0 (map count ls))
        pad  (fn [line] (let [n (- w (count line))]
                          (str line (apply str (repeat n \space)))))
        top  (str "\u250c" (apply str (repeat (+ w 2) \u2500)) "\u2510")
        bot  (str "\u2514" (apply str (repeat (+ w 2) \u2500)) "\u2518")
        body (map #(str "\u2502 " (pad %) " \u2502") ls)]
    (str/join "\n" (concat [top] body [bot]))))

(defn- center
  "Centers every line of `s` within `width` columns."
  [s width]
  (let [ls (str/split-lines s)]
    (->> ls
         (map (fn [line]
                (let [pad (max 0 (quot (- width (count line)) 2))]
                  (str (apply str (repeat pad \space)) line))))
         (str/join "\n"))))

(defn- side-by-side
  "Places two rendered FIGures next to each other with `gap` spaces between."
  [left right & {:keys [gap] :or {gap 4}}]
  (let [ll      (lines left)
        rl      (lines right)
        lw      (reduce max 0 (map count ll))
        height  (max (count ll) (count rl))
        pad-l   (fn [i] (let [line (get ll i "")]
                          (str line (apply str (repeat (- lw (count line)) \space)))))
        spacer  (apply str (repeat gap \space))]
    (->> (range height)
         (map (fn [i] (str (pad-l i) spacer (get rl i ""))))
         (str/join "\n")
         (#(str % "\n")))))

;; ---------------------------------------------------------------------------
;; 1.  The font sampler — render the same word in every bundled font
;; ---------------------------------------------------------------------------

(deftest test-font-sampler
  (testing "Every bundled font renders 'Clojure' without error and produces output"
    (let [fonts ["standard" "small" "big" "slant" "banner"
                 "mini" "shadow" "lean" "block"]]
      (doseq [f fonts]
        (let [out (fig/render-str f "Clojure")]
          (is (not (str/blank? out))
              (str "Font " f " produced blank output"))
          (is (> (count (lines out)) 1)
              (str "Font " f " should be multi-line")))))))

;; ---------------------------------------------------------------------------
;; 2.  Box-framing a banner — CLI splash screens
;; ---------------------------------------------------------------------------

(deftest test-boxed-banner
  (testing "A rendered FIGure can be framed for use as a startup banner"
    (let [art    (fig/render-str "small" "clj-figlet")
          framed (box art)]
      ;; The box adds two chars of border + two of padding on each side
      (is (str/starts-with? framed "\u250c"))
      (is (str/ends-with? framed "\u2518"))
      ;; Every interior line is pipe-bounded
      (doseq [line (butlast (rest (str/split-lines framed)))]
        (is (str/starts-with? line "\u2502"))
        (is (str/ends-with?   line "\u2502"))))))

;; ---------------------------------------------------------------------------
;; 3.  Side-by-side comparison — font previewing
;; ---------------------------------------------------------------------------

(deftest test-side-by-side
  (testing "Two FIGures can be placed next to each other"
    (let [left   (fig/render-str "standard" "AB")
          right  (fig/render-str "slant"    "AB")
          combo  (side-by-side left right :gap 6)]
      ;; Each output line should be wider than either input alone
      (is (> (visible-width combo)
             (max (visible-width left) (visible-width right)))))))

;; ---------------------------------------------------------------------------
;; 4.  Countdown timer — dynamic rendering from a sequence
;; ---------------------------------------------------------------------------

(deftest test-countdown
  (testing "Rendering a countdown sequence from a range"
    (let [font   (fig/load-font "fonts/big.flf")
          frames (mapv #(fig/render font (str %)) (range 3 0 -1))]
      (is (= 3 (count frames)))
      ;; Each frame is non-blank, and "1" should be narrower than "3"
      (is (every? #(not (str/blank? %)) frames))
      (is (< (visible-width (frames 2))   ; "1"
             (visible-width (frames 0))))  ; "3"
      )))

;; ---------------------------------------------------------------------------
;; 5.  Mapping over words — per-word font rendering
;; ---------------------------------------------------------------------------

(deftest test-per-word-render
  (testing "Each word of a sentence can be rendered independently"
    (let [sentence "one two three"
          font     (fig/load-font "fonts/small.flf")
          banners  (mapv #(fig/render font %) (str/split sentence #"\s+"))]
      (is (= 3 (count banners)))
      ;; All banners have the same height (same font)
      (is (apply = (map #(count (lines %)) banners))))))

;; ---------------------------------------------------------------------------
;; 6.  Font metadata introspection — fonts are just maps
;; ---------------------------------------------------------------------------

(deftest test-font-introspection
  (testing "Font maps expose useful metadata for tooling"
    (let [font (fig/load-font "fonts/standard.flf")]
      ;; Heights & baselines — useful for aligning multi-font compositions
      (is (pos? (:height font)))
      (is (pos? (:baseline font)))
      (is (<= (:baseline font) (:height font)))

      ;; Layout metadata
      (is (#{:full :fitting :smushing} (:h-layout font)))
      (is (set? (:h-smush-rules font)))

      ;; Character repertoire — we can enumerate what the font covers
      (let [codes (set (keys (:chars font)))]
        ;; Full printable ASCII
        (is (every? codes (range 32 127)))
        ;; Deutsch extras
        (is (every? codes [196 214 220 228 246 252 223]))))))

;; ---------------------------------------------------------------------------
;; 7.  Substitution cipher art — character-level transforms
;; ---------------------------------------------------------------------------

(deftest test-substitution-art
  (testing "Rendered output can be post-processed with character substitution"
    (let [art     (fig/render-str "standard" "Hi")
          shaded  (-> art
                      (str/replace #"[|/\\]" "#")
                      (str/replace "_" "~"))]
      ;; Original had structure chars; shaded replaces them
      (is (not (str/includes? shaded "|")))
      (is (not (str/includes? shaded "/")))
      (is (not (str/includes? shaded "\\")))
      ;; But it still has the right number of lines
      (is (= (count (lines art))
             (count (lines shaded)))))))

;; ---------------------------------------------------------------------------
;; 8.  Text centering — layout for fixed-width displays
;; ---------------------------------------------------------------------------

(deftest test-centering
  (testing "FIGures can be centered within a fixed width"
    (let [art      (fig/render-str "mini" "ok")
          centered (center art 80)]
      (doseq [line (str/split-lines centered)]
        ;; Every non-blank line should have leading whitespace
        (when (not (str/blank? line))
          (is (str/starts-with? line " ")
              "Centered lines should have leading padding"))
        ;; No line should exceed the target width
        (is (<= (count line) 80))))))

;; ---------------------------------------------------------------------------
;; 9.  Font height comparison — standard vs. compact
;; ---------------------------------------------------------------------------

(deftest test-height-comparison
  (testing "Different fonts produce different vertical sizes for the same text"
    (let [word    "Go"
          std     (fig/render-str "standard" word)
          mini    (fig/render-str "mini" word)
          banner  (fig/render-str "banner" word)]
      ;; mini should be the most compact
      (is (< (count (lines mini))
             (count (lines std))))
      ;; banner is typically the tallest of the common fonts
      (is (>= (count (lines banner))
              (count (lines std)))))))

;; ---------------------------------------------------------------------------
;; 10. Load-once, render-many — font caching pattern
;; ---------------------------------------------------------------------------

(deftest test-font-reuse
  (testing "A font loaded once can render many different strings"
    (let [font    (fig/load-font "fonts/slant.flf")
          labels  ["OK" "WARN" "ERR"]
          renders (mapv #(fig/render font %) labels)]
      (is (= 3 (count renders)))
      ;; Wider label text should produce wider output
      (is (< (visible-width (renders 0))    ; "OK"
             (visible-width (renders 1))))   ; "WARN"
      ;; All renders share the same line count (same font height)
      (is (apply = (map #(count (lines %)) renders))))))

;; ---------------------------------------------------------------------------
;; 11. Single-character glyph extraction — ASCII art sprites
;; ---------------------------------------------------------------------------

(deftest test-single-char-glyphs
  (testing "Individual characters can be extracted as small ASCII sprites"
    (let [font (fig/load-font "fonts/standard.flf")
          star (fig/render font "*")
          at   (fig/render font "@")
          amp  (fig/render font "&")]
      ;; Each is a valid multi-line rendering
      (is (> (count (lines star)) 1))
      (is (> (count (lines at)) 1))
      (is (> (count (lines amp)) 1))
      ;; Punctuation glyphs are typically narrower than wide letters
      (is (< (visible-width star)
             (visible-width (fig/render font "W")))))))

;; ---------------------------------------------------------------------------
;; 12. Table of contents — structured multi-line output
;; ---------------------------------------------------------------------------

(deftest test-table-of-contents
  (testing "Multiple headings rendered and joined create a TOC-like structure"
    (let [font     (fig/load-font "fonts/small.flf")
          headings ["I" "II" "III"]
          renders  (map-indexed
                    (fn [i h]
                      (str "  Chapter " (inc i) "\n"
                           (fig/render font h)
                           "\n"))
                    headings)
          toc      (str/join renders)]
      ;; All three chapters present
      (is (str/includes? toc "Chapter 1"))
      (is (str/includes? toc "Chapter 2"))
      (is (str/includes? toc "Chapter 3"))
      ;; FIGure content is embedded between the labels
      (is (> (count (str/split-lines toc)) (* 3 (:height font)))))))

;; ---------------------------------------------------------------------------
;; 13. Shadow font has depth — visual properties of different styles
;; ---------------------------------------------------------------------------

(deftest test-shadow-depth
  (testing "The shadow font produces wider output due to shadow sub-characters"
    (let [word   "Ah"
          slim   (fig/render-str "small" word)
          shad   (fig/render-str "shadow" word)]
      ;; Shadow's extra weight makes it wider
      (is (> (visible-width shad)
             (visible-width slim))))))

;; ---------------------------------------------------------------------------
;; 14. Programmatic string building — FIGlet inside data pipelines
;; ---------------------------------------------------------------------------

(deftest test-pipeline
  (testing "FIGlet plays well in a functional data pipeline"
    (let [font (fig/load-font "fonts/mini.flf")]
      (->> ["alpha" "beta" "gamma"]
           (map str/upper-case)
           (map #(fig/render font %))
           (map lines)
           (run! (fn [ls]
                   ;; Every rendering is non-trivial
                   (is (> (count ls) 1)
                       "Pipeline render should be multi-line")))))))

;; ---------------------------------------------------------------------------
;; 15. Stress test — the full printable ASCII set
;; ---------------------------------------------------------------------------

(deftest test-full-ascii
  (testing "Every printable ASCII character renders without error"
    (let [font (fig/load-font "fonts/standard.flf")]
      (doseq [c (map char (range 32 127))]
        (let [out (fig/render font (str c))]
          (is (string? out)
              (str "Character " (int c) " (" c ") should render")))))))
