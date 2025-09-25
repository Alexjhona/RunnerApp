package com.example.runnerapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.runnerapp.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.pow

class BMICalculatorActivity : AppCompatActivity() {

    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var btnCalculate: Button
    private lateinit var btnLoadUserData: Button
    private lateinit var tvBMIResult: TextView
    private lateinit var tvBMICategory: TextView
    private lateinit var tvBMIDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bmi_calculator)

        supportActionBar?.title = "Calculadora IMC"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        etWeight = findViewById(R.id.et_weight)
        etHeight = findViewById(R.id.et_height)
        btnCalculate = findViewById(R.id.btn_calculate)
        btnLoadUserData = findViewById(R.id.btn_load_user_data)
        tvBMIResult = findViewById(R.id.tv_bmi_result)
        tvBMICategory = findViewById(R.id.tv_bmi_category)
        tvBMIDescription = findViewById(R.id.tv_bmi_description)
    }

    private fun setupClickListeners() {
        btnCalculate.setOnClickListener {
            calculateBMI()
        }

        btnLoadUserData.setOnClickListener {
            loadUserData()
        }
    }

    private fun calculateBMI() {
        val weightStr = etWeight.text.toString().trim()
        val heightStr = etHeight.text.toString().trim()

        if (weightStr.isEmpty() || heightStr.isEmpty()) {
            tvBMIResult.text = "Por favor, ingresa peso y altura"
            tvBMICategory.text = ""
            tvBMIDescription.text = ""
            return
        }

        try {
            val weight = weightStr.toFloat()
            val height = heightStr.toFloat()

            if (weight <= 0 || height <= 0) {
                tvBMIResult.text = "Valores deben ser mayores a 0"
                tvBMICategory.text = ""
                tvBMIDescription.text = ""
                return
            }

            // Convert height from cm to meters
            val heightInMeters = height / 100.0f
            
            // Calculate BMI: Weight(kg) / (Height(m) ^ 2)
            val bmi = weight / (heightInMeters.pow(2))

            // Display result
            tvBMIResult.text = String.format(Locale.getDefault(), "IMC: %.1f", bmi)
            
            // Determine category and description
            val (category, description, color) = getBMICategory(bmi)
            tvBMICategory.text = category
            tvBMICategory.setTextColor(getColor(color))
            tvBMIDescription.text = description

        } catch (e: NumberFormatException) {
            tvBMIResult.text = "Valores inválidos"
            tvBMICategory.text = ""
            tvBMIDescription.text = ""
        }
    }

    private fun getBMICategory(bmi: Float): Triple<String, String, Int> {
        return when {
            bmi < 18.5 -> Triple(
                "Bajo peso",
                "Un IMC menor a 18.5 indica bajo peso. Se recomienda consultar con un profesional de la salud para evaluar tu estado nutricional.",
                android.R.color.holo_blue_dark
            )
            bmi < 25.0 -> Triple(
                "Peso normal",
                "¡Excelente! Tu IMC está en el rango normal. Mantén un estilo de vida saludable con ejercicio regular y alimentación balanceada.",
                android.R.color.holo_green_dark
            )
            bmi < 30.0 -> Triple(
                "Sobrepeso",
                "Tu IMC indica sobrepeso. Considera adoptar hábitos más saludables como ejercicio regular y una dieta balanceada.",
                android.R.color.holo_orange_dark
            )
            else -> Triple(
                "Obesidad",
                "Tu IMC indica obesidad. Es recomendable consultar con un profesional de la salud para desarrollar un plan personalizado.",
                android.R.color.holo_red_dark
            )
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                // Show loading state
                btnLoadUserData.isEnabled = false
                btnLoadUserData.text = "Cargando..."
                tvBMIResult.text = "Cargando datos del usuario..."
                tvBMICategory.text = ""
                tvBMIDescription.text = ""
                
                val db = AppDatabase.getDatabase(this@BMICalculatorActivity)
                val sessionManager = SessionManager(this@BMICalculatorActivity)
                val userEmail = sessionManager.getUserSession()
                
                // Validate session
                if (userEmail.isNullOrEmpty()) {
                    tvBMIResult.text = "No hay sesión activa. Inicia sesión primero."
                    Toast.makeText(this@BMICalculatorActivity, "No hay sesión activa", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Query database
                val user = withContext(Dispatchers.IO) {
                    db.usuarioDao().getUsuarioByEmail(userEmail)
                }
                
                // Handle user not found
                if (user == null) {
                    tvBMIResult.text = "Usuario no encontrado en la base de datos"
                    Toast.makeText(this@BMICalculatorActivity, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Validate user data
                val peso = user.peso
                val estatura = user.estatura
                
                when {
                    peso == null && estatura == null -> {
                        tvBMIResult.text = "No tienes datos de peso y altura guardados"
                        Toast.makeText(this@BMICalculatorActivity, "Completa tu perfil primero para cargar tus datos", Toast.LENGTH_LONG).show()
                    }
                    peso == null -> {
                        tvBMIResult.text = "No tienes datos de peso guardados"
                        estatura?.let { 
                            etHeight.setText(String.format(Locale.getDefault(), "%.1f", it))
                        }
                        Toast.makeText(this@BMICalculatorActivity, "Solo se cargó la altura. Ingresa tu peso manualmente", Toast.LENGTH_LONG).show()
                    }
                    estatura == null -> {
                        tvBMIResult.text = "No tienes datos de altura guardados"
                        etWeight.setText(String.format(Locale.getDefault(), "%.1f", peso))
                        Toast.makeText(this@BMICalculatorActivity, "Solo se cargó el peso. Ingresa tu altura manualmente", Toast.LENGTH_LONG).show()
                    }
                    peso <= 0 || estatura <= 0 -> {
                        tvBMIResult.text = "Los datos guardados no son válidos"
                        Toast.makeText(this@BMICalculatorActivity, "Tus datos guardados no son válidos. Actualiza tu perfil", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        // Successfully load both values
                        etWeight.setText(String.format(Locale.getDefault(), "%.1f", peso))
                        etHeight.setText(String.format(Locale.getDefault(), "%.1f", estatura))
                        tvBMIResult.text = "Datos cargados correctamente"
                        Toast.makeText(this@BMICalculatorActivity, "Datos cargados exitosamente", Toast.LENGTH_SHORT).show()
                        
                        // Auto-calculate BMI after successful loading
                        calculateBMI()
                    }
                }
                
            } catch (e: Exception) {
                // Handle specific exceptions
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Sin conexión a internet"
                    is java.util.concurrent.TimeoutException -> "Tiempo de espera agotado"
                    else -> "Error al cargar datos: ${e.localizedMessage}"
                }
                
                tvBMIResult.text = errorMessage
                tvBMICategory.text = ""
                tvBMIDescription.text = ""
                Toast.makeText(this@BMICalculatorActivity, errorMessage, Toast.LENGTH_LONG).show()
                
            } finally {
                // Restore button state
                btnLoadUserData.isEnabled = true
                btnLoadUserData.text = "Cargar mis datos"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
