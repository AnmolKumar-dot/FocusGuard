package com.focusguard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** The round timer face: dark disc, thin edge, and a green arc showing how far along you are. */
class RingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D90B1A11")
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = Color.parseColor("#66FFFFFF")
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * d
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#7FDB4A")
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 4f * d
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, edgePaint)
        if (progress > 0f) {
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, -90f, 360f * progress, false, arcPaint)
        }
    }
}

/** A small bar chart for the last few days of focus minutes. */
class BarChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var values: List<Int> = emptyList()
    private var labels: List<String> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A9A")
        textSize = 12f * d
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * d
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setData(newValues: List<Int>, newLabels: List<String>) {
        values = newValues
        labels = newLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val n = values.size
        val labelH = 24f * d
        val topPad = 20f * d
        val slot = width.toFloat() / n
        val barW = slot * 0.5f
        val chartH = height - labelH - topPad
        for (i in 0 until n) {
            val v = values[i]
            val h = if (v > 0) (chartH * v / max).coerceAtLeast(4f * d) else 3f * d
            val left = i * slot + (slot - barW) / 2f
            val bottom = topPad + chartH
            rect.set(left, bottom - h, left + barW, bottom)
            barPaint.color = when {
                v == 0 -> Color.parseColor("#2E2E2E")
                i == n - 1 -> Color.parseColor("#7FDB4A")
                else -> Color.parseColor("#4C8F2E")
            }
            canvas.drawRoundRect(rect, 6f * d, 6f * d, barPaint)
            if (v > 0) canvas.drawText(formatMinutes(v), left + barW / 2f, bottom - h - 6f * d, valuePaint)
            if (i < labels.size) {
                canvas.drawText(labels[i], i * slot + slot / 2f, height - 6f * d, labelPaint)
            }
        }
    }
}
