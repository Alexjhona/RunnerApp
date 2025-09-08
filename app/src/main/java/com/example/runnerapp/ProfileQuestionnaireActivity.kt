package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.AppDatabase
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileQuestionnaireActivity : AppCompatActivity() {

    private lateinit var inputNombre: EditText
    private lateinit var inputEdad: EditText
    private lateinit var spinnerGenero: Spinner
    private lateinit var inputEstatura: EditText
    private lateinit var inputPeso: EditText
    private lateinit var btnContinuar: MaterialButton

    private lateinit var database: AppDatabase
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_questionnaire)

        database = AppDatabase.getDatabase(this)
        sessionManager = SessionManager(this)

        // Initialize views
        inputNombre = findViewById(R.id.input_nombre)
        inputEdad = findViewById(R.id.input_edad)
        spinnerGenero = findViewById(R.id.spinner_genero)
        inputEstatura = findViewById(R.id.input_estatura)
        inputPeso = findViewById(R.id.input_peso)
        btnContinuar = findViewById(R.id.btn_continuar)

        // Setup gender spinner
        val genderOptions = arrayOf("Seleccionar", "Masculino", "Femenino", "Otro")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGenero.adapter = adapter

        btnContinuar.setOnClickListener {
            saveProfile()
        }
    }

    private fun saveProfile() {
        val nombre = inputNombre.text.toString().trim()
        val edadStr = inputEdad.text.toString().trim()
        val genero = spinnerGenero.selectedItem.toString()
        val estaturaStr = inputEstatura.text.toString().trim()
        val pesoStr = inputPeso.text.toString().trim()

        // Validation
        if (nombre.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa tu nombre", Toast.LENGTH_SHORT).show()
            return
        }

        if (edadStr.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa tu edad", Toast.LENGTH_SHORT).show()
            return
        }

        if (genero == "Seleccionar") {
            Toast.makeText(this, "Por favor selecciona tu género", Toast.LENGTH_SHORT).show()
            return
        }

        if (estaturaStr.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa tu estatura", Toast.LENGTH_SHORT).show()
            return
        }

        if (pesoStr.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa tu peso", Toast.LENGTH_SHORT).show()
            return
        }

        val edad = edadStr.toIntOrNull()
        val estatura = estaturaStr.toFloatOrNull()
        val peso = pesoStr.toFloatOrNull()

        if (edad == null || edad < 1 || edad > 120) {
            Toast.makeText(this, "Por favor ingresa una edad válida (1-120)", Toast.LENGTH_SHORT).show()
            return
        }

        if (estatura == null || estatura < 50 || estatura > 250) {
            Toast.makeText(this, "Por favor ingresa una estatura válida (50-250 cm)", Toast.LENGTH_SHORT).show()
            return
        }

        if (peso == null || peso < 20 || peso > 300) {
            Toast.makeText(this, "Por favor ingresa un peso válido (20-300 kg)", Toast.LENGTH_SHORT).show()
            return
        }

        // Save to database
        val userEmail = sessionManager.getUserSession()
        if (userEmail != null) {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        database.usuarioDao().updateUserProfile(
                            email = userEmail,
                            nombre = nombre,
                            edad = edad,
                            genero = genero,
                            estatura = estatura,
                            peso = peso,
                            completed = true
                        )
                    }
                    
                    // Mark profile as completed in session
                    sessionManager.setProfileCompleted(true)
                    
                    Toast.makeText(this@ProfileQuestionnaireActivity, "Perfil guardado exitosamente", Toast.LENGTH_SHORT).show()
                    
                    // Navigate to main activity
                    startActivity(Intent(this@ProfileQuestionnaireActivity, MainActivity::class.java))
                    finish()
                    
                } catch (e: Exception) {
                    Toast.makeText(this@ProfileQuestionnaireActivity, "Error al guardar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
