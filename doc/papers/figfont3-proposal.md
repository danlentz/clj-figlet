        _____  ___  ____   __                _   _____
       |  ___||_ _|/ ___| / _|  ___   _ __  | |_|___ /
       | |_    | || |  _ | |_  / _ \ | '_ \ | __| |_ \
       |  _|   | || |_| ||  _|| (_) || | | || |_ ___) |
       |_|    |___|\____||_|   \___/ |_| |_| \__|____/

       The FIGfont Version 3 Color Extension Proposal
       === ======= ======= = ===== ========= ========
              Proposal Draft 0.1 — March 2026
                  by Dan Lentz
              With reference to:
                  FIGfont Version 2 Standard (Cowan & Burton, 1996-97)
              May be freely copied and distributed.

  _____          __           __
 / ___/__  ___  / /____ ___  / /____
/ /__/ _ \/ _ \/ __/ -_) _ \/ __(_-<
\___/\___/_//_/\__/\__/_//_/\__/___/

    INTRODUCTION
    MOTIVATION
    DESIGN PRINCIPLES
    CHANGES FROM VERSION 2
        The Header Line
        Color Rows
        The Palette
        Color Row Rules
        Detection of Color Rows
    EFFECT ON SMUSHING
    EFFECT ON THE RENDERING PIPELINE
    BACKWARD COMPATIBILITY
    FILE NAMING
    COMPLETE EXAMPLE
    NOTES FOR IMPLEMENTORS
        Parsing Strategy
        Internal Representation
        Serialization
    OPEN QUESTIONS
    REFERENCES


INTRODUCTION
============

This document proposes a minimal extension to the FIGfont Version 2 standard
that adds per-sub-character foreground color to FIGfont files.  It is written
for font designers who wish to create colored FIGfonts, and for implementors
of FIGdrivers who wish to support them.

This proposal is designed as a companion to the FIGfont Version 2 standard
(figfont.txt, Cowan & Burton, 1996-97), which remains the authoritative
reference for all aspects of FIGfont files not explicitly modified here.
Readers should be familiar with the Version 2 standard before reading this
document.

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", and "MAY" in this
document are to be interpreted as described in RFC 2119.


MOTIVATION
==========

The FIGfont Version 2 standard has proven remarkably durable.  Its core
insight — fonts as plain text files, editable in any editor — has kept the
format alive for three decades.  Over 400 FIGfonts exist, and FIGlet has been
ported to virtually every platform.

However, modern terminals universally support ANSI color, and there is no
standard way to embed color information in a FIGfont.  Users who want colored
FIGures must apply color as a post-processing step, which limits color to
uniform application (e.g., "the whole FIGure is red") rather than per-sub-
character precision.

TOIlet (https://github.com/cacalabs/toilet) introduced a font format with a
`.tlf` extension and a `tlf2a` signature.  Despite the different extension,
`.tlf` files are structurally identical to `.flf` files — color in TOIlet is
applied by runtime "filter" functions, not carried in the font data.  This
means a TOIlet font cannot specify that, say, the top of a letter is green
while the bottom is blue.

This proposal addresses the gap by adding an optional color plane to FIGfont
files.  The color data is interleaved with the existing shape data in a way
that preserves the plain-text editability of FIGfonts.


DESIGN PRINCIPLES
====== ==========

    1) Plain text stays readable.

        A color font opened in a text editor MUST still look like
        recognizable ASCII art.  Color is metadata carried alongside the
        shape data, not embedded ANSI escape sequences.  A human reading
        the file should be able to see the FIGcharacters without any
        special tooling.

    2) Uncolored fonts are unchanged.

        A Version 3 font without any color data SHOULD be byte-identical
        to a Version 2 font, except for the header signature.  The color
        extension adds no mandatory overhead.

    3) Smushing is shape-only.

        Color MUST NOT affect layout decisions.  Smushing rules operate
        on shape sub-characters, exactly as in Version 2.  Color follows
        the sub-character that survives the smush.

    4) No new dependencies.

        The format SHOULD be implementable with the same tooling as
        Version 2: a text editor and a parser.  No binary encoding,
        no external color profile files, no escape sequences in the
        font data.


CHANGES FROM VERSION 2
======= ==== ======= =


THE HEADER LINE

The header line format is identical to Version 2, with one change: the
signature.

    Version 2:  flf2a$ 6 5 20 15 3 0 143 229
    Version 3:  flf3a$ 6 5 20 15 3 0 143 229

