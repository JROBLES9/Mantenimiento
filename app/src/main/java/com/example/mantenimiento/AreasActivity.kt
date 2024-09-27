package com.example.mantenimiento

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AreasActivity : AppCompatActivity() {
    private lateinit var areaSpinner: Spinner
    private lateinit var selectedArea: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.areas)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        areaSpinner = findViewById(R.id.areaSpinner)
        val mantenimiento: Button = findViewById(R.id.mantenimientoButton)
        val incidencia: Button = findViewById(R.id.incidenciaButton)

        mantenimiento.setOnClickListener {
            selectedArea = areaSpinner.selectedItem.toString()
            openFormActivity(MainActivity::class.java)
        }

        incidencia.setOnClickListener {
            selectedArea = areaSpinner.selectedItem.toString()
            openFormActivity(IncidenciaActivity::class.java)
        }
    }

    private fun openFormActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.putExtra("SELECTED_AREA", selectedArea)
        startActivity(intent)
    }

    }
