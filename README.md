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
- **Latency-first summarization runtimes**:
  - LiteRT-LM for `.litertlm` assets
  - MediaPipe LLM Inference for `.task` assets
  - `llama.cpp` for `.gguf` fallback
- **Foreground processing**: WorkManager (user-visible, cancelable)
- **Local history**: Room database stores transcript + summary + metadata
- **Progressive UX**: transcript segments and in-flight summaries are persisted as work completes
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
- LiteRT-LM Android runtime (`com.google.ai.edge.litertlm:litertlm-android`)
- MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`)
- llama.cpp JNI fallback for GGUF summaries

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

Supported summarizer model formats:
- `.litertlm` -> LiteRT-LM
- `.task` -> MediaPipe LLM Inference
- `.gguf` -> llama.cpp fallback

Runtime selection:
- `Auto` prefers the model-format-matching runtime.
- `.litertlm` selects LiteRT-LM.
- `.task` selects MediaPipe LLM Inference.
- `.gguf` selects llama.cpp.
- If an explicitly selected runtime cannot support the chosen model format, the app falls back to llama.cpp and records the fallback reason in the probe/debug path.

### Suggested models

Whisper (transcription) from ggerganov/whisper.cpp:
- ggml-base-q5_1.bin
- ggml-base.en-q5_1.bin
- ggml-small-q5_1.bin
- ggml-small.en-q5_1.bin

LLM (summarization), preferred Google-backed paths first:
- gemma-4-E2B-it.litertlm
  Source: `litert-community/gemma-4-E2B-it-litert-lm`
  Runtime: LiteRT-LM
- Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task
  Source: `diamondbelema/edu-hive-llm-models`
  Runtime: MediaPipe LLM Inference
- gemma3-270m-it-q8.task
  Source: `diamondbelema/edu-hive-llm-models`
  Runtime: MediaPipe LLM Inference
- Qwen2.5-0.5B-Instruct-Q4_K_M.gguf
  Runtime: llama.cpp fallback
- Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
  Runtime: llama.cpp fallback
- gemma-2-2b-it-Q4_K_M.gguf
  Runtime: llama.cpp fallback

Debug convenience: bundle models as assets

For local installs, you can bundle model files by placing them under:
- app/src/main/assets/models/ (debug + release)
- app/src/debug/assets/models/ (debug only)

At runtime, bundled assets are extracted into context.filesDir/models/ on first use.

Note: “Summarizer (LLM)” availability is tied to the selected llmModelFileName
(default: gemma-4-E2B-it.litertlm). If you bundle a different `.litertlm`,
`.task`, or `.gguf` file, select it in the summarize settings so the correct
runtime and file become Ready.

### Summarizer runtime notes

- LiteRT-LM is the primary path for supported `.litertlm` models and uses async streamed generation.
- MediaPipe LLM Inference is the secondary Google-backed path for `.task` models and also streams partial output.
- llama.cpp remains fully supported as the fallback path for `.gguf` models and for unsupported format/runtime combinations.
- The app now uses technology-specific runtime names in UI, logs, and probe/debug reporting:
  - `LiteRT-LM`
  - `MediaPipe LLM Inference`
  - `llama.cpp`

## 🔐 Privacy
- Pipeline processing is local (import/conversion/transcription/summarization on-device).
- Session history stores transcript + summary + metadata in Room.
- Temporary audio files in cache are cleaned after processing/cancel.

## 🙏 Acknowledgements
- ggerganov/whisper.cpp
- ggerganov/llama.cpp
- HuggingFace model hosting
