//! 远端曲库：把 Navidrome 的专辑/封面/曲目拉到 UI，两个后台线程各管一摊。
//!
//! * `meta` 线程：先 `ping` + `getAlbumList2`，之后串行处理"要某张专辑的曲目"；
//! * `art` 线程：拿到专辑清单后逐张 `getCoverArt`，解码成 `ColorImage` 交给 UI 上纹理。
//!
//! UI 每帧 `poll()` 一次，拿到的消息在主线程应用，绝不阻塞渲染。

use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::Arc;

use crate::model::{Album, Track};
use crate::subsonic::{Client, Config};

pub enum Msg {
    /// 整份专辑清单
    Albums(Vec<Album>),
    /// 某张的封面（`ink` 是标题区亮度，已在 worker 里算好，UI 不再逐像素扫描）
    Cover { idx: usize, img: eframe::egui::ColorImage, tint: [u8; 3], ink: (crate::model::Ink, crate::model::Ink) },
    /// 某张封面解码失败（下载/读盘/解码任一步出错）。UI 收到后清除去重标记，
    /// 让 maintain_art 下次重新插队要一次，避免"失败一次就永远纯色块"。
    CoverFailed(usize),
    /// 某张的曲目
    Tracks { album_idx: usize, tracks: Vec<Track> },
    /// 一首歌的歌词（None = 这首歌没有歌词）
    Lyrics {
        song_id: String,
        lyrics: Option<crate::model::Lyrics>,
        error: Option<String>,
    },
    Error(String),
    Ready,
}

enum Job {
    Album { idx: usize, id: String },
    /// 后台把下一首拉到本地缓存
    Prefetch(String),
}

pub struct Remote {
    jobs: Sender<Job>,
    /// 当前选中专辑的封面插队：单独通道 + 单独线程，不排在批量后面
    urgent: Sender<(usize, String)>,
    /// 歌词请求：单独的线程，别排在几十 MB 的预取后面
    lyrics: Sender<String>,
    /// scrobble 上报：fire-and-forget，单独线程，绝不阻塞 UI / 音频
    scrobble: Sender<(String, bool)>,
    msgs: Receiver<Msg>,
    client: Arc<Client>,
}

