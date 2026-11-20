//! 与 `App` 无耦合的纯几何/物理函数。
//!
//! 从 `app.rs` 拆出来：这些函数不依赖任何 `App` 字段，单独成模块便于复用与测试，
//! 也减少 `app.rs`（三千余行）的体积。包含：
//! * `step_inertia`——通用惯性滚动（首页瓷砖墙与曲目列表共用一套物理）；
//! * `LyricGeo` / `lyric_geometry`——歌词按字符宽度估算分槽的排版几何。

/// 通用惯性滚动：拖拽时位置已经由手势更新，这里只负责"松手后的滑行"和"回弹"。
///
/// 三段式，和首页瓷砖墙共用：
///
/// 1. **滑行**：`pos += v·dt`，速度按 `v *= exp(-5.2·dt)` 衰减（时间常数 ~190ms，
///    比原来 4.6 略快一点，停得干脆）；
/// 2. **越界回弹**：不再"硬 clamp + 快速插值"，改用近似临界阻尼的弹簧
///    （刚度 260，阻尼 0.95×临界）拉回边界，约 200ms、不过冲——上下两端行为一致；
/// 3. **静止**：速度低于 4 就直接归零并贴边，避免出现"最后一像素抽搐"。
pub fn step_inertia(pos: &mut f32, vel: &mut f32, max: f32, dt: f32, dragging: bool) {
    if dragging || dt <= 0.0 {
        return;
    }
    let max = max.max(0.0);
    let dt = dt.min(0.05);
    let clamped = pos.clamp(0.0, max);
    let out = *pos - clamped;

    // ── 越界：弹簧拉回（上下对称，不过冲）─────────────────────────
    if out.abs() > 0.5 {
        let stiffness = 260.0f32;
        let damping = 2.0 * stiffness.sqrt() * 0.95;
        *vel += (-stiffness * out - damping * *vel) * dt;
        *pos += *vel * dt;
        // 贴边就收：越界量和速度都小到看不出来
        if (*pos - clamped).abs() < 0.4 && vel.abs() < 8.0 {
            *pos = clamped;
            *vel = 0.0;
        }
        return;
    }

    // ── 正常滑行 ─────────────────────────────────────────────────
    if vel.abs() > 2.0 {
        *pos += *vel * dt;
        *vel *= (-5.2 * dt).exp();
        if vel.abs() < 4.0 {
            *vel = 0.0;
        }
        // 滑出边界交给弹簧分支；这里只防跑飞
        *pos = pos.clamp(clamped - 140.0, clamped + 140.0);
    } else {
        *vel = 0.0;
        *pos = clamped;
    }
}

/// 歌词排版几何：按"字符宽度估算"给每行分槽，避免每帧真的去排版上百行。
///
/// 中文按 1 个字宽、ASCII 按 0.52 字宽估；超过面板宽度就按两行算。
/// 只在估算失败极少数情况下会多折/少折一行，换来的是每帧几乎零成本。
pub struct LyricGeo {
    /// 每行槽位中心（内容坐标，从 0 开始）
    pub centers: Vec<f32>,
    /// 每行占几行文本（1 或 2）
    pub rows: Vec<usize>,
    /// 内容总高
    pub height: f32,
}

pub fn lyric_geometry(lines: &[crate::model::LyricLine], width: f32, k: f32) -> LyricGeo {
    let size = 16.5 * k;
    let pitch1 = 34.0 * k;
    let pitch2 = 58.0 * k;
    let mut centers = Vec::with_capacity(lines.len());
    let mut rows_v = Vec::with_capacity(lines.len());
    let mut y = 0.0;
    for l in lines {
        let mut w = 0.0;
        let mut rows = 1;
        for ch in l.text.chars() {
            w += if ch.is_ascii() { size * 0.52 } else { size };
            if w > width {
                rows = 2;
                break;
            }
        }
        let slot = if rows == 2 { pitch2 } else { pitch1 };
        centers.push(y + slot * 0.5);
        rows_v.push(rows);
        y += slot;
    }
    LyricGeo {
        centers,
        rows: rows_v,
        height: y,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::LyricLine;

    #[test]
    fn lyric_geometry_single_and_wrap() {
        let lines = vec![
            LyricLine { start: 0.0, text: "短".into() },
            LyricLine {
                start: 1.0,
                text: "这是一行特别特别特别特别特别特别特别特别特别特别长的歌词".into(),
            },
        ];
        let geo = lyric_geometry(&lines, 200.0, 1.0);
        assert_eq!(geo.centers.len(), 2);
        assert_eq!(geo.rows, vec![1, 2]);
        assert!(geo.height > 0.0);
    }

    #[test]
    fn step_inertia_decays_and_settles() {
        let mut pos = 0.0f32;
        let mut vel = 100.0f32;
        for _ in 0..120 {
            step_inertia(&mut pos, &mut vel, 500.0, 1.0 / 60.0, false);
        }
        assert!(vel.abs() < 1.0, "滑行应衰减到接近停止");
        assert!((0.0..=500.0).contains(&pos));
    }

    #[test]
    fn step_inertia_rubber_bands_back_without_overshoot() {
        // 从顶部越界 90px 松手：应回到 0，且不会反向往里冲
        let mut pos = -90.0f32;
        let mut vel = -600.0f32;
        let mut min_pos = pos;
        for _ in 0..90 {
            step_inertia(&mut pos, &mut vel, 500.0, 1.0 / 60.0, false);
            min_pos = min_pos.min(pos);
        }
        assert!(pos.abs() < 1.0, "应回到边界: pos={pos}");
        assert!(min_pos > -140.0, "回弹不应继续往外滑: min={min_pos}");
    }

    #[test]
    fn step_inertia_bottom_is_symmetric() {
        let mut pos = 640.0f32;
        let mut vel = 900.0f32;
        for _ in 0..90 {
            step_inertia(&mut pos, &mut vel, 500.0, 1.0 / 60.0, false);
        }
        assert!((pos - 500.0).abs() < 1.0, "底部同样贴边: pos={pos}");
    }
}
