package app.lumen.player.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统计长图渲染器：用 Canvas 离屏绘制整份报告（不依赖屏幕可见区域），
 * 因此导出的是完整长图，而不是当前视口截图。
 */
object StatsReport {

    private const val W = 1080
    private const val PAD = 56f

    /** 导出配色：直接取当前主题，保证长图与 App 内观感一致。 */
    data class Palette(
        val backgroundTop: Int,
        val backgroundBottom: Int,
        val card: Int,
        val track: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val accent: Int,
    )

    fun render(
        d: StatsDashboard,
        ins: StatsInsights,
        rangeLabel: String,
        mood: List<Pair<String, Int>>,
        analyzed: Int,
        palette: Palette,
        title: String,
        labels: ReportLabels,
    ): Bitmap {
        val heightGuess = 4200
        val bmp = Bitmap.createBitmap(W, heightGuess, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val accent = palette.accent
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, heightGuess.toFloat(),
                intArrayOf(palette.backgroundTop, palette.backgroundBottom, palette.backgroundTop),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), heightGuess.toFloat(), bg)

        val titlePaint = text(46f, palette.textPrimary, bold = true)
        val subPaint = text(24f, palette.textSecondary)
        val labelPaint = text(22f, palette.textSecondary)
        val valuePaint = text(58f, palette.textPrimary, bold = true)
        val sectionPaint = text(30f, palette.textPrimary, bold = true)
        val smallPaint = text(22f, palette.textPrimary)

        kpiCard = palette.card
        trackColor = palette.track
        var y = 96f
        c.drawText(title, PAD, y, titlePaint)
        y += 40f
        c.drawText(rangeLabel, PAD, y, subPaint)
        y += 30f
        c.drawText(dateLine(), PAD, y, subPaint)
        y += 56f

        // KPI 2x2
        val cardW = (W - PAD * 2 - 28f) / 2f
        val cardH = 190f
        kpi(c, PAD, y, cardW, cardH, labels.plays, d.plays.toString(), labelPaint, valuePaint)
        kpi(c, PAD + cardW + 28f, y, cardW, cardH, labels.duration, duration(d.listenSeconds), labelPaint, valuePaint)
        y += cardH + 28f
        kpi(c, PAD, y, cardW, cardH, labels.tracks, d.tracks.toString(), labelPaint, valuePaint)
        kpi(c, PAD + cardW + 28f, y, cardW, cardH, labels.clients, d.clients.toString(), labelPaint, valuePaint)
        y += cardH + 64f

        // 活跃趋势
        c.drawText(labels.activity, PAD, y, sectionPaint)
        y += 44f
        c.drawText(
            labels.avgLine.format(ins.avgPerDay, ins.activeDays, ins.peakDay?.count ?: 0),
            PAD, y, smallPaint,
        )
        y += 28f
        c.drawText(labels.trendLine.format(ins.trendPct), PAD, y, smallPaint)
        y += 40f
        y = bars(c, d.days.map { it.count }, PAD, y, W - PAD * 2, 260f, accent)
        y += 56f

        // 分类汇总：星期
        c.drawText(labels.byWeekday, PAD, y, sectionPaint)
        y += 40f
        y = hbars(c, labels.weekdays, ins.weekday.toList(), PAD, y, W - PAD * 2, accent, smallPaint)

        y += 40f
        c.drawText(labels.byHour, PAD, y, sectionPaint)
        y += 36f
        y = heat(c, ins.hourCounts, PAD, y, W - PAD * 2, 56f, accent)
        run {
            val hp = text(18f, palette.textSecondary)
            listOf(0, 6, 12, 18, 23).forEachIndexed { i, hour ->
                val tx = PAD + (W - PAD * 2 - 40f) * (hour / 23f)
                c.drawText("%02d".format(hour), tx, y + 26f, hp)
            }
        }
        y += 56f

        // 排行榜
        c.drawText(labels.topArtists, PAD, y, sectionPaint)
        y += 40f
        y = ranks(c, d.artists.take(6).map { it.name to it.count }, PAD, y, W - PAD * 2, accent, smallPaint)
        y += 28f
        c.drawText(labels.topAlbums, PAD, y, sectionPaint)
        y += 40f
        y = ranks(c, d.albums.take(6).map { (it.name + " · " + it.artist) to it.count }, PAD, y, W - PAD * 2, accent, smallPaint)

        // 本机数据 + 心情
        y += 44f
        c.drawText(labels.localData, PAD, y, sectionPaint)
        y += 40f
        c.drawText(labels.analyzedLine.format(analyzed), PAD, y, smallPaint)
        y += 32f
        if (mood.isNotEmpty()) {
            c.drawText(labels.moodLine.format(mood.joinToString(" · ") { "${it.first} ${it.second}" }), PAD, y, smallPaint)
            y += 32f
        }

        // 最近播放
        if (d.history.isNotEmpty()) {
            y += 20f
            c.drawText(labels.recent, PAD, y, sectionPaint)
            y += 40f
            d.history.take(8).forEach { h ->
                c.drawText(ellipsize(h.title, smallPaint, W - PAD * 2 - 160f), PAD, y, smallPaint)
                c.drawText(shortTime(h.playedAt), W - PAD - 110f, y, smallPaint)
                y += 34f
                c.drawText(ellipsize(h.artist, smallPaint, W - PAD * 2), PAD, y, text(20f, palette.textSecondary))
                y += 34f
            }
        }

        y += 40f
        c.drawText(labels.footer, PAD, y, text(20f, palette.textSecondary))
        y += 60f

