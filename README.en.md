# Mimo TTS Engine

[简体中文](README.md) | [Development notes](docs/development.md)

Mimo TTS Engine is an Android system TextToSpeech engine for the Xiaomi Mimo API. It also provides a local HTTP TTS service for reader apps that support online TTS sources.

After installation, the app can be selected in Android's system text-to-speech settings. Reader apps that use Android TTS can route text to Mimo through this engine. Reader apps with online TTS source support can also request streaming AAC or complete WAV audio from the local HTTP endpoint on `127.0.0.1:8765`.

## Features

- Android `TextToSpeechService` engine registration.
- Mimo streaming SSE synthesis with `pcm16` output for system TTS.
- Local HTTP TTS with streaming AAC or complete WAV, sharing two upstream synthesis slots.
- All preset voices and corrected Legado source speed; identical streaming requests share synthesis.
- Legado-compatible online TTS configuration.
- Jetpack Compose + Material 3 settings UI.
- DataStore Preferences for API key, voice, speed, and prompt settings.
- Foreground-service keepalive and wake lock while reading.
- Quick Settings tile for starting or stopping the local HTTP service.

## Usage

### Basic setup

1. Download and install the APK from GitHub Releases.
2. Open Mimo TTS Engine.
3. Enter your Mimo API key.
4. Choose a voice, speed, and style prompt.
5. Use the test reading button to verify playback.

The API key is stored locally in DataStore Preferences. It is not stored in source code.

### System TTS engine

1. Open Android system settings.
2. Go to the text-to-speech output settings.
3. Select `Mimo TTS Engine` as the preferred engine.
4. Use system TTS in your reader app.

Common settings paths:

- `Settings -> Accessibility -> Text-to-speech output`
- `Settings -> General management -> Text-to-speech output`

The system TTS path uses Mimo streaming SSE. Audio chunks are read from:

```text
choices[0].delta.audio.data
```

The value is base64-encoded PCM16 audio and is emitted through Android `SynthesisCallback.audioAvailable()`.

### Local HTTP TTS

The local HTTP service is intended for reader apps that support online TTS sources. It binds only to the local device.

```text
http://127.0.0.1:8765
```

Endpoints:

- `GET /health`
- `GET /tts?text=...&speed=...&voice=...&stream=1` (AAC; omit `stream` or set `0` for WAV)

Open “Legado 配置” on the HTTP page to copy the default AAC streaming source, or choose “复制 WAV 兼容配置” for complete WAV. Both exports include all preset voices for character selection.

When upgrading to **0.2.0**, replace the old source with a fresh export, reselect character voices, and enable Legado's streaming reading option for AAC. This avoids reusing the old source and slow audio cache. Start the local service before playback; control it from the app or Quick Settings tile.

HTTP `speed` is Legado's `speakSpeed`, divided by 10: `10 → 1×`, `15 → 1.5×`, `20 → 2×`. Omission means natural 1×; only finite values from 5 to 85 are accepted. Exported source `speed=5` makes Legado send 10 (natural speed). The app's speed setting is not multiplied into HTTP synthesis. Source speed is expressed through MiMo prompts and is approximate; global playback speed belongs to the reader player.

AAC uses 24 kHz mono AAC-LC at 64 kbps with ADTS framing and lossy compression. Duration may be unknown while generating; precise seeking beyond cached audio is not guaranteed. Use WAV when complete audio is needed. See [HTTP adaptation and validation](docs/http-tts.md) for limits and checks.

## Development

The code targets stable `0.2.0` (`versionCode=7`); consult GitHub Releases for publication status.

Open the repository root in Android Studio and let Gradle Sync finish.

Debug build:

```bash
./gradlew :app:assembleDebug
```

Release builds require a release keystore and signing environment variables. For Android SDK requirements, dependency versions, signing, GitHub Actions, versioning, and release rules, see:

[docs/development.md](docs/development.md)

## Audio Defaults

- Sample rate: `24000Hz`
- Channels: mono
- Encoding: `PCM_16BIT`

These values are centralized in `TtsAudioConfig`.
