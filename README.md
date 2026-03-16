# clj-figlet

```
      _  _        __ _       _      _
  ___| |(_)      / _(_) __ _| | ___| |_
 / __| || |_____| |_| |/ _` | |/ _ \ __|
| (__| || |_____|  _| | (_| | |  __/ |_
 \___|_|/ |     |_| |_|\__, |_|\___|\__|
      |__/             |___/
```

A native Clojure re-implementation of [FIGlet](http://www.figlet.org/) — the
classic ASCII art text renderer.  No shelling out, no external dependencies
beyond Clojure itself.  Parses FIGfont files, implements all six horizontal
smushing rules, and produces output identical to the reference C `figlet`.

## Why?

FIGlet has been the standard way to render large text in terminals since 1991,
but using it from Clojure typically means shelling out to a C binary — which
adds a system dependency, a subprocess per call, and makes the output opaque
to your program.

clj-figlet is a from-scratch implementation against the
[FIGfont Version 2 Standard](http://www.figlet.org/) (Cowan & Burton,
1996-97).  Fonts load as plain Clojure maps, rendering is a pure function
from font + string to string, and the whole thing composes naturally with
`map`, `reduce`, threading macros, and the rest of the language.  No native
dependencies, no subprocess, no state.

## Quick Start

Add the dependency to your `project.clj`:

```clojure
[clj-figlet "0.1.0-SNAPSHOT"]
```

Then in your code:

```clojure
(require '[clj-figlet.core :as fig])

(print (fig/render-str "standard" "Hello!"))
```

```
 _   _      _ _       _
| | | | ___| | | ___ | |
| |_| |/ _ \ | |/ _ \| |
|  _  |  __/ | | (_) |_|
|_| |_|\___|_|_|\___/(_)
```

## Usage

### One-shot rendering

`render-str` loads a font from `resources/fonts/` by name and renders a string
in a single call:

```clojure
(print (fig/render-str "slant" "Clojure"))
```

```
   ________        _
  / ____/ /___    (_)_  __________
 / /   / / __ \  / / / / / ___/ _ \
/ /___/ / /_/ / / / /_/ / /  /  __/
\____/_/\____/_/ /\__,_/_/   \___/
            /___/
```

### Load once, render many

For repeated rendering with the same font, load it once and pass the font map
to `render`:

```clojure
(def font (fig/load-font "fonts/small.flf"))

(print (fig/render font "OK"))
(print (fig/render font "WARN"))
(print (fig/render font "ERR"))
```

`load-font` accepts a classpath resource path, a filesystem path, a `File`, or
a `Reader`.

### Font metadata

A loaded font is a plain Clojure map.  You can inspect its properties directly:

```clojure
(:height font)        ;=> 5
(:baseline font)      ;=> 4
(:hardblank font)     ;=> \$
(:h-layout font)      ;=> :smushing
(:h-smush-rules font) ;=> #{1 2 3 4}
(count (:chars font))  ;=> 229
```

## Bundled Fonts

Ten fonts ship in `resources/fonts/`:

| Font | Style |
|------|-------|
| `standard` | The classic FIGlet default |
| `small` | Compact version of standard |
| `big` | Tall, bold letters |
| `slant` | Italic / slanted |
| `banner` | Large block letters made of `#` |
| `block` | Heavy block style |
| `shadow` | Letters with a drop shadow |
| `lean` | Thin slanted style |
| `mini` | Smallest — just 3 lines tall |
| `ivrit` | Right-to-left Hebrew style |

Any `.flf` FIGfont file can be loaded — the library implements the full
FIGfont Version 2 specification.

The bundled fonts are from the [FIGlet](http://www.figlet.org/) distribution
and are redistributed under the New BSD License.  See
`resources/fonts/NOTICE` for the full license text.

## Examples

### CLI splash screen

```clojure
(defn splash []
  (println (fig/render-str "small" "my-app"))
  (println "  v1.0.0 — starting up..."))

(splash)
```

```
 _ __ _  _ ___ __ _ _ __ _ __
| '  \ || |___/ _` | '_ \ '_ \
|_|_|_\_, |   \__,_| .__/ .__/
      |__/         |_|  |_|

  v1.0.0 — starting up...
```

### Font comparison

Same text, different personality.  The font is just data — easy to let users
choose:

```clojure
(doseq [f ["standard" "small" "shadow"]]
  (print (fig/render-str f "Hello")))
```

```
 _   _      _ _
| | | | ___| | | ___
| |_| |/ _ \ | |/ _ \
|  _  |  __/ | | (_) |
|_| |_|\___|_|_|\___/

 _  _     _ _
| || |___| | |___
| __ / -_) | / _ \
|_||_\___|_|_\___/

 |   |      | |
 |   |  _ \ | |  _ \
 ___ |  __/ | | (   |
_|  _|\___|_|_|\___/
```

### Countdown

Fonts load once; rendering is just a function call:

```clojure
(let [font (fig/load-font "fonts/standard.flf")]
  (doseq [s ["3" "2" "1" "Go!"]]
    (print (fig/render font s))))
```

```
 _____
|___ /
  |_ \
 ___) |
|____/

 ____
|___ \
  __) |
 / __/
|_____|

 _
/ |
| |
| |
|_|

  ____       _
 / ___| ___ | |
| |  _ / _ \| |
| |_| | (_) |_|
 \____|\___/(_)
```

## FIGfont Spec Compliance

The implementation follows the [FIGfont Version 2
Standard](http://www.figlet.org/) (Cowan & Burton, 1996-97).  A copy of the
spec lives in `doc/papers/figfont.txt`.

### What's implemented

- Full header parsing (signature, hardblank, height, baseline, max-length,
  Old_Layout, comment lines, print direction, Full_Layout, codetag count)
- Layout parameter interpretation for both `Old_Layout` and `Full_Layout`
- All three horizontal layout modes: full width, fitting (kerning), smushing
- All six horizontal smushing rules:
  1. Equal character smushing
  2. Underscore smushing
  3. Hierarchy smushing
  4. Opposite pair smushing
  5. Big X smushing
  6. Hardblank smushing
- Universal smushing (when no controlled rules are specified)
- Hardblank handling (visible for horizontal fitting/smushing, rendered as
  spaces in output)
- Required character set (ASCII 32–126 plus 7 Deutsch characters)
- Code-tagged characters (decimal, octal, and hexadecimal code formats)

### Not yet implemented

- Vertical fitting and smushing
- Right-to-left print direction
- Control file (`.flc`) processing
- Word wrapping

## Development

```bash
lein test           # Run all tests (correctness + showcase)
lein repl           # Start a REPL in clj-figlet.core
```

The test suite compares rendered output against the reference C `figlet` binary
(install via `brew install figlet`) across 9 fonts and 24 test strings.  A
separate showcase test suite demonstrates practical usage patterns.

## Acknowledgments

This library would not exist without the work of the original FIGlet authors.
Glenn Chappell and Ian Chai created FIGlet in 1991 as a 170-line C program
called "newban" and grew it into something beloved across the internet.  John
Cowan and Paul Burton later wrote the
[FIGfont Version 2 Standard](http://www.figlet.org/) — a remarkably
well-designed specification that has aged gracefully for three decades.  The
format is elegant in its simplicity: plain text all the way down, editable in
any text editor, portable across every platform, and expressive enough to
support hundreds of fonts with a handful of smushing rules.  It is a model of
what a small, clear standard can accomplish.

The bundled font files are from the FIGlet distribution (New BSD License) and
carry their own attribution in their header comments.  See
`resources/fonts/NOTICE` for details.

Thank you to Glenn, Ian, John, Paul, Christiaan Keet, and Claudio Matsuoka
for creating and maintaining FIGlet over the years, and to the many font
designers who contributed to its remarkable library.

## License

Copyright © 2026 Dan Lentz

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
