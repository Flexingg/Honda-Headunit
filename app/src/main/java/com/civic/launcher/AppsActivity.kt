package com.civic.launcher

import android.app.Activity
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

@Suppress("DEPRECATION")
class AppsActivity : Activity() {
    private class App(val label: String, val icon: Drawable, val launch: Intent)
    private var apps = emptyList<App>()
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
            (v.getChildAt(0) as ImageView).setImageDrawable(apps[p].icon)
            (v.getChildAt(1) as android.widget.TextView).text = apps[p].label
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
                    view.scaleX = 1f; view.scaleY = 1f; startActivity(apps[p].launch)
                }.start()
            }
        }
        val header = row(pill("‹", 44) { finish() }, text("Apps", 22f, face = MEDIUM))
        glassScreen(col(header, glass(grid, 8).weight(1f, false, 6)).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })
        object : AsyncTask<Unit, Unit, List<App>>() {
            override fun doInBackground(vararg p: Unit): List<App> {
                val pm = packageManager
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                return pm.queryIntentActivities(q, 0)
                    .filter { it.activityInfo.packageName != packageName }
                    .map { r: ResolveInfo ->
                        App(r.loadLabel(pm).toString(), r.loadIcon(pm),
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                    }.sortedBy { it.label.lowercase() }
            }
            override fun onPostExecute(r: List<App>) { apps = r; adapter.notifyDataSetChanged() }
        }.execute()
    }
}
