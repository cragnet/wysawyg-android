package com.cragnet.wysawyg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var isAnimating = false
    private val random = Random(System.currentTimeMillis())
    private var bars = FloatArray(12)

    init {
        updateBars()
    }

    fun startAnimation() {
        isAnimating = true
        postInvalidateOnAnimation()
    }

    fun stopAnimation() {
        isAnimating = false
        invalidate()
    }

    private fun updateBars() {
        for (i in bars.indices) {
            bars[i] = 0.2f + random.nextFloat() * 0.8f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAnimating) return

        updateBars()
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / (bars.size * 2)
        val maxBarHeight = height * 0.8f

        bars.forEachIndexed { index, value ->
            val barHeight = value * maxBarHeight
            val left = index * 2 * barWidth + barWidth / 2
            val top = (height - barHeight) / 2
            val right = left + barWidth
            val bottom = top + barHeight
            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2, barWidth / 2, paint)
        }

        postInvalidateOnAnimation()
    }
}
