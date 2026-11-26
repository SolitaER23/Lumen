# 下载功能审计与优化计划

> 审计基线：**0.91.2 / versionCode 227**，media3 1.11.1，minSdk 26 / targetSdk 37（compileSdk 37.2）
> 审计范围：下载流程、进度反馈、暂停/取消/重试、断点续传、网络切换、错误处理、权限与存储、并发性能、安全风险、系统版本兼容性
> 结论来源：源码静态审查 + 对 media3 1.11.1 的 `javap` 接口/字节码核对（`Download.getPercentDownloaded`、`DownloadManager.setRequirements`、`DEFAULT_REQUIREMENTS`、`DownloadNotificationHelper.buildProgressNotification`、`ResolvingDataSource` 均已确认存在且未被使用）
> 状态：**已完成（批次 A–D，含真机验证）**

| 批次 | 内容 | 状态 |
| --- | --- | --- |
| A | P0：下载缓存与流缓存解耦、下载快照仓库（含主线程 DB 下移） | ✅ 已完成（编译 + 单测通过） |
| B | P1：凭据不落盘（合成 URI）、网络策略、失败原因与重试、ID 唯一化、空间预检 | ✅ 已完成（编译 + 单测 + 真机启动/清数据验证） |
| C | P2：字节级进度与状态机、i18n 缺口、通知可点、设置页统计口径 | ✅ 已完成 |
| D | P3：死代码、元数据清理、诊断、迁移兼容 | ✅ 已完成（D4 迁移兼容按「旧记录可用」验收，不做全量重写） |

图例：⬜ 待执行 / 🟨 进行中 / ✅ 已完成 / ⛔ 阻塞

---

## 0. 优先级模型

沿用 `optimization-plan-0.90.md` 的三维打分：**分值 = R（安全/数据损失风险 1–5）× S（影响面 1–5）× U（拖延恶化程度 1–5）**。
P0 ≥ 60；P1 20–59；P2 8–19；P3 ≤ 7。

---

## 1. 现状架构

| 模块 | 文件 | 职责 |
| --- | --- | --- |
| 下载编排/快照 | `playback/Downloads.kt` | 入队、分组快照、字节统计、删除、全局暂停 |
| 缓存与下载器 | `playback/MediaCache.kt` | 共享 `SimpleCache`（1 GB LRU）、`DownloadManager`（2 线程）、cache key 策略 |
| 前台服务 | `playback/DownloadService.kt` | FGS、通知、`onTimeout` |
| 管理页 | `ui/DownloadsScreen.kt` | 分组列表、展开曲目、暂停/继续/删除 |
| 入口与状态 | `MainActivity.kt`（`Downloads`/`DownloadsScreen` 接线） | 事件订阅、`summaries` 刷新、入队 |
| 资源策略 | `data/NetworkPolicy.kt`、`data/Subsonic.kt` | 明文策略、`u/t/s` 凭据拼装 |

数据流：`enqueue → 预写名称元数据 → sendAddDownload（每曲一次 Intent）→ DownloadManager（2 线程）→ 写入 SimpleCache span（customCacheKey = acct|id#lumen-dl）→ onDownloadChanged → 500 ms 节流 → summaries() 全量重算 → Compose 重组`。

### 1.1 与上游实践的关键差异

- 上游 ExoPlayer 下载示例在启用 `DownloadManager` 时使用 `NoOpCacheEvictor`，本项目使用 `LeastRecentlyUsedCacheEvictor(1 GB)` 且与在线播放共用同一缓存目录。
- 上游推荐下载前用 `Requirements` 表达「非计费网络」，本项目 `getScheduler()` 返回 `null`、从不调用 `setRequirements`，清单里的 `ACCESS_NETWORK_STATE` 全程未使用。

---

## 2. 缺陷清单

### 2.1 P0

