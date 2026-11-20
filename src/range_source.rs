//! 支持 HTTP Range 的可 Seek 音频源。
//!
//! Rodio/Symphonia 的 `Decoder` 要求 `Read + Seek`。直接把 HTTP 响应体（只读、不可 Seek）
//! 塞进去，某些格式在解码器尝试回退/探测时会 panic。这里用 HTTP Range 请求实现
//! 一个真正可 Seek 的 `Read`：
//!
//! * 打开时用 `Range: bytes=0-0` 探测：返回 206 说明服务端支持 Range，并从
//!   `Content-Range: bytes 0-0/<总长>` 里拿到文件总长；
//! * **后台线程预读**：网络线程按 1 MB 分块一直往前取（最多缓冲 6 MB），
//!   播放侧只从内存里拿数据。
//! * `seek` 只是改变偏移并通知后台线程换位置，下一次 `read` 会从新位置的块开始。
//!
//! 为什么要预读线程：解码是**在音频线程里**跑的，任何一次阻塞式网络请求都会让
//! 输出欠载（听感就是"每隔几十秒卡一下"）。之前每个 1 MB 块由解码侧同步请求，
//! 实测每次 ~145 ms，正好对上那种周期性卡顿；现在播放侧永远从已备好的内存取。

use std::io::{self, Read, Seek, SeekFrom};
use std::sync::mpsc::{sync_channel, Receiver, Sender, SyncSender};
use std::sync::{Arc, Condvar, Mutex};

const CHUNK: u64 = 1024 * 1024;
/// 起点（或刚 seek 完）的首块用小尺寸：让解码器尽快拿到第一批字节开声，
/// 之后再用 1MB 大块续着预读，减少「切歌到出声」的等待。
const FIRST_CHUNK: u64 = 256 * 1024;
/// 预读窗口：6 块 ≈ 6 MB（320 kbps 下约 2.5 分钟音频）
const AHEAD: usize = 6;

/// 服务端不支持 Range（实时转码）时的共享缓冲：后台线程边下边播。
struct Live {
    data: Vec<u8>,
    eof: bool,
    err: Option<String>,
}

pub struct HttpRangeSource {
    /// 文件总长（探测得到；未知时为 0）
    pub len: u64,
    pos: u64,
    /// 当前正在消费的块
    buf: Vec<u8>,
    buf_start: u64,
    /// 服务端是否真的支持 Range
    pub ranged: bool,
    /// 不支持 Range 时（实时转码流）：边下边播的共享缓冲
    live: Option<Arc<(Mutex<Live>, Condvar)>>,
    /// 后台线程送来的块：(起始偏移, 数据)。用 Mutex 包一层是因为
    /// rodio 的 Decoder 要求源是 Sync，而 std 的 Receiver 不是。
    rx: Mutex<Receiver<(u64, Vec<u8>)>>,
    /// 告诉后台线程换位置
    seek_tx: Sender<u64>,
}

impl HttpRangeSource {
    /// 探测 + 建立源。服务端不支持 Range 时仍然可以顺序读（ranged = false），
    /// 但 Seek 只能在前向时被"跳过"，向后 seek 会失败并返回错误。
    pub fn open(url: &str) -> io::Result<Self> {
        let agent = new_agent();
        let mut resp = agent
            .get(url)
            .header("Range", "bytes=0-0")
            .call()
            .map_err(io_err)?;
        let mut len = 0u64;
        let mut ranged = false;
        // 200 时创建：边下边播的共享缓冲
        let mut live: Option<Arc<(Mutex<Live>, Condvar)>> = None;
        if resp.status() == 206 {
            ranged = true;
            if let Some(cr) = resp.headers().get("content-range").and_then(|v| v.to_str().ok()) {
                if let Some(total) = cr.rsplit('/').next() {
                    len = total.trim().parse().unwrap_or(0);
                }
            }
        } else {
            // 200：实时转码（Navidrome 现场编码），不支持 Range。
            // 以前是 `read_to_end` 整段下完才开声——一首 46 MB 的 ALAC 转码要十几秒，
            // 期间播放器静音、UI 停在 0:00，看起来就是"这首歌不播放"。
            // 现在开后台线程边下边播，首块到达就能出声。
            log::info!("stream not rangeable, progressive buffering");
            let lv = Arc::new((
                Mutex::new(Live {
                    data: Vec::new(),
                    eof: false,
                    err: None,
                }),
                Condvar::new(),
            ));
            live = Some(lv.clone());
            let shared = lv.clone();
            let _ = std::thread::Builder::new()
                .name("lumen-live".into())
                .spawn(move || {
                    let mut reader = resp.body_mut().as_reader();
                    let mut chunk = vec![0u8; 64 * 1024];
                    loop {
                        match reader.read(&mut chunk) {
                            Ok(0) => break,
                            Ok(n) => {
                                let (m, cv) = &*shared;
                                if let Ok(mut g) = m.lock() {
                                    g.data.extend_from_slice(&chunk[..n]);
                                    cv.notify_all();
                                }
                            }
                            Err(e) => {
                                let (m, cv) = &*shared;
                                if let Ok(mut g) = m.lock() {
                                    g.err = Some(e.to_string());
                                    cv.notify_all();
                                }
                                return;
                            }
                        }
                    }
                    let (m, cv) = &*shared;
                    if let Ok(mut g) = m.lock() {
                        g.eof = true;
                        cv.notify_all();
                    }
                });
        }

        // 预读线程：只有真支持 Range 才需要
        let (tx, rx): (SyncSender<(u64, Vec<u8>)>, Receiver<(u64, Vec<u8>)>) = sync_channel(AHEAD);
        let (seek_tx, seek_rx) = std::sync::mpsc::channel::<u64>();
        if ranged && len > 0 {
            let url = url.to_string();
            let _ = std::thread::Builder::new()
                .name("lumen-readahead".into())
                .spawn(move || readahead(url, len, tx, seek_rx));
        }

        Ok(Self {
            len,
            pos: 0,
            buf: Vec::new(),
            buf_start: u64::MAX,
            ranged,
            live,
            rx: Mutex::new(rx),
            seek_tx,
        })
    }

}

