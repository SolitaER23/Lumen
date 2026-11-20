//! 音频引擎：rodio 0.23 负责输出（Android 上 cpal → AAudio），symphonia 负责解码。
//!
//! * 输出流 + Player 只在**专用音频线程**里创建和使用（cpal 的 Stream 在部分平台不是 Send）；
//! * UI 线程通过 mpsc 发命令，绝不阻塞渲染；
//! * 播放位置由音频线程周期写进 `AtomicU64`，UI 直接读。
//!
//! rodio 0.23 的 API：`DeviceSinkBuilder::open_default_sink()` → `MixerDeviceSink`，
//! 再 `Player::connect_new(sink.mixer())`；没有旧版的 `OutputStream` / `Sink`。

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::Arc;
use std::time::Duration;

use rodio::{DeviceSinkBuilder, Player, Source};

enum Cmd {
    /// 本地缓存文件；解不了时用 `fallback`（转码流）兜底。
    /// 缓存统一存成 `<id>.mp3`，但内容可能是任意容器（FLAC/M4A…），
    /// 甚至可能是本机解不了的编码（ALAC/DSD）——这时必须退回服务端转码。
    PlayFile {
        path: PathBuf,
        fallback: Option<String>,
    },
    /// 流式播放：HTTP Range 源 + 起始位置（fallback 用于原编码解不了时退回转码流）
    PlayUrl {
        url: String,
        fallback: Option<String>,
        start: f32,
    },
    Seek(f32),
    Tone(f32, f32),
    Pause,
    Resume,
    Stop,
    Volume(f32),
}

pub struct Audio {
    tx: Sender<Cmd>,
    pos_ms: Arc<AtomicU64>,
    playing: Arc<AtomicBool>,
    /// 目标音量（以 f32 位模式存进原子量，音频线程读）
    vol_bits: Arc<std::sync::atomic::AtomicU32>,
    volume: f32,
}

impl Audio {
    /// 没有可用输出设备时返回 None，UI 仍然能跑（只是没声音）
    pub fn new() -> Option<Self> {
        let pos_ms = Arc::new(AtomicU64::new(0));
        let playing = Arc::new(AtomicBool::new(false));
        let (tx, rx) = channel::<Cmd>();

        let vol_bits = Arc::new(std::sync::atomic::AtomicU32::new(0.72f32.to_bits()));
        let pos2 = pos_ms.clone();
        let play2 = playing.clone();
        let vol2 = vol_bits.clone();
        let spawned = std::thread::Builder::new()
            .name("lumen-audio".into())
            .spawn(move || audio_thread(rx, pos2, play2, vol2))
            .is_ok();
        if !spawned {
            log::error!("audio thread spawn failed");
            return None;
        }
        Some(Self {
            tx,
            pos_ms,
            playing,
            vol_bits,
            volume: 0.72,
        })
    }

    pub fn play_file(&mut self, path: PathBuf) {
        self.play_file_with_fallback(path, None);
    }

    /// 播放本地缓存；解不了（例如缓存下来的是 ALAC，symphonia 没有解码器）时，
    /// 自动退回 `fallback`（服务端转码流），而不是静音卡住。
    pub fn play_file_with_fallback(&mut self, path: PathBuf, fallback: Option<String>) {
        let _ = self.tx.send(Cmd::PlayFile { path, fallback });
    }

    /// 自检音：没有服务器时用来验证音频通路
    pub fn play_tone(&mut self, freq: f32, secs: f32) {
        let _ = self.tx.send(Cmd::Tone(freq, secs));
    }

    /// 流式播放（HTTP Range 可 Seek）
    pub fn play_url(&mut self, url: &str, start_secs: f32) {
        self.play_url_fallback(url, None, start_secs)
    }

    /// 先试原始流，解不了再退回兜底流（转码）
    pub fn play_url_fallback(&mut self, url: &str, fallback: Option<String>, start_secs: f32) {
        let _ = self.tx.send(Cmd::PlayUrl {
            url: url.to_string(),
            fallback,
            start: start_secs,
        });
    }

    /// 拖拽进度条
    pub fn seek(&mut self, secs: f32) {
        let _ = self.tx.send(Cmd::Seek(secs));
    }

    pub fn pause(&mut self) {
        let _ = self.tx.send(Cmd::Pause);
    }

    pub fn resume(&mut self) {
        let _ = self.tx.send(Cmd::Resume);
    }

    pub fn stop(&mut self) {
        let _ = self.tx.send(Cmd::Stop);
    }

    pub fn set_volume(&mut self, v: f32) {
        self.volume = v.clamp(0.0, 1.0);
        self.vol_bits
            .store(self.volume.to_bits(), std::sync::atomic::Ordering::Relaxed);
        let _ = self.tx.send(Cmd::Volume(self.volume));
    }

    pub fn position_secs(&self) -> f32 {
        self.pos_ms.load(Ordering::Relaxed) as f32 / 1000.0
    }

    pub fn is_playing(&self) -> bool {
        self.playing.load(Ordering::Relaxed)
    }
}