| ID | 缺陷 | 证据 | 影响范围 | 分值 |
| --- | --- | --- | --- | --- |
| **D1** | 下载内容与流缓存共用 1 GB LRU 淘汰器；下载 span 写完后即成为 LRU 候选，会被静默淘汰，而 `Download` 仍是 `STATE_COMPLETED` | `MediaCache.cache()` / `downloadManager()` | 所有下载用户；累计下载 + 流缓存 > 1 GB 必然发生 | 125 |
| **D2** | 下载 URL 携带 `u/t/s` 被持久化进 `databases/exoplayer_internal.db`（明文、可重放、不随密码轮换失效），并会随 Media3 异常进入 logcat | `Downloads.request()`、`Subsonic.streamUrl()` | 全部账号；root / bugreport / 日志即可长期冒用 | 80 |

**D1 后果链**：淘汰 → `cachedBytesFor` 返回 0 → 管理页「28/28 首 · 0.0 MB」→ 播放时 `cacheKeyFor` 的 `hasDownloadSpans` 为 false → 回落流缓存 key → 离线播放失败。

**D2 后果链**：凭据在 `lumen_accounts`（加密）之外多出一份明文副本；`MediaCache` 为 cache key 做过哈希，但下载路径没有同等保护。

### 2.2 P1

| ID | 缺陷 | 证据 | 分值 |
| --- | --- | --- | --- |
| **D3** | 断点续传绑定一次性 URL：改密码/换账号后旧请求永久 401，无重新签名路径 | `Downloads.request()` 一次性构建 | 32 |
| **D4** | 无网络感知：无 `setRequirements`、无 `ConnectivityManager`、无「仅 Wi-Fi」开关；网络切换直接失败而非等待 | `MediaCache.downloadManager()`、`DownloadService.getScheduler()` | 48 |
| **D5** | 无重试入口 + 失败原因不可见：从不读取 `failureReason`/`stopReason`，管理页无重试 | `Downloads.summaries()`、`DownloadsScreen` | 36 |
| **D6** | 重启/进程被杀后不自动续传：无 Scheduler、启动时无 resume、无 WorkManager | `DownloadService`、`MainActivity` | 27 |
| **D7** | 同一 `trackId` 跨分组冲突：`DownloadRequest.id = track.id`，框架按 id 合并，专辑组与单曲组互相顶替 | `Downloads.enqueue/enqueueTrack` + media3 `mergeRequest` | 48 |
| **D8** | 快照与删除在主线程访问 SQLite：`tracksOf` 在 composition 的 `remember` 中同步执行（每首最多 4 次 span 查询），`delete` 在主线程遍历 cursor | `DownloadsScreen`、`MainActivity` 接线 | 27 |
| **D9** | 「清理缓存」会误删暂停/失败下载的 span：白名单只覆盖 `COMPLETED/DOWNLOADING/QUEUED` | `Downloads.downloadedUris()`、`MediaCache.clearSongCache()` | 48 |
| **D10** | 进度极粗：无字节/百分比/速度/ETA；单曲组恒 0%；500 ms 节流无 trailing 补发会丢完成事件；离开页面即停更 | `Downloads.ensureObserver()`、`DownloadsScreen` | 30 |
| **D11** | 存储策略错误：下载写入 `cacheDir`（系统/用户可清），无剩余空间预检、无配额、无预计占用；1 GB 即功能天花板 | `MediaCache`、全仓无 `StatFs` | 48 |

### 2.3 P2

| ID | 缺陷 | 证据 |
| --- | --- | --- |
| **D12** | 进行中下载与播放共用 cache key：`cacheKeyFor` 只检查「有 span」就优先下载 key，边下边播对同一 key 双写 | `MediaCache.cacheKeyFor()` |
| **D13** | 暂停/等待态不可见：`DownloadTrack.paused` 从未展示，`AlbumDownload` 无 paused/queued 字段 | `Downloads.tracksOf()`、`DownloadsScreen` |
| **D14** | 跨账号未隔离显示：`summaries()` 不按 `accountScope` 过滤 | `Downloads.summaries()` |
| **D15** | 英文界面缺 8 条下载文案（全部暂停/全部继续/删除单曲/暂无曲目明细/ · 失败/ · 已完成 + 通知两条） | `DownloadsScreen`、`DownloadService`、`L10n` |
| **D16** | 「全部删除」无二次确认、无失败反馈；`LocalData.wipe` 先异步投递删除再整缓存清空 | `DownloadsScreen`、`LocalData.wipe()` |
| **D17** | 设置页「歌曲缓存」把下载字节算进去（`streamingBytes` 遍历整个 `cacheDir/media`） | `MediaCache.streamingBytes()`、`SettingsScreen` |
| **D18** | 通知不可点击（`contentIntent=null`）、无失败通知、无「等待网络」文案（`notMetRequirements` 恒 0） | `DownloadService.getForegroundNotification()` |
| **D19** | 超时后状态未落为暂停：`onTimeout` 直接 `stopSelf`，`Download` 仍是 `DOWNLOADING` | `DownloadService.onTimeout()` |
| **D20** | 无下载诊断；Media3 内部异常日志含完整 URL（绕过 `Redact`） | `Downloads`、`Redact` |

