//! 全局持久化事件日志：用于后期问题调试。
//!
//! - 启动即生效（`init()`），覆盖输入、听歌活动、页面切换、接口异常、崩溃
//! - 每行一条 JSON（JSON Lines），按时间顺序追加，重启后可查
//! - 落在应用私有目录 `logs/events.log`，超限自动轮转
//! - 键盘输入只记长度/字段名，**不落敏感明文**（密码尤其）
//!
//! 统一入口 [`log`] / [`log_state`]，也可用 [`event!`] 宏。

use std::fmt;
use std::fs::{File, OpenOptions};
use std::io::{BufWriter, Write};
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

// ---------------------------------------------------------------------------
// 事件类型
// ---------------------------------------------------------------------------

/// 事件类型：页面/输入/听歌活动/接口/错误，用小写下划线写进日志的 `ev` 字段。
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum EventKind {
    // 生命周期
    AppStart,
    AppExit,
    AppCrash,
    StateChange,
    // 页面 / 设置
    PageSwitch,
    SetupOpen,
    SetupConnect,
    SetupLogout,
    // 交互（通用）
    Click,
    Submit,
    Validate,
    Navigation,
    // 输入（脱敏）
    InputFocus,
    InputText,
    // 听歌活动
    TrackSelect,
    PlayStart,
    PlayPause,
    PlayResume,
    PlayStop,
    PlayNext,
    PlayPrev,
    PlaySeek,
    PlayEnd,
    // 数据 / 接口
    ApiCall,
    ApiOk,
    ApiError,
    CacheTrim,
    Error,
}

impl EventKind {
    /// JSON 里的 `ev` 字段值
    pub fn as_str(self) -> &'static str {
        match self {
            EventKind::AppStart => "app_start",
            EventKind::AppExit => "app_exit",
            EventKind::AppCrash => "app_crash",
            EventKind::StateChange => "state_change",
            EventKind::PageSwitch => "page_switch",
            EventKind::SetupOpen => "setup_open",
            EventKind::SetupConnect => "setup_connect",
            EventKind::SetupLogout => "setup_logout",
            EventKind::Click => "click",
            EventKind::Submit => "submit",
            EventKind::Validate => "validate",
            EventKind::Navigation => "navigation",
            EventKind::InputFocus => "input_focus",
            EventKind::InputText => "input_text",
            EventKind::TrackSelect => "track_select",
            EventKind::PlayStart => "play_start",
            EventKind::PlayPause => "play_pause",
            EventKind::PlayResume => "play_resume",
            EventKind::PlayStop => "play_stop",
            EventKind::PlayNext => "play_next",
            EventKind::PlayPrev => "play_prev",
            EventKind::PlaySeek => "play_seek",
            EventKind::PlayEnd => "play_end",
            EventKind::ApiCall => "api_call",
            EventKind::ApiOk => "api_ok",
            EventKind::ApiError => "api_error",
            EventKind::CacheTrim => "cache_trim",
            EventKind::Error => "error",
        }
    }
}

impl fmt::Debug for EventKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// 日志级别
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Level {
    Info,
    Warn,
    Error,
}

impl Level {
    pub fn as_str(self) -> &'static str {
        match self {
            Level::Info => "INFO",
            Level::Warn => "WARN",
            Level::Error => "ERROR",
        }
    }
}

// ---------------------------------------------------------------------------
// 落盘
// ---------------------------------------------------------------------------

struct Sink {
    file: Option<BufWriter<File>>,
    path: Option<PathBuf>,
    written: u64,
    last_flush: Instant,
}

static SINK: Mutex<Option<Sink>> = Mutex::new(None);
/// 单文件上限：超过就轮转成 .old，避免无限膨胀
const MAX_BYTES: u64 = 2 * 1024 * 1024;

/// 日志文件目录：Android 用应用私有 files 目录，桌面用系统临时目录。
fn log_dir() -> Option<PathBuf> {
    #[cfg(target_os = "android")]
    {
        let dir = crate::safe_area::files_dir()?;
        return Some(PathBuf::from(dir).join("logs"));
    }
    #[cfg(not(target_os = "android"))]
    {
        Some(std::env::temp_dir().join("lumen").join("logs"))
    }
}

/// 启动时调用：建目录、打开（或新建）日志文件。可重复调用（只生效一次）。
pub fn init() {
    let mut guard = SINK.lock().unwrap();
    if guard.is_some() {
        return;
    }
    let Some(dir) = log_dir() else {
        log::warn!("eventlog: 拿不到日志目录，事件只进 logcat");
        return;
    };
    if std::fs::create_dir_all(&dir).is_err() {
        log::warn!("eventlog: 建目录失败 {:?}", dir);
        return;
    }
    let path = dir.join("events.log");
    // 超限轮转：events.log → events.log.old
    if let Ok(meta) = std::fs::metadata(&path) {
        if meta.len() > MAX_BYTES {
            let _ = std::fs::rename(&path, dir.join("events.log.old"));
        }
    }
    match OpenOptions::new().create(true).append(true).open(&path) {
        Ok(f) => {
            let written = std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
            *guard = Some(Sink {
                file: Some(BufWriter::new(f)),
                path: Some(path),
                written,
                last_flush: Instant::now(),
            });
            log::info!("eventlog: 就绪 {:?}", dir);
        }
        Err(e) => log::warn!("eventlog: 打开失败 {e}"),
    }
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// JSON 字符串转义（ctx 里可能有引号/换行/中文）
fn json_escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 8);
    for ch in s.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

