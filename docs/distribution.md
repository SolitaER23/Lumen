# Lumen 直发交付说明

## 当前发布包

| 项目 | 值 |
| --- | --- |
| 版本 | `0.92.1` / `versionCode 229` |
| APK | `Lumen-0.92.1.apk` |
| 文件大小 | `3,365,994` 字节（约 3.2 MiB） |
| SHA-256 | `D9DAF64162EBE77327CE3EB68EAEFCF3F6380BDAC132D5057F52493B7890B43B` |
| 签名证书 DN | `CN=Lumen, OU=Release, O=Lumen, L=Shanghai, ST=Shanghai, C=CN` |
| 证书 SHA-256 | `c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37` |
| 最低系统 | Android 8.0（API 26） |
| 编译 SDK | 37.2（Android 17） |
| 目标系统 | Android 17（API 37） |
| ABI | `arm64-v8a` |
| 构建工具链 | Gradle 9.9.2 + AGP 9.5.2 + Kotlin 2.4.21 |
| 分发方式 | 直接下载 APK（不上架应用商店） |

> 0.51.0 将编译 / 目标 SDK 升到 Android 17（API 37），并全量升级依赖；相比 0.50.5（11.7 MB），
> 包体增大到约 13.1 MiB。签名证书未变，可从 0.50.x 直接覆盖安装并保留本机数据。

### 0.76.0 验证（2026-11-25）

本版只动图标资源，验证重点是「包能装、图能显示、数据不丢」：

| 项 | 结果 | 关键记录 |
| --- | --- | --- |
| `assembleRelease`（正式 keystore + R8 + lint vital） | ✅ 通过 | 41 s，产物 158,112,683 字节 |
| `apksigner verify` | ✅ 证书与上表一致 | DN 与 SHA-256 均未变 |
| `aapt2 dump badging` | ✅ | `versionName 0.76.0` / `versionCode 206` / `targetSdk 37` / `arm64-v8a`，label `Lumen` |
| 覆盖安装 0.75.1(205) → 0.76.0(206) | ✅ 同签名升级 | `firstInstallTime` 保留（2026-11-23 19:28），账号/曲库/历史未丢 |
| 新图标（桌面与应用抽屉） | ✅ | 深紫渐变 + 暖光 + 光芒正常铺满遮罩，无白底、无裁切；背景层随视差移动 |
| 冷启动 | ✅ | `am start -W` COLD 138 ms，`logcat -b crash` 无记录 |
| 应用内「更新日志」 | ✅ | APK 内 `assets/CHANGELOG.md` 顶部已是 `[0.76.0]` |

> 主题图标（Android 13+ 单色层）已打进 `<monochrome>` 并被系统识别，但本机未开启「主题图标」开关，
> 尚未做单色形态的目视确认；开关打开后系统会自动取单色层着色。

## 安装

1. 下载 APK；
2. 允许浏览器/文件管理器“安装未知应用”；
3. 安装后首次启动先阅读并同意用户协议与隐私条款；
4. 填写自建 Navidrome / OpenSubsonic 地址与账号。

校验示例：

```bash
sha256sum Lumen-0.92.1.apk
# 应输出 D9DAF64162EBE77327CE3EB68EAEFCF3F6380BDAC132D5057F52493B7890B43B
```

```bash
apksigner verify --print-certs Lumen-0.92.1.apk
# 证书 SHA-256 应为 c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37
```

## 更新

**官方更新渠道：GitHub Releases**  
链接：https://github.com/SolitaER23/Lumen/releases

- 仅从上述官方渠道下载新 APK，并核对本文件与 `dist/SHA256SUMS.txt` 中的 SHA-256；
- 不要使用来源不明的转存或二次打包版本；
- 必须使用**同一签名证书**的新 APK 覆盖安装；
- **0.50.0 起更换为正式证书**：早期 debug 签名测试版不能覆盖升级，需要卸载重装；卸载会清除本机账号、历史、评分与下载；
- 更新前建议记录服务器地址与账号。

