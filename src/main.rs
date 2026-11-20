//! 桌面入口：`cargo run --bin app-desktop`，按手机比例开窗，快速调界面。

fn main() -> eframe::Result {
    #[cfg(not(target_os = "android"))]
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();

    let options = eframe::NativeOptions {
        viewport: eframe::egui::ViewportBuilder::default()
            .with_inner_size([400.0, 860.0])
            .with_min_inner_size([340.0, 640.0])
            .with_title("Lumen"),
        ..Default::default()
    };

    eframe::run_native(
        "Lumen",
        options,
        Box::new(|cc| Ok(Box::new(app::App::new(cc)))),
    )
}
