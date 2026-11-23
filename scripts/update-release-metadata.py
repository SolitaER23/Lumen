#!/usr/bin/env python3
"""Update release metadata and key attachments after a release build.

Usage:
  python scripts/update-release-metadata.py [path/to/app-release.apk]

It copies the APK into dist/, writes SHA256SUMS.txt and README.txt, and keeps
docs/distribution.md, README.md, docs/privacy-policy.md and docs/terms-of-service.md
in sync with the current version.
"""
from __future__ import annotations

import hashlib
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APK_DEFAULT = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
CERT_SHA256 = "c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37"


def main() -> int:
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else APK_DEFAULT
    if not apk.exists():
        print(f"APK not found: {apk}")
        return 1

    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    version_name = re.search(r'versionName = "([^"]+)"', gradle).group(1)
    version_code = re.search(r"versionCode = (\d+)", gradle).group(1)
    data = apk.read_bytes()
    sha = hashlib.sha256(data).hexdigest().upper()
    size = len(data)

    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    for old in dist.glob("Lumen-*.apk"):
        old.unlink()
    target = dist / f"Lumen-{version_name}.apk"
    shutil.copy2(apk, target)
    (dist / "SHA256SUMS.txt").write_text(f"{sha}  {target.name}\n", encoding="ascii")
    (dist / "README.txt").write_text(
        f"Lumen {version_name} (versionCode {version_code})\n"
        f"APK: {target.name}\nSHA-256: {sha}\n"
        f"Certificate SHA-256: {CERT_SHA256}\n"
        "Minimum: Android 8.0 / arm64-v8a\n"
        "Install: allow unknown apps, open APK, accept Terms & Privacy.\n",
        encoding="utf-8",
    )

    def replace(path: Path, pairs: list[tuple[str, str]]) -> None:
        text = path.read_text(encoding="utf-8")
        for old, new in pairs:
            text = re.sub(old, new, text)
        path.write_text(text, encoding="utf-8", newline="\n")

    series = version_name.rsplit(".", 1)[0] + ".x"
    replace(
        ROOT / "docs/distribution.md",
        [
            (r"`0\.\d+\.\d+` / `versionCode \d+`", f"`{version_name}` / `versionCode {version_code}`"),
            (r"`\d{1,3}(?:,\d{3})+` 字节（约 [\d.]+ MiB）", f"`{size:,}` 字节（约 {size / 1_048_576:.1f} MiB）"),
            (r"\| SHA-256 \| `[0-9A-F]{64}` \|", f"| SHA-256 | `{sha}` |"),
            (r"# 应输出 [0-9A-F]{64}", f"# 应输出 {sha}"),
            (r"Lumen-\d+\.\d+\.\d+\.apk", target.name),
            (
                r"- 已安装：`Lumen-\d+\.\d+\.\d+\.apk`",
                f"- 已安装：`{target.name}`",
            ),
            (
                r"- `versionName \d+\.\d+\.\d+` / `versionCode \d+`",
                f"- `versionName {version_name}` / `versionCode {version_code}`",
            ),
            (
                r"- 安装包 SHA-256 与 `dist/Lumen-\d+\.\d+\.\d+\.apk` 完全一致：`[0-9A-F]{64}`",
                f"- 安装包 SHA-256 与 `dist/{target.name}` 完全一致：`{sha}`",
            ),
        ],
    )
    replace(
        ROOT / "README.md",
        [
            (
                r"当前正式直发版本：\*\*0\.\d+\.\d+ / versionCode \d+\*\*",
                f"当前正式直发版本：**{version_name} / versionCode {version_code}**",
            ),
            (r"`dist/Lumen-\d+\.\d+\.\d+\.apk`", f"`dist/{target.name}`"),
        ],
    )
    for doc in ("privacy-policy.md", "terms-of-service.md"):
        replace(
            ROOT / "docs" / doc,
            [(r"适用版本：Lumen Android \d+\.\d+\.x", f"适用版本：Lumen Android {series}")],
        )

    print(f"Updated {target} ({size} bytes)")
    print(f"SHA-256 {sha}")
    print(f"Version {version_name} ({version_code})")
    print("Key attachments updated: distribution, privacy, terms, dist/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