## 卸载

- 卸载会删除本机数据（应用不启用云备份）；
- 如需保留使用记录，请先自行记录服务器与账号信息；
- 设置 → 账号 →「清除本机数据」可在不卸载时清除账号、历史、评分、统计配置、下载与缓存。

## 支持渠道

- 问题反馈：**bilibili@晓月枫**  
  https://space.bilibili.com/20567705
- 反馈时请附：版本号、机型、Android 版本、服务器类型（Navidrome 版本、HTTP/HTTPS）、复现步骤；
- 设置 → 关于 → 问题反馈 →「复制诊断信息」可生成脱敏信息。

## 法律文本

- 隐私条款：`docs/privacy-policy.md`
- 用户协议：`docs/terms-of-service.md`
- 应用内：首次启动确认页；设置 → 隐私条款；条款页可查看用户协议
- **在线版本尚未上线**：目前无法上线，也不计划上线；以仓库内 `docs/` 文本与应用内条款页为唯一有效版本

## 验证状态（2026-11-23）

**设备**：Pixel 10a（`stallion`）/ Android 17（SDK 37，`CP3A.261005.005`）/ arm64-v8a / 无线调试

### 构建与签名

| 项 | 结果 |
| --- | --- |
| `assembleRelease`（正式签名） | ✅ 通过 |
| `testDebugUnitTest` | ✅ 26 个用例全部通过 |
| `apksigner verify` | ✅ 证书与上表一致 |
| `aapt2 dump badging` | ✅ `versionName 0.52.0` / `versionCode 146` / `targetSdk 37` / `arm64-v8a` |
| R8 保留检查 | ✅ `mapping.txt` 含 6377 条 `coil3.*`，`OkHttpNetworkFetcherFactory` 未被裁剪 |

### Coil 3 迁移验证

| 项 | 结果 | 关键记录 |
| --- | --- | --- |
| 设备端 `CoilNetworkSmokeTest`（debug 变体，同一份 `LumenApp` 配置） | ✅ | 本机 loopback HTTP → `SuccessResult` 1×1 PNG |
| 磁盘缓存目录 | ✅ | `loader.diskCache.directory` = `/data/user/0/app.lumen.player/cache/image_cache`，与「清理缓存」删除路径一致 |

### 真机回归（Pixel 10a / Android 17）

| 项 | 结果 | 关键记录 |
| --- | --- | --- |
| 覆盖安装 0.50.5(143) → 0.51.0(144) | ✅ | 同签名升级成功，`firstInstallTime` 保留 |
| 覆盖安装 0.51.0(144) → 0.51.1(145) | ✅ | 同签名升级成功 |
| 覆盖安装 0.51.1(145) → 0.52.0(146) | ✅ | 同签名升级成功，账号与 95 张曲库保留 |
| 冷启动 | ✅ | `am start -W` 140–186 ms |
| 崩溃 / ANR | ✅ 无 | `logcat -b crash` 为空，`AndroidRuntime` 无记录 |
| 登录与曲库同步 | ✅ | 0.51.0 回归时 95 张专辑，封面与动态取色正常 |
| 播放 | ✅ | MediaSession `state=PLAYING`，元数据正确 |
| 后台播放与通知 | ✅ | `lumen_playback` 渠道 MediaStyle 前台通知，切后台继续播放 |
| 条款门禁 | ✅ | 用户协议 / 隐私条款分别滚动到底后才可同意；在线版本文案显示正确 |
| 权限流程 | ✅ | Android 17 首次启动请求「本地网络」权限；授权后自动重试登录成功 |
| 0.53.0 浅色模式 | ✅ | 外观 → 深浅模式切「浅色」：设置页/首页立即转浅色，首页标题与专辑名自动转深色，状态栏图标同步反转 |
| 0.53.0 主题设置 | ✅ | 外观新增深浅模式/配色风格/对比度三档/六个颜色开关/专辑锁色，真机切换生效 |
| 0.53.0 配色来源 | ✅ | 设置页底部显示「配色来源：…」，取色失败显示「已降级」 |
| 0.52.0 服务端探测 | ✅ | Navidrome 0.64.2：转码流缓存命中时 Range 206 + Content-Range；冷曲目返回 200 无 Range；据此冷缓存逐首回退整段 |
| 0.52.0 歌词交互 | ✅ | 手动滚动可正常翻页且不触发跳转；点按任意行跳转到该行时间（1:15 → 0:34 实测） |
| 0.52.0 四类缓存 | ✅ | 存储页分别显示歌曲 347 MB / 封面 7.7 MB / 歌词 0 MB / 异常 0 条，各自可清 |
| 0.52.0 下载体系 | ✅ | 专辑按钮显示 0/28 → 28/28 进度；播放页艺人旁出现「已下载」徽标 |
| 0.52.0 下载管理 | ✅ | 专辑可展开查看 28 首曲目（标题/体积/状态），支持单曲删除与整组删除 |
| 0.52.0 设置重组 | ✅ | 关于：版本→检查更新→开源信息→用户协议→隐私条款→使用引导→问题反馈；外观仅对比度，播放衔接独立成节 |
| 0.52.0 迷你条 | ✅ | 只显示曲名与艺人，不再显示声音子标签 |

