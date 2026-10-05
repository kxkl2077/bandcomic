package moe.yzf.comic.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.yzf.comic.wear.data.download.DownloadRequest
import moe.yzf.comic.wear.data.download.DownloadState
import moe.yzf.comic.wear.data.download.Downloader
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.net.ComicApi
import moe.yzf.comic.wear.data.store.CacheStore
import moe.yzf.comic.wear.data.store.JsonFileStore
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 下载器的端到端验证：真起一个本地 HTTP 服务，真的写文件。
 *
 * 覆盖的都是容易出错的地方——断点续传（不重复拉已存在的页）、
 * 单页重试、确定性 4xx 不重试、整章失败不牵连其它章、取消后已落盘的页保留。
 */
@RunWith(AndroidJUnit4::class)
class DownloaderTest {

    private lateinit var server: TestHttpServer
    private lateinit var api: ComicApi
    private lateinit var cache: CacheStore
    private lateinit var downloader: Downloader
    private lateinit var indexDir: File
    private lateinit var root: File

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val source get() = ComicSource(
        key = "Test",
        name = "测试源",
        apiUrl = "http://127.0.0.1:${server.port}",
        detailPath = "/comic/<id>",
        photoPath = "/photo/<id>/ch/<chapter>",
        searchPath = "/search/<text>/<page>",
        type = "test",
    )

    /** 一页的图片内容；带上页码方便验证没有串页。 */
    private fun pageBytes(page: Int) = "IMG-$page".toByteArray()

    @Before
    fun setUp() {
        server = TestHttpServer()
        server.start()

        indexDir = File(context.filesDir, "dl-index-${System.nanoTime()}")
        root = File(context.filesDir, "dl-root-${System.nanoTime()}")
        cache = CacheStore(JsonFileStore(indexDir), root)

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        api = ComicApi(
            client = client,
            userAgent = { "test-agent" },
            cookie = { null },
            json = JsonFileStore(indexDir).json,
        )
        // 重试间隔设为 0，避免用例被 sleep 拖慢
        downloader = Downloader(api, cache, retryDelayMs = 0L)
    }

    @After
    fun tearDown() {
        server.stop()
        indexDir.deleteRecursively()
        root.deleteRecursively()
    }

    /** 让 /photo/comic1/ch/N 返回一篇 [pages] 页的图片列表。 */
    private fun serveChapter(id: String, chapter: Int, pages: Int) {
        // 协议里 images 是对象数组（每项带 url），不是裸字符串数组
        val urls = (1..pages).joinToString(",") {
            """{"url":"http://127.0.0.1:${server.port}/img/$it"}"""
        }
        server.json("/photo/$id/ch/$chapter", """{"title":"第 $chapter 章","images":[$urls]}""")
        (1..pages).forEach { server.png("/img/$it", pageBytes(it)) }
    }

    private fun request(
        id: String = "comic1",
        chapters: List<Int> = listOf(1),
        total: Int = 1,
        cover: String = "",
    ) = DownloadRequest(
        comicId = id,
        source = source,
        comicName = "测试漫画",
        cover = cover,
        totalChapters = total,
        chapters = chapters,
    )

    private fun download(req: DownloadRequest, size: Int = 300, quality: Int = 40, png: Boolean = false) =
        runBlocking { downloader.run(req, size, quality, png) }

    // ---- 正常路径 ----

    @Test
    fun `整章下载_页文件按章号页码落盘且索引被回写`() {
        serveChapter("comic1", 1, 5)

        download(request())

        // 文件真的在约定的路径上，且内容没有串页
        (1..5).forEach { page ->
            val f = cache.pageFile("Test", "comic1", 1, page)
            assertTrue("第 $page 页应存在", f.exists())
            assertEquals("IMG-$page", f.readText())
        }

        val comic = cache.find("comic1", "Test")
        assertNotNull(comic)
        val chapter = comic!!.chapters.first { it.num == 1 }
        assertEquals(5, chapter.pageCount)
        assertEquals(5, chapter.downloaded)
        assertTrue(chapter.complete)
        assertEquals("第 1 章", chapter.name)
        // 真实占用被统计出来
        assertTrue("size 应大于 0", comic.size > 0)
        // 不残留临时文件
        assertTrue(
            "不应留下 .tmp",
            cache.chapterDir("Test", "comic1", 1).listFiles()!!.none { it.name.endsWith(".tmp") },
        )
    }

