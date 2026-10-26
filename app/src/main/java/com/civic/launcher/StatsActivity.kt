package com.civic.launcher

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/** Arc gauge with eased needle (animated only when the value changes). */
class ArcGauge(ctx: Context, private val max: Float, private val color: Int) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = ctx.dp(8).toFloat(); this.color = 0x22FFFFFF
    }
    private val fill = Paint(track).apply { this.color = color }
    private val box = RectF()
    private var shown = 0f
    private var anim: android.animation.ValueAnimator? = null

    fun set(v: Float) {
        val t = if (v.isNaN()) 0f else (v / max).coerceIn(0f, 1f)
        if (Math.abs(t - shown) < 0.002f) return
        anim?.cancel()
        anim = android.animation.ValueAnimator.ofFloat(shown, t).apply {
            duration = 300; interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }; start()
        }
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) - track.strokeWidth * 2
        box.set((width - s) / 2, (height - s) / 2, (width + s) / 2, (height + s) / 2)
        c.drawArc(box, 135f, 270f, false, track)
        if (shown > 0f) c.drawArc(box, 135f, 270f * shown, false, fill)
    }
}

class StatsActivity : Activity() {
    private class Cell(val gauge: ArcGauge, val value: android.widget.TextView)
    private val cells = HashMap<String, Cell>()

    private val listener: (CarData) -> Unit = { d ->
        fun set(k: String, raw: Float, label: String) { cells[k]?.apply { gauge.set(raw); value.text = label } }
        set("speed", d.speedKmh, CarBus.speed(this, d))
        set("rpm", d.rpm, if (d.rpm.isNaN()) "--" else "%.0f".format(d.rpm))
        set("cool", d.coolantC, CarBus.temp(this, d.coolantC))
        set("iat", d.intakeC, CarBus.temp(this, d.intakeC))
        set("thr", d.throttle, pct(d.throttle))
        set("load", d.load, pct(d.load))
        set("fuel", d.fuel, pct(d.fuel))
        set("volt", d.volts - 10f, if (d.volts.isNaN()) "--" else "%.1fV".format(d.volts))
        title.text = d.state
    }
    private lateinit var title: android.widget.TextView

    private fun pct(v: Float) = if (v.isNaN()) "--" else "%.0f%%".format(v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = text("", 13f, 0.6f)
        val specs = listOf(
            arrayOf<Any>("speed", "Speed " + CarBus.speedUnit(this), 200f, 0xFF4DABF7),
            arrayOf<Any>("rpm", "RPM", 7000f, 0xFFFF6B6B),
            arrayOf<Any>("cool", "Coolant", 120f, 0xFFFFA94D),
            arrayOf<Any>("iat", "Intake air", 80f, 0xFF63E6BE),
            arrayOf<Any>("thr", "Throttle", 100f, 0xFFB197FC),
            arrayOf<Any>("load", "Engine load", 100f, 0xFFFFD43B),
            arrayOf<Any>("fuel", "Fuel", 100f, 0xFF69DB7C),
            arrayOf<Any>("volt", "Battery", 5f, 0xFF74C0FC)  // 10–15 V mapped to 0–5
        )
        fun cell(s: Array<Any>): View {
            val g = ArcGauge(this, s[2] as Float, (s[3] as Long).toInt())
            val v = text("--", 22f, face = LIGHT).apply { gravity = Gravity.CENTER }
            cells[s[0] as String] = Cell(g, v)
            val f = FrameLayout(this)
            f.addView(g, FrameLayout.LayoutParams(-1, -1))
            f.addView(v, FrameLayout.LayoutParams(-1, -1))
            return glass(col(f.weight(1f, false), text(s[1] as String, 12f, 0.7f).apply { gravity = Gravity.CENTER }
                .also { it.layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2) }), 10)
        }
        val r1 = row(*specs.take(4).map { cell(it).weight(1f, true, 6) }.toTypedArray())
        val r2 = row(*specs.drop(4).map { cell(it).weight(1f, true, 6) }.toTypedArray())
        val header = row(pill("‹", 44) { finish() }, text("Car stats", 22f, face = MEDIUM),
            View(this).weight(1f), title)
        glassScreen(col(header, r1.weight(1f, false), r2.weight(1f, false)).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })
    }

    override fun onResume() { super.onResume(); CarBus.add(listener) }
    override fun onPause() { super.onPause(); CarBus.remove(listener) }
}