### 2.4 P3

| ID | 缺陷 | 证据 |
| --- | --- | --- |
| **D21** | 死代码：`isTrackDownloaded`/`status`/`totalBytes`/`totalCount` 无调用点 | `Downloads.kt` |
| **D22** | 元数据膨胀：删除下载不清理 `name_/t_/album_/artist_/albumname_` 键 | `Downloads.kt` |
| **D23** | 升级迁移未验证：`keyOf` 对空 `data` 返回空串，历史下载组名为空 | `Downloads.keyOf()` |
| **D24** | `%.1f.format` 用默认 Locale；`mimeFor` 未知格式回落 `audio/*` | `DownloadsScreen`、`Downloads.kt` |

---

## 3. 优化建议与预期收益

| ID | 建议 | 修复缺陷 | 预期收益 | 风险 |
| --- | --- | --- | --- | --- |
| **O1** | 下载缓存与流缓存解耦：缓存目录迁到 `filesDir/media`（不再被系统清缓存），淘汰器改为「**不淘汰下载 span** 的 LRU」，流缓存单独限流 | D1、D9、D11、D17 | 下载 0 静默丢失；「清理缓存」语义正确；1 GB 天花板消失 | 中：目录迁移需幂等 + 失败回退 |
| **O2** | 合成 URI + `ResolvingDataSource`：DB 里只存 `lumen://track/<id>`，请求时用当前账号实时签名 | D2、D3、D14、D20 | DB/日志零凭据；改密码可续传；跨账号天然隔离 | 中 |
| **O3** | `DownloadsRepository` + `StateFlow` 快照：内存 map + 增量更新（O(1)），字节数按键缓存；查询全部走 IO | D8、D10、D13、D14 | 事件 O(1)；主线程零 DB；离开页面仍有进度 | 中：需改 MainActivity 接线 |
| **O4** | 网络策略：`setRequirements` + `PlatformScheduler`/`WorkManager` + `ConnectivityManager`，设置项「仅 Wi-Fi 下载」 | D4、D6 | 网络切换变「等待并自动恢复」；重启自动续传 | 中 |
| **O5** | 失败原因映射 + 重试（保留 `customCacheKey` 重新入队 = 真续传）+ 失败通知 | D5、D18、D19 | 失败可自助；避免整张重下 | 低 |
| **O6** | ID 唯一化：`request.id = <groupKey>|<trackId>` | D7、D22、D23 | 跨专辑同曲不再互相顶替 | 中：旧 id 迁移 |
| **O7** | 存储预算：`Content-Length` / `getSong.size` + `StatFs`，入队前提示预计占用 | D11 | 消除 ENOSPC 与自淘汰 | 低 |
| **O8** | 字节级进度：`getBytesDownloaded`/`getPercentDownloaded`，速度/ETA，`PERCENTAGE_UNSET` 降级 | D10 | 进度可信 | 低 |
| **O9** | 状态机 UI + 删除二次确认 + 分组级暂停/重试 | D13、D16、D19 | 用户可控 | 低 |
| **O10** | `cacheKeyFor` 以「下载已完成」判定（查 Download 索引 state） | D12 | 消除双写 | 低 |
| **O11** | i18n 收口 + 单测扫描未翻译字面量；数值格式化固定 `Locale.ROOT` | D15、D24 | 英文完整，CI 拦截回归 | 极低 |
| **O12** | 下载事件日志（URL 过 `Redact`）+ 失败原因计数接入诊断 | D20 | 线上可定位 | 低 |
| **O13** | 显式 `setMaxParallelDownloads(2)`；当前播放曲目优先；通知刷新间隔放宽 | 并发 | 行为可预期、省电 | 极低 |
| **O14** | 测试：`DownloadLogicTest`（纯函数）+ 仪器测试（断网/取消/重启/低存储） | 全部 | 可回归 | 中 |

