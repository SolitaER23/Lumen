# Lumen

Pixel 10a 上的本地音乐播放器：**纯 Kotlin + Jetpack Compose**，接自建的 Navidrome / OpenSubsonic 曲库。

> 0.7.0 起完全用 Kotlin 重写（旧版是 Rust + egui 自绘，源码在 git 历史 `baee628` 及之前）。

## 技术栈

| 层 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3（Expressive 风格） |
| 图标 | `io.github.p2o51:yoin-symbols`（含播放键形变动画） |
| 音频 | Media3 ExoPlayer（512 MB 磁盘缓存，解码失败自动转码兜底） |
| 网络 | OkHttp + `org.json`（Subsonic token + salt 认证） |
| 图片 | Coil + androidx Palette（从封面取主色驱动主题） |
| 目标 | minSdk 26 / targetSdk 36，只打 `arm64-v8a`，仅适配 Pixel 10a（120Hz） |

## 功能

- 专辑网格（2 列圆角卡片）、专辑详情、多碟分页（DISC 1..N）
- 播放：播放全部 / 单曲 / 上一首 / 下一首 / 拖动进度 / 边下边播缓存
- **Yoin 式底栏**：左「首页」/ 中曲目胶囊 / 右播放键，按压时宽度守恒（一侧变宽、胶囊吸收），底部半调网点替代阴影，换歌时曲名左右滑入
- **预测性返回**：跟手缩放/淡出
- 封面取色：整套 M3 配色跟随当前专辑
- 长歌名滚动（`basicMarquee`）

## 构建

```powershell
cd android
gradle :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

需要：JDK 17、Android SDK 36（platform 36）、Gradle 8.11+。

## 目录

```
android/app/src/main/java/com/example/app/
  MainActivity.kt        应用骨架、导航、Yoin 底栏、登录、首页
  data/Settings.kt       连接配置（SharedPreferences）
  data/Subsonic.kt       OpenSubsonic 客户端
  playback/Playback.kt   ExoPlayer + 缓存 + 转码兜底
  ui/AlbumScreen.kt      专辑页（多碟）
  ui/PlayingScreen.kt    播放页
  ui/SettingsScreen.kt   设置
  ui/Palette.kt          封面取色
```
## 许可

- Lumen 自身以 **MIT License** 发布，正式文本见根目录 `LICENSE`。
- 随 APK 分发的第三方组件、版本、版权与许可全文见 `THIRD_PARTY_NOTICES.md` 与 `android/app/src/main/assets/licenses/`。
- 应用内「设置 → 关于 → 开源信息」可直接展开查看 Apache-2.0、MIT、OFL-1.1、CC BY 4.0 的完整文本。
- 预训练模型：YAMNet（Apache-2.0，未修改）、CNN14 / PANNs（CC BY 4.0，Lumen 量化为 int8 ONNX，属修改版本）。
- Navidrome（GPL-3.0）、Navidrome Stat（MIT）仅通过网络协议对接，代码未打包进本应用。
