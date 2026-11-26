# 优化计划（0.90.0 审计后续）

> 依据：《Lumen 项目审查报告（0.90.0：端侧模型移除后）》
> 基线：`0.90.0 / versionCode 224`，65 个 Kotlin 文件 / 12,972 行，APK 3.2 MB，`.git` 148 MB
> 状态：**执行中**（批次 1 与批次 2 已完成并发布 0.91.0；批次 3 需手动触发，批次 4–5 待做）
>
> | 项 | 状态 |
> | --- | --- |
> | A1 遗留清理 | ✅ 0.91.0（LegacyCleanup + 启动/wipe 双接入 + 测试） |
> | A2 缓存隔离 | ✅ 0.91.0（acct 前缀 + 旧键兼容 + 单/仪器测试） |
> | A3 明文下沉 | ✅ 0.91.0（NetworkGate + 构造即校验 + 清单收敛） |
> | A5 wipe 白名单 | ✅ 0.91.0（补 4 类 + 强制遗留清理） |
> | A6 统计 | ✅ 0.91.0（去相册 + 设备时区） |
> | A8 死代码/歌词 | ✅ 0.91.0（删 StreamCapability、lumen_recent；歌词磁盘缓存接通） |
> | A11 OkHttp | ✅ 0.91.0（util/Http 单例） |
> | A4 动画/UI 测试 | ✅ 关闭动效时的动画门控保留；**UI 自动化用例按产品决定移除**（0.91.1），改为发版前人工走查 |
> | A7 仓库瘦身 | ✅ 已执行：filter-repo + LFS prune + 强推；新 clone 148 MB → **3.49 MB** |
> | A9 下载超时 | ✅ 0.91.0（onTimeout 通知 + stopSelf） |
> | A10 脚本加固 | ✅ 证书校验 fail-closed、密钥内容扫描 |
> | A12 ProGuard | ✅ 删除整层 keep（无 Gson 反射/无 @Serializable），真机冒烟通过 |
> | A13 Diagnostics | ✅ 去掉 Build.MODEL |
> | A14 卫生 | ✅ 三项 lint 改就地标注；dist 4.75 GB 归档到 dist/archive |

---

## 0. 优先级模型

每个发现按三维打分，求和排序：**风险 R**（安全/数据损失，1–5）× **影响面 S**（受影响用户/模块比例，1–5）× **紧急度 U**（拖延是否恶化，1–5）。

| # | 问题 | R | S | U | 得分 | 级别 |
| --- | --- | --- | --- | --- | --- | --- |
| A1 | 升级用户设备残留 148 MB 模型 + `moods.db` + 电台缓存 | 4 | 5 | 5 | **100** | P0 |
| A2 | 媒体缓存键缺账号命名空间（多账号串音/串数据） | 4 | 3 | 4 | **48** | P0 |
| A3 | 明文 HTTP 策略只在前端，数据层不校验 | 5 | 4 | 3 | **60** | P0 |
| A4 | 动画/轮询/120 Hz 常驻（耗电 + UI 测试不可用） | 3 | 5 | 3 | **45** | P1 |
| A5 | 「清除本机数据」漏搜索历史/公网明文开关等 4 类 | 3 | 5 | 3 | **45** | P1 |
| A6 | 统计海报写公共相册 + 时区写死 | 4 | 3 | 3 | **36** | P1 |
| A7 | 仓库历史仍带 144 MB LFS 对象（未真正瘦身） | 2 | 4 | 4 | **32** | P1（需协调） |
| A8 | `StreamCapability`(137 行)/`LyricsCache`/`lumen_recent` 死代码 | 2 | 4 | 2 | **16** | P2 |
| A9 | 下载服务 FGS `dataSync` 6 h 上限 | 2 | 3 | 2 | **12** | P2 |
| A10 | 发版脚本证书校验 fail-open、密钥扫描不查内容 | 3 | 2 | 2 | **12** | P2 |
| A11 | 3 个独立 OkHttpClient | 1 | 4 | 2 | **8** | P2 |
| A12 | ProGuard 整层 keep `data.**` | 2 | 3 | 1 | **6** | P3 |
| A13 | `Diagnostics` 含准标识符 | 2 | 2 | 1 | **4** | P3 |
| A14 | lint 全局忽略、`dist/` 2.9 GB 历史包、过期注释 | 1 | 3 | 1 | **3** | P3 |

