package moe.yzf.comic.wear.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.yzf.comic.wear.data.model.ChapterImages
import moe.yzf.comic.wear.data.model.ComicDetail
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.model.SearchPage
import moe.yzf.comic.wear.data.net.ApiErrorType
import moe.yzf.comic.wear.data.net.ApiException
import moe.yzf.comic.wear.data.net.ComicApi
import moe.yzf.comic.wear.data.source.parseSourceConfig
import moe.yzf.comic.wear.data.store.SourceStore

/** `/config` 添加源的结果。 */
data class AddSourceResult(
    val names: List<String>,
    /** true 表示 HTTPS 握手失败后回落到明文 HTTP。 */
    val insecure: Boolean,
)

/**
 * 漫画业务编排层。UI 只依赖这一层，不直接接触 HTTP 与存储细节。
 * 所有网络与文件操作都切到 IO 调度器。
 */
class ComicRepository(
    private val api: ComicApi,
    private val sources: SourceStore,
) {

    fun currentSource(): ComicSource? = sources.current()

    /**
     * 纯数字输入按漫画 ID 直达详情，其余走搜索——对齐快应用版
     * `index.ux` 中 `submitSearch` 的 `/^\d{1,20}$/` 分支。
     */
    fun looksLikeComicId(input: String): Boolean =
        input.length in 1..20 && input.all { it.isDigit() }

    suspend fun search(keyword: String, page: Int): Result<SearchPage> = guarded {
        val source = requireSource()
        api.parseSearch(api.getJson(source.key, api.buildSearchUrl(source, keyword, page)))
    }

    suspend fun detail(id: String): Result<ComicDetail> = guarded {
        val source = requireSource()
        api.parseDetail(api.getJson(source.key, api.buildDetailUrl(source, id)))
            ?: throw ApiException(ApiErrorType.PARSE, 0, "invalid detail payload")
    }

    suspend fun chapterImages(id: String, chapter: Int): Result<ChapterImages> = guarded {
        val source = requireSource()
        api.parseChapterImages(api.getJson(source.key, api.buildPhotoUrl(source, id, chapter)))
    }

    /** 拉取 `{api}/config`，校验后写入源列表。 */
    suspend fun addSourceFromConfig(apiUrlBase: String): Result<AddSourceResult> = guarded {
        val trimmed = apiUrlBase.trim().trimEnd('/')
        if (trimmed.isBlank()) throw ApiException(ApiErrorType.UNKNOWN, 0, "empty api url")
        val fetched = api.fetchText("", api.buildConfigUrl(trimmed))
        val entries = parseSourceConfig(api.parseJson(fetched.body))
        val valid = entries.mapNotNull { it.source }
        if (valid.isEmpty()) {
            throw ApiException(ApiErrorType.PARSE, 0, "no valid source in /config")
        }
        sources.upsert(valid)
        AddSourceResult(names = valid.map { it.name }, insecure = fetched.insecureFallback)
    }

    private fun requireSource(): ComicSource =
        sources.current() ?: throw ApiException(ApiErrorType.UNKNOWN, 0, "no comic source")

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ApiException) {
                Result.failure(e)
            } catch (e: Throwable) {
                Result.failure(api.classify(e))
            }
        }
}
