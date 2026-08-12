package com.carcam.platecheck.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun adjustContrast(src: Bitmap, contrast: Float): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val translate = (1 - contrast) / 2f * 255f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    // rect: 회전 전(raw sensor) 버퍼 좌표계의 사각형 (예: ImageProxy.cropRect).
    // bufferWidth/bufferHeight: 회전 전 전체 버퍼 크기 (ImageProxy.width/height).
    // ML Kit이 InputImage.fromMediaImage(image, rotationDegrees)에서 반환하는 boundingBox와 같은
    // "화면에 보이는 방향" 좌표계로 변환한 사각형을 반환한다.
    fun rotateRect(rect: Rect, bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int): Rect {
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Rect(bufferHeight - rect.bottom, rect.left, bufferHeight - rect.top, rect.right)
            180 -> Rect(bufferWidth - rect.right, bufferHeight - rect.bottom, bufferWidth - rect.left, bufferHeight - rect.top)
            270 -> Rect(rect.top, bufferWidth - rect.right, rect.bottom, bufferWidth - rect.left)
            else -> Rect(rect)
        }
    }

    fun toGrayscale(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    fun cropAndUpscale(src: Bitmap, box: Rect, paddingRatio: Float = 0.3f, targetMaxDim: Int = 800): Bitmap {
        val padX = (box.width() * paddingRatio).toInt()
        val padY = (box.height() * paddingRatio).toInt()
        val left = (box.left - padX).coerceIn(0, src.width - 1)
        val top = (box.top - padY).coerceIn(0, src.height - 1)
        val right = (box.right + padX).coerceIn(left + 1, src.width)
        val bottom = (box.bottom + padY).coerceIn(top + 1, src.height)
        val cropped = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        val scale = targetMaxDim.toFloat() / maxOf(cropped.width, cropped.height)
        if (scale <= 1f) return cropped
        val w = (cropped.width * scale).toInt().coerceAtLeast(1)
        val h = (cropped.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(cropped, w, h, true)
    }

    // YUV_420_888 카메라 프레임을 회전 보정된(업라이트) Bitmap으로 변환한다. 결과 좌표계는
    // InputImage.fromMediaImage(image, rotationDegrees)에 동일한 rotationDegrees를 준 것과 같은
    // "화면에 보이는 방향" 좌표계이므로, 그 호출로 얻은 ML Kit Text의 boundingBox를 그대로 이 비트맵
    // 픽셀 좌표로 사용할 수 있다(별도 좌표 역변환 불필요).
    fun imageProxyToUprightBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap? {
        val mediaImage = imageProxy.image ?: return null
        val nv21 = yuv420888ToNv21(mediaImage)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 90, out)
        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * The same frame, converted without the JPEG round-trip.
     *
     * [imageProxyToUprightBitmap] compresses to JPEG and decodes back, which is fine for the
     * recognition passes but wrong for saving a frame as evidence: JPEG's quantisation is a
     * low-pass filter, and on a plate photographed off a monitor it visibly removes the moiré.
     * Frames saved that way did not reproduce the misreading they were captured for — ML Kit
     * read two of them correctly from the file after failing on them live.
     *
     * Slower than the JPEG path, so it is only used for capture, never per frame.
     */
    fun imageProxyToUprightBitmapExact(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap? {
        val mediaImage = imageProxy.image ?: return null
        val w = mediaImage.width
        val h = mediaImage.height
        val nv21 = yuv420888ToNv21(mediaImage)
        val argb = IntArray(w * h)
        val frameSize = w * h
        for (y in 0 until h) {
            val uvRow = frameSize + (y shr 1) * w
            for (x in 0 until w) {
                val yy = (nv21[y * w + x].toInt() and 0xFF) - 16
                val yv = if (yy < 0) 0 else yy
                val uvIndex = uvRow + (x and 1.inv())
                val v = (nv21[uvIndex].toInt() and 0xFF) - 128
                val u = (nv21[uvIndex + 1].toInt() and 0xFF) - 128
                // BT.601, the range CameraX delivers for YUV_420_888.
                val y1192 = 1192 * yv
                var r = (y1192 + 1634 * v) shr 10
                var g = (y1192 - 833 * v - 400 * u) shr 10
                var b = (y1192 + 2066 * u) shr 10
                r = if (r < 0) 0 else if (r > 255) 255 else r
                g = if (g < 0) 0 else if (g > 255) 255 else g
                b = if (b < 0) 0 else if (b > 255) 255 else b
                argb[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val nv21 = ByteArray(width * height * 3 / 2)

        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var pos = 0
        if (yPixelStride == 1) {
            // 대부분의 기기가 여기 해당: 픽셀 단위 루프 대신 행 단위(또는 전체) 벌크 복사.
            // 960x540 기준 약 52만 회의 get() 호출이 수백 회의 벌크 복사로 줄어든다.
            if (yRowStride == width) {
                yBuffer.position(0)
                yBuffer.get(nv21, 0, width * height)
            } else {
                for (row in 0 until height) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(nv21, row * width, width)
                }
            }
            pos = width * height
        } else {
            for (row in 0 until height) {
                val rowStart = row * yRowStride
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(rowStart + col * yPixelStride)
                }
            }
        }

        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                nv21[pos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                nv21[pos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
            }
        }
        return nv21
    }
}
