# Wispr Flow Clone for Android

Single Android app that floats a record button over other apps, records audio, sends it to a local Ollama `gemma4:12b` model for transcription, and inserts the text into the focused input field.

## Model
- `gemma4:12b` on Ollama (tested working for audio with `think: false`)
- Audio sent as base64-encoded 16kHz mono WAV in the `images` array
- Ollama endpoint: configurable, default `http://alarma.local:11434/api/chat`

## Architecture
- `OverlayService` — foreground service holding the floating record button
- `AudioRecorder` — captures 16kHz mono PCM and writes WAV
- `OllamaClient` — POSTs base64 audio to `/api/chat`, extracts `message.content`
- `TextInjector` — AccessibilityService that inserts text into the focused `EditText`
- `MainActivity` — settings + permission requests

## Permissions needed
- `SYSTEM_ALERT_WINDOW`
- `RECORD_AUDIO`
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE`
- `INTERNET` / `ACCESS_NETWORK_STATE`
- `BIND_ACCESSIBILITY_SERVICE`

## Build
Android Studio / Gradle. Minimum SDK 26 (Android 8), target SDK 35.
