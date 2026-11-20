//! Metro 设计语言（Windows Phone 8）：纯黑舞台、Segoe 细体、锐角瓷砖、单一强调色。
//!
//! 这里只有 token 和绘制原语，不放任何状态。界面代码在 `app.rs`。
//!
//! 三条规矩，和原来的玻璃拟态正好相反：
//!   * 圆角一律 0，投影一律不要，层级靠留白和字重；
//!   * 颜色只有黑 / 白 / 灰 / 一个强调色，绝不用渐变装饰；
//!   * 大字用细体（Light），小标题用大写字母 + 字距。

use eframe::egui::{
    self, pos2, Align2, Color32, FontFamily, FontId, Painter, Pos2, Rect, Shape, Stroke, TextureId,
};

// ---------------------------------------------------------------------------
// 颜色
// ---------------------------------------------------------------------------

/// Windows Phone 8 的默认强调色（Cyan）
pub const ACCENT: Color32 = Color32::from_rgb(0x1B, 0xA1, 0xE2);

pub fn bg() -> Color32 {
    Color32::BLACK
}

/// 瓷砖底色（没有封面时的兜底）
pub fn tile_bg() -> Color32 {
    Color32::from_rgb(0x1F, 0x1F, 0x1F)
}

pub fn fg() -> Color32 {
    Color32::WHITE
}

pub fn text_dim() -> Color32 {
    Color32::from_white_alpha(150)
}

pub fn text_faint() -> Color32 {
    Color32::from_white_alpha(92)
}

/// 分隔线 / 细描边
pub fn rule() -> Color32 {
    Color32::from_white_alpha(26)
}

/// 色相 → 颜色（缺封面时的瓷砖底色，Metro 的纯色砖就是这个味道）
pub fn hue_rgb(h: f32, s: f32, v: f32) -> Color32 {
    let h = h.rem_euclid(360.0) / 60.0;
    let c = v * s;
    let x = c * (1.0 - (h % 2.0 - 1.0).abs());
    let m = v - c;
    let (r, g, b) = match h as u32 {
        0 => (c, x, 0.0),
        1 => (x, c, 0.0),
        2 => (0.0, c, x),
        3 => (0.0, x, c),
        4 => (x, 0.0, c),
        _ => (c, 0.0, x),
    };
    Color32::from_rgb(
        ((r + m) * 255.0) as u8,
        ((g + m) * 255.0) as u8,
        ((b + m) * 255.0) as u8,
    )
}

pub fn accent_a(a: f32) -> Color32 {
    ACCENT.gamma_multiply(a)
}

// ---------------------------------------------------------------------------
// 字体
// ---------------------------------------------------------------------------

const LIGHT: &str = "metro_light";
const SEMI: &str = "metro_semi";

/// 大字标题：Segoe UI Light
pub fn light(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name(LIGHT.into()))
}

/// 正文
pub fn regular(size: f32) -> FontId {
    FontId::proportional(size)
}

/// 半粗：瓷砖标题、按钮
pub fn semi(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name(SEMI.into()))
}

// ---------------------------------------------------------------------------
// 文字
// ---------------------------------------------------------------------------

pub fn text(p: &Painter, pos: Pos2, anchor: Align2, s: impl ToString, font: FontId, color: Color32) {
    p.text(pos, anchor, s.to_string(), font, color);
}

/// 大写小标题：Metro 的标志，字间距拉开
pub fn caps(
    p: &Painter,
    pos: Pos2,
    anchor: Align2,
    s: &str,
    size: f32,
    color: Color32,
    tracking: f32,
) {
    let job = egui::text::LayoutJob::single_section(
        s.to_owned(),
        egui::text::TextFormat {
            font_id: regular(size),
            color,
            extra_letter_spacing: tracking,
            ..Default::default()
        },
    );
    let galley = p.layout_job(job);
    let r = anchor.anchor_size(pos, galley.size());
    p.galley(r.min, galley, color);
}

