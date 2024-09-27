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
import android.widget.Button
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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var textViewLatLong: TextView
    private lateinit var locationManager: LocationManager
    private lateinit var currentPhotoPath: String
    private lateinit var spinnerTipoMantenimiento: Spinner
    private lateinit var spinnerUbicacion: Spinner
    private lateinit var editTextComentario: EditText

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        imageView = findViewById(R.id.imageView)
        textViewLatLong = findViewById(R.id.textViewLatLong)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        spinnerTipoMantenimiento = findViewById(R.id.tipoMantenimiento)
        spinnerUbicacion = findViewById(R.id.ubicacion)
        editTextComentario = findViewById(R.id.Comentario)

        loadTipoMantenimiento()
        loadUbicaciones()

        val buttonTomarF: Button = findViewById(R.id.buttonTomafF)
        buttonTomarF.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            } else {
                openCamera()
            }
        }
    }

    data class TipoMantenimiento(val id: Int, val nombre: String) {
        override fun toString(): String {
            return nombre
        }
    }

    data class Ubicacion(val id: Int, val descripcion: String) {
        override fun toString(): String {
            return descripcion
        }
    }


    private fun parseTipoMantenimiento(json: String?): List<TipoMantenimiento> {
        val tiposMantenimiento = mutableListOf<TipoMantenimiento>()
        json?.let {
            val jsonArray = JSONArray(it)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getInt("IdTipoMantenimiento")
                val nombre = jsonObject.getString("NombreMantenimiento")
                tiposMantenimiento.add(TipoMantenimiento(id, nombre))
            }
        }
        return tiposMantenimiento
    }

    private fun parseUbicaciones(json: String?): List<Ubicacion> {
        val ubicaciones = mutableListOf<Ubicacion>()
        json?.let {
            val jsonArray = JSONArray(it)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getInt("IdUbicaciones")
                val descripcion = jsonObject.getString("NombreUbicacion")
                ubicaciones.add(Ubicacion(id, descripcion))
            }
        }
        return ubicaciones
    }


    private fun loadTipoMantenimiento() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://bfkx72r7-3000.use.devtunnels.ms/tipoMantenimiento/")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Error al obtener tipos de mantenimiento: $response")
                    val responseBody = response.body?.string()
                    val tiposMantenimiento = parseTipoMantenimiento(responseBody)
                    withContext(Dispatchers.Main) {
                        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, tiposMantenimiento)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerTipoMantenimiento.adapter = adapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadUbicaciones() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://bfkx72r7-3000.use.devtunnels.ms/ubicacion/")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Error al obtener ubicaciones: $response")
                    val responseBody = response.body?.string()
                    val ubicaciones = parseUbicaciones(responseBody)
                    withContext(Dispatchers.Main) {
                        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, ubicaciones)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerUbicacion.adapter = adapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun sendPostRequest(imagePath: String, latitude: Double?, longitude: Double?) {
        // Recuperar los IDs y el comentario
        val tipoMantenimientoSeleccionado = spinnerTipoMantenimiento.selectedItem as? TipoMantenimiento
        val ubicacionSeleccionada = spinnerUbicacion.selectedItem as? Ubicacion
        val comentario = editTextComentario.text.toString()

        // Validar que los datos necesarios no sean nulos
        if (tipoMantenimientoSeleccionado == null || ubicacionSeleccionada == null || latitude == null || longitude == null) {
            runOnUiThread {
                Toast.makeText(this, "Faltan datos necesarios para enviar la solicitud.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageBytes = resizeImage(imagePath, 1024, 1024)
                val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                Log.d("MainActivity", "imageBase64: $imageBase64")

                // Construir el JSON utilizando JSONObject
                val json = JSONObject().apply {
                    put("IdTipoMantenimiento", tipoMantenimientoSeleccionado.id)
                    put("IdUbicacion", ubicacionSeleccionada.id)
                    put("Comentario", comentario)
                    put("IdUsuario", 3)
                    put("image", imageBase64)
                    put("lat", latitude.toString())
                    put("longitud", longitude.toString())
                }

                Log.d("MainActivity", "JSON Enviado: ${json.toString()}")

                val requestBody: RequestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://bfkx72r7-3000.use.devtunnels.ms/fotoMantenimiento/")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    Log.d("MainActivity", "Respuesta: $responseBody")
                    if (!response.isSuccessful) throw IOException("Código inesperado $response")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, responseBody, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al enviar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (location != null) {
                        val lat = location.latitude
                        val lon = location.longitude
                        textViewLatLong.text = "($lat, $lon)"
                        Log.d("MainActivity", "Latitud: $lat, Longitud: $lon")
                        sendPostRequest(currentPhotoPath, lat, lon)
                    } else {
                        // Si la ubicación es null, mostrar mensaje y no enviar la solicitud
                        runOnUiThread {
                            Toast.makeText(this, "No se pudo obtener la ubicación actual.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show()
                }
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            }
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
            try {
                val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                location?.let {
                    val lat = it.latitude
                    val lon = it.longitude
                    textViewLatLong.text = "($lat, $lon)"
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openCamera()
                } else {
                    Toast.makeText(this, "El permiso de cámara es necesario para tomar fotos.", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getLocation()
                } else {
                    Toast.makeText(this, "El permiso de ubicación es necesario para acceder a la ubicación.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val REQUEST_LOCATION_PERMISSION = 2
    }


}