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
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val isProcessing = AtomicBoolean(false)

    // 최근 인식 번호판 (최신순, 최대 5개)
    private val recentPlates = ArrayDeque<String>()
    private lateinit var recentViews: List<TextView>

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recentViews = listOf(
            binding.tvRecent1, binding.tvRecent2, binding.tvRecent3,
            binding.tvRecent4, binding.tvRecent5
        )

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
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
            // 해상도를 낮춰 OCR 속도 향상
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("CarCam", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy) {
        // AtomicBoolean으로 중복 처리 방지 (lock-free)
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val imgWidth = imageProxy.width
        val imgHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

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
                isProcessing.set(false)
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