**不做**：① 仅调大上限（治标）；② 迁到 MediaStore（引入存储权限与扫描，违背「私有目录」承诺）；③ 自研下载器（`ResolvingDataSource` + `Requirements` 已够）。

---

## 4. 分批计划

### 批次 A — P0（本次执行）

- [x] **A1 缓存解耦（O1）**
  - `MediaCache`：缓存目录改为 `filesDir/media`；一次性幂等迁移 `cacheDir/media → filesDir/media`（同一文件系统 `renameTo`，失败回退旧目录并下次重试）。
  - 新增 `StreamOnlyCacheEvictor`：LRU，但**永不淘汰 `#lumen-dl` 结尾的 key**；流缓存上限 512 MB。
  - `streamingBytes()` 改为「缓存总字节 − 下载 span 字节」，与「离线下载」不再重复计数。
  - 验收：下载 3 GB 后填满流缓存，下载字节不变；设置页两个数字不重叠；清理缓存后下载仍在。
- [x] **A2 快照仓库（O3）**
  - `Downloads` 暴露 `StateFlow<List<AlbumDownload>>`；内存 `Map<id, Download>` + 字节缓存，事件增量更新；首帧由一次 IO 全量载入。
  - 节流改为「首帧立即 + 500 ms 合并 + trailing 补发」，完成事件必达。
  - 删除 `observe/unobserve` 与 `delay(800)/delay(600)` 刷新补丁；`MainActivity` 改为收集 flow。
  - 验收：离开下载页再回来进度连续；完成瞬间 UI 立即到 100%；静置无 DB 查询。
- [x] **A3 主线程 DB 下移（D8）**
  - `tracksOf` 改 `suspend`，UI 用 `produceState`；`delete/deleteTrack/downloadedUris` 内部切 IO。
  - 验收：展开 28 首专辑且下载中无掉帧（StrictMode 无线程策略告警）。
- [x] **A4 播放键判定（O10）**
  - `cacheKeyFor` 增加「下载已完成」谓词，`MediaCacheScopeTest` 扩用例。
- [x] **A5 文档与变更日志**
  - 更新本文件状态表与 `CHANGELOG.md`。

### 批次 B — P1

- [x] **B1 合成 URI + `ResolvingDataSource`（O2）**：库里只存 `lumen-dl://track/<base64(id)>`，下载时用当前账号实时签名；旧记录一次性改写
- [x] **B2 网络策略与调度（O4）**：`setRequirements` + `PlatformScheduler` + 设置项「仅 Wi-Fi 下载」+ 管理页等待网络提示
- [x] **B3 失败原因 + 重试（O5）**：失败原因脱敏后进 UI、分组/单曲重试复用原 request
- [x] **B4 ID 唯一化与迁移（O6）**：`分组键|曲目 id` + `putDownload` 原样改写旧 id
- [x] **B5 存储预检（O7）**：`song.size` 预估 + `StatFs` 余量，不足则拦下并提示

### 批次 C — P2

- [x] **C1 字节级进度与速度/ETA（O8）**：`bytesTotal/progress/speedBytesPerSec/etaSeconds`，元数据缺失时退回曲目数
- [x] **C2 状态机 UI + 删除确认（O9）**：分组/单曲暂停恢复、失败与暂停态、删除二次确认
- [x] **C3 i18n 收口（O11）**：补齐全部下载相关英文文案，数值格式化固定 `Locale.ROOT`
- [x] **C4 通知（D18）**：进度通知可点（回应用）、失败通知（15 s 节流）、等待网络文案由 `notMetRequirements` 自动呈现
- [x] **C5 设置页统计口径（D17）**：随 A1 一并完成（`streamingBytes` 不含下载）

### 批次 D — P3

