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

  The per-row merge (`addchar-row`) is a direct translation of the C
  figlet's `addchar` function, using a mutable char array to replicate
  C string semantics (NUL truncation, in-place writes, STRCAT at
  STRLEN).  This is the one place in the codebase where Java interop is
  used for correctness rather than convenience — immutable strings
  cannot express NUL-terminated truncation.

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
  "Returns the width of a FIGcharacter as the length of its first row,
  matching the C figlet's `currcharwidth = STRLEN(currchar[0])`."
  [fig-char]
  (if (seq fig-char) (count (first fig-char)) 0))

(defn- pad-right
  "Pads string s with trailing spaces to width n."
  [s n]
  (let [pad (- n (count s))]
    (if (pos? pad)
      (str s (apply str (repeat pad \space)))
      s)))

(defn- leading-spaces
  "Returns the number of leading space characters in s."
  [s]
  (let [n (count s)]
    (loop [i 0]
      (if (and (< i n) (= (nth s i) \space))
        (recur (inc i))
        i))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Overlap / Smush-Amount Computation  [figfont.txt §Layout Modes, §Hardblanks]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private nul
  "Sentinel character representing C's NUL string terminator."
  (char 0))

(defn- last-visible-pos
  "Returns the index of the last non-space character in s, scanning from
  the end.  Matches the C figlet's linebd scan exactly:
    for (linebd=STRLEN(s); ch1=s[linebd], (linebd>0&&(!ch1||ch1==' ')); linebd--)
  Returns 0 for empty and all-space strings (C starts at STRLEN and stops
  at position 0)."
  [s]
  (let [n (count s)]
    (loop [i n]
      (if (and (> i 0)
               (let [c (if (< i n) (nth s i) nul)]
                 (or (= c nul) (= c \space))))
        (recur (dec i))
        i))))

(defn- row-smush-amount
  "Computes the smush amount for a single row pair using the C figlet's
  formula: `amt = charbd + outlinelen - 1 - linebd`.  See figlet.c
  smushamt()."
  [buf-row char-row buf-width prev-width char-width
   h-layout hardblank h-smush-rules]
  (let [linebd (last-visible-pos buf-row)
        charbd (leading-spaces char-row)
        amt    (+ charbd (- buf-width 1) (- linebd))
        ch1    (when (< linebd (count buf-row)) (nth buf-row linebd))
        ch2    (when (< charbd (count char-row)) (nth char-row charbd))]
    (cond
      ;; Buffer row blank — extra column always granted
      (or (nil? ch1) (= ch1 \space))
      (inc amt)

      ;; Char row blank — no visible char to smush
      (nil? ch2)
      amt

      ;; Width < 2 or not smushing — no junction smush
      (or (not= h-layout :smushing)
          (< prev-width 2)
          (< char-width 2))
      amt

      ;; Both visible — try smushing at the junction
      (smushing/h-try-smush ch1 ch2 hardblank h-smush-rules)
      (inc amt)

      :else amt)))

(defn- compute-smush-amount
  "Computes how many columns a FIGcharacter can be moved left to overlap with
  the current output buffer.  Returns the minimum per-row smush amount,
  capped at the current character's width (matching figlet.c smushamt())."
  [buffer fig-char prev-width hardblank h-layout h-smush-rules]
  (if (= h-layout :full)
    0
    (let [buf-width  (count (first buffer))
          char-width (figchar-width fig-char)]
      (max 0 (min char-width
                   (reduce min Integer/MAX_VALUE
                           (map #(row-smush-amount %1 %2 buf-width prev-width char-width
                                                   h-layout hardblank h-smush-rules)
                                buffer fig-char)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Character Appending                           [figfont.txt §Layout Modes]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; addchar-row is a direct translation of the C figlet's addchar per-row
;; logic.  It uses a mutable char array because the C code relies on NUL-
;; terminated string semantics: writing '\0' to a position truncates the
;; string, and STRCAT appends at the first '\0'.  There is no idiomatic
;; Clojure equivalent for this behavior.  See figlet.c addchar().

(defn- addchar-row
  [^String buf-row ^String char-row outlinelen smush-amount
   prev-width char-width h-layout hardblank h-smush-rules]
  (let [buf-len   (.length buf-row)
        char-len  (.length char-row)
        cap       (+ (max buf-len outlinelen) char-len 1)
        ^chars wb (char-array cap nul)]
    (.getChars buf-row 0 buf-len wb 0)
    (dotimes [k smush-amount]
      (let [column (max 0 (+ (- outlinelen smush-amount) k))
            lch    (aget wb column)
            rch    (if (< k char-len) (.charAt char-row k) nul)]
        (aset wb column
              (char (cond
                      (= lch \space) rch
                      (= rch \space) lch
                      (or (< prev-width 2) (< char-width 2)) nul
                      (not= h-layout :smushing) nul
                      :else (or (smushing/h-try-smush lch rch hardblank h-smush-rules)
                                nul))))))
    (let [strlen   (loop [i 0]
                     (if (or (>= i cap) (= (aget wb i) (char 0))) i (recur (inc i))))
          tail-len (if (> char-len smush-amount) (- char-len smush-amount) 0)
          result   (+ strlen tail-len)]
      (when (pos? tail-len)
        (.getChars char-row (int smush-amount) (int char-len) wb (int strlen)))
      (String. ^chars wb (int 0) (int (min result cap))))))

(defn- append-figchar
  "Appends a FIGcharacter to the output buffer, applying the appropriate
  layout mode.  Buffer is a vector of strings (one per row).  When the buffer
  is empty, leading blank columns are trimmed from the first character."
  [buffer fig-char prev-width {:keys [hardblank height h-layout h-smush-rules]}]
  (let [buffer       (or buffer (vec (repeat height "")))
        buf-width    (count (first buffer))
        char-width   (figchar-width fig-char)
        smush-amount (compute-smush-amount buffer fig-char prev-width
                                           hardblank h-layout h-smush-rules)]
    (mapv (fn [buf-row char-row]
            (addchar-row buf-row char-row buf-width smush-amount
                         prev-width char-width
                         h-layout hardblank h-smush-rules))
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
        empty-buf    (vec (repeat height ""))
        [buffer _]   (reduce
                       (fn [[buf prev-w] ch]
                         (let [fig-char (or (get chars (long ch))
                                            (get chars 0)
                                            empty-buf)]
                           [(append-figchar buf fig-char prev-w font)
                            (figchar-width fig-char)]))
                       [nil 0]
                       text)
        output       (-> (or buffer empty-buf)
                         (replace-hardblanks hardblank)
                         rtrim-rows)]
    (str (str/join "\n" output) "\n")))
