# 第三方开源组件与许可

Lumen 自身以 MIT 许可发布（见根目录 `LICENSE`）。以下组件随应用分发或被打包进 APK，按各自许可证要求保留版权与许可声明。

完整许可文本位于 `android/app/src/main/assets/licenses/`，也可在应用内「设置 → 关于 → 开源信息」直接展开查看。

## 随 APK 分发的第三方内容

| 名称 | 版本 | 许可证 | 版权声明 | 许可全文 |
| --- | --- | --- | --- | --- |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 2.3.20 / 标准库 | Apache-2.0 | Copyright JetBrains s.r.o. and contributors | `Apache-2.0.txt` |
| Jetpack Compose（UI / Foundation / Material3 / Runtime） | BOM 2024.10.01 | Apache-2.0 | Copyright 2019-2024 The Android Open Source Project | `Apache-2.0.txt` |
| AndroidX Core / Activity / Lifecycle / WorkManager | core-ktx 1.15.0、activity-compose 1.9.3、lifecycle 2.8.7、work 2.9.1 | Apache-2.0 | Copyright The Android Open Source Project | `Apache-2.0.txt` |
| AndroidX Media3 / ExoPlayer | 1.5.0 | Apache-2.0 | Copyright The Android Open Source Project | `Apache-2.0.txt` |
| OkHttp | 4.12.0 | Apache-2.0 | Copyright Square, Inc. | `Apache-2.0.txt` |
| Retrofit / converter-gson | 2.11.0 | Apache-2.0 | Copyright Square, Inc. | `Apache-2.0.txt` |
| Gson | 2.11.0 | Apache-2.0 | Copyright Google LLC | `Apache-2.0.txt` |
| Coil | 2.7.0 | Apache-2.0 | Copyright Coil Contributors | `Apache-2.0.txt` |
| LiteRT | 1.4.0 | Apache-2.0 | Copyright 2024 The TensorFlow Authors | `Apache-2.0.txt` |
| YAMNet（模型 `yamnet.tflite`） | TensorFlow Models 版本 | Apache-2.0 | Copyright 2019 The TensorFlow Authors | `Apache-2.0.txt` |
| Material Color Utilities（vendored Java 源码） | 2025 源码快照 | Apache-2.0 | Copyright 2021-2025 Google LLC | `MCU-Apache-2.0.txt` |
| Yoin Symbols | 0.2.0 | MIT | Copyright (c) p2o51 | `MIT.txt` |
| Google Sans Flex（字体） | 字体内嵌版本 | OFL-1.1 | Copyright 2015 The Google Sans Flex Authors | `OFL-1.1.txt` |

## 仅通过网络对接、未打包进 APK

| 名称 | 许可证 | 说明 |
| --- | --- | --- |
| Navidrome | GPL-3.0 | 自托管音乐服务器；Lumen 仅通过 HTTP/Subsonic 协议访问，未链接或分发其代码。 |
| navidrome-stat | MIT | 可选的播放统计服务；通过其 REST API 读取数据，未分发其代码。 |
| Subsonic / OpenSubsonic API | 公开规范 | 仅实现协议，不包含规范文本或参考实现代码。 |

## 特别说明

- YAMNet 模型来自 TensorFlow Models（Apache-2.0），未修改。
- Google Sans Flex 以 SIL OFL-1.1 内嵌，未修改字体文件，未单独售卖字体。
- 本项目不包含任何用户凭据、服务器地址或个人数据；运行期数据仅保存在设备本地。
