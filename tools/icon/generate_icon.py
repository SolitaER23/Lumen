# -*- coding: utf-8 -*-
"""
Lumen 应用图标生成器 —— MD3 Expressive「光之心 / Lumen Core」。

设计说明
--------
· 主体：一颗四角光芒（spark），尖角由内凹弧线连接 —— MD3 Expressive 的
  「光 / 能量」母题，也是应用内「氛围电台」那一颗 sparkle 圆点的放大版。
· 底色：深紫径向渐变，中心透出暖光；和界面里「封面取色 + 深色沉浸」一致。
· Lumen = 流明（光通量单位），标志只留下「光」这一件事，缩到 24dp 依然清楚。

可选变体：设环境变量 LUMEN_ICON_RING=1 会加回「唱片沟槽环 + 沟槽高光弧」
（音乐播放器语义更强，但图形更重）。

几何全部在 108x108 的 adaptive icon 视口内定义；图形外缘 r = 21.5dp，
远在 66dp 安全区（半径 33）之内，任何 launcher mask 与视差都不会裁到。

输出
----
  android/app/src/main/res/drawable/ic_launcher_background.xml
  android/app/src/main/res/drawable/ic_launcher_foreground.xml
  android/app/src/main/res/drawable/ic_launcher_monochrome.xml
  target/icon/preview/icon.svg, icon_mono.svg, preview.html   （预览，不进 git）
"""
import math
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable")
OUT = os.path.join(ROOT, "target", "icon", "preview")

CX = CY = 54.0

# 变体开关：唱片环
WITH_RING = os.environ.get("LUMEN_ICON_RING", "") == "1"
RING_R = 30.0
RING_W = 4.8
GROOVE_R = 23.6
GROOVE_W = 2.6
GROOVE_A0, GROOVE_A1 = 155.0, 250.0
GROOVE_ALPHA = 0.50

# 光芒
SPARK_R = 21.5
SPARK_K = 0.33
SPARK_ROT = -6.0

CREAM = "#FBF1DF"
AMBER_LIGHT = "#FFF3D4"
AMBER_MID = "#FFC266"
AMBER_DEEP = "#FF8F3F"

BG_INNER = "#4B2A80"
BG_MID = "#2A1652"
BG_OUTER = "#0B0616"
GLOW = "#FFB45C"


def fmt(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def sparkle_path(cx, cy, r, k, rot_deg):
    kk = r * k
    a = math.radians(rot_deg)
    c, s = math.cos(a), math.sin(a)

    def P(dx, dy):
        return (cx + dx * c - dy * s, cy + dx * s + dy * c)

    tip_t, tip_r, tip_b, tip_l = P(0, -r), P(r, 0), P(0, r), P(-r, 0)
    ctl_r, ctl_b, ctl_l, ctl_t = P(kk, 0), P(0, kk), P(-kk, 0), P(0, -kk)

    def xy(p):
        return f"{fmt(p[0])},{fmt(p[1])}"

    return (f"M{xy(tip_t)} Q{xy(ctl_r)} {xy(tip_r)} Q{xy(ctl_b)} {xy(tip_b)} "
            f"Q{xy(ctl_l)} {xy(tip_l)} Q{xy(ctl_t)} {xy(tip_t)} Z")


def circle_path(cx, cy, r):
    top, bot = (cx, cy - r), (cx, cy + r)
    return (f"M{fmt(top[0])},{fmt(top[1])} "
            f"A{fmt(r)},{fmt(r)} 0 1,1 {fmt(bot[0])},{fmt(bot[1])} "
            f"A{fmt(r)},{fmt(r)} 0 1,1 {fmt(top[0])},{fmt(top[1])}")


def arc_path(cx, cy, r, a0, a1):
    p0 = (cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0)))
    p1 = (cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1)))
    large = 1 if abs(a1 - a0) > 180 else 0
    return (f"M{fmt(p0[0])},{fmt(p0[1])} A{fmt(r)},{fmt(r)} 0 {large} 1 "
            f"{fmt(p1[0])},{fmt(p1[1])}")


