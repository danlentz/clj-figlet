(ns clj-figlet.render
  "Rendering engine for FIGlet text.

  FIGcharacters are assembled left-to-right into an output buffer — a
  vector of strings, one per row.  The core operation is computing how
  far each new FIGcharacter can slide left into the buffer before
  visible sub-characters collide, then merging the overlap region
  according to the font's layout mode:

    :full      — no overlap; characters sit at their full designed width.
    :fitting   — slide until characters touch (no visible overlap).
    :smushing  — slide one column further and apply smushing rules at
                 the single junction point where visible sub-characters
                 meet.

  The merge of each row is expressed as three string regions — left
  (buffer only), overlap (merged), right (new character only) — rather
  than a column-by-column loop, so the hot path stays in Clojure string
  operations without mutable state.

  Hardblanks are opaque during layout (they prevent characters from
  sliding through) but are replaced with spaces in the final output.

  See figfont.txt §Layout Modes, §Hardblanks."
  (:require [clj-figlet.font :as font]
            [clj-figlet.smushing :as smushing]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- figchar-width
  "Returns the width of a FIGcharacter (length of its first row)."
  [fig-char]
  (if (seq fig-char)
    (count (first fig-char))
    0))

(defn- pad-right
  "Pads string s with trailing spaces to width n."
  [^String s n]
  (let [pad (- n (.length s))]
    (if (pos? pad)
      (str s (apply str (repeat pad \space)))
      s)))

(defn- trailing-spaces
  "Returns the number of trailing space characters in s."
  [^String s]
  (let [n (.length s)]
    (loop [i (dec n)]
      (if (and (>= i 0) (= (.charAt s i) \space))
        (recur (dec i))
        (- n (inc i))))))

(defn- leading-spaces
  "Returns the number of leading space characters in s."
  [^String s]
  (let [n (.length s)]
    (loop [i 0]
      (if (and (< i n) (= (.charAt s i) \space))
        (recur (inc i))
        i))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Overlap / Smush-Amount Computation  [figfont.txt §Layout Modes, §Hardblanks]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- row-smush-amount
  "Computes the smush amount for a single row pair.  Trailing spaces in the
  buffer row and leading spaces in the char row determine the fitting overlap.
  For smushing, one additional column is attempted at the junction point.
  Hardblanks are treated as visible.  See figfont.txt §Hardblanks."
  [^String buf-row ^String char-row buf-width h-layout hardblank h-smush-rules]
  (let [bt  (trailing-spaces buf-row)
        cl  (leading-spaces char-row)
        fit (+ bt cl)]
    (if-not (= h-layout :smushing)
      fit
      (let [bi (- buf-width bt 1)
            ci cl]
        (if (or (neg? bi) (>= ci (.length char-row)))
          fit
          (let [lc (.charAt buf-row bi)
                rc (.charAt char-row ci)]
            (if (or (= lc \space) (= rc \space)
                    (smushing/h-try-smush (char lc) (char rc) hardblank h-smush-rules))
              (inc fit)
              fit)))))))

(defn- compute-smush-amount
  "Computes how many columns a FIGcharacter can be moved left to overlap with
  the current output buffer.  Returns the minimum per-row smush amount."
  [buffer fig-char hardblank h-layout h-smush-rules]
  (if (= h-layout :full)
    0
    (let [buf-width (count (first buffer))]
      (reduce min Integer/MAX_VALUE
              (map #(row-smush-amount %1 %2 buf-width h-layout hardblank h-smush-rules)
                   buffer fig-char)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Character Appending                           [figfont.txt §Layout Modes]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- merge-sub-char
  "Merges two overlapping sub-characters at a single position.
  Spaces yield to the other character; two visible characters are smushed
  if possible, otherwise the new character wins."
  [bc cc h-layout hardblank h-smush-rules]
  (cond
    (= bc \space) cc
    (= cc \space) bc
    :else (or (when (= h-layout :smushing)
                (smushing/h-try-smush bc cc hardblank h-smush-rules))
              cc)))

(defn- merge-row
  "Merges a buffer row with a new character row, given the smush amount.
  The result has three regions: left, overlap (merged), and right.

  Normally the left region is the buffer prefix that doesn't overlap, and
  the right region is the new character's suffix.  When smush-amount
  exceeds the buffer width (possible when mostly-blank rows allow deep
  overlap), the new character extends past the buffer's left edge: the
  left region becomes the new character's overhang, the overlap spans the
  full buffer, and the right region is the new character's suffix."
  [^String buf-row ^String char-row smush-amount h-layout hardblank h-smush-rules]
  (let [bw (int (.length buf-row))
        keep-left (- bw smush-amount)]
    (cond
      ;; Empty buffer (first character): trim leading blank columns
      (zero? bw)
      (subs char-row smush-amount)

      ;; Smush exceeds buffer width: new character extends past the buffer's
      ;; left edge.  The overhang columns are clipped (they precede column 0).
      ;; The full buffer participates in the overlap.
      (neg? keep-left)
      (let [char-skip (- smush-amount bw)]
        (str (apply str
               (map #(merge-sub-char %1 %2 h-layout hardblank h-smush-rules)
                    buf-row
                    (subs char-row char-skip smush-amount)))
             (subs char-row smush-amount)))

      ;; Normal case: left (buffer only) + overlap (merged) + right (new char)
      :else
      (str (subs buf-row 0 keep-left)
           (apply str
             (map #(merge-sub-char %1 %2 h-layout hardblank h-smush-rules)
                  (subs buf-row keep-left)
                  (subs char-row 0 smush-amount)))
           (subs char-row smush-amount)))))

(defn- append-figchar
  "Appends a FIGcharacter to the output buffer, applying the appropriate
  layout mode.  Buffer is a vector of strings (one per row).  When the buffer
  is empty, leading blank columns are trimmed from the first character."
  [buffer fig-char {:keys [hardblank height h-layout h-smush-rules]}]
  (let [buffer       (or buffer (vec (repeat height "")))
        buf-width    (count (first buffer))
        char-width   (figchar-width fig-char)
        smush-amount (compute-smush-amount buffer fig-char hardblank h-layout h-smush-rules)]
    (mapv (fn [buf-row char-row]
            (merge-row (pad-right buf-row buf-width)
                       (pad-right char-row char-width)
                       smush-amount h-layout hardblank h-smush-rules))
          buffer fig-char)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Post-Processing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- replace-hardblanks
  "Replaces hardblank characters with spaces in the final output.
  See figfont.txt §Hardblanks."
  [rows hardblank]
  (mapv #(str/replace % (str hardblank) " ") rows))

(defn- rtrim-rows
  "Removes trailing whitespace from each row."
  [rows]
  (mapv str/trimr rows))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- as-bundled-path
  "Expands a font name to a bundled resource path.  Adds the fonts/ prefix
  and .flf extension only if not already present."
  [s]
  (let [with-ext (if (str/ends-with? s ".flf") s (str s ".flf"))]
    (if (str/includes? with-ext "/") with-ext (str "fonts/" with-ext))))

(defn- resolve-font
  "Coerces the argument to a font map.  If already a map, returns it.
  Otherwise delegates to `load-font`, trying the argument as-is first,
  then as a bundled font name if the first attempt fails."
  [font-or-name]
  (if (map? font-or-name)
    font-or-name
    (try
      (font/load-font font-or-name)
      (catch Exception _
        (font/load-font (as-bundled-path font-or-name))))))

(defn render
  "Renders text as a FIGure.  The first argument may be:

    - A font map (as returned by `load-font`)
    - A font name string (e.g. \"standard\"), which loads the bundled font
      from resources/fonts/<name>.flf
    - Any other source accepted by `load-font` (filesystem path, File, Reader)

  Returns a string containing the rendered ASCII art, terminated by a
  newline.

  Each input character is looked up in the font's :chars map by its
  character code.  If a character is not present in the font, FIGcharacter
  code 0 (the font's \"missing character\") is used as a fallback; if that
  is also absent, the character is silently omitted.

  FIGcharacters are assembled left-to-right using the font's default
  horizontal layout mode (:full, :fitting, or :smushing) and its
  configured smushing rules.  Hardblank sub-characters are replaced with
  spaces in the final output, and trailing whitespace is trimmed from
  each line."
  [font-or-name text]
  (let [{:keys [hardblank height chars] :as font} (resolve-font font-or-name)
        empty-buf (vec (repeat height ""))
        buffer    (reduce
                    (fn [buf ch]
                      (let [fig-char (or (get chars (long ch))
                                         (get chars 0)
                                         empty-buf)]
                        (append-figchar buf fig-char font)))
                    nil
                    text)
        output    (-> (or buffer empty-buf)
                      (replace-hardblanks hardblank)
                      rtrim-rows)]
    (str (str/join "\n" output) "\n")))
