# J.A.R.V.I.S. Mobile (Android)

手机上的独立贾维斯：装到安卓手机后不依赖电脑、不需要本地服务器。打开 App 喊一句 "Hey Jarvis"，它就能唤醒、听懂指令、用真实 AI 大脑回答，并且直接操作手机。

## 能力

- 独立运行：全部在手机上完成，无需电脑端、无需本地服务、无需“链接这个链接那个”。
- 语音唤醒：原生前台服务常驻麦克风，支持两种模式：
  - `Porcupine offline`：Picovoice Porcupine 纯离线唤醒，不依赖网络和 Google 服务。
  - `System speech`：系统语音识别唤醒（无需配置，开箱即用）。
- 真实 AI 大脑：接入 OpenAI 兼容接口（默认 `gpt-4o-mini`，可换成 DeepSeek、本地网关等任意兼容模型），在 Settings 里填 API Key 即用；没有 Key 时退回内置规则回复。
- 语音回复：使用 Android 原生 TextToSpeech 朗读贾维斯的回答。
- 手机动作：`open youtube/maps/whatsapp/wechat/...`、`timer 5 minutes`、`vibrate` 直接执行；定时器走系统 AlarmManager，进程被杀也会响。
- 本地数据：对话历史、设备状态、任务、设置全部存在手机本地。

## 使用流程

1. 构建并安装 APK（见下方构建说明）。
2. 打开 App，进入 Settings -> AI engine，打开 `Use AI engine`，填入 API base、Model、API key。
3. 回到 Settings -> Voice interface，打开 `Hey Jarvis wake` 并授权麦克风。
4. 使用离线唤醒（可选）：到 [Picovoice 控制台](https://console.picovoice.ai) 注册账号，训练唤醒词 `jarvis`，下载 `jarvis_android.ppn` 并获取 Access Key；把文件放到 `android/app/src/main/assets/jarvis_android.ppn`，然后在 App 设置里把 Wake mode 切到 `Porcupine offline` 并填入 Porcupine key。
5. 之后只要 App 在前台或唤醒服务运行中，喊 "Hey Jarvis" 即可；也可以直接进 Companion 打字对话。

## 构建 APK

本机安装 Android Studio（自带 JDK 和 Android SDK）后：

```bash
cd outputs/jarvis-android
npm install
npx cap sync android
cd android
gradlew.bat assembleDebug
```

APK 输出在 `android/app/build/outputs/apk/debug/app-debug.apk`，手机连接电脑后：

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开 `android/` 目录点 Run。

本机没有 Android SDK 时，把整个 `outputs/jarvis-android` 推到 GitHub，仓库根目录的 `.github/workflows/build-apk.yml` 会自动构建并产出 debug APK。

## 工作原理

- `WakeWordService`：前台服务。Porcupine 模式下由 `PorcupineManager` 纯离线监听唤醒词；系统模式下用 `SpeechRecognizer`。唤醒后切换到指令模式，把指令回传给界面。
- `AiEnginePlugin`：原生 HTTP 请求 `/chat/completions`，不经过网页 CORS；支持任意 OpenAI 兼容端点。
- `SpeechPlugin`：Android TextToSpeech 朗读回复。
- `ActionsPlugin`：执行打开应用、震动等手机动作；定时器通过 `AlarmManager` + `TimerReceiver` 调度，系统级定时，进程被杀也能响。
- Web 层把唤醒事件、指令、AI 回复、语音朗读和手机动作串成完整流程。

## 权限

- `RECORD_AUDIO`：麦克风唤醒与指令
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`：常驻监听
- `POST_NOTIFICATIONS`：Android 13+ 通知（定时提醒）
- `VIBRATE`：震动反馈
- `SCHEDULE_EXACT_ALARM`：精确定时；未授权时自动降级为近似定时

## 边界说明

- 安卓普通应用做不到 iPhone Siri 那种锁屏黑屏常驻唤醒：需要解锁手机并让 App 在前台，或保持唤醒服务运行（屏幕亮着）。真正锁屏常驻需要系统级白名单或厂商授权，多数 ROM 不开放。
- `System speech` 模式依赖系统 Google 语音识别服务，可能联网；`Porcupine offline` 模式完全离线，但需要先训练 `jarvis` 模型并放置 `jarvis_android.ppn`（README 使用流程第 4 步）。
- “比 Siri 强大”来自大脑：接入 GPT-4o-mini 或更强模型后，理解、记忆和任务处理远超 Siri，代价是模型 API 需要网络和 Key。
- 定时器已使用 `AlarmManager`，即使 App 进程被杀也会按时提醒；系统精确闹钟授权被关闭时会自动降级为近似定时。
