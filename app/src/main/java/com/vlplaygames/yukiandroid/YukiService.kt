package com.vlplaygames.yukiandroid

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class YukiService : Service() {

    companion object {
        private const val CHANNEL_ID = "yuki_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_CONNECT = "ACTION_CONNECT"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val ACTION_FORCE_DISCONNECT = "ACTION_FORCE_DISCONNECT"
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
                        savePreferences(serverUrl, deviceId, authToken)
                        connect()
                    }
                }
                ACTION_DISCONNECT -> {
                    disconnect()
                }
                ACTION_FORCE_DISCONNECT -> {
                    if (::client.isInitialized) {
                        client.forceDisconnect()
                        client = YukiClient("", null) // сброс
                    }
                    updateNotification("Disconnected")
                    sendStatusBroadcast(false)
                }
                "ACTION_UPDATE_SUBSTATUS" -> {
                    val substatus = it.getStringExtra("SUBSTATUS") ?: "idle"
                    if (::client.isInitialized) {
                        client.updateSubstatus(substatus)
                    }
                }
                "ACTION_SEND_TO_DEVICE" -> {
                    val target = it.getStringExtra("TARGET_DEVICE") ?: return START_STICKY
                    val command = it.getStringExtra("COMMAND") ?: return START_STICKY
                    val payloadStr = it.getStringExtra("PAYLOAD") ?: "{}"

                    if (::client.isInitialized) {
                        try {
                            val payload = JsonParser.parseString(payloadStr).asJsonObject
                            client.sendMessage(
                                YukiProtocol.deviceToDeviceMessage(
                                    deviceId, target, command,
                                    payloadToMap(payload), false
                                )
                            )
                            android.util.Log.d("YukiService", "Sent to $target: $command")
                        } catch (e: Exception) {
                            android.util.Log.e("YukiService", "Failed to send: ${e.message}")
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun payloadToMap(json: JsonObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.entrySet().forEach { entry ->
            val value = entry.value
            when {
                value.isJsonPrimitive -> {
                    val prim = value.asJsonPrimitive
                    when {
                        prim.isBoolean -> map[entry.key] = prim.asBoolean
                        prim.isNumber -> map[entry.key] = prim.asNumber
                        prim.isString -> map[entry.key] = prim.asString
                    }
                }
                value.isJsonObject -> map[entry.key] = payloadToMap(value.asJsonObject)
                value.isJsonArray -> {
                    val list = mutableListOf<Any>()
                    value.asJsonArray.forEach { element ->
                        if (element.isJsonPrimitive) {
                            list.add(element.asJsonPrimitive.asString)
                        }
                    }
                    map[entry.key] = list
                }
            }
        }
        return map
    }

    private fun connect() {
        if (::client.isInitialized) {
            client.disconnect()
        }
        client = YukiClient(deviceId, authToken)
        client.onStatusChanged = { connected ->
            val statusText = if (connected) "Connected" else "Disconnected"
            updateNotification(statusText)
            sendStatusBroadcast(connected)
        }
        client.onLog = { log ->
            android.util.Log.d("YukiService", log)
            // записываем в файловый логгер, если есть
            Logger.info(log)
        }
        client.onCommandReceived = { command, params ->
            android.util.Log.d("YukiService", "Command received: $command")
        }
        // Обработка команд от других устройств (аналог PC-клиента)
        client.onDeviceCommand = { fromDevice, command, payload ->
            android.util.Log.d("YukiService", "Device command from $fromDevice: $command")
            // Аналог switch в PC-клиенте
            when (command?.lowercase()) {
                "show_message" -> {
                    val message = payload.get("message")?.asString ?: "No message"
                    // Показываем Toast (или уведомление)
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Message from $fromDevice: $message",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                "get_status" -> {
                    // Отправляем ответ обратно (это уже делается автоматически при require_response)
                    // Но если require_response=false, мы всё равно можем ответить
                    client.sendMessage(
                        YukiProtocol.deviceToDeviceMessage(
                            deviceId, fromDevice, "status_response",
                            mapOf(
                                "status" to "online",
                                "substatus" to client.substatus,
                                "battery" to CommandHandler.getBatteryLevel()
                            ), false
                        )
                    )
                }
            }
        }
        client.onDeviceBroadcast = { command, payload ->
            android.util.Log.d("YukiService", "Broadcast: $command")
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

    private fun runOnUiThread(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(action)
        }
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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