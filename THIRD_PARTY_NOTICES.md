# Third-Party Notices

Portions of this software were originally contributed under the MIT License prior to 2026-05-12. Those contributions remain licensed under the MIT License, and the notice below is preserved as required.

## MIT License

Copyright (c) 2024-2026 Theo Vilardo and PixelPlayer contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Contributors

The following contributor names were generated from the Git history through `ceec1bb9^`, with obvious aliases consolidated and automated accounts omitted:

- Theo Vilardo
- lostf1sh
- Dhruv Varia
- serein-213
- adlifarizi
- rebornloki-dev
- Amonoman
- Dae Euhwa
- BeanVortex
- Colby Cabrera
- VoidX3D
- Ayaan Hussain
- imoyy
- starry-shivam
- Sjoerd Vermeulen
- Clyde Ho
- Claudio Gratton
- Mazen Natour
- Dexter Reed
- Kainoa Kanter
- Ravishanker
- Silke pilon
- lijinkang
- raplivx
- Luis Vervaet
- Sincere-Bhattarai
- faraz152
- mirkiv
- AJ
- Héctor Luis
- Jakob Moen Bekken
- Jared Stemper
- Jean-Baptiste
- Linus
- OpenSource Guy
- Rahul Sharma
- Rishit
- Ryan
- T. Stark
- Zeus-990
- chen
- danZiKr1
- jair1c
- ori
- Неважно

## whisper.cpp and ggml

Used on the device to time lyrics word by word from the audio. Vendored from
https://github.com/ggml-org/whisper.cpp (v1.9.5) under `app/src/main/cpp/third_party/whisper.cpp`,
CPU backend only. MIT License, Copyright (c) 2023-2026 The ggml authors; full text in
`app/src/main/cpp/third_party/whisper.cpp/LICENSE`.

## Whisper model (ggml-base-q5_1)

OpenAI Whisper "base" weights converted and quantized for ggml, from
https://huggingface.co/ggerganov/whisper.cpp (SHA-256
422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898). The Whisper code and model
weights are released by OpenAI under the MIT License.

## NewPipe Extractor

Used to search YouTube and stream online songs and music videos (streaming only, nothing is
saved). https://github.com/TeamNewPipe/NewPipeExtractor v0.26.5, **GNU General Public License
v3.0 or later**, Copyright (c) Team NewPipe. Because this library is GPL-3.0, the APK that
includes it is distributed under the terms of the GPL-3.0 as a whole; the MusicBabe source code
itself stays MIT and is compatible with that. Its dependencies: Mozilla Rhino 1.8.1 (MPL 2.0),
jsoup 1.22.2 (MIT), nanojson (MIT / Apache 2.0, TeamNewPipe fork), protobuf-javalite (BSD-3-Clause),
jsr305 (Apache 2.0).