    @Test
    fun `多章下载_每章各自成目录并全部登记`() {
        serveChapter("comic1", 1, 3)
        serveChapter("comic1", 2, 4)
        serveChapter("comic1", 3, 2)

        val state = runBlocking {
            downloader.run(request(chapters = listOf(1, 2, 3), total = 3), 300, 40, false)
            downloader.state.value
        }

        val finished = state as DownloadState.Finished
        assertEquals(3, finished.success)
        assertEquals(0, finished.total - finished.success)
        assertTrue(finished.failed.isEmpty())

        val chapters = cache.find("comic1", "Test")!!.chapters.associateBy { it.num }
        assertEquals(3, chapters.size)
        assertEquals(3, chapters[1]!!.downloaded)
        assertEquals(4, chapters[2]!!.downloaded)
        assertEquals(2, chapters[3]!!.downloaded)
        assertTrue(chapters.values.all { it.complete })
    }

    @Test
    fun `封面同时被缓存下来`() {
        serveChapter("comic1", 1, 2)
        server.png("/cover.jpg", "COVER".toByteArray())

        download(request(cover = "http://127.0.0.1:${server.port}/cover.jpg"))

        val cover = cache.coverFile("Test", "comic1")
        assertTrue(cover.exists())
        assertEquals("COVER", cover.readText())
    }

    // ---- 断点续传 ----

    @Test
    fun `已存在的页不会被重复请求`() {
        serveChapter("comic1", 1, 4)
        // 预置第 1、3 页（模拟上次下载中断）
        listOf(1, 3).forEach { page ->
            val f = cache.pageFile("Test", "comic1", 1, page)
            f.parentFile?.mkdirs()
            f.writeText("IMG-$page")
        }

        download(request())

        // 只应请求缺失的第 2、4 页，且各请求一次（都是首次即成功）
        assertEquals(0, server.hitCount("/img/1"))
        assertEquals(1, server.hitCount("/img/2"))
        assertEquals(0, server.hitCount("/img/3"))
        assertEquals(1, server.hitCount("/img/4"))

        val chapter = cache.find("comic1", "Test")!!.chapters.first()
        assertEquals(4, chapter.downloaded)
        assertTrue(chapter.complete)
    }

    @Test
    fun `整章都已存在时一次图片请求都不发`() {
        serveChapter("comic1", 1, 3)
        (1..3).forEach { page ->
            val f = cache.pageFile("Test", "comic1", 1, page)
            f.parentFile?.mkdirs()
            f.writeText("IMG-$page")
        }

        download(request())

        assertEquals(0, server.hitCount("/img/1"))
        assertEquals(0, server.hitCount("/img/2"))
        assertEquals(0, server.hitCount("/img/3"))
        // 图片列表接口仍然要问一次，否则不知道这一章共几页
        assertEquals(1, server.hitCount("/photo/comic1/ch/1"))
        assertTrue(cache.find("comic1", "Test")!!.chapters.first().complete)
    }

    // ---- 重试语义 ----

    @Test
    fun `单页先失败后成功会被重试补回来`() {
        serveChapter("comic1", 1, 3)
        var attempts = 0
        server.on("/img/2") {
            attempts++
            if (attempts == 1) {
                TestHttpServer.Response(500, "text/plain", "boom".toByteArray())
            } else {
                TestHttpServer.Response(200, "image/png", pageBytes(2))
            }
        }

        download(request())

        assertEquals(2, attempts)
        assertEquals("IMG-2", cache.pageFile("Test", "comic1", 1, 2).readText())
        assertTrue(cache.find("comic1", "Test")!!.chapters.first().complete)
    }

    @Test
    fun `确定性_404_不重试_该章标记为未下完`() {
        serveChapter("comic1", 1, 3)
        server.on("/img/2") { TestHttpServer.Response(404, "text/plain", "nope".toByteArray()) }

        download(request())

        // 4xx 是确定性错误：只请求一次（初次），补下载那次也不再试
        assertEquals(2, server.hitCount("/img/2"))
        val chapter = cache.find("comic1", "Test")!!.chapters.first()
        assertEquals(2, chapter.downloaded)
        assertEquals(3, chapter.pageCount)
        assertTrue("少一页就不算完整", !chapter.complete)
        assertTrue(chapter.partial)
        // 其它页照常下好
        assertTrue(cache.pageFile("Test", "comic1", 1, 1).exists())
        assertTrue(cache.pageFile("Test", "comic1", 1, 3).exists())
    }

