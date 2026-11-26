package app.lumen.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

object ExternalLinks {
    const val BILIBILI_URL = "https://space.bilibili.com/20567705"
    /** 更新入口：GitHub Releases 的最新版本页（不再使用网盘渠道）。 */
    const val UPDATE_URL = "https://github.com/SolitaER23/Lumen/releases/latest"
    const val FEEDBACK_NAME = "bilibili@晓月枫"

    fun open(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
