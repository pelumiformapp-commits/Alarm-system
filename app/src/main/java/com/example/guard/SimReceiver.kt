package com.example.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SimReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra("ss") ?: intent.getStringExtra("state") ?: return
        val p = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val token = p.getString("token", "") ?: ""
        val chat = p.getString("chat", "") ?: ""
        val name = p.getString("name", "")?.ifBlank { "Guard" } ?: "Guard"
        if (token.isBlank() || chat.isBlank()) return
        if (state.equals("ABSENT", true) || state.equals("NOT_READY", true)) {
            Telegram.send(token, chat, "$name: SIM card removed or changed")
        }
    }
}
