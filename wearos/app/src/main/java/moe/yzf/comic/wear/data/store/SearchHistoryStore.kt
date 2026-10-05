package moe.yzf.comic.wear.data.store

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistoryPayload(
    val keywords: List<String> = emptyList(),
)

private const val FILE_HISTORY = "search_history.json"

/** 落盘上限；首页只展示前 [DISPLAY_LIMIT] 条（对齐快应用版 `slice(0, 5)`）。 */
private const val STORE_LIMIT = 10
const val DISPLAY_LIMIT = 5

/** 搜索关键词历史：去重置顶，写入失败不影响搜索主流程。 */
class SearchHistoryStore(
    private val files: JsonFileStore,
    private val onRecovered: (String) -> Unit = {},
) {

    private var keywords: MutableList<String> = mutableListOf()
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        keywords = files.read(
            FILE_HISTORY,
            SearchHistoryPayload.serializer(),
            SearchHistoryPayload(),
            onRecovered,
        ).keywords.filter { it.isNotBlank() }.toMutableList()
        loaded = true
    }

    @Synchronized
    fun list(): List<String> {
        ensureLoaded()
        return keywords.toList()
    }

    @Synchronized
    fun add(keyword: String): List<String> {
        ensureLoaded()
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            keywords.remove(trimmed)
            keywords.add(0, trimmed)
            while (keywords.size > STORE_LIMIT) keywords.removeAt(keywords.lastIndex)
            persist()
        }
        return keywords.toList()
    }

    @Synchronized
    fun clear(): List<String> {
        ensureLoaded()
        keywords.clear()
        persist()
        return emptyList()
    }

    private fun persist() {
        files.write(FILE_HISTORY, SearchHistoryPayload.serializer(), SearchHistoryPayload(keywords.toList()))
    }
}
