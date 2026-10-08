#!/usr/bin/env python3
"""
MusicBabe Downloader Script
===========================
Scarica brani musicali direttamente in alta qualità MP3 (320kbps) con metadati e cover art,
salvandoli nella cartella Musica/MusicBabe e attivando istantaneamente la scansione multimediale
di Android, così MusicBabe li riconosce subito!

Funzionalità:
- Ricerca automatica di qualsiasi canzone per titolo/artista o download da URL (YouTube, SoundCloud, ecc.)
- Conversione automatica in MP3 ad alta definizione con tag ID3 e copertina integrata
- Rileva automaticamente se eseguito su Android (Termux) o PC
- Se eseguito su PC con telefono collegato via USB (ADB), trasferisce il brano e invia il comando di scansione
- Invia il broadcast MediaScanner ad Android per notificare MusicBabe all'istante
"""

import os
import sys
import argparse
import subprocess
import shutil
from pathlib import Path

def print_banner():
    banner = r"""
  __  __           _      ____        _          
 |  \/  |_   _ ___(_) ___| __ )  __ _| |__   ___ 
 | |\/| | | | / __| |/ __|  _ \ / _` | '_ \ / _ \
 | |  | | |_| \__ \ | (__| |_) | (_| | |_) |  __/
 |_|  |_|\__,_|___/_|\___|____/ \__,_|_.__/ \___|
         ♪ Downloader Musicale per MusicBabe ♪
    """
    print(banner)

def check_dependencies():
    """Verifica e installa yt-dlp se mancante."""
    try:
        import yt_dlp
    except ImportError:
        print("[-] Modulo 'yt-dlp' non trovato. Installazione automatica in corso...")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "--upgrade", "yt-dlp"])
            import yt_dlp
            print("[+] 'yt-dlp' installato con successo!")
        except Exception as e:
            print(f"[!] Errore nell'installazione automatica di yt-dlp: {e}")
            print("[!] Esegui manualmente: pip install yt-dlp")
            sys.exit(1)

    # Verifica ffmpeg (consigliato per la conversione audio e inserimento thumbnail)
    if not shutil.which("ffmpeg"):
        print("[!] ATTENZIONE: 'ffmpeg' non sembra installato nel PATH.")
        if is_android():
            print("[!] Su Termux puoi installarlo con: pkg install ffmpeg")
        else:
            print("[!] Su Ubuntu/Debian puoi installarlo con: sudo apt install ffmpeg")
            print("[!] Su macOS con: brew install ffmpeg")
        print("[*] Il download continuerà con i migliori codec audio disponibili.\n")

def is_android():
    """Verifica se lo script è in esecuzione direttamente su Android (es. Termux)."""
    return os.path.exists("/storage/emulated/0") or "ANDROID_ROOT" in os.environ

def get_default_output_dir():
    """Restituisce la cartella di destinazione appropriata."""
    if is_android():
        music_babe_path = Path("/storage/emulated/0/Music/MusicBabe")
    else:
        music_babe_path = Path.home() / "Music" / "MusicBabe"
    
    music_babe_path.mkdir(parents=True, exist_ok=True)
    return music_babe_path

def trigger_android_media_scanner(file_path):
    """Invia broadcast al MediaScanner di Android per indicizzare subito il file."""
    if is_android():
        try:
            cmd = ["am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file://{file_path}"]
            subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print(f"[✓] MediaScanner di Android notificato: {file_path}")
            print("[✓] MusicBabe mostrerà la notifica: 'Il brano è stato aggiunto. Riproduci ora'")
        except Exception as e:
            print(f"[!] Impossibile inviare broadcast am su Android: {e}")

def check_adb_device():
    """Controlla se un dispositivo Android è connesso via ADB."""
    if shutil.which("adb"):
        try:
            res = subprocess.run(["adb", "devices"], stdout=subprocess.PIPE, text=True)
            lines = [l.strip() for l in res.stdout.strip().split("\n")[1:] if l.strip() and not l.startswith("*")]
            if lines:
                device_id = lines[0].split()[0]
                return device_id
        except Exception:
            pass
    return None

