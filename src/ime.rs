//! Android 软键盘（IME）→ egui 输入事件的翻译层。
//!
//! # 为什么需要这一层
//!
//! Android 的软键盘**不产生 KeyEvent**：它通过 `InputConnection` 把文字交给
//! `GameActivity` 的 GameTextInput（预编辑 / 提交 / 前后删除 / 光标都在那边）。
//! winit 0.30 的 Android 后端只转发 KeyEvent、不转发 IME 事件，所以 egui 永远
//! 收不到 `Event::Ime`——但 egui 0.37 的 `TextEdit` **自己完整支持 IME**
//! （预编辑上屏、提交替换、`DeleteSurrounding`、组合态光标）。缺的只是"事件源"。
//!
//! 这一层只做一件事：把 GameTextInput 的编辑会话状态，翻译成**最小的**
//! [`egui::ImeEvent`] 序列，交给 `TextEdit` 去执行。
//!
//! **文本只有一个所有者：egui 的缓冲区。** 桥本身不改字符串——上一版直接往
//! 缓冲区里 `push_str`，和 `TextEdit` 抢同一个数据源，于是出现重复叠加
//! （`http://http://`）、删除不生效、组合态光标错位、切输入框串字这一整类问题。
//!
//! # 状态机
//!
//! 平台给的是一份"快照"：`text`（含预编辑）+ `composingRegion` + `selection`。
//! 我们记住上一次快照里"已提交"和"预编辑"两部分，然后：
//!
//! | 平台快照 | 缓冲区 | 动作 |
//! | --- | --- | --- |
//! | 有预编辑，预编辑变了 | `committed + preedit` | `Preedit(new)` |
//! | 预编辑结束（composingRegion 变空） | 同上 | `Commit(组合起点之后的新内容)` |
//! | 没有预编辑，文本等长/变长/变短 | `committed` | 按字符差分 → `Commit` / `DeleteSurrounding` |
//! | 改到了光标前面的已有文字 | —— | `Resync`（直接改缓冲区，罕见兜底） |
//!
//! 只有当"用户自己改过缓冲区"（硬件键盘 / ADB / 程序化归一化）时，才把缓冲区
//! 推回平台（[`Bridge::sync_back`]），组合进行中绝不推——否则会打断输入法。

use eframe::egui::{Event, ImeEvent};

/// 输入框下标：0 服务器地址、1 用户名、2 密码（和 `App::setup_input` 的顺序一致）
pub const FIELD_BASE: usize = 0;
pub const FIELD_USER: usize = 1;
pub const FIELD_PASS: usize = 2;

/// 一帧的产出
pub enum Output {
    /// 平台没有新动作
    None,
    /// 注入给 egui 的 IME 事件（由 `TextEdit` 执行）
    Events(Vec<Event>),
    /// 平台状态和缓冲区差得太远（组合中途改到了前面的文字），直接落缓冲区
    Resync { text: String, caret: usize },
}

/// 翻译结果（纯函数返回这个，便于单测）
#[derive(Debug, PartialEq)]
enum Step {
    Idle,
    Events(Vec<Event>),
    Resync { caret: usize },
}

/// 缓冲区模型：`committed` 在 `at` 处插着 `preedit`
#[derive(Default)]
struct Model {
    committed: String,
    preedit: String,
    at: usize,
}

impl Model {
    /// 组合态下的光标（预编辑之后）——测试用
    #[cfg(test)]
    fn caret(&self) -> usize {
        self.at + self.preedit.chars().count()
    }
}

/// 软键盘桥：登记焦点字段，每帧把平台状态翻译成事件
#[derive(Default)]
pub struct Bridge {
    field: Option<usize>,
    model: Model,
    /// 上一次从平台读到的完整文本（没变就什么都不做）
    seen: Option<String>,
    /// 上一次推回平台的文本（用来发现"用户自己改过缓冲区"）
    pushed: Option<String>,
    /// 刚把状态推给平台时，平台可能还回一帧旧快照；这几帧不读取，
    /// 否则会把上一框的文字当成"用户改了文本"再翻译一遍。
    skip: u8,
}

impl Bridge {
    /// 当前正在编辑的字段
    pub fn field(&self) -> Option<usize> {
        self.field
    }

    /// 输入框拿到焦点：把内容登记给平台 IME（编辑器类型 + 文本 + 光标）
    pub fn begin(&mut self, field: usize, text: &str, caret: usize, password: bool) {
        self.field = Some(field);
        self.model = Model {
            committed: text.to_owned(),
            preedit: String::new(),
            at: caret.min(text.chars().count()),
        };
        self.seen = Some(text.to_owned());
        self.pushed = Some(text.to_owned());
        self.skip = 1;
        crate::safe_area::ime_begin(text, caret, password);
    }

