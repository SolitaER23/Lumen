# Lumen UI/UX 重构计划 —— 电台入口（方案 A）

> **已归档**（0.76.0 起移入 `docs/plans/`）：计划内容已随 0.75.0 发布，仅作过程留档。
> 本文件原为**活文档**：每完成一项把 `[ ]` 改成 `[x]`，并在「执行日志」补一行。
> 执行者：AI agent（自动连续推进）；验证设备：Pixel 10a 无线调试（`192.168.1.67:43003`）。
> 基线：`0.74.0` / versionCode 203。**本计划内容已随 0.75.0 正式发布**（详见 §10）。
> **2026-11-26 备注**：本文描述的电台入口已随功能移除，相关章节仅作历史记录。

状态：**全部阶段完成**（含 2 项偏差、1 项暂缓，均有记录）。

---

## 0. 目标与边界

**目标（按优先级）**

1. 电台从「全应用唯一不可见的目的地」变成**首屏可见、一键可达**的一等入口（方案 A）。
2. 进入电台**不再劫持播放队列**；用户的场景选择被记住；「换一批」语义与文案一致。
3. 电台页信息层级纠正（场景为主语），并去掉与迷你条重复的控件与信息。
4. 顺带修掉评估中列出的无障碍/动效/死代码问题。

**硬约束**

- **MD3 Expressive**：沿用现有 `MaterialTheme.motionScheme` / `surfaceContainer*` / 胶囊与超大圆角语言；不引入新依赖。
- **功能不冲突**：不改 Subsonic 协议、不改分析管线与 `moods.db`、不改下载/缓存与账号逻辑；`Radio.build` 的排序算法除"可排除指定曲目"外行为不变。
- **双语**：新文案全部走 `L10n.t` / `L10n.tf`。
- **只做手机单栏布局**（2026-11-25 追加决策：双栏 / 平板 / 折叠屏支持整体移除，见 §8）。
- **省电**：新动效受播放状态驱动。
- **隐私**：不新增个人数据落盘（新增的只有界面偏好）。

---

## 1. 决策记录

| ID | 决策 | 结果 |
| --- | --- | --- |
| D1 | 取消长按进入电台 | ✅ 已移除，改为可见入口 |
| D2 | 入口拆两层：首页电台行（选场景即起播）+ 泛入口（预览） | ✅ |
| D3 | 电台页预览优先，主按钮才切队列 | ✅ |
| D4 | 18 场景 → 三组 chip | ✅（场景 9 / 活动 6 / 情绪 3） |
| D5 | 电台页隐藏迷你条 | ✅ 单栏 |
| D6 | `RadioPrefs` 持久化，不再强制 autoPick | ✅ |
| D7 | 「换一批」= 同场景同种子重排；「换种子」独立 | ✅ |
| D8 | 播放页种子入口：~~"以此曲开场"胶囊~~ | ❌ 已作废（§9）：按用户要求移除播放页电台入口，种子入口只保留首页电台行 |
| D9 | 电台转场注册 origin | ✅ |
| D10 | 「排序」从头部下沉到分区标题 | ⏸ 暂缓（见 §6） |
| D11 | ~~不改版本号、不发布 APK~~ | ❌ 已作废：按用户要求发布 **0.75.0 / versionCode 204**（见 §10） |

---

## 2. 阶段清单

### P0 基线

- [x] **P0-1** `compileDebugKotlin` 基线通过（UP-TO-DATE）
- [x] **P0-2** 真机基线截图：`target/plan/baseline-home.png`、`baseline-radio.png`
- [x] **P0-3** 装机版本确认：`versionName=0.74.0 / versionCode=203`，与仓库 HEAD 一致
- [x] **P0-4**（追加）基线录到关键缺陷：长按进入电台后，正在播的《荒原魂泣·变调》被《桃源仙境》替换

### P1 入口：首页电台行 + 取消长按

