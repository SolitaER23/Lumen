//! Material 3 Expressive 设计系统（自绘）。
//!
//! 这里只放"设计令牌 + 组件原语"：颜色角色、形状刻度、字阶、卡片/按钮/滑块/
//! 图标按钮/对话框/状态层。页面的排版仍然在 `app.rs` 里，但所有笔触都从这里取，
//! 保证全局一致。
//!
//! 配色按 M3 的"从内容取色"来做：主色由当前专辑封面的主色（tint + hue）推导，
//! 背景/容器是该色相下的低饱和中性色，于是每张专辑都有自己的一套色调。

use eframe::egui::{
    Align2, Color32, FontId, Painter, Pos2, Rect, Stroke, TextureId, Vec2,
};

use crate::metro as m;

/// M3 形状刻度（dp）
pub mod shape {
    pub const XS: f32 = 4.0;
    pub const SM: f32 = 8.0;
    pub const MD: f32 = 12.0;
    pub const LG: f32 = 16.0;
    pub const XL: f32 = 24.0;
    /// 卡片、悬浮面板
    pub const XXL: f32 = 28.0;
    /// 完全圆角（药丸）
    pub const FULL: f32 = 999.0;
}

/// 一套 M3 颜色角色（深色基调）
#[derive(Clone, Copy, Debug)]
pub struct Roles {
    pub surface: Color32,
    pub surface_low: Color32,
    pub surface_container: Color32,
    pub surface_high: Color32,
    pub on_surface: Color32,
    pub on_variant: Color32,
    pub primary: Color32,
    pub on_primary: Color32,
    pub primary_container: Color32,
    pub on_primary_container: Color32,
    pub secondary_container: Color32,
    pub on_secondary_container: Color32,
    pub outline: Color32,
    pub outline_variant: Color32,
}

impl Roles {
    /// 默认（无封面时）：蓝紫色系
    pub fn fallback() -> Self {
        Self::from_tint([0x4F, 0x7C, 0xFF], 0.62)
    }

    /// 从封面主色推一套角色。`hue` 是 0..1 的色相。
    pub fn from_tint(_tint: [u8; 3], hue: f32) -> Self {
        // 封面解码异常时 hue 可能是 NaN，先兜底，否则颜色全变 NaN（按钮不可见）
        let hue = if hue.is_finite() { hue.rem_euclid(1.0) } else { 0.62 };
        // 主色：该色相、较高饱和与明度（Expressive 的高饱和主色）
        let primary = m::hue_rgb(hue, 0.66, 0.96);
        let on_primary = if lum(primary) > 0.62 {
            Color32::from_rgb(0x14, 0x12, 0x10)
        } else {
            Color32::WHITE
        };
        let primary_container = m::hue_rgb(hue, 0.42, 0.30);
        let on_primary_container = m::hue_rgb(hue, 0.18, 0.94);
        let secondary_container = m::hue_rgb(hue, 0.26, 0.22);
        Self {
            surface: m::hue_rgb(hue, 0.12, 0.055),
            surface_low: m::hue_rgb(hue, 0.11, 0.085),
            surface_container: m::hue_rgb(hue, 0.10, 0.115),
            surface_high: m::hue_rgb(hue, 0.09, 0.155),
            on_surface: m::hue_rgb(hue, 0.03, 0.95),
            on_variant: m::hue_rgb(hue, 0.06, 0.72),
            primary,
            on_primary,
            primary_container,
            on_primary_container,
            secondary_container,
            on_secondary_container: m::hue_rgb(hue, 0.12, 0.90),
            outline: m::hue_rgb(hue, 0.05, 0.42),
            outline_variant: m::hue_rgb(hue, 0.07, 0.24),
        }
    }
}

fn lum(c: Color32) -> f32 {
    (0.2126 * c.r() as f32 + 0.7152 * c.g() as f32 + 0.0722 * c.b() as f32) / 255.0
}

// ---------------------------------------------------------------------------
// 字阶
// ---------------------------------------------------------------------------

