package com.example.pingmonitor

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class MainActivity : AppCompatActivity() {

    private lateinit var ipInput: EditText
    private lateinit var addBtn: Button
    private lateinit var startBtn: Button
    private lateinit var intervalSeek: SeekBar
    private lateinit var intervalLabel: EditText

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: HostAdapter

    private lateinit var chart: LineChart

    private var isServiceRunning = false

    private val hosts = mutableListOf<HostEntry>()

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Receive updates from service: host list and latencies
            intent?.getSerializableExtra("hosts")?.let { data ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val list = data as ArrayList<HashMap<String, Any>>
                    // update local hosts and UI
                    adapter.updateFromService(list)
                    updateChartFromService(list)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipInput = findViewById(R.id.ipInput)
        addBtn = findViewById(R.id.addBtn)
        startBtn = findViewById(R.id.startBtn)
        intervalSeek = findViewById(R.id.intervalSeek)
        intervalLabel = findViewById(R.id.intervalLabel)

        recycler = findViewById(R.id.recycler)
        chart = findViewById(R.id.lineChart)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = HostAdapter(this) { host ->
            // toggle active -> save to prefs
            saveHostsToPrefs()
            if (isServiceRunning) sendUpdateToService()
        }
        recycler.adapter = adapter

        loadHostsFromPrefs()
        adapter.setHosts(hosts)

        addBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            if (ip.isNotEmpty()) {
                hosts.add(HostEntry(ip, true, mutableListOf()))
                adapter.setHosts(hosts)
                saveHostsToPrefs()
                ipInput.text.clear()
                Toast.makeText(this, "IP añadida", Toast.LENGTH_SHORT).show()
                if (isServiceRunning) sendUpdateToService()
            }
        }

        intervalSeek.max = 120
        intervalSeek.progress = loadIntervalFromPrefs()
        intervalLabel.setText((intervalSeek.progress).toString() + "s")
        intervalSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intervalLabel.setText("$progress s")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveIntervalToPrefs(intervalSeek.progress)
                if (isServiceRunning) sendUpdateToService()
            }
        })

        startBtn.setOnClickListener {
            if (!isServiceRunning) {
                // request notification permission if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@setOnClickListener
                    }
                }
                startForegroundService()
            } else {
                stopForegroundService()
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, IntentFilter("PING_UPDATES"))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startForegroundService()
        } else {
            Toast.makeText(this, "Permiso de notificaciones denegado. Las alertas pueden no mostrarse.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startForegroundService() {
        saveHostsToPrefs()
        saveIntervalToPrefs(intervalSeek.progress)
        val intent = Intent(this, ForegroundPingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isServiceRunning = true
        startBtn.text = "Detener servicio"
    }

    private fun stopForegroundService() {
        val intent = Intent(this, ForegroundPingService::class.java)
        stopService(intent)
        isServiceRunning = false
        startBtn.text = "Iniciar servicio"
    }

    private fun saveHostsToPrefs() {
        val prefs = getSharedPreferences("ping_prefs", MODE_PRIVATE)
        val set = hosts.map { it.toJsonString() }.toSet()
        prefs.edit().putStringSet("hosts_set", set).apply()
    }

    private fun loadHostsFromPrefs() {
        val prefs = getSharedPreferences("ping_prefs", MODE_PRIVATE)
        val set = prefs.getStringSet("hosts_set", emptySet()) ?: emptySet()
        hosts.clear()
        for (s in set) {
            HostEntry.fromJsonString(s)?.let { hosts.add(it) }
        }
    }

    private fun saveIntervalToPrefs(value: Int) {
        val prefs = getSharedPreferences("ping_prefs", MODE_PRIVATE)
        prefs.edit().putInt("interval_seconds", value).apply()
    }

    private fun loadIntervalFromPrefs(): Int {
        val prefs = getSharedPreferences("ping_prefs", MODE_PRIVATE)
        val v = prefs.getInt("interval_seconds", 10)
        return if (v <= 0) 10 else v
    }

    private fun sendUpdateToService() {
        val intent = Intent(this, ForegroundPingService::class.java)
        intent.action = "UPDATE"
        ContextCompat.startForegroundService(this, intent)
    }

    private fun updateChartFromService(list: ArrayList<HashMap<String, Any>>) {
        // show latencies of first active host for simplicity
        if (list.isEmpty()) return
        val first = list.firstOrNull { (it["active"] as? Boolean) == true } ?: list[0]
        val history = first["history"] as? ArrayList<Double> ?: arrayListOf()

        val entries = history.mapIndexed { idx, v -> Entry(idx.toFloat(), v.toFloat()) }
        val set = LineDataSet(entries, "Latencia (ms)")
        set.setDrawCircles(false)
        val data = LineData(set)
        chart.data = data
        val desc = Description()
        desc.text = ""
        chart.description = desc
        chart.invalidate()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
        super.onDestroy()
    }
}