- [x] **P1-1** 新增 `ui/RadioRow.kt`（标题 + Sparkle 圆点 + 5 场景胶囊 + 「全部 18 台」+ 骨架/分析中/未就绪三态）
- [x] **P1-2** 插入到「专辑」页 `SectionRow` 之上，`HomeScrollStore` 续接未受影响
- [x] **P1-3** 移除长按（`combinedClickable` → `clickable`），删掉 `HomeHeader` 的死参数 `onOpenRadio` 与 `haptics`
- [x] **P1-4** 注册 `NavOriginStore("radio")`（26dp 圆点），`originKeyOf` 增加 `10 -> "radio"`
- [x] **P1-5** 文案同步：`OnboardingScreen` 第 5 页、`README.md`、`docs/user-guide.md`、`docs/feature-plan.md`
- [x] **P1-6** 编译 + 装机验证：冷启动可见、点击进页、胶囊一键起播

### P2 不打断播放：预览态 + 显式开始

- [x] **P2-1** `buildRadio()`（只构建）/ `playRadio()`（才切队列）拆分
- [x] **P2-2** `openRadio(vibe, autoStart)`：泛入口预览、胶囊起播
- [x] **P2-3** 主按钮「用这个电台播放」；已在播时显示 播放/暂停 + 下一首
- [x] **P2-4** 重进同一电台复用（`radioMode` 或同种子），不重建不重置
- [x] **P2-5** 返回电台页不改播放器状态；迷你条行为回归
- [x] **P2-6** 真机验证：播放《Asience-Fast Piano》→ 进电台（预览）→ 返回，播放未中断（截图 `step3`/`step4`）

### P3 电台状态与语义

- [x] **P3-1** `data/RadioPrefs.kt`（vibe / strength / autoPick；wipe 保留并在注释说明）
- [x] **P3-2** 启动读回、变更写回；`openRadio` 不再强制 `autoPick=true`
- [x] **P3-3** 「换一批」= 同场景同种子 + `exclude` 最近 6 首；「换种子」独立（`Radio.build(exclude=…)`）
- [x] **P3-4** 失败/空态：已分析曲目数 +「继续分析」（`MoodAnalysisRunner.resume/start`），删除"先运行心情分析"旧文案
- [x] **P3-5** 加载态局部化：不再整页 `alpha=0.55`，改为内容区进度块

### P4 电台页信息层级与视觉

- [x] **P4-1** 场景名 32sp 主标题；曲名 20sp；长专辑名降为元信息（42sp Black 移除）
- [x] **P4-2** 元信息行「种子《…》· 30 首」（有预览时不再追"已分析 N 首"，避免被挤成"已分析…"）
- [x] **P4-3** 场景 chip 三组 + 选中态（primaryContainer + 1dp primary 描边），一屏可见
- [x] **P4-4** 动作区主次：52dp 主按钮 / 「换一批」文本次级 / 「换种子」11sp 三级
- [x] **P4-5** 滑块两端文案「更像我正在听的 ↔ 更贴合场景」；「跟随当前歌曲」独立 Switch
- [x] **P4-6** 电台页隐藏 `YoinBar`（`screen != 10`）

### P5 播放页种子入口

- [x] **P5-1** ~~「以此曲开场」胶囊~~ → 后续按 §9 移除，播放页不再有电台入口
- [x] **P5-2** 点击 → 电台页预览态、种子=当前曲目、不打断播放
- [x] **P5-3** 真机验证：`step8` —— 模式=预览、种子=当前曲目、"用这个电台播放"在场

### P6 无障碍与细节

- [x] **P6-1** 电台页播放键 cd 随状态切换；`PlayerAction` 增加 `label` 并接 `onClickLabel`；首页统计/排序圆钮补 `contentDescription`（真机 a11y 树已确认：聆听统计 / 排序 / 设置 / 搜索 / 下载 / 收藏 / 播放模式）
- [x] **P6-2** 移除电台页常驻 `rememberInfiniteTransition`（改由播放状态与 `AnimatedContent` 驱动）
- [x] **P6-3** 死代码：未接线的 `HomeScheme` 及其 4 个专属 import、`YoinBar.onHome`
- [x] **P6-4** 迷你条空态：去掉"主色播放钮 + 打开第一张专辑"，改为中性引导文案、不可点
- [x] **P6-5** 分段胶囊选中态对比度：实测当前封面配色下可辨（选中项有 primaryContainer 块 + 文字提亮），**暂不改**，避免与"颜色交给封面"的设计原则冲突

