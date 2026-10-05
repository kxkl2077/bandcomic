package moe.yzf.comic.wear.data.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 宽松 JSON 取值。
 *
 * 协议文档明确 `item_id` / `page_count` / `views` / `rate` 允许 number 或 string，
 * 因此这里不使用严格的 @Serializable 映射，改为按实际 JSON 形状取值，
 * 避免字段类型漂移导致整份响应解析失败。
 */
internal fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

private fun JsonObject?.prim(key: String): JsonPrimitive? {
    val value = this?.get(key) ?: return null
    if (value is JsonNull) return null
    return value as? JsonPrimitive
}

internal fun JsonObject?.str(key: String): String = prim(key)?.content.orEmpty()

internal fun JsonObject?.int(key: String, def: Int = 0): Int {
    val raw = prim(key)?.content?.trim().orEmpty()
    if (raw.isEmpty()) return def
    return raw.toDoubleOrNull()?.toInt() ?: def
}

internal fun JsonObject?.long(key: String, def: Long = 0L): Long {
    val raw = prim(key)?.content?.trim().orEmpty()
    if (raw.isEmpty()) return def
    return raw.toDoubleOrNull()?.toLong() ?: def
}

internal fun JsonObject?.bool(key: String, def: Boolean = false): Boolean {
    val raw = prim(key)?.content?.trim()?.lowercase().orEmpty()
    return when (raw) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> def
    }
}

internal fun JsonObject?.strList(key: String): List<String> {
    val array = this?.get(key) as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        if (element is JsonNull) null else (element as? JsonPrimitive)?.content
    }
}