---

## 1. 分批执行顺序

```
批次 1（P0，3–5 天）     A1 遗留清理 ───┐
                        A2 缓存隔离    │ 可并行
                        A3 明文下沉    ┘
                                │
批次 2（P1，5–7 天）     A5 wipe 白名单（依赖 A1）→ A6 统计 → A4 动画/轮询
                                │
批次 3（P1，需协调）     A7 仓库瘦身（独立，可与批次 2 并行）
                                │
批次 4（P2，5–7 天）     A8 死代码 → A9 FGS → A10 脚本 → A11 OkHttp
                                │
批次 5（P3，随版本）     A12 ProGuard → A13 Diagnostics → A14 卫生项
```

依赖说明：A5 与 A1 改同一文件（`LocalData.kt`），必须先后串行；A2 的旧键清理要等 A1 的清理框架落地后再做一次性标记；A7 会改写历史，必须**在所有本地提交推送完之后**执行。

---

## 2. 详细任务卡

### A1 升级用户遗留数据清理（P0）

**目标**：升级后不再保留任何已下线功能的数据；回收 ≥148 MB 磁盘，并移除旧的听歌画像。

**具体措施**
1. 新增 `local/LegacyCleanup.kt`：幂等（prefs `lumen_maintenance.legacy_removed_v090`）清理
   - `filesDir/clap_music_fp16.onnx`、`clap_music_v4.onnx`、`*.onnx.tmp`、`audio_projection.onnx`
   - `ctx.deleteDatabase("moods.db")`（连同 `-wal`/`-shm`）
   - `filesDir/radio_tracks.json`、`cacheDir/mood_tracklist.json`、`cacheDir/mood_*`
   - prefs：`lumen_mood`、`lumen_radio`、`lumen_stream_caps`
2. `LumenApp.onCreate` 在 IO 线程调用一次；`LocalData.wipe` 末尾也调用（保证「清除本机数据」同样干净）。
3. 清理项写成常量列表 + 版本戳，后续下线功能复用同一机制。

**预期效果**：老用户 `filesDir` 中模型/DB 归零；升级首启多一次 <1 s 的 IO。

**验收标准**
- 新增 `LegacyCleanupTest`（instrumented）：预置上述文件与 prefs → 执行 → 断言全部不存在；连续执行两次不抛异常（幂等）。
- `LocalDataWipeTest` 增断言：`moods.db`、`radio_tracks.json`、`lumen_mood`、`lumen_radio` 均被清。
- 真机：从 0.89 APK 覆盖安装 0.90+ → 启动一次 → `run-as`/测试断言 `filesDir` 无 `*.onnx`。

---

### A2 媒体缓存键加账号命名空间（P0）

**目标**：不同服务器/账号的缓存永不互相命中。

**具体措施**
1. 新增 `MediaCache.accountScope(ctx): String` = `sha256(base + "|" + user).take(8)`（取自 `SettingsStore.load`）。
2. 键格式改为 `acct:<scope>|track:<id>#<format>[@bitrate]`；`Downloads.downloadKey` 同步改为 `acct:<scope>|<id>#lumen-dl`。
3. 升级：用幂等标记做一次「清空流缓存」（旧键无法映射，直接 `clearStreamingCache(keepKeys=downloadedUris)` 会误删下载 → 改为先删旧键格式的 span）。
4. `Downloads.summaries` 的字节统计、`cachedBytesFor` 同步使用新键。

