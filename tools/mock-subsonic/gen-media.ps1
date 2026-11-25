# 生成 mock Subsonic 服务器用的测试音频（需要 ffmpeg 在 PATH 中）
# 用法：powershell -File tools/mock-subsonic/gen-media.ps1
$ErrorActionPreference = "Stop"
$dir = Join-Path $PSScriptRoot "media"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$ff = (Get-Command ffmpeg -ErrorAction SilentlyContinue)
if (-not $ff) { throw "找不到 ffmpeg，请先安装（winget install Gyan.FFmpeg）" }

# 六首 30 秒素材：不同基频的正弦 + 粉噪，频谱足够稠密（纯音会让模型输出过于敏感）
$specs = @(
    @{ n = "mock-1.wav"; f = 220 },
    @{ n = "mock-2.wav"; f = 174 },
    @{ n = "mock-3.wav"; f = 130 },
    @{ n = "mock-4.wav"; f = 261 },
    @{ n = "mock-5.wav"; f = 329 },
    @{ n = "mock-6.wav"; f = 392 }
)
foreach ($s in $specs) {
    $out = Join-Path $dir $s.n
    & $ff.Source -v error -y -f lavfi -i "sine=frequency=$($s.f):duration=30:sample_rate=48000" -f lavfi -i "anoisesrc=d=30:c=pink:a=0.02:r=48000" -filter_complex "[0:a][1:a]amix=inputs=2:duration=shortest,volume=2.0" -ac 1 -ar 48000 -c:a pcm_s16le $out
}
& $ff.Source -v error -y -f lavfi -i "color=c=0xE04A5F:s=300x300" -frames:v 1 (Join-Path $dir "cover.png")
Write-Host "generated media in $dir"