# 一键构建 APK：
#   .\build.ps1              # debug APK（快）
#   .\build.ps1 -Release     # release APK（体积小、需 LTO，慢一些）
param(
    [switch]$Release,
    [string[]]$Abis = @("arm64-v8a")
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1" | Out-Null

$root = $PSScriptRoot
$profile = if ($Release) { "--release" } else { "" }
$gradleTask = if ($Release) { "assembleRelease" } else { "assembleDebug" }

# 1) 用 cargo-ndk 交叉编译 Rust cdylib，直接输出到 Gradle 的 jniLibs 目录
$ndkArgs = @("ndk")
foreach ($abi in $Abis) { $ndkArgs += @("-t", $abi) }
$ndkArgs += @("-P", "26", "-o", "android/app/src/main/jniLibs", "build")
if ($Release) { $ndkArgs += "--release" }

Write-Host "==> cargo $($ndkArgs -join ' ')" -ForegroundColor Cyan
Push-Location $root
& cargo @ndkArgs
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "cargo ndk build 失败" }
Pop-Location

# 2) Gradle 打包 APK
# 注意：cargo 每次都会重写 jniLibs/libapp.so，而 AGP 的增量打包会把它**再写一遍**
# 却不更新中央目录 —— APK 变成 70MB（多出 34MB 孤儿数据）。清掉这两个中间产物即可。
Remove-Item "$root\android\app\build\intermediates\merged_native_libs" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$root\android\app\build\outputs\apk" -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "==> gradle $gradleTask" -ForegroundColor Cyan
Push-Location "$root\android"
& gradle $gradleTask --console=plain --no-daemon
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "gradle $gradleTask 失败" }

# 3) 收集产物
$apk = Get-ChildItem "$root\android\app\build\outputs\apk" -Recurse -Filter "*.apk" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force $dist | Out-Null
$dest = Join-Path $dist $apk.Name
Copy-Item $apk.FullName $dest -Force

Write-Host ""
Write-Host "APK 已生成： $dest" -ForegroundColor Green
Write-Host ("大小： {0:N1} MB" -f ((Get-Item $dest).Length / 1MB))
Write-Host ""
Write-Host "安装到手机（先打开 USB 调试）： adb install -r `"$dest`""
