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
}
