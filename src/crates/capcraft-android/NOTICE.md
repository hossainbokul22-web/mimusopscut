# Mimusopscut for Android: notices

Mimusopscut, a fork of CapCraft for Android, is free software: you can redistribute it and/or modify it under the terms of the GNU
Affero General Public License, version 3 or (at your option) any later version, as published by
the Free Software Foundation (`LICENSE`). It is built on the engine of Concat (Jareer and Concat
contributors, AGPL-3.0-or-later); the engine's own third-party notices are in
`THIRD_PARTY_NOTICES.md`.

Mimusopscut is not affiliated with, endorsed by or connected to ByteDance or CapCut.

## In the Android app

| Component | Licence | Use |
|---|---|---|
| Lucide icons (Lucide Contributors, portions Cole Bemis / Feather) | ISC (`LICENSE-lucide.txt`) | the app's icons, as vector paths |
| Material Symbols (Google) | Apache-2.0 (`LICENSE-material-symbols.txt`) | some of the app's icons, as vector paths |
| Kotlin standard library and coroutines (JetBrains) | Apache-2.0 | the app's code |
| AndroidX, Jetpack Compose, Material 3 (Google) | Apache-2.0 | the app's interface |
| JNA (Java Native Access) | Apache-2.0 | calling the engine from Kotlin |
| Google ML Kit Translate | Google APIs / ML Kit terms (proprietary; not free software) | translating captions on the phone; each language model is downloaded once. Remove this line and `TranslateCaptions.kt` for a build with free software only (F-Droid) |
| Pollinations public image address (service, not bundled code) | Pollinations' terms | AI Studio's Text to image and story pictures when no Replicate key is set: the description is sent to image.pollinations.ai without any key. Remove `FreeImage.kt` to drop it |
| Replicate API (service, not bundled code) | Replicate terms; models have their own licences | AI Studio sends the chosen pictures, videos and prompts to replicate.com with the user's own key, kept in app-private storage. Remove `AiStudio.kt` and `engine/Replicate.kt` for an app that never goes online; the models used include ProPainter (video object removal) and Chatterbox multilingual (voice cloning), under their own licences; clone only voices you have permission to use |
| Any OpenAI-style chat service the user names (service, not bundled code) | that service's terms | story scripts: the prompt goes to the address the user sets in AI Studio, with their key kept in app-private storage. The manifest allows plain http only so a server on the user's own network works; the code accepts http only for localhost and private addresses. Remove `TextLlm.kt` to drop it |
| UniFFI (Mozilla) | MPL-2.0 | the engine's Kotlin bindings |
| `gif` and `png` crates (image-rs contributors) | MIT OR Apache-2.0 | writing GIF exports and freeze-frame stills |
| sherpa-onnx (Xiaomi Corporation, k2-fsa) | Apache-2.0 | running the speech-enhancement model for Isolate voice |

Models downloaded on first use (speech recognition, voices, background removal, picture enhancement) come with the
licences listed in `THIRD_PARTY_NOTICES.md`. Isolate voice downloads GTCRN (Xiaobin Rong and contributors, MIT), as
converted and published by the sherpa-onnx project.
