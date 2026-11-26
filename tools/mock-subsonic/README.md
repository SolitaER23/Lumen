# Mock Subsonic 服务器（开发联调用）

一个只实现 Lumen 实际调用端点的最小 Subsonic/OpenSubsonic 服务，用于在没有 Navidrome 的环境下跑通
「登录 → 曲库 → 取流 → 播放」整条链路。**仅供本地测试，不要暴露到公网。**

## 启动

```powershell
# 1) 生成测试音频（需要 ffmpeg）
powershell -File tools/mock-subsonic/gen-media.ps1

# 2) 启动服务（默认 8899）
python tools/mock-subsonic/server.py --port 8899
```

账号固定为 `test` / `testpass`；曲库是 2 张专辑、每张 3 首 30 秒素材（正弦 + 粉噪）。

认证按 Subsonic 规则校验：`t == md5(password + s)`，所以客户端的 token 实现不正确时会直接 40 错误。

## 端到端测试

```powershell
cmd /c "gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=app.lumen.player.ServerE2ETest `
  -Pandroid.testInstrumentationRunnerArguments.serverBase=http://<本机局域网IP>:8899"
```

`ServerE2ETest` 会自己申请 `ACCESS_LOCAL_NETWORK`（Android 17 起没有该权限连不上局域网），
然后验证：ping → 专辑/曲目列表 → 收藏/上报 → 取流 → ExoPlayer 起播。

## 已知限制

- 只支持 GET 的 `rest/*.view`，不实现 Range/转码（客户端会退化成整段读取）；
- `stream` 返回 WAV，因此曲目 `suffix` 固定为 `wav`；
- 不校验 `v`/`c` 参数，不实现播放列表、书签等未使用的接口。
