# 测试设备档案

> 手动维护。最后更新：2026-11-20
>
> **只维护一台目标机**：Google Pixel 10a。其他机型、平板、模拟器不再作为验证对象。

## 目标机：Google Pixel 10a

| 项 | 值 |
| --- | --- |
| 序列号 | `192.0.2.14:42187`（无线调试；mDNS：`adb-DEMO-DEVICE`） |
| 厂商 / 品牌 | Google / Pixel |
| 机型 / 代号 | Pixel 10a / `stallion` |
| Android 版本 | 16（API 36，build `CP1A.260305.018`） |
| 主要 ABI | arm64-v8a |
| 屏幕 / 密度 | 1080×2424 @420dpi（2.625×） |
| 逻辑尺寸 | **411×923 pt**（竖屏）；挖孔 cutout top 152px，圆角 115px |
| 刷新率 | 120 / 60 / 40 / 30 / 24 / 20 Hz，默认 mode 2 = 120Hz；App 钉 120Hz，动画实测 121 fps |

### 连接与验证

```powershell
adb devices -l                                  # 确认序列号
.\build.ps1 -Abis arm64-v8a                     # 只编 arm64
adb -s <serial> install -r .\dist\app-debug.apk
adb -s <serial> shell am force-stop com.example.app
adb -s <serial> shell am start -n com.example.app/com.google.androidgamesdk.GameActivity
adb -s <serial> logcat -d | Select-String "app::"     # 日志 tag 是模块路径：app::app / app::audio / ...
adb -s <serial> exec-out screencap -p > shot.png
```

无线调试时先 `adb connect <ip>:5555`；adb daemon 偶发重启会丢连接，每次操作前重新连接。

### 换机时需要重新确认的项

- `wm size` / `wm density` → 决定 `k`（屏高/760）与瓷砖列数；
- 状态栏/导航栏/挖孔安全区（`safe_area` 打印的 top/bottom px）；
- 面板刷新率与 `ANativeWindow_setFrameRateWithChangeStrategy(120, FIXED_SOURCE+ALWAYS)` 是否真的切到高刷；
- 16 KB 页支持（Pixel 已按 16 KB 对齐，`llvm-readelf -l` 看 `LOAD ... 0x4000`）。

## 打包注意事项

- 只出 `arm64-v8a`（`abiFilters` 与 `build.ps1` 默认值都已收敛），不再编 x86_64；
- `build.ps1` 里 `$ErrorActionPreference = "Stop"`，**不要**给 cargo/gradle 加 `2>&1`，
  cargo 的进度输出走 stderr，会被 PowerShell 当成错误中断构建；
- 如果安装报 `INSTALL_FAILED_OLDER_SDK`，说明系统 API 低于 `minSdk 26`，需要下调 `minSdk`；
- 全面屏手势导航下，系统报的 safe-area bottom 是 0，界面对播放条自己补了 10pt 下限。
