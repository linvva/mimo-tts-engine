# Mimo TTS Engine

[简体中文](README.md) · [Download APK](https://github.com/linvva/mimo-tts-engine/releases) · [Development notes](docs/development.md)

Use Xiaomi MiMo voices on Android and in reader apps. Mimo TTS Engine works as an Android system text-to-speech engine and a local HTTP TTS service for Legado, with streaming playback and character voice selection.

Requires Android 11 or later and your own MiMo API key.

## Screenshots

Switch between Classic and Liquid Glass at any time. Liquid Glass adds floating navigation, transparent cards, and matching controls. Both styles share settings, the current tab, and input state.

<table>
  <tr>
    <th>Classic · TTS</th>
    <th>Liquid Glass · TTS</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/classic-tts.png" width="320" alt="Classic TTS screen with voice, speed, and test reading controls" /></td>
    <td><img src="docs/screenshots/glass-tts.png" width="320" alt="Liquid Glass TTS screen with transparent cards and floating navigation" /></td>
  </tr>
  <tr>
    <th>Local HTTP service</th>
    <th>Voice selection</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/glass-http.png" width="320" alt="Liquid Glass HTTP screen with service controls and Legado configuration" /></td>
    <td><img src="docs/screenshots/glass-voices.png" width="320" alt="Expanded glass voice menu with Chinese and English voices and a selection indicator" /></td>
  </tr>
</table>

Screenshots show the current source's Debug build with demo settings. See [GitHub Releases](https://github.com/linvva/mimo-tts-engine/releases) for published versions. The app currently displays Chinese interface labels.

## Features

- **System TTS**: registers as an Android text-to-speech engine, with streaming PCM16 output for apps that use system TTS.
- **Local HTTP**: streaming AAC or complete WAV for reader apps, listening only on `127.0.0.1:8765`.
- **Symbol-only silence**: optional local silence for paragraphs containing only symbols, with a 0–10000 ms pause and no MiMo request.
- **Character voices**: exports the complete voice catalog so readers can assign voices to characters.
- **Streaming and pre-synthesis**: two concurrent upstream synthesis slots; identical streaming requests share synthesis and a short-lived cache.
- **Two UI styles**: instant Classic / Liquid Glass switching, with saved appearance and last selected tab.
- **Quick controls**: start or stop HTTP from a Quick Settings tile; long-press to open the app. Foreground notifications and background settings help with screen-off reading.

## Quick start

### Install and configure

1. Download and install an APK from [GitHub Releases](https://github.com/linvva/mimo-tts-engine/releases).
2. Open the app and enter your MiMo API key under “设置” (Settings).
3. Choose a voice and speed on the TTS tab, then tap “测试朗读” (Test reading).
4. Adjust the prompt under “设置 → 朗读风格”, or switch UI styles under “外观 → 界面风格”.

The API key, voice, speed, and prompt are saved locally in DataStore. Changing appearance does not change synthesis settings.

### Use system TTS

1. Open Android's text-to-speech output settings, usually under Accessibility or General management.
2. Select `Mimo TTS Engine` as the preferred engine.
3. Choose system TTS in your reader app.

### Connect Legado

1. Open the app's HTTP tab and start the local service.
2. Tap “复制 Legado 配置” (Copy Legado configuration) and import it as an online TTS source in Legado.
3. Enable streaming reading in Legado for the default AAC source.
4. Assign voices to characters as needed. For complete audio, choose “复制 WAV 兼容配置” (Copy WAV compatibility configuration) in the configuration panel.

When upgrading to **0.2.0**, replace the old source with a fresh export and reselect character voices to avoid reusing the old source and slow audio cache.

HTTP speed follows Legado's `speakSpeed / 10`: `10 → 1×`, `15 → 1.5×`, `20 → 2×`. The app's system TTS speed is not multiplied into HTTP synthesis. MiMo prompt-based speed is approximate; the reader player controls global playback speed.

AAC is lossy 24 kHz mono audio at 64 kbps. Duration may be unknown while generating, and precise seeking beyond cached audio is not guaranteed. Use WAV when complete audio is needed. See [HTTP adaptation](docs/http-tts.md) for parameters, caching, timeouts, and checks.

### Background and screen-off reading

Check notification permission, battery optimization, and app background restrictions under “设置 → 后台保活”. Some MIUI / OEM devices also require manually allowing background activity or unrestricted battery use.

System TTS synthesis and the HTTP service use foreground notifications. Add the `Mimo HTTP` Quick Settings tile to start and stop the service.

## Development

The source targets `0.2.1` (`versionCode=8`). Open the repository root in Android Studio and complete Gradle Sync before running.

```bash
./gradlew :app:assembleDebug
```

Debug uses a separate application ID and the default Debug signing key, so it can be installed alongside Release. Settings are separate, but both use port `8765`; run one HTTP service at a time.

- [Development, signing, and releases](docs/development.md)
- [Appearance](docs/ui-styles.md)
- [HTTP and reader integration](docs/http-tts.md)

## License

[GNU GPL v3](LICENSE)
