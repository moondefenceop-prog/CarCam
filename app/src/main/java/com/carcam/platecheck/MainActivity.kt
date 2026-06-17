package com.carcam.platecheck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
    private var isProcessing = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
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
                        ContextCompat.getColor(this, R.color.registered_green))
                } else {
                    binding.tvStatus.text = "❌ 미등록 차량"
                    binding.tvStatus.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.not_registered_red))
                }
                if (result.note.isNotEmpty()) {
                    binding.tvNote.text = result.note
                    binding.tvNote.isVisible = true
                } else {
                    binding.tvNote.isVisible = false
                }
                binding.resultCard.postDelayed({ viewModel.clearResult() }, 3000)
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
        if (isProcessing) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        isProcessing = true
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val plate = KoreanPlateRecognizer.extractPlateNumber(result.text)
                if (plate != null) viewModel.checkPlate(plate)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
    }
}
