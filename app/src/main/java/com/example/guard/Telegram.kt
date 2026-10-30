package com.example.guard

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Telegram {
    fun send(token: String, chatId: String, text: String) {
        Thread {
            try {
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                        "&text=" + URLEncoder.encode(text, "UTF-8")
                val c = URL("https://api.telegram.org/bot$token/sendMessage")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
                c.responseCode
                c.disconnect()
            } catch (e: Exception) {
            }
        }.start()
    }
}
