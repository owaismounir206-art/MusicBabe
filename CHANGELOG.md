# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.4.0] - 2026-10-29

### Added
- Welcome screen on first start.
- Smart playlists (recently added, long, short, before 2000, favorites, for this time of day)
  and a duplicate finder, in the listening options.
- Last.fm scrobbling with your own API key.
- Subscribe to the channel from the player; video chapters you can jump to.
- Podcasts and Live in the online home; concerts of the artist (Bandsintown).
- Download a whole online album or playlist; search inside long lists; top result in search.
- Save lyric lines, report wrong lyrics on LRCLIB, search a song by a lyric line in quotes
  (Gemini).
- Audio visualizer, monthly music recap, save liked online songs on Wi-Fi, saved video quality.

## [2.3.0] - 2026-10-29

### Added
- Listening options (tune icon in the player): skip silences, even out the volume, A–B repeat,
  sleep timer that fades out, skip songs heard today, lyrics text size, share the queue,
  music alarm, online audio quality, music video quality (360p–1080p) and data saver on
  mobile data.
- With a Gemini key: what the song is about, credits and samples, and song recognition from
  10 seconds of microphone audio.
- Voice search in the online search.
- Spotify import: paste a playlist or album link in the search; every song is found on
  YouTube Music.
- Picture-in-picture for music videos when you leave the app.

## [2.2.0] - 2026-10-28

### Added
- **Account YouTube Music** (facoltativo, icona account in Home): accedi con Google dentro l'app. Mi piace, le tue playlist, i brani che ti piacciono, la cronologia e la Home personale ("Perché ascolti…", i tuoi mix) sono quelli del tuo account.
- **Sotto il titolo nel player, come su YouTube Music**:
  - 👍 con il numero dei Mi piace, sincronizzato con l'account e con i preferiti;
  - 👎;
  - commenti (si leggono, con avatar, Mi piace e risposte);
  - visualizzazioni;
  - Condividi;
  - Salva sul telefono.
- **Samples**: spezzoni dei video e brani consigliati da scorrere in verticale. Ognuno parte dalla parte migliore; "Brano intero" lo riprende dall'inizio.
- **Ricerca**: suggerimenti di YouTube Music mentre scrivi e cronologia delle ricerche, cancellabile.
- **Esplora** in Home: Novità e Classifiche.
- **Tema Nero (AMOLED)** in Impostazioni > Aspetto > Colori dell'app.

## [2.1.0] - 2026-10-27

### Added
- **Schermo intero in orizzontale**: dal player o dall'icona sul video. A sinistra il video (o la copertina) con i comandi, a destra i testi che scorrono.
- **Salva sul telefono**: salva esattamente quello che stai ascoltando, senza cercarlo su altre fonti. Formati:
  - audio in qualità originale (.m4a);
  - audio non compresso (.wav);
  - il video musicale (.mp4 fino a 1080p con audio), che compare anche in "Video musicali".
- **Brano | Video** nel player, come in YouTube Music. I video musicali si trovano anche nella Home ("Video musicali per te") e nei risultati di ricerca, e si aprono direttamente sul video.

### Changed
- **La Home è la zona online**, ora più pulita:
  - In alto: ricerca, mood e "Riprendi l'ascolto" con i brani della tua libreria.
  - Poi "Selezione rapida", i video e le sezioni di YouTube Music.
  - Le icone in alto portano a "Video musicali", a "Per te" (mix e statistiche della vecchia Home) e alle Impostazioni.
  - Le schede tornano tre: Home, Cerca, Libreria.
- **Testi**: sfondo scuro tinto col colore della copertina, con la copertina sfocata dietro. Lo stile delle righe resta quello della v1.7.0 ripristinato nella 2.0.3.

### Fixed
- **Brani e video online partono subito**: lo stream viene letto a pezzi, come fanno le app di YouTube. Il brano parte appena arriva il primo pezzo e il resto si carica mentre ascolti; prima un'unica richiesta veniva rallentata o interrotta da YouTube.

## [2.0.3] - 2026-10-27

