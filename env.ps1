# 在新开的 PowerShell 里执行： . .\env.ps1   （注意前面的点和空格）
# 之后 java / cargo / gradle / adb / cargo ndk 都能直接用。
# 说明：用户级环境变量其实已经写好，新终端本来就带这些设置；这个脚本用于“给当前这个终端补上”。

$__prev_eap = $ErrorActionPreference
$ErrorActionPreference = 'SilentlyContinue'

# Rust 装在标准位置（~/.cargo、~/.rustup），不需要 CARGO_HOME / RUSTUP_HOME
$env:JAVA_HOME        = "C:\toolchains\jdk17"
$env:ANDROID_HOME     = "C:\toolchains\android-sdk"
$env:ANDROID_SDK_ROOT = "C:\toolchains\android-sdk"
$env:ANDROID_NDK_HOME = "C:\toolchains\android-sdk\ndk\27.2.12479018"
$env:ANDROID_NDK_ROOT = "C:\toolchains\android-sdk\ndk\27.2.12479018"

$prefixes = @(
    "$env:USERPROFILE\.cargo\bin",
    "C:\toolchains\msys64\mingw64\bin",
    "C:\toolchains\jdk17\bin",
    "C:\toolchains\android-sdk\platform-tools",
    "C:\toolchains\android-sdk\cmdline-tools\latest\bin",
    "C:\toolchains\gradle\bin"
)
foreach ($p in $prefixes) {
    if (($env:PATH -split ';') -notcontains $p) { $env:PATH = "$p;$env:PATH" }
}

$rustcVer = ((& rustc -V) 2>&1 | Out-String).Trim()
$javaRaw = ((& java -version) 2>&1 | Out-String)
$javaVer = [regex]::Match($javaRaw, '(\d+\.\d+\.\d+[^\s"]*)').Groups[1].Value

Write-Host "环境已就绪："
Write-Host "  rustc   $rustcVer"
Write-Host "  java    $javaVer"
Write-Host "  ndk     $env:ANDROID_NDK_HOME"
Write-Host "  sdk     $env:ANDROID_HOME"

$ErrorActionPreference = $__prev_eap
