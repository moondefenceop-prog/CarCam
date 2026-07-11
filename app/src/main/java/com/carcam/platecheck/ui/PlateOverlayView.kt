package com.carcam.platecheck.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PlateOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // isRegistered == null: DB 조회 결과가 아직 안 왔을 때(잠깐)만 쓰는 대기 색상.
    private val boxPaintPending = boxPaint(Color.YELLOW)
    private val boxPaintRegistered = boxPaint(Color.WHITE)
    private val boxPaintUnregistered = boxPaint(Color.RED)

    private val labelBgPaintPending = labelBgPaint(Color.YELLOW)
    private val labelBgPaintRegistered = labelBgPaint(Color.WHITE)
    private val labelBgPaintUnregistered = labelBgPaint(Color.RED)

    private val labelTextPaint = Paint().apply {
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private data class PlateBox(val rect: RectF, val text: String, val isRegistered: Boolean?)

    private var plateBoxes: List<PlateBox> = emptyList()

    // visibleRegion: ML Kit의 회전된(display-oriented) 좌표계에서, 실제로 화면(Preview)에 보이는
    // 영역. ImageAnalysis가 ViewPort로 크롭된 경우 전체 버퍼보다 작을 수 있으므로, 전체 버퍼 크기가
    // 아니라 이 영역 기준으로 스케일/오프셋을 계산해야 박스가 실제 화면 위치와 맞는다.
    // isRegistered: 등록 차량 조회 결과. 아직 조회 전이면 null(대기 색상으로 표시).
    fun setPlateBoxes(
        boxes: List<Triple<Rect, String, Boolean?>>,
        visibleRegion: Rect
    ) {
        val scaleX = width / visibleRegion.width().toFloat()
        val scaleY = height / visibleRegion.height().toFloat()

        plateBoxes = boxes.map { (rect, text, isRegistered) ->
            PlateBox(
                RectF(
                    (rect.left   - visibleRegion.left) * scaleX,
                    (rect.top    - visibleRegion.top)  * scaleY,
                    (rect.right  - visibleRegion.left) * scaleX,
                    (rect.bottom - visibleRegion.top)  * scaleY
                ),
                text,
                isRegistered
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
        for (box in plateBoxes) {
            val (boxPaint, labelBg, labelTextColor) = when (box.isRegistered) {
                true -> Triple(boxPaintRegistered, labelBgPaintRegistered, Color.BLACK)
                false -> Triple(boxPaintUnregistered, labelBgPaintUnregistered, Color.WHITE)
                null -> Triple(boxPaintPending, labelBgPaintPending, Color.BLACK)
            }
            canvas.drawRoundRect(box.rect, 10f, 10f, boxPaint)
            val labelY = if (box.rect.top > 50f) box.rect.top - 10f else box.rect.bottom + 42f
            val textWidth = labelTextPaint.measureText(box.text)
            canvas.drawRect(box.rect.left, labelY - 38f, box.rect.left + textWidth + 16f, labelY + 6f, labelBg)
            labelTextPaint.color = labelTextColor
            canvas.drawText(box.text, box.rect.left + 8f, labelY, labelTextPaint)
        }
    }

    private fun boxPaint(paintColor: Int) = Paint().apply {
        color = paintColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private fun labelBgPaint(paintColor: Int) = Paint().apply {
        color = paintColor
        style = Paint.Style.FILL
    }
}
