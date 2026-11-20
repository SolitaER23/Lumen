//! Lumen —— Metro 版界面。
//!
//! 一屏到底，只有三页：
//!   首页（整库平铺成瓷砖墙）→ 专辑页（封面 + 曲目）→ 播放页（大封面 + 一条细进度线）
//!
//! 视觉语言全部来自 Windows Phone 8 的 Start 屏：
//!   * 纯黑舞台，瓷砖紧贴（6pt 缝），零圆角、零投影；
//!   * 大字用 Segoe UI Light，小标题用大写 + 字距；
//!   * 唯一的颜色是强调色（Cyan），只出现在进度、当前曲目、主按钮上；
//!   * 进场时瓷砖一块接一块翻出来（turnstile），翻完才有文字。

use std::collections::{HashSet, VecDeque};

use crate::anim::{dur, smoothstep, Nav, Spring, Timeline};
use crate::geometry::{lyric_geometry, step_inertia};
use crate::m3;
use crate::metro::{self as m, Glyph};
use crate::model::{fmt_time, Artwork, Library};
use crate::theme;
use eframe::egui::{self, pos2, vec2, Align2, Color32, Pos2, Rect, Sense, Ui};

// ---------------------------------------------------------------------------
// 尺寸
// ---------------------------------------------------------------------------

/// 瓷砖之间的缝
/// 瓷砖墙离屏幕边缘
const MARGIN: f32 = 12.0;
/// 底部常驻的迷你播放条
/// 动画目标帧率：Pixel 10a 是 120Hz 面板，按一帧一个 vsync 递请求。
/// 掉帧判定（平均帧率 / 最长帧）都以它为准，换 60Hz 设备时只改这一个数。
const TARGET_HZ: f32 = 120.0;
/// 最近一帧的时间（毫秒，相对启动）；UI 看门狗用它判断画面是否停住
static LAST_FRAME_MS: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
/// 音频是否在播（每帧由 App 写入）
static AUDIO_PLAYING: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
/// 首帧拿到的 egui Context：看门狗从别的线程 request_repaint 用它
static UI_CTX: std::sync::OnceLock<eframe::egui::Context> = std::sync::OnceLock::new();
static WATCHDOG: std::sync::OnceLock<()> = std::sync::OnceLock::new();

/// Activity 被系统重建、android_main 二次进入时，把上一轮事件循环关掉。
///
/// 一轮 eframe 循环占着一个 GL surface；若它没随 Activity 销毁而退出，
/// 新 Activity 就永远画不出第一帧（停在启动封面），而旧循环里的音频线程还在放。
/// 关闭旧视口会让旧循环 return → App 被 drop → 音频与 surface 一起释放。
pub fn close_previous_loop() {
    if let Some(ctx) = UI_CTX.get() {
        log::warn!("closing previous eframe loop (activity recreated)");
        ctx.send_viewport_cmd(eframe::egui::ViewportCommand::Close);
    }
}
/// UI 看门狗：进程还活着、歌还在放，但画面 2 秒没更新时，主动叫醒事件循环。
///
/// 热启动/息屏回来时，winit 的 Resumed 不一定能让 eframe 立刻排一帧；如果这时
/// 系统还停在启动封面（splash），用户看到的就是"音乐在放、界面没出来"。
/// 从后台线程 request_repaint 是 egui 支持的，能让循环重新跑起来。
fn spawn_ui_watchdog() {
    let _ = WATCHDOG.set(());
    std::thread::Builder::new()
        .name("lumen-ui-watch".into())
        .spawn(|| {
            loop {
                std::thread::sleep(std::time::Duration::from_millis(500));
                let Some(ctx) = UI_CTX.get() else { continue };
                if !AUDIO_PLAYING.load(std::sync::atomic::Ordering::Relaxed) {
                    continue;
                }
                // 和 LAST_FRAME_MS 用同一个时钟（UNIX 毫秒）
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_millis() as u64)
                    .unwrap_or(0);
                let last = LAST_FRAME_MS.load(std::sync::atomic::Ordering::Relaxed);
                if now.saturating_sub(last) > 2000 {
                    log::warn!(
                        "ui watchdog: no frame for {} ms while playing — request repaint",
                        now - last
                    );
                    ctx.request_repaint();
                }
            }
        })
        .ok();
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Page {
    Home,
    Album,
    Playing,
}

/// 首页瓷砖布局（每帧重算，输入和绘制共用同一份）
struct HomeLayout {
    tiles: Vec<(usize, Rect)>,
    /// 瓷砖墙的起点 y（滚动只作用于它下面的内容）
    top: f32,
    /// 内容总高
    content_h: f32,
    /// 视口高
    view_h: f32,
    /// 迷你播放条
    bar: Rect,
}

struct AlbumLayout {
    back: Rect,
    cover: Rect,
    info: Pos2,
    info_w: f32,
    play_btn: Rect,
    list: Rect,
    /// 列表内容偏移（= 手势位移 - 头图塌陷量），保证行始终 1:1 跟手
    list_offset: f32,
    /// 可滚动上限（按未塌陷时的列表顶端算，与塌陷无关）
    max_scroll: f32,
    /// 头图塌陷进度 0..1
    collapse: f32,
    row_h: f32,
    /// 多碟专辑的碟标签：(碟号, 点击区)。单碟为空。
    tabs: Vec<(u16, Rect)>,
    /// 当前展示的碟：(碟号, 起始绝对下标, 曲目数)
    disc: (u16, usize, usize),
}

/// 一首歌的歌词状态
#[derive(Clone)]
enum LyricState {
    Loading,
    /// 服务端明确说没有歌词（或解析出来是空的）
    Missing,
    Ready(crate::model::Lyrics),
}

struct PlayLayout {
    back: Rect,
    cover: Rect,
    bar: Rect,
    prev: Rect,
    play: Rect,
    next: Rect,
    /// 宽屏双列：右侧歌词面板（窄屏 = None，歌词还是封面那块方形区域）
    lyrics_panel: Option<Rect>,
    wide: bool,
}

pub struct App {
    lib: Library,
    art: Vec<Option<Artwork>>,
    art_state: Vec<u8>,
    /// 每张封面文字区的读数：(方形砖, 宽砖)——决定文字颜色和要不要压底
    ink: Vec<(crate::model::Ink, crate::model::Ink)>,
    /// 这个亮度是不是真封面量出来的（程序化封面只做兜底，不能盖掉真值）
    ink_real: Vec<bool>,
    /// 每张封面的淡入进度 0..1：纹理到位后 80ms 内淡进来，不"啪"地跳出来
    art_fade: Vec<f32>,
    decoded: Vec<Option<egui::ColorImage>>,
    decoded_lru: VecDeque<usize>,
    cover_queue: VecDeque<(usize, [u8; 3])>,
    urgent_sent: HashSet<usize>,
    wanted: Vec<usize>,
    /// 启动时把"以后要画的字"排进字体图集的队列：(文字, 字体)
    warm_queue: std::collections::VecDeque<(String, egui::FontId)>,
    /// 队列已建好（曲库到货后建一次）
    warm_built: bool,
    /// 曲目已到货、但曲名字形还没预热完的专辑（等预热完再应用，避免渲染列表时现场光栅化卡顿）
    tracks_pending: Vec<(usize, Vec<crate::model::Track>)>,

    // ---- 导航 ----
    page: Page,
    /// 0 = 首页, 1 = 专辑, 2 = 播放（弹簧驱动，可被返回手势直接接管）
    nav: Nav,
    /// 预测性返回：手势起点/终点（页面值），`None` = 没有进行中的手势
    back_drag: Option<(f32, f32)>,
    /// 首页/覆盖层上被返回手势拉动时的压暗进度 0..1
    home_back: f32,
    /// 底栏曲目胶囊的换歌滑入（1 = 旧名在场，0 = 新名落定）
    bar_slide: Spring,
    /// 上一首曲名（换歌时向左退场）
    bar_title: String,
    bar_prev_title: String,
    bar_song: String,
    /// 首页滚动位置（px，0 = 顶部）。不用弹簧：拖拽 1:1 跟手，松手走惯性衰减
    scroll: f32,
    /// 惯性速度（px/s，正 = 内容继续往上走）
    scroll_vel: f32,
    /// 手指正按着拖（这期间不做纹理重活）
    scrolling: bool,
    /// 上一帧算出的可滚动上限
    scroll_max: f32,
    /// 专辑页曲目列表滚动位置（px）
    list_scroll: f32,
    /// 列表的惯性速度（px/s）
    list_vel: f32,
    /// 列表是否正被拖动
    list_dragging: bool,
    /// 专辑页标题是否已收成一行（带滞回，避免停在折叠阈值上来回翻）
    title_one_line: bool,
    /// 多碟专辑：当前展示第几碟（Album::discs() 的下标）
    disc_view: usize,
    /// 切碟后的横向滑入偏移（pt），Tween 回 0
    disc_anim: Spring,
    /// 横向手势状态：(按下时的 x, 方向判定 0=未定 1=横向 2=纵向)
    disc_drag: Option<(f32, u8)>,
    /// 横向手势累计位移
    disc_dx: f32,
    /// 跑马灯：当前正在滚的歌名 + 它开始展示的时刻（秒）
    marquee_key: String,
    marquee_t0: f64,
    /// 本帧的跑马灯相位（秒），绘制阶段直接用
    marquee_t: f32,
    /// 列表可滚上限（上一帧布局算出）
    list_max: f32,
    /// 这一次拖拽累计移动量（用来区分"点一下"和"拖一下"）
    drag_acc: f32,
    /// 瓷砖进场时间轴
    tl: Timeline,
    pressed_tile: Option<usize>,
    selected: usize,

    // ---- 播放 ----
    playing: bool,
    pos_secs: f32,
    streaming: bool,
    /// 当前正在播放的曲目下标（专辑内）。这是明确状态，不靠 `current_track_index()`
    /// 从 pos_secs 反推——后者在曲目边界会提前指向下一首，导致位置重建错乱、跳歌。
    cur_track: usize,
    /// 切歌时刻：切歌后 audio 引擎有 ~100ms 异步切换窗口（position 还停在旧歌末尾），
    /// 这期间 `cur_start + p` 会把旧歌末尾叠加到新歌 start 上造成 pos 跳变。缓冲期内不重建。
    stream_switch_at: Option<std::time::Instant>,
    /// 当前歌有没有真实音频源（cached / 流式都为 true）。
    /// 有真实音频时进度用引擎 position 同步。
    has_audio: bool,
    /// 正在播放的专辑下标。`selected` 是「选中展示」的专辑，切到别的专辑只是看，
    /// 不打断正在播的这张；两者解耦后，只有 selected == playing_album 才显示播放态。
    playing_album: Option<usize>,
    pending_play: bool,
    /// 迷你条播放键在曲目还没到时记下的待播专辑
    pending_play_album: Option<usize>,
    seek_preview: Option<f32>,
    status: String,
    audio: Option<crate::audio::Audio>,
    remote: Option<crate::remote::Remote>,
    /// 连上曲库（`Msg::Albums` 到货）才算登录成功。`remote.is_some()` 只代表
    /// "有后台线程在跑"，端口写错时它也是 Some——之前拿它当"已登录"，
    /// 于是连不上也显示"已登录"，还卡在加载页出不去。
    connected: bool,
    /// 正在连接（从点"连接"到收到曲库/错误之间）
    connecting: bool,
    /// 连接失败的原因（空 = 没有错误）
    conn_error: String,

    // ---- 登录 ----
    /// 登录界面是否打开（未登录时显示，登录成功自动关闭）
    login_open: bool,
    /// 点击"已登录"标识后弹出退出登录确认面板
    confirm_logout: bool,
    /// 退出登录面板的展开进度（0=收起，1=展开）：淡入 + 上移 10pt
    confirm_fx: Spring,
    /// 播放/暂停图标交叉淡化进度（0=播放三角，1=暂停双线）
    play_fx: Spring,
    /// 曲目列表的进场进度（0=未出现，1=全部就位）：新列表按行错峰淡入
    rows_fx: Spring,
    /// 本帧各控件的按压强度 0..1（输入阶段量、绘制阶段用）。
    /// 顺序：[0]返回 [1]上一首 [2]播放 [3]下一首 [4]迷你条播放键 [5]连接按钮
    press: [f32; 6],
    settings: crate::settings::Settings,
    edit_base: String,
    edit_user: String,
    edit_pass: String,
    setup_msg: String,
    cache_trimmed: bool,
    /// 软键盘桥：把 Android IME 的编辑会话翻译成 egui 的 ImeEvent（见 `src/ime.rs`）。
    /// 文本的唯一所有者是 egui 的 TextEdit 缓冲区，这里只做状态翻译。
    ime: crate::ime::Bridge,
    /// 三个输入框的实际 rect（setup_input 记录，draw_setup 画标签/下划线用）——
    /// 让绘制和输入用同一个 rect，避免两套坐标对不齐。
    field_rects: [egui::Rect; 3],

    // ---- 环境 ----
    safe_top_px: f32,
    safe_bottom_px: f32,
    safe_top_pt: f32,
    safe_bottom_pt: f32,
    /// 屏幕底部圆角半径（px，来自 Display.getRoundedCorner；桌面/旧系统为 0）
    corner_px: f32,
    /// 换算成 pt 后的圆角半径（每帧按 pixels_per_point 更新）
    corner_pt: f32,
    /// 上次复查系统栏的时间（秒，egui 时间轴）
    inset_probe_at: f64,
    k: f32,
    /// 上一帧的 CPU 用时（ms，eframe 提供）：用它决定这帧要不要做纹理重活
    last_cpu_ms: f32,
    /// 滚动期间的帧率统计（每秒打一条日志，用来判断"卡"到底卡在哪）
    scroll_t0: Option<std::time::Instant>,
    /// 慢帧日志限流
    frame_warn_at: Option<std::time::Instant>,
    /// 播放健康度采样：(时间, 位置, 当前歌 id)
    pos_probe: Option<(std::time::Instant, f32, String)>,

    // ---- 歌词 ----
    /// song_id -> 歌词状态
    lyric_cache: std::collections::HashMap<String, LyricState>,
    /// 已经发过请求的 id，避免重复请求
    lyric_asked: std::collections::HashSet<String>,
    /// 歌词内容的滚动位置（px，内容坐标）
    lyric_scroll: Spring,
    /// 歌词面板的淡入比例（歌词比封面晚到，到位后淡入）
    lyric_fade: Spring,
    /// 歌词滚动当前对应的 song_id（换歌要直接落位）
    lyric_song: String,
    scroll_frames: u32,
    scroll_sum_ms: f32,
    scroll_worst_ms: f32,
    hz_ok: bool,
    hz_tries: u8,
    hz_frames: u32,
    tap_at: Option<std::time::Instant>,
    /// 调试：LUMEN_PAGE=album|playing 时，曲库一到就直接进那一页（桌面截图用）
    /// 只在 debug 构建生效，release 不含这个后门。
    #[cfg(debug_assertions)]
    dev_page: Option<String>,
}

