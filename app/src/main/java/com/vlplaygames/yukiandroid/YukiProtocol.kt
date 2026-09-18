package com.vlplaygames.yukiandroid

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonArray

/**
 * Фабрика сообщений Yuki Protocol v1.0
 * Все методы возвращают YukiMessage, готовый к отправке.
 * Поля именуются в snake_case через @SerializedName в YukiMessage.
 */
object YukiProtocol {

    // ============ УПРАВЛЕНИЕ ПОДКЛЮЧЕНИЕМ ============

    fun helloMessage(
        deviceId: String,
        deviceType: String = "android",
        capabilities: List<String> = emptyList(),
        metadata: Map<String, Any>? = null,
        authToken: String? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("device_type", deviceType)
            add("capabilities", capabilities.toJsonArray())
            metadata?.let { add("metadata", it.toJsonObject()) }
            authToken?.let { addProperty("auth_token", it) }
        }
        return YukiMessage(type = "hello", payload = payload)
    }

    fun welcomeMessage(
        sessionId: String,
        serverTime: Long,
        heartbeatInterval: Int = 30
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("session_id", sessionId)
            addProperty("server_time", serverTime)
            addProperty("heartbeat_interval", heartbeatInterval)
        }
        return YukiMessage(type = "welcome", payload = payload)
    }

    // ============ СТАТУСЫ ============

    fun statusMessage(
        deviceId: String,
        status: String,
        details: Map<String, Any>? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("status", status)
            details?.let { add("details", it.toJsonObject()) }
        }
        return YukiMessage(type = "status", payload = payload)
    }

    fun extendedStatusMessage(
        deviceId: String,
        status: String? = null,
        substatus: String? = null,
        details: Map<String, Any>? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            status?.let { addProperty("status", it) }
            substatus?.let { addProperty("substatus", it) }
            details?.let { add("details", it.toJsonObject()) }
        }
        return YukiMessage(type = "extended_status", payload = payload)
    }

    // ============ КОМАНДЫ ============

    fun commandMessage(
        deviceId: String,
        command: String,
        params: Map<String, Any>? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("command", command)
            add("params", (params ?: emptyMap()).toJsonObject())
        }
        return YukiMessage(type = "command", payload = payload)
    }

    fun commandResultMessage(
        originalId: String,
        success: Boolean,
        result: Any? = null,
        error: String? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("success", success)
            result?.let { add("result", it.toJsonElement()) }
            error?.let { addProperty("error", it) }
        }
        val msg = YukiMessage(type = "command_result", payload = payload)
        // Переопределяем id, чтобы он совпал с оригинальным
        return msg.copy(id = originalId)
    }

    fun confirmCommandMessage(
        deviceId: String,
        command: String,
        params: Map<String, Any>? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("command", command)
            add("params", (params ?: emptyMap()).toJsonObject())
        }
        return YukiMessage(type = "confirm_command", payload = payload)
    }

    fun confirmResponseMessage(
        originalId: String,
        approved: Boolean
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("approved", approved)
        }
        val msg = YukiMessage(type = "confirm_response", payload = payload)
        return msg.copy(id = originalId)
    }

    // ============ УПРАВЛЕНИЕ УСТРОЙСТВАМИ ============

    fun devicesUpdateMessage(
        devices: Map<String, Map<String, Any>>,
        removed: List<String>? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            add("devices", devices.toJsonObject())
            removed?.let { add("removed", it.toJsonArray()) }
        }
        return YukiMessage(type = "devices_update", payload = payload)
    }

    fun deviceAuthRequestMessage(
        deviceId: String,
        deviceType: String,
        capabilities: List<String>
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("device_type", deviceType)
            add("capabilities", capabilities.toJsonArray())
        }
        return YukiMessage(type = "device_auth_request", payload = payload)
    }

    fun deviceAuthResponseMessage(
        requestId: String,
        approved: Boolean
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("approved", approved)
        }
        val msg = YukiMessage(type = "device_auth_response", payload = payload)
        return msg.copy(id = requestId)
    }

    // ============ ТОКЕНЫ ============

    fun tokenUpdateMessage(
        newToken: String,
        reason: String = "admin"
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("new_token", newToken)
            addProperty("reason", reason)
        }
        return YukiMessage(type = "token_update", payload = payload)
    }

    fun tokenInfoMessage(
        createdAt: Long?,
        rotationIntervalHours: Int,
        expiresIn: Long? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            createdAt?.let { addProperty("created_at", it) }
            addProperty("rotation_interval_hours", rotationIntervalHours)
            expiresIn?.let { addProperty("expires_in", it) }
        }
        return YukiMessage(type = "token_info", payload = payload)
    }

    fun tokenRotatedMessage(
        success: Boolean,
        newToken: String? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("success", success)
            newToken?.let { addProperty("new_token", it) }
        }
        return YukiMessage(type = "token_rotated", payload = payload)
    }

    fun rotateTokenRequestMessage(): YukiMessage {
        return YukiMessage(type = "rotate_token", payload = JsonObject())
    }

    fun getTokenInfoRequestMessage(): YukiMessage {
        return YukiMessage(type = "get_token_info", payload = JsonObject())
    }

    // ============ МЕТРИКИ ============

    fun metricsMessage(
        deviceId: String,
        metrics: Map<String, Any>
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            add("metrics", metrics.toJsonObject())
            addProperty("timestamp", System.currentTimeMillis() / 1000)
        }
        return YukiMessage(type = "metrics", payload = payload)
    }

    fun metricsRequestMessage(
        deviceId: String,
        metricTypes: List<String>? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            add("metric_types", (metricTypes ?: emptyList()).toJsonArray())
        }
        return YukiMessage(type = "metrics_request", payload = payload)
    }

    // ============ СВЯЗЬ УСТРОЙСТВО–УСТРОЙСТВО ============

    fun deviceToDeviceMessage(
        fromDeviceId: String,
        toDeviceId: String,
        command: String,
        payload: Map<String, Any>? = null,
        requireResponse: Boolean = false
    ): YukiMessage {
        val payloadObj = JsonObject().apply {
            addProperty("from_device_id", fromDeviceId)
            addProperty("to_device_id", toDeviceId)
            addProperty("command", command)
            add("payload", (payload ?: emptyMap()).toJsonObject())
            addProperty("require_response", requireResponse)
            addProperty("sent_at", System.currentTimeMillis() / 1000)
        }
        return YukiMessage(type = "device_to_device", payload = payloadObj)
    }

    fun deviceResponseMessage(
        originalId: String,
        fromDeviceId: String,
        toDeviceId: String,
        success: Boolean,
        result: Any? = null,
        error: String? = null
    ): YukiMessage {
        val payload = JsonObject().apply {
            addProperty("from_device_id", fromDeviceId)
            addProperty("to_device_id", toDeviceId)
            addProperty("success", success)
            result?.let { add("result", it.toJsonElement()) }
            error?.let { addProperty("error", error) }
        }
        val msg = YukiMessage(type = "device_response", payload = payload)
        return msg.copy(id = originalId)
    }

    fun deviceBroadcastMessage(
        fromDeviceId: String,
        command: String,
        payload: Map<String, Any>? = null,
        deviceFilter: List<String>? = null
    ): YukiMessage {
        val payloadObj = JsonObject().apply {
            addProperty("from_device_id", fromDeviceId)
            addProperty("command", command)
            add("payload", (payload ?: emptyMap()).toJsonObject())
            deviceFilter?.let { add("device_filter", it.toJsonArray()) }
            addProperty("sent_at", System.currentTimeMillis() / 1000)
        }
        return YukiMessage(type = "device_broadcast", payload = payloadObj)
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ РАСШИРЕНИЯ ДЛЯ ПРЕОБРАЗОВАНИЯ ============

    private fun List<String>.toJsonArray(): JsonArray {
        val arr = JsonArray()
        this.forEach { arr.add(JsonPrimitive(it)) }
        return arr
    }

    private fun Map<String, Any>.toJsonObject(): JsonObject {
        val obj = JsonObject()
        this.forEach { (key, value) ->
            when (value) {
                is String -> obj.addProperty(key, value)
                is Number -> obj.addProperty(key, value)
                is Boolean -> obj.addProperty(key, value)
                is List<*> -> obj.add(key, value.map { it.toString() }.toJsonArray())
                is Map<*, *> -> obj.add(key, (value as Map<String, Any>).toJsonObject())
                else -> obj.addProperty(key, value.toString())
            }
        }
        return obj
    }

    private fun Any.toJsonElement(): com.google.gson.JsonElement {
        return when (this) {
            is String -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is List<*> -> this.map { it.toString() }.toJsonArray()
            is Map<*, *> -> (this as Map<String, Any>).toJsonObject()
            else -> JsonPrimitive(this.toString())
        }
    }
}