package app.lumen.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

object ExternalLinks {
    const val BILIBILI_URL = "https://space.bilibili.com/20567705"
    const val FEEDBACK_NAME = "bilibili@晓月枫"

    fun open(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
