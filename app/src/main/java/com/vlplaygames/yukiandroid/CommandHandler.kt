package com.vlplaygames.yukiandroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject

/**
 * Обработчик команд, полученных от сервера или других устройств.
 * Все методы должны быть безопасны для вызова из фона (Service).
 */
object CommandHandler {

    private var appContext: Context? = null

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
                    openBrowser(url)
                    Triple(true, mapOf("opened" to url), null)
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
                    // Возвращаем текущий статус (может быть расширен)
                    Triple(true, mapOf(
                        "status" to "online",
                        "substatus" to "idle",
                        "battery" to getBatteryLevel()
                    ), null)
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

    private fun openBrowser(url: String) {
        val ctx = appContext ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
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

    // Делаем этот метод публичным для доступа из YukiClient
    fun getBatteryLevel(): Int {
        val ctx = appContext ?: return 0
        val batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    }
}