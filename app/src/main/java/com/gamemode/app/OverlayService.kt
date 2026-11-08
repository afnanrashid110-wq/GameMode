package com.gamemode.app

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var info: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ping = "..."
    private var dndOn = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gm", "Game Mode", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "gm")
            .setContentTitle("Game Mode চালু আছে")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, n)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildUi()
        handler.post(tick)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bubble = TextView(this).apply {
            text = "🎮"; textSize = 22f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE62EE6A6.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xF0101820.toInt())
            }
            visibility = View.GONE
        }
        info = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f }
        panel.addView(info)
        panel.addView(btn("গেম মোড (DND) চালু/বন্ধ") { toggleDnd() })
        panel.addView(btn("RAM পরিষ্কার") { cleanRam() })
        panel.addView(btn("সাইডবার বন্ধ") { stopSelf() })
        root.addView(bubble)
        root.addView(panel)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = dp(200) }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = sx + dx; params.y = sy + dy
                    wm.updateViewLayout(root, params)
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    refresh()
                }
            }
            true
        }
        wm.addView(root, params)
    }

    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 2000) }
    }

    private fun refresh() {
        Thread {
            val s = System.nanoTime()
            ping = try {
                Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 1500) }
                "${(System.nanoTime() - s) / 1_000_000} ms"
            } catch (e: Exception) { "অফলাইন" }
        }.start()
        if (panel.visibility != View.VISIBLE) return

        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra("level", -1) ?: -1
        val scale = b?.getIntExtra("scale", 100) ?: 100
        val temp = (b?.getIntExtra("temperature", 0) ?: 0) / 10.0
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        info.text = "পিং: $ping\n" +
            "ব্যাটারি: ${lvl * 100 / scale}%  (${temp}°C)\n" +
            "খালি RAM: ${mi.availMem / 1048576} MB\n" +
            "হেডফোন: ${if (headphone()) "যুক্ত 🎧" else "নেই"}\n" +
            "লোকেশন: ${location()}\n" +
            "DND: ${if (dndOn) "চালু" else "বন্ধ"}"
    }

    private fun headphone(): Boolean {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun location(): String {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != 0) return "অনুমতি নেই"
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = lm.getProviders(true).mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null }
        }.maxByOrNull { it.time } ?: return "পাওয়া যায়নি"
        return String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)
    }

    private fun toggleDnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        dndOn = !dndOn
        nm.setInterruptionFilter(
            if (dndOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        refresh()
    }

    private fun cleanRam() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        for (a in packageManager.getInstalledApplications(0)) {
            if (a.packageName == packageName) continue
            if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            am.killBackgroundProcesses(a.packageName); n++
        }
        Toast.makeText(this, "$n টি অ্যাপের ব্যাকগ্রাউন্ড বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (dndOn) getSystemService(NotificationManager::class.java)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        try { wm.removeView(root) } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun btn(t: String, a: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 12f
        setOnClickListener { a() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
