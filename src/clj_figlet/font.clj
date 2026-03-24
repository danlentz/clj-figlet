(ns clj-figlet.font
  "FIGfont Version 2 file parsing and loading.

  A FIGfont file (.flf) is a plain text file containing the graphical
  arrangements of sub-characters that compose each FIGcharacter.  This
  namespace handles every stage of reading one into a Clojure map:
  header parsing, layout parameter interpretation, comment skipping,
  endmark stripping, and extraction of both the 102 required
  FIGcharacters (ASCII 32-126 plus 7 Deutsch) and any code-tagged
  extras.

  The font map returned by `load-font` is a plain Clojure map — no
  custom types or protocols — so fonts compose naturally with the rest
  of the language: they can be merged, filtered, assoc'd with overrides,
  serialized, or passed through any data pipeline.

  See figfont.txt §CREATING FIGFONTS for the full specification."
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Header Parsing                              [figfont.txt §THE HEADER LINE]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;          flf2a$ 6 5 20 15 3 0 143 229
;;            |  | | | |  |  | |  |   |
;;           /  /  | | |  |  | |  |   \
;;  Signature  /  /  | |  |  | |   \   Codetag_Count
;;    Hardblank  /  /  |  |  |  \   Full_Layout*
;;         Height  /   |  |   \  Print_Direction
;;         Baseline   /    \   Comment_Lines
;;          Max_Length      Old_Layout*
;;
;;  The first five characters must be "flf2a".  The sixth character is the
;;  hardblank.  The first seven numeric parameters are required; the last
;;  three (Print_Direction, Full_Layout, Codetag_Count) are optional.
;;
;;  * Old_Layout and Full_Layout are related but not identical.  Both encode
;;    horizontal smushing rules as a bitfield, but Full_Layout also covers
;;    vertical layout.  See §INTERPRETATION OF LAYOUT PARAMETERS.

(defn- parse-header-line
  "Parses the first line of a FIGfont file.  See the header diagram above
  for the positional format."
  [line]
  (when-not (str/starts-with? line "flf2a")
    (throw (ex-info "Invalid FIGfont signature" {:line line})))
  (let [hardblank (nth line 5)
        params    (-> line (subs 6) str/trim (str/split #"\s+"))
        [height baseline max-length old-layout comment-lines
         print-direction full-layout codetag-count] (map parse-long params)]
    {:hardblank       hardblank
     :height          height
     :baseline        baseline
     :max-length      max-length
     :old-layout      old-layout
     :comment-lines   comment-lines
     :print-direction (or print-direction 0)
     :full-layout     full-layout
     :codetag-count   codetag-count}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layout Mode Interpretation    [figfont.txt §INTERPRETATION OF LAYOUT PARAMETERS]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private h-smush-bits
  "Bit-position → rule-number pairs for horizontal smushing rules."
  [[1 1] [2 2] [4 3] [8 4] [16 5] [32 6]])

(def ^:private v-smush-bits
  "Bit-position → rule-number pairs for vertical smushing rules."
  [[256 1] [512 2] [1024 3] [2048 4] [4096 5]])

(defn- extract-rules
  "Returns the set of rule numbers whose bit-positions are set in `value`."
  [value bit-rule-pairs]
  (into #{}
    (keep (fn [[bit rule]]
            (when (pos? (bit-and value bit))
              rule)))
    bit-rule-pairs))

(defn- interpret-layout
  "Determines horizontal and vertical layout modes and smushing rules from
  header parameters.  See figfont.txt §INTERPRETATION OF LAYOUT PARAMETERS."
  [{:keys [old-layout full-layout]}]
  (if full-layout
    {:h-layout      (cond
                      (pos? (bit-and full-layout 128)) :smushing
                      (pos? (bit-and full-layout 64))  :fitting
                      :else                            :full)
     :h-smush-rules (extract-rules full-layout h-smush-bits)
     :v-layout      (cond
                      (pos? (bit-and full-layout 16384)) :smushing
                      (pos? (bit-and full-layout 8192))  :fitting
                      :else                              :full)
     :v-smush-rules (extract-rules full-layout v-smush-bits)}
    ;; No Full_Layout — derive from Old_Layout
    (cond
      (neg? old-layout)
      {:h-layout :full  :h-smush-rules #{}
       :v-layout :full  :v-smush-rules #{}}

      (zero? old-layout)
      {:h-layout :fitting  :h-smush-rules #{}
       :v-layout :full     :v-smush-rules #{}}

      :else
      ;; The C figlet masks old_layout with 31 (bits 0-4, rules 1-5),
      ;; excluding bit 5 (rule 6, hardblank smushing).  When the result
      ;; is zero, this yields universal smushing.  See figlet.c readfont:
      ;;   smushmode = (old_layout & 31) | SM_SMUSH;
      {:h-layout      :smushing
       :h-smush-rules (extract-rules (bit-and old-layout 31) h-smush-bits)
       :v-layout      :full
       :v-smush-rules #{}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FIGcharacter Data Parsing                  [figfont.txt §FIGCHARACTER DATA]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- strip-endmarks
  "Removes trailing endmark characters from a FIGcharacter line.
  The endmark is the last character on the line; the entire trailing run
  of that character is stripped.  See figfont.txt §Basic Data Structure."
  [s]
  (if (empty? s)
    s
    (let [endmark (last s)
          n       (count s)]
      (loop [i (dec n)]
        (if (and (>= i 0) (= (nth s i) endmark))
          (recur (dec i))
          (subs s 0 (inc i)))))))

(defn- parse-figchar-lines
  "Parses `height` lines of font data into a FIGcharacter: a vector of
  strings (one per row) with endmarks removed."
  [lines]
  (mapv strip-endmarks lines))

(defn- parse-code-tag
  "Parses a code tag line to extract the character code.
  Supports decimal, octal (0-prefixed), and hex (0x-prefixed) formats.
  See figfont.txt §CODE TAGGED FIGCHARACTERS."
  [line]
  (-> line str/trim (str/split #"\s+") first Long/decode))

(def ^:private required-codes
  "Character codes that must appear in every FIGfont, in order.
  ASCII 32-126 followed by 7 Deutsch characters.
  See figfont.txt §REQUIRED FIGCHARACTERS."
  (into (vec (range 32 127)) [196 214 220 228 246 252 223]))

(defn- parse-code-tagged
  "Parses code-tagged FIGcharacters from the remaining lines after the
  required characters.  Each entry is a code-tag line followed by `height`
  lines of character data."
  [lines height]
  (loop [remaining lines
         chars     {}]
    (let [[tag & data] remaining]
      (if (or (nil? tag) (str/blank? tag))
        chars
        (let [code      (try (parse-code-tag tag) (catch Exception _ nil))
              char-data (take height data)]
          (if (and code (= height (count char-data)))
            (recur (drop height data)
                   (assoc chars (long code) (parse-figchar-lines (vec char-data))))
            chars))))))

(defn- load-font-from-lines
  "Parses a FIGfont from a sequence of text lines."
  [lines]
  (let [header     (parse-header-line (first lines))
        {:keys [height comment-lines]} header
        layout     (interpret-layout header)
        data-lines (drop (inc comment-lines) lines)
        req-data   (take (* (count required-codes) height) data-lines)
        req-chars  (zipmap required-codes
                           (map parse-figchar-lines (partition height req-data)))
        tag-chars  (parse-code-tagged
                     (drop (* (count required-codes) height) data-lines)
                     height)]
    (merge header layout {:chars (merge req-chars tag-chars)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-font
  "Loads a FIGfont file and returns a font map.  `source` may be a classpath
  resource path (e.g. \"fonts/standard.flf\"), a filesystem path string, a
  java.io.File, or a java.io.Reader.  Classpath resources are tried first
  when given a string.

  The returned map contains the following keys:

    :hardblank       Character used as the hardblank sub-character.
    :height          Number of rows in every FIGcharacter.
    :baseline        Rows from the top to the baseline (for alignment).
    :max-length      Maximum line width in the font file.
    :old-layout      Legacy layout parameter (Old_Layout, -1 to 63).
    :full-layout     Full layout parameter (Full_Layout, 0 to 32767), or nil.
    :comment-lines   Number of comment lines in the font file.
    :print-direction Default print direction (0 = left-to-right).
    :codetag-count   Number of code-tagged characters, or nil.
    :h-layout        Horizontal layout mode (:full, :fitting, or :smushing).
    :h-smush-rules   Set of active horizontal smushing rule numbers (1-6).
    :v-layout        Vertical layout mode (:full, :fitting, or :smushing).
    :v-smush-rules   Set of active vertical smushing rule numbers (1-5).
    :chars           Map of character code (long) to FIGcharacter data, where
                     each FIGcharacter is a vector of strings (one per row)."
  [source]
  (let [reader (cond
                 (instance? java.io.Reader source) source
                 (instance? java.io.File source) (io/reader source)
                 (string? source)
                 (or (some-> (io/resource source) io/reader)
                     (io/reader (io/file source)))
                 :else (io/reader source))
        lines (with-open [r (java.io.BufferedReader. reader)]
                (vec (line-seq r)))]
    (load-font-from-lines lines)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec                                 [figfont.txt §CREATING FIGFONTS]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; --- Header fields ---

;; figfont.txt §Hardblank: "can be any character except a blank (space),
;; a carriage-return, a newline (linefeed) or a null character."
(def ^:private reserved-hardblank-chars #{\space \newline \return (char 0)})

(s/def ::hardblank (s/and char? (complement reserved-hardblank-chars)))

;; figfont.txt §Height: "specifies the consistent height of every
;; FIGcharacter, measured in sub-characters."
(s/def ::height pos-int?)

;; figfont.txt §Baseline: "It is an error for Baseline to be less than 1
;; or greater than the Height parameter."  Cross-field validation with
;; Height is enforced on ::font below.
(s/def ::baseline pos-int?)

;; figfont.txt §Max_Length: maximum line width in the font file.
(s/def ::max-length pos-int?)

;; figfont.txt §Comment_Lines: number of comment lines after the header.
(s/def ::comment-lines nat-int?)

;; figfont.txt §Print_Direction: 0 = left-to-right, 1 = right-to-left.
(s/def ::print-direction #{0 1})

;; figfont.txt §Old_Layout: "Legal values -1 to 63"
(s/def ::old-layout (s/int-in -1 64))

;; figfont.txt §Full_Layout: "Legal values 0 to 32767"
(s/def ::full-layout (s/nilable (s/int-in 0 32768)))

;; figfont.txt §Codetag_Count: number of code-tagged characters, or nil.
(s/def ::codetag-count (s/nilable nat-int?))

;; --- Layout metadata (derived from header during parsing) ---

;; figfont.txt §Layout Modes
(s/def ::h-layout #{:full :fitting :smushing})
(s/def ::v-layout #{:full :fitting :smushing})

;; figfont.txt §Smushing Rules: six horizontal (1-6), five vertical (1-5).
(s/def ::h-smush-rules (s/coll-of (s/int-in 1 7) :kind set?))
(s/def ::v-smush-rules (s/coll-of (s/int-in 1 6) :kind set?))

;; --- FIGcharacter data ---

;; figfont.txt §BASIC DATA STRUCTURE: "there must be a consistent width
;; for each line once the endmarks are removed."
(s/def ::figchar
  (s/and (s/coll-of string? :kind vector? :min-count 1)
         #(apply = (map count %))))

;; figfont.txt §REQUIRED FIGCHARACTERS: all 102 required codes must be
;; present as keys.  Values must be valid FIGcharacters.
(s/def ::chars
  (s/and (s/map-of integer? ::figchar)
         #(every? % required-codes)))

;; --- Font map ---

(s/def ::font
  (s/and
    (s/keys :req-un [::hardblank ::height ::baseline ::max-length
                     ::old-layout ::comment-lines ::h-layout ::h-smush-rules
                     ::v-layout ::v-smush-rules ::chars]
            :opt-un [::print-direction ::full-layout ::codetag-count])
    ;; Cross-field: baseline ≤ height
    #(<= (:baseline %) (:height %))
    ;; Cross-field: every FIGcharacter has exactly :height rows
    (fn [{:keys [height chars]}]
      (every? #(= height (count %)) (vals chars)))))

(defn validate-font
  "Validates a loaded font map against the FIGfont Version 2 specification
  using clojure.spec.  Returns nil if valid, or a spec explain-data map
  describing the violations."
  [font]
  (s/explain-data ::font font))

(defn valid-font?
  "Returns true if the font has no spec violations.  Accepts anything
  `load-font` accepts: a font map, a classpath resource path, a
  filesystem path, a File, or a Reader."
  [font-or-source]
  (let [font (if (map? font-or-source) font-or-source (load-font font-or-source))]
    (s/valid? ::font font)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Serialization                        [figfont.txt §CREATING FIGFONTS]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- compute-full-layout
  "Reconstructs the Full_Layout bitfield from the parsed layout fields."
  [{:keys [h-layout h-smush-rules v-layout v-smush-rules]}]
  (let [h-mode (case h-layout :smushing 128 :fitting 64 0)
        v-mode (case v-layout :smushing 16384 :fitting 8192 0)
        h-bits (reduce + 0 (map {1 1, 2 2, 3 4, 4 8, 5 16, 6 32} h-smush-rules))
        v-bits (reduce + 0 (map {1 256, 2 512, 3 1024, 4 2048, 5 4096} v-smush-rules))]
    (+ h-mode v-mode h-bits v-bits)))

(defn- compute-old-layout
  "Reconstructs Old_Layout from the parsed layout fields."
  [{:keys [h-layout h-smush-rules]}]
  (case h-layout
    :full    -1
    :fitting 0
    :smushing (let [bits (reduce + 0 (map {1 1, 2 2, 3 4, 4 8, 5 16, 6 32} h-smush-rules))]
                (if (zero? bits) 0 bits))))

(defn- serialize-figchar
  "Serializes a FIGcharacter (vector of row strings) with endmarks.
  By convention, all rows get one endmark; the last row gets two."
  [rows endmark]
  (let [n (count rows)]
    (map-indexed (fn [i row]
                   (str row (if (= i (dec n))
                              (str endmark endmark)
                              (str endmark))))
                 rows)))

(defn write-font
  "Serializes a font map to FIGfont Version 2 format and writes it to `dest`.
  `dest` may be anything accepted by `clojure.java.io/writer` (a path string,
  File, OutputStream, etc.).

  The font is validated against the spec before writing; throws ex-info if
  validation fails.

  `comments` is an optional vector of comment lines to include after the
  header.  If omitted, a single attribution line is written.

  See figfont.txt §CREATING FIGFONTS for the file format."
  [font dest & {:keys [comments endmark]
                :or   {comments ["Written by clj-figlet"]
                       endmark  \@}}]
  (when-let [problems (validate-font font)]
    (throw (ex-info "Font fails spec validation" {:problems problems})))
  (let [{:keys [hardblank height baseline chars print-direction]} font
        max-width    (reduce max 0 (for [rows (vals chars) row rows] (count row)))
        full-layout  (compute-full-layout font)
        old-layout   (compute-old-layout font)
        codetag-codes (sort (remove (set required-codes) (keys chars)))
        header       (str "flf2a" hardblank " "
                          height " " baseline " " (+ max-width 2) " "
                          old-layout " " (count comments) " "
                          (or print-direction 0) " "
                          full-layout " " (count codetag-codes))]
    (with-open [w (io/writer dest)]
      ;; Header
      (.write w (str header "\n"))
      ;; Comments
      (doseq [line comments]
        (.write w (str line "\n")))
      ;; Required characters (in order, no code tags)
      (doseq [code required-codes]
        (let [rows (get chars code (vec (repeat height "")))]
          (doseq [line (serialize-figchar rows endmark)]
            (.write w (str line "\n")))))
      ;; Code-tagged characters
      (doseq [code codetag-codes]
        (let [rows (get chars code)]
          (.write w (str code "\n"))
          (doseq [line (serialize-figchar rows endmark)]
            (.write w (str line "\n"))))))))

(defn all-fonts
  "Returns a sorted vector of bundled font names (without path or extension),
  discovered from the fonts/ directory on the classpath."
  []
  (->> (io/resource "fonts")
       io/file
       file-seq
       (keep (fn [^java.io.File f]
               (let [name (.getName f)]
                 (when (str/ends-with? name ".flf")
                   (subs name 0 (- (count name) 4))))))
       sort
       vec))
