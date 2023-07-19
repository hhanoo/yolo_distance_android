package com.example.yolo_distance

import android.graphics.RectF

data class Result(
    var classIndex: Int,
    val score: Float,
    val coordinate: RectF,
    var section: Int,
    var distance: Double,
)