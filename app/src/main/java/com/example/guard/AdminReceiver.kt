package com.example.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        val p = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val token = p.getString("token", "") ?: ""
        val chat = p.getString("chat", "") ?: ""
        val name = p.getString("name", "")?.ifBlank { "Guard" } ?: "Guard"
        if (token.isBlank() || chat.isBlank()) return
        Telegram.send(token, chat, "$name: wrong passcode entered")
        SilentCamera.capture(context, token, chat, name, "wrong passcode photo")
    }
}