The first five characters change from "flf2a" to "flf3a".  The first four
characters "flf3" identify the file as a FIGfont Version 3 file.  The "a" in
the fifth position retains its meaning from Version 2 (currently ignored but
reserved).

The sixth character (Hardblank) and all numeric parameters retain their exact
meanings from Version 2.  No new header parameters are introduced.

NOTE: The Hardblank sub-character MUST NOT be any character that appears in
the color palette (see below).  If your existing FIGfont uses "$" as the
hardblank (the common convention), this is not a concern, since "$" is not a
palette character.  If your FIGfont uses a letter as the hardblank, check
that it does not conflict with the palette.

A Version 3 reader MUST also accept the "flf2a" and "tlf2a" signatures,
loading those fonts as uncolored Version 2 fonts.


COLOR ROWS

Each row of a FIGcharacter may optionally be followed by a color row on the
next line.  A color row has the same width as its corresponding shape row
and encodes a foreground color for each sub-character position using a single
palette character.

Here is an example showing a shape row followed by its color row:

    Shape row:     /_/   \_\@
    Color row:     rr.   rrr@

The color row uses the same endmark as its corresponding shape row.  After
endmark stripping, the color row MUST have the same length as the shape row.

Color rows are optional.  A FIGcharacter may have color rows for all of its
shape rows, for none of them, or for some subset.  However, in practice, a
FIGcharacter is typically either fully colored (all rows have color data) or
fully uncolored (no rows have color data).  See "DETECTION OF COLOR ROWS"
for why this is important for parsers.


THE PALETTE

The palette maps single characters to the 16 standard ANSI colors.  Lowercase
letters represent normal-intensity colors; uppercase letters represent bright
(high-intensity) colors.  The period character "." represents the terminal's
default foreground color.

    Palette Character Table:

        Char  Color             ANSI SGR Code
        ----  -----             -------------
         .    default           (none)
         k    black             30
         r    red               31
         g    green             32
         y    yellow            33
         b    blue              34
         m    magenta           35
         c    cyan              36
         w    white             37
         K    bright black      90
         R    bright red        91
         G    bright green      92
         Y    bright yellow     93
         B    bright blue       94
         M    bright magenta    95
         C    bright cyan       96
         W    bright white      97

The complete set of valid palette characters is:

    . k r g y b m c w K R G Y B M C W

and the space character " " (which is treated as equivalent to ".").

The choice of letters follows a mnemonic scheme:

    - First letter of the color name (r=red, g=green, b=blue, etc.)
    - Exceptions: k=black (since "b" is taken by blue), w=white
    - Case encodes intensity: lowercase=normal, UPPERCASE=bright

The period "." was chosen as the default marker because it is visually
unobtrusive in a text editor, clearly distinct from shape sub-characters,
and already conventional in FIGfont files as a minimal visual element.


COLOR ROW RULES

    1) A color row uses the SAME endmark as its shape row.

    2) A color row MUST consist entirely of palette characters
       (including space) after endmark stripping.

    3) After endmark stripping, a color row MUST have the same
       width as its corresponding shape row.

    4) If a color row is absent for a given shape row, all positions
       in that row default to "." (terminal default foreground).

    5) A font MAY mix colored and uncolored FIGcharacters freely.
       A font MAY also mix colored and uncolored rows within a single
       FIGcharacter, although this is not recommended (see "DETECTION
       OF COLOR ROWS").

    6) Hardblanks in the shape row correspond to "." in the color row.
       Hardblanks are replaced with spaces in rendered output; their
       color is irrelevant.

    7) Space sub-characters in the shape row correspond to "." (or
       space) in the color row.  Coloring a space is meaningless for
       foreground colors and SHOULD be avoided.

    8) The last shape row of a FIGcharacter uses two endmarks (as in
       Version 2).  If a color row follows the last shape row, it
       MUST also use two endmarks.

Here is a complete FIGcharacter with color data, showing endmarks:

    Shape row 1:    /_/   \_\@        (one endmark)
    Color row 1:    rr.   rrr@        (one endmark)
    Shape row 2:   / _ \  @           (one endmark)
    Color row 2:  cc.c.cc @           (one endmark)
    Shape row 3:          @@          (two endmarks — last shape row)
    Color row 3:          @@          (two endmarks — last color row)


DETECTION OF COLOR ROWS

Since color rows are optional, a parser must determine whether the line
following a shape row is a color row or the next shape row (or code tag).
This section describes the algorithm that a Version 3 parser SHOULD use.