### Added
- **Interfaccia Home in stile YouTube Music**:
  - Filtri orizzontali pinned a chip ("Tutto", "Video musicali", "Canzoni", "Album", "Artisti", "Mix") per navigare rapidamente tra i diversi contenuti musicali.
  - Carosello video musicali in formato widescreen 16:9 con badge durata e pulsante di riproduzione diretta o aggiunta video dal dispositivo.
  - Carosello album con copertine quadrate 1:1 e badge con anno e numero di brani.
  - Carosello artisti in evidenza con avatar circolari e badge con conteggio tracce.
  - Sezione "Selezione rapida" con layout a 3 righe orizzontali scorrevoli, pulsanti di riproduzione rapida e menu a tre puntini per le opzioni del brano.

### Changed
- **Restyling estetico dei testi sincronizzati (ritorno all'estetica amata della v1.7.0)**:
  - Eliminato il forzato MAIUSCOLO su tutte le parole per ripristinare il casing naturale e la perfetta leggibilità originale.
  - Ripristinata la tipografia pulita Google Sans Rounded con interlinea corretta (1.15f per la riga attiva, proporzionata a riposo).
  - Ripristinato l'effetto di ingrandimento a molla fluido (1.08x con fisica Springy) sulla parola attualmente cantata senza artefatti o distorsioni di dimensione fittizie.
  - Rimossi effetti di bagliore e scalatura cinetica sfocati che appesantivano la lettura.

## [2.0.2] - 2026-10-27

### Added
- **Testi da KuGou**: nuovo livello di ricerca dei testi sincronizzati tramite le API pubbliche KuGou (nessuna chiave, nessun login), usato automaticamente quando LRCLIB non ha il brano e NetEase non lo espone. I testi trovati vengono salvati come gli altri (cache, file `.lrc` accanto al brano) e la UI indica la sorgente KuGou.
- **Download da YouTube**: quando nessuna delle fonti abituali (KuGou, NetEase, CDN) fornisce il brano intero, il download usa l'estrattore YouTube già presente per l'ascolto online, con verifica della durata prima di salvare il file.

### Changed
- **Download in un tocco**: il pulsante di download nel player non apre più il dialogo; il brano parte subito e l'avanzamento resta nella coda dei download e nella notifica.

### Fixed
- **Testi non trovati**: un brano assente su LRCLIB (404/503) interrompeva l'intera ricerca prima di provare NetEase e KuGou, quindi i testi "non si trovavano" anche quando esistevano. Ora l'errore viene ignorato e la ricerca prosegue con le altre sorgenti.
- **Download non riuscito** sui brani che le fonti gratuite non coprono: ora c'è il fallback YouTube, con supporto ai file `.webm` e verifica della durata reale del file scaricato.

## [2.0.1] - 2026-10-27

### Changed
- **Sincronizzazione dei testi con l'AI**: con una chiave Gemini, "Sincronizza" manda a Gemini il testo e l'audio del brano e riceve il file .mblrc con ogni riga e ogni parola al suo tempo. Funziona con i brani della libreria, i video del telefono (anche quando i testi arrivano da internet) e i brani online. Il modello sul telefono si usa solo senza chiave.
- **Testi parola per parola**: ogni parola si accende tutta insieme, con il bagliore, quando viene cantata. Non c'è più il riempimento lettera per lettera.

### Fixed
- **Brani online in errore**:
  - L'errore dice il motivo: verifica anti-bot di YouTube, limiti di età, paese, video rimosso, nessuna connessione.
  - Un link scaduto o rifiutato viene richiesto di nuovo e riprovato.
  - Se un caricamento non si può ascoltare, si provano gli altri risultati.
  - Un brano online che non parte passa al successivo invece di fermare la musica.

## [2.0.0] - 2026-10-27

### Added
- **Redesign "Fluido"**: tutta l'app nello stile delle app Google di oggi (Material 3 Expressive). Blu Google su superfici pulite, Google Sans Flex con titoli arrotondati, bottoni a pill che cambiano forma quando li tocchi. Le animazioni seguono due regole: gli elementi entrano veloci e rallentano dolcemente, ed escono con una dissolvenza rapida. Il titolo e l'artista nel player cambiano con un'animazione fluida. In Impostazioni > Aspetto puoi tornare ai colori Material You dello sfondo.
- **Scheda Online**, sullo stile di YouTube Music e Spotify:
  - "Selezione rapida" in griglia 3×3, costruita da ciò che ascolti.
  - Le sezioni della home di YouTube Music.
  - "Altro da" i tuoi artisti preferiti.
  - "Cover e remix" delle canzoni che ascolti di più.
  - I mood (Energia, Relax, Allenamento…).
  - Ricerca di brani, album, playlist e artisti, con le loro pagine.
  - Tutto si ascolta per intero, con "Riproduci dopo" e "Aggiungi alla coda".
- **Video musicali dal telefono** (Online > Video musicali):
  - Aggiungi i video che hai sul telefono; artista e titolo vengono letti dal nome del file ("Shiva - Dai Dai (Official Video)" → Shiva, Dai Dai).
  - Il video si ascolta come un brano, con l'immagine nel player.
  - I testi sincronizzati vengono cercati online; se non si trovano e hai una chiave Gemini, vengono scritti ascoltando l'audio del video.
  - Se nella libreria hai la canzone con lo stesso titolo, il player mostra il tuo video anche quando ascolti quella.

### Changed
- **Testi "glowing"**: parole in maiuscolo e in grassetto. Le parole cantate si accendono con un bagliore bianco; le parole che contano sono più grandi e quelle di collegamento più piccole, come nei video lyric di CapCut. Ogni parola che si accende sale con una molla.

### Fixed
- Il crossfade smetteva di funzionare dopo la chiusura del player dalla notifica.
- "Riproduci N volte" non si poteva annullare.
- Il timer "fine brano" non fermava la musica con "ripeti uno" attivo.
- Il timer a tempo restava indicato come attivo dopo essere scattato.
- Le anteprime online potevano restare in riproduzione senza poterle fermare.
- I testi .lrc con tempi senza centesimi ("[01:02]") venivano letti come testo semplice.
- Le righe con parentesi ("Ooh baby [x2]") perdevano la parte iniziale.
- Una cache dei testi danneggiata bloccava per sempre i testi di quel brano.
- Le playlist con più di 999 brani non si aprivano su Android 11.
- La verifica delle chiavi AI lasciava aperte le connessioni.

## [1.10.0] - 2026-10-27

### Changed
- **Testi più leggibili**: le parole accese usano il colore della copertina se si legge bene, altrimenti bianco (tema scuro) o quasi nero (tema chiaro); prima ricadevano sul grigio e tutta la schermata diventava grigia. Tutte le righe usano lo stesso colore a opacità diverse: parole ancora da cantare della riga attiva al 35%, righe passate al 50%, righe in arrivo al 32%.
- **Riempimento karaoke ridisegnato**: ogni parola è un solo testo colorato mentre viene cantata, con un bordo morbido; prima due testi sovrapposti si disallineavano ("Liv" acceso spostato rispetto a "orno"). La parola in corso si solleva leggermente invece di ingrandirsi.
- **Testata dei testi**: l'etichetta "Parola per parola" sta sotto il titolo invece di coprirlo, e le righe non passano più sotto la testata. I controlli di sincronizzazione si chiudono da soli dopo 5 secondi.
- **Impostazioni AI riorganizzate**:
  - "Cosa sbloccano le tue chiavi": ogni funzione AI con il suo stato (pronta, serve una chiave, serve la chiave Gemini).
  - Link "Ottieni una chiave" per ogni servizio.
  - Se il servizio scelto non è Gemini, c'è un campo a parte, facoltativo, per la chiave Gemini: solo Gemini può ascoltare l'audio per scrivere i testi dalla canzone.
  - Stile delle risposte con tre scelte (Preciso, Bilanciato, Creativo); prompt di sistema, parametri numerici e dati dei brani sono in "Impostazioni avanzate".

### Fixed
- **Chiave API**: i pulsanti AI di Libreria e playlist comparivano solo con una chiave per il servizio selezionato, anche se l'AI usa in automatico qualsiasi servizio con una chiave salvata. Ora compaiono appena c'è una chiave.
- **Errori della chiave**: al posto del messaggio grezzo del server, una spiegazione chiara (chiave rifiutata, quota o credito finiti, nessuna connessione, servizio non disponibile) con i dettagli sotto.

## [1.9.0] - 2026-10-26

### Added
- **Ricerca online su YouTube Music**: i risultati vengono direttamente da YouTube Music e ogni brano suona per intero, esattamente quello che vedi (prima la ricerca era su Deezer e il brano veniva "indovinato" su YouTube al momento di suonare, a volte sbagliando). Deezer resta come riserva se YouTube non risponde.
- **Parole che si illuminano come nel karaoke**: il colore riempie ogni parola mentre viene cantata, e le parole non ancora cantate della riga attiva sono più tenui, così si legge bene dove sei.

### Changed
- **Sincronizzazione dall'audio più affidabile**: i tempi delle righe del testo restano quelli originali; dei tempi sentiti da Whisper si tengono solo quelli vicini alla stima, altrimenti si usa la stima. Serve riconoscere almeno il 60% delle parole (prima il 35%), altrimenti il testo non viene toccato.
- **Statistiche in stile Scena**: l'ultima schermata ancora nel vecchio stile ora è coerente con Home, Libreria e Wrapped (sfondo dal tema dinamico, card con le forme Scena, titoli larghi, un solo accento, chip che si invertono, pulsante di aggiornamento semplice).

### Fixed
- **Testi**: "Testi forniti da LRCLIB" compariva anche per i testi presi da NetEase o da AMLL TTML DB. Ora viene indicata la fonte vera (anche per i testi in cache e per quelli non sincronizzati), e nessuna se la fonte non è nota.
- Il dialogo di download (YTDLnis) era solo in italiano; i parametri di generazione AI e i dati dei brani nelle impostazioni, "Ultima sincronizzazione" di Navidrome e il sottotitolo della schermata Informazioni erano solo in inglese. Ora sono tradotti in inglese e italiano.

## [1.8.0] - 2026-10-26

### Changed
- **Wrapped rifatto da zero**: ora racconta davvero il tuo ascolto, solo con dati reali.
  - Scegli il periodo: quest'anno, questo mese o da sempre (prima diceva "quest'anno" ma mostrava tutto lo storico).
  - Storie: minuti di ascolto, il tuo brano, artista e album, **quando ascolti** (grafico per ora del giorno e giorno con più musica), il tuo **profilo d'ascolto** (Superfan, Anima notturna, Spirito esploratore… scelto dai numeri, non dal genere, e con il dato che lo giustifica) e un riepilogo finale.
  - Il riepilogo si **condivide come immagine** formato storia, e "Ascolta i tuoi top" fa partire i brani più ascoltati del periodo.
  - Colori dal tema dinamico (stile Scena) al posto dei gradienti fissi; testi in inglese e italiano; barra di avanzamento che si riempie col tempo, tocca per avanzare, tieni premuto per fermare, scorri per cambiare storia. Niente avanzamento automatico con TalkBack o con le animazioni disattivate.
- **Shuffle bilanciato**: i brani dello stesso artista vengono distanziati invece di capitare di fila.
- **Testi**: se scorri il testo per leggere, lo scorrimento automatico aspetta invece di riportarti indietro alla riga successiva; riprende da solo dopo 4 secondi, oppure subito con "Torna alla riga attuale", toccando una riga o spostando la barra.
- **Testi**: puntini animati durante l'intro e le lunghe parti strumentali, così lo schermo non sembra bloccato.
- **Video musicale**: si sincronizza in modo fluido (piccole differenze corrette accelerando o rallentando di poco il video, salti solo quando serve), segue la velocità di riproduzione e, a musica in pausa, segue la barra mentre la sposti. Mentre carica mostra la copertina.
- **Video musicale**: sceglie meglio il video ufficiale (titolo, artista, durata; scarta video con il testo, live, cover… se non li hai chiesti).
- La CI di debug esegue anche i test unitari.

### Fixed
- **Shuffle**: attivandolo a metà coda alcuni brani finivano "alle spalle" del brano corrente e non venivano mai suonati. Ora resta la cronologia e si mescolano solo i brani in arrivo.
- **Shuffle persistente**: toccando un brano, quelli mescolati prima di lui venivano saltati. Ora il brano toccato parte per primo, poi tutti gli altri.
- **Shuffle**: disattivandolo sparivano i brani aggiunti alla coda nel frattempo (e tornavano quelli rimossi).
- **Shuffle**: disattivandolo su code lunghe la canzone poteva ripartire con un salto.
- **Video musicale**: continuava a riprodursi (e a scaricare dati) con l'app in background o con il player chiuso.
- **Video musicale**: in caso di errore restava un riquadro nero; ora compare "Riprova".
- **Testi**: i puntini delle pause ignoravano la correzione di sincronizzazione; le righe vuote sparivano dopo 8 secondi anche se la pausa continuava.
- **Wrapped**: la barra di navigazione e il mini player coprivano il fondo delle storie.
- Dialogo degli aggiornamenti, barra "nuovo brano", suggerimenti della ricerca e banner del Wrapped nelle statistiche mostravano testi in italiano in tutte le lingue; aggiunte le traduzioni italiane mancanti di licenze e note.

## [1.7.1] - 2026-10-23

### Fixed
- **"Unexpected runtime error" all'avvio di ogni canzone**: il nuovo processore audio del karaoke a volte riceveva in ingresso il proprio buffer d'uscita e la riproduzione si fermava. Ora suonano sia i brani sul telefono sia quelli online (verificato su emulatore).

## [1.7.0] - 2026-10-23

### Added
- **Canzoni online complete**: i risultati della ricerca online ora suonano per intero in streaming da YouTube Music (con NewPipeExtractor), non più l'anteprima di 30 secondi. Solo streaming: nessun nuovo tasto di download.
- **Radio online**: quando un brano online è l'ultimo della coda, l'app aggiunge i brani che YouTube propone dopo, così l'ascolto continua.
- **Video musicale**: nel player il tasto "Video" accanto a "Testo" sostituisce la copertina con il video ufficiale del brano, muto e sincronizzato con l'audio (che continua in background e sulla schermata di blocco).
- **Testo scritto dall'audio**: per le canzoni senza testo, Whisper ascolta il brano sul telefono e scrive il testo con i tempi di ogni parola. Anche il pulsante della finestra "nessun testo trovato" ora fa questo.
- **Gemini che ascolta la canzone** (opzionale, con la tua chiave): scrive il testo parola per parola e riconosce chi canta nei duetti. Invia l'audio a Google; file fino a 14 MB.
- **Formato `.mblrc`**: file JSON dei testi con i tempi di ogni parola, traduzione, romanizzazione e cantante di ogni riga. Si importa, si salva accanto al brano dal menu dei testi, e nei tag del file resta una copia LRC leggibile dagli altri player.
- **Duetti**: le righe dei due cantanti stanno ai due lati dello schermo, quelle cantate insieme al centro.
- **Modalità karaoke**: toglie la voce principale dal brano mantenendo i bassi. Interruttore nel menu dei testi. Mentre è attiva, l'audio offload hardware è sospeso (consuma un po' più di batteria).
- **Condividi un verso**: tieni premuta una riga del testo per condividerla come immagine con copertina, titolo e artista.
- **Stile copertina "Disco"** (Impostazioni → stile carosello): la copertina a forma di disco gira mentre il brano suona, dentro un anello che mostra l'avanzamento.

### Changed
- Sezioni della Home (Ascoltati di recente, Daily Mix, Statistiche) con titoli e colori Scena.
- L'APK include NewPipeExtractor, che è GPL-3.0: l'APK distribuito ricade quindi sotto la GPL-3.0 (vedi THIRD_PARTY_NOTICES.md).

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
