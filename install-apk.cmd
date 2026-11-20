@echo off
setlocal
set SDK=C:\toolchains\android-sdk
set ADB=%SDK%\platform-tools\adb.exe

"%ADB%" devices
echo.
echo Waiting for a device (enable USB debugging on the phone)...
"%ADB%" wait-for-device

rem 选 dist\ 下最新的可安装 APK（跳过 unsigned 的 release 包）
set APK=
for /f "delims=" %%f in ('dir /b /o-d "%~dp0dist\*.apk" 2^>nul ^| findstr /v /i "unsigned"') do if not defined APK set APK=%%f
if not defined APK (
  echo No installable APK in dist\. Run build.cmd first.
  exit /b 1
)

echo Installing dist\%APK% ...
"%ADB%" install -r "%~dp0dist\%APK%"
if errorlevel 1 (
  echo Install failed.
  exit /b 1
)
"%ADB%" shell am start -n com.example.app/com.google.androidgamesdk.GameActivity
echo.
echo Logs: "%ADB%" logcat -s app
endlocal