/// 统一记录入口：写一行 JSON 到持久化文件，同时镜像到 logcat/stderr。
///
/// `ctx` 是关键上下文（key=value 空格分隔），**不要放敏感明文**（密码/令牌）。
pub fn log(level: Level, kind: EventKind, ctx: &str) {
    log_state(level, kind, ctx, None);
}

/// 带状态变化的记录：`state` 记录变化后的状态（如 "playing" / "album" / "error"）。
pub fn log_state(level: Level, kind: EventKind, ctx: &str, state: Option<&str>) {
    let ts = now_millis();
    let ev = kind.as_str();
    let lv = level.as_str();
    let line = match state {
        Some(st) => format!(
            "{{\"t\":{ts},\"lv\":\"{lv}\",\"ev\":\"{ev}\",\"ctx\":\"{}\",\"st\":\"{}\"}}\n",
            json_escape(ctx),
            json_escape(st)
        ),
        None => format!(
            "{{\"t\":{ts},\"lv\":\"{lv}\",\"ev\":\"{ev}\",\"ctx\":\"{}\"}}\n",
            json_escape(ctx)
        ),
    };

    // 镜像到 logcat/stderr，方便实时看
    match level {
        Level::Info => log::info!(target: "eventlog", "{ev} {ctx}"),
        Level::Warn => log::warn!(target: "eventlog", "{ev} {ctx}"),
        Level::Error => log::error!(target: "eventlog", "{ev} {ctx}"),
    }

    // 落盘
    if let Ok(mut guard) = SINK.lock() {
        if let Some(sink) = guard.as_mut() {
            if let Some(f) = sink.file.as_mut() {
                let _ = f.write_all(line.as_bytes());
                sink.written += line.len() as u64;
                // 缓冲写 + 定时 flush（>500ms 才刷一次），不再每条都刷盘——
                // 高频输入时每条 flush 会在 UI 线程引入可感知的磁盘延迟。
                if sink.last_flush.elapsed() > Duration::from_millis(500) {
                    let _ = f.flush();
                    sink.last_flush = Instant::now();
                }
                if sink.written > MAX_BYTES {
                    if let Some(path) = sink.path.clone() {
                        // 轮转前先释放旧文件句柄（Windows 上 rename 打开中的文件会失败）
                        let old_file = sink.file.take();
                        if let Some(mut f) = old_file {
                            let _ = f.flush();
                        }
                        let old = path.with_extension("log.old");
                        let _ = std::fs::rename(&path, &old);
                        if let Ok(nf) = OpenOptions::new().create(true).append(true).open(&path) {
                            sink.file = Some(BufWriter::new(nf));
                            sink.written = 0;
                            sink.last_flush = Instant::now();
                        }
                    }
                }
            }
        }
    }
}

/// 便捷宏：`event!(Level::Info, EventKind::PlayStart, "id={}", id)`
#[macro_export]
macro_rules! event {
    ($lvl:expr, $kind:expr, $($arg:tt)*) => {
        $crate::eventlog::log($lvl, $kind, &format!($($arg)*))
    };
    ($lvl:expr, $kind:expr, $ctx:expr; $state:expr) => {
        $crate::eventlog::log_state($lvl, $kind, $ctx, Some($state))
    };
}

// ---------------------------------------------------------------------------
// 键盘输入脱敏：只记字段名和长度，绝不落明文（尤其密码）
// ---------------------------------------------------------------------------

/// 记录输入框文字变化——**只记长度**，不记内容。
/// `field` 是字段标识（base/user/pass），`len` 是当前字符数。
pub fn input_text(field: &str, len: usize) {
    log_state(
        Level::Info,
        EventKind::InputText,
        &format!("field={field} len={len}"),
        Some(field),
    );
}

/// 记录输入框聚焦
pub fn input_focus(field: &str) {
    log(Level::Info, EventKind::InputFocus, &format!("field={field}"));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn event_kind_str() {
        assert_eq!(EventKind::PlayStart.as_str(), "play_start");
        assert_eq!(EventKind::ApiError.as_str(), "api_error");
    }

    #[test]
    fn json_escape_quotes() {
        assert_eq!(json_escape("a\"b"), "a\\\"b");
        assert_eq!(json_escape("a\nb"), "a\\nb");
    }
}
