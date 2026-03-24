# User's Guide

This guide walks through the clj-figlet API from first use to more
advanced patterns.  All examples assume:

```clojure
(require '[clj-figlet.core :as fig])
```


## Rendering Text

The simplest way to render text is `render`, which accepts a font name
and a string:

```clojure
(print (fig/render "standard" "Hello!"))
```

```
 _   _      _ _       _
| | | | ___| | | ___ | |
| |_| |/ _ \ | |/ _ \| |
|  _  |  __/ | | (_) |_|
|_| |_|\___|_|_|\___/(_)
```

The font name refers to a `.flf` file on the classpath under
`resources/fonts/`.  16 fonts are bundled with the library (see
[Bundled Fonts](#bundled-fonts) below).

When passed a name string, `render` loads and parses the font file on
every call, which is fine for one-off use but wasteful if you're
rendering many strings with the same font.


## Loading Fonts

For repeated rendering, load the font once with `load-font` and pass it
to `render`:

```clojure
(def font (fig/load-font "fonts/standard.flf"))

(print (fig/render font "Hi"))
```

```
 _   _ _
| | | (_)
| |_| | |
|  _  | |
|_| |_|_|
```

`load-font` accepts several source types:

| Source | Example |
|--------|---------|
| Classpath resource | `"fonts/standard.flf"` |
| Filesystem path | `"/usr/share/figlet/fonts/doom.flf"` |
| `java.io.File` | `(java.io.File. "my-font.flf")` |
| `java.io.Reader` | Any open reader |

When given a string, classpath resources are tried first, then the
filesystem.  This means the bundled fonts are always available by their
short names (`"fonts/standard.flf"`), and you can also load any `.flf`
file from disk.


## Fonts as Data

A loaded font is a plain Clojure map.  There are no custom types or
protocols — just keywords and values:

```clojure
(:height font)        ;=> 6
(:baseline font)      ;=> 5
(:hardblank font)     ;=> \$
(:h-layout font)      ;=> :smushing
(:h-smush-rules font) ;=> #{1 2 3 4}
(count (:chars font)) ;=> 324
```

The full set of keys:

| Key | Description |
|-----|-------------|
| `:height` | Number of rows in every FIGcharacter |
| `:baseline` | Rows from top to baseline (capital letter height) |
| `:hardblank` | The sub-character used as a hardblank in this font |
| `:h-layout` | Horizontal layout mode: `:full`, `:fitting`, or `:smushing` |
| `:h-smush-rules` | Set of active horizontal smushing rule numbers (1-6) |
| `:v-layout` | Vertical layout mode: `:full`, `:fitting`, or `:smushing` |
| `:v-smush-rules` | Set of active vertical smushing rule numbers (1-5) |
| `:chars` | Map of character code (long) to FIGcharacter data |
| `:max-length` | Maximum line width in the font file |
| `:old-layout` | Legacy layout parameter (-1 to 63) |
| `:full-layout` | Full layout parameter (0 to 32767), or nil |
| `:print-direction` | 0 = left-to-right, 1 = right-to-left |
| `:comment-lines` | Number of comment lines in the font file |
| `:codetag-count` | Number of code-tagged characters, or nil |

Each FIGcharacter in the `:chars` map is a vector of strings, one per
row.  For example, the capital A in the standard font:

```clojure
(get-in font [:chars 65])
;=> ["     _    "
;    "    / \\   "
;    "   / _ \\  "
;    "  / ___ \\ "
;    " /_/   \\_\\"
;    "          "]
```

Because fonts are just maps, you can merge them, filter their character
sets, assoc in overrides, or pass them through any data pipeline.


## Bundled Fonts

16 fonts ship in `resources/fonts/`.  See [fonts.md](fonts.md) for a
visual catalog with samples of every font.

| Font | Height | Style |
|------|--------|-------|
| `standard` | 6 | The classic FIGlet default |
| `small` | 5 | Compact version of standard |
| `big` | 8 | Tall, bold letters |
| `doom` | 8 | Clean, modern variant of big |
| `slant` | 6 | Italic / slanted |
| `smslant` | 5 | Compact italic |
| `shadow` | 5 | Letters with a drop shadow |
| `smshadow` | 4 | Compact shadow |
| `script` | 7 | Cursive / handwriting |
| `banner` | 8 | Large block letters made of `#` |
| `block` | 8 | Heavy block style |
| `lean` | 8 | Thin slanted style |
| `graffiti` | 7 | Urban graffiti lettering |
| `starwars` | 7 | Inspired by the Star Wars title crawl |
| `mini` | 4 | Smallest — just 3 lines tall |

Here are a few of them rendering the same word:

```
standard:                   small:           shadow:
 _   _                      _  _
| | | | ___ _   _          | || |___ _  _    |   |
| |_| |/ _ \ | | |         | __ / -_) || |   |   |  _ \ |   |
|  _  |  __/ |_| |         |_||_\___|\_, |   ___ |  __/ |   |
|_| |_|\___|\__, |                    |__/  _|  _|\___|\___, |
            |___/                                      ____/
```


## How Rendering Works

When you call `render`, each character of the input string is looked up
in the font's `:chars` map by its character code.  If a character isn't
found, the font's "missing character" (code 0) is used as a fallback.

FIGcharacters are assembled left-to-right.  How tightly they pack
together depends on the font's horizontal layout mode:

- **`:full`** — Full width.  Each character occupies its full designed
  width with no overlap.
- **`:fitting`** — Kerning.  Characters slide together until they touch,
  but visible sub-characters never overlap.
- **`:smushing`** — Characters slide one column past touching, and the
  overlapping sub-characters are merged using the font's smushing rules.

Most fonts default to smushing, which produces the tightest, most
natural-looking output.

After assembly, hardblank sub-characters (which act as invisible
spacers during layout) are replaced with spaces, and trailing whitespace
is trimmed from each line.  The result is returned as a single string
terminated by a newline.


## Smushing Rules

When the layout mode is `:smushing`, the font specifies which of six
rules govern how overlapping sub-characters merge.  The rules are tried
in order; the first match wins.

| Rule | Name | What it does |
|------|------|-------------|
| 1 | Equal character | Two identical characters become one |
| 2 | Underscore | `_` is replaced by `\|`, `/`, `\`, brackets, etc. |
| 3 | Hierarchy | Six classes (`\|`, `/\`, `[]`, `{}`, `()`, `<>`) — higher class wins |
| 4 | Opposite pair | `[]`, `}{`, `)(` etc. become `\|` |
| 5 | Big X | `/\` becomes `\|`, `\/` becomes `Y`, `><` becomes `X` |
| 6 | Hardblank | Two hardblanks merge into one |

If a font enables smushing but specifies no rules, **universal
smushing** is used instead: the later character simply overrides the
earlier one (except that visible characters always override blanks and
hardblanks).

You can see which rules a font uses:

```clojure
(:h-smush-rules (fig/load-font "fonts/standard.flf"))
;=> #{1 2 3 4}
```


## Practical Patterns

### Application startup banner

```clojure
(defn splash []
  (println (fig/render "small" "my-app"))
  (println "  v1.0.0 — starting up..."))
```

```
 _ __ _  _ ___ __ _ _ __ _ __
| '  \ || |___/ _` | '_ \ '_ \
|_|_|_\_, |   \__,_| .__/ .__/
      |__/         |_|  |_|

  v1.0.0 — starting up...
```


### Rendering a sequence of values

Since `render` is a pure function, it works naturally with `map` and
friends:

```clojure
(let [font (fig/load-font "fonts/small.flf")]
  (doseq [word ["alpha" "beta" "gamma"]]
    (print (fig/render font (clojure.string/upper-case word)))))
```

```
   _   _    ___ _  _   _
  /_\ | |  | _ \ || | /_\
 / _ \| |__|  _/ __ |/ _ \
/_/ \_\____|_| |_||_/_/ \_\

 ___ ___ _____ _
| _ ) __|_   _/_\
| _ \ _|  | |/ _ \
|___/___| |_/_/ \_\

  ___   _   __  __ __  __   _
 / __| /_\ |  \/  |  \/  | /_\
| (_ |/ _ \| |\/| | |\/| |/ _ \
 \___/_/ \_\_|  |_|_|  |_/_/ \_\
```


### Using external font files

Any FIGfont file from the [FIGlet font library](http://www.figlet.org/)
will work:

```clojure
(print (fig/render "/usr/share/figlet/fonts/roman.flf" "Hey"))
```


## Reference

The full API:

| Function | Signature | Description |
|----------|-----------|-------------|
| `load-font` | `[source]` | Load a FIGfont file, return a font map |
| `render` | `[font-or-name text]` | Render text; accepts a font map or a font name string |
| `all-fonts` | `[]` | Returns sorted vector of bundled font names |
| `validate-font` | `[font]` | Validate a font map against the spec; returns nil or explain-data |
| `valid-font?` | `[font-or-source]` | Returns true if the font has no spec violations |

For complete docstrings including all returned map keys, see the source
or run `(doc fig/load-font)` at the REPL.

The implementation follows the [FIGfont Version 2
Standard](http://www.figlet.org/) (Cowan & Burton, 1996-97).  A copy
of the spec is included in the repository at `doc/papers/figfont.txt`.