SPARK_D = sparkle_path(CX, CY, SPARK_R, SPARK_K, SPARK_ROT)
RING_D = circle_path(CX, CY, RING_R)
GROOVE_D = arc_path(CX, CY, GROOVE_R, GROOVE_A0, GROOVE_A1)

sx, sy = CX - SPARK_R, CY - SPARK_R
ex, ey = CX + SPARK_R, CY + SPARK_R

# ---------- Android XML ----------
BG_XML = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Lumen 图标背景层：深紫径向渐变 + 唱片中心透出的暖光。
     背景层跟随 launcher 视差，渐变范围比可见区域更大，避免边缘露色。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="48"
                android:gradientRadius="82"
                android:tileMode="clamp">
                <item android:offset="0" android:color="{BG_INNER}" />
                <item android:offset="0.55" android:color="{BG_MID}" />
                <item android:offset="1" android:color="{BG_OUTER}" />
            </gradient>
        </aapt:attr>
    </path>

    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="54"
                android:gradientRadius="42"
                android:tileMode="clamp">
                <item android:offset="0" android:color="#99{GLOW[1:]}" />
                <item android:offset="0.45" android:color="#3D{GLOW[1:]}" />
                <item android:offset="1" android:color="#00{GLOW[1:]}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

_RING_LAYERS = f'''
    <!-- 唱片沟槽环 -->
    <path
        android:pathData="{RING_D}"
        android:strokeColor="{CREAM}"
        android:strokeWidth="{fmt(RING_W)}"
        android:fillColor="#00000000" />

    <!-- 沟槽高光弧 -->
    <path
        android:pathData="{GROOVE_D}"
        android:strokeColor="#80{CREAM[1:]}"
        android:strokeWidth="{fmt(GROOVE_W)}"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />
''' if WITH_RING else ""

FG_XML = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Lumen 图标前景层：「光之心」。
     一颗四角光芒 —— 应用内「氛围电台」sparkle 的放大版，也是 Lumen（流明）本身。
     图形外缘 r = {SPARK_R}dp，位于 66dp 安全区内，任何 mask / 视差都不会裁切。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{_RING_LAYERS}
    <!-- 中心光芒 -->
    <path android:pathData="{SPARK_D}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{fmt(sx)}"
                android:startY="{fmt(sy)}"
                android:endX="{fmt(ex)}"
                android:endY="{fmt(ey)}">
                <item android:offset="0" android:color="{AMBER_LIGHT}" />
                <item android:offset="0.45" android:color="{AMBER_MID}" />
                <item android:offset="1" android:color="{AMBER_DEEP}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

_MONO_RING = f'''
    <path
        android:pathData="{RING_D}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="{fmt(RING_W)}"
        android:fillColor="#00000000" />

    <path
        android:pathData="{GROOVE_D}"
        android:strokeColor="#66FFFFFF"
        android:strokeWidth="{fmt(GROOVE_W)}"
        android:strokeLineCap="round"
        android:fillColor="#00000000" />
''' if WITH_RING else ""

MONO_XML = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 主题图标（Android 13+）单色层：只剩光芒形状，由系统统一着色。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{_MONO_RING}
    <path
        android:pathData="{SPARK_D}"
        android:fillColor="#FFFFFFFF" />
