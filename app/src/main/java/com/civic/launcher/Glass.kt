package com.civic.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * "Fake real-time" glass: the backdrop is static, so we blur it ONCE (RenderScript,
 * available since API 17) at 1/4 resolution and every glass panel samples the blurred
 * bitmap at its own screen position via a BitmapShader. Zero per-frame blur cost,
 * and it works with hardware acceleration on 4.2 (no clipPath needed).
 */
@Suppress("DEPRECATION")
object Backdrop {
    private const val SCALE = 4
    var sharp: Bitmap? = null; private set
    var blurred: Bitmap? = null; private set
    var screenW = 0; private set
    var screenH = 0; private set

    fun ensure(ctx: Context) {
        val dm = ctx.resources.displayMetrics
        val w = maxOf(dm.widthPixels, dm.heightPixels)
        val h = minOf(dm.widthPixels, dm.heightPixels)
        if (sharp != null && w == screenW && h == screenH) return
        screenW = w; screenH = h
        val bw = w / SCALE; val bh = h / SCALE
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        paintAurora(Canvas(bmp), bw.toFloat(), bh.toFloat(), Prefs.theme(ctx))
        sharp = bmp
        blurred = blur(ctx, blur(ctx, bmp, 25f), 25f)
    }

    fun invalidate() { sharp = null }

    /** ColorOS-style soft colour blobs on a deep base. */
    private fun paintAurora(c: Canvas, w: Float, h: Float, theme: Int) {
        val palettes = arrayOf(
            intArrayOf(0xFF0B1020.toInt(), 0xFF2D6BFF.toInt(), 0xFF8A3FFC.toInt(), 0xFF00C2A8.toInt()),
            intArrayOf(0xFF140A0A.toInt(), 0xFFFF5A36.toInt(), 0xFFFFB020.toInt(), 0xFFB0204A.toInt()),
            intArrayOf(0xFF061412.toInt(), 0xFF12B886.toInt(), 0xFF1C7ED6.toInt(), 0xFF94D82D.toInt())
        )
        val p = palettes[theme.coerceIn(0, palettes.size - 1)]
        c.drawColor(p[0])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun blob(cx: Float, cy: Float, r: Float, color: Int) {
            paint.shader = RadialGradient(cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, paint)
        }
        blob(w * 0.15f, h * 0.2f, w * 0.45f, p[1] and 0xCCFFFFFF.toInt())
        blob(w * 0.85f, h * 0.3f, w * 0.40f, p[2] and 0xBBFFFFFF.toInt())
        blob(w * 0.55f, h * 1.0f, w * 0.50f, p[3] and 0x99FFFFFF.toInt())
    }

    private fun blur(ctx: Context, src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val rs = RenderScript.create(ctx)
        try {
            val a = Allocation.createFromBitmap(rs, src)
            val b = Allocation.createFromBitmap(rs, out)
            ScriptIntrinsicBlur.create(rs, Element.U8_4(rs)).apply {
                setRadius(radius); setInput(a); forEach(b)
            }
            b.copyTo(out)
        } finally { rs.destroy() }
        return out
    }
}

/** Full-screen wallpaper view (draws the sharp backdrop, upscaled with filtering). */
class BackdropView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    override fun onDraw(c: Canvas) {
        val b = Backdrop.sharp ?: return
        dst.set(0, 0, width, height)
        c.drawBitmap(b, null, dst, paint)
    }
}

/**
 * Frosted-glass container. Children are laid out normally (FrameLayout).
 * Press feedback: quick shrink, overshoot release — the "fluid" feel.
 */
open class GlassView(ctx: Context, private val radiusDp: Float = 22f) : FrameLayout(ctx) {
    private val r = radiusDp * resources.displayMetrics.density
    private val rect = RectF()
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density; color = 0x40FFFFFF
    }
    private val loc = IntArray(2)
    private val m = Matrix()
    var pressable = false

    init { setWillNotDraw(false) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        shine.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        updateShader()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, rr: Int, b: Int) {
        super.onLayout(changed, l, t, rr, b); updateShader()
    }

    private fun updateShader() {
        val bmp = Backdrop.blurred ?: return
        if (Backdrop.screenW == 0) return
        getLocationInWindow(loc)
        val sx = rootView.width.toFloat().coerceAtLeast(1f) / bmp.width
        val sy = rootView.height.toFloat().coerceAtLeast(1f) / bmp.height
        m.reset(); m.setScale(sx, sy); m.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
        val s = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        s.setLocalMatrix(m); blurPaint.shader = s
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(rect, r, r, blurPaint)
        c.drawRoundRect(rect, r, r, tint)
        c.drawRoundRect(rect, r, r, shine)
        rect.inset(stroke.strokeWidth / 2, stroke.strokeWidth / 2)
        c.drawRoundRect(rect, r, r, stroke)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (pressable) when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                .setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f)
                .setDuration(260).setInterpolator(OvershootInterpolator(2.5f)).start()
        }
        return super.dispatchTouchEvent(e)
    }
}
