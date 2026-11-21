package com.example.app.data

import android.content.Context

/** 单曲本地评分（0.0 - 10.0，步径 0.1），按 trackId 存 SharedPreferences。 */
object Ratings {
    private const val PREFS = "lumen_ratings"

    @Synchronized
    fun get(ctx: Context, trackId: String): Float =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(trackId, 0f)

    @Synchronized
    fun set(ctx: Context, trackId: String, value: Float) {
        val v = (Math.round(value * 10f) / 10f).coerceIn(0f, 10f)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(trackId, v).apply()
    }
}