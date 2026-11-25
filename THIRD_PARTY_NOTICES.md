# 第三方开源组件与许可

Lumen 自身以 MIT 许可发布（见根目录 `LICENSE`）。以下组件随应用分发或被打包进 APK，按各自许可证要求保留版权与许可声明。

完整许可文本位于 `android/app/src/main/assets/licenses/`，也可在应用内「设置 → 关于 → 开源信息」直接展开查看。

## 随 APK 分发的第三方内容

| 名称 | 版本 | 许可证 | 版权声明 | 许可全文 |
| --- | --- | --- | --- | --- |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 2.4.21 / 标准库 / 1.13.0 | Apache-2.0 | Copyright JetBrains s.r.o. and contributors | `Apache-2.0.txt` |
| Jetpack Compose（UI / Foundation / Material3 / Runtime） | BOM 2026.11.01 | Apache-2.0 | Copyright The Android Open Source Project | `Apache-2.0.txt` |
| AndroidX Core / Activity / Lifecycle | core-ktx 1.19.1、activity-compose 1.13.0、lifecycle 2.11.1 | Apache-2.0 | Copyright The Android Open Source Project | `Apache-2.0.txt` |
| AndroidX Media3 / ExoPlayer | 1.11.1 | Apache-2.0 | Copyright The Android Open Source Project | `Apache-2.0.txt` |
| OkHttp | 5.5.1 | Apache-2.0 | Copyright Square, Inc. | `Apache-2.0.txt` |
| Retrofit / converter-gson | 3.0.0 | Apache-2.0 | Copyright Square, Inc. | `Apache-2.0.txt` |
| Gson | 2.15.0 | Apache-2.0 | Copyright Google LLC | `Apache-2.0.txt` |
| Coil（coil-compose / coil-network-okhttp） | 3.6.3 | Apache-2.0 | Copyright Coil Contributors | `Apache-2.0.txt` |
| ONNX Runtime（`com.microsoft.onnxruntime:onnxruntime-android`） | 1.22.0 | MIT | Copyright (c) Microsoft Corporation | `MIT.txt` |
| LAION-CLAP HTSAT-base 音乐版（`clap_music_fp16.onnx` + `audio_projection.onnx`） | music_audioset_epoch_15_esc_90.14 | MIT（laion_clap 代码与权重随上游发布） | Copyright LAION 及上游作者 | `MIT.txt` |
| Material Color Utilities（vendored Java 源码） | 2026 源码快照 | Apache-2.0 | Copyright 2021-2026 Google LLC | `MCU-Apache-2.0.txt` |
| Yoin Symbols | 0.2.0 | MIT | Copyright (c) p2o51 | `MIT.txt` |
| Google Sans Flex（字体） | 字体内嵌版本 | OFL-1.1 | Copyright 2015 The Google Sans Flex Authors | `OFL-1.1.txt` |

## 仅通过网络对接、未打包进 APK

| 名称 | 许可证 | 说明 |
| --- | --- | --- |
| Navidrome | GPL-3.0 | 自托管音乐服务器；Lumen 仅通过 HTTP/Subsonic 协议访问，未链接或分发其代码。 |
| navidrome-stat | MIT | 可选的播放统计服务；通过其 REST API 读取数据，未分发其代码。 |
| Subsonic / OpenSubsonic API | 公开规范 | 仅实现协议，不包含规范文本或参考实现代码。 |

## 特别说明

- CLAP 权重由上游 `music_audioset_epoch_15_esc_90.14.pt` 导出为 ONNX（fp16 存储 / fp32 计算），权重数值未做再训练或修改；转换与校验步骤见 `MODELS.md` 与 `scripts/convert-model-fp16.py`。
- Google Sans Flex 以 SIL OFL-1.1 内嵌，未修改字体文件，未单独售卖字体。
- 本项目不包含任何用户凭据、服务器地址或个人数据；运行期数据仅保存在设备本地。
