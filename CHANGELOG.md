# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.0] - 2026-10-23

### Added
- **Sincronizza parola per parola sull'audio**: nuova voce nel menu dei testi. L'app ascolta la canzone sul telefono con Whisper (whisper.cpp, modello "base" incluso nell'APK) e assegna a ogni parola del testo il momento in cui viene cantata. Il testo resta sempre quello vero: dall'audio vengono presi solo i tempi. Le parole non riconosciute ricevono un tempo interpolato tra le vicine; se se ne sentono troppo poche (meno del 35%), il testo non viene toccato. Richiede qualche minuto per brano, mostra l'avanzamento e si può annullare. Funziona sui brani salvati sul telefono.

### Fixed
- I tempi per parola venivano scartati ogni volta che un testo veniva salvato (database o file `.lrc`): ora sono salvati come LRC avanzato. Questo riguardava anche i tempi veri di NetEase della 1.5.2.
- I tempi LRC salvati con la lingua araba usavano cifre arabo-indiche e non si potevano rileggere.

### Changed
- L'APK passa da circa 66 a circa 129 MB per via del modello vocale incluso.

## [1.5.2] - 2026-10-23

### Added
- **Testi parola per parola su tutte le canzoni**: quando NetEase fornisce i tempi per parola (formato "yrc"), l'app ora li usa davvero; prima li scaricava e li ignorava. Per i testi che hanno solo i tempi per riga, il tempo della riga viene diviso tra le parole in base alla loro lunghezza. L'etichetta in alto dice "Parola per parola · stimato" quando i tempi sono una stima.

## [1.5.1] - 2026-10-23

### Fixed
- **Testi con l'AI (Gemini) lenti o mai generati**: quando una canzone non aveva già il testo, l'app chiedeva all'AI di scriverlo a memoria. Gemini blocca la riproduzione di testi protetti da copyright, quindi dopo una lunga attesa non arrivava niente. Ora l'AI viene usata solo per sincronizzare un testo che c'è già; altrimenti l'app lo dice subito. Se Gemini interrompe la risposta, l'errore spiega il motivo.
- **Titoli Scena**: ora sono davvero larghi e pesanti come nelle anteprime. Il font non applicava la larghezza sul telefono.
- **Colori Scena**: lo sfondo prende di più il colore dinamico.
- **Testi**: l'etichetta "Per riga / Parola per parola" non copre più le righe; il pulsante "Sincronizzato" non va più a capo; tolto il disco sullo sfondo che copriva il testo.
- **Home**: il disco mostra la copertina del mix invece di una forma vuota.

## [1.5.0] - 2026-10-23

### Added
- **Sottotitoli SRT e VTT per i testi**: oltre a LRC e TTML, i file `.srt` e `.vtt` vengono importati e letti. Il formato si riconosce dal contenuto, non dall'estensione. VTT supporta i tempi per parola; SRT ha solo i tempi per riga, quindi le parole non vengono illuminate singolarmente. Lo stesso vale per i file `.srt`/`.vtt` accanto all'audio, cercati dopo il `.lrc`.
- **Nuovo linguaggio visivo "Scena"**: colore sempre dinamico (Material You e copertina), con titoli espansi, copertine squircle che diventano dischi rotanti durante la riproduzione, pulsante play che cambia forma. Applicato a Home, Libreria, player a schermo intero, mini player, barra di navigazione, testi, impostazioni e chiavi AI.

### Changed
- **Testi**: le parole si illuminano una alla volta nel momento in cui vengono cantate, dove il file ha i tempi per parola. Un'etichetta indica se i testi sono "parola per parola" o "per riga".
- **Chiavi AI**: la sezione è riorganizzata, con la chiave mascherata, lo stato di verifica e una nota sulla cifratura.

### Known limits
- Le sezioni Ascoltati di recente e Daily Mix, e la card delle statistiche in Home, usano ancora lo stile precedente.
- Il video musicale è solo nel design, non è ancora implementato nell'app.

## [1.4.0] - 2026-10-22

### Changed
- **Redesign "Sleeve"**: new visual identity inspired by record sleeves. Colours stay dynamic (Material You + cover art); type, shapes and density change.
  - Crisp font (Google Sans Flex with ROND axis at 0) across the whole app, durations and times in tabular figures.
  - **Library**: rows without a card per track, square covers, duration aligned right; text tabs with an underline instead of pills; flat header.
  - **Player**: square mini player with 48 dp touch targets; squarer covers and controls in the full-screen player; technical data (format · kHz · kbps) set as a "catalogue line" between the times.
  - **Home**: more compact "Your Mix" header with a single shuffle button; new default collage **Sleeve Stack** (any collage already chosen is kept).
  - **Settings**: squarer groups and icons, more compact rows, plain back button.
- Design documentation in `DESIGN.md` and product context in `PRODUCT.md`.

### Fixed
- Hardcoded Spanish/Italian labels in the mini player, the player controls (also read aloud by TalkBack) and the Wrapped banner are now localized (EN/IT).
- Widget controls: the background-start error of the foreground service is handled without referencing an API 31 class on Android 11.

## [1.3.7] - 2026-10-21

### Added
- **Coda di download con hub dedicato**: `SongDownloadManager` espone ora `downloadTasks` (`StateFlow<List<DownloadTask>>`) con stati espliciti (in coda, risoluzione, download, completato, fallito, annullato), `cancelDownload`, `retryDownload`, `removeDownload` e `clearFinishedDownloads`. Nuova schermata `DownloadsScreen` (ricerca → icona hub, oppure Impostazioni → Download) con coda attiva, cronologia, annullamento, retry e riproduzione del brano scaricato.
- **Foglio "Scarica" dalla barra di ricerca**: anteprima della migliore corrispondenza (`previewQueryCandidates`) e scelta manuale tra i risultati NetEase già validati, con accesso diretto all'hub dei download.
- **Preferenze download**: "Solo Wi-Fi" (i download su rete a consumo falliscono con messaggio esplicito) e qualità audio (Automatica/Alta/Standard) applicata a NetEase e KuGou.
- **Progresso per riga**: il bottone dei risultati online mostra il progresso reale del download e diventa ✓ quando il brano è già presente.

### Changed
- **Foglio azioni unificato**: i risultati online hanno una sola azione che apre un bottom sheet con Riproduci / Salva in libreria / Scarica (in-app) / Scarica con YTDLnis, eliminando i due bottoni affiancati.
- **Feedback di fine download**: snackbar con "Apri" (riproduce il brano scaricato) o "Riprova" per i download avviati dalla ricerca; non più download silenziosi.
- **Notifica di avanzamento persistente** con percentuale reale e apertura dell'app; le notifiche finali usano stringhe localizzate (EN/IT) invece di testi hardcoded.
- **Dedupe**: lo stesso brano non viene più scaricato due volte; l'indice dei file in `Music/MusicBabe` viene ricostruito all'avvio e aggiornato a ogni download.

### Fixed
- **Estensione e MIME corretti**: il formato viene derivato dal `Content-Type` della risposta (fallback sull'URL) — i flussi m4a/flac/ogg non vengono più salvati come `.mp3` con MIME `audio/mpeg`.
- **Durata locale reale**: se la sorgente non dichiara la durata viene letta dal file scritto, invece di forzare 30s.
- Il doppio tap su "Scarica" non avvia più due download (guardia sulla coda con chiave `titolo|artista`).

## [1.3.6] - 2026-10-21

### Added
- **Download da ricerca, tutto in-app**: il pulsante della barra di ricerca scarica ora il brano completo usando solo sorgenti interne — NetEase (stream integrale) e il nuovo `KugouFullTrackResolver` (API pubbliche, senza chiavi né login). Nessuna app esterna da installare.
- **`SearchQueryMatcher`**: scoring puro e testato (token su titolo/artista, bonus titolo/artista, soglia minima) usato sia dalla ricerca NetEase sia da KuGou.
- **Test unitari** per il matcher di query (7 casi: punteggiatura, parentesi, solo artista, query estranea, input vuoto, token corti).

### Changed
- **Cascata download**: NetEase → KuGou → YTDLnis (solo se già installato). L'installazione automatica di YTDLnis non viene più avviata dal download.
- **Anti-anteprima**: entrambe le sorgenti interne scartano risultati sotto i 45s e KuGou ignora le tracce a pagamento (`url` vuoto) invece di ripiegare su clip promozionali.
- Il pulsante di download nella barra di ricerca resta sempre toccabile (spinner durante il download, guardia nel click) e la tastiera si chiude al tocco.

### Fixed
- Download da query libera: nessun salvataggio di MP3 da 30s, metadati e copertina scritti nei tag anche per i brani risolti da KuGou.

## [0.7.5-beta] - 2026-06-13

### Added
- **Google Drive:** Added Google Drive support and improved player lifecycle management.
- **AI Lyrics:** Integrated AI lyrics translation logic in `LyricsStateHolder` and user preferences.
- **Gemma:** Deleted old Gemini model IDs and integrated Gemma model support.
- **Wear OS:** Added wear lyrics translation/romanization preferences and album art background.
- **Diagnostics:** Added a lag diagnostic tool.
- **Search:** Added multi-selection support to the Search screen.
- **UI:** Added outlined button style for AOD screen.
- **Connectivity:** Added support for HTTP URLs on local-network Navidrome and Jellyfin hosts.
- **Localization:** Added Arabic and Turkish language support, and unrecognized languages.

### Changed
- **Battery Optimization:** Drastically reduced battery consumption via audio offload and adaptive UI polling.
- **Queue System:** Refactored shuffle, queue reordering, and playback orchestration to `QueueStateHolder` using explicit queue indices.
- **Transitions & Animations:** Implemented Material 3 Expressive motion curves for player, queue sheet, and screen transitions.
- **Architecture:** Decomposed `MusicService` and modularized `PlayerViewModel` state listeners.
- **Library Sync:** Optimized library sync with throttled scans and faster artwork loading.
- **Database:** Migrated database to version 42 and updated Navidrome schema.
- **Equalizer:** Added "Save New" action and improved layout.
- **Localization:** Refactored app localization, resource cleanup, and UI text wrapping.
- **Dependencies:** Bumped dependencies including `kotlinx-collections-immutable`, `okhttp`, and Gradle plugins.

### Fixed
- **Playback:** Resolved buffering issues, song skipping lags, and unnecessary recompositions during playback.
- **Media Store Sync:** Improved MediaStore URI resolution, external song deletion, and Android 11+ storage volume resolution.
- **Lyrics & Metadata:** Fixed Chinese lyrics detection, pinyin tone suffixes, and batch metadata/artwork editing consistency.
- **Wear OS:** Resolved memory issues and state persistence.
- **UI:** Fixed marquee text fade glitches, navigation bar corner behavior, blur issues, scrollbar bugs, and layout/padding insets.
- **Other:** Fixed backup playlist update issues and startup AI provider errors.

### New Contributors
- @YtMechnij made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2106
- @juinc made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2109
- @ZL114514 made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2159
- @aliabbasov99 made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2262
- @Hisham-Alzamzami made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2335


## [0.7.0-beta] - 2026-05-25

### Added
- **Wear OS:** Music transfer, local playback, queue synchronization, and remote control from the watch.
- **AI:** Groq AI and OpenRouter (experimental) with token optimization and AI-powered playlist generation.
- **Cloud & Streaming:** Jellyfin support.
- Direct song synchronization from server albums in Navidrome.
- Standardized branding for NetEase Music.
- **Lyrics:** Synchronized translation with a dedicated toggle and Kugou LRC format support.
- Text alignment customization and improvements to TTML parsing.
- Advanced romanization for Japanese characters.
- **UI/UX:** Redesigned queue sheet and "Recently Played" pills with a dynamic palette.
- Marquee support for long titles and a compact mode for the navigation bar.
- New horizontal timeline for monthly statistics and multi-artist support.
- **Telegram:** Native support for topics, playlist display, and reactive updates.

### Changed
- **Audio Engine:** Complete overhaul with support for MIDI, improvements to ALAC/M4A/Opus, and decoder optimization (including Samsung-specific decoders).
- **Energy Efficiency:** Drastically reduced battery consumption and thermal optimization through UI task gates.
- **Database and Cache:** Massive optimizations to queries, cover art cache controller v3, and support for Scoped Storage.
- **Startup:** Improved load times through optimized generation of Baseline Profiles.
- Project license changed from MIT to Proprietary License.

### Fixed
- **Playback:** Fixed stuttering in Opus/MP3, errors in ReplayGain during crossfades, and flickering during album art changes.
- **Navigation:** Fixed navigation loops in Telegram and improved screen entry/exit animations.
- **Stability:** Eliminated crashes on Android 12+, fixed memory leaks (ANRs), and improved exception handling in background services.
- **Security:** CI hardening, encryption of cloud storage credentials, and media server access control.

### Localization
- 🇪🇸 **Spanish** | 🇫🇷 **French** | 🇷🇺 **Russian**
- 🇨🇳 **Simplified Chinese** | 🇮🇩 **Indonesian** | 🇮🇹 **Italian** | 🇩🇪 **German**

## [0.6.0-beta] - 2026-03-05

### Added
- Added Android Auto support through Media3 `MediaLibraryService`.
- Added Wear OS companion support, including watch transfer and playback controls.
- Added cloud provider expansions: Telegram playlist management, NetEase sync improvements, QQ Music integration, Subsonic/Navidrome, and Google Drive streaming (WIP).
- Added a modernized backup/restore system (v3), account management, and persistent queue restoration.
- Added smarter lyrics workflows (manual fallback search + storage refactor), Recently Played, and new multi-selection flows (songs/albums/playlists).
- Added home and UI customization features: collage patterns, quick settings tiles, expressive scrollbar refinements, and new widget styles.

### Changed
- Reworked player architecture and interaction model (unified player sheet refactors, predictive back handling, gesture tuning).
- Redesigned key surfaces including Lyrics, Cast, Artist, Genre, and Daily Mix experiences.
- Refined library/search/navigation behavior with safer navigation APIs and better state restoration.
- Improved audio compatibility and metadata handling (JAudioTagger fallback, URI handling, surround/noisy behavior).
- Expanded integration UX across Telegram/NetEase/QQ login and sync flows.

### Fixed
- Fixed multiple queue/shuffle edge cases (anchored shuffle, start-at-zero shuffle, queue synchronization).
- Fixed playback interruption behavior when headphones disconnect and resolved foreground service start restrictions.
- Fixed Cast-related crash cases and improved cast reliability.
- Fixed Sleep Timer UI issues, files tab navigation, album artist crash, and state-sync regressions in settings/reorder flows.
- Fixed release build stability (`R8`) and numerous UI polish issues across bottom sheets and controls.

### Performance
- Reduced recompositions and state overhead across Player, Library, Queue, and detail screens.
- Improved startup behavior (eliminated blank flash and deferred heavy Telegram native loading off main thread).
- Optimized folder/genre/artist loading, bottom navigation responsiveness, and gesture fluidity.
- Reduced CPU/main-thread pressure and improved service/widget runtime efficiency.
- Reduced APK size using ABI splits, downloadable fonts, and SDK cleanup.

### New Contributors
- @ThatOneCalculator
- @ryan7zoom
- @LarveyOfficial
- @Dv1101
- @Sincere-Bhattarai

## [0.5.0-beta] - 2026-01-14

### Added
- Implemented 10-band Equalizer and effects suite (feat: @theovilardo)
- Added M3U playlist import/export support (feat/fix: @lostf1sh, @theovilardo)
- Integrated Deezer API for artist images (feat: @lostf1sh)
- Added Gemini AI model selection, system prompt settings, and AI playlist entry point (feat: @lostf1sh, @theovilardo)
- Added sync offset support for lyrics and multi-strategy remote search (feat/fix: @lostf1sh, @theovilardo)
- Added Baseline Profiles for improved performance (feat/fix: @theovilardo, @google-labs-julesbot)
- Added support for custom playlist covers

### Changed
- **Material 3 Expressive UI**: Modernized Settings, Stats, Player, Bottom Sheets, and dialogs (refactor: @theovilardo, @lostf1sh)
- **Library Sync**: Rebuilt initial sync flow with phase-based progress reporting and linear indicators (feat: @lostf1sh)
- **Settings Architecture**: Introduced category sub-screens and improved navigation handling (refactor/fix: @theovilardo)
- **Queue & Player**: Decoupled queue updates from scroll animations, added animated queue scrolling (feat/fix: @lostf1sh, @theovilardo)
- Improved widget previews and case-insensitive sorting logic (feat/fix: @lostf1sh, @google-labs-julesbot)

### Fixed
- Fixed casting stability, queue transitions, and reduced latency (fix: @theovilardo)
- Fixed delayed content rendering and unwanted collapses in Player Sheet (fix/refactor: @theovilardo)
- Fixed reordering issues in queue
- General crash fixes and minor UX improvements (fix: @lostf1sh, @theovilardo)

## [0.4.0-beta] - 2025-12-15

### Added
- Major navigation redesign
- New file explorer for choosing source directories
- Landscape mode (thanks to "leave this blank for now")
- New Connectivity and casting functionalities
- Seamless continuity between remote devices
- Gapless transition between songs
- Crossfade
- New Custom Transitions feature (only for playlists)
- Keep playing after closed the app
- UI Optimizations
- Improved stats feature
- Redesigned Queue control with more features
- Improved different filetypes support for playing and metadata editing
- Improved permission controller
- Minor bug fixes

## [0.3.0-beta] - 2025-10-28

### What's new
- Introduced a richer listening stats hub with deeper insights into your sessions.
- Launched a floating quick player to instantly open and preview local files.
- Added a folders tab with a tree-style navigator and playlist-ready view.

### Improvements
- Refined the overall Material 3 UI for a cleaner and more cohesive experience.
- Smoothed out animations and transitions across the app for more fluid navigation.
- Enhanced the artist screen layout with richer details and polish.
- Upgraded DailyMix and YourMix generation with smarter, more diverse selections.
- Strengthened the AI assistant to deliver more relevant playback suggestions.
- Improved search relevance and presentation for faster discovery.
- Expanded support for a broader range of audio file formats.

### Fixes
- Resolved metadata quirks so song details stay accurate everywhere.
- Restored notification shortcuts so they reliably jump back into playback.

## [0.2.0-beta] - 2024-09-15

### Added
- Chromecast support for casting audio from your device (temporarily disabled).
- In-app changelog to keep you updated on the latest features.
- Improved lyrics search
- Support for .LRC files, both embedded and external.
- Offline lyrics support.
- Synchronized lyrics (synced with the song).
- New screen to view the full queue.
- Reorder and remove songs from the queue.
- Mini-player gestures (swipe down to close).
- Added more material animations.
- New settings to customize the look and feel.
- New settings to clear the cache.

### Changed
- Complete redesign of the user interface.
- Complete redesign of the player.
- Performance improvements in the library.
- Improved application startup speed.
- The AI now provides better results.

### Fixed
- Fixed various bugs in the tag editor.
- Fixed a bug where the playback notification was not clearing.
- Fixed several bugs that caused the app to crash.

## [0.1.0-beta] - 2024-08-30

### Added
- Initial beta release of PixelPlayer Music Player.
- Local music scanning and playback (MP3, FLAC, AAC).
- Background playback using a foreground service and Media3.
- Modern UI with Jetpack Compose, Material 3, and Dynamic Color support.
- Music library organization by songs, albums, and artists.
- Home screen widget for music control.
- Real-time audio waveform visualization.
- Built-in tag editor for song metadata.
- AI-powered features using Gemini.
- Smooth in-app permission handling.
