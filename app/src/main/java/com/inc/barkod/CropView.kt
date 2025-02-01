package com.inc.barkod

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val transparentPaint = Paint().apply {
        color = Color.parseColor("#80000000")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Yarı saydam arka plan
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), transparentPaint)

        // Seçili alan
        val left = minOf(startX, endX)
        val top = minOf(startY, endY)
        val right = maxOf(startX, endX)
        val bottom = maxOf(startY, endY)

        // Seçili alanı temizle
        canvas.drawRect(left, top, right, bottom, Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        })

        // Seçili alanın çerçevesi
        canvas.drawRect(left, top, right, bottom, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = event.x
                endY = event.y
                isDragging = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    endX = event.x
                    endY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
            }
        }
        return true
    }

    fun getSelectedRect(): RectF {
        return RectF(
            minOf(startX, endX),
            minOf(startY, endY),
            maxOf(startX, endX),
            maxOf(startY, endY)
        )
    }
}