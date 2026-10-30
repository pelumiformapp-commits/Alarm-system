package com.example.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val token = p.getString("token", "") ?: ""
        val chat = p.getString("chat", "") ?: ""
        val name = p.getString("name", "")?.ifBlank { "Guard" } ?: "Guard"
        if (token.isBlank() || chat.isBlank()) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Telegram.send(token, chat, "$name: phone rebooted")
                if (p.getBoolean("running", false)) {
                    val svc = Intent(context, LocationService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc)
                    else context.startService(svc)
                }
            }
            Intent.ACTION_SHUTDOWN -> {
                // Best-effort only: Android gives very little time before the
                // phone actually powers off, so this message isn't guaranteed.
                Telegram.send(token, chat, "$name: phone powering off")
            }
        }
    }
}