/// 单行文字宽度（用于排版避让）
pub fn text_w(p: &Painter, s: &str, font: FontId) -> f32 {
    p.layout_no_wrap(s.to_owned(), font, Color32::WHITE)
        .size()
        .x
}

/// 多行文字（最多 max_rows 行，超了打省略号），返回实际高度
pub fn text_wrap(
    p: &Painter,
    pos: Pos2,
    s: &str,
    font: FontId,
    color: Color32,
    width: f32,
    max_rows: usize,
) -> f32 {
    let mut job = egui::text::LayoutJob::single_section(
        s.to_owned(),
        egui::text::TextFormat {
            font_id: font,
            color,
            ..Default::default()
        },
    );
    job.wrap.max_width = width.max(12.0);
    job.wrap.max_rows = max_rows.max(1);
    job.wrap.break_anywhere = false;
    job.wrap.overflow_character = Some('…');
    let galley = p.layout_job(job);
    let h = galley.size().y;
    p.galley(pos, galley, color);
    h
}


/// 单行文字，超宽自动打省略号。
///
/// 之前用的是"逐字试排 + 量宽度"的 fit()，一个标题要排十几次（每次都是一次
/// LayoutJob），瓷砖墙一屏十几块砖，一帧就是上百次文字排版——滚动卡顿的大头之一。
/// 现在交给 egui 自己在一次 layout 里截断（max_rows = 1 + overflow_character）。
pub fn ellipsize(
    p: &Painter,
    pos: Pos2,
    anchor: Align2,
    s: &str,
    font: FontId,
    color: Color32,
    width: f32,
) {
    let mut job = egui::text::LayoutJob::single_section(
        s.to_owned(),
        egui::text::TextFormat {
            font_id: font,
            color,
            ..Default::default()
        },
    );
    job.wrap.max_width = width.max(12.0);
    job.wrap.max_rows = 1;
    job.wrap.break_anywhere = true;
    job.wrap.overflow_character = Some('…');
    let galley = p.layout_job(job);
    let r = anchor.anchor_size(pos, galley.size());
    p.galley(r.min, galley, color);
}

/// 单行"停—走—停"跑马灯：装得下就原样画，装不下才来回滑（不跳变）。
///
/// * `rect`：可用区域，文字在其中垂直居中、水平裁剪；
/// * `t`：这段文字已经展示了多久（秒）——调用方在文字变化时归零，所以每首歌都从头读；
/// * `speed`：滑动速度（pt/s），慢一点才读得清。
pub fn marquee(
    p: &Painter,
    rect: Rect,
    s: &str,
    font: FontId,
    color: Color32,
    t: f32,
    speed: f32,
) {
    let galley = p.layout_no_wrap(s.to_owned(), font, color);
    let over = galley.size().x - rect.width();
    let y = rect.center().y - galley.size().y * 0.5;
    if over <= 1.0 {
        p.galley(pos2(rect.min.x, y), galley, color);
        return;
    }
    let travel = (over / speed.max(6.0)).clamp(0.4, 6.0);
    let pause = 1.1;
    let cycle = 2.0 * (pause + travel);
    let ph = t.max(0.0).rem_euclid(cycle);
    let off = if ph < pause {
        0.0
    } else if ph < pause + travel {
        -over * crate::anim::ease_in_out_quart((ph - pause) / travel)
    } else if ph < 2.0 * pause + travel {
        -over
    } else {
        -over * (1.0 - crate::anim::ease_in_out_quart((ph - 2.0 * pause - travel) / travel))
    };
    // 只有真在滚的时候才要 30fps 的帧：静止页面仍是 240 / 400ms 节拍
    p.ctx()
        .request_repaint_after(std::time::Duration::from_millis(33));
    let clip = p.with_clip_rect(rect);
    clip.galley(pos2(rect.min.x + off, y), galley, color);
}

