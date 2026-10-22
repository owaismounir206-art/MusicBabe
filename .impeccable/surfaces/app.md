# Surface brief: MusicBabe app (Home, Library, Player, Settings)

Scope: the app's working screens. Mode: **Operate**. Platform: android (Material 3 governs structure, navigation and controls).
Redesign: replaces the incumbent "Material 3 Expressive" look (pills, circles, stretched Montserrat, a card per row).
Pinned constraint: colour stays dynamic (Material You + cover-art palette), see PRODUCT.md.

Unresolved: no device/emulator in the dev environment; visual verification is on the owner's phone.

## Direction contract

THESIS: MusicBabe dresses like a record sleeve from a restrained label (the ECM school): the cover art is the image, the interface is the typesetting around it. It refuses the category default of one rounded card per row, pill-shaped tabs and inflated display headings.

OWN-WORLD: colours from Material You/the cover only, used flat: a uniform surface, primary only for current state and selection. Square art with a 4 dp corner ("sleeve"); 1 dp hairlines in `outlineVariant` instead of cards; one sans (Google Sans Flex, ROND axis at 0, crisp not rounded) in a tight Material scale; figures always tabular. Controls are standard Material 3: text tabs with an indicator, icon buttons, Switches.

STORY: whoever opens the app recognises the music first (covers, titles), then the controls; finds a track by scrolling a dense, calm list; starts it with one tap and sees it "pressed" into the mini player as a sleeve.

FIRST VIEWPORT: Library: compact title at left, settings at right; row of text tabs (Brani, Album, Artisti, Playlist…) with a 2 dp indicator under the active one; shuffle/sort row; list of 64 dp rows: square 48 dp art, title on one line, artist beneath in `onSurfaceVariant`, duration right-aligned in tabular figures, ⋮ as a plain icon button. Mini player above the navigation bar: square art, title/artist, play and next. Primary action: tap on the row.

FORM: liner notes / record sleeve, first in my ordered list (ECM sleeve → record-store divider → gig poster → listening-room booth → radio-dial → cassette J-card → festival wristband). SIGNATURE MOVE: the "catalogue line", the technical metadata of the track (format · sample rate · duration) set like the catalogue number on a sleeve, in tabular figures, in the player and the details. seed: none (unavailable)

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
