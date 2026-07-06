# 开发文档

本文档记录 Mimo TTS Engine 的开发环境、依赖、构建、签名、版本号和发布规则。普通用户使用说明请看根目录 `README.md`。

## 技术栈

- Kotlin `2.4.0`
- Android Gradle Plugin `9.2.1`
- Gradle wrapper `9.6.1`
- Jetpack Compose BOM `2026.06.01`
- Material 3 `1.5.0-alpha23`
- DataStore Preferences `1.2.0`
- OkHttp `5.3.2`
- kotlinx.serialization JSON `1.9.0`
- kotlinx.coroutines Android `1.10.2`
- Haze `2.0.0-alpha01`

主要 Android 配置：

- `applicationId`: `io.github.linvva.mimottsengine`
- `namespace`: `io.github.linvva.mimottsengine`
- `minSdk`: `30`
- `targetSdk`: `37`
- `compileSdk`: `37`
- `versionName`: `0.1.0-beta.4`
- `versionCode`: `4`

默认音频参数：

- 采样率：`24000Hz`
- 声道：单声道
- 编码：`PCM_16BIT`

## 本地开发

推荐使用 Windows + Android Studio 打开仓库根目录，等待 Gradle Sync 完成后运行 App。

Debug 构建：

```bash
./gradlew :app:assembleDebug
```

Release 构建：

```bash
./gradlew :app:assembleRelease
```

Release 构建会启用 R8 与资源裁剪：

- `isMinifyEnabled = true`
- `isShrinkResources = true`
- `proguard-android-optimize.txt`
- `app/proguard-rules.pro`

如果在 WSL 中构建，需要确保 Android SDK 路径使用 WSL 路径。例如：

```properties
sdk.dir=/mnt/c/Users/<you>/AppData/Local/Android/Sdk
```

也可以临时指定环境变量：

```bash
ANDROID_HOME=/mnt/c/Users/<you>/AppData/Local/Android/Sdk \
ANDROID_SDK_ROOT=/mnt/c/Users/<you>/AppData/Local/Android/Sdk \
./gradlew :app:assembleDebug
```

`local.properties` 是本机配置文件，不应提交到仓库。

## 签名

Release APK 必须使用 release keystore 签名。keystore、密码和本机签名配置不得提交。

忽略文件包括：

- `keystore.properties`
- `*.jks`
- `*.keystore`
- `local.properties`

本地 release 构建需要以下环境变量：

```bash
ANDROID_KEYSTORE_PATH=/path/to/release.jks
ANDROID_KEYSTORE_PASSWORD=your-store-password
ANDROID_KEY_ALIAS=your-key-alias
ANDROID_KEY_PASSWORD=your-key-password
```

GitHub Actions 发布使用仓库 Secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64` 是 release keystore 文件的 base64 内容。

## GitHub Actions 发布

Release workflow 由 tag 触发：

```bash
git tag -a v0.1.0-beta.4 -m "v0.1.0-beta.4"
git push origin v0.1.0-beta.4
```

流程：

1. GitHub Actions 解码 release keystore。
2. 执行 `./gradlew :app:assembleRelease`。
3. 将 APK 重命名为 `mimo-tts-engine-v<version>.apk`。
4. 创建 GitHub Release。
5. tag 中包含 `-beta.` 时自动标记为 pre-release。

注意：workflow 必须先合入 `main`，再推送 tag。tag 只会运行该 tag 所在 commit 中已经存在的 workflow。

## 版本号规则

项目使用 SemVer 风格版本号，并在正式版前使用 beta 版本。

规则：

- `versionName` 必须与 Git tag 去掉 `v` 后一致。
- `versionCode` 必须永远递增，不复用。
- 测试版使用 `beta.x`，例如 `0.1.0-beta.1`、`0.1.0-beta.2`。
- 正式版不带后缀，例如 `0.1.0`。
- 已经发布的 tag 不覆盖；如果发布有问题，递增到新版本。

示例：

```text
versionName        versionCode   Git tag
0.1.0-beta.1      1             v0.1.0-beta.1
0.1.0-beta.2      2             v0.1.0-beta.2
0.1.0-beta.3      3             v0.1.0-beta.3
0.1.0-beta.4      4             v0.1.0-beta.4
0.1.0             5             v0.1.0
0.1.1             6             v0.1.1
```

## 开发规范

- 不提交 `.idea/`、`.gradle/`、`.serena/`、`local.properties`。
- 不提交 `app/build/`、`app/debug/`、`app/release/` 等构建产物。
- 不提交 keystore、签名密码、API Key、设备序列号或本机路径。
- API Key 只通过 App 设置页保存到本地 DataStore。
- 业务代码尽量保持小步修改，避免无关重构。
- 改动发布配置时，同步检查 `app/build.gradle.kts`、tag、Release 标题和 APK 文件名。

## 关键行为说明

系统 TTS 路径：

- 使用 Mimo 流式 SSE。
- 请求 `audio.format = "pcm16"`。
- 读取 `choices[0].delta.audio.data`。
- 解码 base64 后通过 Android `SynthesisCallback.audioAvailable()` 输出。

本地 HTTP 路径：

- 默认绑定 `127.0.0.1:8765`。
- `GET /health` 返回健康状态。
- `GET /tts?text=...&speed=...&voice=...` 返回 `audio/wav`。
- 使用 Mimo 非流式接口，请求 `audio.format = "wav"`。

后台保活：

- 系统 TTS 合成期间使用 `mediaPlayback` 前台服务。
- 本地 HTTP 服务运行期间使用 `dataSync` 前台服务。
- 朗读期间持有 `PARTIAL_WAKE_LOCK`。
- Android 14+ 需要声明对应前台服务类型权限。
