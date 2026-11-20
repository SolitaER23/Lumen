//! 动效：弹簧、补间、缓动与时长令牌。
//!
//! # 节奏（Metro + Zune）
//!
//! 统一三档时长，任何动画都从这里取，不写魔法数字：
//!
//! | 令牌 | 时长 | 用在哪 |
//! | --- | --- | --- |
//! | [`dur::PRESS`] | 100ms | 按下 / 悬停反馈（手指底下要立刻有反应） |
//! | [`dur::FAST`] | 160ms | 图标互换、淡入、错误提示 |
//! | [`dur::BASE`] | 220ms | 内容切换（面板、列表项） |
//! | [`dur::PAGE`] | 300ms | 页面转场 |
//!
//! 缓动只用两条：**进场** [`ease_out_quint`]（起步快、尾巴长，Metro 的指数缓出）、
//! **进出场** [`ease_in_out_quart`]（Zune 那种两端都收得住的手感）。
//! 一律**不过冲**——Metro/Zune 从不弹跳，回弹只留给直接操作（滚动惯性）。
//!
//! 动效可按系统设置减弱：Android 的「移除动画」（`animator_duration_scale=0`）
//! 或环境变量 `LUMEN_MOTION=off`，见 [`motion_scale`]。

use std::sync::OnceLock;

/// 统一时长（秒）。所有动效从这里取值。
pub mod dur {
    /// 按下 / 悬停反馈
    pub const PRESS: f32 = 0.10;
    /// 图标、淡入、小面积变化
    pub const FAST: f32 = 0.16;
    /// 内容切换
    pub const BASE: f32 = 0.22;
    /// 页面转场
    pub const PAGE: f32 = 0.30;
    /// 瓷砖进场：单片翻转时长
    pub const TILE: f32 = 0.26;
    /// 瓷砖进场：相邻两片的错峰间隔
    pub const STAGGER: f32 = 0.022;
}

static MOTION: OnceLock<f32> = OnceLock::new();

/// 系统设定的动效倍率：1 = 正常，0 = 关闭，0.5/1.5/2 = 系统动画速度设置。
///
/// 只查一次；Android 读 `Settings.Global.animator_duration_scale`
/// （无障碍里的「移除动画」就是把它设成 0），桌面用 `LUMEN_MOTION=off` 模拟。
pub fn motion_scale() -> f32 {
    *MOTION.get_or_init(|| {
        if let Ok(v) = std::env::var("LUMEN_MOTION") {
            if v.eq_ignore_ascii_case("off") || v == "0" {
                return 0.0;
            }
        }
        #[cfg(target_os = "android")]
        {
            let s = crate::safe_area::animator_scale().unwrap_or(1.0);
            log::info!("motion: animator scale {s} (0 = 系统要求减弱动效)");
            s
        }
        #[cfg(not(target_os = "android"))]
        {
            1.0
        }
    })
}

/// 系统要求"减弱动效"：所有过渡直接到位，不做位移/翻转/淡入。
pub fn reduced() -> bool {
    motion_scale() <= 0.01
}

/// 把一个时长按系统倍率缩放（0 → 立刻完成）
pub fn scaled(seconds: f32) -> f32 {
    seconds * motion_scale()
}

/// 一维弹簧。`step` 每帧推进一次，参数取 stiffness（刚度）/ damping（阻尼）。
#[derive(Clone, Copy, Debug)]
pub struct Spring {
    pub value: f32,
    pub target: f32,
    pub vel: f32,
    pub stiffness: f32,
    pub damping: f32,
}

impl Spring {
    pub fn new(value: f32, stiffness: f32, damping: f32) -> Self {
        Self {
            value,
            target: value,
            vel: 0.0,
            stiffness,
            damping,
        }
    }

    /// 常用参数：跟手、几乎不回弹（用于按压、进度）
    pub fn snappy(value: f32) -> Self {
        Self::new(value, 420.0, 38.0)
    }

    /// 常用参数：页面/弹层转场，末尾带轻微过冲
    pub fn smooth(value: f32) -> Self {
        Self::new(value, 190.0, 24.0)
    }

    pub fn to(&mut self, target: f32) {
        self.target = target;
    }

    pub fn set(&mut self, value: f32) {
        self.value = value;
        self.target = value;
        self.vel = 0.0;
    }

