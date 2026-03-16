(ns clj-figlet.smushing
  "Horizontal smushing rules for FIGlet rendering.

  The FIGfont spec defines six controlled smushing rules, each governing
  how a specific class of sub-character pair collapses when two
  FIGcharacters overlap by one column.  A font's layout parameter
  selects which subset of these rules is active — so the rules must be
  individually addressable.

  Each rule is implemented as a pure function of (left, right, hardblank)
  returning the smushed character or nil, and the six functions are
  collected in a dispatch map keyed by rule number.  This mirrors the
  spec's enumerated structure directly: rule 1 has code value 1, rule 2
  has code value 2, and so on.  The public entry point `h-try-smush`
  walks only the rules active for the current font and returns the first
  match, or falls back to universal smushing when no controlled rules
  are configured.

  See figfont.txt §Smushing Rules.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Six Horizontal Smushing Rules            [figfont.txt §Smushing Rules]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- h-smush-rule-1
  "EQUAL CHARACTER SMUSHING (code value 1): two identical sub-characters
  smush into a single sub-character.  Does not smush hardblanks."
  [left right hardblank]
  (when (and (= left right) (not= left hardblank))
    left))

(defn- h-smush-rule-2
  "UNDERSCORE SMUSHING (code value 2): _ is replaced by any of:
  |, /, \\, [, ], {, }, (, ), < or >."
  [left right _hardblank]
  (let [replacers #{\| \/ \\ \[ \] \{ \} \( \) \< \>}]
    (cond
      (and (= left \_) (replacers right)) right
      (and (= right \_) (replacers left)) left
      :else nil)))

(def ^:private hierarchy-classes
  "Maps sub-characters to their hierarchy class for rule 3.
  See figfont.txt §Smushing Rules, Rule 3."
  {\| 0, \/ 1, \\ 1, \[ 2, \] 2, \{ 3, \} 3, \( 4, \) 4, \< 5, \> 5})

(defn- h-smush-rule-3
  "HIERARCHY SMUSHING (code value 4): six classes — |, /\\, [], {}, (), <>.
  When two sub-characters are from different classes, the one from the
  later (higher-numbered) class is used."
  [left right _hardblank]
  (let [lc (hierarchy-classes left)
        rc (hierarchy-classes right)]
    (when (and lc rc (not= lc rc))
      (if (> rc lc) right left))))

(defn- h-smush-rule-4
  "OPPOSITE PAIR SMUSHING (code value 8): opposing brackets, braces, and
  parentheses smush into a vertical bar |."
  [left right _hardblank]
  (let [pairs #{[\[ \]] [\] \[] [\{ \}] [\} \{] [\( \)] [\) \(]}]
    (when (pairs [left right])
      \|)))

(defn- h-smush-rule-5
  "BIG X SMUSHING (code value 16): /\\ becomes |, \\/ becomes Y, >< becomes X."
  [left right _hardblank]
  (cond
    (and (= left \/) (= right \\)) \|
    (and (= left \\) (= right \/)) \Y
    (and (= left \>) (= right \<)) \X
    :else nil))

(defn- h-smush-rule-6
  "HARDBLANK SMUSHING (code value 32): two hardblanks smush into one."
  [left right hardblank]
  (when (and (= left hardblank) (= right hardblank))
    hardblank))

(def ^:private h-smush-fns
  "Dispatch table mapping rule numbers (1-6) to their implementation functions."
  {1 h-smush-rule-1
   2 h-smush-rule-2
   3 h-smush-rule-3
   4 h-smush-rule-4
   5 h-smush-rule-5
   6 h-smush-rule-6})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn h-try-smush
  "Attempts to smush two sub-characters using the given smushing rules.
  Returns the resulting character, or nil if no rule applies.

  When `rules` is empty, universal smushing is used: the later character
  overrides the earlier, except blanks and hardblanks are always overridden
  by visible characters.  See figfont.txt §Hardblanks."
  [left right hardblank rules]
  (if (empty? rules)
    ;; Universal smushing
    (cond
      (= left \space) right
      (= right \space) left
      (= left hardblank) right
      (= right hardblank) left
      :else right)
    ;; Controlled smushing: try rules in order
    (reduce (fn [_ rule-num]
              (when-let [result ((h-smush-fns rule-num) left right hardblank)]
                (reduced result)))
            nil
            (sort rules))))
