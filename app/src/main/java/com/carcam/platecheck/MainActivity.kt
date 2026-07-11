package com.carcam.platecheck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.carcam.platecheck.databinding.ActivityMainBinding
import com.carcam.platecheck.ui.MainViewModel
import com.carcam.platecheck.util.ImageUtils
import com.carcam.platecheck.util.PlateOcrEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val activeJobs = java.util.concurrent.atomic.AtomicInteger(0)

    // 프레임 쓰로틀: 카메라가 주는 모든 프레임(보통 30fps)을 다 인식하면 발열/배터리 부담이 크다.
    // 한 프레임만 성공해도 확정되고(CONFIRM_THRESHOLD=1) 오버레이도 800ms 유지되므로, 초당 3회
    // 정도만 분석해도 인식 성능 체감은 거의 그대로면서 부하는 크게 줄어든다. 저사양 기기에서는
    // 인식 자체가 이 값보다 오래 걸려서(400~600ms) 사실상 이 값이 최소 휴식 시간 역할을 한다.
    private val ANALYSIS_INTERVAL_MS = 300L
    private val lastAnalyzedAtMs = java.util.concurrent.atomic.AtomicLong(0)

    // 안정화: 1프레임만 보여도 즉시 표시 (인식률 우선)
    private val CONFIRM_THRESHOLD = 1
    private val plateConfirmCount = mutableMapOf<String, Int>()

    // 지연 clear: 번호판이 안 보여도 800ms 동안 결과 유지
    private val CLEAR_DELAY_MS = 800L
    private var clearOverlayRunnable: Runnable? = null
    private var clearResultRunnable: Runnable? = null

    // 최근 인식 번호판 (최신순, 최대 5개)
    private val recentPlates = ArrayDeque<String>()
    private lateinit var recentViews: List<TextView>

    // 번호판별 등록 여부 캐시. DB 조회(비동기)가 끝나야 채워지므로, 박스를 처음 그릴 때는
    // 아직 모를 수 있다(대기 색상으로 표시했다가 조회 완료 시 다음 프레임에서 갱신됨).
    private val registrationCache = mutableMapOf<String, Boolean>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) binding.previewView.post { startCamera() } else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recentViews = listOf(
            binding.tvRecent1, binding.tvRecent2, binding.tvRecent3,
            binding.tvRecent4, binding.tvRecent5
        )

        // 스레드 1개 + 동시 작업 1개로 제한: 저사양 기기에서 인식 1회에 400~600ms가 걸려
        // 코어 2개를 계속 붙잡고 있으면 발열이 심해진다. 한 번에 하나씩만 처리해도
        // CONFIRM_THRESHOLD=1이라 체감 인식 성능은 거의 그대로다.
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // PreviewView가 레이아웃된 후 ViewPort가 생성되므로 post로 지연 실행
            binding.previewView.post { startCamera() }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnPlateList.setOnClickListener {
            startActivity(Intent(this, PlateListActivity::class.java))
        }

        // 화면 중앙에는 아무것도 띄우지 않는다 — 번호판 텍스트와 등록 여부는 오버레이 박스
        // (흰색=등록, 빨간색=미등록)만으로 표시한다. 여기서는 등록 여부 캐시만 갱신한다.
        viewModel.scanResult.observe(this) { result ->
            if (result == null) {
                binding.tvScanning.isVisible = true
            } else {
                binding.tvScanning.isVisible = false
                registrationCache[result.plateNumber] = result.isRegistered

                // 캐시 3초 후 자동 소거 (이전 예약 취소 후 재등록)
                clearResultRunnable?.let { binding.plateOverlay.removeCallbacks(it) }
                clearResultRunnable = Runnable {
                    viewModel.clearResult()
                    clearResultRunnable = null
                }
                binding.plateOverlay.postDelayed(clearResultRunnable!!, 3000)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(960, 540)) // 한글 인식률과 속도의 균형점
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        val prev = lastAnalyzedAtMs.get()
                        if (now - prev < ANALYSIS_INTERVAL_MS || !lastAnalyzedAtMs.compareAndSet(prev, now)) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        processImage(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                // ViewPort로 Preview와 ImageAnalysis가 완전히 동일한 화면 영역을 공유
                // → 화면 어느 위치에 번호판이 있어도 동일하게 인식 및 박스 정렬
                val viewPort = binding.previewView.viewPort
                if (viewPort != null) {
                    val useCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(imageAnalyzer)
                        .setViewPort(viewPort)
                        .build()
                    cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                    )
                }
            } catch (e: Exception) {
                Log.e("CarCam", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy) {
        if (activeJobs.get() >= 1) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        val imgWidth = imageProxy.width
        val imgHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        // ViewPort로 크롭됐을 때 실제 화면(Preview)에 보이는 영역. ImageAnalysis 출력 버퍼 전체가
        // 아니라 이 영역만 화면에 그려지므로, 오버레이 박스도 이 기준으로 맞춰야 한다.
        val visibleRegion = ImageUtils.rotateRect(imageProxy.cropRect, imgWidth, imgHeight, rotation)
        // 컬러 원본 사용 → ML Kit 한국어 모델 최대 정확도
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        activeJobs.incrementAndGet()
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val direct = PlateOcrEngine.extractPlates(visionText)
                    .mapNotNull { (box, text) -> box?.let { it to text } }

                if (direct.isNotEmpty()) {
                    imageProxy.close()
                    handleDetections(direct, visibleRegion)
                    activeJobs.decrementAndGet()
                } else {
                    // 1차 실패 → 프레임을 비트맵으로 변환해 애매한 영역만 잘라 확대 재인식 시도.
                    // 실패한 프레임에서만 추가 비용이 발생하므로 평상시 속도는 그대로 유지된다.
                    val uprightBitmap = runCatching {
                        ImageUtils.imageProxyToUprightBitmap(imageProxy, rotation)
                    }.getOrNull()
                    imageProxy.close()

                    if (uprightBitmap == null) {
                        handleDetections(emptyList(), visibleRegion)
                        activeJobs.decrementAndGet()
                    } else {
                        // uprightBitmap은 전체 버퍼를 회전만 한 것이라 pass1과 같은 로지컬 좌표계를
                        // 쓰므로, 같은 visibleRegion을 그대로 재사용할 수 있다.
                        runZoomPass(visionText, uprightBitmap) { zoomDetections ->
                            handleDetections(zoomDetections, visibleRegion)
                            activeJobs.decrementAndGet()
                        }
                    }
                }
            }
            .addOnFailureListener {
                imageProxy.close()
                activeJobs.decrementAndGet()
            }
    }

    // 1차 인식에서 놓친, 숫자가 충분히 보이는 애매한 영역(단일 블록 또는 초록 2단 번호판처럼 위아래로
    // 쌓인 블록 쌍)만 잘라서 확대 + 대비 보정 후 다시 인식한다. 후보가 없으면 즉시 빈 결과로 끝난다.
    private fun runZoomPass(
        pass1Text: com.google.mlkit.vision.text.Text,
        uprightBitmap: android.graphics.Bitmap,
        onResult: (List<Pair<Rect, String>>) -> Unit
    ) {
        val ambiguousBox = PlateOcrEngine.findAmbiguousDigitBlocks(pass1Text).firstOrNull()
        if (ambiguousBox == null) {
            onResult(emptyList())
            return
        }

        val crop = ImageUtils.adjustContrast(ImageUtils.cropAndUpscale(uprightBitmap, ambiguousBox), 1.4f)
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { cropText ->
                val cropCandidates = PlateOcrEngine.extractPlates(cropText)
                if (cropCandidates.isEmpty()) {
                    onResult(emptyList())
                } else {
                    // 크롭 좌표계 대신 오버레이 표시용으로는 1차에서 찾은 원본 블록 위치를 재사용한다.
                    onResult(cropCandidates.map { (_, text) -> ambiguousBox to text })
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    private fun handleDetections(plateBoxes: List<Pair<Rect, String>>, visibleRegion: Rect) {
        for ((_, candidate) in plateBoxes) {
            // 연속 감지 카운트 증가
            val count = (plateConfirmCount[candidate] ?: 0) + 1
            plateConfirmCount[candidate] = count

            // CONFIRM_THRESHOLD 이상 연속 감지 시 확정
            if (count >= CONFIRM_THRESHOLD) {
                addToRecent(candidate)
                viewModel.checkPlate(candidate)
            }
        }
        // 이번 프레임에서 감지된 번호판 외 카운트 초기화 (연속성 깨짐)
        val detected = plateBoxes.map { it.second }.toSet()
        plateConfirmCount.keys.retainAll(detected)

        runOnUiThread {
            if (plateBoxes.isNotEmpty()) {
                // 감지됨 → 예약된 clear 취소하고 즉시 표시
                clearOverlayRunnable?.let { binding.plateOverlay.removeCallbacks(it) }
                clearOverlayRunnable = null
                val coloredBoxes = plateBoxes.map { (rect, text) ->
                    Triple(rect, text, registrationCache[text])
                }
                binding.plateOverlay.setPlateBoxes(coloredBoxes, visibleRegion)
            } else {
                // 감지 안 됨 → 800ms 후 clear (깜빡임 방지)
                if (clearOverlayRunnable == null) {
                    clearOverlayRunnable = Runnable {
                        binding.plateOverlay.clear()
                        clearOverlayRunnable = null
                        plateConfirmCount.clear()
                    }
                    binding.plateOverlay.postDelayed(clearOverlayRunnable!!, CLEAR_DELAY_MS)
                }
            }
        }
    }

    private fun addToRecent(plate: String) {
        runOnUiThread {
            recentPlates.remove(plate)
            recentPlates.addFirst(plate)
            if (recentPlates.size > 5) recentPlates.removeLast()
            refreshRecentViews()
        }
    }

    private fun refreshRecentViews() {
        recentViews.forEachIndexed { i, tv ->
            if (i < recentPlates.size) {
                tv.text = recentPlates[i]
                tv.visibility = View.VISIBLE
                // 가장 최신 항목은 밝게, 오래된 것은 흐리게
                tv.alpha = 1f - i * 0.15f
                tv.setTextColor(Color.WHITE)
            } else {
                tv.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
    }
}
