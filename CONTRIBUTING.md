# 贡献指南

感谢愿意改进 Lumen。这个项目是个人自托管场景下的播放器，优先保证播放稳定与界面一致。

## 开发环境

- JDK 17
- Android SDK 37（platform 37.2）+ Build-Tools 37.0.0（Compose 1.13 要求 compileSdk ≥ 37.1，工程用 `compileSdkMinor = 2`）
- 使用仓库自带的 Gradle Wrapper（9.9.2），不要依赖全局 Gradle 版本
- AGP 9 起内置 Kotlin 支持，构建脚本中不再应用 `org.jetbrains.kotlin.android`

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## 提交前检查

1. `./gradlew :app:testDebugUnitTest` 通过；Release 变更还需 `./gradlew :app:assembleRelease` 通过；
2. 改动涉及播放、下载或账号切换时，在真机上做一次冒烟测试；
3. 不要在日志或测试里写真实服务器地址、账号、密码或 Token；
4. 新增第三方依赖时同步更新 `THIRD_PARTY_NOTICES.md` 和 `assets/licenses/`；
5. UI 文案尽量使用中文并与现有风格保持一致；涉及通用组件时优先复用。

## Commit 约定

使用 Conventional Commits 风格，例如：

```
feat(player): add crossfade volume on track change
fix(nav): restore previous page without flicker
docs: clarify build with gradle wrapper
```

提交信息用英文或中文均可，保持简洁并说明“为什么”。

## Pull Request

- 描述动机、改动范围和验证方式；
- 涉及界面变化时附截图或录屏；
- 不要提交构建产物、签名文件、`local.properties` 或个人配置；
- 大改动请先开 Issue 讨论，避免与现有方向冲突。

## 许可

提交代码即表示同意以项目的 MIT 许可发布。
