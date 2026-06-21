package com.vlplaygames.yukiandroid

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject

class YukiService : Service() {

    companion object {
        private const val CHANNEL_ID = "yuki_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_CONNECT = "ACTION_CONNECT"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val EXTRA_SERVER_URL = "SERVER_URL"
        const val EXTRA_DEVICE_ID = "DEVICE_ID"
        const val EXTRA_AUTH_TOKEN = "AUTH_TOKEN"
    }

    private lateinit var client: YukiClient
    private var serverUrl: String = ""
    private var deviceId: String = ""
    private var authToken: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))

        // Инициализация CommandHandler контекстом
        CommandHandler.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_CONNECT -> {
                    serverUrl = it.getStringExtra(EXTRA_SERVER_URL) ?: ""
                    deviceId = it.getStringExtra(EXTRA_DEVICE_ID) ?: "android-${Build.MODEL}"
                    authToken = it.getStringExtra(EXTRA_AUTH_TOKEN)

                    if (serverUrl.isNotEmpty() && deviceId.isNotEmpty()) {
                        // Сохраняем настройки
                        savePreferences(serverUrl, deviceId, authToken)
                        connect()
                    }
                }
                ACTION_DISCONNECT -> {
                    disconnect()
                }
            }
        }
        return START_STICKY
    }

    private fun connect() {
        if (::client.isInitialized) {
            client.disconnect()
        }
        client = YukiClient(deviceId, authToken)
        client.onStatusChanged = { connected ->
            val statusText = if (connected) "Connected" else "Disconnected"
            updateNotification(statusText)
            // Можно отправить broadcast для UI
            sendStatusBroadcast(connected)
        }
        client.onLog = { log ->
            // Можно логировать в файл или отправлять в UI
            android.util.Log.d("YukiService", log)
        }
        client.connect(serverUrl)
    }

    private fun disconnect() {
        if (::client.isInitialized) {
            client.disconnect()
        }
        updateNotification("Disconnected")
        sendStatusBroadcast(false)
    }

    private fun sendStatusBroadcast(connected: Boolean) {
        val intent = Intent("YUKI_STATUS_UPDATE")
        intent.putExtra("connected", connected)
        sendBroadcast(intent)
    }

    private fun savePreferences(serverUrl: String, deviceId: String, authToken: String?) {
        val prefs = getSharedPreferences("yuki", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_url", serverUrl)
            putString("device_id", deviceId)
            putString("auth_token", authToken)
            apply()
        }
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============ УВЕДОМЛЕНИЯ ============

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Yuki Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yuki Client")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}