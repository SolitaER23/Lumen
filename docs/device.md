# 测试设备档案

> 手动维护。最后更新：2026-11-23
>
> **只维护一台目标机**：Google Pixel 10a。其他机型、平板、模拟器不再作为验证对象。
> 应用只提供**手机单栏布局**：曾经的宽屏双栏与折叠屏姿态支持已移除（见 `UI_UX_PLAN.md` §8），
> 大屏设备会把界面按单栏拉伸显示，不再有左右分栏。

## 目标机：Google Pixel 10a

| 项 | 值 |
| --- | --- |
| 厂商 / 品牌 | Google / Pixel |
| 机型 | Pixel 10a |
| Android 版本 | 17（API 37，arm64） |
| 编译/目标 SDK | `compileSdk 37` / `targetSdk 37` |
| 主要 ABI | arm64-v8a |
| 屏幕 / 密度 | 1080×2424 @420dpi（2.625×） |
| 逻辑尺寸 | **411×923 pt**（竖屏）；挖孔 cutout top 152px，圆角 115px |
| 刷新率 | 120 / 60 / 40 / 30 / 24 / 20 Hz；应用在 `MainActivity` 固定请求 120Hz（`preferredRefreshRate = 120f`） |

## 连接与验证

```powershell
adb devices -l                                   # 确认序列号
cd android
.\gradlew.bat :app:testDebugUnitTest             # 单测
.\gradlew.bat :app:assembleRelease               # 正式签名包
adb -s <serial> install -r app\build\outputs\apk\release\app-release.apk
adb -s <serial> shell am force-stop app.lumen.player
adb -s <serial> shell am start -n app.lumen.player/.MainActivity
adb -s <serial> logcat -d -s Lumen LumenPlay LumenMood   # 仅 Debug 构建有 Logx 输出
adb -s <serial> exec-out screencap -p > shot.png
```

说明：

- 唯一入口是 `app.lumen.player/.MainActivity`；
- `Logx` 的打桩日志只在 `BuildConfig.DEBUG` 下输出，Release 版请用 `adb logcat` 观察系统层日志与崩溃栈；
- 无线调试时先 `adb connect <ip>:5555`；adb daemon 偶发重启会丢连接，每次操作前重新连接。

## 换机时需要重新确认的项

- `wm size` / `wm density` → 决定瓷砖列数与逻辑尺寸；
- 状态栏 / 导航栏 / 挖孔安全区；
- 面板刷新率是否真的切到 120Hz；
- 16 KB page 支持：`llvm-readelf -l` 查看 `.so` 的 `LOAD` 对齐是否为 `0x4000`。

## 打包注意事项

- 只出 `arm64-v8a`（`abiFilters` 已收敛，不再编 x86_64）；
- Release 必须使用正式证书：读取 `android/keystore.properties` 或环境变量 `LUMEN_KEYSTORE_FILE` / `LUMEN_KEYSTORE_PASSWORD` / `LUMEN_KEY_ALIAS` / `LUMEN_KEY_PASSWORD`；缺密钥时产出 **unsigned APK**，不会回退 debug 签名；
- 安装报 `INSTALL_FAILED_OLDER_SDK` 说明系统 API 低于 `minSdk 26`；
- 全面屏手势导航下系统报的 safe-area bottom 是 0，界面对播放条自行补了 10pt 下限。
