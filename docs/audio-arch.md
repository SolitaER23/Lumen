# 音频与曲库架构：OpenSubsonic + Rodio/Symphonia

> 结论先说：这套组合在本项目里**已经跑通**。真机（Redmi Pad, Android 16 / arm64）上
> 音频线程成功打开 AAudio 流（48000 Hz / 2 ch），并播放了自检音。
> 关键前提只有一个：**链接平台必须是 API 26+**，否则 NDK 的 sysroot 里没有 `libaaudio.so`。

---

## 1. 选型与理由

| 层 | 选择 | 理由 |
| --- | --- | --- |
| 协议 | **OpenSubsonic**（Subsonic API 的超集） | Navidrome / Gonic / Airsonic 全都支持；JSON + token 认证，端点少而稳定 |
| 解码 | **Symphonia**（经 rodio 的 `symphonia-*` feature） | 纯 Rust：FLAC / MP3 / Vorbis / AAC / WAV / MP4，无需 FFmpeg，交叉编译零 C 依赖 |
| 输出 | **Rodio → Cpal → AAudio** | cpal 0.18 自带 Android 后端（`ndk` / `ndk-context` / `jni` / `audio_thread_priority`），**不需要 oboe，也不需要 CMake** |
| HTTP | **ureq 3（无 TLS）** | 局域网 Navidrome 一般走 `http://ip:4533`；见 §7 的 HTTPS 方案 |
| 缓存 | 应用 `getCacheDir()`（JNI 取得） | 音频整曲落盘后解码，天然支持离线复用 |

**为什么不用 reqwest**：reqwest 0.13 的 `rustls` feature 会拉 `aws-lc-sys`，它需要 CMake 且在 Android 上容易编不过。
**为什么不用 oboe-rs**：cpal 已经内置了 AAudio 后端，多一层封装没有收益。

---

## 2. 模块划分

```
src/
├── audio.rs        音频引擎：输出流 + Player，全部活在一个专用线程里
├── range_source.rs 可 Seek 的 HTTP Range 源（后台线程预读，播放侧只读内存）
├── subsonic.rs     OpenSubsonic 客户端：ping / getAlbumList2 / getAlbum / stream / getCoverArt / getLyricsBySongId / scrobble
├── remote.rs       后台线程编排：元数据、封面下载+解码、歌词、scrobble、预取
├── cache.rs        音频/封面两级磁盘 LRU
├── model.rs        UI 模型（Album/Track/Lyrics/Artwork）
└── app.rs          只发命令、读原子量，不持有任何音频对象
```

### audio.rs

```rust
pub struct Audio { tx: Sender<Cmd>, pos_ms: Arc<AtomicU64>, playing: Arc<AtomicBool> }
enum Cmd { PlayFile(PathBuf), PlayUrl { url, fallback, seek }, Seek(f32), Pause, Resume, Stop, Volume(f32) }
```

* **线程模型**：`Audio::new()` 起一个 `lumen-audio` 线程，线程内
  `DeviceSinkBuilder::open_default_sink()` → `MixerDeviceSink`，再
  `Player::connect_new(sink.mixer())`。`MixerDeviceSink` 与 `Player` **都不出线程**。
* **UI 侧零阻塞**：只 `send`，以及读 `AtomicU64`（播放位置）和 `AtomicBool`（是否在放）。
* **位置回报**：线程用 `recv_timeout(60ms)` 轮询，每轮把 `player.get_pos()` 写进原子量，
  顺带检测 `player.empty()` 自动把 `playing` 置否（一首放完）。
* **rodio 0.23 的 API 与旧版不同**：没有 `OutputStream` / `Sink`，是
  `DeviceSinkBuilder::open_default_sink()` + `Player`；`set_volume`/`pause`/`play`/`stop`/`get_pos`/`empty` 都在 `Player` 上。
* **流式播放**：`PlayUrl` 走 `range_source::HttpRangeSource`（后台线程按块预读），
  播放侧只读内存、**绝不做网络请求**——否则解码线程被 HTTP 阻塞 = 输出欠载 = 听感"每隔一段卡一下"。