impl App {
    /// 歌词面板（Zune 式）：当前行亮、上下行渐暗，整块随进度平滑滚动。
    fn draw_lyrics(
        &self,
        p: &egui::Painter,
        rect: Rect,
        lyrics: &crate::model::Lyrics,
        t_rel: f32,
        fade: f32,
    ) {
        if rect.height() < 40.0 || lyrics.lines.is_empty() || fade <= 0.01 {
            return;
        }
        let k = self.k;
        let roles = self.roles();
        let geo = lyric_geometry(&lyrics.lines, rect.width(), k);
        let clip = p.with_clip_rect(rect);
        let scroll = self.lyric_scroll.value;
        // 当前行停在面板 42% 高度上（略高于中线，给下面留出"接下来"的暗示）
        let anchor = rect.min.y + rect.height() * 0.42;
        let cur = lyrics.index_at(t_rel);
        // 只有"已经开始唱"的同步歌词才有当前行；开唱前和纯文本歌词都整体压暗
        let has_current = lyrics.synced && cur.is_some();
        let font = m::regular(16.5 * k);

        for (i, line) in lyrics.lines.iter().enumerate() {
            let cy = rect.min.y + geo.centers[i] - scroll;
            if cy < rect.min.y - 90.0 * k || cy > rect.max.y + 90.0 * k {
                continue;
            }
            // 强调度：离锚点越远越暗
            let e = if has_current {
                (1.0 - (cy - anchor).abs() / (34.0 * k * 1.6)).clamp(0.0, 1.0)
            } else {
                0.5 // 没有"当前行"，统一亮度
            };
            let a = if has_current {
                0.15 + 0.85 * e.powf(1.5)
            } else {
                0.45
            } * fade;
            // 量化到 1/8：epaint 的 galley 缓存键里带颜色，逐帧微调透明度会让每行每帧都重排
            let a = (a.clamp(0.0, 1.0) * 8.0).round() / 8.0;
            let col = roles.on_surface.gamma_multiply(a);
            // 文本本身约 22.5k/行，按实际高度居中放进槽位里
            let text_h = if geo.rows[i] == 2 { 45.0 * k } else { 22.5 * k };
            m::text_wrap(
                &clip,
                pos2(rect.min.x, cy - text_h * 0.5),
                &line.text,
                font.clone(),
                col,
                rect.width(),
                geo.rows[i],
            );
        }

        // 当前行左侧的强调竖条：位置跟着弹簧走，所以是"滑过去"而不是闪现
        if let (true, Some(ci)) = (has_current, cur) {
            let cy = rect.min.y + geo.centers[ci.min(geo.centers.len() - 1)] - scroll;
            let e = (1.0 - (cy - anchor).abs() / (34.0 * k * 1.6)).clamp(0.0, 1.0);
            if e > 0.05 {
                // 折成两行的句子，竖条也跟着变高
                let bar_h = if geo.rows.get(ci).copied().unwrap_or(1) == 2 {
                    36.0 * k
                } else {
                    22.0 * k
                };
                // 注意用未裁剪的 painter：竖条画在面板左外侧，用 clip 会被裁掉
                m::rect(
                    p,
                    Rect::from_min_size(
                        pos2(rect.min.x - 12.0 * k, cy - bar_h * 0.5),
                        vec2(2.0, bar_h),
                    ),
                    roles.primary.gamma_multiply(0.9 * e * fade),
                );
            }
        }
    }

    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        // 持久化事件日志：启动即生效；panic 也写一条崩溃记录（含堆栈信息）
        crate::eventlog::init();
        // UI 看门狗：热启动/息屏回来卡在启动封面时，主动叫醒事件循环
        spawn_ui_watchdog();
        let default_hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |info| {
            crate::eventlog::log(
                crate::eventlog::Level::Error,
                crate::eventlog::EventKind::AppCrash,
                &format!("panic: {info}"),
            );
            default_hook(info);
        }));
        crate::eventlog::log(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::AppStart,
            &format!("version=dev screen=booting"),
        );

        theme::install_fonts(&cc.egui_ctx);
        // 真曲库由 Navidrome 拉取（Msg::Albums 到来后填充），启动时是空的
        let lib = Library::empty();
        let art = vec![None; lib.albums.len()];

        let settings = crate::settings::Settings::load();
        let auto_connect = settings.is_complete();
        let remote = if auto_connect {
            Some(crate::remote::Remote::spawn(crate::subsonic::Config {
                base: settings.base.clone(),
                user: settings.user.clone(),
                password: settings.password.clone(),
            }))
        } else {
            None
        };

        let mut app = Self {
            art_state: vec![0u8; lib.albums.len()],
            ink: vec![(crate::model::Ink::default(), crate::model::Ink::default()); lib.albums.len()],
            ink_real: vec![false; lib.albums.len()],
            art_fade: vec![1.0; lib.albums.len()],
            art,
            decoded: Vec::new(),
            decoded_lru: VecDeque::new(),
            cover_queue: VecDeque::new(),
            urgent_sent: HashSet::new(),
            wanted: Vec::new(),
            warm_queue: std::collections::VecDeque::new(),
            warm_built: false,
            tracks_pending: Vec::new(),
            selected: 0,
            page: Page::Home,
            nav: Nav::new(0.0),
            back_drag: None,
            home_back: 0.0,
            bar_slide: Spring::spatial(0.0),
            bar_title: String::new(),
            bar_prev_title: String::new(),
            bar_song: String::new(),
            scroll: 0.0,
            scroll_vel: 0.0,
            scrolling: false,
            scroll_max: 0.0,
            list_scroll: 0.0,
            list_vel: 0.0,
            list_dragging: false,
            title_one_line: false,
            disc_view: 0,
            disc_anim: Spring::spatial(0.0),
            disc_drag: None,
            disc_dx: 0.0,
            marquee_key: String::new(),
            marquee_t0: 0.0,
            marquee_t: 0.0,
            list_max: 0.0,
            drag_acc: 0.0,
            tl: Timeline::new(),
            pressed_tile: None,
            playing: false,
            pos_secs: 0.0,
            streaming: false,
            cur_track: 0,
            stream_switch_at: None,
            has_audio: false,
            playing_album: None,
            pending_play: false,
            pending_play_album: None,
            seek_preview: None,
            status: if settings.is_complete() {
                "正在连接曲库…".into()
            } else {
                String::new()
            },
            audio: crate::audio::Audio::new(),
            remote,
            connected: false,
            connecting: auto_connect,
            conn_error: String::new(),
            login_open: !auto_connect,
            confirm_logout: false,
            confirm_fx: Spring::effects(0.0),
            play_fx: Spring::effects(0.0),
            rows_fx: Spring::effects(1.0),
            press: [0.0; 6],
            edit_base: if settings.base.is_empty() {
                "http://".to_string()
            } else {
                settings.base.clone()
            },
            edit_user: settings.user.clone(),
            edit_pass: settings.password.clone(),
            settings,
            setup_msg: String::new(),
            cache_trimmed: false,
            ime: crate::ime::Bridge::default(),
            field_rects: [egui::Rect::NOTHING; 3],
            safe_top_px: 0.0,
            safe_bottom_px: 0.0,
            safe_top_pt: 26.0,
            safe_bottom_pt: 0.0,
            corner_px: crate::safe_area::corner_radius_bottom_px(),
            corner_pt: 0.0,
            inset_probe_at: 0.0,
            k: 1.0,
            last_cpu_ms: 0.0,
            scroll_t0: None,
            frame_warn_at: None,
            pos_probe: None,
            lyric_cache: std::collections::HashMap::new(),
            lyric_asked: std::collections::HashSet::new(),
            lyric_scroll: Spring::new(0.0, 130.0, 26.0),
            lyric_fade: Spring::new(0.0, 200.0, 28.0),
            lyric_song: String::new(),
            scroll_frames: 0,
            scroll_sum_ms: 0.0,
            scroll_worst_ms: 0.0,
            hz_ok: false,
            hz_tries: 0,
            hz_frames: 0,
            tap_at: None,
            #[cfg(debug_assertions)]
            dev_page: std::env::var("LUMEN_PAGE").ok(),
            lib,
        };
        #[cfg(debug_assertions)]
        if app.dev_page.as_deref() == Some("setup") {
            app.login_open = true;
        }
        app
    }

    /// 悬浮迷你条常显（登录页/播放页由调用处排除）——首页没有它就没法直接起播。
    fn bar_visible(&self) -> bool {
        true
    }

    /// 当前页面的 M3 颜色角色（取正在播 / 选中的专辑封面色）
    fn roles(&self) -> crate::m3::Roles {
        let idx = self
            .playing_album
            .unwrap_or(self.selected)
            .min(self.lib.albums.len().saturating_sub(1));
        self.roles_for(idx)
    }

    fn roles_for(&self, idx: usize) -> crate::m3::Roles {
        self.lib
            .albums
            .get(idx)
            .map(|a| crate::m3::Roles::from_tint(a.tint, a.hue))
            .unwrap_or_else(crate::m3::Roles::fallback)
    }
    /// 底部要给悬浮条留出的总高度（含浮起间隙）；不显示时只让安全区
    fn bar_reserve(&self) -> f32 {
        if self.bar_visible() {
            72.0 * self.k + self.bar_bottom() + 6.0 * self.k
        } else {
            self.safe_bottom_pt
        }
    }
    /// 某张专辑封面的宽高比（封面还没到 / 程序化封面按 1:1）
    fn art_aspect(&self, idx: usize) -> f32 {
        self.art
            .get(idx)
            .and_then(|a| a.as_ref())
            .map(|a| a.aspect)
            .unwrap_or(1.0)
    }
    /// 播放条底部要留的高度：手势条（白条）会盖在画面最下面 10pt 左右，
    /// 系统报的 safe bottom 在全面屏上经常是 0，所以给一个下限。
    fn bar_bottom(&self) -> f32 {
        self.safe_bottom_pt.max(10.0 * self.k)
    }

    /// 迷你条内容的左右安全内缩（pt）。
    ///
    /// 条本身铺满整宽（Metro 的边到边），但屏幕底部两个圆角会把贴边的像素裁掉：
    /// 半径为 R、内容底边离屏幕底 d 时，圆角在水平方向要吃掉
    /// `R - √(R² - (R-d)²)`。取它再加 2pt 余量，和基础 8pt 取大。
    /// 没有圆角（桌面 / API < 31）时就是基础 8pt，行为与旧版一致。
    // 预留给胶囊贴屏幕圆角的场景，目前由 M3 药丸外边距接管
    #[allow(dead_code)]
    fn bar_inset_x(&self) -> f32 {
        let k = self.k;
        let base = 8.0 * k;
        let r = self.corner_pt;
        if r <= 0.0 {
            return base;
        }
        let d = 7.0 * k; // 缩略封面底边到屏幕底的距离
        let arc = if d < r {
            let sq = (r * r - (r - d) * (r - d)).max(0.0);
            r - sq.sqrt()
        } else {
            0.0
        };
        base.max(arc + 2.0 * k)
    }

    pub fn set_safe_area(&mut self, top_px: f32, bottom_px: f32) {
        self.safe_top_px = top_px;
        self.safe_bottom_px = bottom_px;
    }

    // -----------------------------------------------------------------
    // 主循环
    // -----------------------------------------------------------------

    pub fn ui_root(&mut self, ui: &mut Ui) {
        let ctx = ui.ctx().clone();
        let dt = ctx.input(|i| i.stable_dt).clamp(0.0, 0.05);
        let screen = ui.max_rect();
        let ppp = ctx.pixels_per_point().max(0.1);
        // 以 760pt 高为基准：Pixel 10a 实测约 1.13
        self.k = (screen.height() / 760.0).clamp(0.86, 1.55);

        self.safe_top_pt = (self.safe_top_px / ppp).max(24.0);
        self.safe_bottom_pt = (self.safe_bottom_px / ppp).max(0.0);
        self.corner_pt = (self.corner_px / ppp).max(0.0);

        // 系统栏 / 手势条变化（切三键导航、进出分屏、旋转）：每秒复查一次，
        // 变了就更新，布局下一帧跟着走，不用重启。失败（读不到）保持原值。
        let now_s = ctx.input(|i| i.time);
        if now_s - self.inset_probe_at > 1.0 {
            self.inset_probe_at = now_s;
            if let Some((t, b)) = crate::safe_area::current_insets() {
                if (t - self.safe_top_px).abs() > 0.5 || (b - self.safe_bottom_px).abs() > 0.5 {
                    log::info!("insets changed: top={t} bottom={b}");
                    self.set_safe_area(t, b);
                    self.corner_px = crate::safe_area::corner_radius_bottom_px();
                }
            }
        }

        // 看门狗需要的两个信号：这一帧的时间、音频是否在播
        {
            use std::sync::atomic::Ordering;
            let _ = UI_CTX.set(ctx.clone());
            let now_ms = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0);
            LAST_FRAME_MS.store(now_ms, Ordering::Relaxed);
            AUDIO_PLAYING.store(self.playing, Ordering::Relaxed);
        }
        let t_frame0 = std::time::Instant::now();
        self.pump_remote(&ctx);
        let t_pump = t_frame0.elapsed().as_secs_f32() * 1000.0;

        // 跑马灯相位：正在播的歌名一变就从头开始，同一首歌的
        // 播放页大标题 / 迷你条 / 列表里那一行永远同步。
        let now = ctx.input(|i| i.time);
        let title_now = self.current_track_info().1;
        if title_now != self.marquee_key {
            self.marquee_key = title_now;
            self.marquee_t0 = now;
        }
        self.marquee_t = (now - self.marquee_t0).max(0.0) as f32;

        // 离开登录页（连上曲库 / 点了"先看看"）：结束软键盘会话，
        // 顺便让输入法把半截组合文字交出去，别留在缓冲区里。
        if !self.login_open && self.ime.field().is_some() {
            let _ = self.ime.end();
        }

        // 曲库还没到（启动后 Msg::Albums 尚未到来）：显示等待/失败态，不跑绘制逻辑——
        // 下面的 draw_album/draw_playing/draw_bar 都会访问 albums[selected]，空库会越界。
        if self.lib.albums.is_empty() {
            // 连接表单优先：没曲库且没连上时，先让用户连接（而不是卡在加载态）
            if self.login_open {
                self.setup_input(ui, screen);
                self.draw_setup(&ui.painter().clone(), screen);
                return;
            }
            self.library_wait_page(ui, screen);
            return;
        }

        self.sync_predictive_back(&ctx);
        self.nav.step(dt);
        step_inertia(&mut self.list_scroll, &mut self.list_vel, self.list_max, dt, self.list_dragging);
        self.tl.step(dt);
        // 播放/暂停图标交叉淡化、退出面板展开：都走同一档缓动，节奏统一
        self.play_fx.to(if self.playing { 1.0 } else { 0.0 });
        self.play_fx.step(dt);
        self.confirm_fx.to(if self.confirm_logout { 1.0 } else { 0.0 });
        self.confirm_fx.step(dt);
        self.disc_anim.step(dt);
        self.rows_fx.to(1.0);
        self.rows_fx.step(dt);
        self.tick_audio(dt);
        step_inertia(&mut self.scroll, &mut self.scroll_vel, self.scroll_max, dt, self.scrolling);

        let lay = self.home_layout(screen);
        self.scroll_max = (lay.content_h - lay.view_h).max(0.0);

        // 新到的封面淡进来（~180ms 指数缓出），滚动时图不会"啪"地跳出来。
        // 减弱动效时直接显示（封面不是操作反馈，不需要任何过渡）。
        if crate::anim::reduced() {
            for f in self.art_fade.iter_mut() {
                *f = 1.0;
            }
        } else if self.art_fade.iter().any(|f| *f < 1.0) {
            for f in self.art_fade.iter_mut() {
                if *f < 1.0 {
                    *f = (*f + dt / dur::FAST.max(0.01)).min(1.0);
                }
            }
        }

        // 字体图集预热：每帧只光栅化少量字形。
        //
        // 中文字形光栅化约 1ms/字，一帧预算才 16.7ms。之前每帧排 16 条（约 96 字 ≈
        // 74~140ms），曲名到货那一帧会卡成「slow frame 74~140ms」。降到每帧 4 条
        // （约 20 字 ≈ 20ms），把曲名/瓷砖光栅化摊到多帧：一张 12~31 首的专辑，
        // 曲名在 3~8 帧内（50~130ms，nav 动画 300ms 内）就绪，列表照常显示，不再卡一帧。
        if !self.warm_built && !self.lib.albums.is_empty() {
            self.warm_queue = self.build_warm_queue();
            self.warm_built = true;
        }
        if !self.warm_queue.is_empty() {
            let mut done = 0;
            while done < 4 {
                let Some((text, font)) = self.warm_queue.pop_front() else { break };
                ctx.fonts_mut(|f| {
                    let _ = f.layout_no_wrap(text, font, Color32::WHITE);
                });
                done += 1;
                // 图集用到 50% 就收手：epaint 在 80% 会把整张图集连同所有 galley 清空，
                // 那时候不是"慢一帧"而是"全部重排"，比不预热还糟。
                if done % 4 == 0 {
                    let ratio = ctx.fonts(|f| f.font_atlas_fill_ratio());
                    if ratio > 0.5 {
                        log::info!("warm: 图集已 {:.0}%，停止预热（剩 {} 条）", ratio * 100.0, self.warm_queue.len());
                        self.warm_queue.clear();
                        break;
                    }
                }
            }
            if self.warm_queue.is_empty() {
                let ratio = ctx.fonts(|f| f.font_atlas_fill_ratio());
                log::info!("warm: 预热完成，图集占用 {:.0}%", ratio * 100.0);
            }
        }
        // 曲名预热完后，把 pending 的曲目应用上（列表这时才渲染，不再现场光栅化卡顿）
        if self.warm_queue.is_empty() && !self.tracks_pending.is_empty() {
            let pending = std::mem::take(&mut self.tracks_pending);
            for (album_idx, tracks) in pending {
                self.apply_tracks(album_idx, tracks);
            }
        }
        let t_warm = t_frame0.elapsed().as_secs_f32() * 1000.0;

        self.wanted = self.want_set(screen, &lay);
        // 滚动中一根手指都不许做纹理重活：上传一张 400² 封面要 2~4ms，
        // 一帧预算才 8.3ms，宁可让砖块先显示纯色，松手再补图。
        let busy = self.scrolling || self.scroll_vel.abs() > 30.0 || !self.nav.at_rest();
        // 封面还没加载完就一定要维护（不受 CPU 阈值限制），否则 warm 阶段
        // CPU 经常 >10ms 会把 maintain_art 卡掉，封面永远停在纯色块。
        let covers_pending = self.wanted.iter().any(|&i| {
            self.art_state.get(i).copied().unwrap_or(0) == 0
                && self.lib.albums.get(i).map(|a| !a.cover_id.is_empty()).unwrap_or(false)
        });
        if (!busy && self.last_cpu_ms < 10.0) || covers_pending {
            self.upload_covers(&ctx, 2);
            self.maintain_art();
        }

        // 首帧之后把面板钉在 120Hz（这时 surface 才挂上）。
        // Pixel 10a 支持 120/60/40/30/24/20；动画期间按 8ms 递帧，正好一帧一个 vsync。
        if !self.hz_ok && self.hz_tries < 8 {
            self.hz_frames += 1;
            if self.hz_frames % 30 == 0 {
                self.hz_tries += 1;
                self.hz_ok = crate::safe_area::set_frame_rate(TARGET_HZ);
            }
        }

        let painter = ui.painter().clone();
        painter.rect_filled(screen, 0.0, m::bg());

        if self.login_open {
            self.setup_input(ui, screen);
            self.draw_setup(&painter, screen);
        } else {
            // 三页叠着画：按 nav 值平移，谁在最上层谁挡着
            let w = screen.width();
            let nav = self.nav.value();
            // 只有页面停稳之后才接受点击，转场中途的残留手指不该误触
            let settled = self.nav.at_rest();
            if nav < 0.999 {
                let dx = -w * nav.clamp(0.0, 1.0);
                if settled && self.page == Page::Home {
                    self.home_input(ui, screen, &lay, dx);
                }
                self.draw_home(&painter, screen, &lay, dx);
            }
            if nav > 0.001 && nav < 1.999 {
                let dx = if nav <= 1.0 {
                    w * (1.0 - nav)
                } else {
                    -w * (nav - 1.0)
                };
                let al = self.album_layout(screen);
                self.list_max = al.max_scroll;
                if settled && self.page == Page::Album {
                    self.album_input(ui, screen, &al, dx);
                }
                self.draw_album(&painter, screen, &al, dx);
            }
            if nav > 1.001 {
                let dx = w * (1.0 - (nav - 1.0).clamp(0.0, 1.0));
                let pl = self.playing_layout(screen);
                if settled && self.page == Page::Playing {
                    self.playing_input(ui, screen, &pl, dx);
                }
                self.draw_playing(&painter, screen, &pl, dx);
            }

            // 悬浮迷你播放条：固定贴底、不跟页面平移，只在最上层画一次——
            // 滚动、翻页、进出专辑时位置不变，不会闪，也不会被页面内容盖住。
            // 播放页不画（那里已经有整套播控，画了会压歌词）。
            // 绘制不受转场影响（转场中也在原位），只有点击要等页面停稳。
            if self.page != Page::Playing {
                if self.bar_visible() {
                    if settled {
                        self.bar_input(ui, screen);
                    }
                    self.draw_bar(&painter, screen, lay_bar(self, screen));
                }
            }
        }

        // 预测性返回：首页/覆盖层被拉动时压暗，读起来像应用正在被退出
        if self.home_back > 0.01 {
            painter.rect_filled(
                screen,
                0.0,
                Color32::from_black_alpha((120.0 * self.home_back) as u8),
            );
        }

        // 退出登录确认面板（画在最上层）
        if (self.confirm_logout || !self.confirm_fx.at_rest()) && !self.login_open {
            self.logout_confirm(ui, screen);
        }

        // 首帧清一次缓存
        if !self.cache_trimmed {
            self.cache_trimmed = true;
            if let Some(r) = &self.remote {
                r.trim_cache();
            }
        }

        // 系统返回（HyperOS 侧滑 / 三键返回）
        // 系统桥装上了就只走回调（避免 KEYCODE_BACK 又来一次，双重返回）
        if !crate::back::has_bridge() && ctx.input(|i| i.key_pressed(egui::Key::BrowserBack)) {
            self.go_back(&ctx);
        }

        // 需要"每帧都画"的东西：翻页、滚动、进场、封面淡入、手指按着、补图
        let animating = !self.nav.at_rest()
            || !self.play_fx.at_rest()
            || !self.confirm_fx.at_rest()
            || !self.disc_anim.at_rest()
            || !self.rows_fx.at_rest()
            || self.scrolling
            || self.scroll_vel.abs() > 2.0
            || (self.scroll - self.scroll.clamp(0.0, self.scroll_max)).abs() > 0.5
            || self.list_dragging
            || self.list_vel.abs() > 2.0
            || (self.list_scroll - self.list_scroll.clamp(0.0, self.list_max)).abs() > 0.5
            || self.tl.t < 3.0
            || self.art_fade.iter().any(|f| *f < 1.0)
            || !self.lyric_scroll.at_rest()
            || !self.lyric_fade.at_rest()
            || !self.cover_queue.is_empty()
            || self.seek_preview.is_some()
            || self.home_back > 0.01
            || !self.bar_slide.at_rest()
            || self.back_drag.is_some()
            || ctx.input(|i| i.pointer.any_down());
        self.track_motion(dt, animating);
        // 任意一帧超过 30ms 都记一笔（限流：一秒最多一条）
        let frame_ms = t_frame0.elapsed().as_secs_f32() * 1000.0;
        if frame_ms > 30.0 {
            let now = std::time::Instant::now();
            if self.frame_warn_at.map(|t| now.duration_since(t).as_millis() > 700).unwrap_or(true) {
                self.frame_warn_at = Some(now);
                log::warn!(
                    "slow frame {frame_ms:.1} ms (cpu {:.1}) pump {:.1} warm {:.1} draw {:.1} nav={:.2}",
                    self.last_cpu_ms,
                    t_pump,
                    t_warm - t_pump,
                    frame_ms - t_warm,
                    self.nav.value()
                );
            }
        }

        if animating {
            // 动画期间直接 request_repaint()，节拍交给 vsync：swap 会阻塞到下一个垂直同步，
            // 120Hz 面板就是 120fps。用 request_repaint_after(8ms) 反而会踩在 vsync 前
            // 0.3ms 才唤醒，抖动一下就漏一帧 → 实测只有 60~73fps。
            ctx.request_repaint();
        } else if self.playing && self.page == Page::Playing {
            // 播放页静止时只有进度线和歌词高亮在变：有歌词按 8fps 走（换行更跟手），
            // 没歌词 4fps 足够——2px 的线走 1 像素要半分钟，不值得整屏重画。
            let ms = if self.has_lyrics() { 120 } else { 240 };
            ctx.request_repaint_after(std::time::Duration::from_millis(ms));
        } else {
            ctx.request_repaint_after(std::time::Duration::from_millis(400));
        }
    }

    /// 动画帧率统计：一段动画（滑动 / 翻页 / 进场 / 淡入）结束后，
    /// 只有真的掉了帧才记一条日志——顺的时候完全静默。
    fn track_motion(&mut self, dt: f32, animating: bool) {
        if !animating {
            if let Some(t0) = self.scroll_t0.take() {
                let n = self.scroll_frames.max(1) as f32;
                // 用"统计到的帧间隔之和"算帧率：拿墙钟会把动画前后的空档也算进去，
                // 满帧的时候会误报成 55fps。
                let fps = if self.scroll_sum_ms > 1.0 {
                    n * 1000.0 / self.scroll_sum_ms
                } else {
                    0.0
                };
                let _ = t0;
                // 掉帧判定按目标帧率算：最长帧超过 2.5 个预算，或平均帧率低于 85%
                let budget = 1000.0 / TARGET_HZ;
                let bad = self.scroll_worst_ms > budget * 2.5 || fps < TARGET_HZ * 0.85;
                if bad {
                    log::warn!(
                        "motion hitches: {fps:.0} fps  avg {:.1} ms  max {:.1} ms  (cpu {:.1} ms)",
                        self.scroll_sum_ms / n,
                        self.scroll_worst_ms,
                        self.last_cpu_ms
                    );
                } else if cfg!(debug_assertions) {
                    // 开发包里顺的时候也报一条，方便对照 60/120 的手感
                    log::info!(
                        "motion: {fps:.0} fps  avg {:.1} ms  max {:.1} ms  (cpu {:.1} ms)",
                        self.scroll_sum_ms / n,
                        self.scroll_worst_ms,
                        self.last_cpu_ms
                    );
                }
                self.scroll_frames = 0;
                self.scroll_sum_ms = 0.0;
                self.scroll_worst_ms = 0.0;
            }
            return;
        }
        self.scroll_t0.get_or_insert_with(std::time::Instant::now);
        let ms = dt * 1000.0;
        if ms > 0.0 && ms < 200.0 {
            self.scroll_frames += 1;
            self.scroll_sum_ms += ms;
            self.scroll_worst_ms = self.scroll_worst_ms.max(ms);
        }
    }

    /// 把整个曲库以后会画的文字排成预热队列。
    ///
    /// 关键是**用真正的 FontId**：瓷砖标题是 proportional、艺人行与专辑页是 metro_light、
    /// 播放条标题是 metro_semi。用错字体族等于没预热（中文会重新光栅化一遍）。
    fn build_warm_queue(&self) -> std::collections::VecDeque<(String, egui::FontId)> {
        use std::collections::VecDeque;
        let k = self.k;
        let mut q: VecDeque<(String, egui::FontId)> = VecDeque::new();

        let tile_big = m::regular(17.0 * k);
        let tile_small = m::regular(12.5 * k);
        let tile_artist = m::light(11.5 * k);

        // 首页瓷砖：一进来就要画，必须预热（这里是之前"卡"的主要来源）
        for a in &self.lib.albums {
            q.push_back((a.title.clone(), tile_big.clone()));
            q.push_back((a.title.clone(), tile_small.clone()));
            q.push_back((a.artist.clone(), tile_artist.clone()));
        }
        q
    }

    /// 把系统返回手势的进度接进页面位移（预测性返回，1:1 跟手）。
    ///
    /// 系统在手势过程中持续给 0..1 的进度；这里直接写 `nav` 的值，
    /// 松手后要么由 `go_back` 交给弹簧走完，要么 `drag_end` 弹回起点。
    fn sync_predictive_back(&mut self, ctx: &egui::Context) {
        use crate::back::{self, ACTIVE, CANCELLED, INVOKED};
        match back::state() {
            ACTIVE => {
                let p = back::progress();
                // 覆盖层（设置页 / 确认面板）：只压暗，不做页面位移
                if self.login_open || self.confirm_logout {
                    self.home_back = p;
                    return;
                }
                if self.back_drag.is_none() {
                    let from = match self.page {
                        Page::Home => 0.0,
                        Page::Album => 1.0,
                        Page::Playing => 2.0,
                    };
                    let to = match self.page {
                        Page::Home => 0.0,
                        Page::Album => 0.0,
                        Page::Playing => 1.0,
                    };
                    self.back_drag = Some((from, to));
                    if from != to {
                        self.nav.drag_begin();
                    }
                }
                if let Some((from, to)) = self.back_drag {
                    if from != to {
                        self.nav.drag_to(from + (to - from) * p);
                    } else {
                        self.home_back = p;
                    }
                }
            }
            INVOKED => {
                back::clear();
                self.back_drag = None;
                self.home_back = 0.0;
                self.go_back(ctx);
            }
            CANCELLED => {
                back::clear();
                if let Some((from, _)) = self.back_drag.take() {
                    self.nav.drag_end(from, 0.0);
                }
                self.home_back = 0.0;
            }
            _ => {}
        }
    }

    fn go_back(&mut self, ctx: &egui::Context) {
        // 只有桌面端用 ctx 关窗口；Android 走 finish_activity
        #[cfg(target_os = "android")]
        let _ = ctx;
        // 覆盖层优先：确认面板 > 设置页 > 页面栈
        if self.confirm_logout {
            self.confirm_logout = false;
            return;
        }
        if self.login_open {
            if self.connected {
                // 已经连上曲库：返回 = 关掉设置页回首页
                self.login_open = false;
            } else {
                // 还没连上：没有别的页面可去。之前这里会关掉登录页 →
                // 落到"正在加载曲库…"的空库页面，既没内容也回不来（就是"卡住"的来源）。
                // 现在直接退出，和"首页再按返回"一致。
                #[cfg(target_os = "android")]
                crate::safe_area::finish_activity();
                #[cfg(not(target_os = "android"))]
                ctx.send_viewport_cmd(egui::ViewportCommand::Close);
            }
            return;
        }
        match self.page {
            Page::Playing => self.goto(Page::Album),
            Page::Album => self.goto(Page::Home),
            Page::Home => {
                #[cfg(target_os = "android")]
                crate::safe_area::finish_activity();
                #[cfg(not(target_os = "android"))]
                ctx.send_viewport_cmd(egui::ViewportCommand::Close);
            }
        }
    }

    fn goto(&mut self, page: Page) {
        let name = match page {
            Page::Home => "home",
            Page::Album => "album",
            Page::Playing => "playing",
        };
        if self.page != page {
            crate::eventlog::log_state(
                crate::eventlog::Level::Info,
                crate::eventlog::EventKind::PageSwitch,
                &format!("to={name} album={}", self.selected),
                Some(name),
            );
        }
        self.page = page;
        // 页面转场：300ms 指数缓出，没有弹簧过冲（Metro/Zune 都不回弹）。
        // 三页按 nav 平移，方向由目标页决定。
        self.nav.to(match page {
            Page::Home => 0.0,
            Page::Album => 1.0,
            Page::Playing => 2.0,
        });
        // 注意：返回首页**不**重置进场时间轴。之前每次返回首页都 tl.reset()，
        // 导致瓷砖墙重新播放 1.6 秒的 turnstile 翻转动画——翻页本身不卡，
        // 但"每次返回都要重新翻转一遍"被感知成卡顿。进场动画只在冷启动播一次。
    }

    // -----------------------------------------------------------------
    // 布局
    // -----------------------------------------------------------------

    fn home_layout(&self, screen: Rect) -> HomeLayout {
        let k = self.k;
        let w = screen.width();
        // M3：手机上两列卡片（一列太满、三列太碎），宽屏多一列
        let cols: usize = if w > 620.0 * k { 3 } else { 2 };
        let mx = 16.0 * k;
        let g = 12.0 * k;
        let unit = (w - mx * 2.0 - g * (cols - 1) as f32) / cols as f32;
        let text_h = 66.0 * k;
        let card_h = unit + text_h;
        let top = self.safe_top_pt + 126.0 * k;

        let n = self.lib.albums.len();
        let mut col_y = vec![0.0f32; cols];
        let mut tiles = Vec::with_capacity(n);
        for i in 0..n {
            let mut best = (0usize, f32::MAX);
            for c in 0..cols {
                let y = col_y[c];
                if y < best.1 - 0.5 {
                    best = (c, y);
                }
            }
            let (c, y) = best;
            let x = mx + c as f32 * (unit + g);
            let yy = top + y - self.scroll;
            tiles.push((
                i,
                Rect::from_min_size(pos2(x, yy), vec2(unit, card_h)),
            ));
            col_y[c] = y + card_h + g;
        }
        let content_h = col_y.iter().cloned().fold(0.0f32, f32::max);
        let bar_h = self.bar_reserve();
        let bar = Rect::from_min_size(pos2(0.0, screen.max.y - bar_h), vec2(w, bar_h));
        HomeLayout {
            tiles,
            top,
            content_h,
            view_h: (bar.min.y - top).max(1.0),
            bar,
        }
    }
    fn album_layout(&self, screen: Rect) -> AlbumLayout {
        let k = self.k;
        let mx = MARGIN * k + 2.0 * k;
        let top = self.safe_top_pt;
        // 横屏不铺满：内容列限宽居中，否则曲目时长会飘到两米之外
        let col_w = (screen.width() - mx * 2.0).min(820.0 * k).max(300.0);
        let cx = screen.center().x - col_w * 0.5;

        let album = &self.lib.albums[self.selected];
        let discs = album.discs();
        let multi = discs.len() > 1;
        let di = self.disc_view.min(discs.len() - 1);
        let disc = discs[di];

        // 头图折叠：列表往上滑，封面缩小、播放键和列表一起上移，把空间让给曲目表。
        // 塌陷量取"手势位移"，所以行的屏幕位置永远是 top0 + i*row - s（1:1 跟手），
        // 只是可视区顶端在往上长——滑到后半程才真正开始滚列表。
        let side_max = (col_w * 0.50)
            .clamp(140.0, 300.0 * k)
            .min(screen.height() * 0.34);
        let side_min = (108.0 * k).min(side_max * 0.72);
        let span = (side_max - side_min).max(1.0);
        let s_now = self.list_scroll.max(0.0);
        let c = s_now.clamp(0.0, span);
        let cover_side = side_max - c;

        // 非正方形封面按原始比例放进方框（左上对齐），信息列跟着封面右缘走，
        // 这样竖版封面不会在右边留一大块空、横版也不会把信息推远。
        let cover_box = Rect::from_min_size(pos2(cx, top + 64.0 * k), vec2(cover_side, cover_side));
        let cover = m::fit_within_tl(cover_box, self.art_aspect(self.selected));
        let info_x = cover.max.x + 22.0 * k;
        let play_btn = Rect::from_min_size(
            pos2(cx, cover.max.y + 22.0 * k),
            vec2(col_w, 46.0 * k),
        );
        // 多碟专辑在"播放全部"下面多一条碟标签，列表相应下移
        let tab_h = 28.0 * k;
        let tab_gap = 10.0 * k;
        let mut tabs = Vec::new();
        if multi {
            let n = discs.len() as f32;
            let tw = ((col_w - tab_gap * (n - 1.0)) / n).min(118.0 * k);
            let ty = play_btn.max.y + 32.0 * k;
            for (i, (d, _, _)) in discs.iter().enumerate() {
                tabs.push((
                    *d,
                    Rect::from_min_size(
                        pos2(cx + i as f32 * (tw + tab_gap), ty),
                        vec2(tw, tab_h),
                    ),
                ));
            }
        }
        let list_top = play_btn.max.y + 62.0 * k + if multi { tab_h + 8.0 * k } else { 0.0 };
        let bar_h = self.bar_reserve();
        let clip_bottom = screen.max.y - bar_h - 6.0 * k;
        let list = Rect::from_min_max(pos2(cx, list_top), pos2(cx + col_w, clip_bottom));

        // 上限用"未塌陷时的列表顶端"推：内容偏移是 s，可视底边固定，
        // 推导下来 max = list_top0 + rows*row_h - clip_bottom，和塌陷量无关。
        // 多碟时只按**当前这碟**的曲目数算滚动上限。
        let rows = disc.2 as f32;
        let list_top0 = list_top + span;
        let max_scroll = (list_top0 + rows * 46.0 * k - clip_bottom).max(0.0);

        AlbumLayout {
            back: Rect::from_min_size(pos2(mx - 8.0 * k, top + 2.0 * k), vec2(40.0, 40.0) * k),
            cover,
            info: pos2(info_x, cover.min.y + 4.0 * k),
            info_w: cx + col_w - info_x,
            play_btn,
            list,
            list_offset: (s_now - c).max(0.0),
            max_scroll,
            collapse: (c / span).clamp(0.0, 1.0),
            row_h: 46.0 * k,
            tabs,
            disc,
        }
    }

    fn playing_layout(&self, screen: Rect) -> PlayLayout {
        let k = self.k;
        let mx = MARGIN * k + 2.0 * k;
        let top = self.safe_top_pt;
        // 横屏（宽 >= 高）走 Apple Music 式双列：左封面+控制、右整列歌词。
        // 竖屏（宽 < 高）走纵向布局：封面在上、歌词在封面下方整屏宽显示——
        // 之前竖屏也走了双栏，右侧歌词栏被压到只剩几个字，每行都被截断。
        let wide = screen.width() >= screen.height();
        let (cover, step, panel) = if wide {
            let content_w = screen.width() - mx * 2.0;
            // 左栏整体内移，不再贴着屏幕边；封面在栏内水平居中，两侧留出等宽留白
            let left_x = mx + 64.0 * k;
            let left_w = ((content_w - 64.0 * k) * 0.46).clamp(260.0 * k, 400.0 * k);
            let top_y = top + 68.0 * k;
            let avail_h = (screen.max.y - self.bar_bottom() - 24.0 * k) - top_y;
            let side = left_w.min(290.0 * k).min(avail_h * 0.50);
            let cover_box = Rect::from_min_size(
                pos2(left_x + (left_w - side) * 0.5, top_y),
                vec2(side, side),
            );
            // 横版封面在方框里按比例居中（高度变小 → 下方歌词自然上移）
            let cover = m::fit_within(cover_box, self.art_aspect(self.playing_album.unwrap_or(self.selected)));
            // 右列：与左栏之间留出更宽的间距；顶部一行 `LYRICS` 小标
            let lx = left_x + left_w + 80.0 * k;
            let ly_top = top_y;
            // 底部多留一点：手势白条会盖住最后一行歌词
            let ly_bottom = screen.max.y - self.bar_bottom() - 40.0 * k;
            let panel = Rect::from_min_max(
                pos2(lx, ly_top + 34.0 * k),
                pos2(screen.max.x - mx, ly_bottom),
            );
            let step = (100.0 * k).min(left_w * 0.26);
            (cover, step, Some(panel))
        } else {
            // 竖屏：封面居中缩小，给下方歌词留出空间
            let side = (screen.width() - mx * 2.0)
                .min(screen.height() * 0.30)
                .min(320.0);
            let cover_box = Rect::from_min_size(
                pos2(screen.center().x - side * 0.5, top + 56.0 * k),
                vec2(side, side),
            );
            let cover = m::fit_within(cover_box, self.art_aspect(self.playing_album.unwrap_or(self.selected)));
            // 歌词面板：从控制按钮下方开始，到屏幕底部（整屏宽，几行都放得下）
            let ly_top = cover.max.y + (104.0 + 92.0 + 26.0 + 20.0) * k;
            // 底部多留一点：手势白条 + 最后一行歌词别贴死屏幕边
            let ly_bottom = screen.max.y - self.bar_bottom() - 28.0 * k;
            let panel = Rect::from_min_max(
                pos2(mx, ly_top),
                pos2(screen.max.x - mx, ly_bottom),
            );
            (cover, 92.0 * k, Some(panel))
        };
        // 进度线和封面同宽：横屏铺满全屏会跟上面的文字完全脱节
        let bar_y = cover.max.y + 104.0 * k;
        let bar = Rect::from_min_max(
            pos2(cover.min.x, bar_y),
            pos2(cover.max.x, bar_y + 2.0),
        );
        let cy = bar_y + 92.0 * k;
        let rr = vec2(52.0, 52.0) * k;
        let ccx = if wide { cover.center().x } else { screen.center().x };
        PlayLayout {
            back: Rect::from_min_size(pos2(mx - 8.0 * k, top + 2.0 * k), vec2(40.0, 40.0) * k),
            cover,
            bar,
            prev: Rect::from_center_size(pos2(ccx - step, cy), rr),
            play: Rect::from_center_size(pos2(ccx, cy), rr),
            next: Rect::from_center_size(pos2(ccx + step, cy), rr),
            lyrics_panel: panel,
            wide,
        }
    }

    fn home_input(&mut self, ui: &mut Ui, screen: Rect, lay: &HomeLayout, dx: f32) {
        let ctx = ui.ctx().clone();
        let view = Rect::from_min_max(pos2(0.0, lay.top), pos2(screen.max.x, lay.bar.min.y));

        // 整块区域一个 gesture：拖动滚墙，点击找砖（手动 hit-test，
        // 避免"按在砖上就拖不动"这类分层问题）
        let resp = ui.interact(
            view,
            ui.scope_id().with("home_scroll"),
            Sense::click_and_drag(),
        );

        let ready = self.tl.t > 0.5;
        let max = (lay.content_h - lay.view_h).max(0.0);
        if resp.drag_started() && ready {
            self.drag_acc = 0.0;
            self.scrolling = true;
            self.scroll_vel = 0.0;
        }
        if resp.dragged() && ready {
            let d = resp.drag_delta().y;
            self.drag_acc += d.abs();
            // 出界时给 45% 的阻尼，手指立刻能感觉到"到边了"，而不是硬顶住
            let over = if self.scroll < 0.0 || self.scroll > max {
                0.45
            } else {
                1.0
            };
            self.scroll = (self.scroll - d * over).clamp(-140.0, max + 140.0);
            // 甩动速度：取手指速度的 EMA（px/s）
            let inst = -d / ctx.input(|i| i.stable_dt).clamp(0.004, 0.05);
            self.scroll_vel = self.scroll_vel * 0.72 + inst * 0.28;
        }
        if resp.drag_stopped() {
            self.scrolling = false;
            self.scroll_vel = self.scroll_vel.clamp(-5200.0, 5200.0);
            self.drag_acc = 0.0;
        }
        // 桌面端滚轮（直接跟手，不做惯性）
        if resp.hovered() && ready {
            let wheel = ctx.input(|i| i.smooth_scroll_delta.y);
            if wheel.abs() > 0.1 {
                self.scroll = (self.scroll - wheel).clamp(0.0, max);
                self.scroll_vel = 0.0;
            }
        }

        // 右上角"已登录"标识：点击查看用户名，并弹出退出登录确认面板（提供登出入口）
        let dots = self.status_badge(screen).translate(vec2(dx, 0.0));
        let dresp = ui.interact(dots, ui.scope_id().with("home_dots"), Sense::click());
        if dresp.clicked() {
            if let Some(_r) = &self.remote {
                self.status = format!("已登录：{}", self.settings.user);
                self.confirm_logout = true;
                crate::eventlog::log(
                    crate::eventlog::Level::Info,
                    crate::eventlog::EventKind::Click,
                    "status_clicked logged_in",
                );
            } else {
                // 未登录：点击提示
                self.status = "未登录".into();
            }
        }

        // 按压反馈
        self.pressed_tile = None;
        if ctx.input(|i| i.pointer.any_down()) {
            if let Some(pos) = ctx.input(|i| i.pointer.interact_pos()) {
                if self.drag_acc < 6.0 {
                    self.pressed_tile = lay
                        .tiles
                        .iter()
                        .find(|(_, r)| r.translate(vec2(dx, 0.0)).contains(pos))
                        .map(|(i, _)| *i);
                }
            }
        }

        // 进场动画期间不收点击：转场时的残留手指/焦点点击不该打开专辑
        if resp.clicked() && self.tl.t > 0.5 {
            if let Some(pos) = resp.interact_pointer_pos() {
                if let Some((idx, _)) = lay
                    .tiles
                    .iter()
                    .find(|(_, r)| r.translate(vec2(dx, 0.0)).contains(pos))
                {
                    self.open_album(&ctx, *idx, false);
                }
            }
        }

    }

    /// 右上角状态胶囊的位置（也是设置入口）
    fn status_badge(&self, screen: Rect) -> Rect {
        let k = self.k;
        let mx = MARGIN * k + 2.0 * k;
        Rect::from_center_size(
            pos2(screen.max.x - mx - 8.0 * k, self.safe_top_pt + 12.0 * k),
            vec2(40.0, 40.0) * k,
        )
    }
    fn draw_tile(&self, p: &egui::Painter, idx: usize, r: Rect, appear: f32) {
        if idx >= self.lib.albums.len() || idx >= self.art.len() {
            return;
        }
        let k = self.k;
        let album = &self.lib.albums[idx];
        let roles = self.roles_for(idx);
        let a = appear.clamp(0.0, 1.0);
        // M3 进场：0.94 → 1 的缩放 + 淡入（不再是 Metro 的翻砖）
        let scale = 0.94 + 0.06 * a;
        let card = Rect::from_center_size(
            r.center() + vec2(0.0, (1.0 - scale) * 10.0 * k),
            r.size() * scale,
        );
        let radius = 24.0 * k;
        let cover = Rect::from_min_size(card.min, vec2(card.width(), card.width()));

        m3::card(p, card, radius, roles.surface_low, None);

        let has_art = self.art_state.get(idx).copied().unwrap_or(0) != 0;
        if has_art {
            if let Some(art) = self.art.get(idx).and_then(|x| x.as_ref()) {
                let fade = self.art_fade.get(idx).copied().unwrap_or(1.0).clamp(0.0, 1.0);
                let fit = m::fit_within(cover, art.aspect);
                m3::image_round(
                    p,
                    art.sharp.id(),
                    m::uv_full(),
                    fit,
                    radius,
                    Color32::WHITE.gamma_multiply(fade * a),
                    roles.surface_low,
                );
            }
        } else {
            let base = if album.tint != [0, 0, 0] {
                Color32::from_rgb(album.tint[0], album.tint[1], album.tint[2])
            } else {
                m::hue_rgb(album.hue, 0.42, 0.34)
            };
            m3::round_rect(p, cover, radius, base.gamma_multiply(0.45 + 0.55 * a));
        }

        // 按下：M3 状态层（不是整块反白）
        let press = press_fx(
            p.ctx(),
            egui::Id::unique(("tile_press", idx)),
            self.pressed_tile == Some(idx),
        );
        m3::state_layer(p, card, radius, roles.on_surface, 0.10 * press);

        let ta = smoothstep(0.55, 1.0, a);
        if ta <= 0.01 {
            return;
        }
        // 文字在封面下方（不再是压在封面上）
        let tx = card.min.x + 12.0 * k;
        let tw = (card.width() - 24.0 * k).max(24.0);
        let ty = cover.max.y + 10.0 * k;
        let title_col = roles.on_surface.gamma_multiply(ta);
        let sub_col = roles.on_variant.gamma_multiply(ta);
        m::text_wrap(
            p,
            pos2(tx, ty),
            &album.title,
            m3::title(15.0 * k),
            title_col,
            tw,
            2,
        );
        if !album.artist.trim().is_empty() {
            m::ellipsize(
                p,
                pos2(tx, ty + 38.0 * k),
                Align2::LEFT_TOP,
                &album.artist,
                m3::body(12.5 * k),
                sub_col,
                tw,
            );
        }
    }

    fn draw_home(&self, p: &egui::Painter, screen: Rect, lay: &HomeLayout, dx: f32) {
        let k = self.k;
        let off = vec2(dx, 0.0);
        let roles = self.roles();
        // 背景横向多铺 16%：弹簧过冲时页面不会把黑底露出来
        p.rect_filled(
            screen.translate(off).expand2(vec2(screen.width() * 0.16, 0.0)),
            0.0,
            roles.surface,
        );

        let clip = p.with_clip_rect(Rect::from_min_max(
            pos2(screen.min.x, lay.top),
            pos2(screen.max.x, lay.bar.min.y),
        ));
        let entering = self.tl.t < 1.0;
        for (order, (idx, r)) in lay.tiles.iter().enumerate() {
            let r = r.translate(off);
            if !screen.intersects(r.expand(60.0)) {
                continue;
            }
            let appear = if entering {
                self.tl.item(order.min(12), dur::STAGGER, dur::TILE)
            } else {
                1.0
            };
            self.draw_tile(&clip, *idx, r, appear);
        }

        // 顶栏：小标题 + 状态胶囊 + Display 大标题
        let mx = 20.0 * k;
        let top = self.safe_top_pt;
        p.rect_filled(
            Rect::from_min_max(
                pos2(screen.min.x, screen.min.y),
                pos2(screen.max.x, lay.top - 1.0),
            )
            .translate(off),
            0.0,
            roles.surface,
        );
        m::text(
            p,
            pos2(mx, top + 8.0 * k) + off,
            Align2::LEFT_TOP,
            "Lumen",
            m3::label(12.0 * k),
            roles.on_variant,
        );

        // 右上角状态胶囊（设置入口，矩形沿用 status_badge）
        let badge = self.status_badge(screen).translate(off);
        let logged_in = self.connected;
        let chip_r = Rect::from_min_size(
            pos2(badge.max.x - 76.0 * k, badge.min.y + 4.0 * k),
            vec2(76.0 * k, 30.0 * k),
        );
        if logged_in {
            m3::round_rect(p, chip_r, 15.0 * k, roles.primary_container);
            m::text(
                p,
                chip_r.center(),
                Align2::CENTER_CENTER,
                "已登录",
                m3::label(12.0 * k),
                roles.on_primary_container,
            );
        } else {
            m3::round_stroke(p, chip_r, 15.0 * k, roles.outline, 1.0);
            m::text(
                p,
                chip_r.center(),
                Align2::CENTER_CENTER,
                "未登录",
                m3::label(12.0 * k),
                roles.on_variant,
            );
        }

        let count = self.lib.albums.len();
        m::text(
            p,
            pos2(mx, top + 40.0 * k) + off,
            Align2::LEFT_TOP,
            "专辑",
            m3::display(36.0 * k),
            roles.on_surface,
        );
        m::text(
            p,
            pos2(mx, top + 88.0 * k) + off,
            Align2::LEFT_TOP,
            format!("{count} 张专辑"),
            m3::body(13.0 * k),
            roles.on_variant,
        );
    }
    fn album_input(&mut self, ui: &mut Ui, screen: Rect, al: &AlbumLayout, dx: f32) {
        let off = vec2(dx, 0.0);
        let back = al.back.translate(off);
        let rb = ui.interact(back, ui.scope_id().with("al_back"), Sense::click());
        self.press[0] = press_fx(ui.ctx(), egui::Id::unique(("pr", "al_back")), rb.is_pointer_button_down_on());
        if rb.clicked() {
            self.goto(Page::Home);
        }
        let pb = ui.interact(
            al.play_btn.translate(off),
            ui.scope_id().with("al_play"),
            Sense::click(),
        );
        if pb.clicked() {
            self.open_album(ui.ctx(), self.selected, true);
        }

        // 多碟：碟标签（点一下切碟）
        for (i, (d, rect)) in al.tabs.iter().enumerate() {
            let r = rect.translate(off);
            let hit = ui.interact(r, ui.scope_id().with(("disc_tab", i)), Sense::click());
            if hit.clicked() && i != self.disc_view {
                self.switch_disc(ui.ctx(), i, if i > self.disc_view { 1.0 } else { -1.0 });
            }
            let _ = d;
        }

        // 曲目列表：竖向拖动滚动 / 横向拖动切碟 / 点击选曲
        let list = al.list.translate(off);
        let resp = ui.interact(list, ui.scope_id().with("al_list"), Sense::click_and_drag());
        let rows = al.disc.2;
        let max = al.max_scroll;
        let multi = al.tabs.len() > 1;
        if resp.drag_started() {
            self.list_dragging = true;
            self.list_vel = 0.0;
            let sx = resp
                .interact_pointer_pos()
                .map(|p| p.x)
                .unwrap_or(list.center().x);
            self.disc_drag = Some((sx, 0));
            self.disc_dx = 0.0;
        }
        if resp.dragged() {
            let dt = ui.ctx().input(|i| i.stable_dt).clamp(0.004, 0.05);
            let (sx, mode) = self.disc_drag.unwrap_or((list.center().x, 2));
            let pos = resp.interact_pointer_pos().unwrap_or(list.center());
            let dx_total = pos.x - sx;
            let dy_total = resp.drag_delta().y;
            // 方向判定：先动够 12pt，再看主方向。判成横向就完全不动列表，
            // 判成纵向就完全不管横向——避免"一边滚一边切碟"互相打架。
            let mode = if mode == 0 {
                if dx_total.abs().max(dy_total.abs()) < 12.0 {
                    0
                } else if multi && dx_total.abs() > dy_total.abs() * 1.15 {
                    self.disc_drag = Some((sx, 1));
                    1
                } else {
                    self.disc_drag = Some((sx, 2));
                    2
                }
            } else {
                mode
            };
            if mode == 1 {
                // 横向：跟手平移，最多拉到 60% 列宽
                self.disc_dx = dx_total.clamp(-list.width() * 0.6, list.width() * 0.6);
            } else if mode == 2 {
                let d = dy_total;
                // 橡皮筋：越靠近边界越"拉不动"（1:1 → 0.15），拉到 140pt 就到底。
                // 固定 0.45 的旧做法在两端手感不一致，现在上下完全对称。
                let dist = if self.list_scroll < 0.0 {
                    -self.list_scroll
                } else if self.list_scroll > max {
                    self.list_scroll - max
                } else {
                    0.0
                };
                let over = if dist > 0.0 {
                    (0.5 * (1.0 - (dist / 140.0).min(1.0) * 0.7)).max(0.15)
                } else {
                    1.0
                };
                self.list_scroll = (self.list_scroll - d * over).clamp(-140.0, max + 140.0);

                // 速度估：45ms 时间常数的一阶低通。之前是固定 0.72/0.28，
                // 快滑时估不准（甩出去的距离时远时近），慢滑又太迟钝。
                let inst = -d / dt;
                let alpha = 1.0 - (-dt / 0.045).exp();
                self.list_vel += (inst - self.list_vel) * alpha;
            }
        }
        if resp.drag_stopped() {
            self.list_dragging = false;
            let mode = self.disc_drag.map(|(_, m)| m).unwrap_or(2);
            if mode == 1 {
                // 横向松手：过 1/4 列宽就切碟，否则弹回
                let w = list.width().max(1.0);
                if self.disc_dx.abs() > w * 0.25 {
                    let dir = if self.disc_dx < 0.0 { 1.0 } else { -1.0 };
                    let next = self.disc_view as i32 + if dir > 0.0 { 1 } else { -1 };
                    if next >= 0 && (next as usize) < al.tabs.len() {
                        self.switch_disc(ui.ctx(), next as usize, dir);
                    }
                }
                self.disc_dx = 0.0;
            } else {
                // 上限 5200 太高，一甩就飞过整张专辑；收紧到 4200 更可控
                self.list_vel = self.list_vel.clamp(-4200.0, 4200.0);
            }
            self.disc_drag = None;
        }

        // 头图折叠过半时标题从两行收成一行：带滞回（0.65 进 / 0.55 出），
        // 否则手指刚好停在阈值附近时标题会来回抽搐。
        self.title_one_line = if self.title_one_line {
            al.collapse > 0.55
        } else {
            al.collapse > 0.65
        };
        if resp.clicked() {
            if let Some(pos) = resp.interact_pointer_pos() {
                let rel = pos.y - list.min.y + al.list_offset;
                let i = (rel / al.row_h).floor();
                if i >= 0.0 && (i as usize) < rows {
                    // 行下标是"本碟内"的，播放用整张专辑的绝对下标
                    let ti = al.disc.1 + i as usize;
                    self.tap_at = Some(std::time::Instant::now());
                    self.play_track_at(ti);
                }
            }
        }
        if resp.hovered() {
            let wheel = ui.ctx().input(|x| x.smooth_scroll_delta.y);
            if wheel.abs() > 0.1 {
                self.list_scroll = (self.list_scroll - wheel).clamp(0.0, max);
                self.list_vel = 0.0;
            }
        }

        let _ = screen;
    }

    fn draw_album(&self, p: &egui::Painter, screen: Rect, al: &AlbumLayout, dx: f32) {
        let k = self.k;
        let off = vec2(dx, 0.0);
        let roles = self.roles();
        // 背景横向多铺 16%：弹簧过冲时页面不会把黑底露出来
        p.rect_filled(
            screen.translate(off).expand2(vec2(screen.width() * 0.16, 0.0)),
            0.0,
            roles.surface,
        );
        let album = &self.lib.albums[self.selected];

        // 顶部：圆形图标按钮 + 页名
        let pb = self.press[0];
        m3::icon_button(
            p,
            al.back.translate(off),
            &roles,
            Glyph::Back,
            roles.on_surface,
            None,
            pb,
        );
        m::text(
            p,
            pos2(al.back.max.x + 12.0 * k + dx, al.back.center().y),
            Align2::LEFT_CENTER,
            "专辑",
            m3::label(14.0 * k),
            roles.on_variant,
        );

        // 封面：M3 大圆角卡片
        let cover = al.cover.translate(off);
        let cover_r = 24.0 * k;
        m3::round_rect(p, cover, cover_r, roles.surface_low);
        if self.art_state.get(self.selected).copied().unwrap_or(0) != 0 {
            if let Some(a) = self.art.get(self.selected).and_then(|x| x.as_ref()) {
                m3::image_round(
                    p,
                    a.sharp.id(),
                    m::uv_full(),
                    cover,
                    cover_r,
                    Color32::WHITE,
                    roles.surface_low,
                );
            }
        } else {
            let base = if album.tint != [0, 0, 0] {
                Color32::from_rgb(album.tint[0], album.tint[1], album.tint[2])
            } else {
                m::hue_rgb(album.hue, 0.42, 0.34)
            };
            m3::round_rect(p, cover, cover_r, base);
        }

        // 右侧信息：标题默认最多两行；头图缩到一半以下时只留一行（和缩小后的封面配平）
        let title_f = m3::headline(25.0 * k);
        let title_rows = if self.title_one_line { 1 } else { 2 };
        let th = m::text_wrap(
            p,
            al.info + off,
            &album.title,
            title_f,
            roles.on_surface,
            al.info_w,
            title_rows,
        );
        let mut y = al.info.y + th + 6.0 * k;
        m::ellipsize(
            p,
            pos2(al.info.x + dx, y),
            Align2::LEFT_TOP,
            &album.artist,
            m3::body(14.0 * k),
            roles.on_variant,
            al.info_w,
        );
        y += 22.0 * k;
        let mins = (album.total_secs / 60.0).round() as u32;
        let meta = if mins > 0 {
            format!("{} · {} 首 · {} 分钟", album.year, album.song_count, mins)
        } else {
            format!("{} · {} 首", album.year, album.song_count)
        };
        m::ellipsize(
            p,
            pos2(al.info.x + dx, y),
            Align2::LEFT_TOP,
            &meta,
            m3::body(12.5 * k),
            roles.on_variant,
            al.info_w,
        );

        // 播放全部：M3 filled 药丸按钮（最"重"的元素）
        let btn = al.play_btn.translate(off);
        let pr = self.press[2];
        m3::round_rect(p, btn, btn.height() * 0.5, roles.primary);
        m3::state_layer(p, btn, btn.height() * 0.5, roles.on_primary, 0.12 * pr);
        m::glyph(
            p,
            Rect::from_center_size(
                pos2(btn.center().x - 46.0 * k, btn.center().y),
                vec2(16.0, 16.0) * k,
            ),
            Glyph::Play,
            roles.on_primary,
            2.0,
        );
        m::text(
            p,
            pos2(btn.center().x - 20.0 * k, btn.center().y),
            Align2::LEFT_CENTER,
            "播放全部",
            m3::label(15.0 * k),
            roles.on_primary,
        );

        // 多碟：M3 分段按钮（选中 = secondary container）
        for (i, (d, rect)) in al.tabs.iter().enumerate() {
            let r = rect.translate(off);
            let active = i == self.disc_view;
            m3::chip(
                p,
                Rect::from_min_max(pos2(r.min.x, r.min.y), pos2(r.max.x, r.max.y - 2.0 * k)),
                &roles,
                &format!("DISC {d}"),
                active,
                k,
                0.0,
            );
        }

        // 列表小标：单碟 TRACKS · n；多碟 DISC n · 本碟曲目数
        let label = if al.tabs.len() > 1 {
            format!("DISC {} · {} 首", al.disc.0, al.disc.2)
        } else {
            format!("TRACKS · {}", album.tracks.len())
        };
        m::text(
            p,
            pos2(al.list.min.x + dx, al.list.min.y - 12.0 * k),
            Align2::LEFT_TOP,
            &label,
            m3::label(12.0 * k),
            roles.on_variant,
        );

        // 曲目行（只画当前这碟）
        let list = al.list.translate(off);
        let clip = p.with_clip_rect(list);
        let cur = self.current_track_index();
        let row_h = al.row_h;
        // 横向手势跟手平移；松手后新碟从来的方向滑入
        let slide = if self.disc_drag.map(|(_, m)| m) == Some(1) {
            self.disc_dx
        } else {
            self.disc_anim.value()
        };
        let disc = al.disc;
        // 新列表进场：220ms 内按行错峰淡入（每行差 2 行的时间），
        // 让"曲目到货 → 列表出现"不是一个硬切。
        let rf = self.rows_fx.value().clamp(0.0, 1.0);
        let n_rows = disc.2.max(1) as f32;
        for i in 0..disc.2 {
            let abs = disc.1 + i;
            let Some(tr) = album.tracks.get(abs) else {
                continue;
            };
            let ra = ((rf * (n_rows + 2.0) - i as f32) / 2.0).clamp(0.0, 1.0);
            if ra <= 0.01 {
                continue;
            }
            let y = list.min.y + i as f32 * row_h - al.list_offset;
            if y > list.max.y || y + row_h < list.min.y {
                continue;
            }
            // 视口边缘渐隐：行滚到上下边界时淡出，而不是被裁掉半行——
            // 上下两端用不同的淡出高度（底部要盖住迷你条的压迫感），但都是同一曲线。
            let cy = y + row_h * 0.5;
            let edge = smoothstep(0.0, 14.0 * k, cy - list.min.y)
                .min(smoothstep(0.0, 20.0 * k, list.max.y - cy));
            let ra = (ra * edge).clamp(0.0, 1.0);
            if ra <= 0.01 {
                continue;
            }
            // 只有「正在播的专辑 == 当前展示的专辑」才高亮当前行；
            // 切到别的专辑查看时，这张专辑保持未播放、无高亮。
            let active = abs == cur && self.playing && self.playing_album == Some(self.selected);
            if active {
                m3::round_rect(
                    &clip,
                    Rect::from_min_max(
                        pos2(list.min.x - 8.0 * k + slide, y + 2.0 * k),
                        pos2(list.max.x + slide, y + row_h - 2.0 * k),
                    ),
                    16.0 * k,
                    roles.primary_container,
                );
            }
            let num_col = if active {
                roles.on_primary_container
            } else {
                roles.on_variant
            }
            .gamma_multiply(ra);
            m::text(
                &clip,
                pos2(list.min.x + 2.0 * k + slide, y + row_h * 0.5 - 8.0 * k),
                Align2::LEFT_TOP,
                format!("{:02}", i + 1),
                m::light(11.0 * k),
                num_col,
            );
            let tf = m3::title(15.0 * k);
            let tcol = if active {
                roles.on_primary_container
            } else {
                roles.on_surface
            }
            .gamma_multiply(ra);
            let avail = (list.width() - 74.0 * k).max(40.0);
            // 歌名：**所有行统一字号**（不随长度缩放，短名不会被放大、长名不会被压小）。
            // 放不下的靠"停稳后跑马灯"把整名滚出来；滚动/甩动时先省略，避免字在飞。
            // 正在播的那一行用全局相位，和播放页大标题、迷你条同步。
            let ty = y + (row_h - tf.size * 1.25) * 0.5;
            let over = m::text_w(&clip, &tr.title, tf.clone()) > avail + 0.5;
            // 只有"完全在视口里"的行才滚：边缘半截行反正已经淡出，滚起来反而分心
            let visible = y >= list.min.y && y + row_h <= list.max.y;
            let idle = !self.list_dragging && self.list_vel.abs() < 20.0;
            if over && idle && visible {
                let t = if active {
                    self.marquee_t
                } else {
                    self.marquee_t + i as f32 * 0.7
                };
                m::marquee(
                    &clip,
                    Rect::from_min_size(pos2(list.min.x + 30.0 * k + slide, y), vec2(avail, row_h)),
                    &tr.title,
                    tf,
                    tcol,
                    t,
                    18.0,
                );
            } else {
                m::ellipsize(
                    &clip,
                    pos2(list.min.x + 30.0 * k + slide, ty),
                    Align2::LEFT_TOP,
                    &tr.title,
                    tf,
                    tcol,
                    avail,
                );
            }
            m::text(
                &clip,
                pos2(list.max.x + slide, y + row_h * 0.5 - 8.0 * k),
                Align2::RIGHT_TOP,
                fmt_time(tr.secs as f32),
                m3::body(12.0 * k),
                (if active {
                    roles.on_primary_container
                } else {
                    roles.on_variant
                })
                .gamma_multiply(ra),
            );

        }

        if album.tracks.is_empty() {
            let txt = if album.id.is_empty() {
                "这张专辑没有曲目"
            } else {
                "正在取曲目…"
            };
            m::text(
                &clip,
                pos2(list.min.x, list.min.y + 8.0 * k),
                Align2::LEFT_TOP,
                txt,
                m::light(14.0 * k),
                m::text_faint(),
            );
        }

    }

    fn current_song_id(&self) -> Option<String> {
        let (ti, _, _) = self.current_track_info();
        self.lib
            .albums
            .get(self.selected)
            .and_then(|a| a.tracks.get(ti))
            .map(|t| t.id.clone())
    }

    fn current_lyrics_ref(&self) -> Option<&crate::model::Lyrics> {
        let id = self.current_song_id()?;
        match self.lyric_cache.get(&id) {
            Some(LyricState::Ready(l)) if !l.is_empty() => Some(l),
            _ => None,
        }
    }

    fn has_lyrics(&self) -> bool {
        self.current_lyrics_ref().is_some()
    }

    /// 歌词滚动目标与淡入：同步歌词跟当前行，纯文本歌词按播放比例滚。
    fn lyric_tick(&mut self, pl: &PlayLayout, dt: f32) {
        self.lyric_scroll.step(dt);
        self.lyric_fade.step(dt);

        let (ti, _, _) = self.current_track_info();
        let (start, len) = self.track_bounds(ti);
        let t_rel = (self.pos_secs - start).max(0.0);
        let song = self.current_song_id().unwrap_or_default();
        let panel = pl.lyrics_panel.unwrap_or(pl.cover);
        let panel_h = panel.height();
        let anchor = panel_h * 0.42;

        let (target, ready) = match self.current_lyrics_ref() {
            Some(l) => {
                let geo = lyric_geometry(&l.lines, panel.width(), self.k);
                let t = match l.index_at(t_rel) {
                    Some(ci) => geo.centers[ci.min(geo.centers.len() - 1)] - anchor,
                    // 同步歌词但还没唱到第一句：停在第一句，别乱滚
                    None if l.synced => geo.centers.first().copied().unwrap_or(0.0) - anchor,
                    None => {
                        // 未同步（或还没到第一行）：按播放进度比例滚
                        let frac = if len > 0.5 { (t_rel / len).clamp(0.0, 1.0) } else { 0.0 };
                        (geo.height - panel_h).max(0.0) * frac
                    }
                };
                (t, true)
            }
            None => (0.0, false),
        };

        // 有歌词就淡入显示（竖屏在封面下方、宽屏在右栏），没歌词就隐藏
        let want = ready;
        self.lyric_fade.to(if want { 1.0 } else { 0.0 });
        if !ready {
            self.lyric_song = song;
            return;
        }
        if self.lyric_song != song {
            // 换歌：直接落位，不要从上一首的位置滑过来
            self.lyric_song = song;
            self.lyric_scroll.set(target);
        } else {
            self.lyric_scroll.to(target);
        }
    }

    fn playing_input(&mut self, ui: &mut Ui, screen: Rect, pl: &PlayLayout, dx: f32) {
        let off = vec2(dx, 0.0);
        let _ = screen;
        self.lyric_tick(pl, ui.ctx().input(|i| i.stable_dt).clamp(0.001, 0.1));

        let rb = ui.interact(
            pl.back.translate(off),
            ui.scope_id().with("pl_back"),
            Sense::click(),
        );
        self.press[0] = press_fx(ui.ctx(), egui::Id::unique(("pr", "pl_back")), rb.is_pointer_button_down_on());
        if rb.clicked() {
            self.goto(Page::Album);
        }

        // 进度条：细到只有 2px，但热区给 30pt
        let hit = pl.bar.translate(off).expand2(vec2(0.0, 18.0 * self.k));
        let progress = ui.interact(hit, ui.scope_id().with("pl_seek"), Sense::click_and_drag());
        let (start, len) = self.track_bounds(self.cur_track);
        let len = len.max(1.0);
        if progress.dragged() || progress.clicked() {
            if let Some(p) = progress.interact_pointer_pos() {
                let frac = ((p.x - pl.bar.min.x - dx) / pl.bar.width()).clamp(0.0, 1.0);
                self.seek_preview = Some(frac * len);
            }
        }
        if progress.drag_stopped() || (progress.clicked() && self.seek_preview.is_some()) {
            if let Some(secs) = self.seek_preview.take() {
                self.pos_secs = start + secs;
                // seek 也是异步的（音频线程处理 Cmd::Seek 有延迟），设置缓冲期
                // 避免 seek 后 pos 被旧 position 拉回、进度条短暂回跳。
                self.stream_switch_at = Some(std::time::Instant::now());
                if let Some(au) = &mut self.audio {
                    au.seek(secs);
                }
            }
        }

        let rp = ui.interact(
            pl.prev.translate(off),
            ui.scope_id().with("pl_prev"),
            Sense::click(),
        );
        self.press[1] = press_fx(ui.ctx(), egui::Id::unique(("pr", "pl_prev")), rp.is_pointer_button_down_on());
        if rp.clicked() {
            self.jump_track(-1);
        }
        let pp = ui.interact(
            pl.play.translate(off),
            ui.scope_id().with("pl_play"),
            Sense::click(),
        );
        self.press[2] = press_fx(ui.ctx(), egui::Id::unique(("pr", "pl_play")), pp.is_pointer_button_down_on());
        if pp.clicked() {
            self.toggle_play();
        }
        let np = ui.interact(
            pl.next.translate(off),
            ui.scope_id().with("pl_next"),
            Sense::click(),
        );
        self.press[3] = press_fx(ui.ctx(), egui::Id::unique(("pr", "pl_next")), np.is_pointer_button_down_on());
        if np.clicked() {
            self.jump_track(1);
        }

        let _ = screen;
    }

    fn draw_playing(&self, p: &egui::Painter, screen: Rect, pl: &PlayLayout, dx: f32) {
        let k = self.k;
        let off = vec2(dx, 0.0);
        let roles = self.roles();
        // 背景横向多铺 16%：弹簧过冲时页面不会把黑底露出来
        p.rect_filled(
            screen.translate(off).expand2(vec2(screen.width() * 0.16, 0.0)),
            0.0,
            roles.surface,
        );
        let album = &self.lib.albums[self.selected];
        let (ti, title, secs) = self.current_track_info();

        let pb = self.press[0];
        m3::icon_button(p, pl.back.translate(off), &roles, Glyph::Back, roles.on_surface, None, pb);
        m::text(
            p,
            pos2(pl.back.max.x + 12.0 * k + dx, pl.back.center().y),
            Align2::LEFT_CENTER,
            "正在播放",
            m3::label(14.0 * k),
            roles.on_variant,
        );
        m::text(
            p,
            pos2(screen.max.x - (MARGIN * k + 4.0 * k) + dx, pl.back.center().y),
            Align2::RIGHT_CENTER,
            format!("{} / {}", ti + 1, album.tracks.len().max(1)),
            m3::label(12.0 * k),
            roles.on_variant,
        );

        // 封面：竖屏（上方）和宽屏（左列）都是常驻显示，歌词移到封面下方/右栏
        let cover = pl.cover.translate(off);
        let (start_secs, _) = self.track_bounds(ti);
        let t_rel = (self.pos_secs - start_secs).max(0.0);

        let cover_r = 28.0 * k;
        m3::round_rect(p, cover, cover_r, roles.surface_low);
        if self.art_state.get(self.selected).copied().unwrap_or(0) != 0 {
            if let Some(art) = self.art.get(self.selected).and_then(|x| x.as_ref()) {
                m3::image_round(
                    p,
                    art.sharp.id(),
                    m::uv_full(),
                    cover,
                    cover_r,
                    Color32::WHITE,
                    roles.surface_low,
                );
            }
        } else {
            let base = if album.tint != [0, 0, 0] {
                Color32::from_rgb(album.tint[0], album.tint[1], album.tint[2])
            } else {
                m::hue_rgb(album.hue, 0.42, 0.34)
            };
            m3::round_rect(p, cover, cover_r, base);
        }

        // 标题 / 艺人：标题过长时在同一行里缓慢往返滚动，不换行、不压到下面的信息
        let f1 = m3::headline(27.0 * k);
        m::marquee(
            p,
            Rect::from_min_size(
                pos2(cover.min.x, cover.max.y + 20.0 * k),
                vec2(cover.width(), 34.0 * k),
            ),
            &title,
            f1,
            roles.on_surface,
            self.marquee_t,
            26.0,
        );
        let sub = if album.artist.is_empty() {
            album.title.clone()
        } else {
            format!("{} · {}", album.artist, album.title)
        };
        m::ellipsize(
            p,
            pos2(cover.min.x, cover.max.y + 58.0 * k),
            Align2::LEFT_TOP,
            &sub,
            m3::body(14.0 * k),
            roles.on_variant,
            cover.width(),
        );

        // 进度：一条 2px 的线，剩下的只有时间
        let (start, len) = self.track_bounds(ti);
        let len = len.max(1.0);
        let shown = match self.seek_preview {
            Some(v) => v,
            None => (self.pos_secs - start).clamp(0.0, len),
        };
        let bar = pl.bar.translate(off);
        m3::slider(
            p,
            Rect::from_center_size(bar.center(), vec2(bar.width(), 24.0 * k)),
            (shown / len).clamp(0.0, 1.0),
            &roles,
            k,
        );
        let remaining = len - shown;
        m::text(
            p,
            pos2(bar.min.x, bar.max.y + 10.0 * k),
            Align2::LEFT_TOP,
            fmt_time(shown),
            m3::body(12.0 * k),
            roles.on_variant,
        );
        m::text(
            p,
            pos2(bar.max.x, bar.max.y + 10.0 * k),
            Align2::RIGHT_TOP,
            &format!("-{}", fmt_time(remaining)),
            m3::body(12.0 * k),
            roles.on_variant,
        );

        // 三个细线图标：按下时略缩 + 极淡的圆底（Zune 的圆形控制），
        // 播放/暂停图标做 160ms 交叉淡化，而不是硬切。
        for (rect, glyph, pr) in [
            (pl.prev, Glyph::Prev, self.press[1]),
            (pl.next, Glyph::Next, self.press[3]),
        ] {
            let rect = rect.translate(off).shrink(10.0 * k);
            m3::icon_button(
                p,
                rect,
                &roles,
                glyph,
                roles.on_surface,
                Some(roles.surface_high),
                pr,
            );
        }

        let pf = self.play_fx.value().clamp(0.0, 1.0);
        let pr = self.press[2];
        let play = pl.play.translate(off).shrink((8.0 + 1.6 * pr) * k);
        p.circle_filled(play.center(), play.width() * 0.5, roles.primary);
        m3::state_layer(
            p,
            Rect::from_center_size(play.center(), play.size()),
            play.width() * 0.5,
            roles.on_primary,
            0.10 * pr,
        );
        let icon = Rect::from_center_size(play.center(), vec2(play.width() * 0.42, play.width() * 0.42));
        if pf < 0.999 {
            m::glyph(p, icon, Glyph::Play, roles.on_primary.gamma_multiply(1.0 - pf), 2.4);
        }
        if pf > 0.001 {
            m::glyph(p, icon, Glyph::Pause, roles.on_primary.gamma_multiply(pf), 2.4);
        }

        // UP NEXT（仅宽屏：竖屏下方空间留给歌词）
        if pl.wide && ti + 1 < album.tracks.len() {
            let nt = &album.tracks[ti + 1];
            m::text(
                p,
                pos2(cover.min.x, pl.play.max.y + 34.0 * k),
                Align2::LEFT_TOP,
                "接下来",
                m3::label(12.0 * k),
                roles.on_variant,
            );
            m::ellipsize(
                p,
                pos2(cover.min.x, pl.play.max.y + 60.0 * k),
                Align2::LEFT_TOP,
                &format!("{} · {}", nt.title, fmt_time(nt.secs as f32)),
                m::light(13.0 * k),
                m::text_dim(),
                cover.width(),
            );
        }
        let _ = secs;

        // 歌词面板：横屏在右列、竖屏在封面下方（同一套绘制，位置由 layout 决定）
        if let Some(rp) = pl.lyrics_panel {
            // 整块随页面平移：转场时歌词要跟着走，否则会留在原地变成"残留"
            let rp = rp.translate(off);
            m::text(
                p,
                pos2(rp.min.x, rp.min.y - 28.0 * k),
                Align2::LEFT_TOP,
                "歌词",
                m3::label(12.0 * k),
                roles.on_variant,
            );
            let fade = self.lyric_fade.value.clamp(0.0, 1.0);
            match self.current_lyrics_ref() {
                Some(l) => {
                    self.draw_lyrics(p, rp, l, t_rel, fade);
                }
                None => {
                    let loading = matches!(
                        self.lyric_cache.get(&self.current_song_id().unwrap_or_default()),
                        Some(LyricState::Loading)
                    );
                    let txt = if loading { "正在取歌词…" } else { "这首歌没有歌词" };
                    m::text(
                        p,
                        pos2(rp.center().x, rp.center().y),
                        Align2::CENTER_CENTER,
                        txt,
                        m::light(13.0 * k),
                        m::text_faint(),
                    );
                }
            }
        }
    }

    /// 曲库还没到位时的页面：连接中 / 连接失败 / 连上了但一张专辑都没有。
    ///
    /// 三种状态都必须**有出口**（重新连接 / 返回登录）。之前这里只有一句
    /// "正在加载曲库…"：连不上（端口写错、服务器没开）就永远停在这，
    /// 右上角还因为 `remote.is_some()` 显示"已登录"，用户既看不到原因也改不了地址。
    fn library_wait_page(&mut self, ui: &mut Ui, screen: Rect) {
        let k = self.k;
        let p = ui.painter().clone();
        p.rect_filled(screen, 0.0, m::bg());
        let top = self.safe_top_pt;
        m::caps(
            &p,
            pos2(MARGIN * k, top + 14.0 * k),
            Align2::LEFT_TOP,
            "LUMEN",
            10.0 * k,
            m::text_faint(),
            2.6 * k,
        );

        // 右上角状态：只有真的拿到曲库才叫"已登录"
        let (label, color) = if !self.conn_error.is_empty() {
            ("连接失败", m::ACCENT)
        } else if self.connecting {
            ("连接中", m::text_faint())
        } else if self.connected {
            ("已登录", m::ACCENT)
        } else {
            ("未登录", m::text_faint())
        };
        let dots = self.status_badge(screen);
        m::caps(
            &p,
            pos2(dots.center().x, dots.center().y - 5.0 * k),
            Align2::CENTER_CENTER,
            label,
            9.0 * k,
            color,
            1.6 * k,
        );
        // 点状态标识 = 回登录页改地址（卡住时的出口之一）
        if ui
            .interact(dots, ui.scope_id().with("wait_badge"), Sense::click())
            .clicked()
        {
            crate::eventlog::log(
                crate::eventlog::Level::Info,
                crate::eventlog::EventKind::Click,
                "btn=wait_badge",
            );
            // 保留 self.status：回到表单后错误原因还看得见（改完地址直接重连）
            self.login_open = true;
        }

        let (cx, w) = self.setup_column(screen);
        let cy = screen.center().y - 150.0 * k;

        if !self.conn_error.is_empty() {
            // ── 连接失败 ──────────────────────────────────────────────
            m::text(&p, pos2(cx, cy), Align2::LEFT_TOP, "连接失败", m::light(30.0 * k), m::fg());
            m::caps(
                &p,
                pos2(cx, cy + 44.0 * k),
                Align2::LEFT_TOP,
                "CONNECTION FAILED",
                9.0 * k,
                m::text_faint(),
                2.2 * k,
            );
            // 正在连的服务器（用户填的地址，端口写错一眼就能看出来）
            m::ellipsize(
                &p,
                pos2(cx, cy + 76.0 * k),
                Align2::LEFT_TOP,
                &self.settings.base,
                m::regular(15.0 * k),
                m::fg(),
                w,
            );
            // 原因（可折行，最多 3 行）
            m::text_wrap(
                &p,
                pos2(cx, cy + 106.0 * k),
                &self.conn_error,
                m::regular(12.0 * k),
                m::text_dim(),
                w,
                3,
            );

            // 主按钮：重新连接
            let btn = Rect::from_min_size(pos2(cx, cy + 176.0 * k), vec2(w, 46.0 * k));
            let b = ui.interact(btn, ui.scope_id().with("wait_retry"), Sense::click());
            if b.is_pointer_button_down_on() {
                p.rect_filled(btn, 0.0, m::accent_a(0.72));
            } else {
                m::rect(&p, btn, m::ACCENT);
            }
            m::text(&p, btn.center(), Align2::CENTER_CENTER, "重新连接", m::semi(14.0 * k), m::fg());
            if b.clicked() {
                crate::eventlog::log(
                    crate::eventlog::Level::Info,
                    crate::eventlog::EventKind::Click,
                    "btn=retry_connect",
                );
                self.connect_server();
            }

            // 次按钮：返回登录（改地址 / 账号）
            let back = Rect::from_min_size(pos2(cx, btn.max.y + 14.0 * k), vec2(w, 40.0 * k));
            let rb = ui.interact(back, ui.scope_id().with("wait_login"), Sense::click());
            if rb.hovered() {
                p.rect_filled(back, 0.0, Color32::from_white_alpha(12));
            }
            m::text(&p, back.center(), Align2::CENTER_CENTER, "返回登录", m::semi(14.0 * k), m::text_dim());
            if rb.clicked() {
                crate::eventlog::log(
                    crate::eventlog::Level::Info,
                    crate::eventlog::EventKind::Click,
                    "btn=back_to_login",
                );
                // 保留 self.status：回到表单后错误原因还看得见
                self.login_open = true;
            }
        } else if self.connected {
            // ── 连上了，但库里一张专辑都没有 ──────────────────────────
            m::text(&p, pos2(cx, cy), Align2::LEFT_TOP, "曲库是空的", m::light(30.0 * k), m::fg());
            m::text_wrap(
                &p,
                pos2(cx, cy + 60.0 * k),
                "服务器连上了，但 Navidrome 里还没有专辑。添加音乐（或等扫描完）后重新拉取。",
                m::regular(12.0 * k),
                m::text_dim(),
                w,
                3,
            );
            let btn = Rect::from_min_size(pos2(cx, cy + 150.0 * k), vec2(w, 46.0 * k));
            let b = ui.interact(btn, ui.scope_id().with("wait_reload"), Sense::click());
            m::rect(&p, btn, m::ACCENT);
            m::text(&p, btn.center(), Align2::CENTER_CENTER, "重新加载", m::semi(14.0 * k), m::fg());
            if b.clicked() {
                crate::eventlog::log(
                    crate::eventlog::Level::Info,
                    crate::eventlog::EventKind::Click,
                    "btn=reload_library",
                );
                self.connect_server();
            }
        } else {
            // ── 连接中 ────────────────────────────────────────────────
            m::caps(&p, pos2(cx, cy), Align2::LEFT_TOP, "CONNECTING", 9.0 * k, m::text_faint(), 2.2 * k);
            m::text(&p, pos2(cx, cy + 24.0 * k), Align2::LEFT_TOP, "正在加载曲库…", m::light(28.0 * k), m::fg());
            m::progress_dots(
                &p,
                Rect::from_min_size(pos2(cx, cy + 78.0 * k), vec2(48.0, 8.0)),
                self.tl.t * 0.7,
            );
            m::text_wrap(
                &p,
                pos2(cx, cy + 108.0 * k),
                "第一次连接会拉取全部专辑，通常几秒就好。连不上可以点右上角回到登录页。",
                m::regular(12.0 * k),
                m::text_faint(),
                w,
                3,
            );
        }

        ui.ctx().request_repaint_after(std::time::Duration::from_millis(200));
    }

    fn draw_setup(&self, p: &egui::Painter, screen: Rect) {
        let k = self.k;
        let roles = self.roles();
        let mx = MARGIN * k + 4.0 * k;
        let top = self.safe_top_pt;
        let (cx, w) = self.setup_column(screen);
        // 注意：这里**不再画全屏背景**——ui_root 已画过一次，再画会盖掉 setup_input
        // 刚画的 TextEdit 文字（输入"不显示，重输才显示"的根源）。

        // 返回 + SETTINGS
        m::glyph(
            p,
            Rect::from_min_size(pos2(mx - 8.0 * k, top + 2.0 * k), vec2(40.0, 40.0) * k)
                .shrink(10.0 * k),
            Glyph::Back,
            m::text_dim(),
            2.0,
        );
        m::caps(
            p,
            pos2(mx + 32.0 * k, top + 14.0 * k),
            Align2::LEFT_TOP,
            "SETTINGS",
            10.0 * k,
            m::text_faint(),
            2.6 * k,
        );
        m::text(
            p,
            pos2(cx - 2.0 * k, top + 34.0 * k),
            Align2::LEFT_TOP,
            "连接曲库",
            m3::display(34.0 * k),
            roles.on_surface,
        );

        // ── 分区 1：连接 ──────────────────────────────
        // 已连接：收起连接表单，原位显示连接信息 + 「退出登录」；
        // 未连接：显示连接表单。两者按钮都在 top+342，CACHE / CREDITS 位置不变。
        // 注意用 self.connected（曲库到货）而不是 self.remote.is_some()（线程起来了）。
        let connected = self.connected;
        let mut y = top + 98.0 * k;
        m::text(p, pos2(cx, y), Align2::LEFT_TOP, "连接", m3::label(12.0 * k), roles.on_variant);
        y += 26.0 * k;

        if connected {
            // 连接信息（替代表单，占同样的纵向区间——和表单一样 3 行）
            // 打码长度对齐真实密码长度（不再截断到 20 / 抬高到 4，避免误导）
            let masked = "•".repeat(self.settings.password.chars().count());
            let info = [
                ("服务器", "SERVER", self.settings.base.as_str()),
                ("用户名", "USERNAME", self.settings.user.as_str()),
                ("密码", "PASSWORD", masked.as_str()),
            ];
            for (label, en, val) in info {
                m::text(p, pos2(cx, y), Align2::LEFT_TOP, label, m::regular(11.0 * k), m::text_faint());
                m::caps(p, pos2(cx + w, y + 1.0 * k), Align2::RIGHT_TOP, en, 9.0 * k, m::text_faint(), 1.8 * k);
                m::ellipsize(p, pos2(cx, y + 17.0 * k), Align2::LEFT_TOP, val, m::regular(15.0 * k), m::fg(), w);
                m::hairline(p, pos2(cx, y + 43.0 * k), pos2(cx + w, y + 43.0 * k), Color32::from_white_alpha(46));
                y += 66.0 * k;
            }
        } else {
            // 表单装饰：只画标签 + 下划线（**不画值**——值由 TextEdit 画，避免双重绘制）。
            // 位置用 setup_input 记录的 field_rects，和输入框严格对齐（一套坐标）。
            let labels = [
                ("服务器地址", "SUBSONIC SERVER"),
                ("用户名", "USERNAME"),
                ("密码", "PASSWORD"),
            ];
            for (i, (label, en)) in labels.iter().enumerate() {
                let r = self.field_rects[i];
                if !r.is_positive() {
                    continue;
                }
                // M3 outlined 输入框：圆角容器 + 描边（聚焦时换成 primary、加粗）
                let focus = self.ime.field() == Some(i);
                let box_r = r.expand2(vec2(4.0 * k, 5.0 * k));
                // 注意：这里只画描边、不填底色——输入文字由 TextEdit 先画，
                // 后画的填充会把它盖住（M3 outlined 本来就是透明底）
                m3::round_stroke(
                    p,
                    box_r,
                    12.0 * k,
                    if focus { roles.primary } else { roles.outline },
                    if focus { 2.0 } else { 1.0 },
                );
                m::text(
                    p,
                    pos2(r.left(), r.top() - 24.0 * k),
                    Align2::LEFT_TOP,
                    *label,
                    m3::label(11.0 * k),
                    if focus { roles.primary } else { roles.on_variant },
                );
                m::text(
                    p,
                    pos2(r.right(), r.top() - 24.0 * k),
                    Align2::RIGHT_TOP,
                    *en,
                    m3::label(9.0 * k),
                    roles.on_variant.gamma_multiply(0.7),
                );
            }
        }

        // 主按钮：已连接「退出登录」（细边框）/ 未连接「连接并同步」（强调色）—— 位置固定
        let btn = Rect::from_min_size(pos2(cx, top + 342.0 * k), vec2(w, 46.0 * k));
        if connected {
            m3::round_stroke(p, btn, btn.height() * 0.5, roles.outline, 1.0);
            m::text(p, btn.center(), Align2::CENTER_CENTER, "退出登录", m3::label(14.0 * k), roles.on_surface);
        } else {
            m3::round_rect(p, btn, btn.height() * 0.5, roles.primary);
            m::text(p, btn.center(), Align2::CENTER_CENTER, "连接并同步", m3::label(14.0 * k), roles.on_primary);
        }

        // 还没填完整也能先进去看看（仅未连接）
        if !connected {
            m::text(
                p,
                pos2(cx, btn.max.y + 15.0 * k),
                Align2::LEFT_TOP,
                "先看看 →",
                m::regular(12.0 * k),
                m::text_dim(),
            );
        }

        // 状态（连接中 / 错误 / 已退出）——固定区间，不推开下方分区
        let mut sy = btn.max.y + 40.0 * k;
        if !self.setup_msg.is_empty() {
            m::ellipsize(p, pos2(cx, sy), Align2::LEFT_TOP, &self.setup_msg, m::regular(12.0 * k), m::accent_a(0.9), w);
            sy += 20.0 * k;
        }
        if !self.status.is_empty() {
            if self.status.starts_with("正在") {
                m::progress_dots(
                    p,
                    Rect::from_center_size(pos2(cx + 8.0 * k, sy + 6.0 * k), vec2(40.0, 8.0)),
                    self.tl.t * 0.7,
                );
                m::ellipsize(
                    p,
                    pos2(cx + 52.0 * k, sy),
                    Align2::LEFT_TOP,
                    &self.status,
                    m::regular(12.0 * k),
                    m::text_dim(),
                    w - 54.0 * k,
                );
            } else {
                m::ellipsize(
                    p,
                    pos2(cx, sy),
                    Align2::LEFT_TOP,
                    &self.status,
                    m::regular(12.0 * k),
                    m::text_dim(),
                    w,
                );
            }
        }

        // ── 分区 2：缓存（位置固定）──────────────────────
        let cy = top + 452.0 * k;
        m::hairline(p, pos2(cx, cy), pos2(cx + w, cy), m::rule());
        m::text(p, pos2(cx, cy + 14.0 * k), Align2::LEFT_TOP, "缓存", m3::label(12.0 * k), roles.on_variant);
        if let Some(r) = &self.remote {
            let u = r.cache_usage();
            m::text(
                p,
                pos2(cx, cy + 33.0 * k),
                Align2::LEFT_TOP,
                &format!("音频 {} MB · 封面 {} MB", u.audio >> 20, u.covers >> 20),
                m::regular(12.0 * k),
                m::text_dim(),
            );
        } else {
            m::text(p, pos2(cx, cy + 33.0 * k), Align2::LEFT_TOP, "未连接", m::regular(12.0 * k), m::text_faint());
        }

        // ── 分区 3：特别鸣谢 ──────────────────────────
        let ry = cy + 66.0 * k;
        m::hairline(p, pos2(cx, ry), pos2(cx + w, ry), m::rule());
        m::text(p, pos2(cx, ry + 14.0 * k), Align2::LEFT_TOP, "关于", m3::label(12.0 * k), roles.on_variant);
        m::text(
            p,
            pos2(cx + 66.0 * k, ry + 12.0 * k),
            Align2::LEFT_TOP,
            "特别鸣谢",
            m::regular(11.0 * k),
            m::text_faint(),
        );
        let credits: [(&str, &str); 6] = [
            ("界面", "eframe · egui 0.37"),
            ("音频", "rodio · symphonia → AAudio"),
            ("曲库", "Navidrome · OpenSubsonic"),
            ("字体", "MiSans（小米，免费商用授权）"),
            ("图片", "image crate（纯 Rust 解码）"),
            ("参考", "nori（流式播放架构）"),
        ];
        let mut ry2 = ry + 38.0 * k;
        for (who, what) in credits {
            m::text(p, pos2(cx, ry2), Align2::LEFT_TOP, who, m::regular(11.0 * k), m::text_faint());
            m::text(
                p,
                pos2(cx + 44.0 * k, ry2),
                Align2::LEFT_TOP,
                what,
                m::regular(11.0 * k),
                m::text_dim(),
            );
            ry2 += 19.0 * k;
        }

        // 光标用 egui 原生的（跟随真实光标位置、闪烁），不自绘。
    }

    fn setup_input(&mut self, ui: &mut Ui, screen: Rect) {
        let k = self.k;
        let mx = MARGIN * k + 4.0 * k;
        let top = self.safe_top_pt;
        let (cx, w) = self.setup_column(screen);
        let back = Rect::from_min_size(pos2(mx - 8.0 * k, top + 2.0 * k), vec2(40.0, 40.0) * k);
        let rb = ui.interact(back, ui.scope_id().with("su_back"), Sense::click());
        if rb.clicked() && self.connected {
            crate::eventlog::log(
                crate::eventlog::Level::Info,
                crate::eventlog::EventKind::Navigation,
                "from=setup to=home",
            );
            self.login_open = false;
        }

        let connected = self.connected;

        // 已连接时这一页没有输入框（表单收起了）：收尾所有还活着的 IME 会话
        if connected && self.ime.field().is_some() {
            let _ = self.ime.end();
        }

        if !connected {
            // ══════════════════════════════════════════════════════════════
            // 三个输入框：普通 Ui 流布局（ui.add_sized）。
            //
            // 关键：**绝不用 scope_builder(max_rect)** —— 那会创建子 Ui，
            // TextEdit 的 focus/键盘/事件路由会异常（V2 用普通 ui.add 正常）。
            // ══════════════════════════════════════════════════════════════
            let field_h = 22.0 * k;
            let row_gap = 66.0 * k - field_h;
            let start_y = top + 124.0 * k + 17.0 * k;

            // ── 软键盘：把 Android IME 的编辑会话翻译成 egui 事件 ──────────
            //
            // TextEdit 自己完整支持 ImeEvent（预编辑上屏、提交替换、组合态光标），
            // 缺的只是 Android 那边的事件源（winit 不转发 IME 事件）。
            // 必须在本帧创建 TextEdit **之前**注入，事件才会被它消费。
            match self.ime.poll() {
                crate::ime::Output::None => {}
                crate::ime::Output::Events(events) => {
                    ui.ctx().input_mut(|i| i.events.extend(events));
                    ui.ctx().request_repaint();
                }
                crate::ime::Output::Resync { text, caret } => {
                    if let Some(f) = self.ime.field() {
                        match f {
                            0 => self.edit_base = text,
                            1 => self.edit_user = text,
                            _ => self.edit_pass = text,
                        }
                        set_caret(ui.ctx(), input_id(f), caret);
                    }
                    ui.ctx().request_repaint();
                }
            }

            // 移动 Ui cursor 到第一个输入框位置（普通 Ui 流，不用绝对定位）
            let cur_y = ui.min_rect().bottom();
            if start_y > cur_y {
                ui.add_space(start_y - cur_y);
            }

            let mut any_focus = false;
            let mut begin: Option<(usize, &'static str, bool, String, usize)> = None;

            for i in 0..3 {
                let (hint, field, password) = match i {
                    0 => ("http://", "base", false),
                    1 => ("请输入用户名", "user", false),
                    _ => ("请输入密码", "pass", true),
                };
                let buf = match i {
                    0 => &mut self.edit_base,
                    1 => &mut self.edit_user,
                    _ => &mut self.edit_pass,
                };

                ui.style_mut().override_font_id = Some(egui::FontId::proportional(15.0 * k));
                ui.style_mut().visuals.override_text_color = Some(m::fg());
                ui.style_mut().visuals.extreme_bg_color = Color32::TRANSPARENT;
                ui.style_mut().visuals.faint_bg_color = Color32::TRANSPARENT;

                let edit = egui::TextEdit::singleline(buf)
                    .id(input_id(i))
                    .desired_width(w)
                    .frame(egui::Frame::NONE)
                    .password(password)
                    .hint_text(hint);
                // 用 horizontal + add_space(cx) 把输入框推到内容列 cx 处，
                // 否则 ui.add_sized 默认从 x=0（屏幕左缘）开始，窄屏手机上标签会被裁切。
                let resp = ui
                    .horizontal(|ui| {
                        ui.add_space(cx);
                        ui.add_sized(egui::vec2(w, field_h), edit)
                    })
                    .inner;
                // 记录实际 rect，draw_setup 画标签/下划线都用它（一套坐标，不偏移）
                self.field_rects[i] = resp.rect;

                // 交互日志：输入变化（脱敏，只记长度不记明文）
                if resp.changed() {
                    crate::eventlog::input_text(field, buf.chars().count());
                }
                if resp.has_focus() {
                    any_focus = true;
                    // 焦点换到这一框：把它的内容登记给 IME（编辑器类型 + 文本 + 光标）。
                    // 旧实现在这里按"长度"判断要不要同步，切到等长的另一框时就不会同步，
                    // IME 仍拿着上一框的文字 —— 那是"串字"的来源。
                    if self.ime.field() != Some(i) {
                        let caret = caret_of(ui.ctx(), resp.id)
                            .unwrap_or_else(|| buf.chars().count());
                        begin = Some((i, field, password, buf.clone(), caret));
                    }
                }

                if i < 2 {
                    ui.add_space(row_gap);
                }
            }

            if let Some((i, field, password, text, caret)) = begin {
                crate::eventlog::input_focus(field);
                self.ime.begin(i, &text, caret, password);
            } else if !any_focus && self.ime.field().is_some() {
                // 三个框都没有焦点了（点了按钮 / 返回）：结束编辑会话，
                // 组合中的文字按原样提交，免得留在输入框里。
                let ev = self.ime.end();
                if !ev.is_empty() {
                    ui.ctx().input_mut(|i| i.events.extend(ev));
                }
            }

            // 帧末：用户自己改过缓冲区（硬件键盘 / ADB / 程序化归一化）就推回 IME，
            // 这样输入法下一次提交的落点才是对的。
            if let Some(f) = self.ime.field() {
                let caret = caret_of(ui.ctx(), input_id(f)).unwrap_or(0);
                let text = match f {
                    0 => &self.edit_base,
                    1 => &self.edit_user,
                    _ => &self.edit_pass,
                };
                self.ime.sync_back(text, caret);
            }

            // 输入框有焦点时按 16ms 走：IME 状态是轮询来的，
            // 不主动要帧的话要等下一条 400ms 的"静止轮询"，打字会明显发滞。
            if self.ime.field().is_some() {
                ui.ctx()
                    .request_repaint_after(std::time::Duration::from_millis(16));
            }
        }

        // 主按钮（位置固定 top+342）：已连接「退出登录」/ 未连接「连接并同步」
        let btn = Rect::from_min_size(pos2(cx, top + 342.0 * k), vec2(w, 46.0 * k));
        let b = ui.interact(btn, ui.scope_id().with("su_connect"), Sense::click());
        let hover = press_fx(ui.ctx(), egui::Id::unique(("hv", "su_connect")), b.hovered());
        if hover > 0.01 {
            ui.painter()
                .rect_filled(btn, 0.0, Color32::from_white_alpha((18.0 * hover) as u8));
        }
        self.press[5] = press_fx(ui.ctx(), egui::Id::unique(("pr", "su_connect")), b.is_pointer_button_down_on());
        if self.press[5] > 0.01 {
            ui.painter()
                .rect_filled(btn, 0.0, Color32::from_black_alpha((40.0 * self.press[5]) as u8));
        }
        if b.clicked() {
            crate::eventlog::log_state(
                crate::eventlog::Level::Info,
                crate::eventlog::EventKind::Click,
                &format!("btn={}", if connected { "logout" } else { "connect" }),
                Some(if connected { "logout" } else { "connect" }),
            );
            if connected {
                self.logout();
            } else {
                self.connect_server();
            }
        }

        // 还没填完整也能先进去看看（仅未连接）
        if !connected {
            let skip = Rect::from_min_size(pos2(cx, btn.max.y + 6.0 * k), vec2(w, 38.0 * k));
            let sk = ui.interact(skip, ui.scope_id().with("su_skip"), Sense::click());
            if sk.hovered() {
                ui.painter().rect_filled(skip, 0.0, Color32::from_white_alpha(12));
            }
            if sk.clicked() {
                crate::eventlog::log(
                    crate::eventlog::Level::Info,
                    crate::eventlog::EventKind::Click,
                    "btn=skip_look",
                );
                self.login_open = false;
            }
        }
    }

    /// 退出登录：清空凭据、断开远端、清空曲库，回到登录界面。
    /// 正在播的本地缓存曲目不打断（音频线程独立），只是不再联网。
    fn logout(&mut self) {
        self.settings = crate::settings::Settings::default();
        let _ = self.settings.save();
        self.remote = None;
        self.connected = false;
        self.connecting = false;
        self.conn_error.clear();
        self.edit_base.clear();
        self.edit_user.clear();
        self.edit_pass.clear();
        // 结束软键盘会话：否则 IME 还记着上一轮的文本，下次登录时第一个字会拼到旧内容上
        let _ = self.ime.end();
        self.setup_msg = String::new();
        self.confirm_logout = false;
        // 清掉旧会话的曲库/封面/播放状态，回到干净的未登录态
        self.lib.albums.clear();
        self.art.clear();
        self.art_state.clear();
        self.ink.clear();
        self.ink_real.clear();
        self.art_fade.clear();
        self.decoded.clear();
        self.decoded_lru.clear();
        self.cover_queue.clear();
        self.urgent_sent.clear();
        self.wanted.clear();
        self.tracks_pending.clear();
        self.warm_built = false;
        self.warm_queue.clear();
        self.playing = false;
        self.has_audio = false;
        self.streaming = false;
        self.playing_album = None;
        self.cur_track = 0;
        self.pos_secs = 0.0;
        self.lyric_cache.clear();
        self.lyric_asked.clear();
        // 关键：重新打开登录界面，否则退出后没有任何路径能再登录
        self.login_open = true;
        self.status = "已退出登录".into();
        crate::eventlog::log_state(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::SetupLogout,
            "user_logged_out",
            Some("disconnected"),
        );
        log::info!("logout: remote disconnected, settings cleared");
    }

    /// 退出登录确认面板：点击"已登录"标识后弹出。遮罩 + 面板，处理退出/取消。
    ///
    /// 动效：220ms 缓出淡入 + 面板上移 10pt（Zune 的弹层感——只有位移和透明度，
    /// 不缩放、不弹跳）。收起时同样走一遍，所以 `confirm_fx` 没停就继续画。
    fn logout_confirm(&mut self, ui: &mut Ui, screen: Rect) {
        let k = self.k;
        let roles = self.roles();
        let p = ui.painter().clone();
        let e = self.confirm_fx.value().clamp(0.0, 1.0);
        let dy = (1.0 - e) * 10.0 * k;
        // 入场没走完不接受点击，避免"手还在划就误触退出"
        let live = e > 0.65;

        // 遮罩：点击空白处 = 取消
        let mask = ui.interact(screen, ui.scope_id().with("logout_mask"), Sense::click());
        p.rect_filled(screen, 0.0, Color32::from_black_alpha((170.0 * e) as u8));

        // 面板
        let pw = (320.0 * k).clamp(260.0, screen.width() - 48.0);
        let ph = 216.0 * k;
        let panel = Rect::from_center_size(screen.center(), vec2(pw, ph)).translate(vec2(0.0, dy));
        m3::round_rect(
            &p,
            panel,
            28.0 * k,
            roles.surface_high.gamma_multiply(e),
        );
        m3::round_stroke(&p, panel, 28.0 * k, roles.outline_variant, 1.0);

        m::text(
            &p,
            pos2(panel.min.x + 24.0 * k, panel.min.y + 22.0 * k),
            Align2::LEFT_TOP,
            "退出登录",
            m3::headline(22.0 * k),
            roles.on_surface.gamma_multiply(e),
        );
        m::text(
            &p,
            pos2(panel.min.x + 24.0 * k, panel.min.y + 58.0 * k),
            Align2::LEFT_TOP,
            &format!("当前登录：{}", self.settings.user),
            m3::body(13.0 * k),
            roles.on_variant.gamma_multiply(e),
        );

        // 退出登录（强调色）
        let yes = Rect::from_min_size(
            pos2(panel.min.x + 24.0 * k, panel.min.y + 96.0 * k),
            vec2(pw - 48.0 * k, 44.0 * k),
        );
        let by = ui.interact(yes, ui.scope_id().with("logout_yes"), Sense::click());
        let hy = press_fx(ui.ctx(), egui::Id::unique(("hv", "logout_yes")), by.hovered() && live);
        if hy > 0.01 {
            p.rect_filled(yes, 0.0, Color32::from_white_alpha((12.0 * hy) as u8));
        }
        m::rect(&p, yes, m::ACCENT.gamma_multiply(e));
        m::text(
            &p,
            yes.center(),
            Align2::CENTER_CENTER,
            "退出登录",
            m::semi(14.0 * k),
            m::fg().gamma_multiply(e),
        );

        // 取消（细边框）
        let no = Rect::from_min_size(
            pos2(panel.min.x + 24.0 * k, panel.min.y + 152.0 * k),
            vec2(pw - 48.0 * k, 40.0 * k),
        );
        let bn = ui.interact(no, ui.scope_id().with("logout_no"), Sense::click());
        p.rect_stroke(
            no,
            0.0,
            egui::Stroke::new(1.0, m::rule().gamma_multiply(e)),
            egui::epaint::StrokeKind::Inside,
        );
        m::text(
            &p,
            no.center(),
            Align2::CENTER_CENTER,
            "取消",
            m::semi(14.0 * k),
            m::text_faint().gamma_multiply(e),
        );

        if !live {
            return;
        }
        if by.clicked() {
            crate::eventlog::log(crate::eventlog::Level::Info, crate::eventlog::EventKind::Click, "logout_confirmed");
            self.confirm_logout = false;
            self.logout();
        } else if bn.clicked() || mask.clicked() {
            self.confirm_logout = false;
        }
    }

    /// 连接按钮：先把编辑框内容同步到 settings（用户输入的是 edit_* 字段），
    /// 校验后保存设置并起新的远端线程（旧线程随旧 Remote 一起释放）。
    fn connect_server(&mut self) {
        // 关键：用户输入落在 edit_* 编辑框，settings 才是真正生效的配置。
        // 之前漏了这步同步，is_complete 永远读空 settings → 永远连不上。
        self.settings.base = self.edit_base.trim().to_string();
        self.settings.user = self.edit_user.trim().to_string();
        self.settings.password = self.edit_pass.clone();
        if !self.settings.is_complete() {
            crate::eventlog::log(
                crate::eventlog::Level::Warn,
                crate::eventlog::EventKind::Validate,
                &format!("connect incomplete base={} user={}", self.settings.base, self.settings.user),
            );
            self.setup_msg = "服务器 / 用户名 / 密码 还没填完整".into();
            return;
        }
        crate::eventlog::log_state(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::Submit,
            &format!("base={} user={}", self.settings.base, self.settings.user),
            Some("connecting"),
        );
        let cfg = self.settings.clone().normalize();
        self.settings = crate::settings::Settings {
            base: cfg.base.clone(),
            user: cfg.user.clone(),
            password: cfg.password.clone(),
        };
        // 同步回编辑框（normalize 后补了 http://、去了斜杠，展示规整后的值）
        self.edit_base = cfg.base.clone();
        self.edit_user = cfg.user.clone();
        self.edit_pass = cfg.password.clone();
        let _ = self.settings.save();
        self.remote = Some(crate::remote::Remote::spawn(crate::subsonic::Config {
            base: cfg.base.clone(),
            user: cfg.user.clone(),
            password: cfg.password.clone(),
        }));
        // 连接状态由「曲库到货 / 收到错误」来翻转；点下按钮这一刻只进入"连接中"
        self.connected = false;
        self.connecting = true;
        self.conn_error.clear();
        self.setup_msg = String::new();
        self.status = "正在连接曲库…".into();
    }

    /// 设置页的内容列（宽屏限宽居中；返回箭头仍贴屏幕左边）
    fn setup_column(&self, screen: Rect) -> (f32, f32) {
        let k = self.k;
        let mx = MARGIN * k + 4.0 * k;
        let w = (screen.width() - mx * 2.0).min(560.0 * k).max(260.0);
        (screen.center().x - w * 0.5, w)
    }

    fn draw_bar(&mut self, p: &egui::Painter, screen: Rect, bar: Rect) {
        let k = self.k;
        let roles = self.roles();
        let pa = self
            .playing_album
            .unwrap_or(self.selected)
            .min(self.lib.albums.len().saturating_sub(1));
        let album = &self.lib.albums[pa];
        let (_, title, _) = self.current_track_info();
        let capsule = self.playing_album.is_some();
        // 换歌：旧名向左退场，新名从右侧弹入
        let sid = self.current_song_id().unwrap_or_default();
        if sid != self.bar_song {
            self.bar_song = sid;
            self.bar_prev_title = std::mem::replace(&mut self.bar_title, title.clone());
            self.bar_slide.set(1.0);
            self.bar_slide.to(0.0);
        }
        let dt = p.ctx().input(|i| i.stable_dt).clamp(0.0, 0.05);
        self.bar_slide.step(dt);

        // 按压（上一帧的输入结果，1 帧延迟无感）
        let pl = self.press[5];
        let pr = self.press[4];
        let (left, center, right) = bar_slots(bar, k, capsule, pl, pr);
        let radius = bar.height() * 0.5;

        // 半调网点：替代阴影，把底栏和滚过去的内容隔开（Yoin 的做法）
        let dots = Rect::from_min_max(
            pos2(screen.min.x, bar.min.y - 10.0 * k),
            pos2(screen.max.x, screen.max.y),
        );
        let step = 7.0 * k;
        let mut y = dots.min.y;
        while y < dots.max.y {
            let mut x = dots.min.x;
            while x < dots.max.x {
                p.circle_filled(
                    pos2(x, y),
                    1.25 * k,
                    roles.on_surface.gamma_multiply(0.10),
                );
                x += step;
            }
            y += step;
        }

        // 药丸本体
        m3::round_rect(p, bar, radius, roles.surface_high);
        m3::round_stroke(p, bar, radius, roles.outline_variant, 1.0);

        // 左：首页
        let home_on = self.page == Page::Home;
        let chip_w = left.width().min(104.0 * k);
        let chip = Rect::from_center_size(
            left.center(),
            vec2(chip_w, left.height() - 8.0 * k),
        );
        if home_on {
            m3::round_rect(p, chip, chip.height() * 0.5, roles.primary_container);
        }
        m3::state_layer(p, chip, chip.height() * 0.5, roles.on_surface, 0.08 * pl);
        let hcol = if home_on {
            roles.on_primary_container
        } else {
            roles.on_variant
        };
        if chip_w > 86.0 * k {
            m::glyph(
                p,
                Rect::from_center_size(
                    pos2(chip.center().x - 24.0 * k, chip.center().y),
                    vec2(18.0, 18.0) * k,
                ),
                Glyph::Home,
                hcol,
                2.0,
            );
            m::text(
                p,
                pos2(chip.center().x + 2.0 * k, chip.center().y),
                Align2::LEFT_CENTER,
                "首页",
                m3::label(13.0 * k),
                hcol,
            );
        } else {
            m::glyph(
                p,
                Rect::from_center_size(chip.center(), vec2(20.0, 20.0) * k),
                Glyph::Home,
                hcol,
                2.0,
            );
        }

        // 右：播放 / 暂停
        let pf = self.play_fx.value().clamp(0.0, 1.0);
        let icon = Rect::from_center_size(right.center(), vec2(26.0, 26.0) * k);
        if pr > 0.01 {
            p.circle_filled(
                right.center(),
                right.height() * 0.34,
                Color32::from_white_alpha((20.0 * pr) as u8),
            );
        }
        let play_col = roles.primary;
        if pf < 0.999 {
            m::glyph(p, icon, Glyph::Play, play_col.gamma_multiply(1.0 - pf), 2.2);
        }
        if pf > 0.001 {
            m::glyph(p, icon, Glyph::Pause, play_col.gamma_multiply(pf), 2.2);
        }

        // 中间：曲目胶囊（没在播就完全收起）
        if capsule && center.width() > 20.0 * k {
            m3::round_rect(p, center.shrink(2.0 * k), center.height() * 0.5, roles.surface_container);
            let side = (center.height() - 16.0 * k).max(18.0);
            let thumb = Rect::from_min_size(
                pos2(center.min.x + 8.0 * k, center.center().y - side * 0.5),
                vec2(side, side),
            );
            let base = if album.tint != [0, 0, 0] {
                Color32::from_rgb(album.tint[0], album.tint[1], album.tint[2])
            } else {
                m::hue_rgb(album.hue, 0.42, 0.34)
            };
            m3::round_rect(p, thumb, 10.0 * k, base);
            if self.art_state.get(pa).copied().unwrap_or(0) != 0 {
                if let Some(a) = self.art.get(pa).and_then(|x| x.as_ref()) {
                    let fade = self.art_fade.get(pa).copied().unwrap_or(1.0).clamp(0.0, 1.0);
                    let fit = m::fit_within(thumb, a.aspect);
                    m3::image_round(
                        p,
                        a.sharp.id(),
                        m::uv_full(),
                        fit,
                        10.0 * k,
                        Color32::WHITE.gamma_multiply(fade),
                        base,
                    );
                }
            }
            let tx = thumb.max.x + 10.0 * k;
            let tw = (center.max.x - 10.0 * k - tx).max(30.0);
            // 曲目切换：旧名向左退场、新名从右侧弹入
            let slide = self.bar_slide.value();
            let dx = tw * slide;
            let tcol = roles.on_surface;
            m::ellipsize(
                p,
                pos2(tx + dx, center.center().y - 9.0 * k),
                Align2::LEFT_TOP,
                &title,
                m3::label(13.0 * k),
                tcol,
                tw,
            );
            m::ellipsize(
                p,
                pos2(tx + dx, center.center().y + 6.0 * k),
                Align2::LEFT_TOP,
                &album.artist,
                m3::body(11.5 * k),
                roles.on_variant,
                tw,
            );
            if slide.abs() > 0.01 {
                m::ellipsize(
                    p,
                    pos2(tx + dx - tw, center.center().y - 9.0 * k),
                    Align2::LEFT_TOP,
                    &self.bar_prev_title,
                    m3::label(13.0 * k),
                    tcol.gamma_multiply(1.0 - slide.abs()),
                    tw,
                );
            }
        }
        let _ = screen;
    }
    fn bar_input(&mut self, ui: &mut Ui, screen: Rect) {
        let k = self.k;
        let bar = lay_bar(self, screen);
        let capsule = self.playing_album.is_some();
        // 命中区用基础宽度算：按压动画会让槽位变宽，用动画值做命中会在边界处点错按钮
        let (left, _center, right) = bar_slots(bar, k, capsule, 0.0, 0.0);
        let resp = ui.interact(bar, ui.scope_id().with("bar"), Sense::click());
        let pos = resp.interact_pointer_pos();
        let down = resp.is_pointer_button_down_on();

        self.press[5] = press_fx(
            ui.ctx(),
            egui::Id::unique(("pr", "bar_home")),
            down && pos.map(|q| left.expand(4.0 * k).contains(q)).unwrap_or(false),
        );
        self.press[4] = press_fx(
            ui.ctx(),
            egui::Id::unique(("pr", "bar_play")),
            down && pos.map(|q| right.expand(4.0 * k).contains(q)).unwrap_or(false),
        );

        if resp.clicked() {
            if let Some(q) = pos {
                if left.expand(4.0 * k).contains(q) {
                    if self.page != Page::Home {
                        self.goto(Page::Home);
                    } else {
                        self.scroll = 0.0;
                        self.scroll_vel = 0.0;
                    }
                } else if right.expand(4.0 * k).contains(q) {
                    self.toggle_play();
                } else if capsule {
                    if let Some(pa) = self.playing_album {
                        self.selected = pa;
                    }
                    self.goto(Page::Playing);
                }
            }
        }
    }
    fn toggle_play(&mut self) {
        // 还没真正起过音频（刚启动、或只选了一张专辑）：先起播，
        // 别只把图标翻成"播放中"却没有声音——这就是"点了播放没反应"。
        let loaded = self
            .current_song_id()
            .map(|s| !s.is_empty())
            .unwrap_or(false);
        if !self.playing && (!loaded || !self.has_audio) {
            let pa = self.playing_album.unwrap_or(self.selected);
            let has = self
                .lib
                .albums
                .get(pa)
                .map(|a| !a.tracks.is_empty())
                .unwrap_or(false);
            if has {
                self.play_in_album(pa, 0);
            } else {
                // 曲目还没拉回来：先请求，到了自动起播（首页刚启动也能点）
                self.pending_play = true;
                self.pending_play_album = Some(pa);
                if let (Some(r), Some(a)) = (&self.remote, self.lib.albums.get(pa)) {
                    if !a.id.is_empty() {
                        r.request_album(pa, a.id.clone());
                    }
                }
            }
            return;
        }
        self.playing = !self.playing;
        let sid = self.current_song_id().unwrap_or_default();
        let (kind, st) = if self.playing {
            (crate::eventlog::EventKind::PlayResume, "playing")
        } else {
            (crate::eventlog::EventKind::PlayPause, "paused")
        };
        crate::eventlog::log_state(
            crate::eventlog::Level::Info,
            kind,
            &format!("song_id={sid}"),
            Some(st),
        );
        if let Some(au) = &mut self.audio {
            if self.playing {
                au.resume();
                // 恢复播放：重新上报"正在播放"，避免暂停太久后 Navidrome 的
                // nowPlaying 窗口超时、统计面板又探测不到。
                if let Some(id) = self.current_song_id() {
                    if !id.is_empty() {
                        if let Some(r) = &self.remote {
                            r.scrobble(&id, false);
                        }
                    }
                }
            } else {
                au.pause();
            }
        }
    }

    /// 打开专辑前，把这页要画的几条文字**同步**排进图集（标题 / 艺人 / 年份行）。
    ///
    /// 必须同步：之前是 push 到 warm_queue 异步处理，但 warm_queue 在本帧更早的
    /// input 之前已经跑过，push 的字形要下一帧才光栅化——而 draw_album 在同帧就
    /// 渲染标题，于是第一帧现场光栅化、点专辑卡一下。现在点击瞬间（nav 动画开始前）
    /// 就把这几条字形光栅化掉，代价约 10~20ms，但 nav 第一帧不再掉帧。
    fn warm_album_page(&mut self, ctx: &egui::Context, idx: usize) {
        let k = self.k;
        let Some(a) = self.lib.albums.get(idx) else { return };
        let items = [
            (a.title.clone(), m::light(23.0 * k)),
            (a.artist.clone(), m::light(13.0 * k)),
            (
                format!("{} · {} 首 · 0 分钟", a.year, a.song_count),
                m::light(11.0 * k),
            ),
            (a.artist.clone(), m::light(11.0 * k)),
            ("ALBUM".to_string(), m::regular(10.0 * k)),
        ];
        ctx.fonts_mut(|f| {
            for (text, font) in items {
                let _ = f.layout_no_wrap(text, font, Color32::WHITE);
            }
        });
    }

    /// 切到第 `to` 碟：列表回顶 + 从拖动方向滑入（dir=+1 表示往后翻一碟）
    fn switch_disc(&mut self, ctx: &egui::Context, to: usize, dir: f32) {
        let n = self.lib.albums[self.selected].discs().len();
        if to >= n || to == self.disc_view {
            return;
        }
        self.disc_view = to;
        self.list_scroll = 0.0;
        self.list_vel = 0.0;
        self.list_dragging = false;
        self.disc_dx = 0.0;
        // 新一碟从"手指来的方向"滑进来（往左甩 → 新碟从右边进）
        self.disc_anim.set(dir * 80.0 * self.k);
        self.disc_anim.to(0.0);
        ctx.request_repaint();
        crate::eventlog::log(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::Navigation,
            &format!("disc={} album={}", to + 1, self.selected),
        );
    }
    fn open_album(&mut self, ctx: &egui::Context, idx: usize, play_all: bool) {
        if idx >= self.lib.albums.len() {
            return;
        }
        // 是否切到「不同」专辑：只重置展示状态（滚动），不动播放状态。
        // 正在播的专辑继续播；新专辑只是「选中/展示」，保持未播放，等手动点播放才开声。
        let switching = idx != self.selected;
        self.selected = idx;
        if switching {
            self.rows_fx.set(0.0);
            self.title_one_line = false;
            self.list_scroll = 0.0;
            self.list_vel = 0.0;
            self.list_dragging = false;
        }
        self.warm_album_page(ctx, idx);
        self.goto(Page::Album);
        // 多碟：默认展示"正在播的那一碟"（这张专辑正在播时），否则从第 1 碟开始
        let discs = self.lib.albums[idx].discs().len().max(1);
        self.disc_view = if self.playing && self.playing_album == Some(idx) {
            self.lib.albums[idx].disc_of(self.cur_track)
        } else {
            0
        }
        .min(discs - 1);
        self.disc_anim.set(0.0);
        self.disc_dx = 0.0;
        self.disc_drag = None;
        let empty = self.lib.albums[idx].tracks.is_empty();
        let id = self.lib.albums[idx].id.clone();
        if empty && !id.is_empty() {
            if let Some(r) = &self.remote {
                r.request_album(idx, id);
            }
        }
        if play_all {
            self.pending_play = true;
            if !self.lib.albums[idx].tracks.is_empty() {
                self.pending_play = false;
                self.play_track_at(0);
            }
        }
    }

    fn track_bounds(&self, i: usize) -> (f32, f32) {
        let Some(album) = self
            .lib
            .albums
            .get(self.playing_album.unwrap_or(self.selected))
        else {
            return (0.0, 0.0);
        };
        let start = album.starts.get(i).copied().unwrap_or(0.0);
        let len = album.tracks.get(i).map(|t| t.secs as f32).unwrap_or(0.0);
        (start, len)
    }

    fn current_track_index(&self) -> usize {
        // 用「正在播的专辑」反推当前曲目，而不是「展示的专辑」——切到别的专辑查看时，
        // pos_secs 属于 playing_album，用它去 selected 的曲目表里反推会错行。
        let Some(album) = self
            .lib
            .albums
            .get(self.playing_album.unwrap_or(self.selected))
        else {
            return 0;
        };
        if album.tracks.is_empty() {
            return 0;
        }
        // starts 没算好（曲目刚拉回、还没 recompute_timing）时，退回 cur_track 兜底，
        // 别用 start=0 逐首反推（会全部命中第一首、错行）。
        if album.starts.len() != album.tracks.len() {
            return self.cur_track.min(album.tracks.len() - 1);
        }
        let pos = self.pos_secs;
        for (i, t) in album.tracks.iter().enumerate() {
            if pos < album.starts[i] + t.secs as f32 {
                return i;
            }
        }
        album.tracks.len() - 1
    }

    fn current_track_info(&self) -> (usize, String, u32) {
        let Some(album) = self
            .lib
            .albums
            .get(self.playing_album.unwrap_or(self.selected))
        else {
            return (0, "—".into(), 0);
        };
        let i = self.current_track_index();
        match album.tracks.get(i) {
            Some(t) => (i, t.title.clone(), t.secs),
            // 曲目还没拉回来（或这张专辑没有曲目）：退到专辑名。
            // 以前这里返回 "—"，迷你条会出现"— / 艺人"这种像坏掉的组合。
            None => (0, album.title.clone(), album.total_secs as u32),
        }
    }

    fn advance_track(&mut self) {
        // 自然播完/自动切歌作用于「正在播的专辑」，而不是当前展示的专辑
        let pa = self.playing_album.unwrap_or(self.selected);
        let n = self
            .lib
            .albums
            .get(pa)
            .map(|a| a.tracks.len())
            .unwrap_or(0);
        if n == 0 {
            self.playing = false;
            return;
        }
        crate::eventlog::log_state(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::PlayEnd,
            &format!("album={pa} track={}", self.cur_track),
            Some("ended"),
        );
        // 切走当前歌：上报 scrobble（submission=true），让统计面板结算这一段收听。
        // 用 cur_track（明确状态）而不是 current_track_index()——后者在曲目边界
        // 已经指向下一首，会报错歌。
        if let Some(t) = self
            .lib
            .albums
            .get(pa)
            .and_then(|a| a.tracks.get(self.cur_track))
        {
            if !t.id.is_empty() {
                if let Some(r) = &self.remote {
                    r.scrobble(&t.id, true);
                }
            }
        }
        if self.cur_track + 1 < n {
            crate::eventlog::log(
                crate::eventlog::Level::Info,
                crate::eventlog::EventKind::PlayNext,
                &format!("album={pa} auto from={} to={}", self.cur_track, self.cur_track + 1),
            );
            self.play_in_album(pa, self.cur_track + 1);
        } else {
            crate::eventlog::log_state(
                crate::eventlog::Level::Info,
                crate::eventlog::EventKind::PlayStop,
                &format!("album={pa} playlist_ended"),
                Some("stopped"),
            );
            self.playing = false;
            // 整张播完：音频已 stop，迷你条的播放键要能"重新起播"而不是空 resume
            self.has_audio = false;
            self.pos_secs = self
                .lib
                .albums
                .get(pa)
                .map(|a| a.total_secs.max(1.0))
                .unwrap_or(1.0);
            if let Some(au) = &mut self.audio {
                au.stop();
            }
        }
    }

    /// 切到某首歌：没有歌词就去要一次（只请求一次，失败/没有都记下来）
    fn request_lyrics(&mut self, song_id: &str) {
        if song_id.is_empty() {
            return;
        }
        if self.lyric_cache.contains_key(song_id) || self.lyric_asked.contains(song_id) {
            return;
        }
        let Some(r) = &self.remote else { return };
        r.lyrics(song_id);
        self.lyric_asked.insert(song_id.to_string());
        self.lyric_cache
            .insert(song_id.to_string(), LyricState::Loading);
    }

    /// 播放「当前选中专辑」的第 ti 首（用户点曲目 / 播放全部）
    fn play_track_at(&mut self, ti: usize) {
        let album = self.selected;
        self.play_in_album(album, ti);
    }

    /// 播放指定专辑的第 ti 首（底层实现；自动切歌用 playing_album 调它，切专辑不打断播放）
    fn play_in_album(&mut self, album_idx: usize, ti: usize) {
        let Some(tr) = self
            .lib
            .albums
            .get(album_idx)
            .and_then(|a| a.tracks.get(ti))
        else {
            return;
        };
        let id = tr.id.clone();
        let title = tr.title.clone();
        crate::eventlog::log_state(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::TrackSelect,
            &format!("album={album_idx} track={ti} id={id} title={title}"),
            Some("selected"),
        );
        crate::eventlog::log_state(
            crate::eventlog::Level::Info,
            crate::eventlog::EventKind::PlayStart,
            &format!("album={album_idx} track={ti} id={id} title={title}"),
            Some("playing"),
        );
        self.has_audio = !id.is_empty();
        let start = self.lib.albums[album_idx].starts.get(ti).copied().unwrap_or(0.0);
        self.pos_secs = start;
        self.seek_preview = None;
        self.playing = true;
        self.cur_track = ti;
        self.playing_album = Some(album_idx);
        // 多碟：切到正在播的那一碟（只在"正在看这张专辑"时跟随，避免打断浏览别处的用户）
        if self.selected == album_idx {
            let dv = self.lib.albums[album_idx].disc_of(ti);
            if dv != self.disc_view {
                self.disc_view = dv;
                self.list_scroll = 0.0;
                self.list_vel = 0.0;
                self.disc_anim.set(0.0);
            }
        }
        // 切歌：记录时刻，audio 引擎切换完成前不重建位置（见 tick_audio）
        self.stream_switch_at = Some(std::time::Instant::now());
        // 换歌重置健康度采样，避免拿上一首的进度做比较
        self.pos_probe = None;

        if id.is_empty() {
            // 演示曲库：没有真实流，只跑时间轴
            self.streaming = false;
            return;
        }
        self.request_lyrics(&id);
        if let Some(r) = &self.remote {
            r.protect(&id);
            // 上报"正在播放"：这是 Navidrome getNowPlaying 的唯一数据来源，
            // 没有它统计面板（navidrome-stat）就探测不到 Lumen 在播什么。
            r.scrobble(&id, false);
        }
        let cached = self.remote.as_ref().and_then(|r| r.cached(&id));
        match cached {
            Some(path) => {
                // 缓存里可能是本机解不了的编码（ALAC / DSD）：带上转码流兜底，
                // 解不了就自动退回服务端转码，不会静音卡在 0:00。
                let fb = self.remote.as_ref().map(|r| r.stream_url_transcoded(&id));
                if let Some(au) = &mut self.audio {
                    au.play_file_with_fallback(path, fb);
                }
                self.streaming = false;
            }
            None => {
                if let Some(r) = &self.remote {
                    let url = r.stream_url(&id);
                    let fb = r.stream_url_transcoded(&id);
                    if let Some(au) = &mut self.audio {
                        au.play_url_fallback(&url, Some(fb), 0.0);
                    }
                }
                self.streaming = true;
            }
        }
        // 预取 album_idx 的后两首
        for k in 1..=2 {
            if let Some(next) = self.lib.albums[album_idx].tracks.get(ti + k) {
                let nid = next.id.clone();
                if !nid.is_empty() {
                    if let Some(r) = &self.remote {
                        r.prefetch(&nid);
                    }
                }
            }
        }
        log::info!("play: {id}");
    }

    fn jump_track(&mut self, delta: i32) {
        // 上一首/下一首作用于「正在播的专辑」，不是当前展示的
        let pa = self.playing_album.unwrap_or(self.selected);
        let n = self
            .lib
            .albums
            .get(pa)
            .map(|a| a.tracks.len())
            .unwrap_or(0);
        if n == 0 {
            return;
        }
        // 切走当前歌：上报 scrobble（submission=true），让统计面板能结算这一段收听。
        // 用 cur_track（明确状态），不靠 current_track_index 反推。
        if let Some(t) = self
            .lib
            .albums
            .get(pa)
            .and_then(|a| a.tracks.get(self.cur_track))
        {
            if !t.id.is_empty() {
                if let Some(r) = &self.remote {
                    r.scrobble(&t.id, true);
                }
            }
        }
        let next = (self.cur_track as i32 + delta).rem_euclid(n as i32) as usize;
        let kind = if delta >= 0 {
            crate::eventlog::EventKind::PlayNext
        } else {
            crate::eventlog::EventKind::PlayPrev
        };
        crate::eventlog::log(
            crate::eventlog::Level::Info,
            kind,
            &format!("album={pa} manual from={} to={next} delta={delta}", self.cur_track),
        );
        self.play_in_album(pa, next);
    }
    fn pump_remote(&mut self, _ctx: &egui::Context) {
        let Some(r) = &self.remote else { return };
        for msg in r.poll_n(6) {
            match msg {
                crate::remote::Msg::Albums(albums) => {
                    let n = albums.len();
                    log::info!("library: {n} albums");
                    // 曲库到达 = 真的连上了（不是"线程起来了"）
                    self.connected = true;
                    self.connecting = false;
                    self.conn_error.clear();
                    // 登录成功（曲库到达）→ 自动关闭登录界面
                    if self.login_open {
                        self.login_open = false;
                        self.status = format!("已登录：{}", self.settings.user);
                        crate::eventlog::log_state(
                            crate::eventlog::Level::Info,
                            crate::eventlog::EventKind::SetupConnect,
                            &format!("auto_close user={}", self.settings.user),
                            Some("logged_in"),
                        );
                    }
                    self.lib.albums = albums;
                   
                    self.warm_built = false;
                    self.warm_queue.clear();
                    self.tracks_pending.clear();
                    self.art = vec![None; n];
                    self.art_state = vec![0; n];
                    self.ink = vec![(crate::model::Ink::default(), crate::model::Ink::default()); n];
                    self.ink_real = vec![false; n];
                    self.art_fade = vec![1.0; n];
                    self.decoded = vec![None; n];
                    self.decoded_lru.clear();
                    self.cover_queue.clear();
                    self.urgent_sent.clear();
                    self.wanted.clear();
                    // 回到首页顶部，清掉旧播放状态
                    self.selected = 0;
                    // 索引是旧曲库的下标，换库后必须清掉：否则迷你条会拿旧下标
                    // 去新曲库里取封面（越界 panic，或者显示成别的专辑）。
                    self.playing_album = None;
                    self.title_one_line = false;
                    self.rows_fx.set(1.0);
                    self.scroll = 0.0;
                    self.scroll_vel = 0.0;
                    self.scrolling = false;
                    self.list_scroll = 0.0;
                    self.list_vel = 0.0;
                    self.list_dragging = false;
                    self.playing = false;
                    self.streaming = false;
                    self.pos_secs = 0.0;
                    self.cur_track = 0;
                    self.lyric_song = String::new();
                    if self.settings.is_complete() {
                        self.status = String::new();
                    }
                }
                crate::remote::Msg::Cover { idx, img, tint, ink } => {
                    self.remember_cover(idx, img, tint, ink);
                }
                crate::remote::Msg::CoverFailed(idx) => {
                    // 解码失败：清掉去重标记，让 maintain_art 下次重新插队要，
                    // 避免"失败一次就永远纯色块"。
                    self.urgent_sent.remove(&idx);
                }
                crate::remote::Msg::Tracks { album_idx, tracks } => {
                    // 曲名先排进预热队列，列表延迟到字形预热完再渲染——
                    // 否则曲目到货的同一帧就渲染列表，几十行曲名现场光栅化会卡一下
                    if album_idx < self.lib.albums.len() {
                        let f = m::regular(16.0 * self.k);
                        for t in &tracks {
                            self.warm_queue.push_back((t.title.clone(), f.clone()));
                        }
                        self.tracks_pending.push((album_idx, tracks));
                    }
                }
                crate::remote::Msg::Lyrics {
                    song_id,
                    lyrics,
                    error,
                } => {
                    let st = match lyrics {
                        Some(l) if !l.is_empty() => LyricState::Ready(l),
                        _ => {
                            if let Some(e) = &error {
                                log::info!("lyrics {song_id}: {e}");
                            }
                            LyricState::Missing
                        }
                    };
                    if let LyricState::Ready(l) = &st {
                        let f = m::regular(16.5 * self.k);
                        for line in &l.lines {
                            self.warm_queue.push_back((line.text.clone(), f.clone()));
                        }
                    }
                    self.lyric_cache.insert(song_id, st);
                }
                crate::remote::Msg::Error(e) => {
                    crate::eventlog::log(
                        crate::eventlog::Level::Error,
                        crate::eventlog::EventKind::ApiError,
                        &format!("msg={e}"),
                    );
                    // 只有"还没连上"时的错误才算连接失败（端口写错、服务器没开、密码不对）。
                    // 连上之后某张专辑拉取失败只是单条失败，别把整个会话打回登录态。
                    if !self.connected {
                        self.connecting = false;
                        self.conn_error = e.clone();
                    }
                    self.status = e;
                }
                crate::remote::Msg::Ready => {}
            }
        }
    }

    /// 应用一张专辑的曲目（曲名字形已预热完才调用，避免渲染列表时现场光栅化卡顿）
    fn apply_tracks(&mut self, album_idx: usize, tracks: Vec<crate::model::Track>) {
        if album_idx >= self.lib.albums.len() {
            return;
        }
        // 累计每首歌在整张专辑里的起点（进度线 / 歌词都靠它）
        let mut st = 0.0f32;
        let mut starts = Vec::with_capacity(tracks.len());
        for t in &tracks {
            starts.push(st);
            st += t.secs as f32;
        }
        let total = st;
        // 曲目到货：列表从头淡入（比硬切自然，也盖住字形刚就绪的那一下）
        self.rows_fx.set(0.0);
        let album = &mut self.lib.albums[album_idx];
        album.tracks = tracks;
        album.starts = starts;
        album.total_secs = total;
        // "播放全部"点在曲目到货之前：到了就补播第一首；迷你条播放键同理
        let bar_pending = self.pending_play_album == Some(album_idx);
        if bar_pending {
            self.pending_play_album = None;
        }
        if (self.pending_play && album_idx == self.selected) || bar_pending {
            self.pending_play = false;
            self.play_in_album(album_idx, 0);
        }
    }

    /// 封面解码完先记下来（不马上占纹理），由 upload_covers 按预算上传。
    ///
    /// `ink`（标题区亮度）已经在后台线程算好，这里只落账，不做任何逐像素扫描。
    fn remember_cover(
        &mut self,
        idx: usize,
        img: eframe::egui::ColorImage,
        tint: [u8; 3],
        ink: (crate::model::Ink, crate::model::Ink),
    ) {
        if idx >= self.lib.albums.len() || idx >= self.decoded.len() {
            return;
        }
        self.lib.albums[idx].tint = tint;
        if idx < self.ink.len() {
            self.ink[idx] = ink;
            self.ink_real[idx] = true;
        }
        self.decoded[idx] = Some(img);
        self.decoded_lru.push_back(idx);
        // 解码缓存 LRU：优先淘汰「纹理已经上传」的（decoded 已无用），
        // 保留「还没上传」的（art_state==0，decoded 还要用来传纹理）。
        // 之前是纯 FIFO，会把排队等上传的封面一起淘汰掉，导致它永远传不上纹理、
        // 又因 urgent_sent 已标记不再重试，最终定格成纯色块——「有时封面不显示」的根因。
        while self.decoded_lru.len() > 64 {
            let mut victim = None;
            for (pos, &cand) in self.decoded_lru.iter().enumerate() {
                if cand != idx
                    && cand < self.decoded.len()
                    && self.art_state.get(cand).copied().unwrap_or(0) != 0
                {
                    victim = Some(pos);
                    break;
                }
            }
            if victim.is_none() {
                for (pos, &cand) in self.decoded_lru.iter().enumerate() {
                    if cand != idx && cand < self.decoded.len() {
                        victim = Some(pos);
                        break;
                    }
                }
            }
            let Some(pos) = victim else { break };
            let old = self.decoded_lru[pos];
            self.decoded_lru.remove(pos);
            self.decoded[old] = None;
        }
    }

    fn tick_audio(&mut self, dt: f32) {
        if self.lib.albums.is_empty() {
            return;
        }
        // 当前播放曲目的边界（用明确状态 cur_track，不靠 pos_secs 反推）
        let (cur_start, cur_len) = self.track_bounds(self.cur_track);

        // 切歌后 audio 引擎还在异步切换（position 停在旧歌末尾），这期间不重建位置，
        // 否则 cur_start(新歌) + p(旧歌末尾) 会叠加出错误位置，进度条就不同步。
        let switching = self
            .stream_switch_at
            .map(|t| t.elapsed().as_secs_f32() < 0.35)
            .unwrap_or(false);

        if self.has_audio {
            // cached / 流式都用引擎 position 同步：之前 cached（streaming=false）走
            // 自由时钟 += dt，和硬件时钟漂移，进度条/秒数就和真实播放对不上。
            if let Some(au) = &self.audio {
                let p = au.position_secs();
                if !switching && p > 0.05 {
                    // 用「当前播放曲目」的起点重建位置。之前用 current_track_index()
                    // 反推，在曲目边界它会提前指向下一首，start 突变 → pos 跳变 → 跳歌。
                    self.pos_secs = cur_start + p;
                }
            }
        } else if self.playing {
            // 没有真实音频（异常兜底）：只能自由时钟模拟
            self.pos_secs += dt;
        }

        // 自然播完（到达当前歌结尾）→ 切下一首
        if self.playing && cur_len > 0.0 && self.pos_secs >= cur_start + cur_len {
            self.advance_track();
        }
        self.check_audio_health();
    }

    /// 播放健康度：引擎回报的位置该跟着真实时间走，某一段里明显走不动了
    /// 就是音频线程被卡住过（听感是"卡一下"）。只在真出问题时打一条日志。
    ///
    /// 采样按「当前歌 id」区分：换歌就重置，绝不拿上一首的进度做比较——之前用
    /// 「曲目下标 + 开头 2 秒」判定，切歌缓冲期（流打开 ~0.8s）会连发误报。
    fn check_audio_health(&mut self) {
        if !self.playing || !self.streaming {
            self.pos_probe = None;
            return;
        }
        let now = std::time::Instant::now();
        let song = self.current_song_id().unwrap_or_default();
        let (start, _) = self.track_bounds(self.current_track_index());
        // 起播缓冲期（含流打开）不判定
        if self.pos_secs - start < 3.0 {
            self.pos_probe = Some((now, self.pos_secs, song));
            return;
        }
        match self.pos_probe.take() {
            Some((t, p, sid)) if sid == song => {
                let dw = now.duration_since(t).as_secs_f32();
                if dw >= 0.5 {
                    let dp = self.pos_secs - p;
                    // 这一段里少走了 120ms 以上 = 一次真实的卡顿
                    if dp < dw - 0.12 {
                        log::warn!("audio stall: {dp:.2}s in {dw:.2}s (pos {:.1})", self.pos_secs);
                    }
                    self.pos_probe = Some((now, self.pos_secs, song));
                } else {
                    self.pos_probe = Some((t, p, sid));
                }
            }
            _ => {
                self.pos_probe = Some((now, self.pos_secs, song));
            }
        }
    }

    /// 当前"视线内"的专辑：首页按瓷砖墙上的位置算，其它页也要保留首页可见瓷砖
    /// （否则专辑页会把所有封面纹理释放掉，返回首页时又要一张张重新上传）
    fn want_set(&self, screen: Rect, lay: &HomeLayout) -> Vec<usize> {
        if self.login_open {
            return Vec::new();
        }
        let mut v = Vec::new();
        // 首页可见瓷砖始终保留纹理（无论在哪个页面）
        let band = Rect::from_min_max(
            pos2(screen.min.x, lay.top - 120.0),
            pos2(screen.max.x, lay.bar.max.y + 220.0),
        );
        for (idx, r) in &lay.tiles {
            if band.intersects(*r) {
                v.push(*idx);
            }
        }
        // 迷你条上那张专辑必须一直保留纹理：有播放时是 playing_album，
        // 没有播放时回退到 selected（首页默认就是第 0 张）。
        // 以前只在"非首页"才保留 selected——首页把它滚出屏幕后纹理被释放，
        // 缩略图就退化成一块纯色（而且再也不会重新加载）。
        let bar_album = self.playing_album.unwrap_or(self.selected);
        if bar_album < self.lib.albums.len() && !v.contains(&bar_album) {
            v.push(bar_album);
        }
        v
    }

    /// 把解码缓存里的封面按预算上传成纹理（每帧最多 budget 张）
    fn upload_covers(&mut self, ctx: &egui::Context, budget: usize) {
        let mut used = 0;
        while used < budget {
            let Some(&(idx, _)) = self.cover_queue.front() else {
                break;
            };
            let img = match self.decoded.get_mut(idx) {
                Some(slot) => slot.take(),
                None => None,
            };
            self.cover_queue.pop_front();
            let Some(img) = img else {
                // decoded 已经被淘汰了（正常不该发生，上面的 LRU 会优先保留待上传的），
                // 兜底：重新触发解码，别让这张封面永远停在纯色块。
                self.urgent_sent.remove(&idx);
                if let Some(r) = &self.remote {
                    if let Some(a) = self.lib.albums.get(idx) {
                        let cid = a.cover_id.clone();
                        r.urgent_cover(idx, &cid);
                        self.urgent_sent.insert(idx);
                    }
                }
                continue;
            };
            if idx >= self.art.len() || idx >= self.art_state.len() || idx >= self.art_fade.len() {
                continue;
            }
            self.art[idx] = Some(Artwork::from_image(ctx, idx, img));
            self.art_state[idx] = 2;
            self.art_fade[idx] = 0.0;
            used += 1;
        }
    }

    /// 纹理窗口维护：屏幕外释放纹理、按预算生成程序化封面、给视线内的插队要封面
    fn maintain_art(&mut self) {
        let n = self.lib.albums.len();
        if n == 0 || self.art.len() != n || self.art_state.len() != n {
            return;
        }
        let want: std::collections::HashSet<usize> = self.wanted.iter().copied().collect();

        for i in 0..n {
            if want.contains(&i) {
                // 没纹理就等真封面（urgent 线程按需解码），先显示纯色砖
            } else if self.art_state[i] != 0 {
                self.art[i] = None;
                self.art_state[i] = 0;
                self.urgent_sent.remove(&i);
            }
        }

        // 迷你条里那张封面优先：它可能不在屏幕上的瓷砖里，但底部一直在显示。
        // 不能等批量队列排完（原来只在 cover_queue.len() < 2 时才补请求），
        // 否则切到一张没缓存的专辑、或它刚被释放纹理时，迷你条会空几秒。
        if let Some(pa) = self.playing_album {
            if pa < n
                && self.art_state.get(pa).copied().unwrap_or(0) == 0
                && !self.urgent_sent.contains(&pa)
            {
                let cid = self.lib.albums[pa].cover_id.clone();
                if !cid.is_empty() {
                    if let Some(r) = &self.remote {
                        r.urgent_cover(pa, &cid);
                        self.urgent_sent.insert(pa);
                    }
                }
            }
        }

        // 解码缓存里已经有的：直接排队上传
        for &i in &self.wanted {
            if self.art_state.get(i) == Some(&2) {
                continue;
            }
            if self.decoded.get(i).map(|d| d.is_some()).unwrap_or(false)
                && !self.cover_queue.iter().any(|(q, _)| *q == i)
            {
                self.cover_queue.push_back((i, self.lib.albums[i].tint));
            }
        }

        // 还没真封面的：插队去要
        if self.cover_queue.len() < 2 {
            if let Some(r) = &self.remote {
                for &i in &self.wanted {
                    if !self.urgent_sent.contains(&i) {
                        if let Some(a) = self.lib.albums.get(i) {
                            let cid = a.cover_id.clone();
                            // cover_id 为空就跳过（别拿去请求，会失败并污染 urgent 线程）
                            if cid.is_empty() {
                                continue;
                            }
                            r.urgent_cover(i, &cid);
                            self.urgent_sent.insert(i);
                        }
                    }
                }
            }
        }
    }

}