impl Remote {
    pub fn spawn(cfg: Config) -> Self {
        let (job_tx, job_rx) = channel::<Job>();
        let (msg_tx, msg_rx) = channel::<Msg>();
        let base = cfg.base.clone();
        let client = Arc::new(Client::new(cfg));

        // ---- 歌词线程 ----
        // 歌词是切歌时才要的，量小、延迟敏感，单独一条线程，绝不跟预取抢。
        let (lyrics_tx, lyrics_rx) = channel::<String>();
        {
            let c = client.clone();
            let tx = msg_tx.clone();
            std::thread::Builder::new()
                .name("lumen-lyrics".into())
                .spawn(move || {
                    while let Ok(song_id) = lyrics_rx.recv() {
                        match c.lyrics(&song_id) {
                            Ok(l) => {
                                let _ = tx.send(Msg::Lyrics {
                                    song_id,
                                    lyrics: l,
                                    error: None,
                                });
                            }
                            Err(e) => {
                                log::warn!("lyrics {song_id} failed: {e}");
                                let _ = tx.send(Msg::Lyrics {
                                    song_id,
                                    lyrics: None,
                                    error: Some(e),
                                });
                            }
                        }
                    }
                })
                .ok();
        }

        // ---- scrobble 线程 ----
        // 播放状态上报：量小、fire-and-forget，单独一条线程，失败只打日志不重试。
        let (scrobble_tx, scrobble_rx) = channel::<(String, bool)>();
        {
            let c = client.clone();
            std::thread::Builder::new()
                .name("lumen-scrobble".into())
                .spawn(move || {
                    while let Ok((song_id, submission)) = scrobble_rx.recv() {
                        if let Err(e) = c.scrobble(&song_id, submission) {
                            log::warn!("scrobble {song_id} (sub={submission}) failed: {e}");
                        }
                    }
                })
                .ok();
        }

        // ---- 封面线程 ----
        let (cover_tx, cover_rx) = channel::<Vec<(usize, String)>>();
        {
            let c = client.clone();
            std::thread::Builder::new()
                .name("lumen-art".into())
                .spawn(move || {
                    let Ok(list) = cover_rx.recv() else { return };
                    let started = std::time::Instant::now();
                    let total = list.len();
                    // 共享队列 + N 个 worker：封面是网络密集，4~6 路并发最划算
                    let queue = std::sync::Arc::new(std::sync::Mutex::new(
                        std::collections::VecDeque::from(list),
                    ));
                    let workers = total.clamp(1, 6);
                    let mut handles = Vec::new();
                    for _ in 0..workers {
                        let q = queue.clone();
                        let c = c.clone();
                        handles.push(std::thread::spawn(move || {
                            loop {
                                let next = q.lock().ok().and_then(|mut g| g.pop_front());
                                let Some((_idx, cover_id)) = next else { break };
                                // 只下到磁盘做缓存预热，不解码、不上纹理：真正的解码由
                                // urgent 线程按「视线内」按需做，避免 120 张封面全量
                                // 解码成 ColorImage 堆进内存（之前 ~76MB，再被 64 张 LRU 淘汰）。
                                if c.fetch_cover(&cover_id, 400).is_err() {
                                    log::warn!("cover fetch failed: {cover_id}");
                                }
                            }
                        }));
                    }
                    for h in handles {
                        let _ = h.join();
                    }
                    log::info!(
                        "covers done: {total} in {} ms ({} workers)",
                        started.elapsed().as_millis(),
                        workers
                    );
                })
                .ok();
        }

        // ---- 元数据线程 ----
        {
            let c = client.clone();
            let tx = msg_tx.clone();
            let base = base.clone();
            std::thread::Builder::new()
                .name("lumen-meta".into())
                .spawn(move || {
                    match c.ping() {
                        Ok(true) => log::info!("navidrome: ping ok"),
                        Ok(false) => {
                            let _ = tx.send(Msg::Error(format!(
                                "服务器拒绝登录：用户名或密码不对（{base}）"
                            )));
                            return;
                        }
                        Err(e) => {
                            let _ = tx.send(Msg::Error(format!("连不上 {base}：{e}")));
                            return;
                        }
                    }
                    // 分页拉取全部专辑：曲库超过一页时循环取，直到返回不足一页为止。
                    // 之前硬编码 120 张，大曲库会静默漏掉剩余专辑。
                    const PAGE: usize = 500;
                    let mut all: Vec<crate::subsonic::Album> = Vec::new();
                    let mut offset = 0usize;
                    loop {
                        match c.album_list(offset, PAGE) {
                            Ok(page) => {
                                let n = page.len();
                                all.extend(page);
                                offset += n;
                                if n < PAGE {
                                    break; // 最后一页
                                }
                            }
                            Err(e) => {
                                // 中途失败：已有数据就先接着用，别让整库都拉不出来
                                if all.is_empty() {
                                    let _ = tx.send(Msg::Error(format!(
                                        "曲库拉取失败（{base}）：{e}"
                                    )));
                                    return;
                                }
                                log::warn!("album_list 分页中断（已取 {} 张）：{e}", all.len());
                                break;
                            }
                        }
                    }
                    {
                        let list = &all;
                        log::info!("navidrome: {} 张专辑", list.len());
                        let covers: Vec<(usize, String)> = list
                            .iter()
                            .enumerate()
                            .map(|(i, a)| (i, a.cover_id.clone()))
                            .collect();
                        let albums: Vec<Album> = list
                            .iter()
                            .enumerate()
                            .map(|(i, a)| Album {
                                id: a.id.clone(),
                                cover_id: a.cover_id.clone(),
                                title: a.name.clone(),
                                artist: a.artist.clone(),
                                year: a.year as u16,
                                hue: hue_of(&a.id, i),
                                song_count: a.song_count as u16,
                                tint: [0, 0, 0],
                                starts: Vec::new(),
                                total_secs: 0.0,
                                tracks: Vec::new(),
                            })
                            .collect();
                        let _ = cover_tx.send(covers);
                        let _ = tx.send(Msg::Albums(albums));
                        let _ = tx.send(Msg::Ready);
                    }

                    while let Ok(job) = job_rx.recv() {
                        let (idx, id) = match job {
                            Job::Prefetch(song_id) => {
                                if let Err(e) = c.fetch_by_id(&song_id) {
                                    log::warn!("prefetch {song_id} failed: {e}");
                                }
                                continue;
                            }
                            Job::Album { idx, id } => (idx, id),
                        };
                        match c.album(&id) {
                            Ok((_a, songs)) => {
                                let tracks = songs
                                    .iter()
                                    .map(|s| Track {
                                        id: s.id.clone(),
                                        title: s.title.clone(),
                                        artist: s.artist.clone(),
                                        secs: s.duration,
                                        disc: s.disc.min(u16::MAX as u32) as u16,
                                    })
                                    .collect();
                                let _ = tx.send(Msg::Tracks {
                                    album_idx: idx,
                                    tracks,
                                });
                            }
                            Err(e) => {
                                let _ = tx.send(Msg::Error(format!("专辑拉取失败：{e}")));
                            }
                        }
                    }
                })
                .ok();
        }

        let (urgent_tx, urgent_rx) = channel::<(usize, String)>();
        {
            // 3 个插队解码线程：单线程时一张 ~15ms，一屏 20 张要 300ms，
            // 看起来就是"拖到了才一张张冒出来"
            let shared = std::sync::Arc::new(std::sync::Mutex::new(urgent_rx));
            for _ in 0..3 {
                let c = client.clone();
                let tx = msg_tx.clone();
                let rx = shared.clone();
                std::thread::Builder::new()
                    .name("lumen-art-urgent".into())
                    .spawn(move || loop {
                        let job = {
                            let g = rx.lock().unwrap();
                            g.recv()
                        };
                        let Ok((idx, cover_id)) = job else { break };
                        let Ok(path) = c.fetch_cover(&cover_id, 400) else {
                            log::warn!("urgent fetch failed idx={} cid={}", idx, cover_id);
                            let _ = tx.send(Msg::CoverFailed(idx));
                            continue;
                        };
                        let Ok(bytes) = std::fs::read(&path) else {
                            log::warn!("urgent read failed idx={}", idx);
                            let _ = tx.send(Msg::CoverFailed(idx));
                            continue;
                        };
                        if let Ok(img) = image::load_from_memory(&bytes) {
                            let rgba = img.to_rgba8();
                            let size = [rgba.width() as usize, rgba.height() as usize];
                            let tint = tint_of(&rgba);
                            let ci = eframe::egui::ColorImage::from_rgba_unmultiplied(
                                size,
                                rgba.as_raw(),
                            );
                            let ink = crate::model::ink_of(&ci);
                            let _ = tx.send(Msg::Cover { idx, img: ci, tint, ink });
                        } else {
                            log::warn!("urgent decode failed idx={}", idx);
                            let _ = tx.send(Msg::CoverFailed(idx));
                        }
                    })
                    .ok();
            }
        }

        Self {
            jobs: job_tx,
            urgent: urgent_tx,
            lyrics: lyrics_tx,
            scrobble: scrobble_tx,
            msgs: msg_rx,
            client,
        }
    }

