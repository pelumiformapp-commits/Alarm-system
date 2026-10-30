package com.example.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder

class LocationService : Service() {

    private lateinit var lm: LocationManager
    private var token = ""
    private var chat = ""
    private var lastSent = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) { report(l) }
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        token = p.getString("token", "") ?: ""
        chat = p.getString("chat", "") ?: ""

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                1, buildNote(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(1, buildNote())
        }

        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.removeUpdates(listener)
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(prov)) {
                    lm.requestLocationUpdates(prov, 5 * 60 * 1000L, 0f, listener)
                }
            }
        } catch (e: SecurityException) {
        }

        Telegram.send(token, chat, "Guard started")
        return START_STICKY
    }

    private fun report(l: Location) {
        val now = System.currentTimeMillis()
        if (now - lastSent < 120000) return
        lastSent = now
        Telegram.send(
            token, chat,
            "Location: https://maps.google.com/?q=${l.latitude},${l.longitude} (${l.accuracy.toInt()}m)"
        )
    }

    private fun buildNote(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel("guard", "Guard", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, "guard")
        } else {
            Notification.Builder(this)
        }
        return b.setContentTitle("Guard active")
            .setContentText("Tracking enabled")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
    }

    override fun onDestroy() {
        if (::lm.isInitialized) {
            try { lm.removeUpdates(listener) } catch (e: SecurityException) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
