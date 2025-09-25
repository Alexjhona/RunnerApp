package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
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

        inputNombre = findViewById(R.id.input_nombre)
        inputEdad = findViewById(R.id.input_edad)
        spinnerGenero = findViewById(R.id.spinner_genero)
        inputEstatura = findViewById(R.id.input_estatura)
        inputPeso = findViewById(R.id.input_peso)
        btnContinuar = findViewById(R.id.btn_continuar)

        val genderOptions = arrayOf("Seleccionar", "Masculino", "Femenino", "Otro")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGenero.adapter = adapter

        btnContinuar.setOnClickListener { saveProfile() }
    }

    private fun saveProfile() {
        val nombre = inputNombre.text.toString().trim()
        val edadStr = inputEdad.text.toString().trim()
        val genero = spinnerGenero.selectedItem?.toString()?.trim().orEmpty()
        val estaturaStr = inputEstatura.text.toString().trim()
        val pesoStr = inputPeso.text.toString().trim()

        if (nombre.isEmpty()) { toast("Por favor ingresa tu nombre"); return }
        if (edadStr.isEmpty()) { toast("Por favor ingresa tu edad"); return }
        if (genero.isEmpty() || genero == "Seleccionar") { toast("Por favor selecciona tu género"); return }
        if (estaturaStr.isEmpty()) { toast("Por favor ingresa tu estatura"); return }
        if (pesoStr.isEmpty()) { toast("Por favor ingresa tu peso"); return }

        val edad = edadStr.toIntOrNull()
        val estatura = estaturaStr.toFloatOrNull()
        val peso = pesoStr.toFloatOrNull()

        if (edad == null || edad !in 1..120) { toast("Por favor ingresa una edad válida (1-120)"); return }
        if (estatura == null || estatura < 50f || estatura > 250f) { toast("Por favor ingresa una estatura válida (50-250 cm)"); return }
        if (peso == null || peso < 20f || peso > 300f) { toast("Por favor ingresa un peso válido (20-300 kg)"); return }

        // Asegura sesión (si quitaste login)
        val userEmail = sessionManager.getUserSession() ?: run {
            val local = "local@runnerapp"
            sessionManager.saveUserSession(local)
            local
        }

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

                sessionManager.setProfileCompleted(true)

                val intent = Intent(this@ProfileQuestionnaireActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                toast("Error al guardar perfil: ${e.message}")
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this@ProfileQuestionnaireActivity, msg, Toast.LENGTH_SHORT).show()
}
