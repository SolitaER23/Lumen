# 回滚手册（Rollback）

## 1. 应用回滚

正式包按版本保留在 `dist/`（该目录不入库，只在发布机上保留），安装旧版即可回退：

```powershell
adb -s <serial> install -r dist\Lumen-<旧版本>.apk
```

- 必须使用**同一签名证书**的包，否则只能卸载重装（会清空本机数据）；
- 0.74.0 起为 CLAP + ONNX（`model_id = clap-fp16-v1`），数据库结构 v13；
- 0.76.0 是纯资源版本，无数据或结构变化，可与 0.74.x / 0.75.x 直接互相覆盖。

## 2. 数据回滚

`moods.db` 的备份属于调试产物（`baseline/` 目录，不入库），需要在发布机上自行保留：

```powershell
# 1) 先装可调试的正式变体（release 签名 + debuggable），若存在
adb install -r android\app\build\outputs\apk\verify\app-verify.apk

# 2) 覆盖数据库
adb push baseline\release-moods.db /data/local/tmp/moods.db
adb shell run-as app.lumen.player cp /data/local/tmp/moods.db databases/moods.db

# 3) 重启应用
adb shell am force-stop app.lumen.player
```

> 跨 `model_id` 的数据不兼容：旧版写入的 YAMNet（`yamnet-v1`）结果会在新版启动时被清除并要求重新分析。

## 3. 模型回滚

随包模型是两个文件（Git LFS 托管，见 [MODELS.md](../MODELS.md)）：

| 文件 | 字节数 | 说明 |
| --- | --- | --- |
| `assets/mood/clap_music_fp16.onnx` | 147,876,819 | CLAP 音频塔，fp16 存储 / fp32 计算 |
| `assets/mood/audio_projection.onnx` | 3,150,329 | 1024 维情绪投影层 |

- 换模型需同时改 `ClapEmbedder.ASSET` 与 `filesDir` 里的缓存版本戳，否则会命中旧副本（字节校验失败时会重新复制）；
- 0.73 及更早的 YAMNet 资产已彻底移除，回退需从对应版本的 APK 中提取 `assets/mood/yamnet.tflite`。

## 4. 回滚验证清单

| 检查 | 期望 |
| --- | --- |
| 版本号 | `adb shell dumpsys package app.lumen.player | findstr versionName` |
| 分析可用 | 日志出现 `LumenMood:` 的行 |
| 数据行数 | `SELECT COUNT(*) FROM moods` |
| 模型标识 | `SELECT model_id, COUNT(*) FROM moods GROUP BY model_id`（当前应为 `clap-fp16-v1`） |
