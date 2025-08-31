package com.example.runnerapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.runnerapp.data.AppDatabase
import com.example.runnerapp.data.RunSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistorialActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView

    private val adapter = RunsAdapter { run ->
        val i = Intent(this, RunDetailActivity::class.java)
        i.putExtra("run_id", run.id)
        startActivity(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        // Toolbar con flecha back
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar_hist)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }

        rv = findViewById(R.id.rvRuns)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { db.runSessionDao().getAll() }
            adapter.submit(data)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} // 👈 esta llave cierra la clase

// ---------- Adapter sencillo ----------
private class RunsAdapter(
    val onClick: (RunSession) -> Unit
) : RecyclerView.Adapter<RunVH>() {

    private val items = mutableListOf<RunSession>()
    fun submit(list: List<RunSession>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RunVH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run, parent, false)
        return RunVH(v, onClick)
    }

    override fun onBindViewHolder(holder: RunVH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}

private class RunVH(
    v: android.view.View,
    val onClick: (RunSession) -> Unit
) : RecyclerView.ViewHolder(v) {
    private val title = v.findViewById<android.widget.TextView>(R.id.tvTitle)
    private val sub   = v.findViewById<android.widget.TextView>(R.id.tvSub)
    private val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    fun bind(run: RunSession) {
        title.text = "${df.format(Date(run.startTime))} • ${run.sport}"
        val km = run.distanceMeters / 1000.0
        val h = run.durationSec / 3600
        val m = (run.durationSec % 3600) / 60
        val s = run.durationSec % 60
        sub.text = String.format(
            Locale.getDefault(),
            "Tiempo %02d:%02d:%02d • Dist %.2f km • Pasos %d",
            h, m, s, km, run.steps
        )
        itemView.setOnClickListener { onClick(run) }
    }
}