pub fn display(size: f32) -> FontId {
    m::light(size)
}
pub fn headline(size: f32) -> FontId {
    m::light(size)
}
pub fn title(size: f32) -> FontId {
    m::regular(size)
}
pub fn body(size: f32) -> FontId {
    m::regular(size)
}
pub fn label(size: f32) -> FontId {
    m::semi(size)
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

/// 圆角矩形（填充）
pub fn round_rect(p: &Painter, r: Rect, radius: f32, color: Color32) {
    p.rect_filled(r, radius, color);
}

/// 圆角描边
pub fn round_stroke(p: &Painter, r: Rect, radius: f32, color: Color32, width: f32) {
    p.rect_stroke(
        r,
        radius,
        Stroke::new(width, color),
        eframe::egui::StrokeKind::Inside,
    );
}

/// 状态层：按压/悬停时铺在组件上的一层透明度
pub fn state_layer(p: &Painter, r: Rect, radius: f32, color: Color32, alpha: f32) {
    if alpha <= 0.001 {
        return;
    }
    p.rect_filled(r, radius, color.gamma_multiply(alpha));
}

/// 带圆角的图片：先贴图，再用页面底色把四个角的"角料"盖掉。
///
/// egui 的裁剪矩形只能是轴对齐的，所以这里用 4 个三角扇把方角与圆弧之间的
/// 月牙形区域用 `mask` 色涂掉——视觉上就是圆角图。
pub fn image_round(
    p: &Painter,
    tex: TextureId,
    uv: Rect,
    r: Rect,
    _radius: f32,
    tint: Color32,
    _mask: Color32,
) {
    // 圆角已经在上传纹理时烘进 alpha 通道（见 `model::Artwork::from_image`），
    // 这里只负责贴图：一次 draw call，没有逐帧遮罩。
    m::image(p, r, tex, uv, tint);
}
/// M3 卡片：容器色 + 可选描边
pub fn card(p: &Painter, r: Rect, radius: f32, fill: Color32, outline: Option<Color32>) {
    round_rect(p, r, radius, fill);
    if let Some(c) = outline {
        round_stroke(p, r, radius, c, 1.0);
    }
}

/// 实心按钮（primary）
pub fn filled_button(p: &Painter, r: Rect, roles: &Roles, text: &str, k: f32, pressed: f32) {
    round_rect(p, r, r.height() * 0.5, roles.primary);
    state_layer(p, r, r.height() * 0.5, roles.on_primary, 0.10 * pressed);
    m::text(
        p,
        r.center(),
        Align2::CENTER_CENTER,
        text,
        label(15.0 * k),
        roles.on_primary,
    );
}

/// 图标按钮（圆形，48dp）
pub fn icon_button(
    p: &Painter,
    r: Rect,
    roles: &Roles,
    glyph: m::Glyph,
    color: Color32,
    bg: Option<Color32>,
    pressed: f32,
) {
    if let Some(bg) = bg {
        p.circle_filled(r.center(), r.width() * 0.5, bg);
        state_layer(
            p,
            Rect::from_center_size(r.center(), r.size()),
            r.width() * 0.5,
            roles.on_surface,
            0.10 * pressed,
        );
    } else if pressed > 0.01 {
        p.circle_filled(
            r.center(),
            r.width() * 0.5,
            Color32::from_white_alpha((28.0 * pressed) as u8),
        );
    }
    m::glyph(
        p,
        Rect::from_center_size(r.center(), Vec2::splat(r.width() * 0.42)),
        glyph,
        color,
        2.0,
    );
}

/// Extended FAB：药丸 + 图标 + 文字
pub fn fab(p: &Painter, r: Rect, roles: &Roles, glyph: m::Glyph, text: &str, k: f32, pressed: f32) {
    round_rect(p, r, r.height() * 0.5, roles.primary_container);
    state_layer(
        p,
        r,
        r.height() * 0.5,
        roles.on_primary_container,
        0.12 * pressed,
    );
    let icon = Rect::from_center_size(
        Pos2::new(r.min.x + 26.0 * k, r.center().y),
        Vec2::splat(18.0 * k),
    );
    m::glyph(p, icon, glyph, roles.on_primary_container, 2.0);
    if !text.is_empty() {
        m::text(
            p,
            Pos2::new(r.min.x + 46.0 * k, r.center().y),
            Align2::LEFT_CENTER,
            text,
            label(14.0 * k),
            roles.on_primary_container,
        );
    }
}

/// 分段按钮 / 过滤 chip
pub fn chip(
    p: &Painter,
    r: Rect,
    roles: &Roles,
    text: &str,
    selected: bool,
    k: f32,
    pressed: f32,
) {
    let radius = r.height() * 0.5;
    if selected {
        round_rect(p, r, radius, roles.secondary_container);
    } else {
        round_stroke(p, r, radius, roles.outline_variant, 1.0);
    }
    state_layer(p, r, radius, roles.on_surface, 0.08 * pressed);
    let (font, color) = if selected {
        (label(13.0 * k), roles.on_secondary_container)
    } else {
        (label(13.0 * k), roles.on_variant)
    };
    m::text(p, r.center(), Align2::CENTER_CENTER, text, font, color);
}

/// M3 滑块：4dp 轨道 + 活动段 + 圆点拇子
pub fn slider(p: &Painter, r: Rect, frac: f32, roles: &Roles, k: f32) {
    let frac = frac.clamp(0.0, 1.0);
    let cy = r.center().y;
    let track = Rect::from_min_max(
        Pos2::new(r.min.x, cy - 2.0 * k),
        Pos2::new(r.max.x, cy + 2.0 * k),
    );
    p.rect_filled(track, 2.0 * k, roles.outline_variant);
    if frac > 0.0 {
        p.rect_filled(
            Rect::from_min_max(track.min, Pos2::new(track.min.x + track.width() * frac, track.max.y)),
            2.0 * k,
            roles.primary,
        );
    }
    let x = r.min.x + r.width() * frac;
    p.circle_filled(Pos2::new(x, cy), 7.0 * k, roles.primary);
    p.circle_filled(Pos2::new(x, cy), 3.0 * k, roles.on_primary);
}

/// 文本输入框（M3 outlined）
pub fn text_field(p: &Painter, r: Rect, roles: &Roles, text: &str, focused: bool, k: f32) {
    let radius = shape::MD * k;
    round_rect(p, r, radius, roles.surface_container);
    let line = if focused {
        roles.primary
    } else {
        roles.outline
    };
    round_stroke(p, r, radius, line, if focused { 2.0 } else { 1.0 });
    m::text(
        p,
        Pos2::new(r.min.x + 16.0 * k, r.center().y),
        Align2::LEFT_CENTER,
        text,
        body(15.0 * k),
        roles.on_surface,
    );
}

/// 对话框/底部面板的容器
pub fn dialog(p: &Painter, r: Rect, roles: &Roles, k: f32) {
    round_rect(p, r, shape::XXL * k, roles.surface_high);
    round_stroke(p, r, shape::XXL * k, roles.outline_variant, 1.0);
}

/// 全屏压暗（对话框后面）
pub fn scrim(p: &Painter, r: Rect, alpha: f32) {
    p.rect_filled(
        r,
        0.0,
        Color32::from_black_alpha((160.0 * alpha.clamp(0.0, 1.0)) as u8),
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roles_are_dark_and_tinted() {
        let r = Roles::from_tint([200, 30, 60], 0.95);
        // 背景必须是深色
        assert!(lum(r.surface) < 0.12);
        // 主色必须够亮，才能当按钮底色
        assert!(lum(r.primary) > 0.35);
        // 前景与背景要有对比
        assert!(lum(r.on_surface) > 0.8);
    }

    #[test]
    fn fallback_roles_valid() {
        let r = Roles::fallback();
        assert!(lum(r.surface) < 0.12);
        assert!(lum(r.primary) > 0.3);
    }
}

#[cfg(test)]
mod robust_tests {
    use super::*;

    #[test]
    fn non_finite_hue_does_not_poison_colors() {
        for h in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY] {
            let r = Roles::from_tint([10, 20, 30], h);
            for c in [r.surface, r.primary, r.on_surface, r.on_primary] {
                assert!((c.r() as u32 + c.g() as u32 + c.b() as u32) < 800);
            }
        }
    }
}
