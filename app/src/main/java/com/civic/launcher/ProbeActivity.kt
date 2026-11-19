package com.civic.launcher

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic dump for finding the head unit's internal vehicle/climate
 * service once ADB access is available (see docs/HACKING.md).
 *
 * Not wired into normal launcher navigation — it's not something the
 * driver should stumble into. Launch it with:
 *   adb shell am start -n com.civic.launcher/.ProbeActivity
 *
 * Everything here is read-only recon: build props, root availability,
 * installed packages (filtered for vehicle/climate/CAN keywords), the
 * full binder service registry, running services, /dev nodes, and
 * open sockets. The goal is spotting the channel the factory climate
 * app uses to reach B-CAN without needing a full /system pull first.
 */
class ProbeActivity : Activity() {
    private lateinit var out: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        out = text("", 11f, face = android.graphics.Typeface.MONOSPACE).apply {
            setSingleLine(false)
            ellipsize = null
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        val header = row(
            pill("‹", 44) { finish() },
            text("Probe", 22f, face = MEDIUM),
            View(this).weight(1f),
            pill("↻", 44) { run() },
            pill("⤓", 44) { save() }
        )
        glassScreen(col(
            header,
            glass(ScrollView(this).apply { addView(out, LinearLayout.LayoutParams(-1, -2)) }, 10)
                .weight(1f, false, 6)
        ).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) })
        run()
    }

    private fun run() {
        val sb = StringBuilder()
        section(sb, "build") { buildProps() }
        section(sb, "root") { rootCheck() }
        section(sb, "packages matching vehicle/climate/CAN keywords") { packages() }
        section(sb, "all packages") { allPackages() }
        section(sb, "binder services (ServiceManager.listServices)") { binderServices() }
        section(sb, "running services (ActivityManager)") { runningServices() }
        section(sb, "/dev nodes") { devNodes() }
        section(sb, "/proc/net/unix") { procFile("/proc/net/unix") }
        section(sb, "/proc/net/tcp") { procFile("/proc/net/tcp") }
        section(sb, "mounts") { mounts() }
        out.text = sb.toString()
    }

    private fun section(sb: StringBuilder, title: String, body: () -> String) {
        sb.append("\n== ").append(title).append(" ==\n")
        sb.append(try { body() } catch (e: Throwable) { "error: $e" }).append("\n")
    }

    // ---- individual probes ----

    private fun buildProps(): String {
        val keys = listOf(
            "ro.build.fingerprint", "ro.build.version.release", "ro.build.version.sdk",
            "ro.product.model", "ro.product.manufacturer", "ro.hardware",
            "ro.secure", "ro.debuggable", "service.adb.tcp.port"
        )
        val props = keys.joinToString("\n") { "$it = ${getprop(it)}" }
        return "$props\ngetenforce = ${exec("getenforce")}"
    }

    private fun rootCheck(): String =
        "which su: ${exec("which su")}\nid: ${exec("id")}\nsu -c id: ${exec("su -c id")}"

    private val keywords = listOf(
        "honda", "mitsubishi", "andromeda", "climate", "hvac", "vehicle",
        "can", "diag", "obd", "vci", "body", "ecu", "bus"
    )

    private fun packages(): String {
        val all = packageManager.getInstalledPackages(PackageManager.GET_SERVICES)
        val hits = all.filter { pkg ->
            val n = pkg.packageName.lowercase(Locale.US)
            keywords.any { n.contains(it) }
        }
        if (hits.isEmpty()) return "(no package name matched — check 'all packages' below)"
        return hits.joinToString("\n\n") { pkg ->
            val svcs = pkg.services?.joinToString(", ") { it.name }.orEmpty()
            "${pkg.packageName}  (uid=${pkg.applicationInfo?.uid})\n  services: $svcs"
        }
    }

    private fun allPackages(): String =
        packageManager.getInstalledPackages(0).map { it.packageName }.sorted().joinToString("\n")

    /**
     * Full binder service registry via the hidden android.os.ServiceManager.
     * Not a public API, but Android 4.2.2 (API 17) predates hidden-API
     * enforcement, so reflection reaches it fine on-device.
     */
    private fun binderServices(): String = try {
        val cls = Class.forName("android.os.ServiceManager")
        val m = cls.getMethod("listServices")
        @Suppress("UNCHECKED_CAST")
        val names = m.invoke(null) as Array<String>
        names.sorted().joinToString("\n")
    } catch (e: Throwable) {
        "reflection failed: $e"
    }

    private fun runningServices(): String {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.getRunningServices(200).joinToString("\n") {
            "${it.service.packageName}/${it.service.className}  pid=${it.pid}"
        }
    }

    private fun devNodes(): String {
        val f = File("/dev")
        val files = f.listFiles()
            ?: return "(cannot list /dev — ${if (f.canRead()) "empty" else "no read permission"})"
        return files.sortedBy { it.name }.joinToString("\n") {
            "${it.name}  r=${it.canRead()} w=${it.canWrite()}"
        }
    }

    private fun procFile(path: String): String = try {
        File(path).readLines().joinToString("\n")
    } catch (e: Throwable) {
        "error reading $path: $e"
    }

    private fun mounts(): String = procFile("/proc/mounts").ifBlank { exec("mount") }

    private fun getprop(key: String) = exec("getprop $key").trim()

    private fun exec(cmd: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
        val stdout = p.inputStream.bufferedReader().readText()
        val stderr = p.errorStream.bufferedReader().readText()
        p.waitFor()
        (stdout + stderr).trim().ifBlank { "(empty)" }
    } catch (e: Throwable) {
        "exec failed: $e"
    }

    private fun save() {
        try {
            val dir = File(Environment.getExternalStorageDirectory(), "civic-probe")
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "probe-$stamp.txt")
            file.writeText(out.text.toString())
            Toast.makeText(this, "Saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Save failed: $e", Toast.LENGTH_LONG).show()
        }
    }
}
