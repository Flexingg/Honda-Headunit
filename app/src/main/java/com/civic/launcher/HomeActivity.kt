package com.civic.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
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
    private lateinit var speedUnit: TextView
    private lateinit var carLine: TextView
    private lateinit var obdState: TextView
    private lateinit var nightBtn: TextView
    private lateinit var dock: LinearLayout
    private var nightScrim: View? = null
    private var lastPinnedKey = ""
    private val content by lazy { build() }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            updateClock()
            NightMode.apply(this@HomeActivity, nightScrim)
            nightBtn.text = NightMode.glyph(this@HomeActivity)
        }
    }
    private val carListener: (CarData) -> Unit = { d ->
        speed.text = CarBus.speed(this, d)
        speedUnit.text = " " + CarBus.speedUnit(this)
        carLine.text = "${CarBus.temp(this, d.coolantC)} coolant  ·  " +
            (if (d.volts.isNaN()) "--" else "%.1fV".format(d.volts))
        obdState.text = d.state
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        MediaBridge.register(this)
        startService(Intent(this, ObdService::class.java))
        nightScrim = glassScreen(content)
        // Fluid entrance: cards rise in, staggered.
        val mainRow = (content as LinearLayout).getChildAt(0) as LinearLayout
        for (i in 0 until mainRow.childCount) mainRow.getChildAt(i).apply {
            alpha = 0f; translationY = dp(24).toFloat()
            animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(380).start()
        }
    }

    private var themeId = -1

    override fun onResume() {
        super.onResume()
        if (themeId != -1 && themeId != Prefs.theme(this)) { recreate(); return }
        themeId = Prefs.theme(this)
        NightMode.apply(this, nightScrim)
        nightBtn.text = NightMode.glyph(this)
        speedUnit.text = " " + CarBus.speedUnit(this)
        refreshDock()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(tick, f)
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

    private fun adjustVolume(dir: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    private fun toggleNightMode() {
        val next = when (Prefs.nightMode(this)) {
            NightMode.AUTO -> if (NightMode.isNight(this)) NightMode.DAY else NightMode.NIGHT
            NightMode.NIGHT -> NightMode.DAY
            else -> NightMode.NIGHT
        }
        Prefs.setNightMode(this, next)
        NightMode.apply(this, nightScrim)
        nightBtn.text = NightMode.glyph(this)
        Toast.makeText(this, NightMode.label(next) + " mode", Toast.LENGTH_SHORT).show()
    }

    private fun refreshDock() {
        val pkgs = Prefs.pinnedApps(this)
        val key = pkgs.joinToString(",")
        if (key == lastPinnedKey && dock.childCount == pkgs.size) return
        lastPinnedKey = key
        dock.removeAllViews()
        val pm = packageManager
        for (pkg in pkgs) {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            val label = pm.getApplicationLabel(info).toString()
            val icon = pm.getApplicationIcon(info)
            val iv = ImageView(this).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(8) }
            }
            val item = glass(row(iv, text(label, 14f, 0.9f), gravity = Gravity.CENTER), 10) {
                runCatching { startActivity(intent) }.onFailure {
                    Toast.makeText(this, "Can't open $label", Toast.LENGTH_SHORT).show()
                }
            }
            dock.addView(item.weight(1f, true, 4))
        }
        dock.visibility = if (dock.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun build(): View {
        clock = text("--:--", 56f, face = LIGHT)
        date = text("", 15f, 0.7f)
        obdState = text("", 11f, 0.5f)

        val nightPill = pill(NightMode.glyph(this), 40, 16f) { toggleNightMode() }
        nightBtn = nightPill.getChildAt(0) as TextView
        val quickControls = row(
            pill("−", 40, 18f) { adjustVolume(AudioManager.ADJUST_LOWER) },
            pill("+", 40, 18f) { adjustVolume(AudioManager.ADJUST_RAISE) },
            nightPill,
            pill("⚙", 40, 16f) { startActivity(Intent(this, SettingsActivity::class.java)) },
            gravity = Gravity.CENTER_VERTICAL
        )

        val clockCard = glass(col(
            clock, date, obdState,
            View(this).weight(1f, false),
            quickControls
        ), 16) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        title = text("", 20f, face = MEDIUM); artist = text("", 14f, 0.7f)
        val playPill = pill("▶") { MediaBridge.playPause(this) }
        playBtn = playPill.getChildAt(0) as TextView
        val controls = row(
            pill("⏮") { MediaBridge.prev(this) },
            playPill,
            pill("⏭") { MediaBridge.next(this) },
            gravity = Gravity.CENTER
        )
        val mediaCard = glass(col(text("NOW PLAYING", 11f, 0.5f), title, artist,
            View(this).weight(1f, false), controls), 16) { openMusic() }

        val left = col(clockCard.weight(1.05f, false, 6), mediaCard.weight(1f, false, 6))

        // Maps card
        val mapsCard = glass(col(
            text("MAPS", 11f, 0.5f), text("Where to?", 24f, face = MEDIUM),
            View(this).weight(1f, false),
            row(chip("⌂  Home") { navigate(Prefs.home(this)) }, chip("◼  Work") { navigate(Prefs.work(this)) })
        ), 16) { openMaps() }

        speed = text("--", 34f, face = LIGHT)
        speedUnit = text(" " + CarBus.speedUnit(this), 13f, 0.6f)
        carLine = text("", 12f, 0.7f)
        val carCard = glass(col(text("CAR", 11f, 0.5f),
            row(speed, speedUnit, gravity = Gravity.BOTTOM), carLine), 14) {
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

        val mainRow = row(left.weight(1f), right.weight(1.25f))
        dock = row(gravity = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { topMargin = dp(2) }
            visibility = View.GONE
        }
        return col(mainRow.weight(1f, false), dock).apply {
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

    private fun openMaps() {
        val custom = Prefs.navApp(this)
        if (custom != null && launch(custom) != null) return
        launch("com.google.android.apps.maps") ?: launchUri("geo:0,0")
    }

    private fun openMusic() {
        val custom = Prefs.musicApp(this)
        if (custom != null && launch(custom) != null) return
        launch("com.spotify.music") ?: launch("com.google.android.music")
            ?: runCatching { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)) }
    }

    private fun launch(pkg: String): Unit? =
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            runCatching { startActivity(it) }.getOrNull()
        }

    private fun launchUri(uri: String): Unit? = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) { null }
}
