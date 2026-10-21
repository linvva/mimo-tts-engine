# Mimo TTS Engine

[English](README.en.md) | [开发文档](docs/development.md)

Mimo TTS Engine 是一个接入小米 Mimo API 的 Android 系统文字转语音引擎，也提供面向阅读软件的本地 HTTP 朗读服务。

安装后，你可以在 Android 系统“文字转语音输出”中选择本引擎。阅读软件调用系统 TTS 时，本应用会请求 Mimo TTS API，将返回的音频输出给系统 TTS。对于支持“在线朗读源”的阅读软件，也可以开启本地 HTTP 服务，让阅读软件直接请求 `127.0.0.1:8765` 获取流式 AAC 或完整 WAV 音频。

## 功能概览

- 注册为 Android 系统 `TextToSpeechService` 引擎。
- 系统 TTS 路径使用 Mimo 流式 SSE，输出 `pcm16` 音频。
- 本地 HTTP 支持流式 `audio/aac` 和完整 `audio/wav`，共享两路上游合成配额。
- 导出完整角色音色目录，修正 Legado 源级语速，重复流式请求共用合成结果。
- 支持 Legado / 阅读 等可配置在线朗读源的阅读软件。
- 设置页基于 Jetpack Compose + Material 3。
- 使用 DataStore Preferences 保存 API Key、音色、语速和风格提示词。
- 朗读期间使用前台服务与 wake lock，改善锁屏和后台朗读稳定性。
- 提供快捷设置磁贴，用于快速启动或停止本地 HTTP 服务。

## 使用方法

### 1. 安装与基础配置

1. 从 GitHub Release 下载并安装 APK。
2. 打开 Mimo TTS Engine。
3. 输入 Mimo API Key。
4. 选择音色、语速和朗读风格提示词。
5. 点击“测试朗读”确认 App 内可以正常播放。

API Key 只保存在本机 DataStore 中，不写入源码或仓库。

### 2. 作为系统 TTS 引擎

1. 打开 Android 系统设置。
2. 进入“文字转语音输出”。
3. 选择 `Mimo TTS Engine` 作为首选引擎。
4. 在阅读软件中选择系统 TTS 朗读。

常见设置路径：

- `设置 -> 无障碍 -> 文字转语音输出`
- `设置 -> 常规管理 -> 文字转语音输出`

系统 TTS 路径使用 Mimo 流式 SSE。音频字段来自：

```text
choices[0].delta.audio.data
```

该字段是 base64 编码的 PCM16 音频，解码后通过 Android `SynthesisCallback.audioAvailable()` 输出。

### 3. 使用本地 HTTP 朗读服务

本地 HTTP 服务适合 Legado / 阅读 这类支持在线朗读源的阅读软件。它默认只绑定本机地址，不开放局域网访问。

```text
http://127.0.0.1:8765
```

支持接口：

- `GET /health`
- `GET /tts?text=...&speed=...&voice=...&stream=1`（AAC 流；省略 `stream` 或设为 `0` 返回 WAV）

在 App 的 HTTP 页面打开“Legado 配置”，默认复制流式 AAC 配置；需要完整 WAV 时选择“复制 WAV 兼容配置”。配置包含全部音色，可在阅读中为不同角色选择音色。

首次升级到 **0.2.0** 时：

1. 停止朗读，用 App 新导出的配置替换阅读中的旧源。
2. 重新选择角色音色，避免继续使用旧源与旧慢速缓存。
3. 使用 AAC 配置时，在阅读中开启“流式朗读”；WAV 配置可用于兼容模式。
4. 先启动本地 HTTP 服务，再试听和朗读。App 开关或快捷设置磁贴均可启停服务。

`/tts` 的 `speed` 是阅读传入的 `speakSpeed`，合成倍率为 `speed / 10`：`10 → 1×`、`15 → 1.5×`、`20 → 2×`；缺省为自然 1×，仅接受有限数值 `5～85`。导出配置的源级 `speed=5` 对应阅读传入 `10`，即自然语速。HTTP 语速不再乘 App 语速设置；源级调速通过 MiMo 提示词表达，并非精确倍速，全局倍速由阅读播放器控制。

AAC 为 24 kHz、单声道、64 kbps 的有损编码。流式生成期间时长可能未知，尚未缓存位置的精确拖动不作保证；需要完整音频时可切换 WAV 配置。接口、缓存、超时和验收步骤见 [HTTP 适配说明](docs/http-tts.md)。

### 4. 后台与锁屏

系统 TTS 合成和本地 HTTP 服务运行时会使用前台服务通知。Android 14+ 需要对应的前台服务权限；部分 MIUI / OEM 系统还需要在系统设置中手动允许后台运行或取消电池优化限制。

如果锁屏后朗读中断，请在 App 设置页的“后台保活”区域检查通知权限、电池优化和应用后台限制。

## 开发

当前代码目标版本为稳定版 `0.2.0`（`versionCode=7`），发布状态以 GitHub Releases 为准。

推荐使用 Android Studio 打开仓库根目录，等待 Gradle Sync 完成后运行。

Debug 构建：

```bash
./gradlew :app:assembleDebug
```

Release 构建需要 release keystore 和签名环境变量。详细开发环境、依赖、签名、GitHub Actions、版本号和发布规则见：

[docs/development.md](docs/development.md)

## 默认音频参数

- 采样率：`24000Hz`
- 声道：单声道
- 编码：`PCM_16BIT`

这些值集中定义在 `TtsAudioConfig` 中，后续如果 Mimo 侧音频参数变化，可以从这里调整。
