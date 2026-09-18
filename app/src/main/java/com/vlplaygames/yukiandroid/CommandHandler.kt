package com.vlplaygames.yukiandroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.FileReader
import java.util.*

/**
 * Обработчик команд, полученных от сервера или других устройств.
 * Все методы должны быть безопасны для вызова из фона (Service).
 */
object CommandHandler {

    private var appContext: Context? = null
    private var flashLightState = false

    // Кеш для CPU - используем для расчета загрузки
    private var lastCpuTime = 0L
    private var lastIdleTime = 0L
    private var cpuUsagePercent = 0

    // Инициализация контекста (вызвать из Application или при старте)
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getAppContext(): Context? = appContext

    /**
     * Выполнить команду.
     * Возвращает тройку: success, result, error.
     * result и error могут быть null.
     */
    suspend fun execute(
        command: String,
        params: JsonObject
    ): Triple<Boolean, Any?, String?> {
        return try {
            when (command.lowercase()) {
                "open_browser", "open_url" -> {
                    val url = params.get("url")?.asString ?: "https://www.google.com"
                    if (openBrowser(url)) {
                        Triple(true, mapOf("opened" to url), null)
                    } else {
                        Triple(false, null, "Refused to open non-http(s) URL")
                    }
                }

                "show_notification" -> {
                    val title = params.get("title")?.asString ?: "Yuki"
                    val body = params.get("body")?.asString ?: ""
                    showNotification(title, body)
                    Triple(true, null, null)
                }

                "set_volume" -> {
                    val level = params.get("level")?.asInt ?: 0
                    val clamped = level.coerceIn(0, 100)
                    setVolume(clamped)
                    Triple(true, mapOf("volume" to clamped), null)
                }

                "volume_up" -> {
                    val newVol = adjustVolume(+5)
                    Triple(true, mapOf("volume" to newVol), null)
                }

                "volume_down" -> {
                    val newVol = adjustVolume(-5)
                    Triple(true, mapOf("volume" to newVol), null)
                }

                "get_status" -> {
                    val systemInfo = getSystemInfo()
                    Triple(true, systemInfo, null)
                }

                // ===== НОВЫЕ КОМАНДЫ =====
                "get_battery" -> {
                    val battery = getBatteryLevel()
                    val isCharging = isCharging()
                    Triple(true, mapOf(
                        "battery" to battery,
                        "charging" to isCharging,
                        "unit" to "percent"
                    ), null)
                }

                "get_brightness" -> {
                    val brightness = getBrightness()
                    Triple(true, mapOf(
                        "brightness" to brightness,
                        "max" to 255,
                        "unit" to "0-255"
                    ), null)
                }

                "set_flashlight" -> {
                    val on = params.get("on")?.asBoolean ?: true
                    val success = setFlashlight(on)
                    if (success) {
                        flashLightState = on
                        Triple(true, mapOf(
                            "flashlight" to on,
                            "state" to if (on) "on" else "off"
                        ), null)
                    } else {
                        Triple(false, null, "Failed to set flashlight")
                    }
                }

                "toggle_flashlight" -> {
                    val newState = !flashLightState
                    val success = setFlashlight(newState)
                    if (success) {
                        flashLightState = newState
                        Triple(true, mapOf(
                            "flashlight" to newState,
                            "state" to if (newState) "on" else "off"
                        ), null)
                    } else {
                        Triple(false, null, "Failed to toggle flashlight")
                    }
                }

                else -> {
                    Triple(false, null, "Unknown command: $command")
                }
            }
        } catch (e: Exception) {
            Triple(false, null, e.message)
        }
    }

    // ============ РЕАЛИЗАЦИЯ КОМАНД ============

    /**
     * Возвращает false (и ничего не запускает) для любой схемы, кроме http/https, чтобы сервер
     * не мог подсунуть intent:// или другую custom-схему для атаки на сторонние приложения.
     */
    private fun openBrowser(url: String): Boolean {
        val ctx = appContext ?: return false
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to parse URL: ${e.message}")
            return false
        }
        val scheme = uri.scheme
        if (scheme == null || !(scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true))) {
            Log.w("CommandHandler", "Rejected open_browser for disallowed scheme: $scheme")
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }

    private fun showNotification(title: String, body: String) {
        val ctx = appContext ?: return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "yuki_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Yuki Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    private fun setVolume(level: Int) {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (level * max / 100).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun adjustVolume(delta: Int): Int {
        val ctx = appContext ?: return 0
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        var current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        current = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, current, 0)
        return (current * 100 / max)
    }

    // ===== МЕТРИКИ (PUBLIC METHODS) =====

    /**
     * Получить полную информацию о системе (для статуса)
     */
    fun getSystemInfo(): Map<String, Any> {
        return mapOf(
            "status" to "online",
            "substatus" to "idle",
            "battery" to getBatteryLevel(),
            "battery_charging" to isCharging(),
            "cpu" to getCpuUsage(),
            "memory" to getMemoryUsage(),
            "memory_percent" to getMemoryPercent()
        )
    }

    fun getBatteryLevel(): Int {
        val ctx = appContext ?: return 0
        val batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    }

    fun isCharging(): Boolean {
        val ctx = appContext ?: return false
        val batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return batteryManager?.isCharging ?: false
    }

    /**
     * Получить загрузку CPU в процентах (0-100)
     */
    fun getCpuUsage(): Int {
        try {
            // Читаем /proc/stat для получения общей и idle загрузки CPU
            val reader = BufferedReader(FileReader("/proc/stat"))
            val line = reader.readLine()
            reader.close()

            if (line != null && line.startsWith("cpu ")) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 8) {
                    // user, nice, system, idle, iowait, irq, softirq, steal
                    val user = parts[1].toLong()
                    val nice = parts[2].toLong()
                    val system = parts[3].toLong()
                    val idle = parts[4].toLong()
                    val iowait = parts[5].toLong()
                    val irq = parts[6].toLong()
                    val softirq = parts[7].toLong()
                    val steal = if (parts.size > 8) parts[8].toLong() else 0

                    val total = user + nice + system + idle + iowait + irq + softirq + steal
                    val idleTime = idle + iowait

                    if (lastCpuTime > 0) {
                        val totalDiff = total - lastCpuTime
                        val idleDiff = idleTime - lastIdleTime

                        if (totalDiff > 0) {
                            cpuUsagePercent = ((totalDiff - idleDiff) * 100 / totalDiff).toInt()
                                .coerceIn(0, 100)
                        }
                    }

                    lastCpuTime = total
                    lastIdleTime = idleTime
                    return cpuUsagePercent
                }
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to get CPU usage: ${e.message}")
        }
        return 0
    }

    /**
     * Получить использование памяти в байтах
     */
    fun getMemoryUsage(): Long {
        val ctx = appContext ?: return 0
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem - mi.availMem
    }

    /**
     * Получить процент использования памяти (0-100)
     */
    fun getMemoryPercent(): Int {
        val ctx = appContext ?: return 0
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem
        val avail = mi.availMem
        return ((total - avail) * 100 / total).toInt().coerceIn(0, 100)
    }

    /**
     * Получить текущую яркость экрана (0-255)
     */
    fun getBrightness(): Int {
        val ctx = appContext ?: return 0
        return try {
            Settings.System.getInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to get brightness: ${e.message}")
            0
        }
    }

    /**
     * Управление фонариком (вспышкой камеры)
     */
    private fun setFlashlight(on: Boolean): Boolean {
        val ctx = appContext ?: return false
        return try {
            val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, on)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to set flashlight: ${e.message}")
            false
        }
    }
}