/// 底部对齐的多行文字：一次 layout，直接贴着 `bottom` 往上长。
///
/// 瓷砖标题要从底边往上排两行，原来先调 wrap_height 量高度、再调 text_wrap 画，
/// 同一段文字排了两遍。这里排一遍、拿到高度再定位。
pub fn text_wrap_bottom(
    p: &Painter,
    left: f32,
    bottom: f32,
    s: &str,
    font: FontId,
    color: Color32,
    width: f32,
    max_rows: usize,
) -> f32 {
    let mut job = egui::text::LayoutJob::single_section(
        s.to_owned(),
        egui::text::TextFormat {
            font_id: font,
            color,
            ..Default::default()
        },
    );
    job.wrap.max_width = width.max(12.0);
    job.wrap.max_rows = max_rows.max(1);
    job.wrap.break_anywhere = false;
    job.wrap.overflow_character = Some('…');
    let galley = p.layout_job(job);
    let h = galley.size().y;
    p.galley(pos2(left, bottom - h), galley, color);
    h
}



// ---------------------------------------------------------------------------
// 形状
// ---------------------------------------------------------------------------

/// 平面矩形：锐角
pub fn rect(p: &Painter, r: Rect, color: Color32) {
    p.rect_filled(r, 0.0, color);
}

pub fn hairline(p: &Painter, a: Pos2, b: Pos2, color: Color32) {
    p.line_segment([a, b], Stroke::new(1.0, color));
}

/// 四边形填充
pub fn quad(p: &Painter, corners: [Pos2; 4], color: Color32) {
    p.add(Shape::convex_polygon(corners.to_vec(), color, Stroke::NONE));
}

pub fn uv_full() -> Rect {
    Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0))
}

/// 把封面按原始宽高比放进 `box`（contain，居中）。
///
/// 非正方形封面在方框里会出现留白条，但**不会变形、也不会被裁掉**：
/// 宽图上下留白、高图左右留白。方图返回原框，行为与旧版一致。
pub fn fit_within(box_: Rect, aspect: f32) -> Rect {
    if aspect <= 0.0 {
        return box_;
    }
    let bw = box_.width();
    let bh = box_.height();
    if bw <= 0.0 || bh <= 0.0 {
        return box_;
    }
    let (w, h) = if aspect > bw / bh {
        (bw, bw / aspect)
    } else {
        (bh * aspect, bh)
    };
    Rect::from_center_size(box_.center(), eframe::egui::vec2(w, h))
}

/// 同上，但左上角对齐（专辑页用，避免封面在列里左右漂）
pub fn fit_within_tl(box_: Rect, aspect: f32) -> Rect {
    let r = fit_within(box_, aspect);
    Rect::from_min_size(
        pos2(box_.min.x, box_.min.y),
        r.size(),
    )
}
/// 按目标宽高比裁剪 UV（居中裁切，不变形）
pub fn uv_cover(aspect_w: f32, aspect_h: f32) -> Rect {
    if aspect_w <= 0.0 || aspect_h <= 0.0 {
        return uv_full();
    }
    let target = aspect_w / aspect_h;
    if target > 1.0 {
        // 目标更宽：上下裁
        let h = 1.0 / target;
        Rect::from_min_max(pos2(0.0, 0.5 - h * 0.5), pos2(1.0, 0.5 + h * 0.5))
    } else {
        let w = target;
        Rect::from_min_max(pos2(0.5 - w * 0.5, 0.0), pos2(0.5 + w * 0.5, 1.0))
    }
}

/// 把纹理贴到（可能被透视变形的）四边形上
pub fn tex_quad(p: &Painter, tex: TextureId, corners: [Pos2; 4], uv: Rect, tint: Color32) {
    let mut mesh = egui::Mesh::default();
    mesh.texture_id = tex;
    let uv_pos = [
        pos2(uv.min.x, uv.min.y),
        pos2(uv.max.x, uv.min.y),
        pos2(uv.max.x, uv.max.y),
        pos2(uv.min.x, uv.max.y),
    ];
    for k in 0..4 {
        mesh.vertices.push(egui::epaint::Vertex {
            pos: corners[k],
            uv: uv_pos[k],
            color: tint,
        });
    }
    mesh.indices.extend_from_slice(&[0, 1, 2, 0, 2, 3]);
    p.add(Shape::mesh(mesh));
}

