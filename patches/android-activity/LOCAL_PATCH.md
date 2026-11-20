# android-activity 本地补丁

这是 `android-activity` 0.6.1 的 vendored 副本，通过 `Cargo.toml` 里的
`[patch.crates-io]` 覆盖上游，仅修一个 bug。

## 修复内容

**问题**：game-activity 后端的 `map_input_state_to_text_event_callback` 里，
`GameTextInputState.text_UTF8` 为 null（无文本时）会直接传给
`std::slice::from_raw_parts`，触发 unsafe precondition panic：
`slice::from_raw_parts requires the pointer to be aligned and non-null`，
导致应用启动即崩溃（SIGABRT）。

**修复**：在 `src/game_activity/mod.rs` 里对 `text_UTF8` 判空，
null 或 `text_length == 0` 时视为空串，不再调用 `from_raw_parts`。

## 上游追踪

- Issue：https://github.com/rust-mobile/android-activity/issues/153
- PR（修复）：https://github.com/rust-mobile/android-activity/pull/154

## 升级时注意

升级 `android-activity` 前先确认上游是否已合入该修复（PR #154）；
若已合入且发布新版本，应删除本目录、移除 `Cargo.toml` 的 `[patch.crates-io]`，
改回直接依赖 crates.io 版本。
