//! 缓存淘汰：音频和封面共用一个容量上限，按 LRU（最近最少使用）删整首。
//!
//! * 每次命中缓存或预取完成都会 `touch` 一次，写进 `lru.json`（id → 最后使用秒）；
//! * 超过上限就从最久没用的开始删，直到降到上限的 85%（留点余量，避免每首都触发一次）；
//! * 正在播放的曲目（`keep`）和写了半截的 `.part` 一律不动。

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};

/// 音频上限：1.5 GB
pub const AUDIO_CAP: u64 = 1536 * 1024 * 1024;
/// 封面上限：200 MB（单独预算，不会被音频挤掉，也不会反过来挤音频）
pub const COVER_CAP: u64 = 200 * 1024 * 1024;
/// 删到上限的这个比例，避免频繁触发
const TRIM_TO: f32 = 0.85;

fn lru_path(dir: &Path) -> PathBuf {
    dir.join("lru.json")
}

/// 内存中的 LRU 覆盖层：`touch` 只改内存，落盘推迟到 `trim`。
///
/// 之前 `touch` 每次命中/预取都同步读+写一次 `lru.json`，而 `has_cached` 这条路径
/// 跑在 UI 线程——每放一首缓存里的歌，UI 线程都要做一次磁盘读写。
/// 现在改成「内存里记一笔，`trim` 时合并落盘」，UI 线程彻底不碰磁盘。
///
/// 覆盖层按**目录**分桶（桌面临时目录 / Android 私有目录互不串扰）。
fn overlay() -> &'static Mutex<HashMap<PathBuf, HashMap<String, u64>>> {
    static O: OnceLock<Mutex<HashMap<PathBuf, HashMap<String, u64>>>> = OnceLock::new();
    O.get_or_init(|| Mutex::new(HashMap::new()))
}

fn load_lru(dir: &Path) -> HashMap<String, u64> {
    let mut map: HashMap<String, u64> = std::fs::read_to_string(lru_path(dir))
        .ok()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_default();
    // 合并尚未落盘的内存覆盖层（只取本目录的）
    if let Ok(o) = overlay().lock() {
        if let Some(inner) = o.get(dir) {
            for (k, v) in inner.iter() {
                map.insert(k.clone(), *v);
            }
        }
    }
    map
}

fn save_lru(dir: &Path, map: &HashMap<String, u64>) {
    if let Ok(t) = serde_json::to_string(map) {
        let _ = std::fs::write(lru_path(dir), t);
    }
    // 落盘后清掉本目录的内存覆盖层，避免下次 load 又叠一遍
    if let Ok(mut o) = overlay().lock() {
        o.remove(dir);
    }
}

fn now() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// 记一次使用（缓存命中、预取完成、播放开始都算）。
/// 只写内存覆盖层，不落盘——落盘在 `trim` 里统一做。
pub fn touch(dir: &Path, key: &str) {
    if dir.as_os_str().is_empty() {
        return;
    }
    if let Ok(mut o) = overlay().lock() {
        o.entry(dir.to_path_buf())
            .or_default()
            .insert(key.to_string(), now());
    }
}

/// 缓存占用：音频与封面分开统计
#[derive(Default, Clone, Copy)]
pub struct Usage {
    pub audio: u64,
    pub covers: u64,
    pub files: usize,
}

impl Usage {
    pub fn total(&self) -> u64 {
        self.audio + self.covers
    }
}

pub fn usage(dir: &Path) -> Usage {
    let mut u = Usage::default();
    if let Ok(rd) = std::fs::read_dir(dir) {
        for e in rd.flatten() {
            let p = e.path();
            if p.extension().map(|x| x == "part").unwrap_or(false) {
                continue;
            }
            if p.file_name().map(|x| x == "lru.json").unwrap_or(false) {
                continue;
            }
            if let Ok(m) = e.metadata() {
                if m.is_file() {
                    if is_cover(&p) {
                        u.covers += m.len();
                    } else {
                        u.audio += m.len();
                    }
                    u.files += 1;
                }
            }
        }
    }
    u
}

fn is_cover(p: &Path) -> bool {
    p.file_name()
        .map(|n| n.to_string_lossy().starts_with("cover_"))
        .unwrap_or(false)
}

/// 两个池子分别按各自上限淘汰，返回释放的字节数。`keep` 里的键不会被删。
pub fn trim(dir: &Path, keep: &[String]) -> u64 {
    if !dir.exists() {
        return 0;
    }
    let mut freed = 0;
    // 先音频、后封面：各自独立预算，互不影响
    freed += trim_pool(dir, AUDIO_CAP, false, keep);
    freed += trim_pool(dir, COVER_CAP, true, keep);
    freed
}