A line is a "definite color row" if, after endmark stripping:

    - It contains at least one non-space palette character
      (i.e., at least one of: k r g y b m c w K R G Y B M C W .)
    - Every character in the line is either a space or a valid
      palette character

A line that consists entirely of spaces (after endmark stripping) is
ambiguous — it could be either an empty shape row or a color row where
all positions are default.

The recommended parsing algorithm:

    1) For each FIGcharacter, maintain a boolean flag "has_color"
       which starts as false.

    2) After consuming a shape row, examine the next line.

    3) If the next line is a "definite color row" (as defined above),
       consume it as the color row for this shape row.  Set has_color
       to true.

    4) If the next line is NOT a definite color row AND has_color is
       false, treat it as the next shape row (do not consume it as
       a color row).

    5) If the next line is ambiguous (all spaces) AND has_color is
       true (a previous row in this same FIGcharacter had a definite
       color row), consume it as a color row.

    6) If the next line is ambiguous AND has_color is false, treat it
       as the next shape row.

This algorithm ensures that:

    - Fully uncolored FIGcharacters (common in logos-huge.flf3, for
      instance, where most FIGcharacters are empty placeholders) are
      parsed as Height lines, not 2*Height.

    - Fully colored FIGcharacters with trailing blank rows (where both
      shape and color are all spaces) are correctly parsed as 2*Height
      lines, because the has_color flag is set by earlier non-blank
      color rows.

IMPORTANT: Because of the ambiguity with blank lines, font designers are
strongly encouraged to make each FIGcharacter either fully colored (all
rows have color) or fully uncolored (no rows have color).  Mixing colored
and uncolored rows within a single FIGcharacter is permitted by the format
but creates parsing edge cases.

NOTE: A shape row that happens to consist entirely of palette characters
(for example, "bbbbbb" used as a shape) will be misidentified as a color
row by the algorithm above.  Font designers SHOULD avoid using palette
characters as shape sub-characters in colored FIGfonts.  This is rarely a
practical limitation, since most FIGfonts use sub-characters like "#", "/",
"\", "|", "_", "(", ")", "[", "]", and similar.


EFFECT ON SMUSHING
====== == ========

Smushing rules are evaluated on shape sub-characters only, exactly as in
Version 2.  Color MUST NOT influence any layout decision.

When two sub-characters are resolved during horizontal smushing, the color
of the resulting sub-character is determined as follows:

    1) If the left sub-character wins (i.e., the right sub-character is a
       space), use the left sub-character's color.

    2) If the right sub-character wins (i.e., the left sub-character is a
       space, or universal smushing overrides the left), use the right
       sub-character's color.

    3) If two sub-characters smush into a new sub-character (e.g.,
       horizontal smushing rule 4: "][" becomes "|", or rule 5: "/\"
       becomes "|"), use the RIGHT sub-character's color.  This matches
       the universal smushing convention where the later FIGcharacter
       has priority.

    4) If both sub-characters are spaces or hardblanks, the resulting
       color is "." (default).

The same rules apply to vertical smushing, with "left" replaced by "top"
and "right" replaced by "bottom" (i.e., the lower FIGcharacter has color
priority).

NOTE: These rules mean that smushing may produce color boundaries at
sub-character junctions.  This is intentional — it allows adjacent colored
FIGcharacters to maintain their distinct colors even when smushed together.


EFFECT ON THE RENDERING PIPELINE
====== == === ========= ========

A Version 3 FIGdriver produces two parallel outputs:

    1) Text plane:  A string of rendered ASCII art, identical to what a
       Version 2 FIGdriver would produce.  This is the primary output.

    2) Color plane:  A parallel structure of the same dimensions as the
       text plane, where each position carries a color keyword (or
       palette character).

The text plane MUST be backward-compatible — for uncolored fonts, and for
callers that do not request color, the output is identical to Version 2.

A separate formatting step converts both planes into terminal output.  This
step is NOT part of the FIGfont format specification; it is an implementation
concern.  Common output formats include:

    - ANSI escape codes for terminal display
    - HTML <span> tags with CSS color classes
    - No color (text plane only)

This separation of concerns means the FIGfont format carries color DATA,
not color PRESENTATION.  The same font can produce output for any target
format.


BACKWARD COMPATIBILITY
======== =============