### P7 收尾与回归

- [x] **P7-1** `:app:lintDebug` 通过（BUILD SUCCESSFUL，无新增 error）；`Slider` 迁移到 `SliderState` 重载以消除新 deprecation
- [x] **P7-2** `:app:testDebugUnitTest` 通过
- [x] **P7-3** 真机回归矩阵（见 §3）
- [x] **P7-4** `CHANGELOG.md` 增加 `[未发布]`；README / user-guide / feature-plan 同步
- [x] **P7-5** 本文件回填结论（含暂缓项与偏差）

---

## 3. 回归矩阵（Pixel 10a 实测）

| # | 场景 | 结果 |
| --- | --- | --- |
| R1 | 冷启动、分析未完 | ✅ 电台行显示「可直接开台 · 仍在分析 716/1567」，胶囊可点（已分析数 > 0） |
| R2 | 分析完成 | ✅（部分完成状态下同样可开台） |
| R3 | 播放专辑 → 点「全部 18 台」 | ✅ 音乐未中断，页面为「预览」+「用这个电台播放」（`step3`） |
| R4 | 预览态点主按钮 | ✅ 切到电台队列并播放（`step5`） |
| R5 | 播放中点「换一批」 | ✅ 曲目从 Dance Till We Die → Love Song，场景仍为「夜晚」，种子不变（`step7`） |
| R6 | 选「夜晚」→ 重启 → 再进 | ✅ 仍为「夜晚」（`RadioPrefs`） |
| R7 | 首页胶囊点「通勤」 | ✅ 直接起播并停在该场景 |
| R8 | ~~播放页「以此曲开场」~~ | ➖ 该入口已在 §9 移除 |
| R9 | ~~双栏~~ | ➖ 双栏布局已移除（§8）；横屏改为单栏，回归 R1–R8 |
| R10 | 返回 | ✅ 预览期返回播放状态不变；已在电台时返回保留电台队列 |
| R11 | 清除本机数据 | ⚠️ 未在真机执行（会丢曲库与分析数据）；代码路径不变（`LocalData.wipe` 不动），仅新增偏好文件被保留 |
| R12 | 无障碍 | ✅ `uiautomator dump` 确认新控件名称；空态 CTA 未触发（需 0 分析数据） |

未覆盖项说明：R1 的"零分析数据"分支、R11 为破坏性操作，均以静态检查代替；`root` 转场动画（P1-4）以代码路径确认，未做慢动作录屏。

---

## 4. 偏差与取舍

1. **D8 播放页入口**：原计划 2×2 操作区，实测右侧仅约 80dp 宽（封面 232dp + 24dp 内边距），2×2 需把封面缩到 200dp。改为**带文字的胶囊**放在曲名下方：可读、可发现、不动封面尺寸。
2. **种子漂移（新发现并修复）**：「换一批」原先取 `playback.current` 当种子，而播放电台后 current 已是电台内曲目 —— 实测种子从《The Grants》变成《Dance Till We Die》。改为种子 id + 标题随电台状态保存并复用。
3. **元信息截断（新发现并修复）**：「种子《很长的专辑名》· 30 首 · 已分析 N 首」在窄屏被截成"已分析…"。改为有预览时不显示"已分析"，只在空态显示。

---

## 5. 变更文件

