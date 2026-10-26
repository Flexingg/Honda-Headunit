package com.civic.launcher

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ScrollView

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
    }

    private fun render() {
        fun item(label: String, value: String, onClick: () -> Unit): View =
            glass(row(text(label, 16f).weight(1f), text(value, 14f, 0.6f).wrap()), 14, onClick)
                .also { it.layoutParams = android.widget.LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) } }

        val list = col(
            item("OBD adapter", Prefs.obdMac(this) ?: "auto (name contains OBD/ELM)") { pickObd() },
            item("Units", if (Prefs.imperial(this)) "mph / °F" else "km/h / °C") {
                Prefs.setImperial(this, !Prefs.imperial(this)); render()
            },
            item("Theme", arrayOf("Aurora", "Sunset", "Forest")[Prefs.theme(this)]) {
                Prefs.setTheme(this, (Prefs.theme(this) + 1) % 3); Backdrop.invalidate(); render()
            },
            item("Home address", Prefs.home(this).ifBlank { "not set" }) { ask("Home address", Prefs.home(this)) { Prefs.setHome(this, it) } },
            item("Work address", Prefs.work(this).ifBlank { "not set" }) { ask("Work address", Prefs.work(this)) { Prefs.setWork(this, it) } },
            item("Default launcher", "choose") {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            item("System settings", "›") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        )
        val header = row(pill("‹", 44) { finish() }, text("Settings", 22f, face = MEDIUM))
        glassScreen(col(header, ScrollView(this).apply { addView(list) }.weight(1f, false, 6)).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })
    }

    private fun pickObd() {
        val devs = BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList().orEmpty()
        if (devs.isEmpty()) { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return }
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