The following table summarizes compatibility:

    Reader      flf2 font    flf3 uncolored    flf3 colored
    ------      ---------    ---------------    ------------
    Version 2   works        fails (sig)        fails (sig)
    Version 3   works        works              works

A Version 3 reader MUST be able to load Version 2 fonts unchanged.  The
color plane is simply absent (nil) for Version 2 fonts.

A Version 2 reader cannot load Version 3 fonts because the "flf3a" signature
is unrecognized.  This is intentional.  A Version 2 reader that attempted to
read a Version 3 colored font would misinterpret color rows as additional
shape data, producing garbled output.

NOTE: It would be possible to design a format where Version 2 readers
silently ignore color rows.  This was considered and rejected, because:

    - A Version 2 reader would count color rows as shape rows, causing
      misalignment of all subsequent FIGcharacters in the file.
    - A reader that "skips unknown lines" would need heuristics that
      could fail on edge cases.
    - The clean break of a new signature provides unambiguous versioning.


FILE NAMING
==== ======

Version 3 font files SHOULD use the extension ".flf3" to distinguish them
from Version 2 ".flf" files.  This is not strictly required — a parser
MUST detect the version from the header signature, not the filename — but
it helps users and tools identify colored fonts at a glance.

A FIGdriver that supports Version 3 SHOULD discover both ".flf" and ".flf3"
files when listing available fonts.  When a user requests a font by name
(without extension), the driver SHOULD try ".flf" first, then ".flf3".


COMPLETE EXAMPLE
======== =======

The following is a complete (if minimal) FIGfont Version 3 file.  It defines
two FIGcharacters — space and "A" — in a font with Height 6 and Baseline 5.
The space FIGcharacter is uncolored; the "A" is colored with a rainbow
gradient.

NOTE: The line drawn below consisting of "|" represents the left margin of
your editor.  It is NOT part of the FIGfont.  The hardblank is "$" and the
endmark is "@".

                |flf3a$ 6 5 20 -1 1
                |Example FIGfont Version 3 font
                |$$@
                |$$@
                |$$@
                |$$@
                |$$@
                |$$@@
                |     _    @
                |     G    @
                |    / \   @
                |   GG GG  @
                |   / _ \  @
                |  YY.Y.YY @
                |  / ___ \ @
                | RRRRRRRRR@
                | /_/   \_\@
                | MM.   MMM@
                |          @@
                |          @@

In this example:

    - Line 1 is the header: "flf3a" signature, "$" hardblank, Height=6,
      Baseline=5, Max_Length=20, Old_Layout=-1 (full width), 1 comment line.

    - Lines 2 is the comment.

    - Lines 3-8: The space FIGcharacter (uncolored, 6 lines).

    - Lines 9-20: The "A" FIGcharacter with interleaved color rows (12 lines
      = 6 shape + 6 color).

        Shape row 1:  "     _    "     Color row 1:  "     G    "
        Shape row 2:  "    / \   "     Color row 2:  "   GG GG  "
        Shape row 3:  "   / _ \  "     Color row 3:  "  YY.Y.YY "
        Shape row 4:  "  / ___ \ "     Color row 4:  " RRRRRRRRR"
        Shape row 5:  " /_/   \_\"     Color row 5:  " MM.   MMM"
        Shape row 6:  "          "     Color row 6:  "          "

    The color gradient is:
        G (bright green) at the top
        Y (bright yellow) in the middle
        R (bright red) below
        M (bright magenta) at the bottom

Notice that:

    - The space FIGcharacter has 6 lines (uncolored).
    - The "A" FIGcharacter has 12 lines (6 shape + 6 color, interleaved).
    - Color row 3 contains "." characters where the shape has "_" and " " —
      these positions use the terminal's default foreground.
    - Both the last shape row and the last color row have double endmarks.
    - The space character between color positions (e.g., "GG GG") is treated
      as "." (default), which is correct since the corresponding shape
      position is a space.

For a more substantial example, see logos-huge.flf3 in the clj-figlet
distribution, which demonstrates a multi-character colored FIGfont.


NOTES FOR IMPLEMENTORS
===== === ============


PARSING STRATEGY

A Version 3 parser extends a Version 2 parser with the following additions:

    1) Signature detection: accept "flf3a" in addition to "flf2a" and
       "tlf2a".  Record the version (:v2 or :v3) from the signature.

    2) For Version 2 fonts, parse as before (no changes).

    3) For Version 3 fonts, after consuming each shape row, check whether
       the next line is a color row using the algorithm described in
       "DETECTION OF COLOR ROWS".  If so, consume it; if not, record
       that row's color as all-default.

    4) The shape data (after color rows are separated out) has the same
       structure as Version 2 character data and MUST be processed
       identically for smushing, fitting, and rendering.