impl eframe::App for App {
    fn ui(&mut self, ui: &mut Ui, frame: &mut eframe::Frame) {
        self.last_cpu_ms = frame.info().cpu_usage.map(|s| s * 1000.0).unwrap_or(0.0);
        self.ui_root(ui);
    }
}

/// 底栏的三个槽位（左导航 / 曲目胶囊 / 右播放键）。
///
/// 宽度守恒：按住某一侧，它向外撑开 `grow`，中间胶囊吸收掉这部分宽度；
/// 没在播歌时胶囊收起，两侧平分整条。总宽与间距都由这几个纯函数算出来。
fn bar_slots(bar: Rect, k: f32, capsule: bool, pl: f32, pr: f32) -> (Rect, Rect, Rect) {
    let pad = 8.0 * k;
    let base = 56.0 * k;
    let grow = 16.0 * k;
    let inner = (bar.width() - pad * 2.0).max(80.0 * k);
    let gap = 8.0 * k;
    let (mut l, mut r) = if capsule {
        (base + grow * pl, base + grow * pr)
    } else {
        let half = (inner - gap) * 0.5;
        (half, half)
    };
    // 胶囊至少留 120dp；不够就把两侧按比例收回去
    let min_mid = if capsule { 120.0 * k } else { 0.0 };
    let room = (inner - gap - min_mid).max(base * 1.4);
    if l + r > room {
        let f = room / (l + r);
        l *= f;
        r *= f;
    }
    let mid = (inner - gap - l - r).max(0.0);
    let y = bar.min.y;
    let h = bar.height();
    let left = Rect::from_min_size(pos2(bar.min.x + pad, y), vec2(l, h));
    let right = Rect::from_min_size(pos2(bar.max.x - pad - r, y), vec2(r, h));
    let center = Rect::from_min_size(pos2(left.max.x + gap, y), vec2(mid, h));
    (left, center, right)
}
/// 迷你播放条的位置（专辑页也要用）
fn lay_bar(app: &App, screen: Rect) -> Rect {
    // M3 悬浮药丸：左右留白、离手势条浮起一点
    let k = app.k;
    let mx = 12.0 * k;
    let h = 72.0 * k;
    let bottom = app.bar_bottom() + 6.0 * k;
    Rect::from_min_size(
        pos2(mx, screen.max.y - bottom - h),
        vec2((screen.width() - mx * 2.0).max(160.0), h),
    )
}

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

