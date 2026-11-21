# Song catalogue (Sounds → Songs)

The Songs shelf lists songs from a `manifest.json` you host. Nothing is bundled in the app.
The app streams a song to preview it, and downloads it (checked against its `sha256`) only when
it is put on the timeline.

## Only songs you may redistribute

The app accepts these `license` values and hides every other entry: `CC0`, `CC0-1.0`,
`PUBLIC-DOMAIN`, `PDM-1.0`, `CC-BY-4.0`, `CC-BY-3.0`, `CC-BY-SA-4.0`, `CC-BY-SA-3.0`.
For CC-BY the app shows the author and licence on each row, which is the credit the licence asks for.
Commercial film and band songs are copyrighted and must not be hosted here without the owner's permission.
Phone music (Sounds → Device) is the user's own, played from their phone.

## Set up

1. Make a public GitHub repository, for example `capcraft-songs`, and put the audio files in it.
2. Write `songs.json` next to them (format in `src/scripts/make-song-manifest.py`).
3. Run `python3 src/scripts/make-song-manifest.py ./capcraft-songs https://raw.githubusercontent.com/YOU/capcraft-songs/main > capcraft-songs/manifest.json`.
4. Commit and push.
5. In `SongCatalogue.kt`, set `MANIFEST_URL` to `https://raw.githubusercontent.com/YOU/capcraft-songs/main/manifest.json`.

`language` is `bn`, `hi` or `en`. `collection` is any shelf name (Folk, Romantic, Festival…); it becomes a chip.
