//! OpenSubsonic 客户端（Navidrome / Gonic / Airsonic 等）。
//!
//! 只做播放器真正需要的几个端点：
//!   * `ping.view`            连通性检查
//!   * `getAlbumList2.view`   按字母序拉专辑
//!   * `getAlbum.view`        拿某张专辑的曲目
//!   * `stream.view`          音频流（下载到本地缓存后交给 rodio 解码）
//!   * `getCoverArt.view`     封面
//!
//! 认证用 token + salt（`t = md5(password + salt)`），不传明文密码。

use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::Deserialize;

#[derive(Clone, Debug)]
pub struct Config {
    pub base: String,
    pub user: String,
    pub password: String,
}

#[derive(Clone, Debug)]
pub struct Album {
    pub id: String,
    pub name: String,
    pub artist: String,
    pub year: u32,
    pub song_count: usize,
    pub cover_id: String,
}

#[derive(Clone, Debug)]
pub struct Song {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    /// 秒
    pub duration: u32,
    pub track: u32,
    pub suffix: String,
    /// 碟号（1 起；服务端不返回就是 1）
    pub disc: u32,
}

pub struct Client {
    cfg: Config,
    agent: ureq::Agent,
    cache: PathBuf,
    /// 正在播 / 即将播的曲目，淘汰时跳过
    protected: std::sync::Mutex<Vec<String>>,
}

impl Client {
    pub fn new(cfg: Config) -> Self {
        let agent = ureq::Agent::config_builder()
            .timeout_global(Some(Duration::from_secs(30)))
            .build()
            .into();
        Self {
            cfg,
            agent,
            cache: cache_dir(),
            protected: std::sync::Mutex::new(Vec::new()),
        }
    }

    /// 标记为不可淘汰（正在播放、下一个预取目标）
    pub fn protect(&self, song_id: &str) {
        if let Ok(mut v) = self.protected.lock() {
            v.retain(|x| x != song_id);
            v.push(song_id.to_string());
            while v.len() > 3 {
                v.remove(0);
            }
        }
    }

    fn protected_list(&self) -> Vec<String> {
        self.protected.lock().map(|v| v.clone()).unwrap_or_default()
    }

    fn auth(&self) -> String {
        let salt = random_salt();
        let token = md5::compute(format!("{}{}", self.cfg.password, salt));
        format!(
            "u={}&t={:x}&s={}&v=1.16.1&c=Lumen&f=json",
            urlencode(&self.cfg.user),
            token,
            salt
        )
    }

    fn url(&self, endpoint: &str, extra: &str) -> String {
        let base = self.cfg.base.trim_end_matches('/');
        format!("{base}/rest/{endpoint}?{}&{extra}", self.auth())
    }

    pub fn ping(&self) -> Result<bool, String> {
        let u = self.url("ping", "");
        let body = self.get_text(&u)?;
        let r: Envelope<Empty> = serde_json::from_str(&body).map_err(|e| e.to_string())?;
        Ok(r.response.status == "ok")
    }

    pub fn album_list(&self, offset: usize, size: usize) -> Result<Vec<Album>, String> {
        let u = self.url(
            "getAlbumList2",
            &format!("type=alphabeticalByName&offset={offset}&size={size}"),
        );
        let body = self.get_text(&u)?;
        let r: Envelope<AlbumList> = serde_json::from_str(&body).map_err(|e| e.to_string())?;
        Ok(r.response.inner
            .album_list
            .map(|l| l.album.into_iter().map(Album::from).collect())
            .unwrap_or_default())
    }

    pub fn album(&self, id: &str) -> Result<(Album, Vec<Song>), String> {
        let u = self.url("getAlbum", &format!("id={}", urlencode(id)));
        let body = self.get_text(&u)?;
        let r: Envelope<AlbumDetail> = serde_json::from_str(&body).map_err(|e| e.to_string())?;
        let d = r.response.inner.album.ok_or("album missing")?;
        let songs: Vec<Song> = d
            .song
            .into_iter()
            .map(|s| Song {
                id: s.id,
                title: s.title,
                artist: s.artist.unwrap_or_default(),
                album: s.album.unwrap_or_default(),
                duration: s.duration.unwrap_or(0),
                track: s.track.unwrap_or(0),
                suffix: s.suffix.unwrap_or_default(),
                disc: s.disc.unwrap_or(1).max(1),
            })
            .collect();
        // 多碟专辑：按 (碟号, 曲内序号) 排好，保证同碟曲目连续——
        // 列表按碟切页、播放仍用整张专辑的绝对下标，都依赖这个顺序。
        let mut songs = songs;
        songs.sort_by_key(|s| (s.disc, s.track));
        Ok((Album::from(d.album), songs))
    }

