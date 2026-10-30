package com.example.guard

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Telegram {
    fun send(token: String, chatId: String, text: String) {
        if (token.isBlank() || chatId.isBlank()) return
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

    fun sendPhoto(token: String, chatId: String, caption: String, photoBytes: ByteArray) {
        if (token.isBlank() || chatId.isBlank()) return
        Thread {
            try {
                val boundary = "----GuardBoundary" + System.currentTimeMillis()
                val c = URL("https://api.telegram.org/bot$token/sendPhoto")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val out = c.outputStream
                fun field(n: String, v: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$n\"\r\n\r\n".toByteArray())
                    out.write("$v\r\n".toByteArray())
                }
                field("chat_id", chatId)
                field("caption", caption)

                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n".toByteArray())
                out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                out.write(photoBytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
                out.flush()
                out.close()
                c.responseCode
                c.disconnect()
            } catch (e: Exception) {
            }
        }.start()
    }

    fun getUpdates(token: String, offset: Long, timeoutSec: Int = 25): JSONArray {
        return try {
            val url = URL("https://api.telegram.org/bot$token/getUpdates?timeout=$timeoutSec&offset=$offset")
            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = "GET"
            c.connectTimeout = (timeoutSec + 10) * 1000
            c.readTimeout = (timeoutSec + 10) * 1000
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            JSONObject(text).optJSONArray("result") ?: JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }
    }
}