A Version 3 parser MUST NOT require color rows to be present.  A Version 3
font with no color rows is valid and produces output identical to Version 2.


INTERNAL REPRESENTATION

The internal representation of a loaded Version 3 font is left to the
implementor.  One natural approach:

    - Shape data: stored identically to Version 2 (vectors/arrays of
      strings, one per row).

    - Color data: a parallel structure mapping each character code to a
      vector of color arrays, one per row.  Each color array has the same
      length as the corresponding shape row.  FIGcharacters with no color
      data have nil/null color.

    - Font metadata: the font map includes all Version 2 fields plus
      an optional color map.

This approach maintains backward compatibility: code that expects Version 2
font maps can ignore the color map and operate on the shape data unchanged.


SERIALIZATION

When writing a Version 3 font to disk:

    1) Write the header with "flf3a" signature.

    2) Write comment lines as in Version 2.

    3) For each FIGcharacter:
       - If the FIGcharacter has color data, interleave shape and color
         rows.  Each shape row is followed by its color row.
       - If the FIGcharacter has no color data, write shape rows only
         (identical to Version 2).
       - The last shape row has two endmarks.  If a color row follows the
         last shape row, it also has two endmarks.

    4) Write code-tagged FIGcharacters as in Version 2, with the same
       interleaving for colored characters.


OPEN QUESTIONS
==== =========

    1) Background colors.

        This proposal covers foreground colors only.  Adding background
        colors would require either doubling the palette (e.g., a
        two-character encoding like "rB" for red-on-blue) or introducing
        a second color row per shape row.  Both approaches significantly
        increase complexity.  For the FIGlet use case, foreground color
        covers the vast majority of needs — background color is typically
        a property of the terminal, not the text.

    2) 256-color and truecolor.

        The 16-color ANSI palette covers the vast majority of FIGlet use
        cases.  Supporting 256-color or 24-bit truecolor would require
        a multi-character encoding (e.g., hex triplets "#FF8000") that
        breaks the one-character-per-position simplicity which makes the
        format editable in a text editor.  If truecolor support proves
        desirable, it would likely warrant a separate encoding scheme
        (perhaps a "flf4" format) rather than an extension to this
        proposal.

    3) Styles (bold, italic, underline).

        ANSI terminals support text styles in addition to colors.  This
        proposal does not address styles, but a future extension could
        use additional palette characters or a style plane.  The current
        palette reserves only 17 of the available ASCII characters,
        leaving room for future expansion.

    4) Interaction with control files.

        The Version 2 control file mechanism (described in figfont.txt
        §CONTROL FILES) is orthogonal to color.  Control files remap
        input character codes to FIGcharacter codes; color is a property
        of the FIGcharacter, not the input.  No changes to the control
        file format are needed.

    5) Vertical smushing color.

        The color priority rules for vertical smushing (lower FIGcharacter
        wins) are analogous to the horizontal rules, but have not been
        tested as extensively.  Implementors who support vertical smushing
        should verify that the color results are visually acceptable.

    6) Font editor support.

        A color font is still a plain text file, but editing paired
        shape/color rows by hand is tedious and error-prone.  A visual
        font editor that displays color data alongside shape data would
        be a valuable companion tool.


REFERENCES
==========

    - figfont.txt — The FIGfont Version 2 Standard (Cowan & Burton,
      1996-97).  The authoritative reference for all aspects of FIGfont
      files not modified by this proposal.

    - figlet.c — The reference C implementation of FIGlet, maintained at
      http://www.figlet.org/.  Source of truth for smushing behavior and
      layout semantics.

    - TOIlet — https://github.com/cacalabs/toilet
      An alternative FIGlet implementation with runtime color filters.
      TOIlet's .tlf format is structurally identical to .flf; color is
      applied by filter functions rather than embedded in font data.

    - ECMA-48 / ANSI X3.64 — The standard defining Select Graphic
      Rendition (SGR) escape sequences used for terminal color.
      The palette in this proposal maps directly to SGR codes 30-37
      (normal foreground) and 90-97 (bright foreground).

    - clj-figlet (https://github.com/danlentz/clj-figlet) — A native
      Clojure FIGlet implementation and the reference implementation
      for FIGfont Version 3.