### 已知问题与注意事项

- **Android 17 本地网络权限**：0.51.0 之前的构建在 Android 17 上会因缺少 `ACCESS_LOCAL_NETWORK` 而报「无法连接服务器」（网络本身可达）；0.51.0 起已修复，但**从旧版本升级的用户首次启动需要允许该权限**。
- **数据清空**：在真机上跑 `connectedDebugAndroidTest` 时，AGP 会先卸载应用，导致本机账号、队列与评分数据被清空，需重新登录。日常验证请避免在有数据的主力机上直接跑 instrumentation。
- **release 变体的 instrumentation 跑不起来**：`testBuildType = "release"` 时 JUnit runner 依赖的 `androidx.tracing` / Kotlin stdlib 内部类会被 R8 裁掉，进程在 `AndroidJUnitRunner.onCreate` 即崩溃（与 Coil 无关）。因此混淆包内的封面渲染目前靠「R8 mapping 保留 + 启动无崩溃」间接确认；**最终目视建议登录后看一眼首页封面**。

## 维护者：签名与发布

签名材料（**不要提交到仓库**）：

- `android/lumen-release.jks`
- `android/keystore.properties`（已被 `.gitignore` 排除）
- 离线备份：`target/keystore-backup/`（含说明，同样被忽略）

CI 手动 release 使用以下 secrets：

- `LUMEN_KEYSTORE_BASE64`：keystore 的 Base64
- `LUMEN_KEYSTORE_PASSWORD`
- `LUMEN_KEY_ALIAS`
- `LUMEN_KEY_PASSWORD`

产物：`.github/workflows/release.yml` 会归档 APK、`apk.sha256`、证书输出与 `mapping.txt`。

## 发布检查清单

- [x] `assembleRelease` 使用正式 keystore，未回退 debug 签名
- [x] `apksigner verify` 证书与上表一致
- [x] `versionName` / `versionCode` / `targetSdk` 与 build.gradle.kts 一致
- [ ] 16 KB page 校验：`llvm-readelf -l` 的 LOAD Align 为 `0x4000`
- [x] SHA-256 与分发包一致（`sha256sum -c`）
- [ ] 在 Pixel 10a 完成冒烟：登录、播放、后台播放、下载、通知、搜索
- [ ] 同一签名覆盖安装上一版，账号/历史/评分数据保留
- [ ] 首次启动同意流程可拒绝并退出；同意后不再重复
- [ ] HTTP 登录提示、通知权限、诊断复制可用
- [ ] 保留 `mapping.txt` 与 keystore 离线备份
