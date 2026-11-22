# Lumen

Android 上的自托管音乐播放器：**Kotlin + Jetpack Compose**，通过 Subsonic / OpenSubsonic 协议连接 Navidrome。

- 参考设备：Pixel 10a（120Hz），minSdk 26 / targetSdk 36，只构建 `arm64-v8a`
- 应用不收集分析数据，曲库、账号与播放记录都保存在设备本地

## 功能

- **首页**：专辑 / 艺人 / 最近 / 收藏四个分区；专辑多碟分页
- **播放页**：歌词（逐行滚动、可点按跳转）、0–10 评分、心情分析、下载、收藏、播放模式（列表 / 单曲 / 随机）
- **最近**：最近播放按单曲展示（本地历史），最近添加按专辑，已下载单独一栏
- **多账号**：设置里保存多个 Navidrome 账号并切换，密码与令牌使用 Android Keystore 加密
- **Navidrome Stat**：可选接入，展示播放统计
- **心情分析**：端侧 YAMNet + CNN14 / DSP，结果只保存在本机，用于动态背景与配色
- **交互**：预测性返回（跟手、上一页实时露出、无闪跳）、从点击位置展开的进入动画、封面交叉溶解、切歌音量淡入淡出
- **离线**：边下边播缓存、整张专辑/单曲下载、独立的缓存清理

## 技术栈

| 层 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3（Expressive 风格） |
| 图标 | `io.github.p2o51:yoin-symbols` |
| 音频 | Media3 ExoPlayer（磁盘缓存 + 解码失败自动转码兜底） |
| 网络 | OkHttp + org.json（Subsonic token + salt 认证） |
| 图片 | Coil + Material Color Utilities 封面取色 |
| 端侧模型 | TensorFlow Lite（YAMNet）+ ONNX Runtime（CNN14 int8） |
| 构建 | Gradle 8.11.1（Wrapper）+ AGP 8.10.1 + Kotlin 2.3.20 |

## 语言

设置 → 语言 可切换「跟随系统 / 中文 / English」；文案集中在 util/L10n.kt，英文缺失时回退中文。

## 构建

需要 JDK 17 与 Android SDK 36（platform 36）：

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 下把 `./gradlew` 换成 `gradlew.bat`。

> 仓库中的 release 构建默认使用本机 debug 签名，仅供个人安装。正式分发时在 ndroid/keystore.properties 或环境变量中提供：
> LUMEN_KEYSTORE_FILE、LUMEN_KEYSTORE_PASSWORD、LUMEN_KEY_ALIAS、LUMEN_KEY_PASSWORD。

## 测试

```bash
cd android
./gradlew :app:testDebugUnitTest
```

目前覆盖 URL 构造、专辑排序与队列索引等纯逻辑。

## 目录

```
android/app/src/main/java/app/lumen/player/
  MainActivity.kt        应用壳、导航与页面装配
  data/                  配置/账号、Subsonic、缓存、心情、统计、历史
  playback/              ExoPlayer、下载服务、后台分析 Worker
  ui/                    各页面与主题/背景组件
android/app/src/main/assets/
  mood/                  端侧模型（约 86 MB）
  licenses/              第三方许可全文
```

## 贡献与安全

- 提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)
- 安全问题请按 [SECURITY.md](SECURITY.md) 私下报告
- 第三方组件与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)

## 许可

Lumen 以 **MIT License** 发布，见 [LICENSE](LICENSE)。

预训练模型：YAMNet（Apache-2.0，未修改）、CNN14 / PANNs（CC BY 4.0，Lumen 量化为 int8 ONNX，属修改版本）。Navidrome（GPL-3.0）与 Navidrome Stat（MIT）仅通过网络协议对接，代码未打包进本应用。
