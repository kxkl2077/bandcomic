package moe.yzf.comic.wear.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import moe.yzf.comic.wear.data.model.ChapterImages
import moe.yzf.comic.wear.data.model.ComicDetail
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.model.SearchItem
import moe.yzf.comic.wear.data.model.SearchPage
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.net.ssl.SSLException

/** 请求失败类型，用于给用户可行动的提示，对齐快应用版的错误分型语义。 */
enum class ApiErrorType { TIMEOUT, SSL, DOMAIN, CONNECTION, HTTP, PARSE, UNKNOWN }

class ApiException(
    val type: ApiErrorType,
    val httpCode: Int = 0,
    val detail: String = "",
    cause: Throwable? = null,
) : IOException(detail.ifEmpty { type.name }, cause)

/** 一次文本请求的结果；[insecureFallback] 表示 HTTPS 失败后回落到了明文 HTTP。 */
data class FetchText(val body: String, val insecureFallback: Boolean)

/**
 * 漫画源 HTTP 层。
 *
 * 负责按协议文档拼接三类接口地址、注入 User-Agent 与 Cookie、
 * 以及把底层 IO 异常归类成可提示的错误类型。
 */
class ComicApi(
    private val client: OkHttpClient,
    private val userAgent: () -> String,
    private val cookie: (String) -> String?,
    private val json: Json,
) {

    fun buildDetailUrl(source: ComicSource, id: String): String =
        source.apiUrl + source.detailPath.replace("<id>", id)

    fun buildSearchUrl(source: ComicSource, keyword: String, page: Int): String =
        source.apiUrl + source.searchPath
            .replace("<text>", encodeUriComponent(keyword))
            .replace("<page>", page.toString())

    /** 单篇图片接口可不含 `<chapter>`；replace 无匹配时原样返回，正好符合协议。 */
    fun buildPhotoUrl(source: ComicSource, id: String, chapter: Int): String =
        source.apiUrl + source.photoPath
            .replace("<id>", id)
            .replace("<chapter>", chapter.toString())

    fun buildConfigUrl(apiUrlBase: String): String =
        apiUrlBase.trim().trimEnd('/') + "/config"

    fun getJson(sourceKey: String, url: String): JsonElement =
        json.parseToJsonElement(fetchText(sourceKey, url).body)

    /** 仅解析不请求；供 `/config` 拉取流程复用同一套宽松解析配置。 */
    fun parseJson(text: String): JsonElement = json.parseToJsonElement(text)

    /**
     * 发起请求。HTTPS 握手/证书失败时自动回落明文 HTTP 并标记，
     * 对齐快应用 v2.1 起的 HTTP 回退行为（协议文档第 1 节）。
     */
    fun fetchText(sourceKey: String, url: String): FetchText {
        try {
            return FetchText(execute(sourceKey, url), false)
        } catch (e: IOException) {
            val apiError = classify(e)
            if (apiError.type != ApiErrorType.SSL || !url.startsWith("https://")) throw apiError
            val fallbackUrl = "http://" + url.removePrefix("https://")
            try {
                return FetchText(execute(sourceKey, fallbackUrl), true)
            } catch (e2: IOException) {
                throw classify(e2)
            }
        }
    }

    private fun execute(sourceKey: String, url: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
        cookie(sourceKey)?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }

        val response = client.newCall(builder.build()).execute()
        var result = ""
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(ApiErrorType.HTTP, resp.code, "HTTP ${resp.code}")
            }
            val text = resp.body?.string().orEmpty()
            if (text.isEmpty()) throw ApiException(ApiErrorType.PARSE, 0, "empty body")
            result = text
        }
        return result
    }

    /** 把底层异常归类。判定顺序很重要：SSLException 属于 IOException 子类。 */
    fun classify(e: Throwable): ApiException = when (e) {
        is ApiException -> e
        is SSLException -> ApiException(ApiErrorType.SSL, cause = e)
        is java.net.SocketTimeoutException -> ApiException(ApiErrorType.TIMEOUT, cause = e)
        is java.net.UnknownHostException -> ApiException(ApiErrorType.DOMAIN, cause = e)
        is java.net.ConnectException -> ApiException(ApiErrorType.CONNECTION, cause = e)
        is IOException -> {
            val message = (e.message ?: "").lowercase()
            when {
                message.contains("ssl") || message.contains("certificate") ||
                    message.contains("handshake") || message.contains("trust") ->
                    ApiException(ApiErrorType.SSL, cause = e)

                message.contains("timed out") || message.contains("timeout") ->
                    ApiException(ApiErrorType.TIMEOUT, cause = e)

                else -> ApiException(ApiErrorType.CONNECTION, cause = e)
            }
        }

        else -> ApiException(ApiErrorType.UNKNOWN, cause = e)
    }

    // ---- 响应解析 ----
    // 字段类型按协议文档做宽松解析：item_id / page_count / views / rate 允许 number 或 string。

    fun parseDetail(root: JsonElement): ComicDetail? {
        val obj = root.asObj() ?: return null
        val id = obj.str("item_id")
        val name = obj.str("name")
        if (id.isBlank() || name.isBlank()) return null
        return ComicDetail(
            itemId = id,
            name = name,
            pageCount = obj.int("page_count"),
            cover = obj.str("cover"),
            views = obj.str("views"),
            rate = obj.str("rate"),
            tags = obj.strList("tags"),
            totalChapters = obj.int("total_chapters", 1).coerceAtLeast(1),
        )
    }

    fun parseSearch(root: JsonElement): SearchPage {
        val obj = root.asObj() ?: throw ApiException(ApiErrorType.PARSE, 0, "search root not object")
        val rows = obj["results"].asArr()
            ?: throw ApiException(ApiErrorType.PARSE, 0, "results not array")
        val items = rows.mapNotNull { element ->
            val row = element.asObj() ?: return@mapNotNull null
            val id = row.str("comic_id")
            if (id.isBlank()) return@mapNotNull null
            SearchItem(
                comicId = id,
                title = row.str("title").ifBlank { id },
                coverUrl = row.str("cover_url"),
                pages = row.int("pages"),
            )
        }
        return SearchPage(
            page = obj.int("page", 1),
            hasMore = obj.bool("has_more", false),
            results = items,
        )
    }

    fun parseChapterImages(root: JsonElement): ChapterImages {
        val obj = root.asObj() ?: throw ApiException(ApiErrorType.PARSE, 0, "photo root not object")
        val rows = obj["images"].asArr()
            ?: throw ApiException(ApiErrorType.PARSE, 0, "images not array")
        val urls = rows.mapNotNull { element ->
            val row = element.asObj() ?: return@mapNotNull null
            row.str("url").takeIf { it.isNotBlank() }
        }
        if (urls.isEmpty()) throw ApiException(ApiErrorType.PARSE, 0, "no usable image url")
        return ChapterImages(title = obj.str("title"), urls = urls)
    }
}
