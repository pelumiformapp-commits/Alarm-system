package com.example.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder

class LocationService : Service() {

    private lateinit var lm: LocationManager
    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var token = ""
    private var chat = ""
    private var name = "Guard"
    private var armed = false
    private var lastSent = 0L
    private var lastAlarm = 0L
    private var started = false
    @Volatile private var running = false
    private var updateOffset = 0L
    private var ringtone: Ringtone? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) { report(l) }
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    private val motionListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!armed) return
            val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
            val mag = kotlin.math.sqrt((x * x + y * y + z * z).toDouble())
            if (mag > 28) triggerAlarm()
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        token = p.getString("token", "") ?: ""
        chat = p.getString("chat", "") ?: ""
        name = p.getString("name", "")?.ifBlank { "Guard" } ?: "Guard"
        armed = p.getBoolean("armed", false)

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, buildNote(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
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

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accel?.let { sensorManager.registerListener(motionListener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        if (!started) {
            started = true
            Telegram.send(token, chat, "$name: Guard started")
        }

        if (!running) {
            running = true
            startCommandListener()
        }

        return START_STICKY
    }

    private fun startCommandListener() {
        Thread {
            while (running) {
                val updates = Telegram.getUpdates(token, updateOffset + 1)
                for (i in 0 until updates.length()) {
                    val upd = updates.getJSONObject(i)
                    updateOffset = upd.optLong("update_id", updateOffset)
                    val msg = upd.optJSONObject("message") ?: continue
                    val text = msg.optString("text", "")
                    val fromChat = msg.optJSONObject("chat")?.optLong("id")?.toString() ?: ""
                    if (fromChat == chat && text.trim().equals("/locate", ignoreCase = true)) {
                        replyWithLocation()
                    }
                }
            }
        }.start()
    }

    private fun replyWithLocation() {
        var best: Location? = null
        try {
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                val l = lm.getLastKnownLocation(prov) ?: continue
                if (best == null || l.accuracy < best!!.accuracy) best = l
            }
        } catch (e: SecurityException) {
        }
        val l = best
        if (l != null) {
            Telegram.send(token, chat, "$name: https://maps.google.com/?q=${l.latitude},${l.longitude} (${l.accuracy.toInt()}m)")
        } else {
            Telegram.send(token, chat, "$name: no location fix yet")
        }
    }

    private fun triggerAlarm() {
        val now = System.currentTimeMillis()
        if (now - lastAlarm < 30000) return
        lastAlarm = now
        Telegram.send(token, chat, "$name: motion alarm triggered")
        SilentCamera.capture(applicationContext, token, chat, name, "motion alarm photo")
        try {
            ringtone = RingtoneManager.getRingtone(
                this, RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            )
            ringtone?.play()
            Handler(mainLooper).postDelayed({ try { ringtone?.stop() } catch (e: Exception) {} }, 15000)
        } catch (e: Exception) {
        }
    }

    private fun report(l: Location) {
        val now = System.currentTimeMillis()
        if (now - lastSent < 120000) return
        lastSent = now
        Telegram.send(token, chat, "$name: https://maps.google.com/?q=${l.latitude},${l.longitude} (${l.accuracy.toInt()}m)")
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
        running = false
        if (::lm.isInitialized) {
            try { lm.removeUpdates(listener) } catch (e: SecurityException) {}
        }
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(motionListener)
        try { ringtone?.stop() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
