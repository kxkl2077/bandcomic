package moe.yzf.comic.wear.data.source

import kotlinx.serialization.json.JsonElement
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.net.asObj
import moe.yzf.comic.wear.data.net.str

/**
 * 源配置校验与 ID 形态判定。
 *
 * 逐条移植快应用版 `src/components/sourceConfig.js`（上游提交 78919b9
 * 「加固八源运行规则与错误诊断」引入），并对照 `docs/SOURCE_RUNTIME.md` 第 2 节。
 * 保持同样的宽松度：只拒绝会真正出错的东西，不因为「格式不熟」就判非法。
 */

/** 不允许作为源 key 的保留键；后三个是原型污染键。 */
private val RESERVED_KEYS = setOf("using", "type", "__proto__", "prototype", "constructor")

/** `/config` 支持的 `idType` 取值。 */
private val ID_TYPES = setOf("numeric", "uuid", "gid_token", "slug", "string")

/** 路径里允许出现的占位符。 */
private val ALLOWED_PLACEHOLDERS = setOf("<id>", "<chapter>", "<text>", "<page>")

/** 各路径必须包含的占位符。 */
private val REQUIRED_PLACEHOLDERS = mapOf(
    "detailPath" to listOf("<id>"),
    "photoPath" to listOf("<id>"),
    "searchPath" to listOf("<text>", "<page>"),
)

private val RE_KEY_ILLEGAL = Regex("[\\\\/<>\\u0000-\\u001f]")
private val RE_BASE_URL = Regex(
    "^https?://(\\[[0-9a-f:]+\\]|[^\\s/?#:@<>%]+)(?::(\\d{1,5}))?(?:/[^\\s?#<>]*)?\$",
    RegexOption.IGNORE_CASE,
)
private val RE_DOT_SEGMENT = Regex("/(?:\\.|\\.\\.)(?:/|\\?|\$)")
private val RE_DOT_SEGMENT_BASE = Regex("/(?:\\.|\\.\\.)(?:/|\$)")
private val RE_PATH_START = Regex("^/(?!/)")
private val RE_PATH_ILLEGAL = Regex("[\\s#\\\\]")
private val RE_PLACEHOLDER = Regex("<[^>]*>")
private val RE_STRAY_ANGLE = Regex("[<>]")
private val RE_IPV4 = Regex("^\\d+(?:\\.\\d+){3}\$")

private val RE_UUID = Regex("^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\$", RegexOption.IGNORE_CASE)
private val RE_GID_TOKEN = Regex("^\\d+_[a-f0-9]{10}\$", RegexOption.IGNORE_CASE)
private val RE_SLUG = Regex("^[a-z0-9][a-z0-9_-]{1,127}\$", RegexOption.IGNORE_CASE)
private val RE_STRING_ID = Regex("^[a-z0-9][a-z0-9_-]{0,127}\$", RegexOption.IGNORE_CASE)
private val RE_NUMERIC = Regex("^\\d{1,20}\$")

/**
 * 源 key 是否合法：非空、首尾无空白、不超过 80 字符、不是保留键、不含路径分隔符与控制字符。
 *
 * key 是 Cookie / 阅读历史 / 离线目录的身份，必须能安全地进文件系统与 JSON 键位。
 */
fun isSourceKey(key: String): Boolean =
    key.isNotEmpty() &&
        key.trim() == key &&
        key.length <= 80 &&
        key !in RESERVED_KEYS &&
        !RE_KEY_ILLEGAL.containsMatchIn(key)

/**
 * 是否为可用的 apiUrl 基地址。
 *
 * Vela 不保证有完整的 WHATWG URL 实现，所以这里用正则自行判定：
 * 支持域名、IPv6 字面量（方括号）、可选端口，拒绝目录穿越与越界端口/IP。
 */
fun isBaseUrl(value: String): Boolean {
    if (!RE_BASE_URL.matches(value)) return false
    // 基地址里出现 /./ 或 /../ 一律拒绝
    if (RE_DOT_SEGMENT_BASE.containsMatchIn(value)) return false

    val matched = RE_BASE_URL.find(value) ?: return false
    val host = matched.groupValues[1]
    val port = matched.groupValues[2]

    if (port.isNotEmpty()) {
        val number = port.toIntOrNull() ?: return false
        if (number < 1 || number > 65535) return false
    }
    // 形如 IPv4 时逐段检查，拒绝 999.1.1.1 这类
    if (RE_IPV4.matches(host) && host.split('.').any { (it.toIntOrNull() ?: 256) > 255 }) return false

    return true
}

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
 * 对齐快应用版「非法条目显示字段原因且不可勾选」的行为，
 * 等价于上游 `validSourceDirectory`。
 */
fun parseSourceConfig(root: JsonElement): List<ConfigEntry> {
    val obj = root.asObj() ?: return emptyList()
    return obj.entries.map { (key, value) -> validateEntry(key, value) }
}

