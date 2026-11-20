//! 字体：Metro 靠字重分层，所以需要三档——细体（大字标题）、常规（正文）、半粗（瓷砖标题）。
//!
//! **三档全部使用小米 MiSans**（嵌入 APK，不依赖系统字体）：
//!
//! | 用途 | 字重 | 字体族 |
//! | --- | --- | --- |
//! | 大字标题（页标题 Light 42 / 专辑标题 Light 23） | Light | `metro_light` |
//! | 正文（曲名 / 艺人 / 说明） | Regular | `Proportional` |
//! | 瓷砖标题 / 小标题 caps | Demibold | `metro_semi` |
//!
//! MiSans 本身覆盖简体中文 + 拉丁，一套文件即可，不需要中西文分档回落。
//! 字体来源：`小米字体/MiSans.zip`（小米官方免费商用授权）。

use eframe::egui::{self, FontFamily};

/// 细体：大字标题（Metro 的 Light 大字是视觉核心）
const LIGHT: &[u8] = include_bytes!("../assets/fonts/MiSans-Light.ttf");
/// 常规：正文
const REGULAR: &[u8] = include_bytes!("../assets/fonts/MiSans-Regular.ttf");
/// 半粗：小标题、瓷砖标题（大写 + 字距）
const SEMI: &[u8] = include_bytes!("../assets/fonts/MiSans-Demibold.ttf");

fn face(bytes: &'static [u8]) -> egui::FontData {
    egui::FontData::from_owned(bytes.to_vec())
}

pub fn install_fonts(ctx: &egui::Context) {
    let mut fonts = egui::FontDefinitions::default();

    // egui 默认字体保留作 emoji / 生僻字兜底
    let default_prop = fonts
        .families
        .get(&FontFamily::Proportional)
        .cloned()
        .unwrap_or_default();

    fonts
        .font_data
        .insert("misans_regular".to_owned(), std::sync::Arc::new(face(REGULAR)));
    fonts
        .font_data
        .insert("misans_light".to_owned(), std::sync::Arc::new(face(LIGHT)));
    fonts
        .font_data
        .insert("misans_semi".to_owned(), std::sync::Arc::new(face(SEMI)));

    // 正文族：MiSans-Regular 排最前，emoji 仍由 egui 自带字体兜底
    let mut proportional = vec!["misans_regular".to_owned()];
    proportional.extend(default_prop.iter().cloned());
    fonts
        .families
        .insert(FontFamily::Proportional, proportional);

    // 细体：MiSans-Light → 常规 → egui 默认
    let mut light = vec!["misans_light".to_owned(), "misans_regular".to_owned()];
    light.extend(default_prop.iter().cloned());
    fonts
        .families
        .insert(FontFamily::Name("metro_light".into()), light);

    // 半粗：MiSans-Demibold → 常规 → egui 默认
    let mut semi = vec!["misans_semi".to_owned(), "misans_regular".to_owned()];
    semi.extend(default_prop.iter().cloned());
    fonts
        .families
        .insert(FontFamily::Name("metro_semi".into()), semi);

    log::info!("fonts: MiSans Light/Regular/Demibold embedded ({} faces)", fonts.font_data.len());
    ctx.set_fonts(fonts);
}