| 文件 | 变更 |
| --- | --- |
| `ui/RadioRow.kt` | **新增** 首页电台行（含 `RadioRailState`） |
| `data/RadioPrefs.kt` | **新增** 电台偏好持久化 |
| `data/Radio.kt` | 新增 `RadioGroup` 与 `RadioVibe.group`；`build(exclude=…)` |
| `ui/RadioScreen.kt` | 重写：预览/正在播双态、分组 chip、层级、空态 CTA、无障碍 |
| `ui/HomeScreen.kt` | 插入电台行、移除长按、头部按钮无障碍名称、删 `HomeScheme` |
| `ui/PlayingScreen.kt` | `PlayerAction(label)`；「以此曲开场」胶囊（后被 §9 移除） |
| `ui/YoinBar.kt` | 空态重做、去掉死参数 `onHome` |
| `ui/OnboardingScreen.kt` | 引导第 5 页改写 |
| `MainActivity.kt` | 预览态状态机、种子保持、偏好读写、隐藏迷你条、origin、已分析数轮询；**删除双栏分支** |
| `playback/Playback.kt` | 新增 `radioMode`（`play(radio=…)`） |
| `ui/HomeScroll.kt` | **新增**（由 `Adaptive.kt` 拆分，仅保留 `HomeScrollStore`） |
| `ui/Adaptive.kt` | **删除**（双栏 + 折叠屏姿态） |
| `android/app/build.gradle.kts` | 删除 `material3-window-size-class`、`androidx.window` |
| `README.md` / `docs/user-guide.md` / `docs/feature-plan.md` / `docs/device.md` / `CHANGELOG.md` | 同步 |

---

## 6. 暂缓项（不在本次范围）

- **「排序」从头部圆钮下沉到分区标题**：电台行已解决可见性问题；下沉要改 4 个列表头 + 状态传递，收益仅为头部视觉平衡，且会同时影响"按 tab 记忆排序"的入口一致性。留作独立任务。
- ~~双栏下左栏标题被截断~~：双栏布局已整体移除（§8），该问题随之消失。
- **R11 数据清除回归**、**零数据空态**：需要在设备上重置后才能覆盖，建议发版前用测试账号验证一次。

---

## 7. 执行日志

| 时间 | 阶段 | 结果 | 备注 |
| --- | --- | --- | --- |
| 19:31 | 计划建立 | — | 基线编译进行中 |
| 19:33 | P0 | 基线通过 | 装机 0.74.0/203；截图 2 张；录到"长按打断播放" |
| 19:34–19:38 | P1 | 入口完成 | RadioRow + 取消长按 + origin |
| 19:35–19:40 | P2/P3 | 预览态完成 | buildRadio/playRadio 拆分、RadioPrefs、exclude |
| 19:43–19:47 | 真机验证 | R3/R4/R10 通过 | 预览不打断、返回不重置 |
| 19:48 | 修正 | 种子漂移 / 元信息截断 | 见 §4 |
| 19:49 | P4/P5/P6 | 层级、胶囊、无障碍、清理 | a11y 树确认新标签 |
| 19:50 | 双栏 | R9 通过（后被移除） | 见 §8：按要求删除双栏/平板支持 |
| 19:51 | P7 | lint + unit test 通过 | Slider 迁移 `SliderState` |
| 19:52 | 收尾 | CHANGELOG/docs/本文件 | 当时未改版本号 |
| 19:54 | 终验 | 重装后冒烟通过 | 首页胶囊「放松」→ 直接起播（正在播放/种子正确）→ 返回后电台行仍在 |
| 20:01 | §8 | 删除双栏 / 平板 / 折叠屏支持 | 按用户要求；`Adaptive.kt` 删除、两个依赖移除；编译/lint/单测/R8 全过 |
| 20:02 | §8 真机 | 单栏冒烟 | 首页/电台行/电台播放/专辑/播放页/设置/搜索/返回全部正常；播放未中断；横屏=单栏无崩溃 |
| 20:0x | §9 | 播放页导航统一 | 删「以此曲开场」与电台「播放页」按钮；共享元素上提宿主；`navBack` 方向标记 |
| 20:1x | §9 真机 | 6 条路径 + 反向各 1 次 | 10× 慢放抓帧确认封面起终点一致，返回位置正确 |
| 20:30 | 修复 | 首页标签"点哪落哪" | pager 程序化滚动期间屏蔽 `currentPage` 回写；见 CHANGELOG「修复」 |

---

## 8. 追加决策：移除双栏 / 平板 / 折叠屏支持（2026-11-25）

