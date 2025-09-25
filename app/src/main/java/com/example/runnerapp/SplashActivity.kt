package com.example.runnerapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Mostramos SIEMPRE nuestro layout (banner completo, fondo blanco)
        setContentView(R.layout.activity_splash)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splash.setOnExitAnimationListener { it.remove() }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val session = SessionManager(this)

            // --- DESCOMENTA UNA SOLA VEZ si quieres forzar mostrar el formulario ahora mismo ---
            // session.clearSession()                 // Limpia todo (incluye profile_completed)
            // session.setProfileCompleted(false)     // Asegura que caiga al formulario
            // ------------------------------------------------------------------------------

            val next = if (session.isProfileCompleted()) {
                MainActivity::class.java
            } else {
                ProfileQuestionnaireActivity::class.java
            }

            startActivity(Intent(this, next))
            finish()
        }, 900L)
    }
}
