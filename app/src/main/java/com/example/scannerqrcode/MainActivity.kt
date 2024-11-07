@file:Suppress("DEPRECATION", "SpellCheckingInspection")

package com.example.scannerqrcode

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.airbnb.lottie.RenderMode
import com.example.scannerqrcode.databinding.ActivityMainBinding
import com.example.scannerqrcode.model.ValidateResponse
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), IScannerView {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private lateinit var presenter: ScannerPresenter
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var flashEnabled = false
    private lateinit var dialog: CustomButtonDialog
    private lateinit var notificationManager: NotificationManager
    private lateinit var reticleAnimation: Animation
    private var isReticleAnimating = false
    private var selectedUsername: String? = "zappele_a"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            100
        )
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        presenter = ScannerPresenter(this, ApiClient.getService())
        dialog = CustomButtonDialog(this@MainActivity, android.R.style.Theme_Translucent_NoTitleBar)
        notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        reticleAnimation = AnimationUtils.loadAnimation(this, R.anim.reticle_animation)
        val channel = NotificationChannel(
            "ZappeleScanner",
            "ZappeleScanner",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        binding.spinner.setOnSpinnerItemSelectedListener<String> { _, _, _, newItem ->
            selectedUsername = newItem
        }
    }

    @SuppressLint("RestrictedApi")
    private fun bindPreview(cameraProvider: ProcessCameraProvider?) {

        if (isDestroyed || isFinishing) {
            return
        }

        cameraProvider?.unbindAll()

        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(binding.cameraPreview.width, binding.cameraPreview.height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val orientationEventListener = object : OrientationEventListener(this as Context) {
            override fun onOrientationChanged(orientation : Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation : Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageAnalysis.targetRotation = rotation
            }
        }
        orientationEventListener.enable()

        //switch the analyzers here, i.e. MLKitBarcodeAnalyzer, ZXingBarcodeAnalyzer
        class ScanningListener : ScanningResultListener {
            override fun onScanned(result: String) {
                runOnUiThread {
                    if (!isReticleAnimating) {
                        startReticleAnimation(cameraProvider, result)
                    }
                }
            }
        }

        val analyzer: ImageAnalysis.Analyzer = MLKitBarcodeAnalyzer(ScanningListener())

        imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

        preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

        val camera =
            cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)

        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            binding.ivFlashControl.visibility = View.VISIBLE

            binding.ivFlashControl.setOnClickListener {
                camera.cameraControl.enableTorch(!flashEnabled)
            }

            camera.cameraInfo.torchState.observe(this) {
                it?.let { torchState ->
                    if (torchState == TorchState.ON) {
                        flashEnabled = true
                        binding.ivFlashControl.isActivated = true
                    } else {
                        flashEnabled = false
                        binding.ivFlashControl.isActivated = false
                    }
                }
            }
        }
    }


    private inner class MLKitBarcodeAnalyzer(private val listener: ScanningResultListener) : ImageAnalysis.Analyzer {

        private var isScanning: Boolean = false

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            // Reticle and Animation Settings
            val reticlePaint = Paint().apply {
                color = applicationContext.getColor(R.color.green)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val mediaImage = imageProxy.image

            if (isScanning) { // Skip if already scanning
                imageProxy.close()
                return
            }

            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                // Barcode Scanning
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstOrNull()?.let { barcode ->
                            val rawValue = barcode.rawValue
                            rawValue?.let {
                                Log.d("Barcode", it)
                                listener.onScanned(it)
                                isScanning = true // Set after successful scan
                            }
                        }
                        // Close resources after barcode processing
                        imageProxy.close()
                        scanner.close()
                    }
                    .addOnFailureListener {
                        isScanning = false
                        imageProxy.close()
                    }
            }

            // Draw reticle (always, even if no QR code found)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val imageHeight = imageProxy.height
            val imageWidth = imageProxy.width
            val reticleSize = imageWidth / 4 // Adjust reticle size as needed

            val reticleRect = Rect(
                (imageWidth / 2) - (reticleSize / 2),
                (imageHeight / 2) - (reticleSize / 2),
                (imageWidth / 2) + (reticleSize / 2),
                (imageHeight / 2) + (reticleSize / 2)
            )

            val bitmap = imageProxy.image?.let { image ->
                val yuvBytes = image.planes.map {
                    val buffer = it.buffer
                    val byteBuffer = ByteArray(buffer.remaining())
                    buffer.get(byteBuffer)
                    byteBuffer
                }

                val yuvImage = YuvImage(
                    yuvBytes[0],
                    ImageFormat.NV21,
                    imageWidth,
                    imageHeight,
                    null
                )
                val outStream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, imageWidth, imageHeight), 100, outStream)
                BitmapFactory.decodeByteArray(outStream.toByteArray(), 0, outStream.size())
            }

            if (bitmap != null) {
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // Make sure it's mutable
                val canvas = Canvas(mutableBitmap)
                canvas.rotate(rotationDegrees.toFloat(), imageWidth / 2f, imageHeight / 2f)
                canvas.drawRect(reticleRect, reticlePaint)

            }

            imageProxy.close()
        }
    }


    private fun startReticleAnimation(cameraProvider: ProcessCameraProvider?, result: String) {
        isReticleAnimating = true
        binding.cameraPreview.startAnimation(reticleAnimation)
        reticleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                isReticleAnimating = false
                //   imageAnalysis.clearAnalyzer()
                cameraProvider?.unbindAll()
//                    bindPreview(cameraProvider)
               presenter.decryptCode(result, selectedUsername ?: "")
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    private fun showDialogs(status: String, message: String) {
        dialog.setDialogTitle(status)
        dialog.setDialogContent(message)
        dialog.setButtonOkText("Tutup")
        dialog.setOnBtnClickListener(object : OnBtnClickListener {
            @SuppressLint("ResourceType")
            override fun okBtnClick(view: View) {
                dialog.dismiss()
                loading(false, "")
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider)
                }, ContextCompat.getMainExecutor(this@MainActivity))
            }

            override fun cancelBtnClick(view: View) {
                dialog.dismiss()
            }

        })
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun loading(status: Boolean, message: String) {
        binding.scanLoadAnim.renderMode = RenderMode.HARDWARE
        binding.scanBg.renderMode = RenderMode.HARDWARE
        if (status) {
            binding.scanLoadAnim.playAnimation()
            binding.loading.visibility = View.VISIBLE
            if (message.isNotEmpty()) binding.textLoad.text = message
        } else {
            binding.scanLoadAnim.cancelAnimation()
            binding.loading.visibility = View.GONE
        }
    }

    override fun error(message: String) {
        Toast.makeText(this@MainActivity, "error : $message", Toast.LENGTH_SHORT).show()
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this@MainActivity))
    }

    override fun validateSuccess(data: ValidateResponse) {
        runOnUiThread {
            showDialogs(if (data.success == true) "Berhasil ${data.obj?.progress}" else "Gagal ${data.obj?.progress}", data.status.toString())
        }
        showNotification(if (data.success == true) "Berhasil ${data.obj?.progress}" else "Gagal ${data.obj?.progress}", data.status.toString())
        binding.scanLoadAnim.cancelAnimation()
    }

    override fun errorApi(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun loadingUserList(status: Boolean) {
        if (status) {
            binding.loadUsername.visibility = View.VISIBLE
            binding.spinner.visibility = View.INVISIBLE
            binding.loadUsername.isIndeterminate = true
        } else {
            binding.loadUsername.visibility = View.GONE
            binding.spinner.visibility = View.VISIBLE
            binding.loadUsername.isIndeterminate = false
        }
    }

    override fun successUserList(data: List<String>) {
        binding.spinner.setItems(data)
        binding.spinner.selectItemByIndex(0)
        if (data.isNotEmpty()) {
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            }, ContextCompat.getMainExecutor(this))
        }
    }


    private fun showNotification(@Suppress("SameParameterValue") title: String, message: String) {
        val builder = NotificationCompat.Builder(applicationContext, "ZappeleScanner")
            .setContentTitle(title)
            .setSmallIcon(R.mipmap.ic_z)
            .setAutoCancel(true)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
        notificationManager.notify(220798, builder.build())
    }

}

interface ScanningResultListener {
    fun onScanned(result: String)
}
