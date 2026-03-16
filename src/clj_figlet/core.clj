(ns clj-figlet.core
  "Native Clojure FIGlet implementation.
  Parses FIGfont files and renders text as ASCII art FIGures.
  See figfont.txt §CREATING FIGFONTS for the full specification.

  This is the top-level public API namespace.  Require this namespace to
  access all user-facing functions:

    (require '[clj-figlet.core :as fig])

    (def font (fig/load-font \"fonts/standard.flf\"))
    (print (fig/render font \"Hello\"))
    (print (fig/render-str \"small\" \"World\"))"
  (:require [clj-figlet.font :as font]
            [clj-figlet.render :as render]))

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
  (font/load-font source))

(defn render
  "Renders input text as a FIGure using a previously loaded font map.
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
  [font text]
  (render/render font text))

(defn render-str
  "Convenience: loads a font by name from resources/fonts/ and renders text
  in a single call.  Equivalent to:

    (render (load-font (str \"fonts/\" font-name \".flf\")) text)

  Font name should not include the .flf extension."
  [font-name text]
  (render/render-str font-name text))
