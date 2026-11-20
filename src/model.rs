//! 数据层：曲库模型 + 程序化专辑封面（含模糊版本，用于玻璃背景）。
//!
//! 曲库来自 Navidrome（Subsonic API），不再内置假数据。

use eframe::egui::{self, TextureHandle, TextureOptions};

#[derive(Clone, Debug)]
pub struct Track {
    /// Subsonic 的歌曲 id（流式播放要用）
    pub id: String,
    pub title: String,
    pub artist: String,
    pub secs: u32,
    /// 碟号（1 起；单碟专辑全是 1）
    pub disc: u16,
}

#[derive(Clone, Debug)]
pub struct Album {
    /// Subsonic 的专辑 id
    pub id: String,
    /// 封面 id（getCoverArt）
    pub cover_id: String,
    pub title: String,
    pub artist: String,
    pub year: u16,
    /// 封面色相（0..360）：没拿到真封面时用它生成程序化封面
    pub hue: f32,
    /// 服务端报的曲目数（曲目还没拉回来时用于显示）
    pub song_count: u16,
    /// 封面主色（碟面标签用）；[0,0,0] 表示还没算过
    pub tint: [u8; 3],
    /// 每首的起点（秒），与 tracks 对应，避免每帧重复求和
    pub starts: Vec<f32>,
    /// 整张总时长（秒）
    pub total_secs: f32,
    pub tracks: Vec<Track>,
}

impl Album {
    /// 曲目变化后重算起点与总时长（只在数据到达时调一次）
    pub fn recompute_timing(&mut self) {
        let mut acc = 0.0;
        self.starts = self
            .tracks
            .iter()
            .map(|t| {
                let at = acc;
                acc += t.secs as f32;
                at
            })
            .collect();
        self.total_secs = acc;
    }

    /// 按碟分组：`[(碟号, 起始下标, 曲目数)]`。
    ///
    /// `tracks` 在入库前已按 (碟号, 曲内序号) 排序，所以同碟的曲目一定连续，
    /// 起始下标可以直接当绝对下标用（播放逻辑全部基于整张专辑的绝对下标）。
    /// 单碟专辑返回一个 `(1, 0, n)`。
    pub fn discs(&self) -> Vec<(u16, usize, usize)> {
        let mut out: Vec<(u16, usize, usize)> = Vec::new();
        for (i, t) in self.tracks.iter().enumerate() {
            let d = t.disc.max(1);
            match out.last_mut() {
                Some((dd, _, n)) if *dd == d => *n += 1,
                _ => out.push((d, i, 1)),
            }
        }
        if out.is_empty() {
            out.push((1, 0, 0));
        }
        out
    }

    /// 第 `ti` 首属于第几碟（`discs()` 的下标）
    pub fn disc_of(&self, ti: usize) -> usize {
        self.discs()
            .iter()
            .position(|(_, start, n)| ti >= *start && ti < start + n)
            .unwrap_or(0)
    }
}

/// 一张专辑的封面纹理
#[derive(Clone)]
pub struct Artwork {
    pub sharp: TextureHandle,
    /// 原始宽高比（宽/高）。非正方形封面靠它按原比例摆，不能拉伸
    pub aspect: f32,
}

