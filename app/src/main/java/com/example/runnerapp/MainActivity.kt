package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        drawer = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)

        // Usa tus strings existentes: nav_open / nav_close
        toggle = ActionBarDrawerToggle(
            this, drawer, toolbar,
            R.string.nav_open, R.string.nav_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        if (savedInstanceState == null) showDashboard()

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_item_workout_routines -> {
                    startActivity(Intent(this, WorkoutRoutinesActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_exercise_progress -> {
                    startActivity(Intent(this, ExerciseProgressActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_hydration -> {
                    startActivity(Intent(this, HydrationActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_achievements -> {
                    startActivity(Intent(this, AchievementsActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_goals -> {
                    startActivity(Intent(this, GoalsActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_record -> {
                    startActivity(Intent(this, HistorialActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_music -> {
                    startActivity(Intent(this, MusicActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_statistics -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_bmi_calculator -> {
                    startActivity(Intent(this, BMICalculatorActivity::class.java))
                    drawer.closeDrawers(); true
                }
                R.id.nav_item_signout -> {
                    sessionManager.clearSession()
                    val i = Intent(this, ProfileQuestionnaireActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(i)
                    true
                }
                else -> { drawer.closeDrawers(); true }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                else -> finish()
            }
        }
    }

    private fun showDashboard() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DashboardFragment())
            .commit()
    }
}