    pub fn step(&mut self, dt: f32) {
        if dt <= 0.0 {
            return;
        }
        // 减弱动效：不演，直接到位
        if reduced() {
            self.value = self.target;
            self.vel = 0.0;
            return;
        }
        // 半隐式欧拉 + 子步，保证大 dt 也稳定
        let remaining = dt.min(0.05);
        let sub = (remaining / 0.004).ceil().max(1.0) as usize;
        let h = remaining / sub as f32;
        for _ in 0..sub {
            let accel = (self.target - self.value) * self.stiffness - self.vel * self.damping;
            self.vel += accel * h;
            self.value += self.vel * h;
        }
        // 收敛判定，避免永远在抖
        if (self.target - self.value).abs() < 0.0005 && self.vel.abs() < 0.005 {
            self.value = self.target;
            self.vel = 0.0;
        }
    }

    /// 当前值（和 Tween 的接口对齐，方便互换）
    pub fn value(&self) -> f32 {
        self.value
    }

    pub fn is_animating(&self) -> bool {
        (self.target - self.value).abs() > 0.0005 || self.vel.abs() > 0.005
    }
    /// 已经停下（位置和速度都贴着目标）：用来决定还要不要每帧重绘
    pub fn at_rest(&self) -> bool {
        (self.target - self.value).abs() < 0.0005 && self.vel.abs() < 0.0005
    }

}

/// Material 3 Expressive 的弹簧档位。
///
/// M3 用"阻尼比"描述弹簧：< 1 会过冲（Expressive 的空间动效就靠它），
/// = 1 是临界阻尼（效果类动效，快而稳）。刚度越大越快。
pub mod exp {
    /// 空间位移：页面、卡片、共享元素。轻微过冲，收得住。
    pub const SPATIAL_STIFFNESS: f32 = 240.0;
    pub const SPATIAL_RATIO: f32 = 0.82;
    /// 效果：透明度、图标、按压反馈。快，不过冲。
    pub const EFFECTS_STIFFNESS: f32 = 1600.0;
    pub const EFFECTS_RATIO: f32 = 0.95;
}

impl Spring {
    /// 用"刚度 + 阻尼比"构造（内部换算成阻尼系数）。
    pub fn from_ratio(value: f32, stiffness: f32, ratio: f32) -> Self {
        Self::new(value, stiffness, 2.0 * ratio * stiffness.sqrt())
    }

    /// M3 Expressive 空间弹簧：跟手、末端约 5~8% 过冲
    pub fn spatial(value: f32) -> Self {
        Self::from_ratio(value, exp::SPATIAL_STIFFNESS, exp::SPATIAL_RATIO)
    }

    /// M3 Expressive 效果弹簧：快、不冲
    pub fn effects(value: f32) -> Self {
        Self::from_ratio(value, exp::EFFECTS_STIFFNESS, exp::EFFECTS_RATIO)
    }

    /// 松手/甩动：把手指速度交给弹簧，接着跑
    pub fn fling(&mut self, velocity: f32) {
        self.vel = velocity;
    }
}

/// 页面位移：弹簧驱动，可被**系统返回手势直接接管**（跟手）。
///
/// 拖拽中是 1:1 跟手（不跑弹簧）；松手时把速度交给弹簧，
/// 于是"跟手 — 过冲 — 收回"是同一段连续的运动，没有二次启动。
#[derive(Clone, Copy, Debug)]
pub struct Nav {
    spring: Spring,
    dragging: bool,
}

impl Nav {
    pub fn new(value: f32) -> Self {
        Self {
            spring: Spring::spatial(value),
            dragging: false,
        }
    }

    /// 动画到目标（弹簧，可中途改目标）
    pub fn to(&mut self, target: f32) {
        self.dragging = false;
        self.spring.to(target);
    }

    /// 直接落位
    pub fn set(&mut self, value: f32) {
        self.dragging = false;
        self.spring.set(value);
    }

    pub fn drag_begin(&mut self) {
        self.dragging = true;
    }

    /// 手势进度直接写值（1:1 跟手）
    pub fn drag_to(&mut self, value: f32) {
        self.dragging = true;
        self.spring.value = value;
        self.spring.vel = 0.0;
    }

    /// 松手：目标 + 手指速度
    pub fn drag_end(&mut self, target: f32, velocity: f32) {
        self.dragging = false;
        self.spring.target = target;
        self.spring.vel = velocity;
    }

    pub fn step(&mut self, dt: f32) {
        if !self.dragging {
            self.spring.step(dt);
        }
    }

    pub fn value(&self) -> f32 {
        self.spring.value
    }

    pub fn at_rest(&self) -> bool {
        !self.dragging && self.spring.at_rest()
    }
}
/// 可中断的补间：目标变了从**当前值**继续（不跳），到点即停、不过冲。
#[derive(Clone, Copy, Debug)]
pub struct Tween {
    from: f32,
    target: f32,
    value: f32,
    /// 已走过的比例 0..1
    t: f32,
    /// 时长（秒，0 = 立刻到位）
    dur: f32,
}