**预期效果**：多账号切换 0 串音；缓存命中率不变（同账号同曲仍命中）。

**验收标准**
- 单测：不同 `base`/不同 `user` → scope 不同；同账号同 id 同 format → 键相同（纯函数可测）。
- instrumented：mock 服务器 A/B 提供同 id 曲目，分别播放后断言两个 cache key 不同、且各自取到的字节数等于对应文件大小。
- 回归：单账号用户升级后播放正常、命中缓存（可通过第二次播放的 bytes 读取验证）。

---

### A3 明文 HTTP 校验下沉到数据层（P0）

**目标**：任何代码路径都无法在未确认的情况下对公网使用明文 HTTP。

**具体措施**
1. `SubsonicClient` 构造时校验：`NetworkPolicy.evaluate(cfg.base, NetworkSettings.allowPublicCleartext(ctx))`，非 `OK` 抛 `InsecureBaseException`（新类型，携带可读原因）。
2. `ServerConfig.complete` 增加同源判定，避免「看似完整但不可用」。
3. 登录页捕获该异常并显示「请改用 HTTPS 或勾选风险开关」，与现有提示复用。
4. 清单删掉冗余的 `android:usesCleartextTraffic="true"`，由 `networkSecurityConfig` 单一控制；更新其注释。

**预期效果**：绕过路径归零；自托管局域网 http 不受影响。

**验收标准**
- 扩 `NetworkPolicyTest`：公网 http + 未允许 → 抛错；允许后放行；私网 http / https 放行。
- instrumented：直接构造公网 http 的 `SubsonicClient` 断言抛 `InsecureBaseException`。
- 真机：内网 http 登录正常；公网 http 被拦并给出勾选入口。

---

### A4 动画/轮询治理（P1）

**目标**：空闲耗电下降；Compose UI 测试能进入 idle，`UiFlowTest` 解封。

**具体措施**
1. 新增 `util/TestHooks.kt`：`@Volatile var disableAmbientAnimation = false`（仅测试置位）。
2. `ExpressiveBackdrop` 的 `rememberInfiniteTransition` 与 `while(true) withFrameNanos`、`PlayingScreen` 的两处循环、`HomeScreen` 艺术家滚动，统一在 `TestHooks` 为真时短路（返回静态值）。
3. `MainActivity` 每 5 s 的 `isPowerSaveMode` 轮询改为 `ACTION_POWER_SAVE_MODE_CHANGED` 广播接收。
4. `preferredRefreshRate = 120f` 改为按 `display.supportedModes` 上限自适应（或仅在动画期请求）。
5. 解封 `UiFlowTest`（删除 `@Ignore`），必要时补 testTag。

**预期效果**：静止场景帧率回落；`UiFlowTest` 可纳入 CI 门禁。

**验收标准**
- `UiFlowTest` 在真机通过（登录→播放→下载→电台入口已删则相应调整→清除数据）。
- `dumpsys gfxinfo` 对比：空闲 10 s 的帧数下降 ≥50%。
- 生产包动画观感无变化（人工确认 3 个页面）。

---

### A5 「清除本机数据」白名单化（P1，依赖 A1）

**目标**：覆盖所有个人数据，保留项显式、可审计。

**具体措施**
1. `LocalData.wipe` 增清：`lumen_search_hist`、`lumen_network`、`lumen_recent`、`lumen_home_scroll`，并调用 `LegacyCleanup.run`。
2. 顶部写「保留白名单」常量（`lumen_appearance`、`lumen_lang`、`lumen_legal`、`lumen_onboarding`）+ 理由注释。
3. 新增规则注释：新增落盘 store 必须在此登记并在测试断言。

**预期效果**：换机/借出场景无残留；后续新增 store 不再漏。

**验收标准**：`LocalDataWipeTest` 覆盖新增 4 项；`lumen_appearance` 仍保留（现有断言保持通过）。

