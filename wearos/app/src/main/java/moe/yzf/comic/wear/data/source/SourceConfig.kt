package moe.yzf.comic.wear.data.source

import kotlinx.serialization.json.JsonElement
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.net.asObj
import moe.yzf.comic.wear.data.net.str

/** `/config` 中不允许作为源 key 的保留键（协议文档 SOURCE_SYNC 规则）。 */
private val RESERVED_KEYS = setOf("using", "type")

/** 单条 `/config` 条目的校验结果；[source] 为 null 时 [reason] 说明不可用原因。 */
data class ConfigEntry(
    val source: ComicSource?,
    val key: String,
    val reason: String?,
)

/**
 * 解析 `GET {api}/config` 的返回值。
 *
 * 一个 `/config` 可返回多个源，逐条校验；非法条目保留原因但不参与写入，
 * 对齐快应用版「非法条目显示字段原因且不可勾选」的行为。
 */
fun parseSourceConfig(root: JsonElement): List<ConfigEntry> {
    val obj = root.asObj() ?: return emptyList()
    return obj.entries.map { (key, value) -> validateEntry(key, value) }
}

private fun validateEntry(key: String, value: JsonElement): ConfigEntry {
    if (key.isBlank()) return ConfigEntry(null, key, "empty key")
    if (key in RESERVED_KEYS) return ConfigEntry(null, key, "reserved key")
    if (key.contains('/') || key.contains('\\')) {
        return ConfigEntry(null, key, "path separator in key")
    }

    val row = value.asObj() ?: return ConfigEntry(null, key, "entry is not an object")
    val name = row.str("name")
    val apiUrl = row.str("apiUrl").trim().trimEnd('/')
    val detailPath = row.str("detailPath")
    val photoPath = row.str("photoPath")
    val searchPath = row.str("searchPath")

    if (name.isBlank()) return ConfigEntry(null, key, "missing name")
    if (apiUrl.isBlank()) return ConfigEntry(null, key, "missing apiUrl")
    if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
        return ConfigEntry(null, key, "invalid apiUrl")
    }
    if (!detailPath.contains("<id>")) return ConfigEntry(null, key, "detailPath missing <id>")
    if (!photoPath.contains("<id>")) return ConfigEntry(null, key, "photoPath missing <id>")
    if (!searchPath.contains("<text>") || !searchPath.contains("<page>")) {
        return ConfigEntry(null, key, "searchPath missing <text>/<page>")
    }

    return ConfigEntry(
        source = ComicSource(
            key = key,
            name = name,
            apiUrl = apiUrl,
            detailPath = detailPath,
            photoPath = photoPath,
            searchPath = searchPath,
            type = row.str("type").ifBlank { key },
        ),
        key = key,
        reason = null,
    )
}
