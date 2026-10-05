package moe.yzf.comic.wear.data.store

import kotlinx.serialization.Serializable
import moe.yzf.comic.wear.data.model.CachedChapter
import moe.yzf.comic.wear.data.model.CachedComic
import java.io.File

@Serializable
data class CachePayload(
    val comics: List<CachedComic> = emptyList(),
)

private const val FILE_CACHE = "comic_cache.json"

/**
 * 本地漫画缓存：索引 + 磁盘布局。
 *
 * 目录约定（与原版快应用一致的部分）：
 * ```
 * files/comics/{sourceKey}_{id}/cover          封面
 * files/comics/{sourceKey}_{id}/{章号}/{页码}   正文页
 * ```
 * 目录名取 `{sourceKey}_{id}`，因为不同源的漫画 id 可能相同。
 *
 * 与原版唯一的差异：原版章节目录名是 `{章号}　{章名}`（全角空格分隔），
 * 章名里一旦出现分隔符就会歧义。这里只用 `{章号}`，章名存进索引，
 * 可读性不受影响且没有解析陷阱。Wear 侧不要求与 Vela 的既有下载目录互通。
 */
class CacheStore(
    private val files: JsonFileStore,
    private val root: File,
    private val onRecovered: (String) -> Unit = {},
) {

    private var comics: MutableList<CachedComic> = mutableListOf()
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        comics = files.read(FILE_CACHE, CachePayload.serializer(), CachePayload(), onRecovered)
            .comics.toMutableList()
        loaded = true
    }

    // ---- 索引 ----

    /** 按最近更新倒序。 */
    @Synchronized
    fun list(): List<CachedComic> {
        ensureLoaded()
        return comics.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun find(id: String, sourceKey: String): CachedComic? {
        ensureLoaded()
        return comics.firstOrNull { it.id == id && it.sourceKey == sourceKey }
    }

    /**
     * 按漫画 id 查找已缓存的记录，优先命中 [preferSourceKey]。
     * 阅读器用它来判断「这一章能不能离线读」——用户换过源之后
     * 也不该认不出自己下过的漫画。
     */
    @Synchronized
    fun findAny(id: String, preferSourceKey: String?): CachedComic? {
        ensureLoaded()
        val sameId = comics.filter { it.id == id }
        if (sameId.isEmpty()) return null
        return sameId.firstOrNull { it.sourceKey == preferSourceKey }
            ?: sameId.maxByOrNull { it.updatedAt }
    }

    @Synchronized
    fun upsert(comic: CachedComic) {
        ensureLoaded()
        val index = comics.indexOfFirst { it.id == comic.id && it.sourceKey == comic.sourceKey }
        if (index >= 0) comics[index] = comic else comics.add(0, comic)
        persist()
    }

    /**
     * 读-改-写。下载过程中每章都要更新一次，用锁保证不会写丢。
     * [transform] 返回 null 表示记录已不存在，此时不新建。
     */
    @Synchronized
    fun update(id: String, sourceKey: String, transform: (CachedComic?) -> CachedComic?): CachedComic? {
        ensureLoaded()
        val index = comics.indexOfFirst { it.id == id && it.sourceKey == sourceKey }
        val current = if (index >= 0) comics[index] else null
        val next = transform(current) ?: return null
        if (index >= 0) comics[index] = next else comics.add(0, next)
        persist()
        return next
    }

    /** 删除索引条目，返回被删掉的记录（调用方据此删文件）。 */
    @Synchronized
    fun remove(id: String, sourceKey: String): CachedComic? {
        ensureLoaded()
        val removed = comics.firstOrNull { it.id == id && it.sourceKey == sourceKey }
        if (removed != null) {
            comics.removeAll { it.id == id && it.sourceKey == sourceKey }
            persist()
        }
        return removed
    }

    private fun persist() {
        files.write(FILE_CACHE, CachePayload.serializer(), CachePayload(comics.toList()))
    }

    // ---- 磁盘布局 ----

    fun comicDir(sourceKey: String, id: String): File = File(root, dirName(sourceKey, id))

    fun coverFile(sourceKey: String, id: String): File = File(comicDir(sourceKey, id), "cover")

    fun chapterDir(sourceKey: String, id: String, chapter: Int): File =
        File(comicDir(sourceKey, id), chapter.toString())

    fun pageFile(sourceKey: String, id: String, chapter: Int, page: Int): File =
        File(chapterDir(sourceKey, id, chapter), page.toString())

    /** 目录名：`{sourceKey}_{id}`，非法字符替换成下划线。 */
    fun dirName(sourceKey: String, id: String): String = sanitize("${sourceKey}_$id")

    /** 删除整本漫画的目录。返回是否真的删掉了东西。 */
    fun deleteComicFiles(sourceKey: String, id: String): Boolean =
        comicDir(sourceKey, id).deleteRecursively()

    /** 删除某一章的目录，用于「重新下载这一章」。 */
    fun deleteChapterFiles(sourceKey: String, id: String, chapter: Int): Boolean =
        chapterDir(sourceKey, id, chapter).deleteRecursively()

    /** 递归统计目录占用；目录不存在按 0 计。 */
    fun directorySize(dir: File): Long {
        if (!dir.isDirectory) return if (dir.isFile) dir.length() else 0L
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += if (child.isDirectory) directorySize(child) else child.length()
        }
        return total
    }

    /** 扫描某章磁盘上真实存在的页文件，按页码升序。 */
    fun existingPages(sourceKey: String, id: String, chapter: Int): List<File> =
        chapterDir(sourceKey, id, chapter)
            .listFiles()
            ?.filter { it.isFile && it.name.toIntOrNull() != null }
            ?.sortedBy { it.name.toInt() }
            ?: emptyList()

    private fun sanitize(name: String): String =
        name.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .take(120)
            .ifBlank { "comic" }

    companion object {
        /** 给目录外的地方（如删除失败提示）用。 */
        fun displaySize(bytes: Long): String = when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "%.2fKB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.2fMB".format(bytes / (1024.0 * 1024))
            else -> "%.2fGB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}

/** 由已有页文件重建 [CachedChapter]，用于下载中断后的自愈。 */
fun rebuildChapter(prev: CachedChapter?, num: Int, pages: List<File>): CachedChapter =
    CachedChapter(
        num = num,
        name = prev?.name.orEmpty(),
        pageCount = prev?.pageCount ?: pages.size,
        downloaded = pages.size,
    )