    @Test
    fun `持续性_5xx_会重试到上限`() {
        serveChapter("comic1", 1, 1)
        server.on("/img/1") { TestHttpServer.Response(503, "text/plain", "down".toByteArray()) }

        download(request())

        // 初次 + 3 次重试 = 4，随后补下载再走一轮 = 4，共 8 次
        assertEquals(8, server.hitCount("/img/1"))
        val chapter = cache.find("comic1", "Test")!!.chapters.first()
        assertEquals(0, chapter.downloaded)
        assertFalse(chapter.complete)
    }

    // ---- 整章失败 ----

    @Test
    fun `图片列表拿不到时该章整章失败`() {
        // 不给 /photo 注册路由 → 404
        val state = runBlocking {
            downloader.run(request(), 300, 40, false)
            downloader.state.value
        }

        val finished = state as DownloadState.Finished
        assertEquals(0, finished.success)
        assertEquals(1, finished.total)
        assertEquals(listOf(1), finished.failed)
        // 索引里仍然登记了这本漫画（对应原版 ensureComicRegistered）
        assertNotNull(cache.find("comic1", "Test"))
    }

    @Test
    fun `一章失败不影响后续章节继续下载`() {
        serveChapter("comic1", 2, 2)
        // 第 1 章没有路由 → 404
        val state = runBlocking {
            downloader.run(request(chapters = listOf(1, 2), total = 2), 300, 40, false)
            downloader.state.value
        }

        val finished = state as DownloadState.Finished
        assertEquals(1, finished.success)
        assertEquals(2, finished.total)
        assertEquals(listOf(1), finished.failed)
        // 第 2 章照常下好
        assertEquals(2, cache.find("comic1", "Test")!!.chapters.first { it.num == 2 }.downloaded)
    }

    @Test
    fun `全部失败时_failed_列出所有章`() {
        val state = runBlocking {
            downloader.run(request(chapters = listOf(1, 2), total = 2), 300, 40, false)
            downloader.state.value
        }

        val finished = state as DownloadState.Finished
        assertEquals(0, finished.success)
        assertEquals(listOf(1, 2), finished.failed)
    }

    // ---- 进度 ----

    @Test
    fun `进度状态反映当前章与页数`() {
        serveChapter("comic1", 1, 3)
        serveChapter("comic1", 2, 2)

        val seen = mutableListOf<DownloadState.Running>()
        runBlocking {
            val job: Job = launch(Dispatchers.Default) {
                downloader.state.collect { if (it is DownloadState.Running) seen.add(it) }
            }
            downloader.run(request(chapters = listOf(1, 2), total = 2), 300, 40, false)
            job.cancel()
        }

        assertTrue("应至少推送过一次进度", seen.isNotEmpty())
        // 全部进度都属于这两章，且页数不超过该章总页数
        assertTrue(seen.all { it.chapter in listOf(1, 2) })
        assertTrue(seen.all { it.page <= it.pageCount })
        assertTrue(seen.all { it.queueTotal == 2 })
        // 第二章的进度必须出现，说明是串行推进而不是只下第一章
        assertTrue("应推进到第 2 章", seen.any { it.chapter == 2 })
        // queueIndex 是 1 起的，第二章即第 2 个
        assertEquals(2, seen.first { it.chapter == 2 }.queueIndex)
    }

    // ---- 取消 ----

    @Test
    fun `取消后已落盘的页保留且状态为已取消`() {
        serveChapter("comic1", 1, 5)
        // 第 2 页拖慢一点，给取消留出时间窗（阻塞的 execute() 无法被取消打断，
        // 取消只在页边界生效，所以这里等这一页自然返回）
        server.on("/img/2") {
            Thread.sleep(1200)
            TestHttpServer.Response(200, "image/png", pageBytes(2))
        }

        runBlocking {
            val job = Job()
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + job)
            scope.launch { downloader.run(request(), 300, 40, false) }

            // 等第 1 页真的落盘
            withTimeout(10_000) {
                while (!cache.pageFile("Test", "comic1", 1, 1).exists()) delay(20)
            }
            job.cancel()

            withTimeout(20_000) {
                while (downloader.state.value !is DownloadState.Cancelled) delay(20)
            }
        }

        assertTrue("取消前下好的页应保留", cache.pageFile("Test", "comic1", 1, 1).exists())
        assertEquals(DownloadState.Cancelled, downloader.state.value)
        // 索引反映真实落盘页数，而不是「已完成 5 页」
        val chapter = cache.find("comic1", "Test")!!.chapters.first()
        assertTrue("取消后不应把 5 页都算成已下载", chapter.downloaded < 5)
        assertTrue(chapter.partial)
    }
}
