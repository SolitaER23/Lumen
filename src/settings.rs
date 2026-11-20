//! 服务器连接设置：持久化到应用私有目录，不再硬编码在源码里。
//!
//! Android 上写到 `context.getFilesDir()/settings.json`（JNI 取路径），
//! 桌面上写到系统临时目录下的 `lumen/settings.json`。
//!
//! ## 密码加密
//!
//! `password` 字段用 AES-256-GCM 加密后存成 base64（字段名 `password_enc`），
//! 密钥由系统熵源（`getrandom`）生成、存到同目录的 `key.bin`。这样 settings.json
//! 里不再有密码明文（比裸存前进一大步）；`base` / `user` 非敏感，仍明文。
//!
//! 说明：密钥与密文同处应用私有目录，仍无法防 root 设备；要做到真正的防 root
//! 需要 Android Keystore 硬件密钥，这里先提供"不裸奔"这一层，避免密码一眼可见。
//!
//! 向后兼容：旧的 `password` 明文字段仍能读取，读入后下一次 `save` 自动迁移为加密。

use std::path::PathBuf;

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Key, Nonce};
use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;

#[derive(Clone, Default, serde::Serialize, serde::Deserialize)]
pub struct Settings {
    #[serde(default)]
    pub base: String,
    #[serde(default)]
    pub user: String,
    #[serde(default)]
    pub password: String,
}

/// 落盘用的结构：内存里 `password` 是明文，磁盘上只写 `password_enc`。
/// `password` 字段保留用于读取旧版明文（迁移用）。
#[derive(serde::Serialize, serde::Deserialize, Default)]
struct Stored {
    #[serde(default)]
    base: String,
    #[serde(default)]
    user: String,
    /// 旧版明文（仅向后兼容读取）
    #[serde(default)]
    password: String,
    /// 新版加密（base64：12 字节 nonce || 密文）
    #[serde(default)]
    password_enc: String,
}

const NONCE_LEN: usize = 12;

impl Settings {
    pub fn load() -> Self {
        let Some(p) = path() else { return Self::default() };
        let Ok(text) = std::fs::read_to_string(&p) else {
            return Self::default();
        };
        let Ok(stored) = serde_json::from_str::<Stored>(&text) else {
            return Self::default();
        };
        let password = if !stored.password_enc.is_empty() {
            decrypt(&stored.password_enc).unwrap_or_default()
        } else {
            stored.password // 旧明文，下次 save 会迁移
        };
        Self {
            base: stored.base,
            user: stored.user,
            password,
        }
    }

