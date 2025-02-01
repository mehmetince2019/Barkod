package com.inc.barkod

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.inc.barkod.databinding.ActivityMainBinding
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var recognizedText: String? = null
    private var originalBitmap: Bitmap? = null
    private var photoUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            try {
                photoUri?.let { uri ->
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    originalBitmap = bitmap
                    binding.imgPhoto.scaleType = ImageView.ScaleType.FIT_START
                    binding.imgPhoto.setImageBitmap(bitmap)
                    // Fotoğraf gösterildikten sonra URI'yi sil
                    contentResolver.delete(uri, null, null)
                    Toast.makeText(this, "Foto erfolgreich aufgenommen", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Fehler beim Anzeigen des Fotos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Kein Foto aufgenommen", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            openCamera()
        } else {
            Toast.makeText(this, "Kamera- und Speicherberechtigungen erforderlich", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        setupUI()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun setupUI() {
        binding.txtResult.isEnabled = false

        binding.btnOpenCamera.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                checkAndRequestPermissions()
            }
        }

        binding.btnZoomIn.setOnClickListener {
            try {
                val cropImageView = binding.imgPhoto as CropImageView
                cropImageView.zoomIn()
            } catch (e: Exception) {
                Toast.makeText(this, "Zoom-Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnZoomOut.setOnClickListener {
            try {
                val cropImageView = binding.imgPhoto as CropImageView
                cropImageView.zoomOut()
            } catch (e: Exception) {
                Toast.makeText(this, "Zoom-Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            if (originalBitmap == null) {
                Toast.makeText(this, "Bitte machen Sie zuerst ein Foto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cropImageView = binding.imgPhoto as CropImageView
            val selectedRegion = cropImageView.getSelectedRegion()

            if (selectedRegion.width() <= 0 || selectedRegion.height() <= 0) {
                Toast.makeText(this, "Bitte wählen Sie einen Bereich aus", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            processImage(originalBitmap!!, selectedRegion)
        }

        binding.btnScan.setOnClickListener {
            if (originalBitmap == null) {
                Toast.makeText(this, "Bitte machen Sie zuerst ein Foto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cropImageView = binding.imgPhoto as CropImageView
            val selectedRegion = cropImageView.getSelectedRegion()

            if (selectedRegion.width() <= 0 || selectedRegion.height() <= 0) {
                Toast.makeText(this, "Bitte wählen Sie einen Bereich aus", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val scaleX = originalBitmap!!.width.toFloat() / binding.imgPhoto.width
                val scaleY = originalBitmap!!.height.toFloat() / binding.imgPhoto.height

                val scaledLeft = (selectedRegion.left * scaleX).roundToInt()
                val scaledTop = (selectedRegion.top * scaleY).roundToInt()
                val scaledWidth = (selectedRegion.width() * scaleX).roundToInt()
                val scaledHeight = (selectedRegion.height() * scaleY).roundToInt()

                val croppedBitmap = Bitmap.createBitmap(
                    originalBitmap!!,
                    maxOf(0, minOf(scaledLeft, originalBitmap!!.width - 1)),
                    maxOf(0, minOf(scaledTop, originalBitmap!!.height - 1)),
                    minOf(scaledWidth, originalBitmap!!.width - scaledLeft),
                    minOf(scaledHeight, originalBitmap!!.height - scaledTop)
                )

                val image = InputImage.fromBitmap(croppedBitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        if (visionText.text.isNotEmpty()) {
                            recognizedText = visionText.text
                            binding.txtResult.setText(recognizedText)
                            Toast.makeText(this, "Text erfolgreich gescannt", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Kein Text im ausgewählten Bereich gefunden", Toast.LENGTH_SHORT).show()
                        }
                        // İşlem bittikten sonra bitmap'i temizle
                        croppedBitmap.recycle()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Scan-Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                        croppedBitmap.recycle()
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Bildverarbeitungsfehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEdit.setOnClickListener {
            binding.txtResult.isEnabled = !binding.txtResult.isEnabled
            binding.btnEdit.text = if (binding.txtResult.isEnabled) "Speichern" else "4. Bearbeiten"

            if (!binding.txtResult.isEnabled) {
                recognizedText = binding.txtResult.text.toString()
                Toast.makeText(this, "Text gespeichert", Toast.LENGTH_SHORT).show()
            } else {
                binding.txtResult.requestFocus()
            }
        }

        binding.btnCreateBarcode.setOnClickListener {
            val textToEncode = binding.txtResult.text.toString()
            if (textToEncode.isEmpty()) {
                Toast.makeText(this, "Bitte scannen Sie zuerst einen Text oder geben Sie einen ein!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val multiFormatWriter = MultiFormatWriter()
                val bitMatrix = multiFormatWriter.encode(
                    textToEncode,
                    BarcodeFormat.QR_CODE,
                    500,
                    500
                )
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.createBitmap(bitMatrix)
                binding.imgBarcode.setImageBitmap(bitmap)
                Toast.makeText(this, "Barcode erfolgreich erstellt", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Fehler beim Erstellen des Barcodes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openCamera() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            photoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            photoUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Öffnen der Kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(bitmap: Bitmap, selectedRegion: RectF) {
        try {
            val scaleX = bitmap.width.toFloat() / binding.imgPhoto.width
            val scaleY = bitmap.height.toFloat() / binding.imgPhoto.height

            val scaledLeft = (selectedRegion.left * scaleX).roundToInt()
            val scaledTop = (selectedRegion.top * scaleY).roundToInt()
            val scaledWidth = (selectedRegion.width() * scaleX).roundToInt()
            val scaledHeight = (selectedRegion.height() * scaleY).roundToInt()

            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                maxOf(0, minOf(scaledLeft, bitmap.width - 1)),
                maxOf(0, minOf(scaledTop, bitmap.height - 1)),
                minOf(scaledWidth, bitmap.width - scaledLeft),
                minOf(scaledHeight, bitmap.height - scaledTop)
            )

            // İşlem bittikten sonra bitmap'i temizle
            croppedBitmap.recycle()
            Toast.makeText(this, "Bereich ausgewählt", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler bei der Bildverarbeitung: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Activity kapatılırken tüm bitmap'leri temizle
        originalBitmap?.recycle()
        originalBitmap = null
    }
}