### subsonic.rs

```rust
Client::new(Config { base, user, password })
client.ping()?                          // 连通性
client.album_list(offset, size)?        // getAlbumList2?type=alphabeticalByName（分页 500/页循环）
client.album(id)?                       // getAlbum → (Album, Vec<Song>)
client.stream_url(&song_id)?            // 原始流地址（支持 Range，不走转码）
client.fetch_cover(cover_id, 400)?      // getCoverArt → cache/cover_*（按 Content-Type 存 jpg/png/webp）
client.lyrics(&song_id)?                // getLyricsBySongId → model::Lyrics（LRC 兜底）
client.scrobble(&song_id, submission)?  // 上报正在播放/播放完成
```

认证不传明文：`t = md5(password + salt)`、`s = salt`（系统熵源 getrandom 生成，不可预测）、
`u = 用户名`、`v = 1.16.1`、`c = Lumen`、`f = json`。

---

## 3. 数据流

```
启动
   └─ album_list() → Vec<Album> → 填充 model::Album；封面由 art worker 下载到磁盘，
      urgent 线程按「视线内」解码成 ColorImage + 送 UI 上纹理
点一张砖（瓷砖墙）
   ├─ 进专辑页（nav 弹簧翻页）
   ├─ album(id) → Vec<Song>（曲名字形预热完再渲染列表）
   └─ 点曲目 → stream_url → rodio（range_source 预读）→ AAudio
播放中
   ├─ 每帧：audio.position_secs() 驱动进度条（UI 不再自己累加时间）
   ├─ 切歌：PlayUrl（seek 后 350ms 内不重建位置，等引擎切换完）
   └─ 返回：播放不中断，各专辑播放状态独立（playing_album 与 selected 解耦）
```

要点：**UI 的时间轴从音频引擎读**，这样暂停、seek、网络卡顿都不会和画面脱节；
**播放侧绝不做网络请求**，网络慢只会吃掉预读缓冲，不会卡住输出。

---

## 4. 缓存与离线

* 目录：Android `context.getCacheDir()/`（JNI 取绝对路径），桌面退回 `%TEMP%/lumen`。
* 键：音频 `<songId>.<suffix>`、封面 `cover_<coverId>_<size>.jpg`。
* 写入用 `<id>.part` 再 `rename`，避免半截文件被当成缓存命中。
* 命中判定：文件存在且 > 1 KB。
* 一首歌约 5 MB（320 kbps MP3），系统会在低存储时自己清缓存，不需要我们管淘汰。

---

## 5. 播放语义

| 动作 | 实现 |
| --- | --- |
| 播放 / 暂停 | `Player::play/pause`；恢复时补报 scrobble `submission=false` |
| 上一首 / 下一首 | `jump_track(±1)`，基于 `cur_track`（明确状态，不从 pos_secs 反推） |
| 切歌 / seek | `Cmd::PlayUrl` / `Cmd::Seek` 是异步的，切换后 350ms 内不重建位置（`stream_switch_at` 缓冲） |
| 进度 | 引擎回报位置（`has_audio`），只有 demo 假时间轴才用自由时钟累加 |
| 一首放完 | `pos >= start+len` → `advance_track()` 自动下一首 + 对上一首报 scrobble `submission=true` |
| 切换专辑 | 不中断播放：`playing_album` 与 `selected` 解耦，各专辑独立 |

`try_seek` 只对**已落盘**的文件可靠；流式 seek 通过 `range_source` 的通道通知预读线程换起点，
播放侧只改偏移、丢当前块，不需要把整首歌下载完。

---

## 6. 实测记录（本机）

```
I app::audio: audio output ready: 48000 Hz, 2 ch
I app::audio: tone 659.25 Hz for 0.8s
I AAudio    : AAudioStreamBuilder_openStream() returns 0 = AAUDIO_OK for s#1
```

踩过的两个坑，已写进构建脚本：

