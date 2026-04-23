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
- **Google-first transcription**: Google AI Edge LiteRT-LM audio transcription on-device
- **Supported summarizer runtimes**:
  - LiteRT-LM for `.litertlm` assets
  - MediaPipe LLM Inference for `.task` assets
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
- Google AI Edge LiteRT-LM audio runtime (`com.google.ai.edge.litertlm:litertlm-android`)
- MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`)

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
core:llm
core:common
core:designsystem
core:testing
---

## ✅ Prerequisites

- Android Studio Meerkat or newer
- Android SDK **36**
- JDK **21** runtime for Gradle tooling

No local NDK/CMake setup is required for the default app build anymore.

---

## 🚀 Quick start

### 1) Clone

```bash
git clone https://github.com/mosmb/idonthaveyourtime.git
cd idonthaveyourtime
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

Supported transcription model formats:
- `.litertlm` -> Google AI Edge LiteRT-LM

Supported summarizer model formats:
- `.litertlm` -> LiteRT-LM
- `.task` -> MediaPipe LLM Inference

### Suggested models

Transcription:
- gemma-4-E2B-it.litertlm
  Source: `litert-community/gemma-4-E2B-it-litert-lm`
  Runtime: Google AI Edge LiteRT-LM
- gemma-4-E4B-it.litertlm
  Source: `litert-community/gemma-4-E4B-it-litert-lm`
  Runtime: Google AI Edge LiteRT-LM
- gemma-3n-E2B-it-int4.litertlm
  Source: `google/gemma-3n-E2B-it-litert-lm`
  Runtime: Google AI Edge LiteRT-LM
- gemma-3n-E4B-it-int4.litertlm
  Source: `google/gemma-3n-E4B-it-litert-lm`
  Runtime: Google AI Edge LiteRT-LM

LLM (summarization):
- gemma-4-E4B-it.litertlm
  Source: `litert-community/gemma-4-E4B-it-litert-lm`
  Runtime: LiteRT-LM
- gemma-4-E2B-it.litertlm
  Source: `litert-community/gemma-4-E2B-it-litert-lm`
  Runtime: LiteRT-LM
- Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task
  Source: `diamondbelema/edu-hive-llm-models`
  Runtime: MediaPipe LLM Inference
- gemma3-270m-it-q8.task
  Source: `diamondbelema/edu-hive-llm-models`
  Runtime: MediaPipe LLM Inference

Debug convenience: bundle models as assets

For local installs, you can bundle model files by placing them under:
- app/src/main/assets/models/ (debug + release)
- app/src/debug/assets/models/ (debug only)

At runtime, bundled assets are extracted into context.filesDir/models/ on first use.

Notes:
- “Transcription (Google AI Edge)” availability is tied to the selected `transcriptionModelFileName`
  (default: `gemma-4-E2B-it.litertlm`). If you import or bundle a different `.litertlm`
  file, select it in the summarize settings so the correct transcription runtime becomes Ready.
- “Summarizer (LLM)” availability is tied to the selected `llmModelFileName`
  (default: `gemma-4-E2B-it.litertlm`). If you bundle a different `.litertlm`
  or `.task` file, select it in the summarize settings so the correct runtime
  and file become Ready.

### Summarizer runtime notes

- LiteRT-LM is the primary path for supported `.litertlm` models and uses async streamed generation.
- MediaPipe LLM Inference is the secondary Google-backed path for `.task` models and also streams partial output.
- The app now uses technology-specific runtime names in UI, logs, and probe/debug reporting:
  - `LiteRT-LM`
  - `MediaPipe LLM Inference`

## 🧪 Verification

Recommended local verification commands:
- `./gradlew :core:model:test :core:llm:test :core:data:test :core:domain:test :feature:summarize:impl:test :app:assembleDebug`
- Run a repository-wide dead-reference scan for removed backends outside `docs/superpowers/plans/**` before merging.

Connected Google-AI-Edge instrumentation coverage now expects a real transcription `.litertlm` model to be present in app storage or bundled assets. The repository currently does not bundle one by default, so the E2E and benchmark instrumentation tests will skip until that asset is supplied.

## 🔐 Privacy
- Pipeline processing is local (import/conversion/transcription/summarization on-device).
- Session history stores transcript + summary + metadata in Room.
- Temporary audio files in cache are cleaned after processing/cancel.

## 🙏 Acknowledgements
- Google AI Edge LiteRT-LM
- MediaPipe LLM Inference
- HuggingFace model hosting
