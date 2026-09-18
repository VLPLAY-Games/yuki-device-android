package com.vlplaygames.yukiandroid

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Базовое сообщение протокола Yuki v1.0
 * Полностью соответствует Python-классу YukiMessage
 */
data class YukiMessage(
    @SerializedName("protocol")
    val protocol: String = "yuki/1.0",

    @SerializedName("type")
    val type: String,

    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis() / 1000,

    @SerializedName("payload")
    val payload: JsonObject = JsonObject()
)