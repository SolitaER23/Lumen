# Lumen 直发交付说明

## 当前发布候选

| 项目 | 值 |
| --- | --- |
| 版本 | `0.50.0` / `versionCode 138` |
| APK | `app-release.apk` |
| 文件大小 | `11,767,694` 字节（约 11.2 MiB） |
| SHA-256 | `F56CC6A8BB8C70CC0EC64A5E6D428A7F7816A6D51EE6A2ED907A777C772C9248` |
| 签名证书 DN | `CN=Lumen, OU=Release, O=Lumen, L=Shanghai, ST=Shanghai, C=CN` |
| 证书 SHA-256 | `c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37` |
| 最低系统 | Android 8.0（API 26） |
| 目标系统 | Android 16（API 36） |
| ABI | `arm64-v8a` |
| 分发方式 | 直接下载 APK（不上架应用商店） |

## 安装

1. 下载 APK；
2. 允许浏览器/文件管理器“安装未知应用”；
3. 安装后首次启动先阅读并同意用户协议与隐私条款；
4. 填写自建 Navidrome / OpenSubsonic 地址与账号。

校验示例：

```bash
sha256sum app-release.apk
# 应输出 275830DACDD8F33C248F323FB58D3071496EE3E21E23DC5487D7BCD31C11CFA5
```

```bash
apksigner verify --print-certs app-release.apk
# 证书 SHA-256 应为 c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37
```

## 更新

- 必须使用**同一签名证书**的新 APK 覆盖安装；
- **0.50.0 起更换为正式证书**：早期 debug 签名测试版不能覆盖升级，需要卸载重装；卸载会清除本机账号、历史、评分、心情分析与下载；
- 更新前建议记录服务器地址与账号；
- 发布页应同时提供新 APK 的 SHA-256 与更新说明。

## 卸载

- 卸载会删除本机数据（应用不启用云备份）；
- 如需保留使用记录，请先自行记录服务器与账号信息；
- 设置 → 账号 →「清除本机数据」可在不卸载时清除账号、历史、评分、心情、统计配置、下载与缓存。

## 支持渠道

- 问题反馈：**bilibili@晓月枫**  
  https://space.bilibili.com/20567705
- 反馈时请附：版本号、机型、Android 版本、服务器类型（Navidrome 版本、HTTP/HTTPS）、复现步骤；
- 设置 → 关于 → 问题反馈 →「复制诊断信息」可生成脱敏信息。

## 法律文本

- 隐私条款：`docs/privacy-policy.md`
- 用户协议：`docs/terms-of-service.md`
- 应用内：首次启动确认页；设置 → 隐私条款；设置 → 关于 → 用户协议入口（条款确认页）

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

- [ ] `assembleRelease` 使用正式 keystore，未回退 debug 签名
- [ ] `apksigner verify` 证书与上表一致
- [ ] 16 KB page 校验：`llvm-readelf -l` 的 LOAD Align 为 `0x4000`
- [ ] 设备端 YAMNet 冒烟测试通过（`connectedDebugAndroidTest`）
- [ ] SHA-256 与发布页一致
- [ ] 在 API 26 / 33 / 36 arm64 设备完成冒烟：登录、播放、后台播放、下载、通知、分析、电台
- [ ] 同一签名覆盖安装上一版，账号/历史/评分/心情数据保留
- [ ] 首次启动同意流程可拒绝并退出；同意后不再重复
- [ ] HTTP 登录提示、通知权限、诊断复制可用
- [ ] 隐私条款与用户协议可访问且无过期描述
- [ ] 保留 `mapping.txt` 与 keystore 离线备份
