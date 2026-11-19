package com.civic.launcher

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
    }

    private fun render() {
        fun item(label: String, value: String, onClick: () -> Unit): View =
            glass(row(text(label, 16f).weight(1f), text(value, 14f, 0.6f).wrap()), 14, onClick)
                .also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) } }

        val brightnessSteps = floatArrayOf(0.10f, 0.25f, 0.40f, -1f)
        val curBright = Prefs.nightBrightness(this)
        val brightLabel = if (curBright < 0f) "System" else "${(curBright * 100).toInt()}%"
        val pinnedCount = Prefs.pinnedApps(this).size
        val hiddenCount = Prefs.hiddenApps(this).size

        val items = mutableListOf<View>(
            item("OBD adapter", Prefs.obdMac(this) ?: "auto (name contains OBD/ELM)") { pickObd() },
            item("Units", if (Prefs.imperial(this)) "mph / °F" else "km/h / °C") {
                Prefs.setImperial(this, !Prefs.imperial(this)); render()
            },
            item("Theme", arrayOf("Aurora", "Sunset", "Forest")[Prefs.theme(this)]) {
                Prefs.setTheme(this, (Prefs.theme(this) + 1) % 3); Backdrop.invalidate(); render()
            },
            item("Night mode", NightMode.label(Prefs.nightMode(this))) {
                Prefs.setNightMode(this, (Prefs.nightMode(this) + 1) % 3); render()
            },
            item("Night brightness", brightLabel) {
                val idx = brightnessSteps.indexOfFirst { Math.abs(it - curBright) < 0.02f }
                val next = brightnessSteps[(idx + 1) % brightnessSteps.size]
                Prefs.setNightBrightness(this, next); render()
            },
            item("Music app", appLabel(Prefs.musicApp(this))) {
                pickApp("Preferred music app") { Prefs.setMusicApp(this, it); render() }
            },
            item("Navigation app", appLabel(Prefs.navApp(this))) {
                pickApp("Preferred navigation app") { Prefs.setNavApp(this, it); render() }
            },
            item("Home address", Prefs.home(this).ifBlank { "not set" }) {
                ask("Home address", Prefs.home(this)) { Prefs.setHome(this, it) }
            },
            item("Work address", Prefs.work(this).ifBlank { "not set" }) {
                ask("Work address", Prefs.work(this)) { Prefs.setWork(this, it) }
            },
            item("Pinned apps", if (pinnedCount == 0) "long-press in Apps" else "$pinnedCount pinned (tap to clear)") {
                if (pinnedCount > 0) {
                    Prefs.setPinnedApps(this, emptyList())
                    Toast.makeText(this, "Cleared pinned apps", Toast.LENGTH_SHORT).show()
                    render()
                } else {
                    startActivity(Intent(this, AppsActivity::class.java))
                }
            }
        )
        if (hiddenCount > 0) {
            items += item("Hidden apps", "$hiddenCount hidden (tap to restore)") {
                Prefs.setHiddenApps(this, emptySet())
                Toast.makeText(this, "Restored all hidden apps", Toast.LENGTH_SHORT).show()
                render()
            }
        }
        items += item("Default launcher", "choose") {
            runCatching {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        items += item("System settings", "›") {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        items += item("Diagnostic probe", "recon dump ›") {
            startActivity(Intent(this, ProbeActivity::class.java))
        }

        val list = col(*items.toTypedArray())
        val header = row(pill("‹", 44) { finish() }, text("Settings", 22f, face = MEDIUM))
        glassScreen(col(header, ScrollView(this).apply { addView(list) }.weight(1f, false, 6)).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })
    }

    private fun appLabel(pkg: String?): String {
        if (pkg == null) return "auto"
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun pickApp(title: String, onPick: (String?) -> Unit) {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .sortedBy { it.first.lowercase() }
        val labels = arrayOf("Auto-detect") + installed.map { it.first }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK).setTitle(title)
            .setItems(labels) { _, i ->
                onPick(if (i == 0) null else installed[i - 1].second)
            }.show()
    }

    private fun pickObd() {
        val devs = BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList().orEmpty()
        if (devs.isEmpty()) {
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            return
        }
        AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK).setTitle("Paired devices")
            .setItems(devs.map { "${it.name}  (${it.address})" }.toTypedArray()) { _, i ->
                Prefs.setObdMac(this, devs[i].address); ObdService.restart(this); render()
            }.show()
    }

    private fun ask(title: String, cur: String, save: (String) -> Unit) {
        val e = EditText(this).apply { setText(cur) }
        AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK).setTitle(title).setView(e)
            .setPositiveButton("Save") { _, _ -> save(e.text.toString().trim()); render() }
            .setNegativeButton("Cancel", null).show()
    }
}