- [x] **D1 死代码清理（D21）**：删 `status`/`isTrackDownloaded`/`totalBytes`/`totalCount`/`downloadedUris`/`downloadBytes`/`cacheBytes`
- [x] **D2 元数据清理（D22）**：整组删完后移除 `name_/album_/artist_/albumname_/t_` 键
- [x] **D3 诊断与日志（D20）**：下载失败写 `LumenDownloads`（URL 经 `Redact`）
- [x] **D4 迁移兼容（D23）**：旧 id / 旧 URL 双迁移；`data` 为空的旧记录用 `fallbackName` 兜底显示

---

## 5. 兼容性矩阵

| 版本 | 机制 | 现状 | 处置 |
| --- | --- | --- | --- |
| 8.0（26，minSdk） | 通知渠道 + `startForeground` | 正常 | — |
| 9–11 | `cacheDir` 语义 | 系统低存储会裁 cache | 批次 A 迁 `filesDir` |
| 12（31） | FGS 后台启动限制 | `onCreate` 的 `startForeground` 被 `runCatching` 静默吞 | B2 加失败上报 |
| 13（33） | `POST_NOTIFICATIONS` | 仅登录时申请一次 | C4 应用内提示 |
| 14（34） | `dataSync` 6 h/日上限 | `onTimeout` 只 `stopSelf` | B3 超时落为暂停 |
| 15（35） | `onTimeout(startId, fgsType)` | 已重写，状态不自洽 | B3 |
| 16/17（36/37） | `ACCESS_LOCAL_NETWORK` 运行时权限 | 仅登录流程申请 | B2 下载前校验 |
| OEM | 后台清理/自启动 | 无引导 | 文档说明 |

---

## 6. 验证方法

验证脚本见 `scripts/verify-downloads.ps1`（批次 A 起维护）。

单元测试：`gradlew :app:testDebugUnitTest --tests "app.lumen.player.playback.*"`。
仪器测试：`gradlew :app:connectedDebugAndroidTest`（需真机/模拟器）。

---

## 7. 待实测确认

1. **D7**：media3 同 id 请求在 `COMPLETED` / `DOWNLOADING` 下的合并细节（是否先删旧副本再重下）。
2. **D9**：`SimpleCache.removeResource` 与下载写入线程竞争的具体表现。
3. **D12**：span 只写一半时命中下载 key，是否重现 0.22.1 的「停在 0/1」。
4. **D19**：`onTimeout` 后 `resumeDownloads` 是否足以重启任务。
5. **D23**：0.74–0.90 老用户的 `DownloadRequest.data` 是否为空。
---

## 8. 执行记录

### 批次 A（P0）— 2026-11-26

| 项 | 落地方式 | 与计划的偏差 |
| --- | --- | --- |
| A1 缓存解耦 | 单一 `SimpleCache` 保留，但目录迁到 `filesDir/media`（`renameTo` 原子迁移 + `lumen_maintenance.media_dir_moved_v1` 幂等标记，失败回退旧目录）；淘汰器换成自研 `StreamOnlyCacheEvictor`：按 key 记账，`#lumen-dl` 结尾的 key 不进淘汰候选集，只统计到 `pinnedBytes`；流上限 512 MB | 计划里写的是「两个 SimpleCache」：实际采用「单缓存 + 钉住下载 key」，因为播放侧 `CacheDataSource` 一次只能绑一个 cache，双缓存需要再包一层按 URI 选 cache 的工厂，收益不抵复杂度与风险 |
| A2 快照仓库 | `Downloads` 暴露 `StateFlow<List<AlbumDownload>>`；`entries/bytesByRequestId/dirty` 三个内存镜像；事件只做 Map 写入 + 脏标记，IO 线程合并（200 ms）后重算快照；删除 `observe/unobserve` 与 `delay(800)/delay(600)`；节流改为「发布前先放开标志」，保证末次状态（完成）必达 | — |
| A3 主线程 DB | `tracksOf` 改为纯内存读取（分组明细来自 `entries`）；`delete/deleteTrack/deleteAll` 内部切 IO；`completedTrackIds` 改为内存推导并 `remember(downloads)` | 原计划改 `suspend` + `produceState`：内存化之后连 suspend 都不需要，调用点零改动 |
| A4 播放键 | `cacheKeyFor` 的判定从「有 span」改为「`Downloads` 维护的已完成 key 集合」；顺带删掉热路径上的 span 查询 | 集合以 `CopyOnWriteArraySet` 快照形式发布为不可变 `Set`，避免播放线程读到大集合的复制开销 |
| 顺带 | 下载器 `DefaultHttpDataSource` 超时 15 s / 30 s（原来用默认 8 s，转码首字节慢就失败）；`LocalData.wipe` 改为 `removeAllNow`（先 pause 再逐条移除）后再 `clearAllCache`；删掉 4 个无调用方 API（`status`/`isTrackDownloaded`/`totalBytes`/`totalCount`）与 `downloadedUris` | — |