    /// 最多取 n 条（防止一帧里醒来太多大图）
    pub fn poll_n(&self, n: usize) -> Vec<Msg> {
        let mut out = Vec::new();
        for _ in 0..n {
            match self.msgs.try_recv() {
                Ok(m) => out.push(m),
                Err(_) => break,
            }
        }
        out
    }

    pub fn poll(&self) -> Vec<Msg> {
        let mut out = Vec::new();
        while let Ok(m) = self.msgs.try_recv() {
            out.push(m);
        }
        out
    }

    pub fn request_album(&self, idx: usize, id: String) {
        let _ = self.jobs.send(Job::Album { idx, id });
    }

    /// 插队拉某张封面（当前在看的专辑）
    pub fn urgent_cover(&self, idx: usize, cover_id: &str) {
        let _ = self.urgent.send((idx, cover_id.to_string()));
    }

    /// 保护某首不被缓存淘汰（正在播）
    pub fn protect(&self, song_id: &str) {
        self.client.protect(song_id);
    }

    /// 预取下一首（已有缓存会自动跳过）
    /// 请求一首歌的歌词（后台线程去取，结果从 Msg::Lyrics 回来）
    pub fn lyrics(&self, song_id: &str) {
        let _ = self.lyrics.send(song_id.to_string());
    }

    /// 上报播放状态（后台线程 fire-and-forget，绝不阻塞 UI）。
    /// `submission=false` 起播/恢复、`submission=true` 播完/切走。
    pub fn scrobble(&self, song_id: &str, submission: bool) {
        let _ = self.scrobble.send((song_id.to_string(), submission));
    }

    pub fn prefetch(&self, song_id: &str) {
        let _ = self.jobs.send(Job::Prefetch(song_id.to_string()));
    }

    /// 已经在本地缓存里了就返回路径
    pub fn cached(&self, song_id: &str) -> Option<std::path::PathBuf> {
        if self.client.has_cached(song_id) {
            Some(self.client.cached_path(song_id))
        } else {
            None
        }
    }

    /// 缓存占用（音频 / 封面分开）
    pub fn cache_usage(&self) -> crate::cache::Usage {
        self.client.cache_usage()
    }

    /// 启动时清一次（超过上限就淘汰）
    pub fn trim_cache(&self) {
        self.client.trim();
    }

    pub fn stream_url(&self, song_id: &str) -> String {
        self.client.stream_url(song_id)
    }

    /// 兜底：原编码解不了时退回服务端转码
    pub fn stream_url_transcoded(&self, song_id: &str) -> String {
        self.client.stream_url_transcoded(song_id)
    }
}

fn hue_of(id: &str, idx: usize) -> f32 {
    let mut h: u32 = 2166136261 ^ idx as u32;
    for b in id.bytes() {
        h ^= b as u32;
        h = h.wrapping_mul(16777619);
    }
    (h % 360) as f32
}

/// 封面的主色：按"饱和度 × 不太亮不太暗"加权平均，偏灰和极端的像素降权。
fn tint_of(img: &image::RgbaImage) -> [u8; 3] {
    let mut acc = [0f64; 3];
    let mut wsum = 0f64;
    for px in img.pixels().step_by(7) {
        let (r, g, b) = (px[0] as f64, px[1] as f64, px[2] as f64);
        let max = r.max(g).max(b);
        let min = r.min(g).min(b);
        let sat = if max > 0.0 { (max - min) / max } else { 0.0 };
        let lum = (max + min) / 2.0 / 255.0;
        let w = sat * (1.0 - (lum - 0.5).abs() * 1.2).max(0.0) + 0.05;
        acc[0] += r * w;
        acc[1] += g * w;
        acc[2] += b * w;
        wsum += w;
    }
    if wsum <= 0.0 {
        return [0, 0, 0];
    }
    [
        (acc[0] / wsum) as u8,
        (acc[1] / wsum) as u8,
        (acc[2] / wsum) as u8,
    ]
}