**背景**：用户明确「不需要验证双栏和平板，相关代码可删除，但不能影响现有 app 的功能」，
且 `docs/device.md` 本就写明只维护 Pixel 10a、平板与模拟器不作为验证对象。

**删除范围**

| 位置 | 内容 |
| --- | --- |
| `MainActivity.kt` | `if (adaptive.twoPane && screen != 0)` 的整段双栏分支、左栏 `weight(leftWeight)`、右栏独立迷你条、`screenContent(..., "-wide")` 后缀、`visible = … && !adaptive.twoPane` |
| `ui/Adaptive.kt` | `AdaptiveLayout`、`FoldState`、`rememberAdaptiveLayout()`、`Context.findActivity()`（仅被前者使用）；文件仅保留 `HomeScrollStore` 并改名 `HomeScroll.kt` |
| `build.gradle.kts` | `androidx.compose.material3:material3-window-size-class`、`androidx.window:window:1.3.0` |

**保留不动**：`HomeScrollStore`（首页滚动续接）、`android:resizeableActivity="true"` 与 `configChanges`
（旋转/分屏仍可用，只是布局恒为单栏）、`SharedPageHost` 的共享元素转场、迷你条与播放页逻辑。

**影响**：大屏 / 横屏 / 分屏从"左右分栏"变为"单栏拉伸"，功能与导航完全一致；
手机竖屏（唯一目标形态）行为零变化。

**验证**：`compileDebugKotlin`、`lintDebug`、`testDebugUnitTest`、`assembleRelease` 全通过；
装机后冒烟（首页 / 电台行 / 电台预览与播放 / 播放页 / 专辑页 / 设置 / 返回）均正常，横屏单栏无崩溃。

---

## 9. 追加决策：正在播放页导航统一（封面共享元素，2026-11-25）

**背景**：用户要求移除播放页「以此曲开场」与电台右上角「播放页」，改为点电台正在播放栏进入播放页；
并要求**所有**进入播放页的路径统一用共享元素转场（以对应歌曲封面为切入元素），
同时"从哪里点击进入，退出就返回哪里"，转场起点/终点/返回位置一致。

### 9.1 进入播放页的路径审计

| 路径 | 处理 |
| --- | --- |
| 迷你条（首页 / 专辑 / 艺人 / 搜索 / 收藏 / 下载 / 设置） | ✅ 唯一常驻入口；封面注册 `np-<albumId>`，与播放页封面配对 |
| 电台页「正在播放」栏 | ✅ 原「播放页」按钮删除，改由该栏进入；封面同样注册 `np-<albumId>` |
| 收藏列表点歌 | ❌ 移除自动跳转：与其它列表一致，只开始播放并留在原地（原先会把你甩进播放页，返回又是另一层） |
| `HomeScreen(onOpenPlayer=…)` | ❌ 死参数，删除（该入口从未渲染） |
| 通知栏点击 | ➖ 打开 MainActivity 本身，不进入播放页，保持原样 |
| 播放页「以此曲开场」 | ❌ 按需求删除，播放页不再有电台入口 |

### 9.2 转场实现

- `SharedTransitionLayout` 从页面壳**上提到公共宿主** `SharedElementHost`（页面 + 迷你条在同一作用域），
  页面切换改为 `SharedPageSwitcher`（只负责 `AnimatedContent` 与页面级 scope）；
- 迷你条不在任何页面里，因此给它自己的 `AnimatedVisibilityScope`：
  `Modifier.coverSharedElement("np-<albumId>", barScope)`；页面内元素用 `Modifier.albumSharedElement(key)`；
- `AnimatedCover` 的共享 key 改为显式参数 `sharedKey`（原来硬编码 `cover-<albumId>`，会与专辑网格封面重名）；
- 三处封面共用同一 key 与同一 `BoundsTransform`（MD3 `motionScheme.defaultSpatialSpec`），
  因此进入/退出是同一段动画的正反播放。

### 9.3 返回与方向

