# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of
[keepachangelog.com](https://keepachangelog.com/).

## 0.1.4 — (unreleased)

### Fixed — FIGlet C compatibility

The rendering engine was rewritten to exactly match the C figlet's
`addchar`, `smushamt`, and `smushem` functions from figlet.c.  Specific
fixes:

- **Row merge rewritten as direct C translation.**  The per-row merge now
  uses a mutable char array matching the C code's in-place writes +
  STRCAT semantics, including NUL-byte truncation for characters whose
  rows are shorter than the smush amount.  This replaces the previous
  three-region string merge which diverged from C behavior for edge cases.

- **`old_layout & 31` masking.**  When `Full_Layout` is absent, the C
  figlet masks `Old_Layout` with 31 (bits 0-4, rules 1-5), excluding
  bit 5 (rule 6, hardblank smushing).  Fonts like Colossal with
  `Old_Layout=32` now correctly use universal smushing.
  See figlet.c `readfont()`.

- **`previouscharwidth < 2` guard.**  The C figlet disables smushing
  (but not fitting) when either the previous or current character is
  narrower than 2 columns.  See figlet.c `smushem()`.

- **`maxsmush = currcharwidth` cap.**  The smush amount is now capped
  at the current character's width, matching figlet.c `smushamt()`.

- **`linebd > 0` scan termination.**  The C figlet's backward scan for
  the last non-space character stops at position 0 (using `linebd > 0`,
  not `>= 0`).  This affects the overlap calculation for all-space rows.

- **Blank-row `amt++` is unconditional.**  The C figlet increments the
  overlap for blank buffer rows regardless of smushing mode or character
  widths.  See figlet.c `smushamt()`: `if (!ch1||ch1==' ') amt++;`.

### Fixed — Malformed bundled fonts

Three contributed fonts had FIGcharacters with inconsistent row widths,
violating figfont.txt §BASIC DATA STRUCTURE ("there must be a consistent
width for each line once the endmarks are removed"):

- **Bear.flf** — `#` (code 35): row 0 was 0 chars, others 1 char.
  Padded row 0 to 1 space.
- **Bear.flf** — `>` (code 62): row 0 was 3 chars, others 1 char.
  Padded rows 1-8 to 3 spaces.
- **dancingfont.flf** — `#` (code 35): row 0 was 0 chars, others
  1 char.  Padded row 0 to 1 space.
- **flowerpower.flf** — `#` (code 35): row 0 was 0 chars, others
  1 char.  Padded row 0 to 1 space.

### Added

- 30 new bundled fonts (46 total), including doom, script, graffiti,
  starwars, and many contributed fonts
- `all-fonts` function returning bundled font names
- `render` now accepts a font name string, a resource path, a filesystem
  path, a File, or a font map as its first argument
- Smushing stress tests targeting all six rule types, width extremes,
  bracket nesting, and dense punctuation
- Generative tests using randomized printable ASCII across all fonts
- Regression test suite for specific problem cases found during development
- Font catalog documentation (`doc/fonts.md`)
- User's guide (`doc/users-guide.md`)

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
