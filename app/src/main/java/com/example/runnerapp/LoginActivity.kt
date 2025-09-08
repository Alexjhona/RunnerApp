package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // Si ya hay sesión guardada, verificar si completó el perfil
        if (sessionManager.isLoggedIn()) {
            if (sessionManager.isProfileCompleted()) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, ProfileQuestionnaireActivity::class.java))
            }
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        // Botón único "Ingresar"
        val btnEnter = findViewById<MaterialButton>(R.id.btn_enter)
        btnEnter.setOnClickListener {
            // Guardamos una sesión simple (modo invitado)
            sessionManager.saveUserSession("guest")
            startActivity(Intent(this, ProfileQuestionnaireActivity::class.java))
            finish()
        }
    }
}
