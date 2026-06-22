package com.vlplaygames.yukiandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etDeviceId: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var chkShowToken: CheckBox
    private lateinit var btnConnect: Button
    private lateinit var btnOpenPanel: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvLogsLabel: TextView
    private lateinit var btnToggleFeatures: Button
    private lateinit var btnToggleLogs: Button
    private lateinit var btnToggleTheme: Button
    private lateinit var btnOpenLogs: Button
    private lateinit var groupCapabilities: LinearLayout
    private lateinit var spinnerSubstatus: Spinner
    private lateinit var btnUpdateStatus: Button
    private lateinit var etTargetDevice: EditText
    private lateinit var etCustomCommand: EditText
    private lateinit var etPayload: EditText
    private lateinit var btnSendToDevice: Button

    private var isConnected = false
    private var featuresVisible = false
    private var logsVisible = false
    private var isDarkTheme = true
    private val logMessages = mutableListOf<String>()
    private var capabilities = mutableListOf<String>()

    private val substatuses = arrayOf("idle", "working", "sleeping", "charging", "error", "updating", "maintenance")

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connected = intent.getBooleanExtra("connected", false)
            isConnected = connected
            runOnUiThread {
                updateUI(connected)
                addLog(if (connected) "Connected to server" else "Disconnected from server")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadPreferences()
        setupListeners()
        setupSubstatusSpinner()
        applyTheme()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(statusReceiver, IntentFilter("YUKI_STATUS_UPDATE"))

        CommandHandler.init(applicationContext)
        updateUI(false)
    }

    private fun initViews() {
        etServer = findViewById(R.id.etServer)
        etDeviceId = findViewById(R.id.etDeviceId)
        etAuthToken = findViewById(R.id.etAuthToken)
        chkShowToken = findViewById(R.id.chkShowToken)
        btnConnect = findViewById(R.id.btnConnect)
        btnOpenPanel = findViewById(R.id.btnOpenPanel)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        tvLogsLabel = findViewById(R.id.tvLogsLabel)
        btnToggleFeatures = findViewById(R.id.btnToggleFeatures)
        btnToggleLogs = findViewById(R.id.btnToggleLogs)
        btnToggleTheme = findViewById(R.id.btnToggleTheme)
        btnOpenLogs = findViewById(R.id.btnOpenLogs)
        groupCapabilities = findViewById(R.id.groupCapabilities)
        spinnerSubstatus = findViewById(R.id.spinnerSubstatus)
        btnUpdateStatus = findViewById(R.id.btnUpdateStatus)
        etTargetDevice = findViewById(R.id.etTargetDevice)
        etCustomCommand = findViewById(R.id.etCustomCommand)
        etPayload = findViewById(R.id.etPayload)
        btnSendToDevice = findViewById(R.id.btnSendToDevice)
    }

    private fun setupListeners() {
        chkShowToken.setOnCheckedChangeListener { _, isChecked ->
            etAuthToken.inputType = if (isChecked) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etAuthToken.setSelection(etAuthToken.text.length)
        }

        btnConnect.setOnClickListener {
            if (isConnected) {
                val intent = Intent(this, YukiService::class.java)
                intent.action = YukiService.ACTION_DISCONNECT
                startService(intent)
                btnConnect.text = "Connect"
            } else {
                val server = etServer.text.toString().trim()
                val device = etDeviceId.text.toString().trim()
                val token = etAuthToken.text.toString().trim().takeIf { it.isNotEmpty() }

                if (server.isEmpty() || device.isEmpty()) {
                    tvStatus.text = "Please fill server and device ID"
                    return@setOnClickListener
                }

                // Собираем capabilities
                capabilities.clear()
                if (findViewById<CheckBox>(R.id.chkOpenBrowser).isChecked) capabilities.add("open_browser")
                if (findViewById<CheckBox>(R.id.chkShowNotification).isChecked) capabilities.add("show_notification")
                if (findViewById<CheckBox>(R.id.chkSetVolume).isChecked) capabilities.add("set_volume")
                if (findViewById<CheckBox>(R.id.chkVolumeUp).isChecked) capabilities.add("volume_up")
                if (findViewById<CheckBox>(R.id.chkVolumeDown).isChecked) capabilities.add("volume_down")
                if (findViewById<CheckBox>(R.id.chkGetStatus).isChecked) capabilities.add("get_status")
                // НОВЫЕ CAPABILITIES
                if (findViewById<CheckBox>(R.id.chkGetBattery).isChecked) capabilities.add("get_battery")
                if (findViewById<CheckBox>(R.id.chkGetBrightness).isChecked) capabilities.add("get_brightness")
                if (findViewById<CheckBox>(R.id.chkSetFlashlight).isChecked) capabilities.add("set_flashlight")
                if (findViewById<CheckBox>(R.id.chkToggleFlashlight).isChecked) capabilities.add("toggle_flashlight")

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

        // Force disconnect by long press
        btnConnect.setOnLongClickListener {
            if (isConnected) {
                val intent = Intent(this, YukiService::class.java)
                intent.action = YukiService.ACTION_FORCE_DISCONNECT
                startService(intent)
                addLog("Force disconnect")
                Toast.makeText(this, "Force disconnect", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        btnOpenPanel.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val httpUrl = etServer.text.toString()
                .replace("ws://", "http://")
                .replace("wss://", "https://")
                .replace(":8000", ":5000")

            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(httpUrl))
            startActivity(intent)
            addLog("Opened control panel: $httpUrl")
        }

        btnToggleFeatures.setOnClickListener {
            featuresVisible = !featuresVisible
            btnToggleFeatures.text = if (featuresVisible) "Hide features" else "Show features"
            groupCapabilities.visibility = if (featuresVisible) android.view.View.VISIBLE else android.view.View.GONE
            savePreferences()
        }

        btnToggleLogs.setOnClickListener {
            logsVisible = !logsVisible
            btnToggleLogs.text = if (logsVisible) "Hide logs" else "Show logs"
            tvLog.visibility = if (logsVisible) android.view.View.VISIBLE else android.view.View.GONE
            tvLogsLabel.visibility = if (logsVisible) android.view.View.VISIBLE else android.view.View.GONE
            savePreferences()
        }

        btnToggleTheme.setOnClickListener {
            isDarkTheme = !isDarkTheme
            btnToggleTheme.text = if (isDarkTheme) "🌙 Dark" else "☀️ Light"
            applyTheme()
            savePreferences()
        }

        btnOpenLogs.setOnClickListener {
            Toast.makeText(this, "Logs are stored in app's internal storage", Toast.LENGTH_SHORT).show()
        }

        btnUpdateStatus.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val substatus = spinnerSubstatus.selectedItem.toString()
            val intent = Intent(this, YukiService::class.java)
            intent.action = "ACTION_UPDATE_SUBSTATUS"
            intent.putExtra("SUBSTATUS", substatus)
            startService(intent)
            addLog("Extended status updated to: $substatus")
        }

        btnSendToDevice.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val target = etTargetDevice.text.toString().trim()
            val command = etCustomCommand.text.toString().trim()
            val payload = etPayload.text.toString().trim()

            if (target.isEmpty() || command.isEmpty()) {
                Toast.makeText(this, "Target device and command are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, YukiService::class.java)
            intent.action = "ACTION_SEND_TO_DEVICE"
            intent.putExtra("TARGET_DEVICE", target)
            intent.putExtra("COMMAND", command)
            intent.putExtra("PAYLOAD", payload)
            startService(intent)
            addLog("Sent to $target: $command")
        }
    }

    private fun setupSubstatusSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, substatuses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSubstatus.adapter = adapter
        spinnerSubstatus.setSelection(0)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("yuki", Context.MODE_PRIVATE)

        etServer.setText(prefs.getString("server_url", "ws://192.168.1.100:8000/device") ?: "")
        etDeviceId.setText(prefs.getString("device_id", "android-${Build.MODEL}") ?: "")
        etAuthToken.setText(prefs.getString("auth_token", "") ?: "")

        val caps = prefs.getStringSet("capabilities", setOf(
            "open_browser", "show_notification", "set_volume",
            "volume_up", "volume_down", "get_status",
            "get_battery", "get_brightness", "set_flashlight", "toggle_flashlight"
        )) ?: emptySet()

        findViewById<CheckBox>(R.id.chkOpenBrowser).isChecked = "open_browser" in caps
        findViewById<CheckBox>(R.id.chkShowNotification).isChecked = "show_notification" in caps
        findViewById<CheckBox>(R.id.chkSetVolume).isChecked = "set_volume" in caps
        findViewById<CheckBox>(R.id.chkVolumeUp).isChecked = "volume_up" in caps
        findViewById<CheckBox>(R.id.chkVolumeDown).isChecked = "volume_down" in caps
        findViewById<CheckBox>(R.id.chkGetStatus).isChecked = "get_status" in caps
        // НОВЫЕ CAPABILITIES
        findViewById<CheckBox>(R.id.chkGetBattery).isChecked = "get_battery" in caps
        findViewById<CheckBox>(R.id.chkGetBrightness).isChecked = "get_brightness" in caps
        findViewById<CheckBox>(R.id.chkSetFlashlight).isChecked = "set_flashlight" in caps
        findViewById<CheckBox>(R.id.chkToggleFlashlight).isChecked = "toggle_flashlight" in caps

        featuresVisible = prefs.getBoolean("features_visible", false)
        logsVisible = prefs.getBoolean("logs_visible", false)
        isDarkTheme = prefs.getBoolean("dark_theme", true)

        btnToggleFeatures.text = if (featuresVisible) "Hide features" else "Show features"
        groupCapabilities.visibility = if (featuresVisible) android.view.View.VISIBLE else android.view.View.GONE

        btnToggleLogs.text = if (logsVisible) "Hide logs" else "Show logs"
        tvLog.visibility = if (logsVisible) android.view.View.VISIBLE else android.view.View.GONE
        tvLogsLabel.visibility = if (logsVisible) android.view.View.VISIBLE else android.view.View.GONE

        btnToggleTheme.text = if (isDarkTheme) "🌙 Dark" else "☀️ Light"

        val substatus = prefs.getString("substatus", "idle")
        val idx = substatuses.indexOf(substatus)
        if (idx >= 0) spinnerSubstatus.setSelection(idx)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("yuki", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_url", etServer.text.toString())
            putString("device_id", etDeviceId.text.toString())
            putString("auth_token", etAuthToken.text.toString())

            val caps = mutableSetOf<String>()
            if (findViewById<CheckBox>(R.id.chkOpenBrowser).isChecked) caps.add("open_browser")
            if (findViewById<CheckBox>(R.id.chkShowNotification).isChecked) caps.add("show_notification")
            if (findViewById<CheckBox>(R.id.chkSetVolume).isChecked) caps.add("set_volume")
            if (findViewById<CheckBox>(R.id.chkVolumeUp).isChecked) caps.add("volume_up")
            if (findViewById<CheckBox>(R.id.chkVolumeDown).isChecked) caps.add("volume_down")
            if (findViewById<CheckBox>(R.id.chkGetStatus).isChecked) caps.add("get_status")
            // НОВЫЕ CAPABILITIES
            if (findViewById<CheckBox>(R.id.chkGetBattery).isChecked) caps.add("get_battery")
            if (findViewById<CheckBox>(R.id.chkGetBrightness).isChecked) caps.add("get_brightness")
            if (findViewById<CheckBox>(R.id.chkSetFlashlight).isChecked) caps.add("set_flashlight")
            if (findViewById<CheckBox>(R.id.chkToggleFlashlight).isChecked) caps.add("toggle_flashlight")
            putStringSet("capabilities", caps)

            putBoolean("features_visible", featuresVisible)
            putBoolean("logs_visible", logsVisible)
            putBoolean("dark_theme", isDarkTheme)
            putString("substatus", spinnerSubstatus.selectedItem.toString())

            apply()
        }
    }

    private fun applyTheme() {
        val rootView = findViewById<android.view.View>(android.R.id.content)

        if (isDarkTheme) {
            rootView.setBackgroundColor(0xFF1a1a1a.toInt())
            tvLog.setBackgroundColor(0xFF0d0d0d.toInt())
            tvLog.setTextColor(0xFFcccccc.toInt())
        } else {
            rootView.setBackgroundColor(0xFFf5f5f5.toInt())
            tvLog.setBackgroundColor(0xFFffffff.toInt())
            tvLog.setTextColor(0xFF000000.toInt())
        }
    }

    private fun updateUI(connected: Boolean) {
        tvStatus.text = if (connected) "Connected" else "Offline"
        tvStatus.setTextColor(if (connected) 0xFF00cc00.toInt() else 0xFFFF0000.toInt())
        btnConnect.text = if (connected) "Disconnect" else "Connect"
        btnOpenPanel.isEnabled = connected
        btnSendToDevice.isEnabled = connected
        btnUpdateStatus.isEnabled = connected
    }

    private fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(System.currentTimeMillis())
        logMessages.add("[$time] $msg")
        if (logMessages.size > 100) logMessages.removeAt(0)
        tvLog.text = logMessages.joinToString("\n")
        tvLog.post { tvLog.scrollTo(0, tvLog.bottom) }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        savePreferences()
        super.onDestroy()
    }
}