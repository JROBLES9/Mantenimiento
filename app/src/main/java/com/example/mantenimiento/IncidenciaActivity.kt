package com.example.mantenimiento

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64
import java.io.File
import java.io.IOException
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.location.Location
import android.net.Uri
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IncidenciaActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var textViewLatLong: TextView
    private lateinit var locationManager: LocationManager
    private lateinit var currentPhotoPath: String
    private lateinit var selectedArea: String
    private val client = OkHttpClient()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.incidencia)
        val selectedArea = intent.getStringExtra("SELECTED_AREA")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //enviar y regresar
        val enviarButton: Button = findViewById(R.id.continueButton)
        enviarButton.setOnClickListener {
            val intent = Intent(this, AreasActivity::class.java)
            startActivity(intent)
        }

        textViewLatLong = findViewById(R.id.textViewLatLong)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val buttonTomarF: ImageButton = findViewById(R.id.buttonTomafF)
        buttonTomarF.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            } else {
                openCamera()
            }
        }
    }

    private fun sendPostRequest(imagePath: String, lat: Double, lon: Double, comment: String, tipo: String, area: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageBytes = resizeImage(imagePath, 1024, 1024)
                val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                Log.d("imageBase64", imageBase64)
                val json = """{
                    "data": "${imageBase64.replace("\n", "\\n").replace("\r", "\\r")}",
                    "lat": $lat,
                    "longitud": $lon,
                    "Comentario": "$comment",
                    "IdTipoIncidencia": "$tipo",
                    "Area": "$area"
                }"""
                val requestBody: RequestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://bfkx72r7-3000.use.devtunnels.ms/")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val responseBody = response.body?.string()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@IncidenciaActivity, responseBody, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resizeImage(imagePath: String, width: Int, height: Int): ByteArray {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return outputStream.toByteArray()
    }

    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(null)
        return try {
            File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
        } catch (ex: IOException) {
            null
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = BitmapFactory.decodeFile(currentPhotoPath)
            imageView.setImageBitmap(imageBitmap)
            getLocation()
        }
    }

    private fun openCamera() {
        val photoFile: File? = createImageFile()
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "com.example.mantenimiento.fileprovider",
                it
            )
            currentPhotoPath = it.absolutePath
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            }
            cameraLauncher.launch(takePictureIntent)
        }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        } else {
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            location?.let {
                val lat = it.latitude
                val lon = it.longitude
                textViewLatLong.text = "($lat, $lon)"

                val comment = findViewById<EditText>(R.id.commentEditText).text.toString()
                val tipo = findViewById<Spinner>(R.id.incidenciaSpinner).selectedItem.toString()

                sendPostRequest(currentPhotoPath, lat, lon, comment, tipo, selectedArea)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openCamera()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getLocation()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val REQUEST_LOCATION_PERMISSION = 2

    }
}