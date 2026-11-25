# 过程文档归档（docs/plans）

这里存放**已完成或已废弃**的工作计划，仅作历史留档，不代表当前实现的准确描述。
需要当前的、承诺长期维护的文档请看上一级 `docs/`。

| 文件 | 时间 | 状态 | 说明 |
| --- | --- | --- | --- |
| [optimization-plan.md](optimization-plan.md) | 2026-11-25 | 已完成 | 安全 / 隐私 / 性能 / 一致性收敛（阶段 A–G），成果随 0.74.0–0.76.0 发布 |
| [ui-ux-plan.md](ui-ux-plan.md) | 2026-11-25 | 已完成 | 电台入口重构（方案 A），随 0.75.0 发布 |
| [analysis-plan.md](analysis-plan.md) | 2026-11-25 | 部分完成 | 音频分析管线优化；未完成项（调性、拍点网格、OpenL3）已冻结 |
| [presentation-script.md](presentation-script.md) | 2026-11-25 | 历史稿 | 0.50.5 时期的演示讲稿，版本与功能描述均已过时 |

约定：这些文件**不再更新**；若其中的结论仍有效，应把结论提炼到 `docs/` 或根目录文档，而不是继续在这里追加。

> 历史备注：归档前它们位于仓库根目录（`OPTIMIZATION_PLAN.md`、`UI_UX_PLAN.md`）和 `docs/` 下，代码注释里的引用已同步指向本目录。

## 历史清理（2026-11-25）

仓库在 2026-11-25 做过两次历史改写，**v0.76.0 及之前的全部 commit hash 已作废**：

| 次序 | 剔除内容 | 体积变化 |
| --- | --- | --- |
| 第一次 | 构建产物（`android/**/build`、`intermediates`、`.kotlin` 日志）、构建期生成的 `assets/CHANGELOG.md` | 180.19 MiB → 76.49 MiB |
| 第二次 | 已被取代的二进制资产：`cnn14_int8.onnx`（78 MB）、`yamnet.tflite`、MiSans 字体（23 MB） | 76.49 MiB → 3.05 MiB |

影响：

- 旧 commit 链接、`?at=<sha>` 形式的文件链接、指向旧 sha 的 PR / issue 引用一律 404；
- 改写前的老 clone **不要再 push**，否则会把旧历史整包带回来，需重新 `git clone`；
- `v0.76.0` tag 已重建到新历史（提交内容未变，仅 hash 变化），Release 资产未受影响；
- 两次清理只删「已不在 HEAD」的文件，清理后提交的 tree 完全一致。
