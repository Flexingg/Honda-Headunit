package com.civic.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

@Suppress("DEPRECATION")
class AppsActivity : Activity() {
    private class App(val pkg: String, val label: String, val icon: Drawable, val launch: Intent)
    private var apps = emptyList<App>()
    private var pinned = emptySet<String>()
    private lateinit var grid: GridView

    private val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val v = (convert as? LinearLayout) ?: col(
                ImageView(this@AppsActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)) },
                text("", 12f, 0.85f).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, -2) },
                gravity = Gravity.CENTER
            ).apply { setPadding(0, dp(10), 0, dp(10)) }
            val app = apps[p]
            (v.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (v.getChildAt(1) as TextView).text = if (app.pkg in pinned) "★ ${app.label}" else app.label
            return v
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        grid = GridView(this).apply {
            numColumns = 6; verticalSpacing = dp(4); selector = android.graphics.drawable.ColorDrawable(0)
            adapter = this@AppsActivity.adapter
            setOnItemClickListener { _, view, p, _ ->
                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                    view.scaleX = 1f; view.scaleY = 1f
                    runCatching { startActivity(apps[p].launch) }.onFailure {
                        Toast.makeText(this@AppsActivity, "Can't open ${apps[p].label}", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
            setOnItemLongClickListener { _, _, p, _ ->
                showAppMenu(apps[p])
                true
            }
        }
        val header = row(
            pill("‹", 44) { finish() },
            text("Apps", 22f, face = MEDIUM),
            View(this).weight(1f),
            text("Long-press to pin or hide", 12f, 0.55f)
        )
        glassScreen(col(header, glass(grid, 8).weight(1f, false, 6)).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })
        loadApps()
    }

    private fun showAppMenu(app: App) {
        val curPinned = Prefs.pinnedApps(this).toMutableList()
        val isPinned = app.pkg in curPinned
        val pinLabel = if (isPinned) "Unpin from Home" else "Pin to Home"
        AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
            .setTitle(app.label)
            .setItems(arrayOf(pinLabel, "Hide from drawer")) { _, which ->
                when (which) {
                    0 -> {
                        if (isPinned) {
                            curPinned.remove(app.pkg)
                            Prefs.setPinnedApps(this, curPinned)
                            Toast.makeText(this, "Unpinned ${app.label}", Toast.LENGTH_SHORT).show()
                        } else if (curPinned.size >= 4) {
                            Toast.makeText(this, "Max 4 pinned apps — unpin one first", Toast.LENGTH_SHORT).show()
                        } else {
                            curPinned.add(app.pkg)
                            Prefs.setPinnedApps(this, curPinned)
                            Toast.makeText(this, "Pinned ${app.label} to Home", Toast.LENGTH_SHORT).show()
                        }
                        pinned = Prefs.pinnedApps(this).toSet()
                        adapter.notifyDataSetChanged()
                    }
                    1 -> {
                        if (isPinned) {
                            curPinned.remove(app.pkg)
                            Prefs.setPinnedApps(this, curPinned)
                        }
                        Prefs.setHiddenApps(this, Prefs.hiddenApps(this) + app.pkg)
                        Toast.makeText(this, "Hidden ${app.label} (restore in Settings)", Toast.LENGTH_SHORT).show()
                        loadApps()
                    }
                }
            }.show()
    }

    private fun loadApps() {
        pinned = Prefs.pinnedApps(this).toSet()
        val hidden = Prefs.hiddenApps(this)
        object : AsyncTask<Unit, Unit, List<App>>() {
            override fun doInBackground(vararg p: Unit): List<App> {
                val pm = packageManager
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                return pm.queryIntentActivities(q, 0)
                    .filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName !in hidden }
                    .map { r: ResolveInfo ->
                        App(
                            r.activityInfo.packageName,
                            r.loadLabel(pm).toString(),
                            r.loadIcon(pm),
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        )
                    }.sortedBy { it.label.lowercase() }
            }
            override fun onPostExecute(r: List<App>) { apps = r; adapter.notifyDataSetChanged() }
        }.execute()
    }
}
