package com.example.runnerapp

import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.Usuario
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class RegisterActivity : AppCompatActivity() {

    private lateinit var inputEmail: EditText
    private lateinit var inputPassword: EditText
    private lateinit var inputApodo: EditText
    private lateinit var btnRegister: Button

    private lateinit var database: AppDatabase  // Asegúrate de tener tu Room DB creada
    private lateinit var sessionManager: SessionManager  // Ya lo habías creado antes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Referencias a los elementos de la vista
        inputEmail = findViewById(R.id.input_email)
        inputPassword = findViewById(R.id.input_password)
        inputApodo = findViewById(R.id.input_apodo)
        btnRegister = findViewById(R.id.btn_register)

        database = AppDatabase.getDatabase(this)  // Tu base de datos local
        sessionManager = SessionManager(this)     // Tu clase SessionManager

        btnRegister.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()
            val apodo = inputApodo.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Correo y contraseña son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val usuarioExistente = withContext(Dispatchers.IO) {
                    database.usuarioDao().getUsuarioByEmail(email)
                }

                if (usuarioExistente != null) {
                    Toast.makeText(this@RegisterActivity, "Este correo ya está registrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val nuevoUsuario = Usuario(email = email, password = password, apodo = apodo)

                withContext(Dispatchers.IO) {
                    database.usuarioDao().insert(nuevoUsuario)
                }

                sessionManager.saveUserSession(email)
                Toast.makeText(this@RegisterActivity, "Registro exitoso", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@RegisterActivity, ProfileQuestionnaireActivity::class.java))
                finish()
            }
        }

    }
}
