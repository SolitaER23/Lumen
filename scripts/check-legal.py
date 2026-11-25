#!/usr/bin/env python3
"""法务文本完整性检查。

检查 android/app/src/main/assets/legal/*.json：
  1) 是否存在未替换的占位符 {{XXX}}（法律主体、联系方式、适用法域等）；
  2) 中英文段落数量是否一致（漏译会导致英文用户读到中文残留）；
  3) version / updated 字段是否存在。

任一问题返回 1。CI 在打包前调用，避免把模板文本发给用户。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent
LEGAL_DIR = ROOT / "android/app/src/main/assets/legal"
PLACEHOLDER = re.compile(r"\{\{([A-Z_]+)\}\}")


def check_file(path: Path) -> list[str]:
    problems: list[str] = []
    text = path.read_text(encoding="utf-8")

    placeholders = sorted(set(PLACEHOLDER.findall(text)))
    for key in placeholders:
        problems.append(f"{path.name}: 未替换占位符 {{{{{key}}}}}")

    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        problems.append(f"{path.name}: JSON 解析失败 ({exc})")
        return problems

    if not data.get("version"):
        problems.append(f"{path.name}: 缺少 version 字段")
    if not data.get("updated"):
        problems.append(f"{path.name}: 缺少 updated 字段")

    sections = data.get("sections") or []
    if not sections:
        problems.append(f"{path.name}: sections 为空")

    for i, section in enumerate(sections, start=1):
        zh = section.get("zh") or {}
        en = section.get("en") or {}
        if not zh.get("title"):
            problems.append(f"{path.name}: 第 {i} 节缺少中文标题")
        if not en.get("title"):
            problems.append(f"{path.name}: 第 {i} 节缺少英文标题")

        def count(body: dict) -> int:
            return len(body.get("body") or []) + len(body.get("paragraphs") or []) + len(body.get("bullets") or [])

        if count(zh) and count(en) and count(zh) != count(en):
            problems.append(
                f"{path.name}: 第 {i} 节中英条目数不一致 (zh={count(zh)}, en={count(en)})"
            )
    return problems


def main() -> int:
    if not LEGAL_DIR.exists():
        print(f"check-legal: 法务文本目录不存在: {LEGAL_DIR}")
        return 1

    files = sorted(LEGAL_DIR.glob("*.json"))
    if not files:
        print("check-legal: 未找到 legal/*.json")
        return 1

    problems: list[str] = []
    for path in files:
        problems.extend(check_file(path))

    if problems:
        print(f"check-legal: 发现 {len(problems)} 个问题")
        for item in problems:
            print("  - " + item)
        print("\n请先补齐占位符（见 docs/plans/optimization-plan.md A6 / §4），再打包发布。")
        return 1

    print("check-legal: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
