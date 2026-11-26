package app.lumen.player.data

import android.content.Context
import android.os.Build
import app.lumen.player.BuildConfig
import java.util.Locale

/** 用户可主动复制的脱敏诊断信息；不包含服务器地址、账号或曲库内容。 */
object Diagnostics {
    fun summary(ctx: Context): String {
        val abi = Build.SUPPORTED_ABIS.joinToString(",")
        val locale = Locale.getDefault().toString()
        return buildString {
            appendLine("Lumen ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: $abi")
            appendLine("Locale: $locale")
            appendLine("Accounts: ${Accounts.list(ctx).size}")
        }.trim()
    }
}