    /// 输入框失焦：结束编辑会话。组合中的文字按原样提交（和系统输入法一致），
    /// 返回要注入 egui 的事件。
    pub fn end(&mut self) -> Vec<Event> {
        let mut ev = Vec::new();
        if !self.model.preedit.is_empty() {
            ev.push(Event::Ime(ImeEvent::Commit(std::mem::take(
                &mut self.model.preedit,
            ))));
        }
        self.field = None;
        self.model = Model::default();
        self.seen = None;
        self.pushed = None;
        crate::safe_area::ime_end();
        ev
    }

    /// 每帧调用（必须在创建 `TextEdit` 之前）：读平台状态并翻译成事件
    pub fn poll(&mut self) -> Output {
        let Some(_field) = self.field else {
            return Output::None;
        };
        if self.skip > 0 {
            self.skip -= 1;
            return Output::None;
        }
        let Some((text, compose, _sel)) = crate::safe_area::ime_state() else {
            return Output::None;
        };
        if self.seen.as_deref() == Some(text.as_str()) {
            return Output::None;
        }
        let step = translate(&mut self.model, &text, compose);
        self.seen = Some(text.clone());
        match step {
            Step::Idle => Output::None,
            Step::Events(ev) if ev.is_empty() => Output::None,
            Step::Events(ev) => {
                // 事件执行完，缓冲区就等于平台的文本了：别在帧末再推一遍
                // （每敲一个字都 setTextInputState 会打断输入法自己的状态机）。
                self.pushed = Some(text);
                Output::Events(ev)
            }
            Step::Resync { caret } => {
                self.model = Model {
                    committed: text.clone(),
                    preedit: String::new(),
                    at: caret,
                };
                Output::Resync { text, caret }
            }
        }
    }

    /// 帧末：用户自己改过缓冲区（硬件键盘 / ADB / 程序化归一化）时，把文本和光标
    /// 推回平台，保证输入法下一次提交落点正确。组合进行中不推。
    pub fn sync_back(&mut self, text: &str, caret: usize) {
        if self.field.is_none() {
            return;
        }
        if !self.model.preedit.is_empty() {
            return;
        }
        if self.pushed.as_deref() == Some(text) && self.model.at == caret {
            return;
        }
        crate::safe_area::ime_set(text, caret);
        self.pushed = Some(text.to_owned());
        self.seen = Some(text.to_owned());
        self.model.committed = text.to_owned();
        self.model.at = caret;
        self.skip = 1;
    }
}

/// 把平台快照翻译成事件（纯函数）。`text` 是含预编辑的完整文本。
fn translate(model: &mut Model, text: &str, compose: Option<(usize, usize)>) -> Step {
    let chars: Vec<char> = text.chars().collect();
    let (preedit, committed) = split_compose(&chars, compose);

    if let Some(pre) = preedit {
        // ── 组合中 ──────────────────────────────────────────────
        if committed != model.committed {
            // 组合期间改到了已提交部分（少见）：丢掉组合，整体对齐
            return Step::Resync {
                caret: chars.len(),
            };
        }
        if pre == model.preedit {
            return Step::Idle;
        }
        model.preedit = pre.clone();
        return Step::Events(vec![Event::Ime(ImeEvent::Preedit {
            text: pre,
            active_range_chars: None,
        })]);
    }

    // ── 组合结束：把"组合起点之后"的新内容作为一次提交 ──────────
    if !model.preedit.is_empty() {
        let tail = model.committed.chars().count().saturating_sub(model.at);
        let end = chars.len().saturating_sub(tail).max(model.at);
        let start = model.at.min(end);
        let commit: String = chars[start..end].iter().collect();
        model.committed = text.to_owned();
        model.preedit.clear();
        model.at = start + commit.chars().count();
        return Step::Events(vec![Event::Ime(ImeEvent::Commit(commit))]);
    }

    // ── 普通输入 / 删除（逐字提交、退格、选词替换）────────────
    let old: Vec<char> = model.committed.chars().collect();
    if old == chars {
        return Step::Idle;
    }
    let (at, del, ins) = split_diff(&old, &chars);
    // egui 的 Commit/DeleteSurrounding 都相对于它自己的光标，
    // 所以改动必须发生在光标处；否则走硬同步。
    if model.at != at + del {
        return Step::Resync {
            caret: at + ins.chars().count(),
        };
    }
    let mut ev = Vec::new();
    if del > 0 {
        ev.push(Event::Ime(ImeEvent::DeleteSurrounding {
            before_chars: del,
            after_chars: 0,
        }));
    }
    if !ins.is_empty() {
        ev.push(Event::Ime(ImeEvent::Commit(ins.clone())));
    }
    model.committed = text.to_owned();
    model.at = at + ins.chars().count();
    Step::Events(ev)
}

/// 拆出预编辑文本和"去掉预编辑之后"的已提交文本
fn split_compose(chars: &[char], compose: Option<(usize, usize)>) -> (Option<String>, String) {
    match compose {
        Some((s, e)) if e > s && e <= chars.len() => {
            let pre: String = chars[s..e].iter().collect();
            let mut committed = String::with_capacity(chars.len());
            committed.extend(&chars[..s]);
            committed.extend(&chars[e..]);
            (Some(pre), committed)
        }
        _ => (None, chars.iter().collect()),
    }
}

