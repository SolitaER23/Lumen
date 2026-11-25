# 回滚手册（Rollback）

## 1. 应用回滚

```powershell
adb -s <serial> install -r dist\Lumen-0.60.5.apk
```

- 0.60.5 使用 YAMNet（521 维），数据库结构为 v10（无 model_id）；
- 0.61.0 升到 v13 并**删除了所有非 CLAP 结果**，降级后 0.60.5 会看到空的分析数据，需要重新分析（约 2.5 小时）或按下文恢复备份。

## 2. 数据回滚

备份文件：`baseline/release-moods.db`（YAMNet 时代 1452 行，v10 结构）。

```powershell
# 1) 先降级到可调试的正式变体（release 签名 + debuggable）
adb install -r android\app\build\outputs\apk\verify\app-verify.apk   # 若存在

# 2) 覆盖数据库
adb push baseline\release-moods.db /data/local/tmp/moods.db
adb shell run-as app.lumen.player cp /data/local/tmp/moods.db databases/moods.db

# 3) 重启应用
adb shell am force-stop app.lumen.player
```

## 3. 模型回滚

CLAP 模型在 `assets/mood/clap_music_int8.onnx`（294.8 MB，实为 fp32 双输出）。

- 删除或替换该文件即可换模型，但**必须同时改 `ClapEmbedder.ASSET` 与缓存文件名**，否则会命中 `filesDir` 里的旧副本（大小校验会失败并重新复制）；
- YAMNet 资产已移除，回退需从 0.60.5 APK 中提取 `assets/mood/yamnet.tflite`。

## 4. 回滚验证清单

| 检查 | 期望 |
| --- | --- |
| 版本号 | `dumpsys package app.lumen.player \| grep versionName` |
| 分析可用 | 日志出现 `LumenMood: mood ... label=` |
| 数据行数 | `SELECT COUNT(*) FROM moods` |
| 模型标识 | `SELECT model_id, COUNT(*) FROM moods GROUP BY model_id` |
