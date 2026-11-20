@echo off
setlocal
rem Usage: build.cmd            -> debug APK (installable)
rem        build.cmd -Release   -> release APK (unsigned)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
if errorlevel 1 (
  echo Build failed. See the errors above.
  exit /b 1
)
endlocal