/// 字符级差分：返回 (公共前缀长度, 被删长度, 新插入的文本)
fn split_diff(old: &[char], new: &[char]) -> (usize, usize, String) {
    let mut prefix = 0;
    while prefix < old.len() && prefix < new.len() && old[prefix] == new[prefix] {
        prefix += 1;
    }
    let mut suffix = 0;
    while suffix < old.len() - prefix
        && suffix < new.len() - prefix
        && old[old.len() - 1 - suffix] == new[new.len() - 1 - suffix]
    {
        suffix += 1;
    }
    let deleted = old.len() - prefix - suffix;
    let inserted: String = new[prefix..new.len() - suffix].iter().collect();
    (prefix, deleted, inserted)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn text_of(ev: &[Event]) -> String {
        let mut out = String::new();
        for e in ev {
            if let Event::Ime(ImeEvent::Commit(t)) = e {
                out.push_str(t);
            }
        }
        out
    }

    fn model(s: &str, at: usize) -> Model {
        Model {
            committed: s.to_owned(),
            preedit: String::new(),
            at,
        }
    }

    #[test]
    fn plain_typing_appends_one_commit() {
        let mut m = model("", 0);
        let step = translate(&mut m, "a", None);
        match step {
            Step::Events(ev) => assert_eq!(text_of(&ev), "a"),
            _ => panic!("expected commit"),
        }
        assert_eq!(m.committed, "a");
        assert_eq!(m.at, 1);

        let step = translate(&mut m, "ab", None);
        match step {
            Step::Events(ev) => assert_eq!(text_of(&ev), "b"),
            _ => panic!("expected commit"),
        }
        assert_eq!(m.committed, "ab");
    }

    #[test]
    fn backspace_becomes_delete_surrounding() {
        let mut m = model("abc", 3);
        let step = translate(&mut m, "ab", None);
        match step {
            Step::Events(ev) => {
                assert_eq!(ev.len(), 1);
                assert!(matches!(
                    ev[0],
                    Event::Ime(ImeEvent::DeleteSurrounding {
                        before_chars: 1,
                        after_chars: 0
                    })
                ));
            }
            _ => panic!("expected delete"),
        }
        assert_eq!(m.committed, "ab");
        assert_eq!(m.at, 2);
    }

    #[test]
    fn composing_preedit_then_commit() {
        // 空 -> 拼音 "ni"（预编辑）-> 选中后提交 "你"
        let mut m = model("http://", 7);
        let step = translate(&mut m, "http://ni", Some((7, 9)));
        match step {
            Step::Events(ev) => {
                assert!(matches!(
                    &ev[0],
                    Event::Ime(ImeEvent::Preedit { text, .. }) if text == "ni"
                ));
            }
            _ => panic!("expected preedit"),
        }
        assert_eq!(m.preedit, "ni");
        assert_eq!(m.caret(), 9);

        let step = translate(&mut m, "http://你", None);
        match step {
            Step::Events(ev) => assert_eq!(text_of(&ev), "你"),
            _ => panic!("expected commit"),
        }
        assert_eq!(m.committed, "http://你");
        assert!(m.preedit.is_empty());
        assert_eq!(m.at, 8);
    }

    #[test]
    fn preedit_update_does_not_insert_whole_text_again() {
        let mut m = model("", 0);
        let _ = translate(&mut m, "h", Some((0, 1)));
        let step = translate(&mut m, "he", Some((0, 2)));
        match step {
            Step::Events(ev) => {
                assert_eq!(ev.len(), 1);
                assert!(matches!(
                    &ev[0],
                    Event::Ime(ImeEvent::Preedit { text, .. }) if text == "he"
                ));
            }
            _ => panic!("expected preedit"),
        }
        // 关键：缓冲区里只有一份 "he"，不是 "hhe"
        assert_eq!(m.preedit, "he");
        assert_eq!(m.committed, "");
    }

    #[test]
    fn commit_after_preedit_keeps_committed_tail() {
        // 光标在中间："ab|cd"，在光标处组合 "x"，提交 "X"
        let mut m = model("abcd", 2);
        let _ = translate(&mut m, "abxcd", Some((2, 3)));
        let step = translate(&mut m, "abXcd", None);
        match step {
            Step::Events(ev) => assert_eq!(text_of(&ev), "X"),
            _ => panic!("expected commit"),
        }
        assert_eq!(m.committed, "abXcd");
        assert_eq!(m.at, 3);
    }

    #[test]
    fn edit_before_caret_requests_resync() {
        // 平台在光标前面删了字（正常输入法不会这么做）→ 硬同步而不是乱插
        let mut m = model("abcdef", 6);
        let step = translate(&mut m, "bcdef", None);
        assert!(matches!(step, Step::Resync { .. }));
    }
}
