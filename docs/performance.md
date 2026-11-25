# 性能基线（0.53.5，Pixel 10a / Android 17）

> **历史记录**：本页数据测于 0.53.5，之后版本（0.74 起换成 CLAP + ONNX）未重新测量，
> 绝对值只作趋势参考；需要当前版本数据请按文末命令重测并覆盖本页。

测量条件：局域网 Navidrome（192.168.1.164:4533），1567 首 / 95 张专辑，Release 构建。

## 专辑打开（点击 → 曲目就绪）

| 路径 | 耗时 | 说明 |
| --- | --- | --- |
| 内存命中 | 0 ms（不触发请求） | 同一会话内二次进入 |
| 磁盘命中（冷启动后首次） | **11 ms** | `cacheDir/album_tracks` 读取 + 解析 |
| 预取命中 | **12 ms** | 启动时预取的 6 张专辑 |
| 网络拉取 | **31 ms**（10 首） | 局域网，未预取的专辑；期间显示骨架占位 |

> 结论：局域网下网络本身不是瓶颈，感知等待主要来自"无占位 + 无缓存"的空白；磁盘缓存把冷启动首次进入也压到 ~11 ms。

## 冷启动

`adb shell am start -W` × 3：TotalTime **151 / 140 / 151 ms**。

## 滚动流畅度（首页网格连续 6 屏）

`dumpsys gfxinfo app.lumen.player`：

| 指标 | 数值 |
| --- | --- |
| 渲染帧数 | 468 |
| Janky frames | **1（0.21%）** |
| 50th / 90th / 95th / 99th | 10 / 13 / 14 / **15 ms** |
| Missed Vsync | 0 |

> 结论：分页 + 预取 + 解码对齐后，滚动基本无掉帧；p99 15 ms 仍在 60Hz 预算内。

## 复测方法

```bash
# 专辑打开耗时（需带临时日志的构建）
adb logcat -c && adb shell am start -n app.lumen.player/.MainActivity
adb shell input tap 280 891 && adb logcat -d -s LumenPerf:*

# 滚动帧率
adb shell dumpsys gfxinfo app.lumen.player reset
# …滚动若干屏…
adb shell dumpsys gfxinfo app.lumen.player | grep -E "Janky|percentile|Missed"

# 冷启动
adb shell am force-stop app.lumen.player && adb shell am start -W -n app.lumen.player/.MainActivity
```