private fun validateEntry(key: String, value: JsonElement): ConfigEntry {
    if (key.isBlank()) return ConfigEntry(null, key, "empty key")
    if (key in RESERVED_KEYS) return ConfigEntry(null, key, "reserved key")
    if (!isSourceKey(key)) return ConfigEntry(null, key, "illegal key")

    val row = value.asObj() ?: return ConfigEntry(null, key, "entry is not an object")
    val name = row.str("name")
    val apiUrl = row.str("apiUrl").trim().trimEnd('/')
    val idType = row.str("idType").trim()
    val type = row.str("type").trim()

    if (name.isBlank()) return ConfigEntry(null, key, "missing name")
    if (apiUrl.isBlank()) return ConfigEntry(null, key, "missing apiUrl")
    if (!isBaseUrl(apiUrl)) return ConfigEntry(null, key, "invalid apiUrl")
    if (idType.isNotEmpty() && idType !in ID_TYPES) {
        return ConfigEntry(null, key, "invalid idType")
    }

    val paths = mapOf(
        "detailPath" to row.str("detailPath"),
        "photoPath" to row.str("photoPath"),
        "searchPath" to row.str("searchPath"),
    )
    for ((field, path) in paths) {
        checkPath(field, path)?.let { return ConfigEntry(null, key, it) }
    }

    return ConfigEntry(
        source = ComicSource(
            key = key,
            name = name,
            apiUrl = apiUrl,
            detailPath = paths.getValue("detailPath"),
            photoPath = paths.getValue("photoPath"),
            searchPath = paths.getValue("searchPath"),
            type = type.ifBlank { key },
            idType = idType,
        ),
        key = key,
        reason = null,
    )
}

/** 返回 null 表示该路径可用，否则返回不可用原因。 */
private fun checkPath(field: String, path: String): String? {
    if (path.isBlank()) return "$field missing"
    // 必须以单个 / 开头（// 开头是协议相对地址，不允许）
    if (!RE_PATH_START.containsMatchIn(path)) return "$field must start with /"
    // 空白、片段锚点、反斜杠都会让拼接出的请求地址不可控
    if (RE_PATH_ILLEGAL.containsMatchIn(path)) return "$field has illegal character"
    // 目录穿越
    if (RE_DOT_SEGMENT.containsMatchIn(path)) return "$field has dot segment"

    val placeholders = RE_PLACEHOLDER.findAll(path).map { it.value }.toList()
    if (placeholders.any { it !in ALLOWED_PLACEHOLDERS }) return "$field has unknown placeholder"
    // 挖掉合法占位符后不该再有尖括号
    if (RE_STRAY_ANGLE.containsMatchIn(RE_PLACEHOLDER.replace(path, ""))) {
        return "$field has stray angle bracket"
    }

    val required = REQUIRED_PLACEHOLDERS.getValue(field)
    val missing = required.filterNot { path.contains(it) }
    if (missing.isNotEmpty()) {
        return if (field == "searchPath") {
            "$field missing <text>/<page>"
        } else {
            "$field missing ${missing.joinToString("/")}"
        }
    }
    return null
}

/**
 * 判断用户输入是这条源的漫画 ID，还是应当走关键词搜索。
 *
 * `idType` 显式声明优先；未声明时按 `type` / `key` 推断（MangaDex 认 UUID、
 * E-Hentai 认 `gid_token`、拷贝漫画认 slug），都不匹配则按纯数字 `^\d{1,20}$`。
 * 这三类此前会被一律错当成关键词丢去搜索。
 *
 * 注：上游 `isComicId` 把 key 的通配判断与 `idType` 并列在同一个 `||` 链里，
 * 于是 key 里含 `mangadex` 时会盖掉显式的 `idType` —— 与它自己的文档
 * （「`idType` 可选…**默认识别**：MangaDex 匹配 UUID」）相矛盾。
 * 这里按文档语义实现，让显式声明真正生效。
 */
fun isComicId(value: String, source: ComicSource?): Boolean {
    val text = value.trim()
    val kind = source?.idType.orEmpty().ifBlank { inferIdKind(source) }
    return when (kind) {
        "uuid" -> RE_UUID.matches(text)
        "gid_token" -> RE_GID_TOKEN.matches(text)
        "slug" -> RE_SLUG.matches(text)
        "string" -> RE_STRING_ID.matches(text)
        else -> RE_NUMERIC.matches(text)
    }
}

/** 由 `type` / `key` 推断 ID 形态；返回空串表示没有可识别的特征，按纯数字处理。 */
private fun inferIdKind(source: ComicSource?): String {
    if (source == null) return ""
    val type = source.type
    val key = source.key
    return when {
        MANGA_DEX.containsMatchIn(type) || MANGA_DEX.containsMatchIn(key) -> "uuid"
        E_HENTAI.containsMatchIn(type) || E_HENTAI.containsMatchIn(key) -> "gid_token"
        COPY_MANGA.containsMatchIn(type) || COPY_MANGA_KEY.containsMatchIn(key) -> "slug"
        else -> ""
    }
}

private val MANGA_DEX = Regex("mangadex|manga_dex", RegexOption.IGNORE_CASE)
private val E_HENTAI = Regex("ehentai|e-hentai", RegexOption.IGNORE_CASE)
private val COPY_MANGA = Regex("copymanga|copy_manga", RegexOption.IGNORE_CASE)
private val COPY_MANGA_KEY = Regex("copy_manga", RegexOption.IGNORE_CASE)
