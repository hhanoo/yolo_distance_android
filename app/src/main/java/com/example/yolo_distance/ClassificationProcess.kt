package com.example.yolo_distance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.util.ArrayList
import kotlin.math.exp

class ClassificationProcess(val context: Context) {

    lateinit var classifyClasses: Array<String>

    companion object {
        const val BATCH_SIZE = 1
        const val INPUT_SIZE = 224
        const val PIXEL_SIZE = 3

        const val LABEL_NAME = "gender.txt"
        const val FILE_NAME = "gender-classification-2.onnx"
    }

    // ImageProxy를 bitmap으로 만들고, 224x224 bitmap으로 변환
    fun cropFaceToBitmap(imageProxy: ImageProxy, coordinate: RectF, detectSize:Int): Bitmap {
        val bitmap = imageProxy.toBitmap()
        val cropImage = Bitmap.createBitmap(
            bitmap,
            (coordinate.left / detectSize * INPUT_SIZE).toInt(),
            (coordinate.top / detectSize * INPUT_SIZE).toInt(),
            (coordinate.right / detectSize * INPUT_SIZE).toInt(),
            (coordinate.bottom / detectSize * INPUT_SIZE).toInt()
        )
        return Bitmap.createScaledBitmap(cropImage, INPUT_SIZE, INPUT_SIZE, true)
    }

    fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val imageSTD = 255.0f   // Image normalize 위해 사용되는 표준화 상수, RGB의 각 픽셀값을  나누어 정규화
        // 입력 이미지를 저장할 FloatBuffer를 생성. FloatBuffer는 실수 값을 저장하는 버퍼
        // 입력 이미지의 BATCH_SIZE, 픽셀 채널 수(PIXEL_SIZE), INPUT_SIZE, INPUT_SIZE 따라 계산
        val buffer = FloatBuffer.allocate(BATCH_SIZE * PIXEL_SIZE * INPUT_SIZE * INPUT_SIZE)
        buffer.rewind() // 버퍼 position 초기화

        val area = INPUT_SIZE * INPUT_SIZE
        val bitmapData = IntArray(area) // 사진 하나에 대한 정보, 224x224 사이즈
        bitmap.getPixels(
            bitmapData,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        ) // 배열에 정보 담기

        // 픽셀 값을 Preprocessing 후에 FloatBuffer에 저장
        for (i in 0 until INPUT_SIZE - 1) {
            for (j in 0 until INPUT_SIZE - 1) {
                val idx = INPUT_SIZE * i + j
                val pixelValue = bitmapData[idx]
                // 위에서 부터 차례대로 R 값 추출, G 값 추출, B값 추출 -> 255로 나누어서 0~1 사이로 normalization
                buffer.put(idx, ((pixelValue shr 16 and 0xff) / imageSTD))
                buffer.put(idx + area, ((pixelValue shr 8 and 0xff) / imageSTD))
                buffer.put(idx + area * 2, ((pixelValue and 0xff) / imageSTD))
                // 원리 bitmap == ARGB 형태의 32bit, R값의 시작은 16bit (16 ~ 23bit 가 R영역), 따라서 16bit 를 쉬프트
                // 그럼 A값이 사라진 RGB 값인 24bit 가 남는다. 이후 255와 AND 연산을 통해 맨 뒤 8bit 인 R값만 가져오고, 255로 나누어 정규화를 한다.
                // 다시 8bit 를 쉬프트 하여 R값을 제거한 G,B 값만 남은 곳에 다시 AND 연산, 255 정규화, 다시 반복해서 RGB 값을 buffer 에 담는다.
            }
        }
        buffer.rewind() // 버퍼 position 초기화
        return buffer
    }

    // onnx 파일 불러오기
    fun loadModel() {
        val assetManager = context.assets
        val outputFile = File(context.filesDir.toString() + "/" + FILE_NAME)

        assetManager.open(FILE_NAME).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
            }
        }
    }

    // txt 파일 불러오기
    fun loadLabel() {
        BufferedReader(InputStreamReader(context.assets.open(LABEL_NAME))).use { reader ->
            var line: String?
            val classList = ArrayList<String>()
            while (reader.readLine().also { line = it } != null) {
                classList.add(line!!)
            }
            classifyClasses = classList.toTypedArray()
        }
    }

    fun outputTypeClassification(outputs: Array<*>): Int {
        val outputArray = outputs[0] as FloatArray  // outputs[0]를 FloatArray로 변환

        // Logits를 softmax로 변환하여 클래스 확률 계산
        val softmaxValues = softmax(outputArray)

        // 최대 확률의 클래스 인덱스 반환 (여성일 경우 0, 남성일 경우 1)
        return argmax(softmaxValues)
    }

    // Softmax 함수 정의
    fun softmax(logits: FloatArray): FloatBuffer {
        val softmaxValues = FloatBuffer.allocate(logits.size)
        val maxLogit = logits.max()
        var sumExp = 0.0
        for (i in logits.indices){
            softmaxValues.put(i, exp(logits[i] - maxLogit))
            sumExp += softmaxValues.get(i)
        }
        for (i in logits.indices) {
            softmaxValues.put(i, (softmaxValues.get(i) / sumExp).toFloat())
        }
        return softmaxValues
    }

    // argmax 함수 정의
    fun argmax(array: FloatBuffer): Int {
        var maxIndex = 0
        var maxValue = array.get(0)
        for (i in 1 until array.capacity()) {
            if (array.get(i) > maxValue) {
                maxValue = array.get(i)
                maxIndex = i
            }
        }
        return maxIndex
    }

}