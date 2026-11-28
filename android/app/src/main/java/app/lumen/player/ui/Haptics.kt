package app.lumen.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 全应用唯一的触感反馈入口。
 *
 * 设计原则（对齐 Android / Material 的触感语义，而不是每处都"震一下"）：
 * - **按语义分类**：轻点用 VirtualKey、进入详情用 ContextClick、切换用 ToggleOn/Off、
 *   分段切换用 SegmentTick、确认用 Confirm、失败用 Reject、长按用 LongPress、
 *   拖拽跨过阈值用 GestureThresholdActivate、手势结束用 GestureEnd。
 *   不同档位在真机上是不同强度/纹理的反馈，避免"所有交互同一个长震"。
 * - **只在有意义的地方震**：文字输入、滚动、纯展示不震；重复点击的次级按钮用最轻的一档。
 * - **尊重系统开关**：`View.performHapticFeedback` 在系统「触摸时振动」关闭时不发声，
 *   所以这里不再自行判断系统设置；应用内另给一个总开关（无障碍 / 省电偏好）。
 * - **低版本自动降级**：Compose 的 `HapticFeedbackType` 在旧系统上会回落到最接近的
 *   `HapticFeedbackConstants`，minSdk 26 也不需要写分支。
 */
class LumenHaptics internal constructor(
    private val feedback: HapticFeedback,
    val enabled: Boolean,
) {
    /** 最轻的一档：图标按钮、普通按钮、列表行。 */
    fun tap() = fire(HapticFeedbackType.VirtualKey)

    /** 打开详情 / 进入另一层：比 tap 稍"实体"的确认感。 */
    fun navigate() = fire(HapticFeedbackType.ContextClick)

    /** 提交了一个动作（播放、连接、保存、确认对话框）。 */
    fun confirm() = fire(HapticFeedbackType.Confirm)

    /** 动作失败或被拒绝（登录失败、请求出错）。 */
    fun reject() = fire(HapticFeedbackType.Reject)

    /** 开关 / 勾选的状态变化。 */
    fun toggle(on: Boolean) = fire(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** 分段按钮、标签页、筛选芯片之间的切换。 */
    fun segment() = fire(HapticFeedbackType.SegmentTick)

    /** 长按进入上下文（长按菜单、批量选择）。 */
    fun longPress() = fire(HapticFeedbackType.LongPress)

    /** 拖动跨过阈值（进度条开始拖动、下拉刷新触发）。 */
    fun threshold() = fire(HapticFeedbackType.GestureThresholdActivate)

    /** 手势结束（松手、进度条定位完成）。 */
    fun gestureEnd() = fire(HapticFeedbackType.GestureEnd)

    private fun fire(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

/** 应用级触感总开关（系统级开关由平台处理，不在这里重复判断）。 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

private object NoopHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

private val DisabledHaptics = LumenHaptics(NoopHapticFeedback, enabled = false)

/** 在 Composable 里取当前生效的触感入口（开关变化时会重建）。 */
@Composable
fun rememberLumenHaptics(): LumenHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    if (!enabled) return DisabledHaptics
    return remember(feedback) { LumenHaptics(feedback, enabled = true) }
}