/// 正投影贴图（不做裁切）
pub fn image(p: &Painter, r: Rect, tex: TextureId, uv: Rect, tint: Color32) {
    tex_quad(
        p,
        tex,
        [r.left_top(), r.right_top(), r.right_bottom(), r.left_bottom()],
        uv,
        tint,
    );
}

// ---------------------------------------------------------------------------
// 透视
// ---------------------------------------------------------------------------

const CAM_DIST: f32 = 1100.0;

/// 绕 Y 轴旋转的四边形四个角（瓷砖翻转动画用）
pub fn flip_quad(rect: Rect, yaw: f32) -> [Pos2; 4] {
    let (hw, hh) = (rect.width() * 0.5, rect.height() * 0.5);
    let (sy, cy) = yaw.sin_cos();
    let local = [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)];
    let c = rect.center();
    let mut out = [Pos2::ZERO; 4];
    for (k, (x, y)) in local.iter().enumerate() {
        let x1 = x * cy;
        let z1 = -x * sy;
        let s = CAM_DIST / (CAM_DIST - z1).max(140.0);
        out[k] = pos2(c.x + x1 * s, c.y + y * s);
    }
    out
}

// ---------------------------------------------------------------------------
// 图标：细线 + 实心三角，全部直角
// ---------------------------------------------------------------------------

#[derive(Clone, Copy)]
pub enum Glyph {
    Back,
    Home,
    Play,
    Pause,
    Next,
    Prev,
    Dots,
}

pub fn glyph(p: &Painter, r: Rect, g: Glyph, color: Color32, w: f32) {
    // Material Symbols 的填充式图标（24×24 归一化坐标），不再用细线描边
    let (rw, rh) = (r.width(), r.height());
    let pt = |x: f32, y: f32| pos2(r.min.x + x * rw, r.min.y + y * rh);
    let tri = |a: (f32, f32), b: (f32, f32), c: (f32, f32)| {
        p.add(Shape::convex_polygon(
            vec![pt(a.0, a.1), pt(b.0, b.1), pt(c.0, c.1)],
            color,
            Stroke::NONE,
        ));
    };
    let bar = |x0: f32, x1: f32| {
        p.rect_filled(
            Rect::from_min_max(pt(x0, 0.12), pt(x1, 0.88)),
            rw * 0.04,
            color,
        );
    };
    match g {
        Glyph::Play => tri((0.22, 0.10), (0.86, 0.50), (0.22, 0.90)),
        Glyph::Pause => {
            bar(0.22, 0.40);
            bar(0.60, 0.78);
        }
        Glyph::Next => {
            tri((0.16, 0.12), (0.68, 0.50), (0.16, 0.88));
            bar(0.74, 0.86);
        }
        Glyph::Prev => {
            tri((0.84, 0.12), (0.32, 0.50), (0.84, 0.88));
            bar(0.14, 0.26);
        }
        Glyph::Home => {
            // Material 的 home：五边形（尖顶 + 方身）
            p.add(Shape::convex_polygon(
                vec![
                    pt(0.50, 0.10),
                    pt(0.90, 0.44),
                    pt(0.90, 0.90),
                    pt(0.10, 0.90),
                    pt(0.10, 0.44),
                ],
                color,
                Stroke::NONE,
            ));
        }
        Glyph::Back => {
            let s = Stroke::new(w, color);
            p.line_segment([pt(0.82, 0.5), pt(0.18, 0.5)], s);
            p.line_segment([pt(0.40, 0.22), pt(0.18, 0.5)], s);
            p.line_segment([pt(0.40, 0.78), pt(0.18, 0.5)], s);
        }
        Glyph::Dots => {
            for x in [0.22f32, 0.5, 0.78] {
                p.circle_filled(pt(x, 0.5), rw * 0.06, color);
            }
        }
    }
}