---

### A6 统计去相册 + 时区（P1）

**目标**：收听画像不落公共存储；统计时区正确。

**具体措施**
1. 删除 `StatsScreen` 中 `MediaStore` 写入块（保留 `cacheDir/share` + `FileProvider` 分享）。
2. `StatsClient` 的 `timezone` 参数改 `URLEncoder.encode(ZoneId.systemDefault().id, UTF_8)`；把 URL 构造抽成纯函数便于测试。
3. 若隐私条款提到「导出到相册」，同步修订（当前 `privacy.json` 未提，确认即可）。

**预期效果**：公共媒体库里不再出现 `lumen-stats.png`；非中国时区用户分布正确。

**验收标准**
- `grep EXTERNAL_CONTENT_URI` 无命中。
- 单测：`buildDashboardUrl(base, days, zone)` 对 `Asia/Shanghai`、`Europe/Berlin` 生成正确编码。
- 真机：分享一次 → 相册无新增文件，分享面板可读到图片。

---

### A7 仓库瘦身（P1，需协调，可与批次 2 并行）

**目标**：新 clone 体积 <10 MB（不含 `dist/`），远端不再保留 144 MB 模型对象。

**具体措施**
1. `git clone --mirror` 备份；确认无未合并 PR / 协作者。
2. `git filter-repo --invert-paths --path removed-ai-model-features --path android/app/src/main/assets/mood`。
3. 删除 `.gitattributes` 的 `*.onnx`/`*.tflite` LFS 行；`git lfs uninstall --local`（可选）。
4. `git push --force --all` + `--tags`；远端执行 LFS prune / 平台侧 GC；通知协作者重新 clone。

**预期效果**：`.git` 148 MB → <10 MB；CI 缓存与 clone 时间显著下降。

**验收标准**：新目录 `git clone` 后 `du -sh .git` < 10 MB；`git lfs ls-files` 为空；CI 全绿；仓库无 404 的 LFS 指针错误。

**回滚**：mirror 备份可完整恢复（若尚未强推）。

---

### A8 死代码清理（P2）

**目标**：仓库里不存在「看起来有、实际没有」的功能模块。

**具体措施**
1. 删除 `data/StreamCapability.kt`（137 行）及 `LocalData`/`LocalDataWipeTest` 中的 `lumen_stream_caps` 引用。
2. `LyricsCache` 二选一：**推荐接线**（换歌时 `put`、进播放页先 `get`，删除内存 40 条 LRU 或降级为二级缓存）；若不接线则删类 + 删设置页「歌词缓存」行。
3. 删除 `HomeScreen` 的 `lumen_recent` 只读分支。

**预期效果**：-175 行左右；设置页不再有「永远 0 B」的假功能。

**验收标准**：`grep -r "StreamProber|StreamCapability"` 为空；歌词缓存行要么显示真实体积并随使用增长，要么整行消失。

---

### A9 下载前台服务时限（P2）

**目标**：Android 14+ 的 `dataSync` 6 小时上限不会静默中断整库下载。

**具体措施**：`DownloadService` 处理 `Service.onTimeout`（API 35+）回调 → 发通知「下载已暂停，点击继续」；队列分批（每批 N 首）降低触发概率。

**验收标准**：单元/仪器测试模拟超时回调 → 通知出现；恢复后队列继续。

---

### A10 发版脚本与密钥扫描（P2）

**具体措施**
1. `update-release-metadata.py`：`apksigner` 缺失或指纹不匹配时**直接失败**（当前 fail-open）。
2. `check-secrets.ps1`：增加内容扫描（`Bearer `、`token=`、`password=`、`sk-`、`ghp_`），命中即失败。
3. CI 里两者都作为门禁。

**验收标准**：未装 apksigner 时脚本返回非 0；仓库内植入假 token 时 `check-secrets` 失败。

---

### A11 OkHttp 单例（P2）

