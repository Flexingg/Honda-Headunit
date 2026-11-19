package com.civic.launcher

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("civic", Context.MODE_PRIVATE)
    fun obdMac(c: Context): String? = p(c).getString("obd", null)
    fun setObdMac(c: Context, v: String) = p(c).edit().putString("obd", v).apply()
    fun imperial(c: Context) = p(c).getBoolean("imperial", true)
    fun setImperial(c: Context, v: Boolean) = p(c).edit().putBoolean("imperial", v).apply()
    fun theme(c: Context) = p(c).getInt("theme", 0)
    fun setTheme(c: Context, v: Int) = p(c).edit().putInt("theme", v).apply()
    fun home(c: Context): String = p(c).getString("home", "") ?: ""
    fun work(c: Context): String = p(c).getString("work", "") ?: ""
    fun setHome(c: Context, v: String) = p(c).edit().putString("home", v).apply()
    fun setWork(c: Context, v: String) = p(c).edit().putString("work", v).apply()

    // 0 = Auto (7pm-6:30am), 1 = Day, 2 = Night
    fun nightMode(c: Context) = p(c).getInt("night_mode", 0)
    fun setNightMode(c: Context, v: Int) = p(c).edit().putInt("night_mode", v).apply()

    // Window brightness in night mode (0.05..1.0, or -1f for system default)
    fun nightBrightness(c: Context) = p(c).getFloat("night_brightness", 0.25f)
    fun setNightBrightness(c: Context, v: Float) = p(c).edit().putFloat("night_brightness", v).apply()

    // Ordered list of pinned package names (up to 4)
    fun pinnedApps(c: Context): List<String> =
        (p(c).getString("pinned_apps", "") ?: "").split(',').filter { it.isNotBlank() }
    fun setPinnedApps(c: Context, pkgs: List<String>) =
        p(c).edit().putString("pinned_apps", pkgs.distinct().take(4).joinToString(",")).apply()

    // Hidden package names in the app drawer
    fun hiddenApps(c: Context): Set<String> =
        (p(c).getString("hidden_apps", "") ?: "").split(',').filter { it.isNotBlank() }.toSet()
    fun setHiddenApps(c: Context, pkgs: Set<String>) =
        p(c).edit().putString("hidden_apps", pkgs.joinToString(",")).apply()

    // Optional preferred apps for the Media and Maps cards (null = auto-detect)
    fun musicApp(c: Context): String? = p(c).getString("music_app", null)?.takeIf { it.isNotBlank() }
    fun setMusicApp(c: Context, pkg: String?) = p(c).edit().putString("music_app", pkg ?: "").apply()
    fun navApp(c: Context): String? = p(c).getString("nav_app", null)?.takeIf { it.isNotBlank() }
    fun setNavApp(c: Context, pkg: String?) = p(c).edit().putString("nav_app", pkg ?: "").apply()
}
