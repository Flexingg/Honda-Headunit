package com.civic.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny view DSL so we don't need AndroidX or layout inflation. */
fun Context.dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dp(v: Int) = dp(v.toFloat())

val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
val MEDIUM: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

fun Context.text(s: String = "", size: Float = 16f, alpha: Float = 1f, face: Typeface = Typeface.DEFAULT) =
    TextView(this).apply {
        text = s; textSize = size; setTextColor(0xFFFFFFFF.toInt()); this.alpha = alpha
        typeface = face; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END
    }

fun Context.col(vararg kids: View, gravity: Int = Gravity.START) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; this.gravity = gravity; kids.forEach { addView(it) }
}

fun Context.row(vararg kids: View, gravity: Int = Gravity.CENTER_VERTICAL) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; this.gravity = gravity; kids.forEach { addView(it) }
}

fun <T : View> T.weight(w: Float, horizontal: Boolean = true, marginDp: Int = 0): T {
    val c = context
    layoutParams = (if (horizontal) LinearLayout.LayoutParams(0, MATCH_PARENT, w)
    else LinearLayout.LayoutParams(MATCH_PARENT, 0, w)).apply {
        val m = c.dp(marginDp); setMargins(m, m, m, m)
    }
    return this
}

fun <T : View> T.wrap(): T { layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT); return this }

fun Context.glass(child: View, padDp: Int = 16, onClick: (() -> Unit)? = null) = GlassView(this).apply {
    setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
    addView(child, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    if (onClick != null) { pressable = true; isClickable = true; setOnClickListener { onClick() } }
}

/** Round glass icon button (text glyph = no image assets). */
fun Context.pill(glyph: String, sizeDp: Int = 52, onClick: () -> Unit) = GlassView(this, sizeDp / 2f).apply {
    pressable = true; isClickable = true; setOnClickListener { onClick() }
    addView(text(glyph, 20f).apply { gravity = Gravity.CENTER },
        FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { setMargins(dp(6), 0, dp(6), 0) }
}

/** Wraps a screen: backdrop + content, immersive. */
fun Activity.glassScreen(content: View) {
    Backdrop.ensure(this)
    val root = FrameLayout(this)
    root.addView(BackdropView(this), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    setContentView(root)
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
}