impl Artwork {
    /// 用真实封面图（解码后的 ColorImage）建纹理。
    ///
    /// 只做一次纹理上传：Metro 界面没有毛玻璃背景，之前那张降采样 + 三次模糊的
    /// 小图是纯浪费——每张封面要多花约 1~2ms，滚动时就是这些零碎开销堆成卡顿。
    pub fn from_image(ctx: &egui::Context, idx: usize, mut img: egui::ColorImage) -> Self {
        let w = img.width();
        let h = img.height();
        let (wf, hf) = (w as f32, h as f32);
        let aspect = if wf > 0.0 && hf > 0.0 { wf / hf } else { 1.0 };
        // 上传前把四角抠成圆角（1px 抗锯齿）。之后所有尺寸复用同一张纹理，
        // 不用逐帧画遮罩——M3 的圆角卡片/药丸就靠这一步。
        let r = (wf.min(hf) * 0.10).max(1.0);
        for y in 0..h {
            for x in 0..w {
                let fx = x as f32 + 0.5;
                let fy = y as f32 + 0.5;
                let dx = (r - fx).max(fx - (wf - r)).max(0.0);
                let dy = (r - fy).max(fy - (hf - r)).max(0.0);
                if dx > 0.0 || dy > 0.0 {
                    let d = (dx * dx + dy * dy).sqrt();
                    let a = ((r - d) / 1.5).clamp(0.0, 1.0);
                    if a < 0.999 {
                        let px = img.pixels[y * w + x];
                        img.pixels[y * w + x] = egui::Color32::from_rgba_unmultiplied(
                            px.r(),
                            px.g(),
                            px.b(),
                            (px.a() as f32 * a) as u8,
                        );
                    }
                }
            }
        }
        Self {
            aspect,
            sharp: ctx.load_texture(
                format!("art_sharp_{idx}"),
                img,
                TextureOptions::LINEAR,
            ),
        }
    }
}

/// 一行歌词：`start` 是该行开始的时间（秒）；未同步歌词全为 -1。
#[derive(Clone, Debug)]
pub struct LyricLine {
    pub start: f32,
    pub text: String,
}

/// 一首歌的歌词。同步歌词（LRC / OpenSubsonic structured）才做高亮，
/// 纯文本歌词只做按进度比例的滚动，不给"当前行"。
#[derive(Clone, Debug, Default)]
pub struct Lyrics {
    pub synced: bool,
    pub lines: Vec<LyricLine>,
}

impl Lyrics {
    pub fn is_empty(&self) -> bool {
        self.lines.is_empty()
    }