/// 依次尝试若干个流地址（原文件 → 转码流），成功返回 true。
///
/// 打开/解码都在音频线程里做——`HttpRangeSource::open` 是同步网络请求，
/// 所以调用点之后才 append；失败时返回 false，由调用方决定是否提示。
fn play_stream(player: &Player, playing: &AtomicBool, urls: Vec<String>, start: f32) -> bool {
    let t0 = std::time::Instant::now();
    for (i, u) in urls.iter().enumerate() {
        match crate::range_source::HttpRangeSource::open(u) {
            Ok(src) => {
                log::info!(
                    "stream[{}]: ranged={} len={} ({} ms to open)",
                    i,
                    src.ranged,
                    src.len,
                    t0.elapsed().as_millis()
                );
                match rodio::Decoder::new(std::io::BufReader::new(src)) {
                    Ok(dec) => {
                        player.append(dec);
                        player.play();
                        playing.store(true, Ordering::Relaxed);
                        if start > 0.0 {
                            let _ = player.try_seek(Duration::from_secs_f32(start));
                        }
                        log::info!("stream[{}] playing ({} ms total)", i, t0.elapsed().as_millis());
                        return true;
                    }
                    Err(e) => log::warn!("stream[{i}] decode failed: {e}"),
                }
            }
            Err(e) => log::warn!("stream[{i}] open failed: {e}"),
        }
    }
    false
}

fn audio_thread(    rx: Receiver<Cmd>,
    pos: Arc<AtomicU64>,
    playing: Arc<AtomicBool>,
    vol_bits: Arc<std::sync::atomic::AtomicU32>,
) {
    let sink = match DeviceSinkBuilder::open_default_sink() {
        Ok(s) => s,
        Err(e) => {
            log::error!("no audio output: {e}");
            return;
        }
    };
    let player = Player::connect_new(sink.mixer());
    player.set_volume(0.72);
    log::info!(
        "audio output ready: {} Hz, {} ch",
        sink.config().sample_rate().get(),
        sink.config().channel_count().get()
    );

    // seek 后的音量淡入：(结束时间, 目标音量)。非阻塞——在 recv_timeout 循环里
    // 逐帧恢复，避免之前在音频线程里 sleep ~144ms 导致的欠载。
    let mut fade: Option<(std::time::Instant, f32)> = None;

    loop {
        match rx.recv_timeout(Duration::from_millis(60)) {
            Ok(Cmd::PlayFile { path, fallback }) => {
                player.stop();
                let mut ok = false;
                match std::fs::File::open(&path) {
                    Ok(f) => match rodio::Decoder::try_from(f) {
                        Ok(dec) => {
                            player.append(dec);
                            player.play();
                            playing.store(true, Ordering::Relaxed);
                            log::info!("playing cached: {:?}", path);
                            ok = true;
                        }
                        Err(e) => log::warn!("cache decode failed ({path:?}): {e}"),
                    },
                    Err(e) => log::warn!("open failed ({path:?}): {e}"),
                }
                if !ok {
                    // 缓存里是本机解不了的编码（ALAC / DSD 之类）：退回服务端转码流。
                    // 之前这里只打一条 error 就结束 → UI 显示"正在播放"但永远 0:00。
                    playing.store(false, Ordering::Relaxed);
                    if let Some(u) = fallback {
                        log::info!("cache unplayable, fallback to transcoded stream: {path:?}");
                        if !play_stream(&player, &playing, vec![u], 0.0) {
                            log::error!("fallback stream also failed: {path:?}");
                        }
                    } else {
                        log::error!("no playable source: {path:?}");
                    }
                }
            }
            Ok(Cmd::PlayUrl {
                url,
                fallback,
                start,
            }) => {
                player.stop();
                // 依次尝试：原文件 → 转码流
                let mut urls = vec![url];
                if let Some(f) = fallback {
                    urls.push(f);
                }
                if !play_stream(&player, &playing, urls, start) {
                    log::error!("no playable stream");
                    playing.store(false, Ordering::Relaxed);
                }
            }
            Ok(Cmd::Seek(secs)) => {
                // 跳变处快速淡入，避免咔哒声——但不 sleep 阻塞音频线程，
                // 音量在后续 recv_timeout 循环里逐帧恢复（约 120ms 淡入）。
                let target = f32::from_bits(vol_bits.load(Ordering::Relaxed));
                match player.try_seek(Duration::from_secs_f32(secs.max(0.0))) {
                    Ok(()) => log::info!("seek -> {secs:.1}s"),
                    Err(e) => log::warn!("seek failed: {e}"),
                }
                player.set_volume(0.0);
                fade = Some((std::time::Instant::now() + Duration::from_millis(120), target));
            }
            Ok(Cmd::Tone(freq, secs)) => {
                player.stop();
                let tone = rodio::source::SineWave::new(freq)
                    .take_duration(Duration::from_secs_f32(secs))
                    .amplify(0.18)
                    .fade_in(Duration::from_millis(12))
                    .fade_out(Duration::from_millis(150));
                player.append(tone);
                player.play();
                playing.store(true, Ordering::Relaxed);
                log::info!("tone {freq} Hz for {secs}s");
            }
            Ok(Cmd::Pause) => {
                player.pause();
                playing.store(false, Ordering::Relaxed);
            }
            Ok(Cmd::Resume) => {
                player.play();
                playing.store(true, Ordering::Relaxed);
            }
            Ok(Cmd::Stop) => {
                player.stop();
                playing.store(false, Ordering::Relaxed);
                pos.store(0, Ordering::Relaxed);
            }
            Ok(Cmd::Volume(v)) => player.set_volume(v),
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => break,
        }
        // seek 后的淡入：逐帧恢复音量，不阻塞
        if let Some((until, target)) = fade {
            let now = std::time::Instant::now();
            if now >= until {
                player.set_volume(target);
                fade = None;
            } else {
                let remaining = until.duration_since(now).as_secs_f32().max(0.0);
                let progress = 1.0 - remaining / 0.120;
                player.set_volume(target * progress.clamp(0.0, 1.0));
            }
        }
        pos.store(player.get_pos().as_millis() as u64, Ordering::Relaxed);
        if player.empty() && playing.load(Ordering::Relaxed) {
            playing.store(false, Ordering::Relaxed);
        }
    }
}