**具体措施**：新增 `util/Http.kt` 进程级 `OkHttpClient`；`SubsonicClient`/`StatsClient` 用 `newBuilder()` 派生各自的超时。

**验收标准**：`grep "OkHttpClient.Builder"` 仅剩 `Http.kt` 一处；连续切号 10 次线程数不增长（`dumpsys` 观察）。

---

### A12–A14（P3，随版本）

| 项 | 措施 | 验收 |
| --- | --- | --- |
| A12 ProGuard | `-keep class data.**` 收窄为 Retrofit 接口 + `@Serializable` 模型 | `assembleRelease` 通过 + 真机冒烟（登录/播放/下载/统计） |
| A13 Diagnostics | 去掉 `Build.MODEL` 或复制时提示脱敏 | 复制文本不含机型 |
| A14 卫生 | `lint.xml` 全局 ignore 改为就地 `tools:ignore`；`dist/` 归档 0.60–0.89；清理 `MainActivity` 两处过期注释 | lint 无新增 error；`dist/` 只保留最近 3 个版本 |

---

## 3. 每批的统一验收门槛

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:assembleRelease            # R8 规则校验
./gradlew :app:connectedDebugAndroidTest  # 真机：Pixel 10a（无线调试）
python ../scripts/check-legal.py
powershell -File ../scripts/check-secrets.ps1
```

**每批必过的人工回归**：登录（内网 http）→ 曲库 → 播放/切歌 → 下载一首 → 搜索 → 统计页 → 设置清除数据 → 重登。

---

## 4. 度量看板（起始值 → 目标值）

| 指标 | 起始 | 目标 | 关联项 |
| --- | --- | --- | --- |
| 升级用户残留模型/DB | 148 MB + `moods.db` | 0 | A1 |
| 多账号串音 | 存在 | 0 | A2 |
| 公网明文绕过路径 | 存在 | 0 | A3 |
| 空闲 10 s 帧数 | 基准 | -50% | A4 |
| 清除数据覆盖项 | 13 | 17（白名单外全清） | A5 |
| 相册写入 | 1 处 | 0 | A6 |
| `.git` 体积 | 148 MB | <10 MB | A7 |
| 死代码行数 | ~175 | 0 | A8 |
| `UiFlowTest` | @Ignore | 通过 | A4 |
| lint warning | 15 | ≤5 | A14 |
| OkHttpClient 实例 | 3 | 1 | A11 |

---

## 5. 需要你决策的事项（阻塞对应项）

1. **A1**：是否允许升级时**自动删除** `moods.db` 与旧模型（不可逆；功能已下线，删除无功能损失）。
2. **A7**：是否重写 Git 历史并强推（会改写全部 commit hash，协作者必须重新 clone）。
3. **A8**：`LyricsCache` 是「接线」还是「删除」？接线才会真正有离线歌词。
4. **A6**：Navidrome Stat 统计功能是否继续维护？若否，可整块移除，连同时区/相册问题一起消失。
5. **A4**：是否同意在生产代码中加入测试专用动画开关 `TestHooks.disableAmbientAnimation`（用于解封 UI 自动化）。

---

## 6. 风险与回滚

| 项 | 主要风险 | 回滚方式 |
| --- | --- | --- |
| A1 | 误删现役缓存文件 | 清理项写成白名单常量；先在 0.89→0.90 的测试包上验证；`album_tracks`、`lyrics` 等现役缓存**不在**删除列表 |
| A2 | 升级后首次播放缓存全部 miss（一次性） | 保留旧键读取路径一版；或接受一次重下（内网成本低） |
| A3 | 局域网 http 用户被误拦 | 私网判定有单测覆盖；发布说明中标注 |
| A4 | 动画观感变化 | 开关默认关闭；仅测试置位；生产行为不变 |
| A7 | 历史重写不可逆 | 先 `clone --mirror` 备份；确认无协作者后再强推 |
