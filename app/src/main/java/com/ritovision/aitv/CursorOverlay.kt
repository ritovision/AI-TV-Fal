package com.ritovision.aitv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** A non-interactive overlay. Remote clicks are dispatched to the WebView below it. */
class CursorOverlay(context: Context) : View(context) {
    var cursorX = 0f
        private set
    var cursorY = 0f
        private set

    var scrollMode = false
        set(value) {
            field = value
            invalidate()
        }

    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 6f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val pointer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 221, 242)
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val labelFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 221, 242)
        style = Paint.Style.FILL
        textSize = 15f * resources.displayMetrics.density
        isFakeBoldText = true
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun center() {
        cursorX = width / 2f
        cursorY = height / 2f
        invalidate()
    }

    fun move(dx: Float, dy: Float): Boolean {
        val px = cursorX
        val py = cursorY
        cursorX = (cursorX + dx).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        invalidate()
        return px == cursorX && py == cursorY
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val d = resources.displayMetrics.density
        canvas.drawCircle(cursorX, cursorY, 14f * d, border)
        canvas.drawCircle(cursorX, cursorY, 14f * d, pointer)
        canvas.drawLine(cursorX - 23f * d, cursorY, cursorX - 8f * d, cursorY, border)
        canvas.drawLine(cursorX - 23f * d, cursorY, cursorX - 8f * d, cursorY, pointer)
        canvas.drawLine(cursorX + 8f * d, cursorY, cursorX + 23f * d, cursorY, border)
        canvas.drawLine(cursorX + 8f * d, cursorY, cursorX + 23f * d, cursorY, pointer)
        canvas.drawLine(cursorX, cursorY - 23f * d, cursorX, cursorY - 8f * d, pointer)
        canvas.drawLine(cursorX, cursorY + 8f * d, cursorX, cursorY + 23f * d, pointer)
        canvas.drawCircle(cursorX, cursorY, 2f * d, pointer)
        if (scrollMode) {
            val title = "SCROLL"
            val left = (cursorX + 26f * d).coerceIn(2f * d, (width - 100f * d).coerceAtLeast(2f * d))
            val top = (cursorY - 12f * d).coerceIn(2f * d, (height - 27f * d).coerceAtLeast(2f * d))
            val right = left + labelFg.measureText(title) + 14f * d
            canvas.drawRoundRect(left, top, right, top + 26f * d, 7f * d, 7f * d, labelBg)
            canvas.drawText(title, left + 7f * d, top + 18f * d, labelFg)
        }
    }
}