- `navigate()` 仍把「来处」压栈（页面 + 专辑上下文），`performBack()` 原样恢复 → 严格"回到点击的位置"；
- 转场方向不再用"目标是不是首页"猜测，改为导航动作标记 `navBack`：
  - 进入：`scaleIn` 从目标页登记的入口（设置/搜索/统计/下载/电台行）或纯淡入（专辑详情/播放页，由共享元素承担）；
  - 返回：`scaleOut` 收缩回**来源页**登记的入口。
  - 修复：从播放页返回电台页时，电台页曾错误地按"电台行"的位置再长出来一次。

### 9.4 验证（Pixel 10a，动画时长 10× 抓中间帧）

| 场景 | 结果 |
| --- | --- |
| 首页迷你条 → 播放页 | ✅ 封面从 46dp 迷你条位置放大到 232dp（`nav2-enter-mid.png`） |
| 播放页 → 返回首页 | ✅ 封面收缩回迷你条，页面交叉淡出（`nav3-exit-mid.png`） |
| 电台正在播放栏 → 播放页 | ✅ 封面从 56dp 栏位置放大（`nav6-radio-to-player-mid.png`） |
| 播放页 → 返回电台 | ✅ 封面缩回该栏；电台页**整页淡入、不再从电台行位置缩放**（`nav7-player-to-radio-mid.png`） |
| 电台 → 返回首页 | ✅ 收缩回首页电台行（`nav8-radio-to-home-mid.png`） |
| 专辑详情 → 迷你条 → 返回 | ✅ 回到同一张专辑页（`nav9-back-to-album.png`） |
| 收藏列表点歌 | ✅ 留在收藏列表，不再自动进播放页 |
| 播放页 / 电台页顶栏 | ✅ 「以此曲开场」「播放页」两个按钮均已不存在 |

**清理**：`enterAnim` / `enterRect` / `exitRect` / `enterOriginX,Y` 与根布局按压监听（只写不读）随本轮删除。

**质量门**：`compileDebugKotlin`、`assembleRelease`、`lintDebug`、`testDebugUnitTest` 全通过；装机后崩溃缓冲区无记录。

---

## 10. 正式发布：0.75.0 → **0.75.1**（2026-11-25）

> **现状：以 0.75.1 为准**（versionCode 205）。0.75.0 打包后发现"应用内更新日志仍是 0.74.0"，
> 原因是 `assets/CHANGELOG.md` 与仓库根 `CHANGELOG.md` 是两份副本且从未同步；
> 现已改为**构建配置阶段自动同步**，并发的 0.75.1 包替换了 0.75.0（旧包保留在 `dist/` 便于核对/回滚）。
>
> **2026-11-25 追加**：0.76.0（versionCode 206）已发布，只做图标换代（MD3 Expressive「光之心」），
> 本计划涉及的界面与行为均未改动，可直接以 0.76.0 为准；发版信息见 `docs/distribution.md` 与 `CHANGELOG.md`。

本计划的全部改动（含后续修复）已作为 **0.75.0** 正式发布：

| 项 | 值 |
| --- | --- |
| 版本 | `0.75.0` / `versionCode 204`（`android/app/build.gradle.kts`） |
| APK | `dist/Lumen-0.75.0.apk`（158,105,767 字节 / 约 150.8 MiB） |
| SHA-256 | `61AE982455B1805F96C65F2BC17C4F29BF2DC18D70A3815C8E4D624B247C9519` |
| 证书 SHA-256 | `c1b188500af0f966cfebec1c58de8908c8cccc4b3b6a65250fab02d43bd60a37`（与 0.74.0 同一签名） |
| 覆盖安装 | ✅ 已实测：本机曲库、分析结果、下载与设置全部保留 |

发布动作按仓库既有流程执行：`assembleRelease` → `python scripts/update-release-metadata.py`；
该脚本同步了 `dist/README.txt`、`dist/SHA256SUMS.txt`、`docs/distribution.md`、`README.md`
以及条款页的「适用版本 0.75.x」。`dist/` 在 `.gitignore` 中，APK 走夸克网盘分发，不进仓库。
