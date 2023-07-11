package com.example.yolov8_distance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class DividedAreaView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width
        val height = height

        // 가로 선 그리기
        canvas.drawLine(0f, height / 3f, width.toFloat(), height / 3f, paint)
        canvas.drawLine(0f, height * 2f / 3f, width.toFloat(), height * 2f / 3f, paint)

        // 세로 선 그리기
        canvas.drawLine(width / 3f, 0f, width / 3f, height.toFloat(), paint)
        canvas.drawLine(width * 2f / 3f, 0f, width * 2f / 3f, height.toFloat(), paint)
    }
}
