# Istruzioni Tecniche e Roadmap di Correzione per GLM 5.3
**Progetto**: MusicBabe (PixelPlayer - `com.theveloper.pixelplay`)  
**Target Model**: GLM 5.3 (in esecuzione tramite Cline)  
**Ambiente**: Kotlin 2.x | Java 21 (`JAVA_HOME=/home/owais/.jdks/jdk-21.0.12.1+1`) | Jetpack Compose (Material 3) | Media3 ExoPlayer | Dagger Hilt | Room DB

---

## 1. REGOLE OPERATIVE INDEROGABILI PER GLM 5.3
Prima di scrivere o modificare qualsiasi riga di codice, rispetta queste regole:

1. **Zero Fake Stubs / Mock Rule (Nessun codice fittizio)**:
   - NON creare classi finte o logiche simulate basate su sleep, stime lineari fittizie o stub (`// TODO`, `// implement here`, `Vocal phrase [1]`).
   - Il codice deve essere al 100% reale, manutenibile, privo di allucinazioni e compilabile.
   - Non duplicare servizi core già presenti: integra chirurgicamente nelle classi esistenti.
2. **Nessuna Dipendenza Fantasma**:
   - Utilizza SOLO librerie già dichiarate in `app/build.gradle.kts`.
   - NON importare runtime C++, ONNX, JNI, Whisper o Vosk non censiti in Gradle.
3. **Modifiche Chirurgiche e Token-Efficient**:
   - NON riscrivere file interi di oltre 1000 righe (`MusicService.kt`, `DualPlayerEngine.kt`, `PlayerScreen.kt`).
   - Utilizza diff puntuali (search-and-replace / regex matching).
4. **Verifica della Compilazione Obbligatoria**:
   - Dopo OGNI modifica, esegui il comando di verifica:
     ```bash
     JAVA_HOME=/home/owais/.jdks/jdk-21.0.12.1+1 ./gradlew compileDebugKotlin --quiet --console=plain
     ```
   - Esegui i test unitari:
     ```bash
     JAVA_HOME=/home/owais/.jdks/jdk-21.0.12.1+1 ./gradlew testDebugUnitTest --quiet --console=plain
     ```

---

## 2. AUDIT COMPLETO DELLE CRITICITÀ DA SISTEMARE

---

### PRIORITÀ 1: Consolidamento e Test della Cleartext Network Policy (Sicurezza)
**Stato attuale**:
Nel branch di lavoro sono presenti modifiche uncommitted e la classe `CleartextPolicy.kt` in `app/src/main/java/com/theveloper/pixelplay/data/network/CleartextPolicy.kt`.
L'app permette comunicazioni HTTP solo verso host locali/LAN (Navidrome, Jellyfin), mentre deve bloccare HTTP in chiaro verso host pubblici non fidati.

**Cosa sistemare**:
1. **Chiamate non protette dirette via `java.net.URL.openConnection()`**:
   Attualmente, `CleartextPolicy` è integrato nei client OkHttp e Media3, ma diverse classi usano ancora direttamente `URL.openConnection()` senza controllo della policy:
   - `SongDownloadManager.kt` (righe 251, 265, 670)
   - `AppUpdateManager.kt` (righe 65, 150, 164)
   - `GitHubContributorService.kt` (riga 34)
   - `GitHubAnnouncementPropertiesService.kt` (riga 48)
   - `InAppYtdlnisInstaller.kt` (righe 86, 144, 158)
   - `GeminiModelService.kt` (riga 52)
   *Azione*: Integra `CleartextPolicy.isAllowed(url)` prima di eseguire `openConnection()`, oppure converti le chiamate verso l'`OkHttpClient` condiviso configurato con `enforceCleartextPolicy()`.
2. **Scrittura Test Unitari per CleartextPolicy**:
   - File da creare: `app/src/test/java/com/theveloper/pixelplay/data/network/CleartextPolicyTest.kt`.
   - Casi di test da verificare:
     - Indirizzi LAN / loopback consentiti in HTTP: `127.0.0.1`, `localhost`, `192.168.1.50`, `10.0.0.2`, `172.16.0.5`, `*.local`.
     - Host pubblici bloccati in HTTP: `http://example.com`, `http://api.deezer.com`.
     - Qualsiasi URL HTTPS sempre consentito: `https://example.com`, `https://api.github.com`.
     - `CleartextGuardInterceptor` lancia correttamente `CleartextNotPermittedException`.

