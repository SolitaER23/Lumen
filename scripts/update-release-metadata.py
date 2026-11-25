#!/usr/bin/env python3
"""Update release metadata after a build.

Usage:
  python scripts/update-release-metadata.py [path/to/app-release.apk] [--dry-run]

与旧版的差别：
  * 不再删除 dist/ 下的历史 APK —— 回滚需要旧包（docs/rollback.md）；
  * SHA256SUMS.txt 按版本追加/更新一行，不再整份覆盖；
  * README.txt 与 docs/distribution.md / README.md 的版本、体积、校验和同步；
  * 打印证书指纹并与 CERT_SHA256 比对，不一致直接失败（防止用错密钥签名）。
"""
from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APK_DEFAULT = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
CERT_SHA256 = "c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37"
BT = chr(96)  # 反引号：用于拼 markdown 的行内代码


def apk_cert_sha256(apk: Path) -> str | None:
    """Best-effort：用 apksigner 读签名证书 SHA-256（本地与 CI 均可用）。"""
    try:
        out = subprocess.run(
            ["apksigner", "verify", "--print-certs", str(apk)],
            capture_output=True, text=True, timeout=120,
        ).stdout
    except (FileNotFoundError, subprocess.SubprocessError):
        return None
    m = re.search(r"SHA-256 digest:\s*([0-9a-fA-F]+)", out)
    return m.group(1).lower() if m else None


def update_sums(dist: Path, target: Path, sha: str, dry: bool) -> list[str]:
    path = dist / "SHA256SUMS.txt"
    lines = [ln for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()] if path.exists() else []
    kept = [ln for ln in lines if not ln.endswith("  " + target.name)]
    kept.append(f"{sha}  {target.name}")
    kept.sort(key=lambda ln: ln.split("  ", 1)[-1])
    if not dry:
        path.write_text("\n".join(kept) + "\n", encoding="utf-8")
    return kept


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", nargs="?", default=str(APK_DEFAULT))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    apk = Path(args.apk)
    if not apk.exists():
        print(f"APK not found: {apk}")
        return 1

    cert = apk_cert_sha256(apk)
    if cert and cert != CERT_SHA256:
        print(f"证书指纹不匹配：\n  实际 {cert}\n  预期 {CERT_SHA256}")
        return 2

    gradle = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    version_name = re.search(r'versionName = "([^"]+)"', gradle).group(1)
    version_code = re.search(r"versionCode = (\d+)", gradle).group(1)
    data = apk.read_bytes()
    sha = hashlib.sha256(data).hexdigest().upper()
    size = len(data)

    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    target = dist / f"Lumen-{version_name}.apk"
    prefix = "[dry-run] " if args.dry_run else ""
    print(f"{prefix}APK {target.name}: {size:,} bytes, sha256={sha}")
    if not args.dry_run:
        shutil.copy2(apk, target)

    sums = update_sums(dist, target, sha, args.dry_run)
    readme = (
        f"Lumen {version_name} (versionCode {version_code})\n"
        f"APK: {target.name}\nSHA-256: {sha}\n"
        f"Certificate SHA-256: {CERT_SHA256}\n"
        "Minimum: Android 8.0 / arm64-v8a\n"
        "Install: allow unknown apps, open APK, accept Terms & Privacy.\n"
        "\n历史版本与校验和见 SHA256SUMS.txt（脚本不再删除旧包，便于回滚）。\n"
    )
    if not args.dry_run:
        (dist / "README.txt").write_text(readme, encoding="utf-8")

    def replace(path: Path, pairs: list[tuple[str, str]]) -> None:
        if not path.exists():
            return
        text = path.read_text(encoding="utf-8")
        for old, new in pairs:
            text = re.sub(old, new, text)
        if not args.dry_run:
            path.write_text(text, encoding="utf-8", newline="\n")

    series = version_name.rsplit(".", 1)[0] + ".x"
    ver_cell = BT + r"\d+\.\d+\.\d+" + BT + " / " + BT + r"versionCode \d+" + BT
    size_cell = BT + r"[\d,]+" + BT + r" 字节（约 [\d.]+ MiB）"
    sha_row = r"\| SHA-256 \| " + BT + r"[0-9A-F]{64}" + BT + r" \|"
    expect_cmd = r"# 应输出 [0-9A-F]{64}"
    apk_name = r"Lumen-\d+\.\d+\.\d+\.apk"
    dist_apk = BT + r"dist/Lumen-\d+\.\d+\.\d+\.apk" + BT

    replace(ROOT / "docs/distribution.md", [
        (ver_cell, BT + version_name + BT + " / " + BT + "versionCode " + version_code + BT),
        (size_cell, BT + f"{size:,}" + BT + f" 字节（约 {size / 1_048_576:.1f} MiB）"),
        (sha_row, "| SHA-256 | " + BT + sha + BT + " |"),
        (expect_cmd, "# 应输出 " + sha),
        (apk_name, target.name),
    ])
    replace(ROOT / "README.md", [
        (r"当前正式直发版本：\*\*\d+\.\d+\.\d+ / versionCode \d+\*\*",
         f"当前正式直发版本：**{version_name} / versionCode {version_code}**"),
        (dist_apk, BT + "dist/" + target.name + BT),
    ])
    for doc in ("privacy-policy.md", "terms-of-service.md"):
        replace(ROOT / "docs" / doc, [(r"适用版本：Lumen Android \d+\.\d+\.x", f"适用版本：Lumen Android {series}")])

    print("SHA256SUMS entries:", len(sums))
    print("Updated: dist/, docs/distribution.md, README.md, docs/privacy-policy.md, docs/terms-of-service.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
