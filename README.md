# Mimo TTS Engine

Android system TextToSpeech engine for Xiaomi Mimo TTS API.

The app can be selected in Android's system text-to-speech settings. Reader apps that use Android TTS can route text to Mimo, receive PCM16 audio from the streaming SSE API, and play it through the system TTS callback. It also includes a local HTTP endpoint for reader apps that support online TTS sources.

## Features

- Android `TextToSpeechService` engine registration.
- Mimo streaming SSE synthesis with `pcm16` output.
- Local HTTP TTS service on `127.0.0.1:8765` with WAV output.
- Jetpack Compose + Material 3 settings UI.
- DataStore Preferences for local API key, voice, speed, and prompt settings.
- Foreground-service keepalive and partial wake lock while reading.
- Quick Settings tile for starting or stopping the local HTTP service.

## Requirements

- Android Studio with Android Gradle Plugin support.
- Android SDK matching the project `compileSdk`.
- JDK 17 or newer.
- Android device running Android 11 or newer.
- A Mimo API key.

Current Android config:

- `minSdk`: 30
- `targetSdk`: 36
- `compileSdk`: 37

## Build

Open the repository root in Android Studio and let Gradle sync.

From a terminal:

```bash
./gradlew :app:assembleDebug
```

The API key is not stored in source code. Enter it in the app settings after installing the APK. The current implementation stores it locally in DataStore Preferences.

## Use as System TTS

1. Install and open the app.
2. Enter the Mimo API key.
3. Choose a voice, speed, and style prompt.
4. Open Android text-to-speech settings.
5. Select `Mimo TTS Engine` as the preferred engine.

Common settings paths:

- `Settings -> Accessibility -> Text-to-speech output`
- `Settings -> General management -> Text-to-speech output`

The system TTS path uses Mimo streaming SSE. Audio chunks are read from:

```text
choices[0].delta.audio.data
```

The value is base64-encoded PCM16 and is emitted through Android `SynthesisCallback.audioAvailable()`.

## Local HTTP TTS

The app can also run a local HTTP service for reader apps that support online TTS sources.

Base URL:

```text
http://127.0.0.1:8765
```

Endpoints:

- `GET /health`
- `GET /tts?text=...&speed=...&voice=...`

The HTTP service binds only to `127.0.0.1`; it is intended for apps on the same device, not LAN access. The HTTP path uses Mimo non-streaming synthesis with `audio.format = "wav"` and returns `audio/wav`.

Example Legado online TTS source:

```json
{
  "name": "Mimo 本地 TTS",
  "url": "http://127.0.0.1:8765/tts?text={{java.encodeURI(speakText)}}&speed={{speakSpeed}}&voice=Chloe",
  "contentType": "audio/.*"
}
```

The app UI also shows a ready-to-copy Legado configuration that includes the selected voice.

## Audio Defaults

- Sample rate: `24000Hz`
- Channels: mono
- Encoding: `PCM_16BIT`

These values are centralized in `TtsAudioConfig`.

## Permissions

The app declares foreground-service permissions for Android 14+:

- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for system TTS synthesis.
- `FOREGROUND_SERVICE_DATA_SYNC` for the local HTTP TTS service.

It also requests notification, wake lock, and battery-optimization related permissions so long-running reading can continue while the screen is locked. Some OEM Android builds may still require manually allowing background activity in system settings.

## Release and Versioning

This project uses SemVer-style versions with beta pre-releases before stable releases.

Recommended first releases:

- `v0.1.0-beta.1`: first public test release.
- `v0.1.0`: first stable release after testing.

Version rules:

- `versionName` must match the Git tag without the leading `v`.
- `versionCode` must always increase and must never be reused.
- Use `beta.x` for test releases, for example `0.1.0-beta.1`, `0.1.0-beta.2`.
- Use a stable version without suffix after testing, for example `0.1.0`.
- If a published release has a bug, publish a new version instead of replacing the old tag.

Example sequence:

```text
versionName        versionCode   Git tag
0.1.0-beta.1      1             v0.1.0-beta.1
0.1.0-beta.2      2             v0.1.0-beta.2
0.1.0             3             v0.1.0
0.1.1             4             v0.1.1
0.2.0-beta.1      5             v0.2.0-beta.1
```

GitHub Release rules:

- Beta releases should be marked as pre-releases.
- Stable releases should not be marked as pre-releases.
- Signed APK files should be named `mimo-tts-engine-v<version>.apk`, for example `mimo-tts-engine-v0.1.0-beta.1.apk`.

## Release Signing

Release APKs must be signed with a private release keystore. Do not commit keystores or signing passwords.

Ignored signing files:

- `keystore.properties`
- `*.jks`
- `*.keystore`

For GitHub Actions based releases, store signing data in GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release workflow must be committed to `main` before pushing the release tag. A tag only runs workflows that already exist at the tagged commit.

Current release workflow:

- Push a tag like `v0.1.0-beta.1`.
- GitHub Actions builds `:app:assembleRelease`.
- The signed APK is uploaded to the GitHub Release.
- Tags containing `-beta.` are marked as pre-releases automatically.

Current beta release command:

```bash
git tag -a v0.1.0-beta.2 -m "v0.1.0-beta.2"
git push origin v0.1.0-beta.2
```
