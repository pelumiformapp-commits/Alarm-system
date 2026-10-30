package com.example.guard

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {

    private fun launch() {
        val i = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putBoolean("running", true).apply()
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show()
    }

    private fun sendSOS() {
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val token = p.getString("token", "") ?: ""
        val chat = p.getString("chat", "") ?: ""
        val name = p.getString("name", "")?.ifBlank { "Guard" } ?: "Guard"
        if (token.isBlank() || chat.isBlank()) {
            Toast.makeText(this, "Set up token and chat ID first", Toast.LENGTH_LONG).show()
            return
        }
        Telegram.send(token, chat, "🚨 $name: EMERGENCY — SOS pressed")

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            try {
                for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    val l = lm.getLastKnownLocation(prov) ?: continue
                    if (best == null || l.accuracy < best!!.accuracy) best = l
                }
            } catch (e: SecurityException) {
            }
            best?.let {
                Telegram.send(token, chat, "🚨 $name: https://maps.google.com/?q=${it.latitude},${it.longitude} (${it.accuracy.toInt()}m)")
            }
        }
        SilentCamera.capture(applicationContext, token, chat, name, "SOS photo")
        Toast.makeText(this, "SOS sent", Toast.LENGTH_SHORT).show()
    }

    private fun stopAlarm() {
        val i = Intent(this, LocationService::class.java)
        i.action = "STOP_ALARM"
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Toast.makeText(this, "Alarm stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) launch()
        else Toast.makeText(this, "Location permission needed", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }
        val name = EditText(this).apply { hint = "Your name"; setText(p.getString("name", "")) }
        val token = EditText(this).apply { hint = "Bot token"; setText(p.getString("token", "")) }
        val chat = EditText(this).apply { hint = "Chat ID"; setText(p.getString("chat", "")) }
        val appPin = EditText(this).apply {
            hint = "Safety PIN (to cancel timer — not your phone PIN)"
            setText(p.getString("apppin", ""))
        }
        val armed = CheckBox(this).apply { text = "Armed (loud alarm on grab)"; isChecked = p.getBoolean("armed", false) }
        val shake = CheckBox(this).apply { text = "Shake-to-SOS (silent)"; isChecked = p.getBoolean("shake", false) }

        val start = Button(this).apply { text = "Start tracking" }
        val stop = Button(this).apply { text = "Stop" }
        val adminBtn = Button(this).apply { text = "Enable Device Admin (wrong-passcode photo)" }
        val sosBtn = Button(this).apply { text = "🚨 SOS — send emergency alert now" }
        val stopAlarmBtn = Button(this).apply { text = "🔇 Stop alarm sound" }

        val timerMinutes = EditText(this).apply { hint = "Safety timer minutes (e.g. 10)" }
        val startTimerBtn = Button(this).apply { text = "Start silent safety timer" }
        val cancelPinInput = EditText(this).apply { hint = "Enter safety PIN to cancel timer" }
        val cancelTimerBtn = Button(this).apply { text = "Cancel timer" }

        fun saveConfig() {
            p.edit()
                .putString("name", name.text.toString().trim())
                .putString("token", token.text.toString().trim())
                .putString("chat", chat.text.toString().trim())
                .putString("apppin", appPin.text.toString().trim())
                .putBoolean("armed", armed.isChecked)
                .putBoolean("shake", shake.isChecked)
                .apply()
        }

        start.setOnClickListener {
            saveConfig()
            val perms = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.CAMERA)
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1) else launch()
        }
        stop.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))
            p.edit().putBoolean("running", false).apply()
        }
        adminBtn.setOnClickListener {
            val compName = ComponentName(this, AdminReceiver::class.java)
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed to detect wrong passcode attempts")
            startActivity(i)
        }
        sosBtn.setOnClickListener { sendSOS() }
        stopAlarmBtn.setOnClickListener { stopAlarm() }

        startTimerBtn.setOnClickListener {
            saveConfig()
            val mins = timerMinutes.text.toString().toIntOrNull() ?: 10
            val i = Intent(this, LocationService::class.java)
            i.action = "START_TIMER"
            i.putExtra("minutes", mins)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            Toast.makeText(this, "Safety timer started: $mins min", Toast.LENGTH_SHORT).show()
        }
        cancelTimerBtn.setOnClickListener {
            val entered = cancelPinInput.text.toString().trim()
            val saved = p.getString("apppin", "") ?: ""
            if (saved.isNotBlank() && entered == saved) {
                val i = Intent(this, LocationService::class.java)
                i.action = "CANCEL_TIMER"
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                Toast.makeText(this, "Timer cancelled", Toast.LENGTH_SHORT).show()
                cancelPinInput.text.clear()
            } else {
                Toast.makeText(this, "Wrong code", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(name)
        root.addView(token)
        root.addView(chat)
        root.addView(appPin)
        root.addView(armed)
        root.addView(shake)
        root.addView(start)
        root.addView(stop)
        root.addView(adminBtn)
        root.addView(sosBtn)
        root.addView(stopAlarmBtn)
        root.addView(timerMinutes)
        root.addView(startTimerBtn)
        root.addView(cancelPinInput)
        root.addView(cancelTimerBtn)
        setContentView(root)
    }
}