    /// 原始流：服务器直接把文件吐出来，支持 Range，能边下边播、能拖拽。
    /// 之前用 `format=mp3` 会让 Navidrome 现场转码，转码流不可 Range、只能整段缓冲才开声。
    pub fn stream_url(&self, song_id: &str) -> String {
        self.url("stream", &format!("id={}", urlencode(song_id)))
    }

    /// 兜底：原文件是 Symphonia 不认识的编码时，退回服务端转码 mp3
    pub fn stream_url_transcoded(&self, song_id: &str) -> String {
        self.url(
            "stream",
            &format!("id={}&format=mp3&maxBitRate=320", urlencode(song_id)),
        )
    }

    /// 取歌词（OpenSubsonic `getLyricsBySongId`）。
    ///
    /// 返回 Ok(None) 表示"这首歌确实没有歌词"，Err 才是网络/解析问题。
    /// Navidrome 没有歌词时给的是 `lyricsList: {}`，解析出来就是空。
    pub fn lyrics(&self, song_id: &str) -> Result<Option<crate::model::Lyrics>, String> {
        let u = self.url("getLyricsBySongId", &format!("id={}", urlencode(song_id)));
        let body = self.get_text(&u)?;
        let r: Envelope<LyricsList> = serde_json::from_str(&body).map_err(|e| e.to_string())?;
        let Some(list) = r.response.inner.lyrics_list else {
            return Ok(None);
        };
        // 优先同步歌词；没有就退到第一条纯文本
        let best = list
            .structured
            .iter()
            .find(|l| l.synced == Some(true) && !l.line.is_empty())
            .or_else(|| list.structured.first());
        let Some(best) = best else {
            return Ok(None);
        };
        let mut out = crate::model::Lyrics::default();
        // structuredLyrics 里每行形如 { start: 11040, value: "..." }
        // 少数服务端把未同步歌词的 start 全填 0，这时按"没有时间轴"处理
        let all_zero = best
            .line
            .iter()
            .filter(|l| !l.value.trim().is_empty())
            .all(|l| l.start.unwrap_or(0) == 0);
        let synced = best.synced.unwrap_or(false) && !all_zero;
        for l in &best.line {
            let text = l.value.trim_end_matches(['\r', '\n']).to_string();
            if text.trim().is_empty() {
                continue;
            }
            let start = if synced {
                l.start.unwrap_or(0) as f32 / 1000.0
            } else {
                -1.0
            };
            out.lines.push(crate::model::LyricLine { start, text });
        }
        out.synced = synced;
        // 兜底：服务端说不同步，但文本里带着 LRC 时间戳
        if !synced && out.lines.iter().any(|l| l.text.contains("[0")) {
            let joined = out
                .lines
                .iter()
                .map(|l| l.text.as_str())
                .collect::<Vec<_>>()
                .join("\n");
            if let Some(parsed) = parse_lrc(&joined) {
                out = parsed;
            }
        }
        out.synced = out.synced && out.lines.iter().all(|l| l.start >= 0.0);
        if out.lines.is_empty() {
            Ok(None)
        } else {
            Ok(Some(out))
        }
    }

    /// 上报播放状态（Subsonic `scrobble`）：
    /// * `submission=false` → "正在播放"（now playing，`getNowPlaying` 的数据来源）
    /// * `submission=true`  → "播放完成"（scrobble 历史）
    ///
    /// Navidrome 的 `getNowPlaying` 完全靠客户端上报才维护；没有这个调用，
    /// 统计面板（navidrome-stat 等）永远探测不到"正在播放什么"。
    pub fn scrobble(&self, song_id: &str, submission: bool) -> Result<(), String> {
        let sub = if submission { "true" } else { "false" };
        let u = self.url(
            "scrobble",
            &format!("id={}&submission={sub}", urlencode(song_id)),
        );
        let body = self.get_text(&u)?;
        // Navidrome 对 scrobble 返回标准 subsonic-response JSON；个别实现可能返回空 body，
        // 只要 HTTP 请求成功就视为已上报，只有明确 status != ok 才算失败。
        if let Ok(r) = serde_json::from_str::<Envelope<Empty>>(&body) {
            if r.response.status != "ok" {
                return Err(format!("scrobble 被拒绝（status={}）", r.response.status));
            }
        }
        Ok(())
    }

    pub fn cover_url(&self, cover_id: &str, size: u32) -> String {
        self.url(
            "getCoverArt",
            &format!("id={}&size={size}", urlencode(cover_id)),
        )
    }

