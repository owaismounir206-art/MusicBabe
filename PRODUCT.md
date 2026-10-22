# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

A mixed audience, with no single dominant profile (confirmed by the owner):
- people with large libraries of their own files (MP3/FLAC) who need to find and organize them fast;
- casual listeners who open the app, start a mix or playlist and leave it playing;
- people who stream from their own servers (Navidrome/Subsonic, Jellyfin), Telegram or Google Drive.

All of them use it on a phone, often one-handed and while doing something else; the player is reopened many times a day for a few seconds at a time.

## Product Purpose

MusicBabe is an Android music player that brings local files, personal servers and cloud sources into one library and one player. Success: music starts in a couple of taps, the library stays navigable at thousands of tracks, and the app feels better to use than the system and competing players.

## Positioning

Beauty and fluidity: the owner wants MusicBabe to be the most beautiful and the smoothest music player on Android. The claim has to show on screen: in motion, in finish and in how the cover art carries the interface, not in a list of features.

## Operating Context

- Daily use on a phone, light and dark themes, outdoors and in the dark.
- Background playback with notifications, lock screen, home-screen widgets, Wear OS, Android Auto and Cast.
- Short sessions (pick → play → close) alternating with long library-maintenance sessions (tags, playlists, folders).

## Capabilities and Constraints

- Sources: local MediaStore, Navidrome/Subsonic, Jellyfin, Telegram, Google Drive, online search; equalizer, crossfade, ReplayGain, mashup decks.
- Synced lyrics, AI-generated mixes and playlists (user-supplied API keys), listening stats ("Wrapped").
- Kotlin, Jetpack Compose, Material 3 Expressive, Media3; minSdk 30.
- **Colour stays dynamic** (owner's decision): the app scheme comes from Material You (wallpaper) and the player scheme from the cover art of the current track, with the existing static fallback. A redesign changes shape, typography, layout, density and motion, not where colour comes from.
- Performance is a product requirement: no jank while scrolling, during sheet gestures or palette transitions (README targets 120 fps).
- UI available in 13 languages; every visible string lives in resources, English default, Italian always included.
- No changes to the Room schema, `applicationId` or package names without the owner's say-so.

## Brand Commitments

- Name: MusicBabe (fork of PixelPlayer; it must no longer present itself as PixelPlayer).
- Colours: Material You + cover-art palette (see above).
- No logo or brand guidelines beyond `assets/icon.png` and `assets/PixelPlayer.svg` (upstream).

## Evidence on Hand

- `assets/screenshot1-4.jpg`: screenshots of the **upstream PixelPlayer**, not of current MusicBabe; useful only as evidence of the incumbent look.
- No device or emulator in the dev environment: visual verification happens on the owner's phone.
- No testimonials, user counts or benchmarks: do not invent them.

## Product Principles

1. Music before chrome: the cover art and the track are the protagonists; the interface steps back.
2. Smoothness is a feature: every transition must stay fluid on mid-range devices; a beautiful effect that stutters is a defect.
3. One library, many sources: the source is a detail; the experience is the same for local and remote.
4. Fast to the first note: from the app icon to music playing in as few gestures as possible.
5. Native Android: Material 3 governs structure, navigation and system gestures; identity lives in theming and craft.

## Accessibility & Inclusion

- Touch targets at least 48 dp; text in `sp` so it follows the system font size.
- Respect the system's "Remove animations" setting.
- Right-to-left support (Arabic among the locales).
