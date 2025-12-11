package com.example.pingmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.concurrent.thread
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.ArrayList

class ForegroundPingService : Service() {

    private val channelId = "ping_service_channel"
    private val alertChannelId = "ping_alerts"
    private val handler = Handler(Looper.getMainLooper())
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val hostMap = ConcurrentHashMap<String, HostEntry>()
    private var intervalSeconds = 10

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        loadPrefs()
        startForeground(2, buildServiceNotification("Monitoring ${hostMap.size} hosts"))
        startScheduler()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service channel
            val svc = NotificationChannel(channelId, "Ping Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(svc)
            // Alert channel with sound
            val alert = NotificationChannel(alertChannelId, "Ping Alerts", NotificationManager.IMPORTANCE_HIGH)
            val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            alert.setSound(alarmSound, attrs)
            nm.createNotificationChannel(alert)
        }
    }

    private fun buildServiceNotification(text: String): Notification {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ping Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences("ping_prefs", Context.MODE_PRIVATE)
        intervalSeconds = prefs.getInt("interval_seconds", 10)
        val set = prefs.getStringSet("hosts_set", emptySet()) ?: emptySet()
        for (s in set) {
            try {
                val o = JSONObject(s)
                val ip = o.getString("ip")
                val active = o.optBoolean("active", true)
                val hist = mutableListOf<Double>()
                val arr = o.optJSONArray("history")
                if (arr != null) {
                    for (i in 0 until arr.length()) hist.add(arr.optDouble(i, 0.0))
                }
                hostMap[ip] = HostEntry(ip, active, hist)
            } catch (e: Exception) {}
        }
        // If none configured, add default provided IP
        if (hostMap.isEmpty()) {
            hostMap["45.238.146.173"] = HostEntry("45.238.146.173", true, mutableListOf())
        }
    }

    private fun startScheduler() {
        scheduler.scheduleAtFixedRate({
            for ((ip, host) in hostMap) {
                if (!host.active) continue
                // perform ping in thread
                thread {
                    val (online, latency) = pingHost(ip)
                    synchronized(host) {
                        if (online) {
                            host.history.add(latency?.toDouble() ?: 0.0)
                            if (host.history.size > 100) host.history.removeAt(0)
                        } else {
                            host.history.add(0.0)
                            if (host.history.size > 100) host.history.removeAt(0)
                        }

                        // send notification if transitioned to down
                        val size = host.history.size
                        val recentlyDown = size >= 2 && host.history[size - 1] == 0.0 && host.history[size - 2] != 0.0
                        if (recentlyDown) {
                            sendAlertNotification(ip)
                        }
                    }
                }
            }
            // broadcast update to activity
            broadcastHosts()
        }, 0, intervalSeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun sendAlertNotification(ip: String) {
        val builder = NotificationCompat.Builder(this, alertChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Servidor no responde")
            .setContentText("Fallo el ping a $ip")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        val nm = NotificationManagerCompat.from(this)
        nm.notify(ip.hashCode(), builder.build())
    }

    private fun broadcastHosts() {
        val list = ArrayList<HashMap<String, Any>>()
        for ((ip, host) in hostMap) {
            val map = HashMap<String, Any>()
            map["ip"] = ip
            map["active"] = host.active
            map["history"] = ArrayList(host.history)
            list.add(map)
        }
        val intent = Intent("PING_UPDATES")
        intent.putExtra("hosts", list)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun pingHost(ip: String): Pair<Boolean, Int?> {
        return try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 $ip")
            val result = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            if (result == 0) {
                val regex = "time=(\\d+\\.\\d+)".toRegex()
                val match = regex.find(output)
                val latency = match?.groups?.get(1)?.value?.toFloat()?.toInt()
                Pair(true, latency)
            } else {
                Pair(false, null)
            }
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // support update action
        if (intent?.action == "UPDATE") {
            loadPrefs()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
