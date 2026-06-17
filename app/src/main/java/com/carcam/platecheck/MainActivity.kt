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
import com.carcam.platecheck.util.KoreanPlateRecognizer
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

    // 안정화: 1프레임만 보여도 즉시 표시 (인식률 우선)
    private val CONFIRM_THRESHOLD = 1
    private val plateConfirmCount = mutableMapOf<String, Int>()
    private var confirmedPlate: String? = null

    // 지연 clear: 번호판이 안 보여도 800ms 동안 결과 유지
    private val CLEAR_DELAY_MS = 800L
    private var clearOverlayRunnable: Runnable? = null
    private var clearResultRunnable: Runnable? = null

    // 최근 인식 번호판 (최신순, 최대 5개)
    private val recentPlates = ArrayDeque<String>()
    private lateinit var recentViews: List<TextView>

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

        // 스레드 2개: 한 프레임 처리 중에도 다음 프레임 바로 시작
        cameraExecutor = Executors.newFixedThreadPool(2)

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

        viewModel.scanResult.observe(this) { result ->
            if (result == null) {
                binding.resultCard.isVisible = false
                binding.tvScanning.isVisible = true
                confirmedPlate = null
            } else {
                binding.resultCard.isVisible = true
                binding.tvScanning.isVisible = false
                binding.tvPlateNumber.text = result.plateNumber
                if (result.isRegistered) {
                    binding.tvStatus.text = "✅ 등록 차량"
                    binding.tvStatus.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.registered_green)
                    )
                } else {
                    binding.tvStatus.text = "❌ 미등록 차량"
                    binding.tvStatus.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.not_registered_red)
                    )
                }
                binding.tvNote.isVisible = result.note.isNotEmpty()
                if (result.note.isNotEmpty()) binding.tvNote.text = result.note

                // 최근 목록 색상 업데이트
                updateRecentColor(result.plateNumber, result.isRegistered)

                // 결과 카드 3초 후 자동 소거 (이전 예약 취소 후 재등록)
                clearResultRunnable?.let { binding.resultCard.removeCallbacks(it) }
                clearResultRunnable = Runnable {
                    viewModel.clearResult()
                    clearResultRunnable = null
                }
                binding.resultCard.postDelayed(clearResultRunnable!!, 3000)
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
        if (activeJobs.get() >= 2) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        val imgWidth = imageProxy.width
        val imgHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        // 컬러 원본 사용 → ML Kit 한국어 모델 최대 정확도
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        activeJobs.incrementAndGet()
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val plateBoxes = mutableListOf<Pair<Rect, String>>()
                for (block in visionText.textBlocks) {
                    val candidate = KoreanPlateRecognizer.extractPlateNumber(block.text)
                    if (candidate != null) {
                        block.boundingBox?.let { box -> plateBoxes.add(Pair(box, candidate)) }

                        // 연속 감지 카운트 증가
                        val count = (plateConfirmCount[candidate] ?: 0) + 1
                        plateConfirmCount[candidate] = count

                        // CONFIRM_THRESHOLD 이상 연속 감지 시 확정
                        if (count >= CONFIRM_THRESHOLD) {
                            addToRecent(candidate)
                            viewModel.checkPlate(candidate)
                        }
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
                        binding.plateOverlay.setPlateBoxes(plateBoxes, imgWidth, imgHeight, rotation)
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
            .addOnCompleteListener {
                activeJobs.decrementAndGet()
                imageProxy.close()
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

    private fun updateRecentColor(plate: String, isRegistered: Boolean) {
        val idx = recentPlates.indexOf(plate)
        if (idx in recentViews.indices) {
            recentViews[idx].setTextColor(
                if (isRegistered) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
    }
}