1. **`-laaudio` 找不到** → cargo-ndk 默认按 API 21 链接；`build.ps1` 现在显式 `-P 26`。
2. **rodio 默认 feature 被关掉** → `default-features = false` 会连 `playback`（`= dep:cpal`）一起关掉，
   必须显式写 `features = ["playback", ...]`，否则 `Sink`/`Player`/`DeviceSinkBuilder` 全都不存在。

---

## 7. 待办与风险

1. **HTTPS**：目前 `ureq` 不带 TLS。要用 `https://` 有两种加法：
   * `ureq = { version = "3", features = ["rustls"] }` + `rustls = { default-features = false, features = ["ring", "std", "tls12"] }`
     （ring 用 NDK clang 就能编，**不要**让 rustls 走默认的 aws-lc，那需要 CMake）；
   * 或者局域网内就保持 http，公网访问交给反向代理 + 应用内不做 TLS。
2. **错误面**：服务器不可达 / 认证失败 / 曲目 404，需要在界面上给出可读的状态（现在只有日志）。
3. **后台播放**：Android 上要让音频在切后台继续，需要 `MediaSessionService` 或前台服务；
   GameActivity 应用在后台被冻结时音频线程会停。这块要么加 Java 侧服务，要么接受"仅前台播放"。


## 8. Seek：为什么必须自己实现 `Read + Seek`

**问题**：Symphonia 在探测容器、跳过 ID3、建立 FLAC seektable 时会 `seek`；rodio 的
`Decoder` 签名也要求 `Read + Seek`。如果把 HTTP 响应体（只读、不可 Seek）直接塞进去，
部分格式会识别失败甚至 panic。纯顺序播放往往看不出问题，一拖进度条就暴露。

**做法**（`src/range_source.rs`）：

1. 打开时发 `Range: bytes=0-0` 探测：
   * 返回 **206** → 服务端支持 Range，从 `Content-Range: bytes 0-0/<总长>` 拿到文件长度；
   * 返回 200 → 不支持，`ranged = false`，只能顺序读（向后 seek 会报错，此时退回整曲下载）。
2. **后台线程预读**：网络线程按 1 MB 分块一路往前取，通过有界通道最多缓冲 6 块（≈6 MB，
   320 kbps 下约 2.5 分钟音频）；
3. 播放侧只从内存取数据，**永远不做网络请求**；
4. `seek` 只改偏移、丢掉当前块，并通知预读线程换起点——拖进度条就是一次新的 Range 请求，
   不需要把整首歌下载完。

> **为什么必须预读**：解码是在音频线程里跑的。原来 1 MB 块由解码侧同步请求，实测每次
> **145 ms**，1 MB ≈ 30 秒音频——听感就是"每隔半分钟左右卡一下"。把网络搬到独立线程后，
> 播放侧只在内存里拷贝，网络再慢也只是把缓冲吃掉，不会卡住输出。

**实测**（本地 Range 服务器 + 40 s WAV，桌面先验证，再上真机）：

```
probe: ranged=true len=7056044 bytes
decoded: total = Some(40s)
playing 0s..3s → pos = 2.99s
seek -> 20s OK   → pos = 22.005s
```

真机（Redmi Pad，通过 adb reverse 指向 PC 上的 Range 服务）：

```
I app::audio: stream: ranged=true len=7056044
127.0.0.1 - "GET /test.wav HTTP/1.1" 206   ← 连续 206 分段
```

**两个必须开的权限**（否则 Android 上直接 `EPERM`）：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<application android:usesCleartextTraffic="true" ...>
```

第二条是为了局域网 `http://` 的 Navidrome；如果只走 https 可以去掉。

**顺手修掉的一个坑**：WAV 能认出容器却报 "format has not been recognized"，
是因为 rodio 的 `symphonia-wav` 只带容器、不带 PCM 解码器，必须再加 **`symphonia-pcm`**。

---

## 9. 参考实现：nori（已读源码）