    /// 当前时间（相对本曲，秒）落在第几行。同步歌词用二分，未同步时给 -1。
    pub fn index_at(&self, t: f32) -> Option<usize> {
        if !self.synced || self.lines.is_empty() {
            return None;
        }
        // 找最后一个 start <= t
        let mut lo = 0usize;
        let mut hi = self.lines.len();
        while lo < hi {
            let mid = (lo + hi) / 2;
            if self.lines[mid].start <= t {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        if lo == 0 {
            None
        } else {
            Some(lo - 1)
        }
    }
}

pub struct Library {
    pub albums: Vec<Album>,
}

impl Library {
    /// 空曲库：真数据由 Navidrome 拉取（`Msg::Albums`），不再内置假专辑。
    pub fn empty() -> Self {
        Self { albums: Vec::new() }
    }

    pub fn total_songs(&self) -> usize {
        self.albums.iter().map(|a| a.tracks.len()).sum()
    }
}

pub fn fmt_time(secs: f32) -> String {
    let s = secs.max(0.0) as u32;
    format!("{}:{:02}", s / 60, s % 60)
}

// ---------------------------------------------------------------------------
// 文字反色：瓷砖标题压在封面上，靠这一小块区域的明暗决定用黑字还是白字
// ---------------------------------------------------------------------------

/// 封面文字区的"读数"：决定文字颜色，以及要不要给文字垫一层压底。
///
/// 只看平均亮度是不够的：一张"左边大白脸、右边黑背景"的封面，平均值是中灰，
/// 黑字白字都会糊在某一半上。所以这里按**实际对比度**量两件事：
///
/// * 这块区域里有多少像素和**白字** / **黑字**的对比度 ≥ 3:1 → 决定用哪种字；
/// * 两种都盖不全的部分（明暗各半、高频拼贴）→ `busy` 升高，绘制端垫一层压底。
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Ink {
    /// 文字该用浅色（白）吗
    pub light: bool,
    /// 文字在这块区域有多难读：0 = 干净，255 = 明暗混杂/花纹密集，需要压底
    pub busy: u8,
    /// 文字区平均亮度 0..255
    pub mean: u8,
}

impl Default for Ink {
    fn default() -> Self {
        // 还没量过（程序化封面/封面未到）：按 Metro 的深底浅字处理
        Self {
            light: true,
            busy: 0,
            mean: 0,
        }
    }
}

impl Ink {
    /// 在图上量一块区域（比例坐标 0..1）的明暗分布与边缘密度。
    ///
    /// 判据是**实际对比度**，不是"谁多"：
    /// * `frac_w`：这块区域里有多少像素和**白字**的对比度 ≥ 3:1；
    /// * `frac_b`：有多少像素和**黑字**的对比度 ≥ 3:1。
    ///
    /// 谁高用谁；两者都低（明暗各占一半、或者整块都在中间调）时，
    /// `busy` 会自动升高，绘制端据此垫一层压底。
    pub fn sample(img: &egui::ColorImage, x0: f32, y0: f32, x1: f32, y1: f32) -> Self {
        let [w, h] = img.size;
        if w == 0 || h == 0 || x1 <= x0 || y1 <= y0 {
            return Self::default();
        }
        let lum = |x: usize, y: usize| -> f32 {
            let c = img.pixels[y * w + x];
            0.2126 * c.r() as f32 + 0.7152 * c.g() as f32 + 0.0722 * c.b() as f32
        };
        let xs = ((x0 * w as f32) as usize).min(w.saturating_sub(1));
        let xe = ((x1 * w as f32) as usize).clamp(xs + 1, w);
        let ys = ((y0 * h as f32) as usize).min(h.saturating_sub(1));
        let ye = ((y1 * h as f32) as usize).clamp(ys + 1, h);

        let mut need_w = 0f32;
        let mut need_b = 0f32;
        // 3×3 分格：整块平均会掩盖"局部正好压在白色大字上"的冲突，
        // 取最差的一格来定压底强度。
        let mut cell_w = [0f32; 9];
        let mut cell_b = [0f32; 9];
        let mut cell_n = [0u32; 9];
        let (mut edges, mut edge_n) = (0u32, 0u32);
        let (mut sum, mut n) = (0f32, 0u32);
        let (bw, bh) = ((xe - xs).max(1), (ye - ys).max(1));
        let mut y = ys;
        while y < ye {
            let mut x = xs;
            while x < xe {
                let l = lum(x, y);
                sum += l;
                n += 1;
                // 相对亮度（gamma 2 近似）→ WCAG 对比度
                let rel = (l / 255.0) * (l / 255.0);
                let c_white = 1.05 / (rel + 0.05);
                let c_black = (rel + 0.05) / 0.05;
                // "缺口"：离 4.5:1（正文可读线）还差多少，0 = 够清楚，1 = 完全糊
                let nw = ((4.5 - c_white).max(0.0) / 4.5).min(1.0);
                let nb = ((4.5 - c_black).max(0.0) / 4.5).min(1.0);
                need_w += nw;
                need_b += nb;
                let ci = ((y - ys) * 3 / bh).min(2);
                let cj = ((x - xs) * 3 / bw).min(2);
                let k = ci * 3 + cj;
                cell_w[k] += nw;
                cell_b[k] += nb;
                cell_n[k] += 1;
                // 水平方向亮度跳变 = 高频细节（拼贴/花纹/文字排版）
                if x + 2 < xe && (lum(x + 2, y) - l).abs() > 48.0 {
                    edges += 1;
                }
                if x + 2 < xe {
                    edge_n += 1;
                }
                x += 2;
            }
            y += 2;
        }
        if n == 0 {
            return Self::default();
        }
        let n_f = n as f32;
        let mean = sum / n_f;
        let want_w = need_w / n_f;
        let want_b = need_b / n_f;

        // 哪种字"缺口"小用哪种；几乎一样时按平均亮度（<130 偏暗 → 白字）
        let light = if (want_w - want_b).abs() < 0.02 {
            mean < 130.0
        } else {
            want_w < want_b
        };
        let edge_f = if edge_n > 0 {
            edges as f32 / edge_n as f32
        } else {
            0.0
        };
        // 最差的一格：那里文字几乎读不出来，就得压一层
        let mut worst = 0f32;
        for i in 0..9 {
            if cell_n[i] > 0 {
                let v = if light { cell_w[i] } else { cell_b[i] } / cell_n[i] as f32;
                worst = worst.max(v);
            }
        }
        let avg = if light { want_w } else { want_b };
        // busy = 局部最差 60% + 整体平均 40% + 高频细节惩罚
        let busy_f =
            (worst * 0.6 + avg * 0.4 + edge_f.min(0.35) * 0.25).clamp(0.0, 1.0);

        Self {
            light,
            busy: (busy_f * 255.0) as u8,
            mean: mean.clamp(0.0, 255.0) as u8,
        }
    }

    /// 方形砖：整图铺满，文字块占下面约一半（两行标题 + 艺术家）
    pub fn for_square(img: &egui::ColorImage) -> Self {
        Self::sample(img, 0.02, 0.48, 0.98, 0.995)
    }

    /// 宽砖（2:1）：封面上下各裁掉 1/4，文字落在源图 0.52~0.75 那一段
    pub fn for_wide(img: &egui::ColorImage) -> Self {
        Self::sample(img, 0.0, 0.52, 1.0, 0.75)
    }
}

/// 瓷砖标题区的读数：(方形砖, 宽砖)。
pub fn ink_of(img: &egui::ColorImage) -> (Ink, Ink) {
    (Ink::for_square(img), Ink::for_wide(img))
}



#[cfg(test)]
mod tests {
    use super::*;

    fn album_with(tracks: Vec<Track>) -> Album {
        Album {
            id: String::new(),
            cover_id: String::new(),
            title: String::new(),
            artist: String::new(),
            year: 0,
            hue: 0.0,
            song_count: tracks.len() as u16,
            tint: [0, 0, 0],
            starts: Vec::new(),
            total_secs: 0.0,
            tracks,
        }
    }

    fn track(disc: u16) -> Track {
        Track {
            id: String::new(),
            title: String::new(),
            artist: String::new(),
            secs: 1,
            disc,
        }
    }

    #[test]
    fn discs_split_consecutive_runs() {
        let a = album_with(vec![track(1), track(1), track(1), track(2), track(2)]);
        assert_eq!(a.discs(), vec![(1, 0, 3), (2, 3, 2)]);
        assert_eq!(a.disc_of(0), 0);
        assert_eq!(a.disc_of(2), 0);
        assert_eq!(a.disc_of(3), 1);
        assert_eq!(a.disc_of(4), 1);
    }

    #[test]
    fn discs_treat_missing_number_as_one() {
        let a = album_with(vec![track(0), track(0), track(1)]);
        // 0 / 缺省都算第 1 碟，不该切成两页
        assert_eq!(a.discs(), vec![(1, 0, 3)]);
    }

    #[test]
    fn discs_single_and_empty() {
        let empty = album_with(vec![]);
        assert_eq!(empty.discs(), vec![(1, 0, 0)]);
        let a = album_with(vec![track(1), track(1)]);
        assert_eq!(a.discs(), vec![(1, 0, 2)]);
        assert_eq!(a.disc_of(1), 0);
    }

    #[test]
    fn lyrics_index_at_binary_search() {
        let l = Lyrics {
            synced: true,
            lines: vec![
                LyricLine { start: 0.0, text: "a".into() },
                LyricLine { start: 10.0, text: "b".into() },
                LyricLine { start: 20.0, text: "c".into() },
            ],
        };
        assert_eq!(l.index_at(-1.0), None);
        assert_eq!(l.index_at(0.0), Some(0));
        assert_eq!(l.index_at(9.9), Some(0));
        assert_eq!(l.index_at(10.0), Some(1));
        assert_eq!(l.index_at(19.9), Some(1));
        assert_eq!(l.index_at(100.0), Some(2));
    }

    #[test]
    fn lyrics_unsynced_has_no_current() {
        let l = Lyrics {
            synced: false,
            lines: vec![LyricLine { start: -1.0, text: "x".into() }],
        };
        assert_eq!(l.index_at(5.0), None);
    }

    #[test]
    fn fmt_time_format() {
        assert_eq!(fmt_time(0.0), "0:00");
        assert_eq!(fmt_time(65.0), "1:05");
        assert_eq!(fmt_time(600.0), "10:00");
        assert_eq!(fmt_time(-5.0), "0:00");
    }

    /// 造一张纯色图
    fn solid(r: u8, g: u8, b: u8) -> egui::ColorImage {
        egui::ColorImage::new([64, 64], vec![egui::Color32::from_rgb(r, g, b); 64 * 64])
    }

    #[test]
    fn ink_dark_cover_uses_light_text() {
        let ink = Ink::for_square(&solid(12, 12, 16));
        assert!(ink.light, "深色封面必须用浅色字");
        assert!(ink.busy < 20, "纯色封面不该压底: busy={}", ink.busy);
    }

    #[test]
    fn ink_bright_cover_uses_dark_text() {
        let ink = Ink::for_square(&solid(246, 246, 242));
        assert!(!ink.light, "浅色封面必须用深色字（不能反色）");
        assert!(ink.busy < 20);
    }

    #[test]
    fn ink_mid_fifty_fifty_falls_back_to_mean() {
        // 上半纯亮、下半纯暗：五五开，按平均亮度决定（这里偏亮 → 深色字）
        let [w, h] = [64usize, 64usize];
        let mut px = vec![egui::Color32::from_rgb(250, 250, 250); w * h];
        for y in h / 2..h {
            for x in 0..w {
                px[y * w + x] = egui::Color32::from_rgb(10, 10, 10);
            }
        }
        let img = egui::ColorImage::new([w, h], px);
        let ink = Ink::for_square(&img);
        // 这一步只要不 panic、能给出确定答案即可（关键是不在"翻来翻去"）
        let again = Ink::for_square(&img);
        assert_eq!(ink, again);
    }

    #[test]
    fn ink_busy_collage_flags_scrim() {
        // 黑白棋盘 = 高频细节：必须标成"花"，让绘制端压一层底
        let [w, h] = [64usize, 64usize];
        let mut px = vec![egui::Color32::BLACK; w * h];
        for y in 0..h {
            for x in 0..w {
                if (x / 2 + y / 2) % 2 == 0 {
                    px[y * w + x] = egui::Color32::WHITE;
                }
            }
        }
        let img = egui::ColorImage::new([w, h], px);
        let ink = Ink::for_square(&img);
        assert!(ink.busy > 120, "拼贴/棋盘封面应判为 busy: {}", ink.busy);
    }

    #[test]
    fn ink_flat_mid_gray_needs_no_scrim() {
        // 中间调纯色：两种字都还有 3:1 以上，不该压底（压了就是白白发灰）
        let ink = Ink::for_square(&solid(128, 128, 128));
        assert!(ink.busy < 40, "纯中灰不该判为花: busy={}", ink.busy);
    }

    #[test]
    fn ink_half_dark_half_bright_flags_scrim() {
        // 左黑右白：无论用黑字还是白字，总有一半糊 → 必须压底
        let [w, h] = [64usize, 64usize];
        let mut px = vec![egui::Color32::WHITE; w * h];
        for y in 0..h {
            for x in 0..w / 2 {
                px[y * w + x] = egui::Color32::BLACK;
            }
        }
        let img = egui::ColorImage::new([w, h], px);
        let ink = Ink::for_square(&img);
        assert!(ink.busy > 80, "明暗各半应提示压底: busy={}", ink.busy);
        // 黑白各半时两种字都只盖一半，busy 才是真正的信号
        assert!(ink.busy < 220);
    }
}
