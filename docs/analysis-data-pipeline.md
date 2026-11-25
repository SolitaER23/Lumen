# 分析数据链路（v18 数据库）

## 表结构

`moods` 表关键列：

| 列 | 内容 | 写入方 | 消费方 |
| --- | --- | --- | --- |
| `env` / `env_step` | 能量包络（100 ms/点） | dspFeatures | 进度条波形 |
| `beats` | 全曲拍点 | dspFeatures | 节拍脉冲 |
| `windows_vec` | 5 × 1024 维嵌入 | CLAP 逐窗 | 电台检索、一致性 |
| `windows_beats` | 各窗拍点（相对窗起点） | splitBeatsByWindow | MusicPulse |
| `windows_tempo` | 各窗 BPM | 逐窗自相关 | 呼吸周期、电台匹配 |
| `windows_start` | 各窗起始毫秒 | sampled.startsMs | 窗定位 |
| `windows_va` | 各窗 valence/arousal | 逐窗锚点投影 | 段落地色、动效增益 |

## 5 窗的统一来源

`AudioSource.windows = 5` 是唯一来源，三条取流路径都从它派生：

1. `fetchParts`（TIME_OFFSET 转码分段）
2. `rawSegmentSampled`（原始流 Range 分段）
3. `decodeSegments`（整段下载后本地切窗）

历史上这三处各自硬编码过 3 窗，导致"3 窗/5 窗混杂"。

## 常见故障与判据

| 现象 | 判据 | 原因 |
| --- | --- | --- |
| 数据库无记录 | logcat `mood put failed` | 建表缺列 → 已用 ensureColumns 自愈 |
| 有 env 无 windows_* | 自检页「窗数分布」为空 | `anchorResult.copy()` 丢字段 → 已修 |
| 窗数恒为 3 | 自检页显示 3 窗 × N | 取流路径硬编码 → 已统一 |

## 验证入口

- 设置 → 存储 → 已分析曲目（用户向）
- 长按首页齿轮 → 分析自检（开发向，含清除旧数据/复制诊断）