        val h = y.toInt().coerceAtMost(heightGuess)
        return Bitmap.createBitmap(bmp, 0, 0, W, h)
    }

    data class ReportLabels(
        val plays: String,
        val duration: String,
        val tracks: String,
        val clients: String,
        val activity: String,
        val avgLine: String,
        val trendLine: String,
        val byWeekday: String,
        val weekdays: List<String>,
        val byHour: String,
        val topArtists: String,
        val topAlbums: String,
        val localData: String,
        val analyzedLine: String,
        val moodLine: String,
        val recent: String,
        val footer: String,
    )

    /** 把主色向白色插值，保证在深色背景上有足够对比 */
    private fun lighten(color: Int, f: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * f).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * f).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private var kpiCard: Int = Color.parseColor("#1F2529")
    private var trackColor: Int = Color.parseColor("#242B30")

    private fun kpi(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        label: String, value: String, labelPaint: Paint, valuePaint: Paint,
    ) {
        val card = Paint().apply { color = kpiCard; isAntiAlias = true }
        c.drawRoundRect(RectF(x, y, x + w, y + h), 28f, 28f, card)
        c.drawText(label, x + 32f, y + 56f, labelPaint)
        c.drawText(value, x + 32f, y + 132f, valuePaint)
    }

    private fun bars(
        c: Canvas, values: List<Int>, x: Float, y: Float, w: Float, h: Float, accent: Int,
    ): Float {
        if (values.isEmpty()) return y
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val gap = w * 0.02f / values.size
        val bw = (w - gap * (values.size - 1)) / values.size
        val track = Paint().apply { color = trackColor; isAntiAlias = true }
        val bar = Paint().apply { color = lighten(accent, 0.55f); isAntiAlias = true }
        values.forEachIndexed { i, v ->
            val bx = x + i * (bw + gap)
            c.drawRoundRect(RectF(bx, y, bx + bw, y + h), bw / 2f, bw / 2f, track)
            val bh = h * (v / max.toFloat())
            if (bh > 1f) {
                c.drawRoundRect(RectF(bx, y + h - bh, bx + bw, y + h), bw / 2f, bw / 2f, bar)
            }
        }
        return y + h
    }

    private fun hbars(
        c: Canvas, labels: List<String>, values: List<Int>, x: Float, y: Float, w: Float,
        accent: Int, paint: Paint,
    ): Float {
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        var yy = y
        val track = Paint().apply { color = trackColor; isAntiAlias = true }
        val bar = Paint().apply { color = lighten(accent, 0.55f); isAntiAlias = true }
        labels.forEachIndexed { i, label ->
            val v = values.getOrElse(i) { 0 }
            c.drawText(label, x, yy + 18f, paint)
            val bx = x + 56f
            val bw = w - 140f
            c.drawRoundRect(RectF(bx, yy, bx + bw, yy + 16f), 8f, 8f, track)
            val fill = bw * (v / max.toFloat())
            if (fill > 1f) c.drawRoundRect(RectF(bx, yy, bx + fill, yy + 16f), 8f, 8f, bar)
            c.drawText(v.toString(), bx + bw + 16f, yy + 18f, paint)
            yy += 40f
        }
        return yy
    }

    private fun heat(c: Canvas, counts: IntArray, x: Float, y: Float, w: Float, h: Float, accent: Int): Float {
        val hot = lighten(accent, 0.55f)
        val max = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
        val gap = 4f
        val cell = (w - gap * 23) / 24f
        val base = Paint().apply { isAntiAlias = true }
        for (i in 0 until 24) {
            val v = counts.getOrElse(i) { 0 }
            val alpha = (0.10f + 0.90f * (v / max.toFloat())).coerceIn(0.10f, 1f)
            base.color = Color.argb((alpha * 255).toInt(), Color.red(hot), Color.green(hot), Color.blue(hot))
            val bx = x + i * (cell + gap)
            c.drawRoundRect(RectF(bx, y, bx + cell, y + h), 8f, 8f, base)
        }
        return y + h
    }

    private fun ranks(
        c: Canvas, rows: List<Pair<String, Int>>, x: Float, y: Float, w: Float, accent: Int, paint: Paint,
    ): Float {
        if (rows.isEmpty()) return y
        val max = (rows.maxOf { it.second }).coerceAtLeast(1)
        var yy = y
        val track = Paint().apply { color = trackColor; isAntiAlias = true }
        val bar = Paint().apply { color = lighten(accent, 0.55f); isAntiAlias = true }
        rows.forEachIndexed { i, (name, count) ->
            c.drawText("${i + 1}", x, yy + 18f, paint)
            c.drawText(ellipsize(name, paint, w - 140f), x + 34f, yy + 18f, paint)
            c.drawText(count.toString(), x + w - 46f, yy + 18f, paint)
            val bw = w - 60f
            c.drawRoundRect(RectF(x + 34f, yy + 26f, x + 34f + bw, yy + 32f), 3f, 3f, track)
            val fill = bw * (count / max.toFloat())
            if (fill > 1f) c.drawRoundRect(RectF(x + 34f, yy + 26f, x + 34f + fill, yy + 32f), 3f, 3f, bar)
            yy += 52f
        }
        return yy
    }

    private fun ellipsize(s: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(s) <= maxWidth) return s
        var text = s
        while (text.length > 1 && paint.measureText(text + "…") > maxWidth) text = text.dropLast(1)
        return text + "…"
    }

    private fun duration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun shortTime(raw: String): String {
        val t = parsePlayedAt(raw) ?: return ""
        return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
    }

    private fun dateLine(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
