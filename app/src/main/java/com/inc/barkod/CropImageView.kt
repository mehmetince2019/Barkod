package com.inc.barkod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var currentScale = 1f
    private var matrix = Matrix()

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#40000000")  // Yarı saydam siyah
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val handleRadius = 20f
    private var activeHandle: Int = HANDLE_NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    companion object {
        private const val HANDLE_NONE = 0
        private const val HANDLE_TOP_LEFT = 1
        private const val HANDLE_TOP_RIGHT = 2
        private const val HANDLE_BOTTOM_LEFT = 3
        private const val HANDLE_BOTTOM_RIGHT = 4
        private const val HANDLE_INSIDE = 5
    }

    init {
        scaleType = ScaleType.FIT_START
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        if (bm != null) {
            currentScale = 1f
            matrix = Matrix()
            scaleType = ScaleType.FIT_START
            updateMatrix()
        }
    }

    fun zoomIn() {
        if (currentScale < 3.0f) {
            currentScale *= 1.25f
            updateMatrix()
            invalidate()
        }
    }

    fun zoomOut() {
        if (currentScale > 0.5f) {
            currentScale *= 0.8f
            updateMatrix()
            invalidate()
        }
    }

    private fun updateMatrix() {
        if (drawable == null) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        matrix.reset()
        matrix.postScale(currentScale, currentScale)

        imageMatrix = matrix
    }

    private fun getHandle(x: Float, y: Float): Int {
        val handleTouchRadius = handleRadius * 1.5f

        if (isNearPoint(x, y, startX, startY, handleTouchRadius)) return HANDLE_TOP_LEFT
        if (isNearPoint(x, y, endX, startY, handleTouchRadius)) return HANDLE_TOP_RIGHT
        if (isNearPoint(x, y, startX, endY, handleTouchRadius)) return HANDLE_BOTTOM_LEFT
        if (isNearPoint(x, y, endX, endY, handleTouchRadius)) return HANDLE_BOTTOM_RIGHT

        val left = minOf(startX, endX)
        val top = minOf(startY, endY)
        val right = maxOf(startX, endX)
        val bottom = maxOf(startY, endY)

        if (x in left..right && y in top..bottom) return HANDLE_INSIDE

        return HANDLE_NONE
    }

    private fun isNearPoint(x: Float, y: Float, pointX: Float, pointY: Float, radius: Float): Boolean {
        val dx = x - pointX
        val dy = y - pointY
        return dx * dx + dy * dy <= radius * radius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activeHandle = getHandle(event.x, event.y)

                if (activeHandle == HANDLE_NONE) {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    isDragging = true
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                when (activeHandle) {
                    HANDLE_TOP_LEFT -> {
                        startX = event.x.coerceIn(0f, width.toFloat())
                        startY = event.y.coerceIn(0f, height.toFloat())
                    }
                    HANDLE_TOP_RIGHT -> {
                        endX = event.x.coerceIn(0f, width.toFloat())
                        startY = event.y.coerceIn(0f, height.toFloat())
                    }
                    HANDLE_BOTTOM_LEFT -> {
                        startX = event.x.coerceIn(0f, width.toFloat())
                        endY = event.y.coerceIn(0f, height.toFloat())
                    }
                    HANDLE_BOTTOM_RIGHT -> {
                        endX = event.x.coerceIn(0f, width.toFloat())
                        endY = event.y.coerceIn(0f, height.toFloat())
                    }
                    HANDLE_INSIDE -> {
                        val left = minOf(startX, endX)
                        val right = maxOf(startX, endX)
                        val top = minOf(startY, endY)
                        val bottom = maxOf(startY, endY)
                        val width = right - left
                        val height = bottom - top

                        val newLeft = (left + dx).coerceIn(0f, this.width - width)
                        val newTop = (top + dy).coerceIn(0f, this.height - height)

                        startX = if (startX < endX) newLeft else newLeft + width
                        endX = if (startX < endX) newLeft + width else newLeft
                        startY = if (startY < endY) newTop else newTop + height
                        endY = if (startY < endY) newTop + height else newTop
                    }
                    HANDLE_NONE -> if (isDragging) {
                        endX = event.x.coerceIn(0f, width.toFloat())
                        endY = event.y.coerceIn(0f, height.toFloat())
                    }
                }

                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                activeHandle = HANDLE_NONE
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isDragging || (!isDragging && startX != endX && startY != endY)) {
            // Yarı saydam overlay
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

            val left = minOf(startX, endX)
            val top = minOf(startY, endY)
            val right = maxOf(startX, endX)
            val bottom = maxOf(startY, endY)

            // Seçili alanı hafif şeffaf yap
            canvas.drawRect(left, top, right, bottom, Paint().apply {
                color = Color.parseColor("#20FFFFFF")  // Çok hafif beyaz overlay
                style = Paint.Style.FILL
            })

            // Seçili alanın çerçevesini çiz
            paint.color = Color.RED
            paint.strokeWidth = 5f
            canvas.drawRect(left, top, right, bottom, paint)

            // Köşe tutamaçları
            handlePaint.color = Color.RED
            canvas.drawCircle(left, top, handleRadius, handlePaint)
            canvas.drawCircle(right, top, handleRadius, handlePaint)
            canvas.drawCircle(left, bottom, handleRadius, handlePaint)
            canvas.drawCircle(right, bottom, handleRadius, handlePaint)
        }
    }

    fun getSelectedRegion(): RectF {
        return RectF(
            minOf(startX, endX),
            minOf(startY, endY),
            maxOf(startX, endX),
            maxOf(startY, endY)
        )
    }

    fun resetZoom() {
        currentScale = 1f
        matrix = Matrix()
        scaleType = ScaleType.FIT_START
        updateMatrix()
        invalidate()
    }
}