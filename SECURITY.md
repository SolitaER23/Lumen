# 安全策略

## 报告问题

如果你发现安全相关问题（凭据泄露、越权访问、恶意串流、依赖漏洞等），请不要公开开 Issue，通过 B 站私信联系：

**bilibili@晓月枫**  
https://space.bilibili.com/20567705

私信中请尽量包含：

- 受影响版本（设置 → 关于 → 版本）
- 复现步骤与预期结果
- 是否需要特定服务器配置（Navidrome 版本、HTTP/HTTPS、Navidrome Stat）
- 如果涉及日志，请先移除账号、Token、内网地址

我会在确认后尽快回复，并在修复版本发布后致谢（如你愿意）。

## 范围

- 本仓库的 Android 客户端代码与打包资源
- 不覆盖用户自建的 Navidrome、反向代理或 Navidrome Stat 服务本身

## 已知设计取舍

- 支持 HTTP 自托管服务器（`usesCleartextTraffic`），因此请尽量在可信内网使用，或为服务器配置 HTTPS；
- 账号密码与统计 Token 使用 Android Keystore 加密存储，但仍建议为 Navidrome 使用独立账号；
- 应用不包含分析 SDK，也不上传曲库或播放数据。