</vector>
'''


# ---------- 预览 ----------
def svg_doc(size, mono=False):
    ring = ""
    if WITH_RING:
        ring = (f'<circle cx="{CX}" cy="{CY}" r="{RING_R}" fill="none" stroke="{CREAM if not mono else "#fff"}" stroke-width="{RING_W}"/>'
                f'<path d="{GROOVE_D}" fill="none" stroke="{CREAM if not mono else "#fff"}" stroke-width="{GROOVE_W}" stroke-linecap="round" opacity="{GROOVE_ALPHA}"/>')
    if mono:
        body = f'{ring}<path d="{SPARK_D}" fill="#fff"/>'
        return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" '
                f'height="{size}" style="display:block">{body}</svg>')
    defs = f'''<defs>
  <radialGradient id="bg" cx="0.5" cy="0.444" r="0.76">
    <stop offset="0" stop-color="{BG_INNER}"/><stop offset="0.55" stop-color="{BG_MID}"/><stop offset="1" stop-color="{BG_OUTER}"/>
  </radialGradient>
  <radialGradient id="glow" cx="0.5" cy="0.5" r="0.407">
    <stop offset="0" stop-color="{GLOW}" stop-opacity="0.60"/><stop offset="0.45" stop-color="{GLOW}" stop-opacity="0.24"/><stop offset="1" stop-color="{GLOW}" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="spark" x1="{fmt(sx / 108)}" y1="{fmt(sy / 108)}" x2="{fmt(ex / 108)}" y2="{fmt(ey / 108)}">
    <stop offset="0" stop-color="{AMBER_LIGHT}"/><stop offset="0.45" stop-color="{AMBER_MID}"/><stop offset="1" stop-color="{AMBER_DEEP}"/>
  </linearGradient>
</defs>'''
    body = (f'<rect width="108" height="108" fill="url(#bg)"/>'
            f'<rect width="108" height="108" fill="url(#glow)"/>'
            f'{ring}'
            f'<path d="{SPARK_D}" fill="url(#spark)"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" '
            f'height="{size}" style="display:block">{defs}{body}</svg>')


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT), len(text), "bytes")


write(os.path.join(RES, "ic_launcher_background.xml"), BG_XML)
write(os.path.join(RES, "ic_launcher_foreground.xml"), FG_XML)
write(os.path.join(RES, "ic_launcher_monochrome.xml"), MONO_XML)


def masked(size, mono=False):
    """裁到 launcher 实际可见的 72dp 区域（108 视口的正中 72）。"""
    inner = (svg_doc(108, mono)
             .replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"')
             .replace('width="108"', 'width="100%"').replace('height="108"', 'height="100%"'))
    return f'<div style="width:{size}px;height:{size}px;border-radius:50%;overflow:hidden;flex:none">{inner}</div>'


sizes = "".join(f'<div class="cell"><div class="lbl">{s}dp</div>{masked(s)}</div>' for s in (64, 48, 32, 24))
mono_sizes = "".join(
    f'<div class="cell"><div class="lbl">{s}dp</div><div class="mono">{masked(s, True)}</div></div>'
    for s in (64, 48, 32, 24))

html = f'''<!doctype html><html><head><meta charset="utf-8"><style>
body{{margin:0;background:#0a0a10;color:#dcd6ef;font:13px/1.4 "Segoe UI","Microsoft YaHei",sans-serif;padding:26px}}
h1{{font-size:14px;font-weight:600;color:#9d92c9;margin:0 0 4px}}
p.sub{{margin:0 0 22px;color:#6f6791;font-size:12px}}
.row{{display:flex;gap:22px;align-items:center;margin-bottom:26px}}
.cell{{text-align:center}}.lbl{{font-size:11px;color:#7d7499;margin-bottom:8px}}
.mono{{background:#2b2b33;border-radius:12px;padding:10px;display:inline-block}}
</style></head><body>
<h1>Lumen · MD3 Expressive 应用图标</h1>
<p class="sub">「光之心」—— 深紫暖光底 + 四角光芒</p>
<div class="row">{masked(220)}<div style="display:flex;gap:18px;align-items:flex-end">{sizes}</div></div>
<h1>主题图标（单色层）</h1>
<div class="row">{mono_sizes}</div>
</body></html>'''
write(os.path.join(OUT, "preview.html"), html)
write(os.path.join(OUT, "icon.svg"), svg_doc(512))
write(os.path.join(OUT, "icon_mono.svg"), svg_doc(512, True))
print("done")
