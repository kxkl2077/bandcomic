package moe.yzf.comic.wear

import kotlinx.serialization.json.Json
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.source.isBaseUrl
import moe.yzf.comic.wear.data.source.isComicId
import moe.yzf.comic.wear.data.source.isSourceKey
import moe.yzf.comic.wear.data.source.parseSourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/config` 源配置校验与 ID 形态判定的规则测试。
 *
 * 逐条对应上游 `src/components/sourceConfig.js`（提交 78919b9）与
 * `docs/SOURCE_RUNTIME.md` 第 2 节。全是纯函数，不需要 Android 运行环境。
 */
class SourceConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String) = parseSourceConfig(json.parseToJsonElement(body))

    /** 一条字段齐全、各占位符都合法的源。 */
    private fun body(
        key: String = "Demo",
        name: String = "演示",
        apiUrl: String = "https://api.example.com",
        detailPath: String = "/comic/<id>",
        photoPath: String = "/photo/<id>/ch/<chapter>",
        searchPath: String = "/search/<text>/<page>",
        extra: String = "",
    ) = """
        {"$key":{"name":"$name","apiUrl":"$apiUrl","detailPath":"$detailPath",
        "photoPath":"$photoPath","searchPath":"$searchPath"$extra}}
    """.trimIndent()

    private fun source(
        key: String = "Demo",
        type: String = "",
        idType: String = "",
    ) = ComicSource(
        key = key,
        name = key,
        apiUrl = "https://api.example.com",
        detailPath = "/comic/<id>",
        photoPath = "/photo/<id>/ch/<chapter>",
        searchPath = "/search/<text>/<page>",
        type = type,
        idType = idType,
    )

    // ---- key 规则 ----

    @Test
    fun `key_规则`() {
        assertTrue(isSourceKey("MangaDex"))
        assertTrue(isSourceKey("CopyManga"))
        assertTrue(isSourceKey("a"))
        // 保留键：既是 JSON 元字段位，也是原型污染面
        listOf("using", "type", "__proto__", "prototype", "constructor").forEach {
            assertFalse("$it 应被拒绝", isSourceKey(it))
        }
        // 路径分隔符与尖括号不能进 key（key 会变成目录名与 JSON 键）
        listOf("a/b", "a\\b", "a<b", "a>b", "a\nb").forEach {
            assertFalse("$it 应被拒绝", isSourceKey(it))
        }
        assertFalse("空串", isSourceKey(""))
        assertFalse("首尾空白", isSourceKey(" Demo"))
        assertFalse("首尾空白", isSourceKey("Demo "))
        assertFalse("超 80 字符", isSourceKey("a".repeat(81)))
        assertTrue("正好 80 字符应通过", isSourceKey("a".repeat(80)))
    }

    // ---- apiUrl 规则 ----

    @Test
    fun `apiUrl_规则`() {
        assertTrue(isBaseUrl("https://api.example.com"))
        assertTrue(isBaseUrl("http://comic.kxkl2024.cn"))
        assertTrue(isBaseUrl("http://127.0.0.1:8080"))
        assertTrue(isBaseUrl("http://[2001:db8::1]:8443"))
        assertTrue(isBaseUrl("https://api.example.com/api/v1"))

        assertFalse("非 http 协议", isBaseUrl("ftp://api.example.com"))
        assertFalse("无协议", isBaseUrl("api.example.com"))
        assertFalse("无主机", isBaseUrl("https://"))
        assertFalse("端口越界", isBaseUrl("http://a.com:70000"))
        assertFalse("端口为 0", isBaseUrl("http://a.com:0"))
        assertFalse("IPv4 段越界", isBaseUrl("http://999.1.1.1"))
        assertFalse("目录穿越", isBaseUrl("https://a.com/../etc"))
        assertFalse("当前目录段", isBaseUrl("https://a.com/./x"))
        assertFalse("含空白", isBaseUrl("https://a b.com"))
    }

    // ---- /config 条目校验 ----

    @Test
    fun `合法条目解析出全部字段含_idType`() {
        val entry = parse(body(extra = ",\"type\":\"copymanga\",\"idType\":\"slug\"")).single()
        assertNull(entry.reason)
        val source = requireNotNull(entry.source)
        assertEquals("Demo", source.key)
        assertEquals("演示", source.name)
        assertEquals("https://api.example.com", source.apiUrl)
        assertEquals("copymanga", source.type)
        // idType 此前被静默丢弃，现在必须原样带出来
        assertEquals("slug", source.idType)
    }

    @Test
    fun `type_缺省时回退成_key`() {
        val source = parse(body()).single().source!!
        assertEquals("Demo", source.type)
    }

    @Test
    fun `apiUrl_末尾斜杠被归一化`() {
        val source = parse(body(apiUrl = "https://api.example.com///")).single().source!!
        assertEquals("https://api.example.com", source.apiUrl)
    }

    @Test
    fun `非法条目给出原因且不产出源`() {
        val cases = mapOf(
            body(name = "") to "missing name",
            body(apiUrl = "") to "missing apiUrl",
            body(apiUrl = "ftp://x.y") to "invalid apiUrl",
            body(extra = ",\"idType\":\"nope\"") to "invalid idType",
            body(detailPath = "/c") to "detailPath missing <id>",
            body(photoPath = "/p") to "photoPath missing <id>",
            body(searchPath = "/s/<text>") to "searchPath missing <text>/<page>",
        )
        cases.forEach { (payload, expected) ->
            val entry = parse(payload).single()
            assertEquals(expected, entry.reason)
            assertNull(entry.source)
        }
    }

    @Test
    fun `路径必须以单个斜杠开头`() {
        assertEquals("detailPath must start with /", parse(body(detailPath = "c/<id>")).single().reason)
        // // 开头是协议相对地址，会绕开 apiUrl 基地址
        assertEquals("detailPath must start with /", parse(body(detailPath = "//c/<id>")).single().reason)
    }

    @Test
    fun `路径拒绝空白井号反斜杠与目录穿越`() {
        assertEquals(
            "detailPath has illegal character",
            parse(body(detailPath = "/c d/<id>")).single().reason,
        )
        assertEquals(
            "detailPath has illegal character",
            parse(body(detailPath = "/c#f/<id>")).single().reason,
        )
        assertEquals(
            "detailPath has illegal character",
            parse(body(detailPath = "/c\\\\d/<id>")).single().reason,
        )
        assertEquals(
            "detailPath has dot segment",
            parse(body(detailPath = "/../c/<id>")).single().reason,
        )
    }

    @Test
    fun `占位符必须在白名单内且不留裸尖括号`() {
        assertEquals(
            "detailPath has unknown placeholder",
            parse(body(detailPath = "/c/<foo>")).single().reason,
        )
        assertEquals(
            "detailPath has stray angle bracket",
            parse(body(detailPath = "/c/<id>/x<")).single().reason,
        )
    }

    @Test
    fun `保留键与非法_key_在解析阶段就被挡掉`() {
        val payload = """
            {"ok":{"name":"N","apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "using":{"name":"N","apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "__proto__":{"name":"N","apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"},
            "a/b":{"name":"N","apiUrl":"https://x.y","detailPath":"/c/<id>",
            "photoPath":"/p/<id>","searchPath":"/s/<text>/<page>"}}
        """.trimIndent()

        val entries = parse(payload).associateBy { it.key }
        assertNotNull(entries["ok"]!!.source)
        assertEquals("reserved key", entries["using"]!!.reason)
        assertEquals("reserved key", entries["__proto__"]!!.reason)
        assertEquals("illegal key", entries["a/b"]!!.reason)
    }

    // ---- ID 形态判定 ----

    @Test
    fun `未声明时按纯数字判定`() {
        val s = source(key = "SomeSource", type = "some")
        assertTrue(isComicId("12345", s))
        assertTrue(isComicId("1", s))
        assertTrue(isComicId("12345678901234567890", s))
        assertFalse("21 位超出", isComicId("123456789012345678901", s))
        assertFalse("含字母", isComicId("12a", s))
        assertFalse("空串", isComicId("", s))
        assertFalse(isComicId("   ", s))
    }

    @Test
    fun `MangaDex_认_UUID_不认纯数字`() {
        // 内置源的 type 就是 mangadex
        val md = source(key = "MangaDex", type = "mangadex")
        assertTrue(isComicId("595e3a7a-c762-4f87-90ee-a7dc0dabef91", md))
        assertTrue(isComicId("595E3A7A-C762-4F87-90EE-A7DC0DABEF91", md))
        assertFalse("MangaDex 的数字不是 ID，应走搜索", isComicId("12345", md))
        assertFalse("UUID 形态不对", isComicId("595e3a7a-c762-4f87-90ee", md))
        // 只靠 key 也能推断出来（type 为空时）
        assertTrue(isComicId("595e3a7a-c762-4f87-90ee-a7dc0dabef91", source(key = "MangaDex")))
    }

    @Test
    fun `显式_idType_优先于_key_推断`() {
        // key 看着像 MangaDex，但显式声明 numeric 就该按数字判定
        val s = source(key = "MangaDex", type = "mangadex", idType = "numeric")
        assertTrue(isComicId("12345", s))
        assertFalse(isComicId("595e3a7a-c762-4f87-90ee-a7dc0dabef91", s))
    }

    @Test
    fun `E_Hentai_认_gid_token`() {
        val eh = source(key = "E-Hentai", type = "ehentai")
        assertTrue(isComicId("1234567_abcdef1234", eh))
        assertFalse(isComicId("1234567", eh))
        assertFalse("hex 段长度不对", isComicId("1234567_abc", eh))
    }

    @Test
    fun `拷贝漫画认_slug`() {
        // 用户自己那条源的真实取值：别当哥哥了 → biedangounijiangle
        val cm = source(key = "CopyManga", type = "copymanga")
        assertTrue(isComicId("biedangounijiangle", cm))
        assertFalse("slug 至少 2 字符", isComicId("b", cm))
        assertFalse("含空格", isComicId("bie dang", cm))
    }

    @Test
    fun `string_形态放宽到单字符`() {
        val s = source(key = "S", type = "s", idType = "string")
        assertTrue(isComicId("a", s))
        assertTrue(isComicId("a-b_c", s))
        assertFalse(isComicId("-a", s))
        assertFalse("超 128 字符", isComicId("a".repeat(129), s))
    }

    @Test
    fun `没有源时退化为纯数字判定`() {
        assertTrue(isComicId("12345", null))
        assertFalse(isComicId("abc", null))
    }
}
