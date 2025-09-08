package com.example.runnerapp


import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun saveUserSession(email: String) {
        prefs.edit().putString("user_email", email).apply()
    }

    fun getUserSession(): String? {
        return prefs.getString("user_email", null)
    }

    fun isLoggedIn(): Boolean {
        return getUserSession() != null
    }

    fun setProfileCompleted(completed: Boolean) {
        prefs.edit().putBoolean("profile_completed", completed).apply()
    }

    fun isProfileCompleted(): Boolean {
        return prefs.getBoolean("profile_completed", false)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
