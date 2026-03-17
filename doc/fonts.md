# Bundled Font Catalog

clj-figlet ships with 16 FIGfont files in `resources/fonts/`.  Any of
them can be used by name with `render`:

```clojure
(fig/render "doom" "Hello")
```

Or loaded once with `load-font` for repeated rendering:

```clojure
(def font (fig/load-font "fonts/doom.flf"))
(fig/render font "Hello")
```

All bundled fonts are from the [FIGlet](http://www.figlet.org/)
distribution and are redistributed under the New BSD License.  See
`resources/fonts/NOTICE` for the full license text.  Each font file also
carries its own attribution in its header comments.


## Standard Family

The core fonts by Glenn Chappell and Ian Chai from the original FIGlet
distribution.  These are the workhorses.

### standard

The classic FIGlet default.  Glenn Chappell & Ian Chai, 1993.

```
 _   _      _ _
| | | | ___| | | ___
| |_| |/ _ \ | |/ _ \
|  _  |  __/ | | (_) |
|_| |_|\___|_|_|\___/
```

### small

Compact version of standard.  Same aesthetic, less vertical space.

```
 _  _     _ _
| || |___| | |___
| __ / -_) | / _ \
|_||_\___|_|_\___/
```

### big

Tall, bold letters.  Glenn Chappell, 1993.

```
 _    _      _ _
| |  | |    | | |
| |__| | ___| | | ___
|  __  |/ _ \ | |/ _ \
| |  | |  __/ | | (_) |
|_|  |_|\___|_|_|\___/
```

### doom

Clean, modern variant of big.  One of the most popular FIGlet fonts on
the internet.  Frans P. de Vries, 1996.

```
 _   _      _ _
| | | |    | | |
| |_| | ___| | | ___
|  _  |/ _ \ | |/ _ \
| | | |  __/ | | (_) |
\_| |_/\___|_|_|\___/
```


## Slanted Family

Italic-style fonts with a rightward lean.

### slant

Glenn Chappell, 1993.

```
    __  __     ____
   / / / /__  / / /___
  / /_/ / _ \/ / / __ \
 / __  /  __/ / / /_/ /
/_/ /_/\___/_/_/\____/
```

### smslant

Compact version of slant.

```
   __ __    ____
  / // /__ / / /__
 / _  / -_) / / _ \
/_//_/\__/_/_/\___/
```


## Shadow Family

Letters with a drop shadow effect.

### shadow

Glenn Chappell, 1993.

```
 |   |      | |
 |   |  _ \ | |  _ \
 ___ |  __/ | | (   |
_|  _|\___|_|_|\___/
```

### smshadow

Compact version of shadow.

```
 |  |      | |
 __ |  -_) | |  _ \
_| _|\___|_|_|\___/
```


## Script Family

Cursive / handwriting style — a completely different aesthetic from the
standard family.

### script

Glenn Chappell, 1993.

```
 ,          _   _
/|   |     | | | |
 |___|  _  | | | |  __
 |   |\|/  |/  |/  /  \_
 |   |/|__/|__/|__/\__/
```

### smscript

Compact version of script.

```
 ,
/|  |  _ |\ |\  _
 |--| |/ |/ |/ / \_
 |  |)|_/|_/|_/\_/
```


## Display Fonts

Larger or more decorative fonts for headings, banners, and splash
screens.

### banner

Large block letters made of `#`.  Ryan Youck, 1994.

```
#     #
#     # ###### #      #       ####
#     # #      #      #      #    #
####### #####  #      #      #    #
#     # #      #      #      #    #
#     # #      #      #      #    #
#     # ###### ###### ######  ####
```

### block

Heavy block style using underscores.  Glenn Chappell, 1993.

```
_|    _|            _|  _|
_|    _|    _|_|    _|  _|    _|_|
_|_|_|_|  _|_|_|_|  _|  _|  _|    _|
_|    _|  _|        _|  _|  _|    _|
_|    _|    _|_|_|  _|  _|    _|_|
```

### lean

Thin slanted style using underscores and slashes.  Glenn Chappell, 1993.

```
    _/    _/            _/  _/
   _/    _/    _/_/    _/  _/    _/_/
  _/_/_/_/  _/_/_/_/  _/  _/  _/    _/
 _/    _/  _/        _/  _/  _/    _/
_/    _/    _/_/_/  _/  _/    _/_/
```

### graffiti

Urban graffiti lettering.  Leigh Purdie, 1994.

```
  ___ ___         .__  .__
 /   |   \   ____ |  | |  |   ____
/    ~    \_/ __ \|  | |  |  /  _ \
\    Y    /\  ___/|  |_|  |_(  <_> )
 \___|_  /  \___  >____/____/\____/
       \/       \/
```

### starwars

Inspired by the Star Wars title crawl.  Ryan Youck, 1994.

```
 __    __   _______  __       __        ______
|  |  |  | |   ____||  |     |  |      /  __  \
|  |__|  | |  |__   |  |     |  |     |  |  |  |
|   __   | |   __|  |  |     |  |     |  |  |  |
|  |  |  | |  |____ |  `----.|  `----.|  `--'  |
|__|  |__| |_______||_______||_______| \______/
```


## Compact

### mini

The smallest font — just 3 lines tall.  Glenn Chappell, 1993.

```
|_| _ || _
| |(/_||(_)
```


## Quick Reference

| Font | Height | Style | Author |
|------|--------|-------|--------|
| `standard` | 6 | Classic default | Glenn Chappell & Ian Chai |
| `small` | 5 | Compact standard | Glenn Chappell |
| `big` | 8 | Tall, bold | Glenn Chappell |
| `doom` | 8 | Clean, modern | Frans P. de Vries |
| `slant` | 6 | Italic | Glenn Chappell |
| `smslant` | 5 | Compact italic | Glenn Chappell |
| `shadow` | 5 | Drop shadow | Glenn Chappell |
| `smshadow` | 4 | Compact shadow | Glenn Chappell |
| `script` | 7 | Cursive | Glenn Chappell |
| `smscript` | 5 | Compact cursive | Glenn Chappell |
| `banner` | 8 | Block `#` letters | Ryan Youck |
| `block` | 8 | Heavy underscores | Glenn Chappell |
| `lean` | 8 | Thin slashes | Glenn Chappell |
| `graffiti` | 7 | Urban lettering | Leigh Purdie |
| `starwars` | 7 | Title crawl | Ryan Youck |
| `mini` | 4 | Smallest | Glenn Chappell |