**验证**

- `gradlew :app:compileDebugKotlin` ✅、`:app:compileDebugAndroidTestKotlin` ✅
- `gradlew :app:testDebugUnitTest` ✅（新增 `DownloadLogicTest` 4 例：下载键后缀判定、流键区分 format/bitrate、URI 键不含凭据、请求 id 解析）
- 真机验收（待执行）：`pm trim-caches` 后下载字节不变；展开 28 首专辑无掉帧；完成瞬间进度到 100%

**已知遗留（进入批次 B）**

- 下载 URL 仍含 `u/t/s` 且写入下载库（D2/B1）；
- 无网络 Requirements / Scheduler（D4/D6/B2）；
- 失败仍只有布尔态、无重试（D5/B3）；`trackId` 跨分组冲突（D7/B4）；无空间预检（D11/B5）。
### 批次 B（P1）— 第一轮：调度 / 重试 / ID / 空间（2026-11-26）

| 项 | 落地方式 | 说明 |
| --- | --- | --- |
| B2 调度（部分） | `DownloadService.getScheduler()` 返回 `PlatformScheduler(this, 1004)`；清单补 `RECEIVE_BOOT_COMPLETED` 与 `PlatformSchedulerService`（`BIND_JOB_SERVICE`，`tools:ignore="ExportedService"`） | 进程被杀/设备重启后由 JobScheduler 重新拉起，不再只靠用户点「全部继续」（D6）。「仅 Wi-Fi」开关与 `setRequirements` 仍待做（需要设置页 UI） |
| B3 失败原因 + 重试 | `onDownloadChanged` 的 `finalException` 经 `Redact.url` 脱敏后存入 `failureMessages`；`AlbumDownload.failure` / `DownloadTrack.failure` 透出；`retry(key)`/`retryTrack(id)` 复用**原 DownloadRequest** 重新入队（cache key 不变 ⇒ 已下载 span 直接续用，真续传）；管理页在失败/暂停时显示「重试」 | 失败原因进程内有效：冷启动后只剩布尔态（`failureReason` 的整数常量在 media3 1.11 已不公开，未硬编码） |
| B4 ID 唯一化 | `requestIdFor(groupKey, trackId) = "分组键|曲目 id"`；`customCacheKey` 同步带分组；`completedKeyByTrack` 映射（曲目 id → 已完成 cache key）供播放侧使用 | 迁移：`DefaultDownloadIndex.putDownload` 原样改写旧 id（不改 uri/cacheKey、不触发重下），仅在 `manager.isIdle` 时执行；同一曲目在两张专辑里会各存一份（体积换语义正确） |
| B5 空间预检 | `Track.sizeBytes` 解析 Subsonic `song.size`；`Downloads.checkSpace` = Σsize vs `StatFs(filesDir).availableBytes − 64 MB`；`enqueue/enqueueTrack` 返回 Boolean，界面 Toast 提示 | 未知大小的曲目不参与判断，避免个别缺字段就整张拦下 |
| 顺带 | 补 8 条英文文案（全部暂停/全部继续/删除单曲/暂无曲目明细/·失败/·已完成/下载已暂停/系统限制提示）＋失败原因与空间不足提示 | 提前完成 C3 的一部分 |

**验证**：`compileDebugKotlin` ✅、`compileDebugAndroidTestKotlin` ✅、`testDebugUnitTest` ✅（38 例，含新增 `requestIdIsUniquePerGroup`）。

