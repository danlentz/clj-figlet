# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of
[keepachangelog.com](https://keepachangelog.com/).

## 0.1.4 — (unreleased)

### Fixed

- Row merge when smush amount exceeds buffer width.  Characters whose
  visible content concentrates on a single row (e.g. `_`) could lose that
  content when followed by a character with mostly-blank rows, because the
  deep overlap caused the merge to discard the buffer instead of merging it.
  Affects universal-smushing fonts (mini, shadow, smshadow) with specific
  character pairs like `_/` and `_\`.

### Added

- 7 new bundled fonts: doom, script, smscript, smslant, smshadow, graffiti,
  starwars (16 total)
- Smushing stress tests targeting all six rule types, width extremes, bracket
  nesting, and dense punctuation
- Generative tests using randomized substrings of printable ASCII, with seed
  reported on failure for reproducibility
- Font catalog documentation (`doc/fonts.md`)
- User's guide (`doc/users-guide.md`)
- `render` now accepts a font name string, a resource path, a filesystem
  path, a File, or a font map as its first argument

### Removed

- `render-str` (superseded by `render` accepting font name strings directly)
- `ivrit` font (right-to-left not yet supported)

## 0.1.0 — 2026-03-16

Initial release.

### Font Parsing

- Full FIGfont Version 2 header parsing: signature, hardblank, height,
  baseline, max-length, Old\_Layout, comment lines, print direction,
  Full\_Layout, and codetag count
- Layout parameter interpretation for both `Old_Layout` and `Full_Layout`,
  including all horizontal and vertical mode/rule combinations
- Required character set: ASCII 32–126 plus 7 Deutsch characters (196, 214,
  220, 228, 246, 252, 223)
- Code-tagged characters with decimal, octal, and hexadecimal code formats
- Endmark stripping per the FIGfont spec

### Rendering

- All three horizontal layout modes: full width, fitting (kerning), smushing
- All six horizontal smushing rules:
  1. Equal character smushing
  2. Underscore smushing
  3. Hierarchy smushing
  4. Opposite pair smushing
  5. Big X smushing
  6. Hardblank smushing
- Universal smushing when no controlled rules are specified
- Hardblank handling: treated as visible during horizontal layout, rendered as
  spaces in final output
- Missing-character fallback to code 0

### Bundled Fonts

- 10 fonts included in `resources/fonts/`: standard, small, big, slant,
  banner, block, shadow, lean, mini, ivrit

### Not Yet Implemented

- Vertical fitting and smushing
- Right-to-left print direction
- Control file (`.flc`) processing
- Word wrapping
