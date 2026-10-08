# MusicBabe Downloader (Python CLI)

Uno script Python potente e leggero basato su `yt-dlp` per scaricare brani musicali in alta definizione MP3 (320kbps) con copertine e metadati ID3, salvandoli direttamente nella cartella di MusicBabe e attivando il **MediaScanner** di Android.

---

## 🚀 Requisiti

- Python 3.8+
- `yt-dlp` (lo script lo installa automaticamente se manca, oppure `pip install yt-dlp`)
- `ffmpeg` (consigliato per la conversione e l'incorporamento della copertina)

---

## 📲 Utilizzo su Android (Termux)

Se usi il telefono con Termux:
1. Installa i requisiti su Termux:
   ```bash
   pkg update
   pkg install python ffmpeg
   pip install yt-dlp
   termux-setup-storage
   ```
2. Esegui lo script:
   ```bash
   python scripts/musicbabe_downloader.py "Nome Canzone o Artista"
   ```
   oppure incolla un URL:
   ```bash
   python scripts/musicbabe_downloader.py "https://www.youtube.com/watch?v=..."
   ```
3. Il brano viene salvato in `/storage/emulated/0/Music/MusicBabe/` e il MediaScanner di Android viene immediatamente notificato.
4. Apri MusicBabe: apparirà la notifica interna:
   **"Il brano è stato aggiunto. Riproduci ora"** con il pulsante per ascoltarlo subito!

---

## 💻 Utilizzo su Computer (PC / Mac / Linux)

1. Installa `yt-dlp`:
   ```bash
   pip install yt-dlp
   ```
2. Esegui lo script:
   ```bash
   python3 scripts/musicbabe_downloader.py "Queen Bohemian Rhapsody"
   ```
3. **Integrazione ADB automatica**:
   Se colleghi il tuo smartphone Android al computer con il debug USB abilitato:
   - Lo script rileva il telefono connesso
   - Ti chiede se desideri inviare il brano direttamente su MusicBabe sul telefono
   - Trasferisce il file via `adb push` e invia il comando di scansione a MusicBabe sul dispositivo!