[norifm/nori](https://github.com/norifm/nori) 是一个 44 个 crate 的 Rust 工作区
（`crates/engine` 引擎、`crates/player` 播放语义、`crates/output-cpal` 输出、`crates/net` 网络）。
读了它处理流和 Seek 的几处，结论如下。

### 9.1 它是怎么做的

**`crates/engine/src/source.rs`（2906 行）——每首歌一个 loader 线程**

> "fill a window ahead of the demuxer up to the `load_control` high mark, closes the connection,
> and sleeps until the reader comes within the low mark … With a stream cache entry, each burst is
> written to it; once the entry is whole the loader drops its memory copy and reads the file."

* **窗口 + 双水位**：`Window { high, low, cap }`，读到 low 以下才重新开连接；
* **连接用完就关**：分段拉取，网络可以休眠（省电的核心理由）；
* **流缓存**：每段落盘到 `.part`，整首齐了改成正式条目，之后直接从文件读——Seek 就变成普通文件 Seek；
* **`MediaSource::is_seekable()` / `byte_len()`**：长度已知才报可 Seek。

**`crates/player/src/transport.rs`——几个具体数字**

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `load_control()` | `[60_000ms, 600_000ms, 1_000ms, 2_000ms, cap]` | 最小缓冲 60 s、最大 600 s、起播需 1 s、恢复需 2 s |
| `cap` | `内存/4`，夹在 **16–48 MB** | 所有 loader 共享的字节上限 |
| `PRECACHE_AFTER_MS` | 6 000 | 播放到 6 s 时预取下一首 |
| `switch_dip()` | 淡出 ≤150 ms，淡入同长度；换歌至少 120 ms | **"a hard cut clicks"**——跳变会咔哒 |
| `IDLE_RELEASE_MS` | 5 分钟 | 暂停久了释放音频设备让系统休眠 |

**`crates/engine/src/arriving.rs`**：边到边解码（给 AutoMix 测速用），管道 2 MB、每 256 KB 唤醒一次。
**`crates/engine/src/store.rs`**：缓存就是磁盘上的普通文件（键 `<id>:<quality>`），`.part` 到整首才转正，
按"本次运行没用过的优先 → LRU"淘汰。
**`crates/player/src/queue.rs`**：队列只算下标（放置、随机、预取/预量区间、跨尾部续接），列表由平台持有。

### 9.2 我们与它的差异

| 维度 | nori | 我们（当前） |
| --- | --- | --- |
| 取字节 | 每首一个 loader 线程，窗口 + 双水位，连接用完即关 | 按需 1 MB Range 请求，读到哪里取到哪里 |
| Seek | 指针 + 等 loader 追上来（顺序续传） | 直接按新位置发 Range 请求（远跳更省流量） |
| 起播条件 | 缓冲够 1 s 且到达速率跟得上才开声 | 解码器第一次 read 就等数据 |
| 缓存 | 流缓存分段落盘，整首转正，带 LRU 淘汰 | 先整首下载再解码，无 `.part` |
| 跳变 | **淡出 → 跳 → 淡入** | 已照抄（见下） |
| 预取 | 播到 6 s 预取下一首 | 无 |
| 进度显示 | 跳转目标一直显示到引擎上报新位置 | 拖拽预览，松手即用目标值 |

### 9.3 已经抄过来的

* **`switch_dip` 的淡出/淡入**：`audio.rs` 的 `Cmd::Seek` 现在先 8 步降到 0（约 48 ms），
  `try_seek`，再 12 步升回（约 96 ms）。目标音量存在 `AtomicU32`（f32 位模式）里，UI 改音量也能同步。
* **分块从 256 KB 提到 1 MB**：请求数少四倍，单次 Range 更划算。
* **进度按当前曲目计时**（`track_bounds`），不用整张专辑时长。

### 9.4 还没有、且值得做的（按性价比排序）

1. **流缓存 `.part` → 转正**（`store.rs` 的做法）：边下边写盘，整首齐了改名；之后重播、Seek 都是本地文件，
   顺带得到"听过即离线"。
2. **预取下一首**（`PRECACHE_AFTER_MS = 6s`）：切歌从"等网络"变成"瞬切"。
3. **起播阈值**：缓冲 ≥1 s 再开声，避免开头卡顿；我们现在是解码器直接读，弱网下会断续。
4. **`shown_place` 式的进度显示**：跳转后别立刻被引擎的旧位置拉回去。
5. **暂停 5 分钟后释放音频设备**（`IDLE_RELEASE_MS`），省电。

认证方式两边一致：`t = md5(password + salt)`、`s = salt`，不传明文。


## 10. 登录 / 预取（本轮落地）

**A. 连接登录（凭据不再在源码里）**

* `src/settings.rs`：`Settings { base, user, password }`，写到
  `getFilesDir()/settings.json`（JNI 取路径；桌面退回临时目录），密码 AES-GCM 加密。
* 启动未登录时显示一次性登录界面（服务器/账号/密码），连上后自动关闭；
  右上角「已登录」标识可查看用户名 / 退出登录。
* 校验：`settings saved to "/data/user/0/app.lumen.player/files/settings.json"`，
  重启后 `settings loaded` → `navidrome: 120 张专辑`，源码里已搜不到服务器/口令字符串。

**B. 预取下一首**

* `remote.rs` 增加 `Job::Prefetch(id)`，由元数据线程串行执行；`Client::fetch_by_id`
  把整首下到 `cache/<id>.mp3`（`.part` 再改名）。
* 开播时预取 `ti+1`、`ti+2`；切歌走 `play_track_at(ti)`：命中缓存就 `play_file`，否则 `play_url`。
* 校验：`prefetched 75zkI2… (12785 KB)` → 切到该曲时 `play local cache: 75zkI2…` →
  同时预取下一首 `prefetched 0wByks… (12389 KB)`。

**顺手修的一个坑**：不是所有曲目都能 Range——Navidrome 对需要转码的曲目返回 200（`ranged=false`）。
这时 `HttpRangeSource` 会把整段读进内存用 `Cursor` 提供 `Read + Seek`，
否则解码器拿不到可 Seek 的源，拖进度条会失败甚至 panic。


## 11. 缓存淘汰

`src/cache.rs`：**音频和封面各有独立预算**（`AUDIO_CAP = 1.5 GB`、`COVER_CAP = 200 MB`），
两个池子分别按 LRU 淘汰，互不挤占。

* **使用记录**：每次命中缓存、预取完成、开始播放都 `touch` 一次，写进 `cache/lru.json`
  （`id → 最后使用秒`）；没有记录的用文件 mtime 兜底。
* **两个池子**：先裁音频池，再裁封面池，各自独立预算；池内从最久没用的开始删。
  删到该池上限的 **85%** 就停，避免每首都触发一次扫描。
  这样低预算下封面不会被音频挤没（反过来也一样）。
* **保护集**：正在播放、下一个预取目标（最近 3 个 id）不会被删——否则播放中丢文件，
  seek 会失败。`.part`（下载中）和 `lru.json` 永远跳过。
* **触发点**：每次预取完成、以及启动首帧。

实测（把上限临时压到 40 MB 逼它淘汰）：

```
cache evict: cover_4VTMW6… (15 KB)      ← 先清封面（很多是上次启动留下的）
cache trimmed: freed 23 MB, now 33 MB
prefetched 2INWe5… (6544 KB)
cache evict: 75zkI2…mp3 (12785 KB)      ← 淘汰"上一首"
cache trimmed: freed 12 MB, now 27 MB
```

淘汰后目录里保留了正在播的 `0wByks…mp3` 和预取的 `2INWe5…mp3`，验证保护集生效。
登录界面同时显示「缓存 xx MB / n 个文件（上限 1500 MB）」。


实测（临时压到 audio 20 MB / cover 1 MB）：

```
cache trimmed cover: freed 2 MB, now 0 MB / cap 1 MB
cache trimmed audio: freed 20 MB, now 0 MB / cap 20 MB
```

登录界面显示「缓存 音频 x MB / 封面 y MB（上限 1536 / 200 MB）」。


## 12. 封面并行拉取

首页封面原本是串行拉的（120 张要 10 秒以上）。现在改成 **6 个 worker 共用一个队列**（只下载到磁盘做缓存预热）：

```rust
let workers = total.clamp(1, 6);
// 每个 worker：pop_front 拿一张 → fetch_cover → 解码 → Msg::Cover
```

* 网络是瓶颈，6 路并发能把往返延迟叠起来；
* 纹理上传仍在主线程，所以加了一层节流：**每帧最多上传 4 张**（拖动时 1 张），
  否则 120 张一次性上传会卡一帧；
* 桌面冷启动实测：`covers done: 120 in 3426 ms (6 workers)`（单张约 29 ms，涵盖网络 + 解码）。

另外给桌面端补了 `env_logger`（`RUST_LOG=info`），以后调 UI 也能看日志了。


## 13. 三处开销优化

1. **时长计算缓存**：`Album` 现在存 `starts: Vec<f32>` 与 `total_secs`，曲目到货时
   `recompute_timing()` 算一次。`track_bounds()` / `album_secs()` 从"每帧对十几首求和"
   变成 O(1) 取表。
2. **预取后两首**：开播时把 `ti+1`、`ti+2` 都排进预取队列（曲目刚到货时也会补排一次），
   快速连点"下一首"不会掉到流式。
3. **当前在看的那张封面插队**：单独的通道 + 单独的线程，只服务"选中的那张"，
   不排在 120 张批量后面；每个下标只请求一次（`urgent_sent`）。


## 14. 为什么第一首要等几秒（已修）

**症状**：点专辑后要等 2～5 秒才出声。

**原因**：`stream_url` 里带了 `format=mp3&maxBitRate=320`，Navidrome 会**现场转码**。
转码响应是 `200`（没有 `Content-Range`），我们的 `HttpRangeSource` 只好把整段读进内存
（见 §8 的兜底分支），所以**整首下完才开声**——一首 38 MB 的歌就是几秒。

**修法**：默认请求**原始流**（不带 `format`），服务器直接把文件吐出来：

* 文件是 FLAC/MP3 时支持 `Range` → `ranged=true` → 按需分块读 → **边下边播**；
* Symphonia 本来就支持 flac / mp3 / aac / vorbis / wav，不需要服务端转码；
* 真遇到解不了的编码，才退回 `format=mp3` 的转码流（`PlayUrl` 现在带 `fallback`，
  音频线程会按顺序尝试）。

**实测（同一首 38 MB 的 FLAC）**：

```
stream start: 108个关键词 (+67 ms from tap)   ← getAlbum 往返
stream[0]: ranged=true len=38389747 (86 ms to open)
stream[0] playing (211 ms total)              ← 点下到出声
```

对比之前的"整段缓冲"是数量级的差别。seek 与切歌同时验证通过：

```
seek -> 674.8s
play local cache: 2ZHmladcQBsAe8eGv57TwC      ← 预取命中，瞬切
```


## 15. 本机解不了的编码（ALAC / DSD）：缓存兜底与边下边播（0.3.6）

缓存里存的是**原始文件**（Navidrome 的 `stream` 不转码），所以可能是 ALAC、DSD 这类
symphonia 没有解码器的格式。三个约束：

1. **缓存播放必须有兜底**：`Cmd::PlayFile { path, fallback }`，解码失败就改走
   `stream?format=mp3`（服务端转码），而不是打一行 error 后静音卡死；
2. **转码流不支持 Range，不能整段下完再播**：46MB ALAC 转码要十几秒，
   以前 `read_to_end` 就是"拿转码时间当起播时间"。现在 `HttpRangeSource` 对 200 响应
   走**边下边播**：后台线程 64KB 块填充 + Condvar，`read` 等数据、`seek` 只改偏移；
   实测首播 12.5s → 108ms；
3. **别缓存解不了的格式**：`fetch_by_id` 下载完做一次解码探测，失败就删 `.part`，
   否则每首白占 46MB，还会让下次播放重复走"解码失败→兜底"。
