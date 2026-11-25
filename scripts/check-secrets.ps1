<#
.SYNOPSIS
  仓库密钥卫生检查：确认签名材料与明文口令没有落进仓库，也没有被 git 跟踪。

.DESCRIPTION
  检查三类问题：
   1) 工作区出现 *.jks / *.keystore / *.p12 / *.pfx 私钥文件；
   2) 出现含非空 storePassword / keyPassword 的 properties 文件；
   3) 上述文件被 git 跟踪（历史泄露需用 git log -S 另行排查）。
  发现问题返回 1，可用于本地检查或 CI 前置步骤。

.EXAMPLE
  powershell -NoProfile -File scripts/check-secrets.ps1
#>
param(
    [string]$Root = (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
)

$ErrorActionPreference = "Stop"
$problems = New-Object System.Collections.Generic.List[string]
$ignoreRe = [regex]'[\\/](\.git|build|\.gradle|target|dist)[\\/]'

foreach ($ext in @("*.jks", "*.keystore", "*.p12", "*.pfx")) {
    Get-ChildItem -Path $Root -Recurse -File -Filter $ext -ErrorAction SilentlyContinue |
        Where-Object { -not $ignoreRe.IsMatch($_.FullName) } |
        ForEach-Object { $problems.Add("私钥文件在仓库内: " + $_.FullName) }
}

Get-ChildItem -Path $Root -Recurse -File -Include "keystore.properties", "secrets.properties", ".env" -ErrorAction SilentlyContinue |
    Where-Object { -not $ignoreRe.IsMatch($_.FullName) } |
    ForEach-Object {
        $text = Get-Content $_.FullName -Raw
        if ($text -match "(?m)^\s*(storePassword|keyPassword)\s*=\s*\S+") {
            $problems.Add("明文口令出现在: " + $_.FullName)
        }
    }

Push-Location $Root
try {
    $tracked = git ls-files 2>$null
    foreach ($pattern in @('\.jks$', '\.keystore$', '(^|/)keystore\.properties$')) {
        $hit = $tracked | Select-String -Pattern $pattern
        if ($hit) { $problems.Add("被 git 跟踪: " + ($hit -join ", ")) }
    }
} finally {
    Pop-Location
}

if ($problems.Count -gt 0) {
    Write-Host "check-secrets: 发现 $($problems.Count) 个问题" -ForegroundColor Red
    $problems | ForEach-Object { Write-Host ("  - " + $_) -ForegroundColor Red }
    Write-Host "签名材料请放在仓库外（~/.lumen/），见 docs/plans/optimization-plan.md A2。"
    exit 1
}

Write-Host "check-secrets: OK（未发现仓库内的签名材料或明文口令）" -ForegroundColor Green
exit 0
