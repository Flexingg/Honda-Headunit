package com.civic.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Media on Android 4.2 has no MediaSession/notification-listener APIs, so:
 *  - metadata: listen to the de-facto "metachanged" broadcasts most players send
 *  - control:  inject media keys through the hidden IAudioService (routes to the
 *              app that currently owns audio focus, like steering-wheel buttons),
 *              falling back to a plain MEDIA_BUTTON broadcast.
 */
object MediaBridge {
    data class Track(val title: String, val artist: String, val playing: Boolean)

    var listener: ((Track) -> Unit)? = null
    var last = Track("Nothing playing", "Tap to open music", false); private set

    private val ACTIONS = arrayOf(
        "com.android.music.metachanged", "com.android.music.playstatechanged",
        "com.spotify.music.metadatachanged", "com.spotify.music.playbackstatechanged",
        "com.google.android.music.metachanged", "com.google.android.music.playstatechanged",
        "com.maxmpz.audioplayer.TRACK_CHANGED", "com.maxmpz.audioplayer.STATUS_CHANGED",
        "com.htc.music.metachanged", "fm.last.android.metachanged",
        "com.sec.android.app.music.metachanged", "com.amazon.mp3.metachanged"
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val title = i.getStringExtra("track") ?: i.getStringExtra("title") ?: last.title
            val artist = i.getStringExtra("artist") ?: last.artist
            val playing = when {
                i.hasExtra("playing") -> i.getBooleanExtra("playing", last.playing)
                i.hasExtra("playstate") -> i.getBooleanExtra("playstate", last.playing)
                i.hasExtra("isPlaying") -> i.getBooleanExtra("isPlaying", last.playing)
                else -> last.playing
            }
            last = Track(title, artist, playing)
            listener?.invoke(last)
        }
    }

    private var registered = false
    fun register(c: Context) {
        if (registered) return; registered = true
        val f = IntentFilter(); ACTIONS.forEach { f.addAction(it) }
        c.applicationContext.registerReceiver(receiver, f)
    }

    fun playPause(c: Context) = key(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(c: Context) = key(c, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prev(c: Context) = key(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun key(c: Context, code: Int) {
        val t = SystemClock.uptimeMillis()
        val down = KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)
        val up = KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)
        if (!dispatchViaAudioService(down, up)) {
            c.sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, down), null)
            c.sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, up), null)
        }
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            last = last.copy(playing = !last.playing); listener?.invoke(last)
        }
    }

    private fun dispatchViaAudioService(vararg events: KeyEvent): Boolean = try {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java).invoke(null, "audio") as IBinder
        val stub = Class.forName("android.media.IAudioService\$Stub")
        val svc = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val m = svc.javaClass.getMethod("dispatchMediaKeyEvent", KeyEvent::class.java)
        events.forEach { m.invoke(svc, it) }
        true
    } catch (t: Throwable) { false }
}