    pub fn save(&self) -> Result<(), String> {
        let p = path().ok_or("拿不到设置目录")?;
        if let Some(dir) = p.parent() {
            std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        let stored = Stored {
            base: self.base.clone(),
            user: self.user.clone(),
            password: String::new(),
            password_enc: if self.password.is_empty() {
                String::new()
            } else {
                encrypt(&self.password)?
            },
        };
        let text = serde_json::to_string_pretty(&stored).map_err(|e| e.to_string())?;
        std::fs::write(&p, text).map_err(|e| e.to_string())?;
        log::info!("settings saved to {:?}", p);
        Ok(())
    }

    pub fn is_complete(&self) -> bool {
        !self.base.trim().is_empty() && !self.user.trim().is_empty()
    }

    /// 规整用户输入：去空白、补协议头、去尾部斜杠
    pub fn normalize(mut self) -> Self {
        self.base = self.base.trim().to_string();
        if !self.base.is_empty() && !self.base.starts_with("http://") && !self.base.starts_with("https://")
        {
            self.base = format!("http://{}", self.base);
        }
        self.base = self.base.trim_end_matches('/').to_string();
        self.user = self.user.trim().to_string();
        self.password = self.password.trim().to_string();
        self
    }
}

/// 取（或生成）加密密钥：应用私有目录下的 key.bin，32 字节，系统熵源生成。
fn key() -> Result<[u8; 32], String> {
    let dir = path()
        .and_then(|p| p.parent().map(|d| d.to_path_buf()))
        .ok_or("拿不到设置目录")?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let kp = dir.join("key.bin");
    if let Ok(bytes) = std::fs::read(&kp) {
        if bytes.len() == 32 {
            let mut k = [0u8; 32];
            k.copy_from_slice(&bytes);
            return Ok(k);
        }
    }
    // 没有（或损坏）就重新生成一个：密钥丢失只意味着下次要重输密码，不影响别的
    let mut k = [0u8; 32];
    getrandom::getrandom(&mut k).map_err(|e| format!("随机源不可用：{e}"))?;
    std::fs::write(&kp, k).map_err(|e| e.to_string())?;
    Ok(k)
}

fn encrypt(plain: &str) -> Result<String, String> {
    encrypt_with(&key()?, plain)
}

fn decrypt(enc: &str) -> Result<String, String> {
    decrypt_with(&key()?, enc)
}

/// 核心加密（与密钥解耦，便于测试用固定密钥、避免并发写 key.bin 竞态）
fn encrypt_with(k: &[u8; 32], plain: &str) -> Result<String, String> {
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(k));
    let mut nonce = [0u8; NONCE_LEN];
    getrandom::getrandom(&mut nonce).map_err(|e| format!("随机源不可用：{e}"))?;
    let ct = cipher
        .encrypt(Nonce::from_slice(&nonce), plain.as_bytes())
        .map_err(|_| "加密失败".to_string())?;
    let mut out = Vec::with_capacity(NONCE_LEN + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    Ok(B64.encode(out))
}

/// 核心解密（与密钥解耦）
fn decrypt_with(k: &[u8; 32], enc: &str) -> Result<String, String> {
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(k));
    let data = B64.decode(enc).map_err(|e| format!("base64 解析失败：{e}"))?;
    if data.len() <= NONCE_LEN {
        return Err("密文过短".into());
    }
    let (nonce, ct) = data.split_at(NONCE_LEN);
    let pt = cipher
        .decrypt(Nonce::from_slice(nonce), ct)
        .map_err(|_| "解密失败（密钥不匹配）".to_string())?;
    String::from_utf8(pt).map_err(|e| e.to_string())
}

fn path() -> Option<PathBuf> {
    #[cfg(target_os = "android")]
    {
        if let Some(dir) = crate::safe_area::files_dir() {
            return Some(PathBuf::from(dir).join("settings.json"));
        }
    }
    Some(std::env::temp_dir().join("lumen").join("settings.json"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_key() -> [u8; 32] {
        [7u8; 32]
    }

    #[test]
    fn normalize_adds_http_and_trims() {
        let s = Settings {
            base: "192.0.2.1:4533/".into(),
            user: " bob ".into(),
            password: " pw ".into(),
        }
        .normalize();
        assert_eq!(s.base, "http://192.0.2.1:4533");
        assert_eq!(s.user, "bob");
        assert_eq!(s.password, "pw");
    }

    #[test]
    fn normalize_keeps_https() {
        let s = Settings {
            base: "https://x.com/".into(),
            user: "u".into(),
            password: "p".into(),
        }
        .normalize();
        assert_eq!(s.base, "https://x.com");
    }

    #[test]
    fn encrypt_decrypt_roundtrip() {
        let k = test_key();
        let plain = "s3cr3t-密码-🔐";
        let enc = encrypt_with(&k, plain).expect("encrypt");
        assert!(!enc.contains(plain), "密文不应包含明文");
        assert_eq!(decrypt_with(&k, &enc).expect("decrypt"), plain);
    }

    #[test]
    fn encrypt_is_nondeterministic() {
        let k = test_key();
        // 每次 nonce 都不同，同一明文两次加密结果应不同（语义安全）
        let a = encrypt_with(&k, "same").expect("a");
        let b = encrypt_with(&k, "same").expect("b");
        assert_ne!(a, b);
        assert_eq!(decrypt_with(&k, &a).unwrap(), "same");
        assert_eq!(decrypt_with(&k, &b).unwrap(), "same");
    }

    #[test]
    fn decrypt_rejects_corrupt() {
        let k = test_key();
        assert!(decrypt_with(&k, "not-base64!!").is_err());
        assert!(decrypt_with(&k, &B64.encode([0u8; 4])).is_err()); // 太短
    }

    #[test]
    fn decrypt_fails_with_wrong_key() {
        let enc = encrypt_with(&[1u8; 32], "secret").unwrap();
        assert!(decrypt_with(&[2u8; 32], &enc).is_err());
    }
}