fn trim_pool(dir: &Path, cap: u64, covers: bool, keep: &[String]) -> u64 {
    // 只读一次 LRU（含内存覆盖层），不要在下面的文件循环里反复 load_lru——
    // 那会变成"每个缓存文件读一遍 lru.json + 解析一遍 JSON"。
    let lru = load_lru(dir);
    let mut entries: Vec<(u64, PathBuf, String, u64)> = Vec::new();
    let mut total = 0u64;
    if let Ok(rd) = std::fs::read_dir(dir) {
        for e in rd.flatten() {
            let p = e.path();
            if !p.is_file() || is_cover(&p) != covers {
                continue;
            }
            if p.extension().map(|x| x == "part").unwrap_or(false) {
                continue;
            }
            if p.file_name().map(|x| x == "lru.json").unwrap_or(false) {
                continue;
            }
            let stem = p
                .file_stem()
                .map(|s| s.to_string_lossy().to_string())
                .unwrap_or_default();
            if keep.iter().any(|k| *k == stem) {
                continue;
            }
            let Ok(m) = std::fs::metadata(&p) else { continue };
            let len = m.len();
            let used = lru
                .get(&stem)
                .copied()
                .or_else(|| {
                    m.modified()
                        .ok()
                        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                        .map(|d| d.as_secs())
                })
                .unwrap_or(0);
            total += len;
            entries.push((used, p, stem, len));
        }
    }
    if total <= cap {
        return 0;
    }
    let target = (cap as f32 * TRIM_TO) as u64;
    entries.sort_by_key(|(used, _, _, _)| *used);
    let mut map = lru;
    let mut freed = 0u64;
    for (_, path, stem, len) in entries {
        if total <= target {
            break;
        }
        if std::fs::remove_file(&path).is_ok() {
            total = total.saturating_sub(len);
            freed += len;
            map.remove(&stem);
            log::info!(
                "cache evict {}: {:?} ({} KB)",
                if covers { "cover" } else { "audio" },
                path.file_name(),
                len / 1024
            );
        }
    }
    save_lru(dir, &map);
    if freed > 0 {
        log::info!(
            "cache trimmed {}: freed {} MB, now {} MB / cap {} MB",
            if covers { "cover" } else { "audio" },
            freed >> 20,
            total >> 20,
            cap >> 20
        );
    }
    freed
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tempdir(name: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!("lumen_cache_test_{name}"));
        std::fs::create_dir_all(&d).unwrap();
        d
    }

    fn cleanup(d: &Path) {
        let _ = std::fs::remove_dir_all(d);
    }

    #[test]
    fn usage_splits_audio_and_cover() {
        let d = tempdir("usage");
        std::fs::write(d.join("song1.mp3"), vec![0u8; 100]).unwrap();
        std::fs::write(d.join("cover_x_400.jpg"), vec![0u8; 200]).unwrap();
        std::fs::write(d.join("half.part"), vec![0u8; 50]).unwrap(); // .part 不计入
        let u = usage(&d);
        assert_eq!(u.audio, 100);
        assert_eq!(u.covers, 200);
        assert_eq!(u.files, 2);
        cleanup(&d);
    }

    #[test]
    fn is_cover_by_prefix() {
        assert!(is_cover(Path::new("cover_al-1_400.jpg")));
        assert!(is_cover(Path::new("cover_al-1_400.png"))); // 任意扩展名都算封面
        assert!(!is_cover(Path::new("al-1.mp3")));
    }

    #[test]
    fn trim_pool_evicts_oldest_first_and_keeps_protected() {
        let d = tempdir("trim");
        // 3 个音频文件各 100 字节
        for name in ["a", "b", "c"] {
            std::fs::write(d.join(format!("{name}.mp3")), vec![0u8; 100]).unwrap();
        }
        // lru：a 最新(300)、b 中间(200)、c 最旧(100)
        let lru: HashMap<String, u64> = [
            ("a".to_string(), 300u64),
            ("b".to_string(), 200u64),
            ("c".to_string(), 100u64),
        ]
        .into_iter()
        .collect();
        std::fs::write(d.join("lru.json"), serde_json::to_string(&lru).unwrap()).unwrap();

        // cap=150 → target=127，300 字节要删到 <=127：先删 c(100)、再删 b(100)
        let freed = trim_pool(&d, 150, false, &[]);
        assert!(freed >= 100, "至少淘汰一个");
        assert!(!d.join("c.mp3").exists(), "最旧的 c 应先被删");
        assert!(d.join("a.mp3").exists(), "最新的 a 应保留");
        cleanup(&d);
    }

    #[test]
    fn trim_pool_respects_keep() {
        let d = tempdir("keep");
        for name in ["a", "b", "c"] {
            std::fs::write(d.join(format!("{name}.mp3")), vec![0u8; 100]).unwrap();
        }
        // a 最旧(100)、b(200)、c 最新(300)
        let lru: HashMap<String, u64> = [
            ("a".to_string(), 100u64),
            ("b".to_string(), 200u64),
            ("c".to_string(), 300u64),
        ]
        .into_iter()
        .collect();
        std::fs::write(d.join("lru.json"), serde_json::to_string(&lru).unwrap()).unwrap();

        // keep 保护最旧的 a：即使 a 最旧也不该被删
        let freed = trim_pool(&d, 150, false, &["a".to_string()]);
        assert!(freed > 0, "应淘汰未保护的文件");
        assert!(d.join("a.mp3").exists(), "被 keep 的 a 不该删");
        // b、c 中至少有一个被删（凑到 target 127 以下）
        assert!(!d.join("b.mp3").exists() || !d.join("c.mp3").exists());
        cleanup(&d);
    }
}

