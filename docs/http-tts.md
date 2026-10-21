# Legado HTTP 适配 · 0.2.0

本项只调整 HTTP 服务、网络请求和配置导出。系统 TTS 的音色、语速适配另行处理。此前快捷开关启动、标签页恢复和 Debug/Release 共存行为继续保留。

## 接口与语速

服务只监听 `127.0.0.1:8765`，两版需要交替运行。`GET /health` 返回 `OK`，不占合成配额。

```text
GET /tts?text=...&speed=10&voice=茉莉&stream=1
```

参数需要 URL 编码。`text` 必填；`voice` 缺省使用 App 当前音色，导出的 URL 则以导出时音色作为角色未指定时的默认值。

| 参数 | 行为 |
| --- | --- |
| `speed` | Legado 的 `speakSpeed`，有限数值 5～85，合成倍率为该值 / 10；省略为 1× |
| `stream=1` | chunked `audio/aac`，AAC-LC、24 kHz、单声道、64 kbps、ADTS |
| 不传 `stream` 或 `stream=0` | 完整 `audio/wav`，真实 Content-Length |

非法语速或 `stream` 返回 400，缺少 API Key 返回 401。`speed=10/15/20` 分别表达约 1×/1.5×/2×，不再压缩到 0.6～1.4×，也不乘 App 的语速设置。MiMo 调速通过提示词表达，不保证精确倍速；全局倍速由阅读播放器控制。

## 配置与升级

App 默认复制流式配置，另有“复制 WAV 兼容配置”。两种配置都通过 JSON 序列化生成，包含完整 `VoicePresets` 目录，`speakersJson` 是包含 `speakerName`、`toneID` 的 JSON 数组字符串。URL 通过 `currentToneID` 选择角色音色，无角色选择时回退到导出时选中的音色。

导出源级 `speed=5`（阅读加 5 后传入 `speakSpeed=10`，对应自然 1×），`synthesisThreadCount=2`。固定 ID 为 Release `87650001`、Debug `87650002`，每次复制刷新 `lastUpdateTime`，便于后续覆盖更新。

首次升级时，停止朗读并替换旧源，再重新选择角色音色。使用 AAC 源时开启阅读的“流式朗读”。新 URL 及音色目录用于隔离旧的慢速缓存；不要继续用旧源测试语速。

AAC 包含编码开销和有损压缩，流式生成期间允许时长未知，未缓存位置的精确拖动不作保证。WAV 保留完整音频兼容路径，流式编码器不可用时应主动复制 WAV 配置；服务不会自动重发可能收费的合成请求。

## 生命周期与共享

- MiMo 流式 SSE 返回 PCM16，MediaCodec 逐帧编码 AAC，并补 ADTS 头。
- 收到首个有效编码帧才返回 200；HTTP 使用 chunked，不虚构音频长度。仅上游完整结束且编码器 EOS 输出完成后写结束块。中途异常关闭连接，不写成功结束块，避免把半段音频当作正常缓存。
- WAV 与流式共用两个上游配额，排队不额外启动 MiMo 请求。
- 同一文本、音色、语速、风格提示词和凭据的并发流式请求共用一次 MiMo 合成和编码，各自读取 App 私有临时文件。改变任意参数会创建独立任务。
- 正常完成结果保留 60 秒，完成缓存总量上限 32 MiB，超限先移除最早完成的结果。失败立即移出缓存；仍在读取的文件在读者退出后删除。停止服务取消任务、关闭连接并清理文件，启动时也清理遗留临时文件。
- 总超时 180 秒，包含排队；MiMo 连续 30 秒无网络数据会失败。客户端断开需等下一次写入检测到，最后一个消费者离开后取消上游。尚未检测到断连时由超时兜底。
- 停止服务直接取消服务协程作用域；阻塞的 socket/OkHttp 调用由取消监听关闭，无 HTTP 独立 `runBlocking`。

## 自动检查

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

本地单元测试覆盖语速边界、中文和带引号的配置、全部音色、ADTS 头与帧长、chunked 正常结束及异常截断、首块提前到达、重复请求共享、完成缓存回放/过期/容量、失败移除、混合 WAV/AAC 并发上限、健康检查、取消与请求超时。伪 MiMo SSE 测试覆盖 PCM 提前到达、语速提示词、缺失 `[DONE]`、上游错误、读取停滞和阻塞请求取消，不调用收费 API。

实际 MediaCodec 编解码测试需 Android 设备或模拟器：

```bash
./gradlew :app:connectedDebugAndroidTest
```

`AacEncoderTest` 用生成的 PCM 验证可在输入结束前输出 AAC、奇数字节分块对齐、ADTS 去头后能解码，以及短句 EOS 余量完整排出。它不使用 API Key，也不替代真实阅读播放验收。未连接设备时只能编译测试 APK，不能视为编解码实测通过。

2026-10-21 开发环境检查：23 项 JVM 测试通过，Debug APK、设备测试 APK 构建通过，Lint 为 0 error / 13 warning（保留的旧代码及依赖提示）。APK 元数据为 `io.github.linvva.mimottsengine.debug`、`versionName=0.2.0`、`versionCode=7`。本轮未执行设备编解码测试、真实 MiMo 请求或手机阅读验收；首音频延迟与连续朗读效果待设备记录。

## 手机验收

1. 确认使用 0.2.0（versionCode 7）；分别复制 AAC 与 WAV 配置，检查源覆盖更新、中文音色及角色切换。
2. 对比源语速 5（实际传入 10）、10（传入 15）、15（传入 20）；保持 App 语速设置不变，再单独修改阅读全局倍速。确认源级和播放级调速各自生效。
3. 检查普通模式、流式模式、试听、预合成、连续切章、暂停续播、缓存重播，以及短句句尾完整性。生成中时长未知及未缓存位置无法精确拖动属于当前限制。
4. 同时试听与预合成同一段，核对日志只出现一次上游合成；多个不同任务最多占用两个配额。断网、取消及停止服务后确认任务结束，重启可正常请求。
5. 回归隔夜快捷磁贴启动、启停与 `/health`；从磁贴长按进入 App 恢复原标签；Debug 与 Release 可并排安装，设置独立，占用同一端口时另一版显示错误。

日志使用 `LocalTtsHttpService` 标签，记录排队耗时、首块 PCM、首个 AAC 输出和总耗时/字节数。只记录文本长度，不记录正文或 API Key。手机记录应包含设备/Android 版本、使用的配置类型与测试结果；自动检查结果与设备实测分别报告。
