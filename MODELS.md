# 情绪与音色模型（Models）

Lumen 的离线分析依赖一个音乐专用音频模型。本文件记录版本、来源、许可与校验值，便于复现与审计。

## CLAP 音频塔（音乐版）

| 项 | 值 |
| --- | --- |
| 模型 | LAION-CLAP HTSAT-base（music_audioset_epoch_15_esc_90.14） |
| 权重来源 | `lukewys/laion_clap` → `music_audioset_epoch_15_esc_90.14.pt`（2.24 GB，训练权重） |
| 适配方式 | 用 laion_clap 的 `CLAP_Module` 加载，导出 `audio_branch` 为 ONNX |
| 输入 | `[B, 480000]`，48 kHz 单声道 PCM，10 秒窗 |
| 输出 | `embedding [B, 1024]`、`clipwise [B, 527]`（AudioSet 527 类） |
| 分类标签 | 527 类，顺序取自 `laion_clap/training/audioset_textmap.npy`（已内置于 `ClapLabels.kt`） |
| 运行时 | ONNX Runtime Android **1.22.0**，**CPU EP**（NNAPI 在 Pixel 上不兼容；XNNPACK 的 fp16 累加不可用，见下） |
| 随包精度 | **fp16 权重 + fp32 计算**（权重用 Cast 节点在加载时还原，数值等价于 fp32） |
| 体积（fp16 权重） | 147,876,819 字节（≈141 MiB；APK 内 DEFLATE 后 133,333,966 字节） |
| 许可 | 代码 MIT（laion_clap）；权重随上游仓库发布，分发前请再核对上游条款 |
| 随包文件 | `assets/mood/clap_music_fp16.onnx` |
| 随包 SHA-256 | `606354E025DCE6F8C87BEC6CEC15EB8684F79D6F807A10EB0ACC1A49B5BD8763` |
| 投影层 | `assets/mood/audio_projection.onnx`（3,150,329 字节） |
| 投影层 SHA-256 | `ECD8B5C5CE097CE73359EE4E2D157782F100171FD7D81574E3428ED71F685EBC` |

### 为什么是「fp16 权重 + fp32 计算」

直接整图转 fp16（连计算也是 fp16）时 Android 上不可用：

| 方案 | embedding 余弦（对比 fp32） | 说明 |
| --- | --- | --- |
| 整图 fp16 + CPU EP | 0.802 | fp16 累加误差 |
| 整图 fp16 + XNNPACK | 0.380 ~ 0.717 | XNNPACK fp16 路径失真最严重 |
| **fp16 权重 + fp32 计算 + CPU EP** | **0.99998** | 当前方案 |
| fp32（原方案） | 1.000000 | 设备 CPU/XNNPACK 与桌面 fp32 一致到 1e-12 |

速度代价约 8%（1688 ms vs 1558 ms / 10 秒窗），换来 APK 从 278 MB 降到 **150.8 MB**。

> 注意：这个模型对「频谱稀疏」的输入（纯音、单频扫频）极其敏感 —— 权重加 1e-6
> 相对噪声就能让 embedding 余弦掉到 0.85，因为 LayerNorm 落在极小方差上。用纯音
> 做精度回归会得出错误结论；真机回归统一使用**宽带合成输入**（见
> `ClapModelQualityTest`），宽带下 fp16 与 fp32 的余弦是 0.999997。

> 原始 fp32 权重（`models/clap_music_v4.onnx`，294,812,140 字节，
> SHA-256 `B76C9FDCCE49030000B51C852CF14E51EE2F483FA962A93006CA0EDAA3470105`）
> **只保留在本地**，不进 git、不进 APK；需要重新生成随包模型时用
> `scripts/convert-model-fp16.py`。
>
> 随包的 `assets/mood/clap_music_fp16.onnx` 必须入库（建议 Git LFS）：
> 构建前按本节校验值确认文件完整；应用首次分析时把它解压落到 `filesDir`，
> 并用 `<文件>.ok`（版本戳:字节数）标记，之后冷启动不再重复写入。

### 真实曲库抽样（fp16 vs fp32，2026-11-25）

在一台自建 Navidrome（局域网、FLAC）上按 `getAlbumList2 type=newest` 抽 10 张专辑各 1 首，
取每首中段 10 秒，分别用 fp32 与 fp16 权重推理后对比：

| 指标 | 结果 |
| --- | --- |
| embedding 余弦 | 均值 **0.992**（最低 0.956） |
| clipwise 余弦 | 均值 **0.997** |
| 情绪类 argmax 一致 | **10 / 10** |
| Top-5 标签重合 | **49 / 50** |

对照组是 Windows 系统音效（闹铃/提示音，频谱极其稀疏）：clipwise 余弦只有 0.913、
情绪类一致 6/10。两组结果合起来说明：**fp16 的差异集中在频谱稀疏的边界样本上**，
真实音乐（宽带、多声部）几乎不受影响。

> 复跑方式：`models/validate_real.py` 风格脚本（把音频来源换成你的曲库即可）。仓库不保存
> 任何服务器地址或凭据；真机联调用 `ServerE2ETest`，地址/账号通过 instrumentation 参数传入。
## 已移除

| 模型 | 原因 |
| --- | --- |
| YAMNet（`yamnet.tflite`，4 MB） | 被 CLAP 取代：标签塌缩（74% Happy、valence sd 0.014），维度 521 → 1024 |

## 量化尝试（未采用）

| 方案 | 体积 | 余弦 | 结论 |
| --- | --- | --- | --- |
| 动态 int8（全算子） | 76 MB | — | Android 缺 `ConvInteger` 实现 |
| QDQ 静态（合成校准） | 72 MB | 0.308 | 不可用 |
| QDQ 静态（真实校准 36 段） | 72 MB | 0.084 | 不可用 |
| QDQ 仅 MatMul/Gemm | 79 MB | 0.076 | 不可用 |
| 动态仅 MatMul/Gemm | 79 MB | 0.058 | 不可用 |

结论：该模型对权重扰动极敏感，端侧保留 fp32。

## 体积与分发

- fp32 权重使安装包从 13 MB（YAMNet 时代）涨到约 278 MB，已超过 Google Play 单 APK 200 MB 上限；
  若上架商店需改用 Play Asset Delivery（install-time 分包）或首次启动下载。
- 当前通过网盘直发，**每次更新都要重下整包**；这是已知取舍，见 [docs/plans/optimization-plan.md](docs/plans/optimization-plan.md) §4.2。

## 情绪类索引

CLAP 527 类中的 6 个音乐情绪类：

| 索引 | 名称 |
| --- | --- |
| 276 | Happy music |
| 278 | Sad music |
| 279 | Tender music |
| 280 | Exciting music |
| 281 | Angry music |
| 282 | Scary music |

（注意：不是 YAMNet 的 271–276，那里依次是 Lullaby / Video game music / Christmas music / Dance music / Wedding music / Happy music。）
