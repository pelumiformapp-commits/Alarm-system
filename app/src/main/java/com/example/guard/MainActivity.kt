package com.example.guard

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
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
            val i = Intent(this, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show()
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
