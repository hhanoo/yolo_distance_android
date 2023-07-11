package com.example.yolov8_distance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.pm.PackageManager
import android.hardware.Camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.example.yolov8_distance.databinding.ActivityMainBinding
import java.util.Collections
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var rectView: RectView
    private lateinit var edit: EditText
    private lateinit var btn: AppCompatButton
    private lateinit var btn2: AppCompatButton
    private lateinit var ortEnvironment: OrtEnvironment //  OrtEnvironment 클래스의 인스턴스를 참조하기 위한 ortEnvironment 변수를 선언
    private lateinit var session: OrtSession    // OrtSession 클래스의 인스턴스를 참조하기 위한 session 변수를 선언
    private val dataProcess = DataProcess(context = this)

    companion object {
        const val PERMISSION = 1    // 앱에서 권한 요청을 식별
        lateinit var info: Array<Double>
        var realHeight = 0.0
        var isDistance = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        previewView = binding.preview
        rectView = binding.rectView
        edit = binding.controlEdit
        btn = binding.controlBtn1
        btn2 = binding.controlBtn2

        // 자동 꺼짐 해제
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 권한 허용
        setPermissions()

        // onnx 파일 && txt 파일 불러오기
        load()

        // 카메라 정보 갖고오기
        info = getCameraParams()

        // 카메라 켜기
        setCamera()

        // 거리 계산 시작 버튼 클릭
        btn.setOnClickListener {
            if (!edit.text.equals("")) {
                isDistance = true
                realHeight = edit.text.toString().toDouble() // 센치 단위
                Toast.makeText(this, "입력 완료!", Toast.LENGTH_SHORT).show()
                // 거리 계산
            } else {
                isDistance = false
                Toast.makeText(this, "입력해 주세요", Toast.LENGTH_SHORT).show()
            }
        }

        // 거리 계산 종료 버튼 클릭
        btn2.setOnClickListener {
            isDistance = false
            Toast.makeText(this, "거리 계산을 종료합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 필요한 권한을 확인하고, 권한이 허용되지 않았을 경우에만 권한을 요청
    private fun setPermissions() {
        val permission = ArrayList<String>()
        permission.add(android.Manifest.permission.CAMERA)

        permission.forEach {
            if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permission.toTypedArray(), PERMISSION)
            }
        }
    }

    private fun load() {
        dataProcess.loadModel() // onnx 모델 불러오기
        dataProcess.loadLabel() // coco txt 파일 불러오기

        ortEnvironment = OrtEnvironment.getEnvironment()    // ONNX 런타임 환경을 초기화
        // ONNX 세션을 생성
        // ortEnvironment.createSession() 메서드를 호출하여 세션을 생성
        // 첫 번째 매개변수: 모델 파일의 경로를 지정
        // 두 번째 매개변수: 세션의 옵션을 설정
        session = ortEnvironment.createSession(
            this.filesDir.absolutePath.toString() + "/" + DataProcess.FILE_NAME,
            OrtSession.SessionOptions() // 기본 옵션 사용
        )

        // dataProcess.classes는 DataProcess 객체에서 클래스 label에 해당하는 데이터를 가져옴
        // 가져온 데이터를 rectView에 설정하여 객체 감지 결과에 대한 클래스 label 표시
        rectView.setClassLabel(dataProcess.classes)
    }

    fun getCameraParams(): Array<Double> {
        val camera = Camera.open() // 카메라 객체 생성
        val parameters = camera.parameters  // 카메라 파라미터 가져오기
        val focalLengthString = parameters.get("focal-length")
        var focalLength = focalLengthString?.toDoubleOrNull() ?: 0.0 // 초점거리 (단위 밀리미터)
        focalLength *= 10 // 밀리미터 -> 센치 단위로 변환
        val sensorHeight = parameters.previewSize.height.toDouble() // (단위 픽셀)
        val displayMetrics = resources.displayMetrics
        val dpi = displayMetrics.densityDpi // DPI 값
        var pixelHeight = sensorHeight / dpi // 세로 크기를 인치 단위로 변환
        pixelHeight *= 0.0254 // 세로 크기를 인치 -> 센치 단위로 변환
        camera.release()

        return arrayOf(focalLength, pixelHeight)
    }

    // 카메라를 설정하고, 카메라 미리보기와 이미지 분석을 위한 객체를 생성
    private fun setCamera() {
        // 카메라 제공 객체
        val processCameraProvider = ProcessCameraProvider.getInstance(this).get()
        // View 내에 가득 차도록 이미지 비율을 유지하면서 중앙
        // 이미지 비율은 유지, 일부 부분은 잘림
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        // 전면 카메라
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // 16:9 화면으로 받아옴
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build()

        // preview 에서 받아와서 previewView 출력
        preview.setSurfaceProvider(previewView.surfaceProvider)

        //분석 중이면 그 다음 화면이 대기중인 것이 아니라 계속 받아오는 화면으로 새로고침 함. 분석이 끝나면 그 최신 사진을 다시 분석
        val analysis = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

        // ImageAnalysis 객체에 대한 설정
        // executor: 분석 작업을 처리하기 위한 스레드 풀
        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) {
            imageProcess(it) // it = imageProxy 객체
            it.close()
        }

        // 카메라의 수명 주기를 메인 액티비티에 귀속
        processCameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
    }

    private fun imageProcess(imageProxy: ImageProxy) {
        val bitmap = dataProcess.imageToBitmap(imageProxy)
        val floatBuffer = dataProcess.bitmapToFloatBuffer(bitmap)
        val inputName = session.inputNames.iterator().next() // session 이름
        //모델의 요구 입력값 [1 3 640 640] [배치 사이즈, 픽셀(RGB), 너비, 높이], 모델마다 크기는 다를 수 있음.
        val shape = longArrayOf(
            DataProcess.BATCH_SIZE.toLong(),
            DataProcess.PIXEL_SIZE.toLong(),
            DataProcess.INPUT_SIZE.toLong(),
            DataProcess.INPUT_SIZE.toLong()
        )
        // 객체를 사용하여 모델을 실행하고, 결과 텐서를 받아옴
        val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape)
        // 입력 이름과 입력 텐서를 mapping하여 모델에 전달
        val resultTensor = session.run(Collections.singletonMap(inputName, inputTensor))
        // [1 84 8400] = [배치 사이즈, 라벨링 개수, 좌표값]
        val outputs = resultTensor.get(0).value as Array<*>
        // 출력을 객체 감지 결과로 변환
        val results = dataProcess.outputsToNPMSPredictions(outputs)

        //화면 표출
        rectView.transformRect(results) // results를 사용하여 rectView에 객체 감지 결과를 전달하여 사각형으로 표시할 위치와 정보를 변환
        rectView.invalidate()   // rectView를 다시 그리도록 호출해서 update
    }
}