/// 三个输入框的稳定 id（创建和存取光标必须用同一个）
fn input_id(i: usize) -> egui::Id {
    egui::Id::unique(("lumen_input", i))
}

/// 按压/悬停反馈的 0..1 强度：100ms 缓出，手指底下立刻有反应但不闪。
/// 系统要求减弱动效时直接给 0/1。`id` 用来在 egui 里存动画状态，必须每帧一致。
fn press_fx(ctx: &egui::Context, id: egui::Id, on: bool) -> f32 {
    if crate::anim::reduced() {
        return if on { 1.0 } else { 0.0 };
    }
    ctx.animate_bool_with_time(id, on, crate::anim::scaled(crate::anim::dur::PRESS))
}

/// TextEdit 当前光标位置（字符下标）
fn caret_of(ctx: &egui::Context, id: egui::Id) -> Option<usize> {
    let st = egui::text_edit::TextEditState::load(ctx, id)?;
    Some(st.cursor.char_range()?.primary.index.0)
}

/// 设 TextEdit 光标（"硬同步"时用：平台状态和缓冲区差太远，直接对齐）
fn set_caret(ctx: &egui::Context, id: egui::Id, index: usize) {
    let mut st = egui::text_edit::TextEditState::load(ctx, id).unwrap_or_default();
    st.cursor.set_char_range(Some(egui::text::CCursorRange::one(
        egui::text::CCursor::new(index),
    )));
    st.store(ctx, id);
}


