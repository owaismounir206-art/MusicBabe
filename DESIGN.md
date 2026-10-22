---
name: MusicBabe
description: Android music player dressed like a record sleeve from a restrained label
colors:
  # Colour is dynamic: Material You (wallpaper) for the app, the cover-art palette for the player.
  # The values below are only the static fallback scheme (ui/theme/Color.kt), used when neither applies.
  fallback-primary: "#6C4FF5"
  fallback-primary-container: "#E3DBFF"
  fallback-surface: "#FBF8FF"
  fallback-on-surface: "#1E1237"
  fallback-on-surface-variant: "#4D4165"
  fallback-outline: "#78659A"
typography:
  display:
    fontFamily: "Google Sans Flex (ROND 0)"
    fontSize: "36sp"
    fontWeight: 600
    letterSpacing: "-0.5sp"
  headline:
    fontFamily: "Google Sans Flex (ROND 0)"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "36sp"
  title:
    fontFamily: "Google Sans Flex (ROND 0)"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
  body:
    fontFamily: "Google Sans Flex (ROND 0)"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
  label:
    fontFamily: "Google Sans Flex (ROND 0)"
    fontSize: "14sp"
    fontWeight: 500
    fontFeature: "tnum"
rounded:
  art: "4dp"
  cover: "8dp"
  row: "8dp"
  control: "12dp"
  group: "12dp"
  group-inner: "2dp"
spacing:
  gutter: "20dp"
  row-vertical: "8dp"
  art-to-text: "16dp"
components:
  song-row:
    height: "64dp"
    padding: "8dp 0 8dp 8dp"
    rounded: "{rounded.row}"
  song-row-art:
    size: "48dp"
    rounded: "{rounded.art}"
  mini-player:
    height: "64dp"
    rounded: "{rounded.control}"
  mini-player-play:
    size: "40dp"
    rounded: "{rounded.row}"
  transport-skip:
    rounded: "{rounded.control}"
  fab-shuffle:
    size: "56dp"
    rounded: "16dp"
---

# Design System: MusicBabe

## Overview

**Creative North Star: "The Record Sleeve"**

MusicBabe dresses like a sleeve from a restrained label in the ECM school: the cover art is the image, and the interface is the typesetting around it. Colour belongs to the user (Material You) and to the music (the cover palette in the player); the identity lives in what does not change, namely type, square forms, density, whitespace and a few precise details.

The screens are Operate surfaces: the user wants to find and play music. Familiarity matters more than surprise; Material 3 governs navigation, controls and gestures. The craft shows in the hairline instead of the card, in tabular figures, in the 4 dp corner of the covers.

Confirmed rejection: the previous "Expressive" look (a card per row, pills, circles, stretched Montserrat headings).

**Key Characteristics:**
- Square covers with a barely softened corner, everywhere.
- Lists set on the page, without a container per row.
- One sans (Google Sans Flex, crisp), tight Material scale, tabular figures.
- One filled element per area: the primary action.

## Colors

The palette is not fixed: every colour comes from the dynamic scheme's roles, used flat.

### Primary
- **Primary of the scheme** (dynamic): current track (title, equalizer icon), selected tab and its underline, the play button. Never decoration.

### Neutral
- **Surface** (dynamic): the single ground of every screen, from top to bottom; no gradients behind the headers.
- **On-surface-variant** (dynamic): artists, durations, secondary icons, unselected tabs.
- **Outline-variant at 70%** (`hairlineColor`): hairlines between areas.

### Named Rules
**The Borrowed Colour Rule.** MusicBabe has no colours of its own: it never introduces hex values in screens, only roles of the dynamic scheme. The fallback values in the frontmatter exist only for devices without Material You.

## Typography

**Font:** Google Sans Flex variable (`res/font/gflex_variable.ttf`), ROND axis at 0. The family is still called `GoogleSansRounded` in code for historical reasons.

**Character:** a single typeset voice, crisp and calm; hierarchy comes from size and weight, never from horizontally stretched letters.

### Hierarchy
- **Display** (600, 36 sp, tracking −0.5 sp): only the Home title ("Your Mix").
- **Headline** (600, 28 sp): screen titles (Library); the player title uses headlineSmall 600.
- **Title** (500, 14 sp): tabs.
- **Body** (500 / 400, 16 / 14 sp): track and row titles (bodyLarge Medium), artists and subtitles (bodyMedium).
- **Label** (500, 12–14 sp, `tnum`): durations, times, the "catalogue line".

### Named Rules
**The Tabular Rule.** Every figure that changes or lines up in a column (durations, times, counts, selection index) uses `Sleeve.TabularFigures`.

## Layout

Page margin of 20 dp (`Sleeve.Gutter`). Song rows of 64 dp: 48 dp art, 16 dp to the text, duration right-aligned, overflow menu as a 48 dp icon button; 2 dp between rows. Settings in groups (12 dp outside, 2 dp between items). More space above a section heading than below it.

## Elevation & Depth

Flat by default. Depth is conveyed by Material's tonal levels (surfaceContainer for groups and the mini player), not by shadows: buttons and FABs have no elevation. The selection/current-track background is a 10% tint of primary.

## Shapes

- **Art** 4 dp: thumbnails in lists, mini player, collage (6 dp).
- **Cover** 8 dp: the cover in the full-screen player.
- **Row / small control** 8 dp: row background, mini player play button, segmented buttons (8 outside, 2 inside).
- **Control** 12 dp: skip buttons, lyrics/queue, icon tiles in settings, collapsed player sheet.
- Circles only where Material requires them (Switch, progress indicators).

## Components

- **Song row** (`EnhancedSongListItem`): transparent; current track → title in primary, equalizer in place of the duration, 10% primary background; selection → secondaryContainer with a 2 dp outline and a numbered overlay on the cover.
- **Tab** (`TabAnimation`): text, selected in primary with a 2 dp underline that opens from the centre (200 ms); the row has a hairline below. No pills.
- **Mini player**: square cover 48 dp, title/artist, previous · play · next with 48 dp touch targets; only play is filled.
- **Transport (full player)**: play/pause morphs between 12 dp (paused) and 28 dp (playing); skip at 12 dp.
- **Catalogue line** (signature): format · sample rate · bitrate in `labelSmall` + `tnum`, tracking 0.8 sp, between the times under the progress bar.
- **Collage "Sleeve Stack"**: default Home pattern; square sleeves fanned out and overlapping, unrotated.
- **Top bars**: plain icon buttons (no filled circle).

## Do's and Don'ts

- **Do** use `Sleeve.ArtShape` for every cover; **don't** turn it into a circle in any state.
- **Do** separate with whitespace and hairlines; **don't** put each row in its own card or nest cards.
- **Do** keep a single filled element per area; **don't** fill secondary controls.
- **Do** write visible text in `strings.xml` (English + Italian); **don't** leave hardcoded labels or contentDescriptions.
- **Don't** add new hex colours: use dynamic scheme roles.
- **Don't** use decorative motion (wobble, bounce on tabs); motion signals state, 150–250 ms, and the press "give" on controls stays.