    /// 某首歌在缓存里的固定路径（我们统一请求 mp3）
    pub fn cached_path(&self, song_id: &str) -> PathBuf {
        self.cache.join(format!("{song_id}.mp3"))
    }

    /// 判断某首歌是否已在本地缓存。只在切歌（play_track_at）时调一次，
    /// 不是每帧热点——一次 `metadata` stat 是微秒级，可接受；`touch` 走内存
    /// overlay（不落盘），磁盘写放大已消除。
    pub fn has_cached(&self, song_id: &str) -> bool {
        let hit = is_cached(&self.cached_path(song_id));
        if hit {
            crate::cache::touch(&self.cache, song_id);
        }
        hit
    }

    /// 启动/退出时清一次
    pub fn trim(&self) {
        let keep = self.protected_list();
        crate::cache::trim(&self.cache, &keep);
    }

    /// 缓存占用（音频 / 封面分开）
    pub fn cache_usage(&self) -> crate::cache::Usage {
        crate::cache::usage(&self.cache)
    }

    /// 按 id 预取（已有就跳过）
    pub fn fetch_by_id(&self, song_id: &str) -> Result<PathBuf, String> {
        std::fs::create_dir_all(&self.cache).map_err(|e| e.to_string())?;
        let path = self.cached_path(song_id);
        if is_cached(&path) {
            return Ok(path);
        }
        let tmp = path.with_extension("part");
        let mut resp = self
            .agent
            .get(&self.stream_url(song_id))
            .call()
            .map_err(|e| e.to_string())?;
        let mut f = std::fs::File::create(&tmp).map_err(|e| e.to_string())?;
        std::io::copy(&mut resp.body_mut().as_reader(), &mut f).map_err(|e| e.to_string())?;
        drop(f);
        // 预取完先验一下能不能解码：ALAC / DSD 这类本机没有解码器的格式，
        // 缓存下来也用不上（播放时还得走转码流），只会白占 1.5GB 缓存池。
        let playable = std::fs::File::open(&tmp)
            .ok()
            .and_then(|f| rodio::Decoder::try_from(f).ok())
            .is_some();
        if !playable {
            let _ = std::fs::remove_file(&tmp);
            log::info!("skip caching {song_id}: 本机解不了的格式（会走转码流）");
            return Err(format!("unsupported format: {song_id}"));
        }
        std::fs::rename(&tmp, &path).map_err(|e| e.to_string())?;
        log::info!(
            "prefetched {song_id} ({} KB)",
            std::fs::metadata(&path).map(|m| m.len() / 1024).unwrap_or(0)
        );
        crate::cache::touch(&self.cache, song_id);
        // 预取完就检查容量，超过上限按 LRU 淘汰（不动正在播的）
        let keep = self.protected_list();
        crate::cache::trim(&self.cache, &keep);
        Ok(path)
    }

    /// 封面下载到缓存。按响应 Content-Type 存正确扩展名（jpg/png/webp），
    /// 解码靠 `image` crate 按内容嗅探，扩展名只用于命名，不再固定误标为 .jpg。
    pub fn fetch_cover(&self, cover_id: &str, size: u32) -> Result<PathBuf, String> {
        std::fs::create_dir_all(&self.cache).map_err(|e| e.to_string())?;
        // 命中已有缓存（任意扩展名）
        if let Some(hit) = self.find_cover(cover_id, size) {
            return Ok(hit);
        }
        let mut resp = self
            .agent
            .get(&self.cover_url(cover_id, size))
            .call()
            .map_err(|e| e.to_string())?;
        let ct = resp
            .headers()
            .get("content-type")
            .and_then(|v| v.to_str().ok())
            .unwrap_or("image/jpeg");
        let ext = if ct.contains("png") {
            "png"
        } else if ct.contains("webp") {
            "webp"
        } else {
            "jpg"
        };
        let path = self.cache.join(format!("cover_{cover_id}_{size}.{ext}"));
        let mut f = std::fs::File::create(&path).map_err(|e| e.to_string())?;
        std::io::copy(&mut resp.body_mut().as_reader(), &mut f).map_err(|e| e.to_string())?;
        Ok(path)
    }

    /// 在缓存目录里按 `cover_{id}_{size}.*` 前缀找已缓存的封面（任意扩展名）。
    fn find_cover(&self, cover_id: &str, size: u32) -> Option<PathBuf> {
        let prefix = format!("cover_{cover_id}_{size}.");
        let rd = std::fs::read_dir(&self.cache).ok()?;
        for e in rd.flatten() {
            let name = e.file_name();
            if name.to_string_lossy().starts_with(&prefix) {
                if e.metadata().map(|m| m.len() > 512).unwrap_or(false) {
                    return Some(e.path());
                }
            }
        }
        None
    }

