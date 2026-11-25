# 动效与交互清单（0.54.0 基线，0.76.0 复核）

> 0.76.0 复核：本文条目除下方「已知差距」外仍然成立；0.74 起端侧分析换成 CLAP + ONNX，
> 「分析胶囊」「背景按声音家族变化」等描述沿用，未再改动动效参数。

## 已完成

| 优先级 | 项目 | 实现要点 |
| --- | --- | --- |
| P0 | 专辑首帧配色 | `schemeFromCache()` 同步命中，杜绝"先错色再改色" |
| P0 | 返回期间底层背景 | 底层页画静态渐变（`animate=false`），不再纯色 |
| P0 | 光斑相位共享 | `BackdropMotion` 用 `State` 承载，绘制阶段读取，切页不跳位 |
| P0 | 首页分页可恢复 | `shown` 改 `rememberSaveable` |
| P1 | 迷你条显隐 | 淡入上滑 220ms / 淡出下滑 180ms |
| P1 | 容器变换 | `NavOriginStore` 记录卡片矩形；进入从卡片长出、返回收缩回去（仅"返回首页"生效） |
| P1 | 返回缓动 | 300ms `CubicBezier(0.2, 0, 0, 1)` |
| P1 | Tab 切换 | `animateScrollToPage(260ms, emphasized)` |
| P2 | 按钮按压 | 圆形按钮 `scale 0.94 / 120ms` |
| P2 | 返回视差 | 背景页额外 `translationX` 让出 |
| P2 | 分析胶囊弹出 | `0.94→1` + 淡入 160ms |
| P2 | 二次返回确认 | 2s 内再按退出 |
| P2 | 下载列表 | `animateItem()` |
| P2 | 搜索/电台淡入 | 160ms / 200ms alpha |

## 已知差距

1. ~~非真共享元素~~：0.54.1 已改为 `SharedTransitionLayout` + `sharedElement`，封面在卡片/专辑头图/播放页之间真实 morph。
2. **返回为对称转场**：已取消跟手式预测性返回，返回与进入走同一条共享元素转场（0.54.2）；
   0.76.0 复核确认代码里已无 `PredictiveBackHandler` 调用，仅剩的无用 import 也已清理。
3. **转场未帧级验证**：`screencap` 延迟 ≈200ms，转场 ≈300ms，只能用 `animator_duration_scale 5` 慢放人工确认。
4. **未覆盖**：页面内局部展开（如设置分组）仍无过渡；多窗口/分屏下的转场未测。

## 复验命令

```bash
adb shell settings put global animator_duration_scale 5
# 进/出专辑、切 Tab、点分析胶囊、连按两次返回
adb shell settings put global animator_duration_scale 1
```
