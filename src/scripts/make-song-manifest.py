#!/usr/bin/env python3
"""Builds manifest.json for the Songs shelf (see docs/SONG_CATALOGUE.md).

Usage:
  make-song-manifest.py SONGS_DIR BASE_URL > manifest.json

SONGS_DIR holds the audio files and one songs.json describing them:
  [{"file": "ekla-cholo.mp3", "title": "একলা চলো", "artist": "…", "language": "bn",
    "collection": "Folk", "license": "CC-BY-4.0", "author": "…"}]
BASE_URL is where the files will be served over https, with no trailing slash.
The id, size, checksum and (when ffprobe is installed) length are filled in here.
"""
import hashlib, json, re, shutil, subprocess, sys
from pathlib import Path

LICENCES = {"CC0", "CC0-1.0", "PUBLIC-DOMAIN", "PDM-1.0", "CC-BY-4.0", "CC-BY-3.0", "CC-BY-SA-4.0", "CC-BY-SA-3.0"}

def duration(path):
    if not shutil.which("ffprobe"):
        return 0
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "default=nw=1:nk=1", str(path)], capture_output=True, text=True)
    try:
        return round(float(out.stdout.strip()), 1)
    except ValueError:
        return 0

def main():
    if len(sys.argv) != 3 or not sys.argv[2].startswith("https://"):
        sys.exit(__doc__)
    folder, base = Path(sys.argv[1]), sys.argv[2].rstrip("/")
    entries = json.loads((folder / "songs.json").read_text(encoding="utf-8"))
    songs, seen = [], set()
    for e in entries:
        path = folder / e["file"]
        lic = e.get("license", "").upper()
        if lic not in LICENCES:
            sys.exit(f"{e['file']}: licence {lic!r} is not one the app accepts")
        if not path.is_file():
            sys.exit(f"{e['file']}: file not found")
        sid = re.sub(r"[^a-z0-9]+", "-", path.stem.lower()).strip("-")[:80] or "song"
        if sid in seen:
            sys.exit(f"{e['file']}: id {sid!r} is used twice; rename the file")
        seen.add(sid)
        data = path.read_bytes()
        songs.append({
            "id": sid, "title": e["title"], "artist": e.get("artist", ""),
            "language": e.get("language", ""), "collection": e.get("collection", ""),
            "url": f"{base}/{e['file']}", "duration": duration(path), "bytes": len(data),
            "license": lic, "author": e.get("author", ""), "sha256": hashlib.sha256(data).hexdigest(),
        })
    print(json.dumps({"version": 1, "songs": songs}, ensure_ascii=False, indent=2))

main()
