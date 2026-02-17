# Local Audio Summarizer (Android)
<!-- Badges -->
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/architecture-Clean%20Architecture%20%2B%20MVVM-000000)


Offline/on-device Android app that receives shared audio files (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`), converts them to **16kHz mono WAV**, runs **local transcription** and **local summarization**, then shows **transcription + summary**.

> **Privacy-first:** conversion, transcription, and summarization run on-device. No server required.

---

## ✨ Features

- **Share-to-summarize**: process single or multiple audio files from Android Sharesheet
- **Offline pipeline**: conversion → transcription → summarization (all on-device)
- **No FFmpeg dependency**: MediaCodec-based conversion
- **Whisper transcription**: `whisper.cpp` via JNI
- **LLM summarization**: `llama.cpp` (GGUF) via JNI
- **Foreground processing**: WorkManager (user-visible, cancelable)
- **Local history**: Room database stores transcript + summary + metadata
- **Model management**:
  - download suggested models from HuggingFace (requires `INTERNET`)
  - import local model files
  - optional model bundling via assets for debug convenience

---

## 🧱 Tech stack

- Kotlin + Jetpack Compose
- Clean Architecture + MVVM
- Hilt DI
- WorkManager (foreground)
- Room (local persistence)
- HuggingFace model download (WorkManager)
- MediaCodec-based conversion
- Whisper JNI (`whisper.cpp` submodule)
- llama.cpp JNI (GGUF) for summaries

---

## 📦 Module layout
app
feature:summarize:api
feature:summarize:impl
core:model
core:domain
core:data
core:database
core:audio
core:whisper
core:llm
core:common
core:testing
---

## ✅ Prerequisites

- Android Studio (Flamingo+/Koala+ line)
- Android SDK **36**
- NDK + CMake (required by `core:whisper` JNI module)
- JDK **17** runtime for Gradle tooling

---

## 🚀 Quick start

### 1) Clone + init submodules

```bash
git clone https://github.com/mosmb/idonthaveyourtime.git
cd idonthaveyourtime
git submodule update --init --recursive
```

### 2) Build
`./gradlew :app:assembleDebug`

### 3) Run
- Install the debug APK on a device/emulator.
- Share an audio file to Local Audio Summarizer from any app.
- Download or import models when prompted (see below).

## 🧠 Model Setup

Models are stored in internal app storage:
- context.filesDir/models/

The app can:
- download suggested models from HuggingFace (requires INTERNET)
- import local model files (copied into context.filesDir/models/)

### Suggested models

Whisper (transcription) from ggerganov/whisper.cpp:
- ggml-base-q5_1.bin
- ggml-base.en-q5_1.bin
- ggml-small-q5_1.bin
- ggml-small.en-q5_1.bin

LLM (summarization) GGUF:
- Qwen2.5-0.5B-Instruct-Q4_K_M.gguf (recommended default)
- Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
- gemma-2-2b-it-Q3_K_M.gguf
- gemma-2-2b-it-Q4_K_M.gguf
- gemma-2-2b-it-Q5_K_M.gguf

Debug convenience: bundle models as assets

For local installs, you can bundle model files by placing them under:
- app/src/main/assets/models/ (debug + release)
- app/src/debug/assets/models/ (debug only)

At runtime, bundled assets are extracted into context.filesDir/models/ on first use.

Note: “Summarizer (LLM)” availability is tied to the selected llmModelFileName
(default: Qwen2.5-0.5B-Instruct-Q4_K_M.gguf). If you bundle a different GGUF,
select it in the “Quality vs speed” section so it becomes Ready.

## 🔐 Privacy
- Pipeline processing is local (import/conversion/transcription/summarization on-device).
- Session history stores transcript + summary + metadata in Room.
- Temporary audio files in cache are cleaned after processing/cancel.

## 🙏 Acknowledgements
- ggerganov/whisper.cpp
- ggerganov/llama.cpp
- HuggingFace model hosting