package com.example.guard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
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
        val token = EditText(this).apply {
            hint = "Bot token"
            setText(p.getString("token", ""))
        }
        val chat = EditText(this).apply {
            hint = "Chat ID"
            setText(p.getString("chat", ""))
        }
        val start = Button(this).apply { text = "Start tracking" }
        val stop = Button(this).apply { text = "Stop" }

        start.setOnClickListener {
            p.edit()
                .putString("token", token.text.toString().trim())
                .putString("chat", chat.text.toString().trim())
                .apply()

            if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                requestPermissions(perms.toTypedArray(), 1)
            } else {
                launch()
            }
        }
        stop.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))
        }

        root.addView(token)
        root.addView(chat)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }
}