    fn get_text(&self, url: &str) -> Result<String, String> {
        let mut resp = self.agent.get(url).call().map_err(|e| e.to_string())?;
        resp.body_mut()
            .read_to_string()
            .map_err(|e| e.to_string())
    }
}

fn cache_dir() -> PathBuf {
    #[cfg(target_os = "android")]
    {
        if let Some(dir) = crate::safe_area::cache_dir() {
            return PathBuf::from(dir);
        }
    }
    std::env::temp_dir().join("lumen")
}

fn urlencode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// 生成不可预测的 salt：优先用系统熵源（`getrandom`，Android 上走系统调用），
/// 拿不到才退回纳秒时间戳 + 单调计数。之前只用时间戳+计数，取值空间可枚举，
/// 攻击者可对常见密码预计算彩虹表。
fn random_salt() -> String {
    let mut buf = [0u8; 8];
    if getrandom::getrandom(&mut buf).is_ok() {
        return buf.iter().map(|b| format!("{b:02x}")).collect();
    }
    use std::sync::atomic::{AtomicU64, Ordering};
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0);
    let c = COUNTER.fetch_add(1, Ordering::Relaxed);
    format!("{nanos:x}{c:x}")
}

pub fn is_cached(path: &Path) -> bool {
    std::fs::metadata(path).map(|m| m.len() > 1024).unwrap_or(false)
}

// ---------------- JSON 形状 ----------------

#[derive(Deserialize)]
struct Envelope<T> {
    #[serde(rename = "subsonic-response")]
    response: Response<T>,
}

#[derive(Deserialize)]
struct Response<T> {
    status: String,
    #[serde(flatten)]
    inner: T,
}

#[derive(Deserialize, Default)]
struct Empty {}

#[derive(Deserialize, Default)]
struct LyricsList {
    #[serde(rename = "lyricsList", default)]
    lyrics_list: Option<StructuredLyricsList>,
}

#[derive(Deserialize, Default)]
struct StructuredLyricsList {
    #[serde(rename = "structuredLyrics", default)]
    structured: Vec<StructuredLyrics>,
}

#[derive(Deserialize, Default)]
struct StructuredLyrics {
    #[serde(default)]
    synced: Option<bool>,
    #[serde(default)]
    line: Vec<RawLyricLine>,
}

#[derive(Deserialize, Default)]
struct RawLyricLine {
    #[serde(default)]
    start: Option<i64>,
    #[serde(default)]
    value: String,
}

#[derive(Deserialize, Default)]
struct AlbumList {
    #[serde(rename = "albumList2")]
    album_list: Option<AlbumListInner>,
}

#[derive(Deserialize, Default)]
struct AlbumListInner {
    #[serde(default)]
    album: Vec<RawAlbum>,
}

#[derive(Deserialize, Default)]
struct AlbumDetail {
    album: Option<RawAlbumDetail>,
}

#[derive(Deserialize, Default, Clone)]
struct RawAlbum {
    id: String,
    #[serde(default)]
    name: String,
    #[serde(default)]
    artist: String,
    #[serde(default)]
    year: Option<u32>,
    /// Navidrome 用的是驼峰 songCount
    #[serde(default, rename = "songCount")]
    song_count: Option<usize>,
    /// Navidrome 返回 `coverArt`（驼峰）。之前漏了 rename，serde 默认匹配蛇形
    /// `cover_art` 永远解析不到 → cover_id 退化成专辑 id（碰巧 Navidrome 兼容，
    /// 但部分专辑封面可能取错或取不到，是「有时封面不显示」的诱因之一）。
    #[serde(default, rename = "coverArt")]
    cover_art: Option<String>,
}

#[derive(Deserialize, Default)]
struct RawAlbumDetail {
    #[serde(flatten)]
    album: RawAlbum,
    #[serde(default)]
    song: Vec<RawSong>,
}

#[derive(Deserialize, Default)]
struct RawSong {
    id: String,
    #[serde(default)]
    title: String,
    #[serde(default)]
    artist: Option<String>,
    #[serde(default)]
    album: Option<String>,
    #[serde(default)]
    duration: Option<u32>,
    #[serde(default)]
    track: Option<u32>,
    #[serde(default, rename = "discNumber")]
    disc: Option<u32>,
    #[serde(default)]
    suffix: Option<String>,
}

