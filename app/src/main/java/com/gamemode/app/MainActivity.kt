package com.gamemode.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
            setBackgroundColor(Color.parseColor("#0F1720"))
        }
        col.addView(TextView(this).apply {
            text = "🎮 Game Mode"; textSize = 26f; setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = "ধাপগুলো ওপর থেকে নিচে করুন"; setTextColor(Color.LTGRAY); setPadding(0, dp(4), 0, dp(16))
        })
        status = TextView(this).apply { setTextColor(Color.parseColor("#2EE6A6")); setPadding(0, 0, 0, dp(16)) }
        col.addView(status)

        col.addView(btn("১. অন্য অ্যাপের ওপর দেখানোর অনুমতি") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(btn("২. লোকেশন ও নোটিফিকেশন অনুমতি") {
            val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) p.add("android.permission.POST_NOTIFICATIONS")
            requestPermissions(p.toTypedArray(), 1)
        })
        col.addView(btn("৩. ডু নট ডিস্টার্ব অ্যাক্সেস") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        col.addView(btn("▶ সাইডবার বাবল চালু করুন") {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "আগে ধাপ ১ এর অনুমতি দিন"
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                status.text = "বাবল চালু হয়েছে। এখন গেমে যান।"
            }
        })
        col.addView(btn("■ বাবল বন্ধ করুন") {
            stopService(Intent(this, OverlayService::class.java))
            status.text = "বাবল বন্ধ"
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        status.text = "ওভারলে: " + (if (Settings.canDrawOverlays(this)) "✓" else "✗") +
            "   DND: " + (if (nm.isNotificationPolicyAccessGranted) "✓" else "✗") +
            "   লোকেশন: " + (if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == 0) "✓" else "✗")
    }

    private fun btn(t: String, a: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; gravity = Gravity.CENTER
        setOnClickListener { a() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
