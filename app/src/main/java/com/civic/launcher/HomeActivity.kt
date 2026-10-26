package com.civic.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playBtn: TextView
    private lateinit var speed: TextView
    private lateinit var carLine: TextView
    private lateinit var obdState: TextView
    private val content by lazy { build() }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }
    private val carListener: (CarData) -> Unit = { d ->
        speed.text = CarBus.speed(this, d)
        carLine.text = "${CarBus.temp(this, d.coolantC)} coolant  ·  " +
            (if (d.volts.isNaN()) "--" else "%.1fV".format(d.volts))
        obdState.text = d.state
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        MediaBridge.register(this)
        startService(Intent(this, ObdService::class.java))
        glassScreen(content)
        // Fluid entrance: cards rise in, staggered.
        (content as LinearLayout).let { root ->
            for (i in 0 until root.childCount) root.getChildAt(i).apply {
                alpha = 0f; translationY = dp(24).toFloat()
                animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(380).start()
            }
        }
    }

    private var themeId = -1

    override fun onResume() {
        super.onResume()
        if (themeId != -1 && themeId != Prefs.theme(this)) { recreate(); return }
        themeId = Prefs.theme(this)
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
        updateClock()
        MediaBridge.listener = ::showTrack; showTrack(MediaBridge.last)
        CarBus.add(carListener)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(tick) }; MediaBridge.listener = null; CarBus.remove(carListener)
    }

    override fun onBackPressed() { /* launcher: swallow */ }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now)
    }

    private fun showTrack(t: MediaBridge.Track) {
        title.text = t.title; artist.text = t.artist
        playBtn.text = if (t.playing) "❚❚" else "▶"
    }

    private fun build(): View {
        clock = text("--:--", 64f, face = LIGHT)
        date = text("", 16f, 0.7f)
        obdState = text("", 12f, 0.5f)

        val clockCard = glass(col(clock, date, View(this).weight(1f, false), obdState), 20) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        title = text("", 20f, face = MEDIUM); artist = text("", 14f, 0.7f)
        playBtn = text("▶", 20f).apply { gravity = Gravity.CENTER }
        val controls = row(
            pill("⏮") { MediaBridge.prev(this) },
            pill("▶") { MediaBridge.playPause(this) }.also { playBtn = it.getChildAt(0) as TextView },
            pill("⏭") { MediaBridge.next(this) },
            gravity = Gravity.CENTER
        )
        val mediaCard = glass(col(text("NOW PLAYING", 11f, 0.5f), title, artist,
            View(this).weight(1f, false), controls), 18) { openMusic() }

        val left = col(clockCard.weight(1f, false, 6), mediaCard.weight(1.15f, false, 6))

        // Maps card
        val mapsCard = glass(col(
            text("MAPS", 11f, 0.5f), text("Where to?", 24f, face = MEDIUM),
            View(this).weight(1f, false),
            row(chip("⌂  Home") { navigate(Prefs.home(this)) }, chip("◼  Work") { navigate(Prefs.work(this)) })
        ), 18) { launch("com.google.android.apps.maps") ?: launchUri("geo:0,0") }

        speed = text("--", 34f, face = LIGHT); carLine = text("", 12f, 0.7f)
        val carCard = glass(col(text("CAR", 11f, 0.5f),
            row(speed, text(" " + CarBus.speedUnit(this), 13f, 0.6f), gravity = Gravity.BOTTOM), carLine), 14) {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        val appsCard = glass(col(text("⋮⋮", 30f), text("Apps", 14f, 0.8f), gravity = Gravity.CENTER), 12) {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        val phoneCard = glass(col(text("✆", 30f), text("Phone", 14f, 0.8f), gravity = Gravity.CENTER), 12) {
            launchUri("tel:") 
        }
        val right = col(
            mapsCard.weight(1.1f, false, 6),
            row(carCard.weight(1.6f, true, 6), phoneCard.weight(1f, true, 6), appsCard.weight(1f, true, 6))
                .weight(1f, false)
        )

        return row(left.weight(1f), right.weight(1.25f)).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
    }

    private fun chip(label: String, onClick: () -> Unit) = GlassView(this, 18f).apply {
        pressable = true; isClickable = true; setOnClickListener { onClick() }
        setPadding(dp(16), dp(8), dp(16), dp(8))
        addView(text(label, 14f))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
    }

    private fun navigate(addr: String) {
        if (addr.isBlank()) {
            Toast.makeText(this, "Set this address in Settings", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java)); return
        }
        launchUri("google.navigation:q=" + Uri.encode(addr)) ?: launchUri("geo:0,0?q=" + Uri.encode(addr))
    }

    private fun openMusic() {
        launch("com.spotify.music") ?: launch("com.google.android.music")
            ?: runCatching { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)) }
    }

    private fun launch(pkg: String): Unit? =
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }

    private fun launchUri(uri: String): Unit? = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) { null }
}