def push_to_android_adb(local_file_path):
    """Copia il file scaricato dal PC al dispositivo Android tramite ADB e aggiorna MediaStore."""
    device_id = check_adb_device()
    if not device_id:
        return False

    dest_folder = "/storage/emulated/0/Music/MusicBabe"
    remote_file = f"{dest_folder}/{os.path.basename(local_file_path)}"
    
    print(f"\n[*] Dispositivo Android rilevato via ADB ({device_id})!")
    choice = input(f"[?] Vuoi trasferire il brano direttamente su MusicBabe nel telefono? [S/n]: ").strip().lower()
    if choice in ("", "s", "si", "y", "yes"):
        print(f"[*] Trasferimento in {remote_file}...")
        try:
            subprocess.run(["adb", "shell", f"mkdir -p '{dest_folder}'"], check=True)
            subprocess.run(["adb", "push", str(local_file_path), remote_file], check=True)
            print("[+] File trasferito con successo sul tuo smartphone!")
            
            # Scansione MediaStore via ADB
            subprocess.run([
                "adb", "shell", "am", "broadcast",
                "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                "-d", f"file://{remote_file}"
            ], check=True)
            print("[✓] MediaScanner su Android notificato con successo!")
            print("[✓] Apri MusicBabe sul telefono per ascoltarlo subito!")
            return True
        except Exception as e:
            print(f"[!] Errore durante il trasferimento ADB: {e}")
    return False

def download_music(query, output_dir=None):
    """Scarica il brano utilizzando yt-dlp."""
    import yt_dlp

    if not output_dir:
        output_dir = get_default_output_dir()
    else:
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

    print(f"[*] Cartella di destinazione: {output_dir}")

    # Se query è un testo e non un URL, usa ytsearch
    is_url = query.startswith("http://") or query.startswith("https://")
    search_target = query if is_url else f"ytsearch1:{query}"

    out_template = str(output_dir / "%(artist,creator,uploader)s - %(title)s.%(ext)s")

    ydl_opts = {
        'format': 'bestaudio/best',
        'outtmpl': out_template,
        'noplaylist': True,
        'quiet': False,
        'no_warnings': False,
        'postprocessors': [
            {
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '320',
            },
            {
                'key': 'FFmpegMetadata',
                'add_metadata': True,
            },
            {
                'key': 'EmbedThumbnail',
            }
        ],
        'writethumbnail': True,
    }

    print(f"\n[*] Ricerca e download di '{query}' in corso...")
    downloaded_file = None

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(search_target, download=True)
            if 'entries' in info and info['entries']:
                entry = info['entries'][0]
            else:
                entry = info

            # Trova il file MP3 generato
            title = entry.get('title', 'Unknown')
            uploader = entry.get('artist') or entry.get('creator') or entry.get('uploader') or 'MusicBabe'
            print(f"\n[+] Brano: {title}")
            print(f"[+] Artista: {uploader}")

            # Identifica il percorso del file
            for file in output_dir.glob("*.mp3"):
                if file.stat().st_mtime > (os.times().elapsed if hasattr(os.times(), 'elapsed') else 0):
                    downloaded_file = file
            
            # Fallback ricerca file recente
            if not downloaded_file:
                mp3_files = sorted(output_dir.glob("*.mp3"), key=lambda f: f.stat().st_mtime, reverse=True)
                if mp3_files:
                    downloaded_file = mp3_files[0]

        if downloaded_file and downloaded_file.exists():
            print(f"\n[✓] Salvato con successo: {downloaded_file}")
            
            # Se siamo su Android, notifica subito il MediaScanner
            if is_android():
                trigger_android_media_scanner(downloaded_file)
            else:
                # Se siamo su PC, prova a inviarlo al dispositivo collegato via ADB
                push_to_android_adb(downloaded_file)

            print("\n🎵 Operazione completata! Il brano è pronto per essere riprodotto in MusicBabe.")
            return True
        else:
            print("[!] Download terminato, verifica la cartella per il file salvato.")
            return True

    except Exception as e:
        print(f"\n[!] Errore durante il download: {e}")
        return False

def main():
    print_banner()
    check_dependencies()

    parser = argparse.ArgumentParser(description="Scarica musica in alta qualità per MusicBabe")
    parser.add_argument("query", nargs="?", help="Titolo del brano, 'Artista - Canzone', o URL da scaricare")
    parser.add_argument("-o", "--output", help="Cartella di destinazione personalizzata")
    args = parser.parse_args()

    query = args.query
    if not query:
        query = input("Inserisci il titolo della canzone o l'URL da scaricare: ").strip()

    if not query:
        print("[!] Nessun brano specificato. Esci.")
        sys.exit(0)

    success = download_music(query, args.output)
    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()
