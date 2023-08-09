package com.krri.yolo_distance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.krri.yolo_distance.databinding.ActivityMainBinding
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.roundToLong


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var rectView: RectView
    private lateinit var male: RadioButton
    private lateinit var female: RadioButton
    private lateinit var nonDivision: RadioButton
    private lateinit var yoloOrtEnvironment: OrtEnvironment //  OrtEnvironment 클래스의 인스턴스를 참조하기 위한 ortEnvironment 변수를 선언
    private lateinit var yoloSession: OrtSession    // OrtSession 클래스의 인스턴스를 참조하기 위한 session 변수를 선언
    private val detectProcess = DetectProcess(context = this)
    companion object {
        const val PERMISSION = 1    // 앱에서 권한 요청을 식별
        lateinit var info: Array<Double>
        var realHeight = 0.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        previewView = binding.preview
        rectView = binding.rectView
        male = binding.controlMale
        female = binding.controlFemale
        nonDivision = binding.controlAverage

        // 자동 꺼짐 해제
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // UWB 사용 여부
        setUWB()

        // 권한 허용
        setPermissions()

        // onnx 파일 && txt 파일 불러오기
        yoloLoad()

        // 카메라 정보 갖고오기
        info = getCameraParams()

        // 카메라 켜기
        setCamera()

        // 라다오 버튼 default
        if (male.isChecked) {
            realHeight = 24.6
        } else if (female.isChecked) {
            realHeight = 23.7
        } else if (nonDivision.isChecked) {
            realHeight = 24.1
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

    private fun yoloLoad() {
        detectProcess.loadModel() // onnx 모델 불러오기
        detectProcess.loadLabel() // coco txt 파일 불러오기

        yoloOrtEnvironment = OrtEnvironment.getEnvironment()    // ONNX 런타임 환경을 초기화
        // ONNX 세션을 생성
        // ortEnvironment.createSession() 메서드를 호출하여 세션을 생성
        // 첫 번째 매개변수: 모델 파일의 경로를 지정
        // 두 번째 매개변수: 세션의 옵션을 설정
        yoloSession = yoloOrtEnvironment.createSession(
            this.filesDir.absolutePath.toString() + "/" + DetectProcess.FILE_NAME,
            OrtSession.SessionOptions() // 기본 옵션 사용
        )

        // dataProcess.classes는 DataProcess 객체에서 클래스 label에 해당하는 데이터를 가져옴
        // 가져온 데이터를 rectView에 설정하여 객체 감지 결과에 대한 클래스 label 표시
        rectView.setClassLabel(detectProcess.classes)
    }

    private fun getCameraParams(): Array<Double> {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager // 카메라 객채 생성
        val cameraId = cameraManager.cameraIdList[0] // 원하는 카메라의 ID 선택
        val characteristics = cameraManager.getCameraCharacteristics(cameraId) // 카메라 특성 가져오기
        val focalLengths =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) as FloatArray

        val focalLength = focalLengths[0].toDouble() // 초점 거리 (단위: 밀리미터)
        val dpi = resources.displayMetrics.densityDpi.toDouble() // DPI 값
        val pixelHeight = 2.54 / dpi // 픽셀 1개의 실제 사이즈
        return arrayOf(focalLength, pixelHeight)
    }

    // 카메라를 설정하고, 카메라 미리보기와 이미지 분석을 위한 객체를 생성
    private fun setCamera() {
        // 카메라 제공 객체
        val processCameraProvider = ProcessCameraProvider.getInstance(this).get()
        // View 내에 가득 차도록 이미지 비율을 유지하면서 중앙
        // 이미지 비율은 유지, 일부 부분은 잘림
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        // 후면 카메라
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // 16:9 화면으로 받아옴
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()

        // preview 에서 받아와서 previewView 출력
        preview.setSurfaceProvider(previewView.surfaceProvider)

        //분석 중이면 그 다음 화면이 대기중인 것이 아니라 계속 받아오는 화면으로 새로고침 함. 분석이 끝나면 그 최신 사진을 다시 분석
        val analysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

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
        // 시작 시간 기록
        val startTime = System.currentTimeMillis()

        val bitmap = detectProcess.imageToBitmap(imageProxy)
        val floatBuffer = detectProcess.bitmapToFloatBuffer(bitmap)
        val inputName = yoloSession.inputNames.iterator().next() // session 이름
        //모델의 요구 입력값 [1 3 640 640] [배치 사이즈, 픽셀(RGB), 너비, 높이], 모델마다 크기는 다를 수 있음.
        val shape = longArrayOf(
            DetectProcess.BATCH_SIZE.toLong(),
            DetectProcess.PIXEL_SIZE.toLong(),
            DetectProcess.INPUT_SIZE.toLong(),
            DetectProcess.INPUT_SIZE.toLong()
        )
        // 객체를 사용하여 모델을 실행하고, 결과 텐서를 받아옴
        val inputTensor = OnnxTensor.createTensor(yoloOrtEnvironment, floatBuffer, shape)
        // 입력 이름과 입력 텐서를 mapping하여 모델에 전달
        val resultTensor = yoloSession.run(Collections.singletonMap(inputName, inputTensor))
        // [1 84 8400] = [배치 사이즈, 라벨링 개수, 좌표값]
        val outputs = resultTensor.get(0).value as Array<*>
        // 출력을 객체 감지 결과로 변환
        val results = detectProcess.outputsToNPMSPredictions(outputs)

        //화면 표출
        rectView.transformRect(results) // results를 사용하여 rectView에 객체 감지 결과를 전달하여 사각형으로 표시할 위치와 정보를 변환
        rectView.invalidate()   // rectView를 다시 그리도록 호출해서 update

        // 종료 시간 기록
        val endTime = System.currentTimeMillis()

        // 경과 시간 계산
        val elapsedTime = endTime - startTime

        // FPS 계산
        val fps = (1000.0 / elapsedTime * 10).roundToLong() / 10f

        // FPS를 메인 스레드에서 TextView에 업데이트
        runOnUiThread {
            binding.fpsTv.text = "FPS: $fps"
        }
    }

    private fun setUWB() {
        val packageManager: PackageManager = applicationContext.packageManager
        val deviceSupportsUwb = packageManager.hasSystemFeature("android.hardware.uwb")
        if (deviceSupportsUwb)
            Log.e("TEST", "Available")
        else
            Log.e("TEST", "Not available")
    }
}