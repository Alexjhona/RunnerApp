package com.example.runnerapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MusicActivity : AppCompatActivity() {

    data class Track(val uri: Uri, val name: String)

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvNowPlaying: TextView
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private lateinit var toolbar: MaterialToolbar

    private val tracks = mutableListOf<Track>()
    private lateinit var adapter: TracksAdapter
    private var currentIndex = -1

    // ===== Permiso notificaciones (Android 13+) =====
    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* noop */ }

    private fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ===== Servicio =====
    private var svc: MusicService? = null
    private var bound = false
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            svc = (service as MusicService.LocalBinder).getService()
            bound = true
            syncUiWithService()
        }
        override fun onServiceDisconnected(name: ComponentName) { bound = false; svc = null }
    }

    private fun syncUiWithService() {
        updatePlayPauseIcon(svc?.isPlaying() == true)
        val t = svc?.nowPlaying().orEmpty()
        if (t.isNotBlank()) tvNowPlaying.text = getString(R.string.music_now_playing_fmt, t)
    }

    // Broadcast del servicio para refrescar UI
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val playing = i.getBooleanExtra(MusicService.EXTRA_PLAYING, false)
            val title = i.getStringExtra(MusicService.EXTRA_TITLE).orEmpty()
            tvNowPlaying.text = getString(R.string.music_now_playing_fmt, title)
            updatePlayPauseIcon(playing)
        }
    }

    // Selector de varios audios
    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { addTrackFromUri(it) }
        persistUris(uris)
        refreshList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)

        ensureNotifPermission()

        toolbar = findViewById(R.id.toolbar_music)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rv = findViewById(R.id.rvTracks)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        fabAdd = findViewById(R.id.fabAdd)

        adapter = TracksAdapter(tracks,
            onRowClick = { playIndex(it) },
            onRowPlay  = { playIndex(it) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnPlayPause.setOnClickListener {
            ensureServiceStarted()
            startService(Intent(this, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE))
        }
        fabAdd.setOnClickListener { picker.launch(arrayOf("audio/*")) }

        restorePersistedUris()
        refreshList()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MusicService::class.java), conn, BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(stateReceiver, IntentFilter(MusicService.ACTION_STATE))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
        if (bound) unbindService(conn)
        bound = false
        super.onStop()
    }

    private fun ensureServiceStarted() {
        ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java))
    }

    private fun playIndex(idx: Int) {
        if (idx !in tracks.indices) return
        currentIndex = idx
        val t = tracks[idx]
        ensureServiceStarted()
        startService(
            Intent(this, MusicService::class.java)
                .setAction(MusicService.ACTION_PLAY_URI)
                .putExtra("uri", t.uri)
                .putExtra("title", t.name)
        )
        adapter.setCurrentIndex(idx)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun refreshList() {
        tvEmpty.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun addTrackFromUri(uri: Uri) {
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Audio"
        tracks.add(Track(uri, name))
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    // ==== Persistencia de URIs ====
    private fun persistUris(uris: List<Uri>) {
        uris.forEach {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
        }
        val set = (getSavedUriStrings() + uris.map { it.toString() }).toSet()
        getPrefs().edit().putStringSet("music_uris", set).apply()
    }

    private fun restorePersistedUris() {
        getSavedUriStrings().map { Uri.parse(it) }.forEach { addTrackFromUri(it) }
    }

    private fun getSavedUriStrings(): Set<String> =
        getPrefs().getStringSet("music_uris", emptySet()) ?: emptySet()

    private fun getPrefs() = getSharedPreferences("music_prefs", MODE_PRIVATE)

    // ==== Adapter / ViewHolder ====
    private class TracksAdapter(
        private val items: List<Track>,
        private val onRowClick: (Int) -> Unit,
        private val onRowPlay: (Int) -> Unit
    ) : RecyclerView.Adapter<TrackVH>() {
        private var current = -1
        fun setCurrentIndex(i: Int) { current = i; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track, parent, false)
            return TrackVH(v, onRowClick, onRowPlay)
        }
        override fun onBindViewHolder(holder: TrackVH, position: Int) =
            holder.bind(items[position], position == current)
        override fun getItemCount(): Int = items.size
    }

    private class TrackVH(
        itemView: View,
        val onRowClick: (Int) -> Unit,
        val onRowPlay: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tvName)
        private val btnPlay = itemView.findViewById<ImageButton>(R.id.btnRowPlay)

        fun bind(t: Track, isCurrent: Boolean) {
            tvName.text = t.name
            itemView.setBackgroundResource(
                if (isCurrent) R.drawable.mode_bg_selected else R.drawable.mode_bg_unselected
            )
            itemView.setOnClickListener { onRowClick(bindingAdapterPosition) }
            btnPlay.setOnClickListener { onRowPlay(bindingAdapterPosition) }
        }
    }
}
