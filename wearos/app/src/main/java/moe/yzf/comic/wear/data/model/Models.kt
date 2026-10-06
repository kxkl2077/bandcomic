package moe.yzf.comic.wear.data.model

import kotlinx.serialization.Serializable

/**
 * 漫画源配置。字段命名与协议文档 `/config` 返回保持一致，
 * 见 docs/CUSTOM_SOURCE.md 第 2 节。
 */
@Serializable
data class ComicSource(
    val key: String,
    val name: String,
    val apiUrl: String,
    val detailPath: String,
    val photoPath: String,
    val searchPath: String,
    val type: String = "",
    /**
     * ID 形态声明，决定「输入的是 ID 还是关键词」的判定。
     * 取值 `numeric` / `uuid` / `gid_token` / `slug` / `string`，空表示按 [type] 或 [key] 推断。
     * 对齐上游 `sourceConfig.js` 的 `ID_TYPES`。
     */
    val idType: String = "",
    /** 出厂内置源标记：用于「删光了就恢复兜底」的判定。 */
    val builtin: Boolean = false,
)

/**
 * 书架条目。同时承载「我的书架」与「阅读历史」，
 * chapter / page 即续读进度，updatedAt 用于按最近阅读排序。
 */
@Serializable
data class BookEntry(
    val id: String,
    val sourceKey: String,
    val name: String,
    val cover: String = "",
    val pageCount: Int = 0,
    val totalChapters: Int = 1,
    val chapter: Int = 1,
    val page: Int = 1,
    val updatedAt: Long = 0L,
)

/** 详情接口数据（`detailPath`）。 */
data class ComicDetail(
    val itemId: String,
    val name: String,
    val pageCount: Int,
    val cover: String,
    val views: String,
    val rate: String,
    val tags: List<String>,
    val totalChapters: Int,
)

/** 单条搜索结果。 */
data class SearchItem(
    val comicId: String,
    val title: String,
    val coverUrl: String,
    val pages: Int,
)

/** 搜索接口分页数据（`searchPath`）。 */
data class SearchPage(
    val page: Int,
    val hasMore: Boolean,
    val results: List<SearchItem>,
)

/** 图片列表接口数据（`photoPath`）。 */
data class ChapterImages(
    val title: String,
    val urls: List<String>,
)
