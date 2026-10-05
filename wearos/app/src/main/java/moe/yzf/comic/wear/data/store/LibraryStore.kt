package moe.yzf.comic.wear.data.store

import kotlinx.serialization.Serializable
import moe.yzf.comic.wear.data.model.BookEntry

@Serializable
data class LibraryPayload(
    val books: List<BookEntry> = emptyList(),
)

private const val FILE_LIBRARY = "library.json"

/**
 * 书架 + 阅读进度存储。
 *
 * 快应用版把「本地漫画索引」和「阅读历史」分开存放；Wear OS 首版的核心闭环
 * 没有下载器，两者合并成一份 [BookEntry] 列表更简单，也让「书架即历史」。
 */
class LibraryStore(
    private val files: JsonFileStore,
    private val onRecovered: (String) -> Unit = {},
) {

    private var books: MutableList<BookEntry> = mutableListOf()
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        books = files.read(FILE_LIBRARY, LibraryPayload.serializer(), LibraryPayload(), onRecovered)
            .books.toMutableList()
        loaded = true
    }

    /** 按最近阅读倒序。 */
    @Synchronized
    fun list(): List<BookEntry> {
        ensureLoaded()
        return books.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun find(id: String, sourceKey: String): BookEntry? {
        ensureLoaded()
        return books.firstOrNull { it.id == id && it.sourceKey == sourceKey }
    }

    @Synchronized
    fun upsert(entry: BookEntry) {
        ensureLoaded()
        val index = books.indexOfFirst { it.id == entry.id && it.sourceKey == entry.sourceKey }
        if (index >= 0) books[index] = entry else books.add(0, entry)
        persist()
    }

    @Synchronized
    fun remove(id: String, sourceKey: String) {
        ensureLoaded()
        books.removeAll { it.id == id && it.sourceKey == sourceKey }
        persist()
    }

    @Synchronized
    fun clear() {
        ensureLoaded()
        books.clear()
        persist()
    }

    private fun persist() {
        files.write(FILE_LIBRARY, LibraryPayload.serializer(), LibraryPayload(books.toList()))
    }
}
