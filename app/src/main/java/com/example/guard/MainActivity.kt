package com.example.guard

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        code: Int, perms: Array<out String>, res: IntArray
    ) {
        super.onRequestPermissionsResult(code, perms, res)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) launch()
        else Toast.makeText(this, "Location permission needed", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }
        val name = EditText(this).apply {
            hint = "Your name"
            setText(p.getString("name", ""))
        }
        val token = EditText(this).apply {
            hint = "Bot token"
            setText(p.getString("token", ""))
        }
        val chat = EditText(this).apply {
            hint = "Chat ID"
            setText(p.getString("chat", ""))
        }
        val armed = CheckBox(this).apply {
            text = "Armed (motion alarm)"
            isChecked = p.getBoolean("armed", false)
        }
        val start = Button(this).apply { text = "Start tracking" }
        val stop = Button(this).apply { text = "Stop" }
        val adminBtn = Button(this).apply { text = "Enable Device Admin (wrong-passcode photo)" }

        start.setOnClickListener {
            p.edit()
                .putString("name", name.text.toString().trim())
                .putString("token", token.text.toString().trim())
                .putString("chat", chat.text.toString().trim())
                .putBoolean("armed", armed.isChecked)
                .apply()

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

            if (perms.isNotEmpty()) {
                requestPermissions(perms.toTypedArray(), 1)
            } else {
                launch()
            }
        }
        stop.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))
        }
        adminBtn.setOnClickListener {
            val compName = ComponentName(this, AdminReceiver::class.java)
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed to detect wrong passcode attempts")
            startActivity(i)
        }

        root.addView(name)
        root.addView(token)
        root.addView(chat)
        root.addView(armed)
        root.addView(start)
        root.addView(stop)
        root.addView(adminBtn)
        setContentView(root)
    }
}
