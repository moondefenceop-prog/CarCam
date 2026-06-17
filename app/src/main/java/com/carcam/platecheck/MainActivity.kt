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
    // 동시에 처리 중인 프레임 수 (최대 2)
    private val activeJobs = java.util.concurrent.atomic.AtomicInteger(0)

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

                binding.resultCard.postDelayed({
                    viewModel.clearResult()
                    binding.plateOverlay.clear()
                }, 3000)
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
                .setTargetResolution(Size(640, 480)) // 번호판은 굵은 글씨 → 저해상도도 충분
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
        // 동시 처리 2개 초과 시 스킵 (최신 프레임 우선)
        if (activeJobs.get() >= 2) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        val imgWidth = imageProxy.width
        val imgHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        // Y 평면(그레이스케일)만 추출 → 컬러 디코딩 생략으로 처리 속도 향상
        val yPlane = mediaImage.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        // NV21 포맷으로 감싸기 (UV는 0으로 채워 그레이스케일 효과)
        val nv21 = ByteArray(imgWidth * imgHeight * 3 / 2)
        if (yPixelStride == 1 && yRowStride == imgWidth) {
            yBuffer.get(nv21, 0, imgWidth * imgHeight)
        } else {
            // row stride가 다를 경우 행별로 복사
            for (row in 0 until imgHeight) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, row * imgWidth, imgWidth)
            }
        }
        // UV 영역은 이미 0(128,128 중립)으로 초기화되어 있음

        val image = InputImage.fromByteArray(nv21, imgWidth, imgHeight, rotation, InputImage.IMAGE_FORMAT_NV21)

        activeJobs.incrementAndGet()
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val plateBoxes = mutableListOf<Pair<Rect, String>>()
                for (block in visionText.textBlocks) {
                    val candidate = KoreanPlateRecognizer.extractPlateNumber(block.text)
                    if (candidate != null) {
                        block.boundingBox?.let { box -> plateBoxes.add(Pair(box, candidate)) }
                        addToRecent(candidate)
                        viewModel.checkPlate(candidate)
                    }
                }
                runOnUiThread {
                    if (plateBoxes.isNotEmpty()) {
                        binding.plateOverlay.setPlateBoxes(plateBoxes, imgWidth, imgHeight, rotation)
                    } else {
                        binding.plateOverlay.clear()
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
