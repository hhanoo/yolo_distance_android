package com.krri.yolo_distance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.krri.yolo_distance.MainActivity.Companion.info
import com.krri.yolo_distance.MainActivity.Companion.realHeight
import kotlin.math.round

class RectView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    private var results: ArrayList<Result>? = null
    private lateinit var classes: Array<String>


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        //그림 그리기
        results?.forEach {
            canvas.drawRect(it.coordinate, findPaint(it.section))
            val textInfo =
                it.distance.toString() + "m"
            canvas.drawRect(
                it.coordinate.left,
                it.coordinate.top,
                it.coordinate.left + textPaint.measureText(textInfo),
                it.coordinate.top + textPaint.textSize,
                findPaintFill(it.section)
            )
            canvas.drawText(
                textInfo,
                it.coordinate.left,
                it.coordinate.top + 40,
                textPaint,
            )
        }
    }

    private val textPaint = Paint().also {
        it.textSize = 50f
        it.color = Color.WHITE
    }

    fun setClassLabel(classes: Array<String>) {
        this.classes = classes
    }

    // 가로 화면 기준
    fun transformRect(results: ArrayList<Result>) {
        // scale 구하기
        val scaleX = width / DetectProcess.INPUT_SIZE.toFloat()
        val scaleY = scaleX * 9f / 16f
        val realY = width * 9f / 16f
        val diffY = realY - height

        // 촬영 사진과 앱 내 보이는 뷰 사이즈 조정
        results.forEach {
            it.coordinate.left *= scaleX
            it.coordinate.right *= scaleX
            it.coordinate.top = it.coordinate.top * scaleY - (diffY / 2f)
            it.coordinate.bottom = it.coordinate.bottom * scaleY - (diffY / 2f)
            it.section = findSection(it.coordinate.centerX(), it.coordinate.centerY())
            it.distance = calibration(
                calculateDistance(
                    info[0],
                    it.coordinate.height() * info[1]
                ), it.section
            )
        }
        this.results = results
    }

    // 구역 찾기
    private fun findSection(x: Float, y: Float): Int {
        var isSection = 0
        // 가로 구역
        isSection += if (0 <= x && x < width / 3)
            0   // 좌측
        else if (x < width * 2 / 3)
            1   // 중앙
        else
            2   // 우측

        // 세로 구역
        isSection += if (0 <= y && y < height / 3)
            0 // 상단
        else if (y < height * 2 / 3)
            3 // 중앙
        else
            6 // 하단

        return isSection
    }

    // 거리 계산하기
    private fun calculateDistance(focalLength: Double, detectHeight: Double): Double {
        // 피사계의 식: Distance = (objectHeight * FocalLength) / 화면 상 높이
        return round((realHeight * focalLength) / detectHeight / 10) / 10
    }

    // 거리 보정
    private fun calibration(length: Double, section: Int): Double {
        // y = 1.17x - 0.38
        var calibrationDistance = (length + 0.38) / 1.17
        when (section) {
            1, 3, 7, 9 -> {
                calibrationDistance += 0.2
            }

            2, 4, 6, 8 -> {
                calibrationDistance += 0.05
            }

            else -> {

            }
        }
        return round(calibrationDistance * 10) / 10
    }

    // 랜덤 색 지정
    private fun getRandomColor(section: Int): Int {
        val colors = listOf(
            ContextCompat.getColor(context, R.color.green), // 상단 좌측
            ContextCompat.getColor(context, R.color.red),   // 상단 중앙
            ContextCompat.getColor(context, R.color.blue),  // 상단 우측
            ContextCompat.getColor(context, R.color.indigo),// 중앙 좌측
            ContextCompat.getColor(context, R.color.gray),  // 정중앙
            ContextCompat.getColor(context, R.color.yellow),// 중앙 우측
            ContextCompat.getColor(context, R.color.orange),// 하단 좌측
            ContextCompat.getColor(context, R.color.teal),  // 하단 중앙
            ContextCompat.getColor(context, R.color.purple),// 하단 우측
        )
        return colors[section]
    }

    //paint 지정
    private fun findPaint(section: Int): Paint {
        val paint = Paint()
        paint.style = Paint.Style.STROKE    // 빈 사각형 그림
        paint.strokeWidth = 10.0f           // 굵기 10
        paint.strokeCap = Paint.Cap.ROUND   // 끝을 뭉특하게
        paint.strokeJoin = Paint.Join.ROUND // 끝 주위도 뭉특하게
        paint.strokeMiter = 100f            // 뭉특한 정도는 100도
        paint.color = getRandomColor(section) // 사각형 색상

        return paint
    }

    //paint 지정
    private fun findPaintFill(section: Int): Paint {
        val paint = Paint()
        paint.style = Paint.Style.FILL      // 전부 칠한 사각형 그림
        paint.strokeWidth = 10.0f           // 굵기 10
        paint.strokeCap = Paint.Cap.ROUND   // 끝을 뭉특하게
        paint.strokeJoin = Paint.Join.ROUND // 끝 주위도 뭉특하게
        paint.strokeMiter = 100f            // 뭉특한 정도는 100도
        paint.color = getRandomColor(section) // 사각형 색상

        return paint
    }

}