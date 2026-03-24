# clj-figlet

[![Clojure CI](https://github.com/danlentz/clj-figlet/actions/workflows/clojure.yml/badge.svg?branch=master)](https://github.com/danlentz/clj-figlet/actions/workflows/clojure.yml)
[![Clojars](https://img.shields.io/clojars/v/com.github.danlentz/clj-figlet.svg)](https://clojars.org/com.github.danlentz/clj-figlet)

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
from font + string to string, and the whole thing composes naturally
with the rest of the language.  No native dependencies, no subprocess, no state.

## Quick Start

Add the dependency:

```clojure
;; project.clj
[com.github.danlentz/clj-figlet "0.1.4"]

;; deps.edn
com.github.danlentz/clj-figlet {:mvn/version "0.1.4"}
```

Then in your code:

```clojure
(require '[clj-figlet.core :as fig])

(print (fig/render "standard" "Hello!"))
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

```clojure
(print (fig/render "slant" "Clojure"))
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

For repeated rendering, load the font once and pass the font map to
`render`:

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

45 fonts ship in `resources/fonts/`.  Here are a few highlights:

**standard** — the classic
```
     _                  _               _
 ___| |_ __ _ _ __   __| | __ _ _ __ __| |
/ __| __/ _` | '_ \ / _` |/ _` | '__/ _` |
\__ \ || (_| | | | | (_| | (_| | | | (_| |
|___/\__\__,_|_| |_|\__,_|\__,_|_|  \__,_|
```

**small** — compact
```
 _  _     _ _
| || |___| | |___
| __ / -_) | / _ \_
|_||_\___|_|_\___(_)
```

**doom** — clean and modern
```
______
|  _  \
| | | |___   ___  _ __ ___
| | | / _ \ / _ \| '_ ` _ \
| |/ / (_) | (_) | | | | | |
|___/ \___/ \___/|_| |_| |_|
```

**slant** — italic
```
         __            __
   _____/ /___ _____  / /_
  / ___/ / __ `/ __ \/ __/
 (__  ) / /_/ / / / / /_
/____/_/\__,_/_/ /_/\__/
```

**script** — cursive
```
               o
 ,   __   ,_        _ _|_
/ \_/    /  |  |  |/ \_|
 \/ \___/   |_/|_/|__/ |_/
                 /|
                 \|
```

**graffiti** — urban
```
                      _____  _____.__  __  .__
   ________________ _/ ____\/ ____\__|/  |_|__|
  / ___\_  __ \__  \\   __\\   __\|  \   __\  |
 / /_/  >  | \// __ \|  |   |  |  |  ||  | |  |
 \___  /|__|  (____  /__|   |__|  |__||__| |__|
/_____/            \/
```

**banner** — retro block
```
 #####  ####### ####### ######
#     #    #    #     # #     #
#          #    #     # #     #
 #####     #    #     # ######
      #    #    #     # #
#     #    #    #     # #
 #####     #    ####### #
```

**Roman** — classic serif
```
ooooooooo.
`888   `Y88.
 888   .d88'  .ooooo.  ooo. .oo.  .oo.    .oooo.   ooo. .oo.
 888ooo88P'  d88' `88b `888P"Y88bP"Y88b  `P  )88b  `888P"Y88b
 888`88b.    888   888  888   888   888   .oP"888   888   888
 888  `88b.  888   888  888   888   888  d8(  888   888   888
o888o  o888o `Y8bod8P' o888o o888o o888o `Y888""8o o888o o888o
```

**Colossal** — tall digits
```
888888b.  d8b        888
888  "88b Y8P        888
888  .88P            888
8888888K. 888 .d88b. 888
888  "Y88b888d88P"88b888
888    888888888  888Y8P
888   d88P888Y88b 888 "
8888888P" 888 "Y88888888
                  888
             Y8b d88P
              "Y88P"
```

**ghost** — ethereal
```
             ('-. .-.               .-')    .-') _
            ( OO )  /              ( OO ). (  OO) )
  ,----.    ,--. ,--. .-'),-----. (_)---\_)/     '._
 '  .-./-') |  | |  |( OO'  .-.  '/    _ | |'--...__)
 |  |_( O- )|   .|  |/   |  | |  |\  :` `. '--.  .--'
 |  | .--, \|       |\_) |  |\|  | '..`''.)   |  |
(|  | '. (_/|  .-.  |  \ |  | |  |.-._)   \   |  |
 |  '--'  | |  | |  |   `'  '-'  '\       /   |  |
  `------'  `--' `--'     `-----'  `-----'    `--'
```

**dancingfont** — decorative
```
  ____       _      _   _      ____ U _____ u
 |  _"\  U  /"\  u | \ |"|  U /"___|\| ___"|/
/| | | |  \/ _ \/ <|  \| |> \| | u   |  _|"
U| |_| |\ / ___ \ U| |\  |u  | |/__  | |___
 |____/ u/_/   \_\ |_| \_|    \____| |_____|
  |||_    \\    >> ||   \\,-._// \\  <<   >>
 (__)_)  (__)  (__)(_")  (_/(__)(__)(__) (__)
```

**Doh** — enormous
```
DDDDDDDDDDDDD                        hhhhhhh
D::::::::::::DDD                     h:::::h
D:::::::::::::::DD                   h:::::h
DDD:::::DDDDD:::::D                  h:::::h
  D:::::D    D:::::D    ooooooooooo   h::::h hhhhh
  D:::::D     D:::::D oo:::::::::::oo h::::hh:::::hhh
  D:::::D     D:::::Do:::::::::::::::oh::::::::::::::hh
  D:::::D     D:::::Do:::::ooooo:::::oh:::::::hhh::::::h
  D:::::D     D:::::Do::::o     o::::oh::::::h   h::::::h
  D:::::D     D:::::Do::::o     o::::oh:::::h     h:::::h
  D:::::D     D:::::Do::::o     o::::oh:::::h     h:::::h
  D:::::D    D:::::D o::::o     o::::oh:::::h     h:::::h
DDD:::::DDDDD:::::D  o:::::ooooo:::::oh:::::h     h:::::h
D:::::::::::::::DD   o:::::::::::::::oh:::::h     h:::::h
D::::::::::::DDD      oo:::::::::::oo h:::::h     h:::::h
DDDDDDDDDDDDD           ooooooooooo   hhhhhhh     hhhhhhh
```

See [doc/fonts.md](doc/fonts.md) for the complete visual catalog of all
45 fonts.

Any `.flf` FIGfont file can be loaded — the library implements the full
FIGfont Version 2 specification.

The bundled fonts are from the [FIGlet](http://www.figlet.org/) distribution
and are redistributed under the New BSD License.  See
`resources/fonts/NOTICE` for the full license text.

## Examples

### CLI splash screen

```clojure
(defn splash []
  (println (fig/render "small" "my-app"))
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

Same text, different personality.  The font is just data — easy to let
users choose:

```clojure
(doseq [f ["standard" "doom" "slant" "shadow" "small"]]
  (print (fig/render f "Hello")))
```

```
 _   _      _ _
| | | | ___| | | ___
| |_| |/ _ \ | |/ _ \
|  _  |  __/ | | (_) |
|_| |_|\___|_|_|\___/

 _   _      _ _
| | | |    | | |
| |_| | ___| | | ___
|  _  |/ _ \ | |/ _ \
| | | |  __/ | | (_) |
\_| |_/\___|_|_|\___/

    __  __     ____
   / / / /__  / / /___
  / /_/ / _ \/ / / __ \
 / __  /  __/ / / /_/ /
/_/ /_/\___/_/_/\____/

 |   |      | |
 |   |  _ \ | |  _ \
 ___ |  __/ | | (   |
_|  _|\___|_|_|\___/

 _  _     _ _
| || |___| | |___
| __ / -_) | / _ \
|_||_\___|_|_\___/
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
lein test           # Run all tests
lein repl           # Start a REPL in clj-figlet.core
```

The test suite compares rendered output against the reference C `figlet` binary
(install via `brew install figlet`) across all 45 bundled fonts using common
strings, smushing stress cases, regression cases, and randomized generative
tests (50 random strings per font per category, 6000+ assertions per run).

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
