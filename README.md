# YOLO_Distance

# 1. YOLOv5 vs. YOLOv8

- 사전 훈련 모델 정보
    
    
    | Model | mAP val(50-95) |
    | --- | --- |
    | YOLOv5n | 28.0 |
    | YOLOv8n | 37.3 |
    | YOLOv8s | 44.9 |
    
    *※ YOLO 공식문서 자료*
    
- 모델의 Parameter와 Speed 비교
    
    ![[https://raw.githubusercontent.com/ultralytics/assets/main/yolov8/yolo-comparison-plots.png](https://raw.githubusercontent.com/ultralytics/assets/main/yolov8/yolo-comparison-plots.png)](https://raw.githubusercontent.com/ultralytics/assets/main/yolov8/yolo-comparison-plots.png)
    
    [https://raw.githubusercontent.com/ultralytics/assets/main/yolov8/yolo-comparison-plots.png](https://raw.githubusercontent.com/ultralytics/assets/main/yolov8/yolo-comparison-plots.png)
    
- 실제 앱 내 FPS
    
    
    | Model | FPS |
    | --- | --- |
    | YOLOv5n | 5.2 |
    | YOLOv8n | 4.9 |
    | YOLOv8s | 2.2 |
    
    *※ 작동 기기: 갤럭시 S21 Ultra 5G*
    
    *※ FPS (First Per Second): 초당 프래임 수*
    

# 2. 실제 구현

# 3. 알고리즘 구현 방식

## 3-1. YOLO structure

1. 그리드에서 분할한 이미지를 모델(YOLOv8n.pt)에 입력
2. 모델(YOLOv8n.pt)에 **1 x 3 x 640 x 640 형태로 변환된 이미지**를 입력
3. 입력된 이미지를 신경망을 통과시켜 **1 x 84 x 8400 형태**로 결과 출력
(1 = 배치 사이즈, 84 = 라벨링 개수와 좌표값, 8400 = 후보군)
4. 통과된 결과를 **NMS(Non-maximum Suppression)**을 이용해 탐지 결과 정리

<aside>
💡 객체를 탐지하는 YOLO Structure를 그대로 사용하였음.

</aside>

## 3-2. 거리 계산 추정

- **피사계의 식**

    ![피사계의 식](https://github.com/hhanoo/yolo_distance_android/assets/71388566/cbdb9996-1039-4cf3-82da-cc367a6d85d0)
    
    $$
    Detect\,Hight : Focal\,Length = Real\,Hight : Distance
    $$
    
    $$
    Distance = (Real\,Hight\, \times \, Focal\,Length)/Detect\,Hight
    $$
    
- **Real Hight**
    
    한국인 인체 치수 조사 (2020) 통계청 자료를 참고
    
    |  |   측정  수 (명) |  평균  (cm) |
    | --- | --- | --- |
    | 남자 | 447 | 24.6   |
    | 여자 | 444 | 23.7 |
    | 전체 | 891 | 24.2 |
    - 통계 자료를 확인해보면 남녀 머리 수직 길이의 차이가 약 1cm로 큰 차이가 나지 않음.
    - 따라서 남녀 구분 없이 평균 값을 사용
    
- **Detect Hight 계산**
    1. 디스플레이의 세로 길이 = 세로 픽셀 개수
    2. 현재 디스플레이의 DPI (화면의 농도를 나타내며, 인치 당 픽셀의 수 의미)
    3. 디스플레이의 세로 길이 / DPI 를 통해 인치 단위로 변환
    4. 인치 단위 값을 센치 단위로 변환 (1 inch = 2.54cm)
    5. 관찰된 object의 세로 픽셀 x 위 값을 곱해 화면 실제 크기 도출
    
- **Focal Length**
    
    안드로이드의 카메라 센서 라이브러리를 통해 Focal Length 도출
    
    ```kotlin
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
    ```
    

## 3-3. 방향 추정

앱 안에서 보이는 카메라 화면 크기를 자체 라이브러리를 통해 확보 (width, height)

가로, 세로를 각각 3분할하여 총 화면을 9분할로 분류

탐지된 객체의 중심점이 9분할된 화면의 위치에 따라 테두리색이 결정


![9분할 화면 위치에 따른 Object Detection의 테두리색](https://github.com/hhanoo/yolo_distance_android/assets/71388566/e39ffcd9-a78f-4d2b-97a1-c5d45f9ede67)


**9분할 화면 위치에 따른 Object Detection의 테두리색**

# Reference

- YOLOv5 : [https://github.com/ultralytics/yolov5](https://github.com/ultralytics/yolov5)
- YOLOv8 : [https://github.com/ultralytics/ultralytics](https://github.com/ultralytics/ultralytics)
- YOLOv8 & Android
    - [https://github.com/Yurve/YOLOv8_Android_coco](https://github.com/Yurve/YOLOv8_Android_coco)
    - [https://velog.io/@aloe/안드로이드-카메라-미리-보기](https://velog.io/@aloe/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EC%B9%B4%EB%A9%94%EB%9D%BC-%EB%AF%B8%EB%A6%AC-%EB%B3%B4%EA%B8%B0)
- YOLOv8 Face : [https://github.com/akanametov/yolov8-face](https://github.com/akanametov/yolov8-face)
- 한국인인체치수조사(머리수직길이) 
: [https://kosis.kr/statHtml/statHtml.do?orgId=115&tblId=DT_115019_312&conn_path=I2](https://kosis.kr/statHtml/statHtml.do?orgId=115&tblId=DT_115019_312&conn_path=I2)
