package com.carcam.platecheck.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.text.Text

class PlateOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val labelBgPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    private val labelTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private var plateBoxes: List<Pair<RectF, String>> = emptyList()

    fun setPlateBoxes(
        boxes: List<Pair<Rect, String>>,
        imgWidth: Int,
        imgHeight: Int,
        rotationDegrees: Int
    ) {
        // Build transform matrix: rotate original image coords → screen coords
        val matrix = Matrix()
        when (rotationDegrees) {
            90 -> {
                matrix.postRotate(90f)
                matrix.postTranslate(imgHeight.toFloat(), 0f)
            }
            180 -> {
                matrix.postRotate(180f)
                matrix.postTranslate(imgWidth.toFloat(), imgHeight.toFloat())
            }
            270 -> {
                matrix.postRotate(270f)
                matrix.postTranslate(0f, imgWidth.toFloat())
            }
        }
        // Logical (display) dimensions after rotation
        val logicalW = if (rotationDegrees == 90 || rotationDegrees == 270) imgHeight.toFloat() else imgWidth.toFloat()
        val logicalH = if (rotationDegrees == 90 || rotationDegrees == 270) imgWidth.toFloat() else imgHeight.toFloat()
        matrix.postScale(width / logicalW, height / logicalH)

        plateBoxes = boxes.map { (rect, text) ->
            // Map all 4 corners to handle rotation-induced axis flips
            val pts = floatArrayOf(
                rect.left.toFloat(),  rect.top.toFloat(),
                rect.right.toFloat(), rect.top.toFloat(),
                rect.right.toFloat(), rect.bottom.toFloat(),
                rect.left.toFloat(),  rect.bottom.toFloat()
            )
            matrix.mapPoints(pts)
            val xs = floatArrayOf(pts[0], pts[2], pts[4], pts[6])
            val ys = floatArrayOf(pts[1], pts[3], pts[5], pts[7])
            Pair(RectF(xs.min(), ys.min(), xs.max(), ys.max()), text)
        }
        invalidate()
    }

    fun clear() {
        plateBoxes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((rect, text) in plateBoxes) {
            canvas.drawRoundRect(rect, 10f, 10f, boxPaint)
            val labelY = if (rect.top > 50f) rect.top - 10f else rect.bottom + 42f
            val textWidth = labelTextPaint.measureText(text)
            canvas.drawRect(rect.left, labelY - 38f, rect.left + textWidth + 16f, labelY + 6f, labelBgPaint)
            canvas.drawText(text, rect.left + 8f, labelY, labelTextPaint)
        }
    }
}
