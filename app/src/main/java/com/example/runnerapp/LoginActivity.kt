package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si quieres mostrar el banner del layout:
        setContentView(R.layout.activity_login)

        // Si esta pantalla ya no se usa, por si acaso redirigimos al formulario:
        startActivity(Intent(this, ProfileQuestionnaireActivity::class.java))
        finish()
    }
}