---

### PRIORITÀ 2: Pulizia Dead Code e Finto ASR/Allineatore nei Testi (Architettura)
**Stato attuale**:
Nel package `com.theveloper.pixelplay.data.lyrics.engine` sono presenti:
- `AudioStemSeparatorImpl.kt`
- `ForcedAlignerImpl.kt`
- `LyricsEngineRepository.kt`
- `RobustSongDownloader.kt` (in `data/downloader`)

**Il problema**:
1. `ForcedAlignerImpl.kt` dichiara nel docstring di eseguire *"trascrizione ASR (Whisper) e allineamento forzato CTC (Wav2Vec2)"*, ma in realtà:
   - `alignKnownTextToAudio` distribuisce le parole linearmente in base alla lunghezza dei caratteri diviso la durata.
   - `generateAsrVocalSegments` genera testo fittizio: `"Vocal phrase [1]"`, `"Vocal phrase [2]"`.
   Questo viola esplicitamente la regola *Zero Fake Stubs* stabilita nel progetto.
2. Queste classi NON sono legate nei moduli Hilt DI e NON sono utilizzate né dalla UI (`LyricsStateHolder`), né dai servizi (`MusicService`).
3. Il vero motore testi funzionante e integrato è `LyricsRepositoryImpl.kt` (che implementa la cascata a 4 livelli: Tag ID3 integrati -> File .lrc locale -> LrcLib -> NetEase).

**Cosa sistemare**:
- Rimuovere o bonificare questo ramo duplicato:
  - Se non ci sono librerie ASR native nel gradle, non fingere che esista Whisper/Wav2Vec2.
  - Rimuovere `ForcedAlignerImpl`, `AudioStemSeparatorImpl`, `LyricsEngineRepository` e `RobustSongDownloader` o rifattorizzare le funzioni utili direttamente all'interno di `LyricsRepositoryImpl.kt` e `SongDownloadManager.kt`.
  - Assicurarsi che nessuna dipendenza non utilizzata appesantisca il bytecode.

---

### PRIORITÀ 3: Scoped Storage e Descrittori File in SongDownloadManager (Crash Android 11+)
**Stato attuale**:
In `app/src/main/java/com/theveloper/pixelplay/data/downloader/SongDownloadManager.kt`, riga 605:
```kotlin
val audioFile = File(filePath)
val pfd: ParcelFileDescriptor = if (audioFile.exists()) {
    ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE)
} else {
    contentUri?.let { uri ->
        runCatching { context.contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
    } ?: return
}
```

**Il problema**:
Su Android 11+ (API 30+), aprire direttamente un `java.io.File` con `ParcelFileDescriptor.MODE_READ_WRITE` nella directory pubblica `Music/` solleva un'eccezione di permessi (`EACCES: Permission denied`), anche se il file esiste nel filesystem, perché Scoped Storage richiede l'uso dell'URI MediaStore.

**Cosa sistemare**:
1. Invertire la priorità su Android Q+: se `contentUri` è disponibile, aprire **sempre** tramite `context.contentResolver.openFileDescriptor(contentUri, "rw")`. Usare il fallback su `File` solo se `contentUri` è nullo (pre-Android 10).
2. **Prevenzione leak di stream e connessioni di rete**:
   In `performDownload` (righe 251-398): racchiudere lo scaricamento in un blocco `try-finally` o usare `.use {}` per garantire la chiusura di `inputStream` e `connection.disconnect()`, anche se si verificano errori a metà trasferimento o il download viene annullato dall'utente.

---

### PRIORITÀ 4: Risoluzione Warning Risorse Stringa AAPT2 (Localizzazione)
**Stato attuale**:
Durante `processDebugResources`, AAPT2 emette decine di warning:
`warn: removing resource com.theveloper.pixelplay.debug:string/... without required default value.`

