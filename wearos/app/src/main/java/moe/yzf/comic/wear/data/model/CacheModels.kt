package moe.yzf.comic.wear.data.model

import kotlinx.serialization.Serializable

/**
 * 本地已缓存的一章。
 *
 * [pageCount] 是接口登记的总页数，[downloaded] 是磁盘上实际存在的页数——
 * 两者分离才能表达「下载中断」：downloaded < pageCount 即未完成，
 * 对应原版 offline.ux 里 `isComicIncomplete` 的判定依据。
 */
@Serializable
data class CachedChapter(
    val num: Int,
    /** 接口返回的章节名，可能为空。 */
    val name: String = "",
    val pageCount: Int = 0,
    val downloaded: Int = 0,
) {
    /** 完整下载（下载页 >= 登记页数）。旧数据 pageCount 为 0 时按有页即完整处理。 */
    val complete: Boolean
        get() = downloaded > 0 && (pageCount <= 0 || downloaded >= pageCount)

    /** 下了一半：本地有页但不完整。对应原版 `.dl-partial` 橙色标记。 */
    val partial: Boolean
        get() = downloaded > 0 && !complete
}

/**
 * 本地已缓存的漫画。
 *
 * 一个漫画由 [sourceKey] + [id] 唯一确定：不同源的 id 可能撞车，
 * 目录名也按这两者拼出来（见 `CacheStore.dirName`）。
 */
@Serializable
data class CachedComic(
    val id: String,
    val sourceKey: String,
    val name: String,
    /** 源站封面地址（仅用于展示，真实文件在目录下的 `cover`）。 */
    val cover: String = "",
    val totalChapters: Int = 1,
    val chapters: List<CachedChapter> = emptyList(),
    /** 磁盘占用字节数。 */
    val size: Long = 0L,
    /** 最近一次下载/更新时间，用于列表排序。 */
    val updatedAt: Long = 0L,
) {
    /** 任一章未下完，或连载已缓存章数少于总章数。对应原版 `isComicIncomplete`。 */
    val incomplete: Boolean
        get() {
            if (chapters.isEmpty()) return false
            if (totalChapters > 1 && chapters.size < totalChapters) return true
            return chapters.any { !it.complete }
        }

    /** 已完整缓存的章节号集合，供选择页打标与阅读器判断能否离线读。 */
    fun downloadedChapterNums(): Set<Int> =
        chapters.filter { it.downloaded > 0 }.map { it.num }.toSet()
}