impl Tween {
    pub fn new(value: f32) -> Self {
        Self {
            from: value,
            target: value,
            value,
            t: 1.0,
            dur: 0.0,
        }
    }

    /// 改目标：以当前值为起点重新计时（中途被打断也不会跳）。
    pub fn to(&mut self, target: f32, seconds: f32) {
        let seconds = scaled(seconds);
        if (target - self.target).abs() < 1e-5 && !self.at_rest() {
            return; // 同一个目标、已经在动：别重置进度
        }
        if (target - self.value).abs() < 1e-5 {
            self.target = target;
            self.value = target;
            self.from = target;
            self.t = 1.0;
            return;
        }
        self.from = self.value;
        self.target = target;
        self.t = 0.0;
        self.dur = seconds;
        if self.dur <= 1e-4 {
            self.value = target;
            self.t = 1.0;
        }
    }

    /// 直接落位（不做动画）
    pub fn set(&mut self, value: f32) {
        self.from = value;
        self.target = value;
        self.value = value;
        self.t = 1.0;
        self.dur = 0.0;
    }

    pub fn step(&mut self, dt: f32) {
        if self.at_rest() || dt <= 0.0 {
            return;
        }
        self.t = (self.t + dt / self.dur.max(1e-4)).clamp(0.0, 1.0);
        self.value = lerp(self.from, self.target, ease_out_quint(self.t));
        if self.t >= 1.0 {
            self.value = self.target;
        }
    }

    pub fn value(&self) -> f32 {
        self.value
    }

    pub fn target(&self) -> f32 {
        self.target
    }

    pub fn at_rest(&self) -> bool {
        self.t >= 1.0 || (self.target - self.value).abs() < 1e-4
    }

    /// 进度 0..1（已缓动），给"跟着一起动的次级元素"用
    pub fn progress(&self) -> f32 {
        if self.at_rest() {
            1.0
        } else {
            self.t
        }
    }
}

/// 缓动函数
pub fn ease_out_cubic(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    1.0 - (1.0 - t).powi(3)
}

/// Metro 的"指数缓出"：起步快、尾部很长，收得干净利落（页面/瓷砖进场都用它）
pub fn ease_out_quint(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    1.0 - (1.0 - t).powi(5)
}

pub fn ease_in_quad(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    t * t
}

/// 两端都收：Zune 的面板/淡出用它，比 cubic 更"沉"
pub fn ease_in_out_quart(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    if t < 0.5 {
        8.0 * t * t * t * t
    } else {
        1.0 - (-2.0 * t + 2.0).powi(4) / 2.0
    }
}

pub fn ease_in_out(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    if t < 0.5 {
        2.0 * t * t
    } else {
        1.0 - (-2.0 * t + 2.0).powi(2) / 2.0
    }
}

pub fn smoothstep(edge0: f32, edge1: f32, x: f32) -> f32 {
    if (edge1 - edge0).abs() < f32::EPSILON {
        return if x < edge0 { 0.0 } else { 1.0 };
    }
    let t = ((x - edge0) / (edge1 - edge0)).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

/// 数字插值
pub fn lerp(a: f32, b: f32, t: f32) -> f32 {
    a + (b - a) * t
}

/// 时间轴：入场错峰动画用
#[derive(Clone, Copy, Debug, Default)]
pub struct Timeline {
    pub t: f32,
}

impl Timeline {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn step(&mut self, dt: f32) {
        self.t += dt;
    }
    pub fn reset(&mut self) {
        self.t = 0.0;
    }
    /// 第 index 个元素的进度（延迟 stagger 秒，duration 秒）——Metro 的指数缓出
    pub fn item(&self, index: usize, stagger: f32, duration: f32) -> f32 {
        if reduced() {
            return 1.0;
        }
        let local = self.t - index as f32 * stagger;
        ease_out_quint((local / duration.max(1e-4)).clamp(0.0, 1.0))
    }
}

#[cfg(test)]
mod nav_tests {
    use super::*;

    #[test]
    fn drag_suspends_spring() {
        let mut n = Nav::new(0.0);
        n.to(1.0);
        n.drag_begin();
        n.drag_to(0.3);
        for _ in 0..30 {
            n.step(0.016);
        }
        assert!((n.value() - 0.3).abs() < 1e-6);
        assert!(!n.at_rest());
    }

    #[test]
    fn drag_end_hands_velocity_to_spring() {
        let mut n = Nav::new(0.0);
        n.drag_begin();
        n.drag_to(0.2);
        n.drag_end(1.0, 6.0);
        let before = n.value();
        n.step(0.05);
        assert!(n.value() > before);
        for _ in 0..400 {
            n.step(0.008);
        }
        assert!(n.at_rest());
        assert!((n.value() - 1.0).abs() < 1e-3);
    }
}
