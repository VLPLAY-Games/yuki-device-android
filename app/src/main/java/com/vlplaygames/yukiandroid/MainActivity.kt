package com.vlplaygames.yukiandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etDeviceId: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    private var isConnected = false
    private val logMessages = mutableListOf<String>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connected = intent.getBooleanExtra("connected", false)
            isConnected = connected
            updateUI(connected)
            addLog(if (connected) "Connected to server" else "Disconnected from server")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServer = findViewById(R.id.etServer)
        etDeviceId = findViewById(R.id.etDeviceId)
        etAuthToken = findViewById(R.id.etAuthToken)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

        // Загружаем сохранённые настройки
        loadPreferences()

        btnConnect.setOnClickListener {
            if (isConnected) {
                // Отключаемся
                val intent = Intent(this, YukiService::class.java)
                intent.action = YukiService.ACTION_DISCONNECT
                startService(intent)
                btnConnect.text = "Connect"
            } else {
                // Подключаемся
                val server = etServer.text.toString().trim()
                val device = etDeviceId.text.toString().trim()
                val token = etAuthToken.text.toString().trim().takeIf { it.isNotEmpty() }

                if (server.isEmpty() || device.isEmpty()) {
                    tvStatus.text = "Please fill server and device ID"
                    return@setOnClickListener
                }

                val intent = Intent(this, YukiService::class.java)
                intent.action = YukiService.ACTION_CONNECT
                intent.putExtra(YukiService.EXTRA_SERVER_URL, server)
                intent.putExtra(YukiService.EXTRA_DEVICE_ID, device)
                intent.putExtra(YukiService.EXTRA_AUTH_TOKEN, token)
                startService(intent)

                btnConnect.text = "Disconnect"
                addLog("Connecting to $server ...")
            }
        }

        // Регистрируем BroadcastReceiver для обновлений статуса
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(statusReceiver, IntentFilter("YUKI_STATUS_UPDATE"))

        // Инициализация CommandHandler (для метрик)
        CommandHandler.init(applicationContext)

        updateUI(false)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("yuki", Context.MODE_PRIVATE)
        etServer.setText(prefs.getString("server_url", "ws://192.168.1.100:8000/device") ?: "")
        etDeviceId.setText(prefs.getString("device_id", "android-${android.os.Build.MODEL}") ?: "")
        etAuthToken.setText(prefs.getString("auth_token", "") ?: "")
    }

    private fun updateUI(connected: Boolean) {
        tvStatus.text = if (connected) "Connected" else "Disconnected"
        btnConnect.text = if (connected) "Disconnect" else "Connect"
    }

    private fun addLog(msg: String) {
        logMessages.add("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(System.currentTimeMillis())} $msg")
        if (logMessages.size > 50) logMessages.removeAt(0)
        tvLog.text = logMessages.joinToString("\n")
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        super.onDestroy()
    }
}