fn new_agent() -> ureq::Agent {
    ureq::Agent::config_builder()
        .timeout_global(None)
        .build()
        .into()
}

/// 后台预读：顺序取块、送进有界通道；收到 seek 请求就换起点。
fn readahead(
    url: String,
    len: u64,
    tx: SyncSender<(u64, Vec<u8>)>,
    seek_rx: Receiver<u64>,
) {
    let agent = new_agent();
    let mut pos = 0u64;
    let mut small = true; // 起点 / 刚 seek 完用小块
    loop {
        // 处理 seek（可能有多个，取最后一个）
        let mut seeked = None;
        while let Ok(p) = seek_rx.try_recv() {
            seeked = Some(p);
        }
        if let Some(p) = seeked {
            pos = p;
            small = true;
        }
        if pos >= len {
            break;
        }
        let chunk = if small { FIRST_CHUNK } else { CHUNK };
        let end = (pos + chunk - 1).min(len - 1);
        let t0 = std::time::Instant::now();
        let body = match agent
            .get(&url)
            .header("Range", &format!("bytes={pos}-{end}"))
            .call()
            .map_err(io_err)
        {
            Ok(mut resp) => {
                let mut body = Vec::with_capacity(CHUNK as usize);
                match resp.body_mut().as_reader().read_to_end(&mut body) {
                    Ok(_) => body,
                    Err(e) => {
                        log::warn!("readahead read failed: {e}");
                        break;
                    }
                }
            }
            Err(e) => {
                log::warn!("readahead request failed: {e}");
                break;
            }
        };
        let ms = t0.elapsed().as_millis();
        if ms > 250 {
            // 网络慢没关系——数据是提前取的，只要通道没见底就不影响播放
            log::info!("readahead {pos} took {ms} ms ({} KB)", body.len() / 1024);
        }
        if body.is_empty() {
            break;
        }
        let n = body.len() as u64;
        if tx.send((pos, body)).is_err() {
            break; // 播放侧已经丢了这一首
        }
        pos += n;
        small = false;
    }
}

impl Read for HttpRangeSource {
    fn read(&mut self, out: &mut [u8]) -> io::Result<usize> {
        if out.is_empty() {
            return Ok(0);
        }
        if let Some(live) = &self.live {
            // 边下边播：有数据就返回，没数据就等后台线程（转码）送来；EOF 返回 0。
            let (m, cv) = &**live;
            let mut g = m
                .lock()
                .map_err(|_| io::Error::new(io::ErrorKind::Other, "live lock"))?;
            loop {
                let pos = self.pos as usize;
                if pos < g.data.len() {
                    let n = (g.data.len() - pos).min(out.len());
                    out[..n].copy_from_slice(&g.data[pos..pos + n]);
                    self.pos += n as u64;
                    return Ok(n);
                }
                if let Some(e) = &g.err {
                    return Err(io::Error::new(io::ErrorKind::Other, e.clone()));
                }
                if g.eof {
                    return Ok(0);
                }
                g = cv
                    .wait(g)
                    .map_err(|_| io::Error::new(io::ErrorKind::Other, "live wait"))?;
            }
        }
        loop {
            let in_buf =
                self.pos >= self.buf_start && self.pos < self.buf_start + self.buf.len() as u64;
            if !in_buf {
                // 取下一块：正常情况下后台早就备好了，这里是纯内存拷贝
                let next = self.rx.lock().map(|rx| rx.recv());
                match next.unwrap_or_else(|_| Err(std::sync::mpsc::RecvError)) {
                    Ok((start, data)) => {
                        // 丢掉 seek 之前发出的过期块
                        if start + data.len() as u64 <= self.pos {
                            continue;
                        }
                        self.buf = data;
                        self.buf_start = start;
                        continue;
                    }
                    Err(_) => return Ok(0), // 预读线程结束 = EOF
                }
            }
            let off = (self.pos - self.buf_start) as usize;
            let n = (self.buf.len() - off).min(out.len());
            out[..n].copy_from_slice(&self.buf[off..off + n]);
            self.pos += n as u64;
            return Ok(n);
        }
    }
}

impl Seek for HttpRangeSource {
    fn seek(&mut self, from: SeekFrom) -> io::Result<u64> {
        let target = match from {
            SeekFrom::Start(v) => v as i64,
            SeekFrom::Current(v) => self.pos as i64 + v,
            SeekFrom::End(v) => {
                if !self.ranged || self.len == 0 {
                    return Err(io::Error::new(
                        io::ErrorKind::Unsupported,
                        "seek from end needs a known length",
                    ));
                }
                self.len as i64 + v
            }
        };
        if target < 0 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "negative seek"));
        }
        if self.live.is_some() {
            // 边下边播时长度未知：只允许在已下到 / 将下到的位置里跳，
            // 真正的等待发生在 read()（后台线程还在转码）。
            self.pos = target as u64;
            return Ok(self.pos);
        }
        self.pos = target as u64;
        // 丢掉当前块，并让后台线程改成从新位置预读
        self.buf_start = u64::MAX;
        self.buf.clear();
        let _ = self.seek_tx.send(self.pos);
        Ok(self.pos)
    }
}

fn io_err(e: impl std::fmt::Display) -> io::Error {
    io::Error::new(io::ErrorKind::Other, e.to_string())
}