#[cfg(test)]
mod bar_layout_tests {
    use super::*;

    fn inner(bar: Rect, k: f32) -> f32 {
        (bar.width() - 16.0 * k).max(80.0 * k)
    }

    #[test]
    fn slots_conserve_width() {
        for w in [300.0f32, 411.0, 600.0] {
            let k = 1.1;
            let bar = Rect::from_min_size(pos2(0.0, 100.0), vec2(w, 72.0 * k));
            for capsule in [false, true] {
                for (pl, pr) in [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)] {
                    let (l, c, r) = bar_slots(bar, k, capsule, pl, pr);
                    assert!(c.width() >= -0.01);
                    let used = l.width() + c.width() + r.width() + 8.0 * k;
                    assert!(used <= inner(bar, k) + 0.5);
                    if !capsule {
                        assert!(c.width() < 0.01);
                    }
                }
            }
        }
    }

    #[test]
    fn pressing_side_expands_it() {
        let k = 1.1;
        let bar = Rect::from_min_size(pos2(0.0, 0.0), vec2(411.0, 72.0 * k));
        let (l0, _, r0) = bar_slots(bar, k, true, 0.0, 0.0);
        let (l1, _, r1) = bar_slots(bar, k, true, 1.0, 1.0);
        assert!(l1.width() > l0.width() + 1.0);
        assert!(r1.width() > r0.width() + 1.0);
    }
}
