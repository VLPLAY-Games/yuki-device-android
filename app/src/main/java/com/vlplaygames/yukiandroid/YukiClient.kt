package com.vlplaygames.yukiandroid

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class YukiClient(
    private val deviceId: String,
    private val authToken: String? = null,
    private val capabilities: List<String> = listOf(
        "open_browser", "show_notification", "set_volume",
        "volume_up", "volume_down", "get_status",
        "get_battery", "get_brightness", "set_flashlight", "toggle_flashlight"
    )
) {
    companion object {
        private const val TAG = "YukiClient"
    }

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isAuthorized = false
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    // Source of truth for what the server is allowed to invoke; mirrors YukiClient.cs's _enabledCapabilities check.
    private val enabledCapabilities: Set<String> = capabilities.map { it.lowercase() }.toSet()

    private fun isCapabilityEnabled(command: String): Boolean = enabledCapabilities.contains(command.lowercase())

    var substatus: String = "idle"

    var onStatusChanged: ((Boolean) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    var onCommandReceived: ((String, JsonObject) -> Unit)? = null
    var onDeviceCommand: ((String, String, JsonObject) -> Unit)? = null
    var onDeviceBroadcast: ((String, JsonObject) -> Unit)? = null

    private var serverUrl: String = ""

    fun connect(serverUrl: String) {
        this.serverUrl = serverUrl
        // Автоматически добавляем /device, если его нет
        val fullUrl = if (serverUrl.endsWith("/device")) serverUrl else "$serverUrl/device"

        if (isConnected) {
            onLog?.invoke("Already connected")
            return
        }

        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(fullUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isAuthorized = false
                onStatusChanged?.invoke(true)
                onLog?.invoke("WebSocket opened")
                sendHello()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                onStatusChanged?.invoke(false)
                onLog?.invoke("Closed: $reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                onStatusChanged?.invoke(false)
                onLog?.invoke("Error: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        scope.coroutineContext.cancelChildren()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected = false
        isAuthorized = false
        onStatusChanged?.invoke(false)
        onLog?.invoke("Disconnected")
    }

    fun forceDisconnect() {
        reconnectJob?.cancel()
        scope.coroutineContext.cancelChildren()
        webSocket?.cancel()
        webSocket = null
        isConnected = false
        isAuthorized = false
        onStatusChanged?.invoke(false)
        onLog?.invoke("Force disconnected")
    }

    private fun sendHello() {
        val hello = YukiProtocol.helloMessage(
            deviceId = deviceId,
            deviceType = "android",
            capabilities = capabilities,
            authToken = authToken
        )
        onLog?.invoke("Sending hello...")
        sendMessage(hello)
    }

    fun sendMessage(msg: YukiMessage) {
        webSocket?.send(gson.toJson(msg)) ?: run {
            onLog?.invoke("Cannot send: WebSocket is null")
        }
    }

    private fun handleMessage(text: String) {
        try {
            onLog?.invoke("Received: $text")
            val jsonElement = JsonParser.parseString(text)
            val obj = jsonElement.asJsonObject
            val type = obj.get("type")?.asString ?: return
            val id = obj.get("id")?.asString ?: ""
            val payload = obj.get("payload")?.asJsonObject ?: JsonObject()

            when (type) {
                "welcome" -> {
                    onLog?.invoke("Welcome received")
                    isAuthorized = true
                    onStatusChanged?.invoke(true)
                    sendMessage(YukiProtocol.statusMessage(deviceId, "online"))
                    sendExtendedStatus()
                    onLog?.invoke("Status and extended status sent")
                    startPeriodicTasks()
                }

                "device_auth_response" -> {
                    val approved = payload.get("approved")?.asBoolean ?: false
                    onLog?.invoke("Auth response: approved=$approved")
                    if (approved) {
                        isAuthorized = true
                        onLog?.invoke("Device authorized, waiting for welcome...")
                    } else {
                        onLog?.invoke("Device authorization denied")
                        disconnect()
                    }
                }

                "command" -> {
                    val command = payload.get("command")?.asString ?: return
                    val params = payload.get("params")?.asJsonObject ?: JsonObject()
                    onCommandReceived?.invoke(command, params)
                    executeCommand(id, command, params)
                }

                "device_command" -> {
                    val from = payload.get("from_device_id")?.asString ?: return
                    val cmd = payload.get("command")?.asString ?: return
                    val cmdPayload = payload.get("payload")?.asJsonObject ?: JsonObject()
                    val requireResponse = payload.get("require_response")?.asBoolean ?: false

                    onDeviceCommand?.invoke(from, cmd, cmdPayload)

                    if (!isCapabilityEnabled(cmd)) {
                        onLog?.invoke("Device command '$cmd' from $from rejected: disabled by user")
                        if (requireResponse) {
                            val responseMsg = YukiProtocol.deviceResponseMessage(
                                id, deviceId, from, false, null, "Command disabled by user"
                            )
                            sendMessage(responseMsg)
                        }
                        return
                    }

                    if (requireResponse) {
                        scope.launch {
                            val (success, result, error) = CommandHandler.execute(cmd, cmdPayload)
                            val responseMsg = YukiProtocol.deviceResponseMessage(
                                id, deviceId, from, success, result, error
                            )
                            sendMessage(responseMsg)
                        }
                    }
                }

                "device_broadcast" -> {
                    val cmd = payload.get("command")?.asString ?: return
                    val broadcastPayload = payload.get("payload")?.asJsonObject ?: JsonObject()
                    onDeviceBroadcast?.invoke(cmd, broadcastPayload)
                }

                "metrics_request" -> {
                    sendMetrics()
                }

                "ping" -> {
                    val pong = YukiMessage(type = "pong", id = id)
                    sendMessage(pong)
                }

                "token_update" -> {
                    val newToken = payload.get("new_token")?.asString
                    newToken?.let {
                        onLog?.invoke("Token updated: $it")
                    }
                }

                "disconnect" -> {
                    onLog?.invoke("Server requested disconnect")
                    disconnect()
                }

                else -> {
                    onLog?.invoke("Unhandled message type: $type")
                }
            }
        } catch (e: Exception) {
            onLog?.invoke("Error parsing message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun executeCommand(cmdId: String, command: String, params: JsonObject) {
        if (!isCapabilityEnabled(command)) {
            onLog?.invoke("Command '$command' rejected: disabled by user")
            val response = YukiProtocol.commandResultMessage(cmdId, false, null, "Command disabled by user")
            sendMessage(response)
            return
        }
        scope.launch {
            val (success, result, error) = CommandHandler.execute(command, params)
            val response = YukiProtocol.commandResultMessage(cmdId, success, result, error)
            sendMessage(response)
        }
    }

    private fun startPeriodicTasks() {
        scope.launch {
            while (isConnected && isAuthorized) {
                delay(30_000)
                if (isConnected && isAuthorized) {
                    sendMessage(YukiProtocol.statusMessage(deviceId, "online"))
                    sendExtendedStatus()
                }
                delay(30_000)
                if (isConnected && isAuthorized) {
                    sendMetrics()
                }
            }
        }
    }

    /**
     * Отправить расширенный статус с информацией о CPU, RAM и батарее
     */
    private fun sendExtendedStatus() {
        val systemInfo = CommandHandler.getSystemInfo()
        val details = mutableMapOf<String, Any>(
            "battery" to CommandHandler.getBatteryLevel(),
            "battery_charging" to CommandHandler.isCharging(),
            "memory_percent" to CommandHandler.getMemoryPercent(),
            "memory_usage" to CommandHandler.getMemoryUsage(),
            "cpu" to CommandHandler.getCpuUsage()
        )

        val msg = YukiProtocol.extendedStatusMessage(
            deviceId = deviceId,
            status = "online",
            substatus = substatus,
            details = details
        )
        sendMessage(msg)
        onLog?.invoke("Extended status sent: cpu=${details["cpu"]}%, memory=${details["memory_percent"]}%, battery=${details["battery"]}%")
    }

    private fun sendMetrics() {
        val metrics = collectSystemMetrics()
        val msg = YukiProtocol.metricsMessage(deviceId, metrics)
        sendMessage(msg)
    }

    private fun collectSystemMetrics(): Map<String, Any> {
        return mapOf(
            "battery" to CommandHandler.getBatteryLevel(),
            "battery_charging" to CommandHandler.isCharging(),
            "cpu" to CommandHandler.getCpuUsage(),
            "memory_percent" to CommandHandler.getMemoryPercent(),
            "memory_usage" to CommandHandler.getMemoryUsage(),
            "timestamp" to System.currentTimeMillis() / 1000
        )
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(5_000)
            if (!isConnected && serverUrl.isNotEmpty()) {
                onLog?.invoke("Reconnecting...")
                connect(serverUrl)
            }
        }
    }

    fun updateSubstatus(newSubstatus: String) {
        substatus = newSubstatus
        if (isConnected && isAuthorized) {
            sendExtendedStatus()
        }
    }
}