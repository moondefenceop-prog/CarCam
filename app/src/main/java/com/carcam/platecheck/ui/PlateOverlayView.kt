package com.carcam.platecheck.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

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
        // ML Kit already returns bounding boxes in display-oriented space (rotation applied).
        // Only need to scale from the rotated image dimensions to view dimensions.
        val logicalW = if (rotationDegrees == 90 || rotationDegrees == 270) imgHeight.toFloat() else imgWidth.toFloat()
        val logicalH = if (rotationDegrees == 90 || rotationDegrees == 270) imgWidth.toFloat() else imgHeight.toFloat()
        val scaleX = width / logicalW
        val scaleY = height / logicalH

        plateBoxes = boxes.map { (rect, text) ->
            Pair(
                RectF(
                    rect.left   * scaleX,
                    rect.top    * scaleY,
                    rect.right  * scaleX,
                    rect.bottom * scaleY
                ),
                text
            )
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
