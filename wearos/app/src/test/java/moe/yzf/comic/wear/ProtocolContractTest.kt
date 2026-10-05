package moe.yzf.comic.wear

import kotlinx.serialization.json.Json
import moe.yzf.comic.wear.data.net.ApiErrorType
import moe.yzf.comic.wear.data.net.ApiException
import moe.yzf.comic.wear.data.net.ComicApi
import moe.yzf.comic.wear.data.net.addCoverParams
import moe.yzf.comic.wear.data.net.addImageParams
import moe.yzf.comic.wear.data.net.addUrlParam
import moe.yzf.comic.wear.data.source.parseSourceConfig
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议契约测试。
 *
 * 下面这些 JSON 不是编造的样例，而是 2026-10 从真实源
 * `https://mangadex.yuzifu.top`（由内置源 `mangadex.yzf.moe` 经 308 跳转得到）
 * 实际抓取的响应原文。移植风险最大的地方就是「解析是否真的吃得下线上数据」，
 * 所以这里用真实报文把契约钉死。
 */
class ProtocolContractTest {

    private val api = ComicApi(
        client = OkHttpClient(),
        userAgent = { "test" },
        cookie = { null },
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    )

    // 实测 GET {api}/config 原文
    private val configBody = """
        {"MangaDex":{"apiUrl":"https://mangadex.yuzifu.top","detailPath":"/comic/<id>",
        "name":"MangaDex","photoPath":"/photo/<id>/ch/<chapter>",
        "searchPath":"/search/<text>/<page>","type":"mangadex"}}
    """.trimIndent()

    // 实测 GET {api}/search/one/1 原文（截取前两条，字段完整保留）
    private val searchBody = """
        {"has_more":true,"page":1,"results":[
        {"comic_id":"595e3a7a-c762-4f87-90ee-a7dc0dabef91",
        "cover_url":"https://mangadex.yuzifu.top/comic/595e3a7a-c762-4f87-90ee-a7dc0dabef91/cover",
        "pages":0,"title":"Tennis no Oujisama - Onecoin Reserve (Doujinshi)"},
        {"comic_id":"6a0ccf67-f930-4aa2-b095-c60dc4cd2451",
        "cover_url":"https://mangadex.yuzifu.top/comic/6a0ccf67-f930-4aa2-b095-c60dc4cd2451/cover",
        "pages":0,"title":"Pretty Rhythm Rainbow Live - The one who loves Naru the most is... (Doujinshi)"}]}
    """.trimIndent()

    // 实测 GET {api}/comic/{id} 原文：注意 rate 是数字、没有 views 字段
    private val detailBody = """
        {"cover":"https://mangadex.yuzifu.top/comic/595e3a7a-c762-4f87-90ee-a7dc0dabef91/cover",
        "item_id":"595e3a7a-c762-4f87-90ee-a7dc0dabef91",
        "name":"Tennis no Oujisama - Onecoin Reserve (Doujinshi)",
        "page_count":30,"rate":8.07,"tags":["Boys' Love","Doujinshi"],"total_chapters":1}
    """.trimIndent()

    // 实测 GET {api}/photo/{id}/ch/1 原文（截取前三条）
    private val photoBody = """
        {"images":[
        {"url":"https://mangadex.yuzifu.top/photo/595e3a7a-c762-4f87-90ee-a7dc0dabef91/ch/1/1.jpg"},
        {"url":"https://mangadex.yuzifu.top/photo/595e3a7a-c762-4f87-90ee-a7dc0dabef91/ch/1/2.jpg"},
        {"url":"https://mangadex.yuzifu.top/photo/595e3a7a-c762-4f87-90ee-a7dc0dabef91/ch/1/3.jpg"}]}
    """.trimIndent()

    @Test
    fun `config 解析出以 map 键为源 key 的条目`() {
        val entries = parseSourceConfig(api.parseJson(configBody))
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertNull(entry.reason)
        val source = entry.source
        requireNotNull(source)
        // key 取自外层 map 键，而不是条目里的字段——线上报文里没有 key 字段。
        assertEquals("MangaDex", source.key)
        assertEquals("MangaDex", source.name)
        assertEquals("https://mangadex.yuzifu.top", source.apiUrl)
        assertEquals("/comic/<id>", source.detailPath)
        assertEquals("/photo/<id>/ch/<chapter>", source.photoPath)
        assertEquals("/search/<text>/<page>", source.searchPath)
        assertEquals("mangadex", source.type)
    }

    @Test
    fun `搜索响应字段与分页标志解析正确`() {
        val page = api.parseSearch(api.parseJson(searchBody))
        assertEquals(1, page.page)
        assertTrue(page.hasMore)
        assertEquals(2, page.results.size)

        val first = page.results.first()
        assertEquals("595e3a7a-c762-4f87-90ee-a7dc0dabef91", first.comicId)
        assertEquals("Tennis no Oujisama - Onecoin Reserve (Doujinshi)", first.title)
        assertTrue(first.coverUrl.endsWith("/cover"))
        assertEquals(0, first.pages)
    }

