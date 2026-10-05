package moe.yzf.comic.wear.data.store

import kotlinx.serialization.Serializable
import moe.yzf.comic.wear.data.model.ComicSource

@Serializable
data class SourcesPayload(
    val sources: Map<String, ComicSource> = emptyMap(),
    val using: String = "",
)

@Serializable
data class CookiesPayload(
    val cookies: Map<String, String> = emptyMap(),
)

/**
 * 内置出厂源。取值与快应用版 `src/app.ux` 中的 `global.API_SETTING.MangaDex` 一致，
 * 用作「所有源被删除」时的恢复兜底。
 */
val BUILTIN_SOURCES: List<ComicSource> = listOf(
    ComicSource(
        key = "MangaDex",
        name = "MangaDex",
        apiUrl = "https://mangadex.yzf.moe",
        detailPath = "/comic/<id>",
        photoPath = "/photo/<id>/ch/<chapter>",
        searchPath = "/search/<text>/<page>",
        type = "mangadex",
        builtin = true,
    ),
)

private const val FILE_SOURCES = "sources.json"
private const val FILE_COOKIES = "cookies.json"

/**
 * 漫画源与 Cookie 存储。
 *
 * 兜底规则对齐快应用版的 `ensureUsingSourceValid`：源集合为空时恢复内置源，
 * `using` 悬空时指向第一个可用源——绝不让 `using` 指向不存在的 key，
 * 否则首页取名解引用会直接崩溃（快应用版 P0-11 的教训）。
 */
class SourceStore(
    private val files: JsonFileStore,
    private val onRecovered: (String) -> Unit = {},
) {

    private var payload: SourcesPayload = SourcesPayload()
    private var cookies: CookiesPayload = CookiesPayload()
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        payload = files.read(FILE_SOURCES, SourcesPayload.serializer(), SourcesPayload(), onRecovered)
        cookies = files.read(FILE_COOKIES, CookiesPayload.serializer(), CookiesPayload(), onRecovered)
        if (normalize()) persistSources()
        loaded = true
    }

    /** @return 是否发生了修正（需要落盘）。 */
    private fun normalize(): Boolean {
        val map = LinkedHashMap(payload.sources)
        var changed = false

        if (map.isEmpty()) {
            BUILTIN_SOURCES.forEach { map[it.key] = it }
            changed = true
        }

        var using = payload.using
        if (using.isBlank() || !map.containsKey(using)) {
            using = map.keys.firstOrNull().orEmpty()
            changed = true
        }

        payload = SourcesPayload(map, using)
        return changed
    }

    @Synchronized
    fun list(): List<ComicSource> {
        ensureLoaded()
        return payload.sources.values.toList()
    }

    @Synchronized
    fun current(): ComicSource? {
        ensureLoaded()
        return payload.sources[payload.using]
    }

    @Synchronized
    fun currentKey(): String {
        ensureLoaded()
        return payload.using
    }

    @Synchronized
    fun setUsing(key: String) {
        ensureLoaded()
        if (!payload.sources.containsKey(key)) return
        payload = payload.copy(using = key)
        persistSources()
    }

    /** 写入/更新源；同 key 覆盖。返回是否新增。 */
    @Synchronized
    fun upsert(sources: List<ComicSource>): Boolean {
        ensureLoaded()
        if (sources.isEmpty()) return false
        val map = LinkedHashMap(payload.sources)
        var added = false
        sources.forEach { source ->
            if (!map.containsKey(source.key)) added = true
            map[source.key] = source
        }
        val using = if (map.containsKey(payload.using)) payload.using else sources.first().key
        payload = SourcesPayload(map, using)
        persistSources()
        return added
    }

    @Synchronized
    fun remove(key: String) {
        ensureLoaded()
        val map = LinkedHashMap(payload.sources)
        map.remove(key)
        payload = SourcesPayload(map, payload.using)
        normalize()
        persistSources()
        cookies = CookiesPayload(cookies.cookies.filterKeys { it != key })
        files.write(FILE_COOKIES, CookiesPayload.serializer(), cookies)
    }

    @Synchronized
    fun cookie(key: String): String? {
        ensureLoaded()
        return cookies.cookies[key]
    }

    @Synchronized
    fun setCookie(key: String, value: String) {
        ensureLoaded()
        val map = LinkedHashMap(cookies.cookies)
        if (value.isBlank()) map.remove(key) else map[key] = value
        cookies = CookiesPayload(map)
        files.write(FILE_COOKIES, CookiesPayload.serializer(), cookies)
    }

    private fun persistSources() {
        files.write(FILE_SOURCES, SourcesPayload.serializer(), payload)
    }
}
