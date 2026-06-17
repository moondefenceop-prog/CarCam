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
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private var plateBoxes: List<Pair<RectF, String>> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1

    fun setPlateBoxes(boxes: List<Pair<Rect, String>>, imgWidth: Int, imgHeight: Int) {
        imageWidth = imgWidth
        imageHeight = imgHeight
        val scaleX = width.toFloat() / imgHeight  // rotated
        val scaleY = height.toFloat() / imgWidth
        plateBoxes = boxes.map { (rect, text) ->
            val mapped = RectF(
                rect.top * scaleX,
                rect.left * scaleY,
                rect.bottom * scaleX,
                rect.right * scaleY
            )
            Pair(mapped, text)
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
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint)
            val labelY = if (rect.top > 50f) rect.top - 8f else rect.bottom + 36f
            val textWidth = labelTextPaint.measureText(text)
            canvas.drawRect(rect.left, labelY - 34f, rect.left + textWidth + 12f, labelY + 4f, labelBgPaint)
            canvas.drawText(text, rect.left + 6f, labelY, labelTextPaint)
        }
    }
}