pub fn progress_dots(p: &Painter, r: Rect, phase: f32) {
    let n = 5;
    let gap = r.width() / n as f32;
    let head = (phase.rem_euclid(1.0) * (n as f32 + 2.0)) - 1.5;
    for i in 0..n {
        let d = (i as f32 - head).abs();
        let a = (1.0 - (d * 0.55).min(1.0)).powf(1.6);
        let c = Color32::from_white_alpha((a * 235.0) as u8);
        let s = 4.0;
        let x = r.min.x + gap * (i as f32 + 0.5) + (r.width() - gap * n as f32) * 0.5;
        p.rect_filled(
            Rect::from_center_size(pos2(x, r.center().y), egui::vec2(s, s)),
            0.0,
            c,
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hue_rgb_primary() {
        let red = hue_rgb(0.0, 1.0, 1.0);
        assert_eq!((red.r(), red.g(), red.b()), (255, 0, 0));
        let green = hue_rgb(120.0, 1.0, 1.0);
        assert_eq!((green.r(), green.g(), green.b()), (0, 255, 0));
        let blue = hue_rgb(240.0, 1.0, 1.0);
        assert_eq!((blue.r(), blue.g(), blue.b()), (0, 0, 255));
    }

    #[test]
    fn fit_within_keeps_aspect_and_centers() {
        let b = Rect::from_min_size(pos2(100.0, 50.0), eframe::egui::vec2(200.0, 200.0));
        // 方图：原框
        let sq = fit_within(b, 1.0);
        assert!((sq.width() - 200.0).abs() < 0.01 && (sq.height() - 200.0).abs() < 0.01);
        // 高图 1:2：高度占满，宽度一半，水平居中
        let tall = fit_within(b, 0.5);
        assert!((tall.height() - 200.0).abs() < 0.01);
        assert!((tall.width() - 100.0).abs() < 0.01);
        assert!((tall.center().x - b.center().x).abs() < 0.01);
        // 宽图 2:1：宽度占满，高度一半，垂直居中
        let wide = fit_within(b, 2.0);
        assert!((wide.width() - 200.0).abs() < 0.01);
        assert!((wide.height() - 100.0).abs() < 0.01);
        assert!((wide.center().y - b.center().y).abs() < 0.01);
    }

    #[test]
    fn fit_within_tl_anchors_top_left() {
        let b = Rect::from_min_size(pos2(10.0, 20.0), eframe::egui::vec2(120.0, 120.0));
        let tall = fit_within_tl(b, 0.75);
        assert!((tall.min.x - b.min.x).abs() < 0.01);
        assert!((tall.min.y - b.min.y).abs() < 0.01);
        assert!((tall.height() - 120.0).abs() < 0.01);
    }
    #[test]
    fn uv_cover_crops_wide_target() {
        // 目标 2:1：上下各裁掉 1/4
        let uv = uv_cover(2.0, 1.0);
        assert!((uv.min.y - 0.25).abs() < 0.001);
        assert!((uv.max.y - 0.75).abs() < 0.001);
        assert!(uv.min.x.abs() < 0.001 && (uv.max.x - 1.0).abs() < 0.001);
    }

    #[test]
    fn uv_cover_crops_tall_target() {
        // 目标 1:2：左右各裁掉 1/4
        let uv = uv_cover(1.0, 2.0);
        assert!((uv.min.x - 0.25).abs() < 0.001);
        assert!((uv.max.x - 0.75).abs() < 0.001);
        assert!(uv.min.y.abs() < 0.001 && (uv.max.y - 1.0).abs() < 0.001);
    }
}