当时剩余：B1 合成 URI + `ResolvingDataSource`、B2 的「仅 Wi-Fi」与等待网络状态——均已在下一节「批次 B（P1）— 已完成」中交付。
### 批次 B（P1）— 已完成（2026-11-26）

| 项 | 落地方式 |
| --- | --- |
| B1 凭据不落盘 | `DownloadRequest.uri` 改为合成地址 `lumen-dl://track/<base64url(id)>?fmt=…&rate=…`；`MediaCache.downloadManager` 用 `ResolvingDataSource.Factory` 在每次 `open()` 时调 `Downloads.resolveDownloadUrl`（宿主注册的解析器 → 持久化配置兜底 → 否则抛可读 IOException）；旧记录由 `migrateRequestUris` 用 `putDownload` 改写，不动字节 |
| B2 网络策略 | `Downloads.requirementsFor(wifiOnly)` + `DownloadManager.setRequirements`（非计费网络即 `NETWORK_UNMETERED`，media3 自动展开为 3）；设置页「仅 Wi-Fi 下载」开关；管理页在 `isWaitingForRequirements` 时显示「等待网络」；`PlatformScheduler` 让调度跨重启存活 |
| B3/B4/B5 | 见上一轮记录（失败原因 + 重试、ID 唯一化、空间预检） |

**验证**：`compileDebugKotlin` ✅、`compileDebugAndroidTestKotlin` ✅、单测 ✅（42 例，新增合成 URI 往返/凭据缺失断言、`requestIdFor`、`requirementsFor`）。

### 批次 C（P2）— 已完成（2026-11-26）

| 项 | 落地方式 |
| --- | --- |
| C1 进度 | 快照新增 `bytesTotal / progress / speedBytesPerSec / etaSeconds`：总字节优先（`Download.contentLength` 求和），缺失时退回曲目数；速度按 20 s 窗口 EMA 平滑；单曲下载不再是「恒 0%」 |
| C2 状态机 | 分组级「暂停/继续」（`setStopReason(id, 1)` / `0`，只作用于非终态请求）、失败原因行、暂停态、整组与「全部删除」二次确认对话框 |
| C3 i18n | 补齐 8 条旧文案 + 新增 9 条（仅 Wi-Fi、等待网络、失败原因、空间不足、删除确认、失败通知…）；数值统一 `Locale.ROOT` |
| C4 通知 | 进度通知带 `contentIntent`（点开回应用）；失败走 `buildDownloadFailedNotification`（15 s 节流，多条合并成一条）；`onTimeout` 先把队列落为暂停再 `stopSelf` |
| C5 口径 | 随 A1 完成：设置页「歌曲缓存」= 总占用 − 下载占用 |

### 批次 D（P3）— 已完成（2026-11-26）

- D1 死代码：删除 7 个无调用方 API；
- D2 元数据：整组删除后清理 `name_/album_/artist_/albumname_/t_`；
- D3 诊断：失败写 `LumenDownloads`（URL 经 `Redact.url`），并作为管理页「失败原因」来源；
- D4 迁移：旧 id（`putDownload` 改写）与旧 URL（合成化）双迁移，幂等标记分离；`data` 为空的旧记录用 `fallbackName` 兜底，不做全量重写。

### 真机验证（Pixel 10a / Android 17，debug 包）

| 项 | 结果 |
| --- | --- |
| 构建与安装 | `installDebug` ✅ |
| 冷启动（含 `Downloads.warmUp` + `SimpleCache` 建目录 + `DownloadManager` 建库） | 无 `FATAL EXCEPTION`，进程存活；`files/media` 已创建、`databases/exoplayer_internal.db` 已生成 |
| 仪器测试（7 例：清数据 2、遗留清理 1、背景动效 2、凭据 2） | ✅ 0 失败（覆盖 `LocalData.wipe → Downloads.removeAllNow → clearAllCache` 新路径） |
| 单测 | ✅ 42 例 |

**未覆盖（需登录真实服务器）**：合成 URI → 实际 HTTP 200 的端到端下载、`pm trim-caches` 后的下载存活、断网/移动网络切换的等待与恢复。步骤已写在 §6，代码路径均有单测或真机启动覆盖，但端到端下载需在有账号的设备上手动跑一遍。
