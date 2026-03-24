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
  "Returns the width of a FIGcharacter as the length of its first row,
  matching the C figlet's `currcharwidth = STRLEN(currchar[0])`.  This
  governs the smush cap and accumulated buffer width."
  [fig-char]
  (if (seq fig-char) (count (first fig-char)) 0))


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

(defn- last-visible-pos
  "Returns the index of the last non-space character in s, scanning from
  the end.  Matches the C figlet's linebd scan exactly:
    for (linebd=STRLEN(s); ch1=s[linebd], (linebd>0&&(!ch1||ch1==' ')); linebd--)
  Returns 0 for empty and all-space strings (C starts at STRLEN and stops
  at position 0)."
  [^String s]
  (let [n (.length s)]
    (loop [i n]
      (if (and (> i 0)
               (let [c (if (< i n) (.charAt s i) (char 0))]
                 (or (= c (char 0)) (= c \space))))
        (recur (dec i))
        i))))

(defn- row-smush-amount
  "Computes the smush amount for a single row pair using the C figlet's
  formula: `amt = charbd + outlinelen - 1 - linebd`, where linebd and
  charbd are the last and first non-space positions respectively, and
  outlinelen is row 0's width (buf-width).  See figlet.c smushamt().

  `prev-width` and `char-width` are the original FIGcharacter widths;
  the C figlet disables smushing when either is less than 2."
  [^String buf-row ^String char-row buf-width prev-width char-width
   h-layout hardblank h-smush-rules]
  (let [linebd (last-visible-pos buf-row)
        charbd (leading-spaces char-row)
        amt    (+ charbd (- buf-width 1) (- linebd))]
    ;; The C figlet checks ch1 (buffer's last visible) BEFORE consulting
    ;; smushem.  Blank buffer rows always get +1, unconditionally.
    (let [ch1 (when (< linebd (.length buf-row)) (.charAt buf-row linebd))
          ch2 (when (< charbd (.length char-row)) (.charAt char-row charbd))]
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
        (smushing/h-try-smush (char ch1) (char ch2) hardblank h-smush-rules)
        (inc amt)

        :else amt))))

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

(defn- addchar-row
  "Direct translation of the C figlet's addchar per-row logic (left-to-right).
  Uses a char array to replicate C string semantics (NUL truncation, in-place
  writes, STRCAT at STRLEN).  See figlet.c addchar()."
  [^String buf-row ^String char-row outlinelen smush-amount
   prev-width char-width h-layout hardblank h-smush-rules]
  (let [buf-len   (.length buf-row)
        char-len  (.length char-row)
        cap       (+ (max buf-len outlinelen) char-len 1)
        ^chars wb (char-array cap (char 0))]
    ;; Copy existing output row into working buffer (NUL-terminated by init)
    (.getChars buf-row 0 buf-len wb 0)
    ;; Smush overlap: write into positions [outlinelen-smush, outlinelen-1]
    (dotimes [k smush-amount]
      (let [column (max 0 (+ (- outlinelen smush-amount) k))
            lch    (aget wb column)
            rch    (if (< k char-len) (.charAt char-row k) (char 0))]
        ;; Inline smushem — matches figlet.c smushem() exactly:
        ;;   if (lch==' ') return rch;
        ;;   if (rch==' ') return lch;
        ;;   if (previouscharwidth<2 || currcharwidth<2) return '\0';
        ;;   if ((smushmode & SM_SMUSH)==0) return '\0';
        ;;   [then universal or controlled smushing]
        (aset wb column
              (char (cond
                      (= lch \space) rch
                      (= rch \space) lch
                      (or (< prev-width 2) (< char-width 2)) (char 0)
                      (not= h-layout :smushing) (char 0)
                      :else
                      (or (smushing/h-try-smush lch rch hardblank h-smush-rules)
                          (char 0)))))))
    ;; Find STRLEN (first NUL) and STRCAT the remainder
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
  is empty, leading blank columns are trimmed from the first character.
  `prev-width` is the original width of the previous FIGcharacter (0 for
  the first character); the C figlet disables smushing when either adjacent
  character is narrower than 2 columns."
  [buffer fig-char prev-width {:keys [hardblank height h-layout h-smush-rules]}]
  (let [buffer        (or buffer (vec (repeat height "")))
        buf-width     (count (first buffer))
        char-width    (figchar-width fig-char)
        smush-amount  (compute-smush-amount buffer fig-char prev-width
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