impl From<RawAlbum> for Album {
    fn from(r: RawAlbum) -> Self {
        Self {
            cover_id: r.cover_art.clone().unwrap_or_else(|| r.id.clone()),
            id: r.id,
            name: r.name,
            artist: r.artist,
            year: r.year.unwrap_or(0),
            song_count: r.song_count.unwrap_or(0),
        }
    }
}

/// LRC 解析：`[mm:ss.xx]歌词`，一行可以带多个时间戳，`[ti:]` 之类的元信息跳过。
/// 返回 None 表示没有任何时间戳（纯文本）。
fn parse_lrc(text: &str) -> Option<crate::model::Lyrics> {
    let mut out = crate::model::Lyrics::default();
    for raw in text.lines() {
        let line = raw.trim();
        if line.is_empty() {
            continue;
        }
        let mut rest = line;
        let mut stamps: Vec<f32> = Vec::new();
        while let Some(stripped) = rest.strip_prefix('[') {
            let Some(end) = stripped.find(']') else { break };
            let tag = &stripped[..end];
            match parse_stamp(tag) {
                Some(secs) => stamps.push(secs),
                None => {} // [ti:] [ar:] [offset:] 之类，忽略
            }
            rest = &stripped[end + 1..];
        }
        let content = rest.trim();
        if stamps.is_empty() {
            continue;
        }
        if content.is_empty() {
            // 纯时间戳行：当作间奏的空行
            continue;
        }
        for secs in stamps {
            out.lines.push(crate::model::LyricLine {
                start: secs,
                text: content.to_string(),
            });
        }
    }
    if out.lines.is_empty() {
        return None;
    }
    out.lines.sort_by(|a, b| a.start.partial_cmp(&b.start).unwrap_or(std::cmp::Ordering::Equal));
    out.synced = true;
    Some(out)
}

fn parse_stamp(tag: &str) -> Option<f32> {
    let (m, rest) = tag.split_once(':')?;
    let mins: f32 = m.trim().parse().ok()?;
    let secs: f32 = rest.trim().replace(',', ".").parse().ok()?;
    Some(mins * 60.0 + secs)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lrc_basic() {
        let text = "[ti:test]\n[00:12.50]第一行\n[00:20.00][01:05.25]重复行\n";
        let l = parse_lrc(text).expect("should parse");
        assert_eq!(l.lines.len(), 3);
        assert!((l.lines[0].start - 12.5).abs() < 0.01);
        assert_eq!(l.lines[2].text, "重复行");
        assert!((l.lines[2].start - 65.25).abs() < 0.01);
    }


    #[test]
    fn raw_album_cover_art_camel_case() {
        let j = r#"{"id":"abc","name":"n","artist":"a","coverArt":"al-abc_xyz","songCount":3,"year":2020}"#;
        let r: RawAlbum = serde_json::from_str(j).expect("should parse");
        println!("cover_art = {:?}", r.cover_art);
        assert_eq!(r.cover_art.as_deref(), Some("al-abc_xyz"));
        let album = Album::from(r);
        assert_eq!(album.cover_id, "al-abc_xyz");
    }


    #[test]
    fn album_list_envelope_cover_art() {
        let j = r#"{"subsonic-response":{"status":"ok","albumList2":{"album":[{"id":"abc","name":"n","artist":"a","coverArt":"al-abc_xyz","songCount":3,"year":2020}]}}}"#;
        let r: Envelope<AlbumList> = serde_json::from_str(j).expect("should parse envelope");
        let list = r.response.inner.album_list.expect("should have albumList2");
        let albums: Vec<Album> = list.album.into_iter().map(Album::from).collect();
        println!("album[0].cover_id = {:?}", albums[0].cover_id);
        assert_eq!(albums[0].cover_id, "al-abc_xyz");
    }

    #[test]
    fn plain_text_is_not_lrc() {
        assert!(parse_lrc("没有时间戳\n第二行").is_none());
    }

    #[test]
    fn parse_stamp_edges() {
        assert!((parse_stamp("12:34").unwrap() - 754.0).abs() < 0.01);
        assert!((parse_stamp("1:05.25").unwrap() - 65.25).abs() < 0.01);
        // LRC 允许用逗号当小数点
        assert!((parse_stamp("1:05,25").unwrap() - 65.25).abs() < 0.01);
        assert!(parse_stamp("ti:test").is_none());
        assert!(parse_stamp("notatime").is_none());
    }

    #[test]
    fn urlencode_basic() {
        assert_eq!(urlencode("abc-123._~"), "abc-123._~");
        assert_eq!(urlencode("a b"), "a%20b");
        assert_eq!(urlencode("中文"), "%E4%B8%AD%E6%96%87");
    }
}