    @Test
    fun `详情响应里的数字型 rate 与缺失 views 都能容忍`() {
        val detail = api.parseDetail(api.parseJson(detailBody))
        requireNotNull(detail)
        assertEquals("595e3a7a-c762-4f87-90ee-a7dc0dabef91", detail.itemId)
        assertEquals("Tennis no Oujisama - Onecoin Reserve (Doujinshi)", detail.name)
        assertEquals(30, detail.pageCount)
        // rate 线上是 JSON number(8.07)，必须按文本读取而不是要求字符串。
        assertEquals("8.07", detail.rate)
        // views 线上不存在，应为空串而非解析失败。
        assertEquals("", detail.views)
        assertEquals(listOf("Boys' Love", "Doujinshi"), detail.tags)
        assertEquals(1, detail.totalChapters)
    }

    @Test
    fun `详情缺 item_id 或 name 时判为无效`() {
        assertNull(api.parseDetail(api.parseJson("""{"name":"x"}""")))
        assertNull(api.parseDetail(api.parseJson("""{"item_id":"1"}""")))
    }

    @Test
    fun `章节图片列表解析正确且空列表报错`() {
        val images = api.parseChapterImages(api.parseJson(photoBody))
        assertEquals(3, images.urls.size)
        assertEquals(
            "https://mangadex.yuzifu.top/photo/595e3a7a-c762-4f87-90ee-a7dc0dabef91/ch/1/1.jpg",
            images.urls.first(),
        )

        val failure = runCatching { api.parseChapterImages(api.parseJson("""{"images":[]}""")) }
        assertTrue(failure.exceptionOrNull() is ApiException)
        assertEquals(
            ApiErrorType.PARSE,
            (failure.exceptionOrNull() as ApiException).type,
        )
    }

    @Test
    fun `URL 构造按协议替换占位符`() {
        val source = requireNotNull(
            parseSourceConfig(api.parseJson(configBody)).first().source,
        )
        assertEquals(
            "https://mangadex.yuzifu.top/comic/abc",
            api.buildDetailUrl(source, "abc"),
        )
        assertEquals(
            "https://mangadex.yuzifu.top/search/a%20b/2",
            api.buildSearchUrl(source, "a b", 2),
        )
        assertEquals(
            "https://mangadex.yuzifu.top/photo/abc/ch/7",
            api.buildPhotoUrl(source, "abc", 7),
        )
    }

    @Test
    fun `图片参数拼接保留 fragment 且原位替换同名参数`() {
        // fragment 必须留在末尾：快应用版用它命名固件临时文件。
        assertEquals(
            "http://a/b.jpg?width=480#frag",
            addUrlParam("http://a/b.jpg#frag", "width", "480"),
        )
        // 同名参数原位替换，不重复追加。
        assertEquals(
            "http://a/b.jpg?width=800&quality=50",
            addUrlParam("http://a/b.jpg?width=480&quality=50", "width", "800"),
        )
        // 地址以 ? 结尾时不再多插一个分隔符（快应用版的边界缺陷）。
        assertEquals(
            "http://a/b.jpg?width=480",
            addUrlParam("http://a/b.jpg?", "width", "480"),
        )
        // 空格编码成 %20 而不是 +，对齐 JS encodeURIComponent。
        assertEquals(
            "http://a/b.jpg?width=480&quality=50",
            addImageParams("http://a/b.jpg", 480, 50, false),
        )
        assertEquals(
            "http://a/b.jpg?width=480&quality=50&ifPNG=1",
            addImageParams("http://a/b.jpg", 480, 50, true),
        )
        // 封面固定 width=80。
        assertEquals(
            "http://a/b.jpg?width=80&quality=60",
            addCoverParams("http://a/b.jpg", 60, false),
        )
    }

    @Test
    fun `非法源配置被拒绝且给出原因`() {
        val body = """
            {"ok":{"name":"N","apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "noName":{"apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "badScheme":{"name":"N","apiUrl":"ftp://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "noPlaceholder":{"name":"N","apiUrl":"https://x.y","detailPath":"/c",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "using":{"name":"N","apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"}}
        """.trimIndent()

        val entries = parseSourceConfig(api.parseJson(body)).associateBy { it.key }
        requireNotNull(entries["ok"]!!.source)

        assertEquals("missing name", entries["noName"]!!.reason)
        assertEquals("invalid apiUrl", entries["badScheme"]!!.reason)
        assertEquals("detailPath missing <id>", entries["noPlaceholder"]!!.reason)
        // using 是保留键，不能被当成源。
        assertEquals("reserved key", entries["using"]!!.reason)
        assertNull(entries["using"]!!.source)
    }
}