**Il problema**:
Alcune stringhe esistono nelle cartelle tradotte (es. `values-ar`, `values-es`, `values-it`), ma mancano nel file base predefinito `app/src/main/res/values/strings.xml` o `strings_components.xml`. Esempi:
- `song_field_replaygain_album_db`
- `sync_complete`
- `tab_albums`
- `toast_added_to_queue`

**Cosa sistemare**:
Aggiungere le chiavi mancanti con il testo predefinito inglese nei file `values/strings.xml` o `values/strings_components.xml`, eliminando tutti i warning di build e prevenendo crash su configurazioni di lingua non previste.

---

### PRIORITÀ 5: Ciclo di Vita e Memory Leak in MusicService e DualPlayerEngine
**Stato attuale**:
`MusicService.kt` ha oltre 2400 righe e gestisce ExoPlayer, Android Auto, Wear OS, Cast e Widget.
`DualPlayerEngine.kt` ha oltre 1100 righe e gestisce crossfade, audio offload e pipeline di decodifica.

**Cosa sistemare**:
1. Verificare che all'arresto del servizio (`onDestroy`):
   - Tutti i listener ExoPlayer vengano deregistrati.
   - Tutti i `Job` coroutine attivi nello `serviceScope` vengano cancellati.
   - I receiver audio (noisy receiver, focus listeners) vengano rilasciati senza leak.
2. Prevenire `ForegroundServiceStartNotAllowedException` su Android 12+ gestendo accuratamente le notifiche e l'avvio in background quando l'app viene chiusa dal selettore app.

---

### PRIORITÀ 6: Ottimizzazione Ricomposizioni Jetpack Compose (120 FPS)
**Stato attuale**:
Nei file di presentazione:
- `PlayerScreen.kt` (2163 righe)
- `QueueScreen.kt` (505 righe)
- `LyricsScreen.kt`

**Cosa sistemare**:
1. Evitare di leggere direttamente `playbackPosition` o `progress` ad alto livello nelle Composable che contengono l'intera gerarchia dello schermo: questo causa il ricalcolo dell'intero albero ad ogni frame (60-120 volte al secondo).
2. Spostare la lettura dei timestamp nei widget foglia (la barra di avanzamento / slider / riga del testo corrente) o passare `() -> Float` per isolare il disegno.
3. Usare `ImmutableList` (`kotlinx.collections.immutable`) per i parametri della coda musicale nelle Composable per garantire la stabilità di Compose Compiler.

---

### PRIORITÀ 7: Ripristino e Completamento Test Unitari
**File target**:
- `app/src/test/java/com/theveloper/pixelplay/data/database/MusicDaoTest.kt`
- `app/src/test/java/com/theveloper/pixelplay/data/repository/MusicRepositoryImplTest.kt`

**Cosa sistemare**:
1. De-commentare e aggiornare i metodi di test disattivati in `MusicDaoTest.kt`.
2. Completare i test su cronologia e playlist in `MusicRepositoryImplTest.kt`.
3. Verificare che l'intera suite passi con:
   ```bash
   JAVA_HOME=/home/owais/.jdks/jdk-21.0.12.1+1 ./gradlew testDebugUnitTest --quiet --console=plain
   ```

---

## 3. CHECKLIST DI VERIFICA FINALE PER GLM 5.3
Quando completi un task:
- [ ] Nessun file contiene metodi stub fake o calcoli lineari ingannevoli.
- [ ] Non sono state aggiunte dipendenze esterne in `build.gradle.kts`.
- [ ] La compilazione ha esito positivo:
  ```bash
  JAVA_HOME=/home/owais/.jdks/jdk-21.0.12.1+1 ./gradlew compileDebugKotlin --quiet --console=plain
  ```
- [ ] I test unitari hanno esito positivo:
  ```bash
  JAVA_HOME=/home/owais/.jdks/jdk-21.0.12.1+1 ./gradlew testDebugUnitTest --quiet --console=plain
  ```
- [ ] Il diff `git diff` contiene solo modifiche chirurgiche e mirate.
