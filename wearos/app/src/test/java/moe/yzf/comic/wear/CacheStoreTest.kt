package moe.yzf.comic.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import moe.yzf.comic.wear.data.model.CachedChapter
import moe.yzf.comic.wear.data.model.CachedComic
import moe.yzf.comic.wear.data.store.CacheStore
import moe.yzf.comic.wear.data.store.JsonFileStore
import moe.yzf.comic.wear.data.store.rebuildChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 本地缓存层的真实文件读写验证。
 *
 * 每个用例用全新的临时根目录，所以「新实例读到旧实例的数据」才真正证明落盘，
 * 而不是内存里自洽。目录布局的断言直接对着原版约定的路径写死。
 */
@RunWith(AndroidJUnit4::class)
class CacheStoreTest {

    private class Fixture {
        private val context: Context get() = ApplicationProvider.getApplicationContext()

        val indexDir: File = File(context.filesDir, "cache-index-${System.nanoTime()}")
        val root: File = File(context.filesDir, "cache-root-${System.nanoTime()}")
        val store = CacheStore(JsonFileStore(indexDir), root)

        fun comic(
            id: String = "100",
            sourceKey: String = "MangaDex",
            chapters: List<CachedChapter> = emptyList(),
            totalChapters: Int = 1,
        ) = CachedComic(
            id = id,
            sourceKey = sourceKey,
            name = "测试漫画",
            cover = "https://example.com/cover.jpg",
            totalChapters = totalChapters,
            chapters = chapters,
            size = 0L,
            updatedAt = 1L,
        )

        /** 造出 [count] 个页文件，内容各不相同，便于验证没有串页。 */
        fun writePages(id: String, sourceKey: String, chapter: Int, count: Int) {
            (1..count).forEach { page ->
                val f = store.pageFile(sourceKey, id, chapter, page)
                f.parentFile?.mkdirs()
                f.writeText("page-$chapter-$page")
            }
        }
    }

    // ---- 目录布局 ----

    @Test
    fun `目录名按源与漫画id拼接并替换非法字符`() {
        val fx = Fixture()
        assertEquals("MangaDex_100", fx.store.dirName("MangaDex", "100"))
        // 斜杠、冒号等不能进文件名
        assertEquals("src_a_b_c", fx.store.dirName("src", "a/b:c"))
        // 路径拼接真的落在 root 下
        val cover = fx.store.coverFile("MangaDex", "100")
        assertEquals("cover", cover.name)
        assertEquals("MangaDex_100", cover.parentFile?.name)
        assertEquals(fx.root, cover.parentFile?.parentFile)
    }

    @Test
    fun `页文件路径为_漫画目录_章号_页码`() {
        val fx = Fixture()
        val f = fx.store.pageFile("MangaDex", "100", chapter = 3, page = 7)
        assertEquals("7", f.name)
        assertEquals("3", f.parentFile?.name)
        assertEquals("MangaDex_100", f.parentFile?.parentFile?.name)
    }

    // ---- 索引持久化 ----

    @Test
    fun `缓存索引跨实例持久化并含章节明细`() {
        val fx = Fixture()
        fx.store.upsert(
            fx.comic(
                chapters = listOf(
                    CachedChapter(num = 1, name = "第一话", pageCount = 20, downloaded = 20),
                    CachedChapter(num = 2, name = "第二话", pageCount = 18, downloaded = 5),
                ),
                totalChapters = 2,
            ),
        )

        // 全新实例（模拟进程重启）应读到同样内容
        val reopened = CacheStore(JsonFileStore(fx.indexDir), fx.root)
        val comic = reopened.find("100", "MangaDex")
        assertEquals("测试漫画", comic?.name)
        assertEquals(2, comic?.chapters?.size)
        assertEquals("第一话", comic?.chapters?.first()?.name)
        assertEquals(20, comic?.chapters?.first()?.downloaded)
        assertEquals(5, comic?.chapters?.get(1)?.downloaded)
    }

    @Test
    fun `同一漫画只在索引里出现一次`() {
        val fx = Fixture()
        fx.store.upsert(fx.comic())
        fx.store.upsert(fx.comic().copy(name = "改名了"))
        assertEquals(1, fx.store.list().size)
        assertEquals("改名了", fx.store.find("100", "MangaDex")?.name)
    }

    @Test
    fun `不同源的同一个漫画id互不覆盖`() {
        val fx = Fixture()
        fx.store.upsert(fx.comic(sourceKey = "MangaDex").copy(name = "A"))
        fx.store.upsert(fx.comic(sourceKey = "CopyManga").copy(name = "B"))
        assertEquals(2, fx.store.list().size)
        assertEquals("A", fx.store.find("100", "MangaDex")?.name)
        assertEquals("B", fx.store.find("100", "CopyManga")?.name)
    }

    @Test
    fun `列表按最近更新倒序`() {
        val fx = Fixture()
        fx.store.upsert(fx.comic(id = "old").copy(updatedAt = 100))
        fx.store.upsert(fx.comic(id = "new").copy(updatedAt = 300))
        fx.store.upsert(fx.comic(id = "mid").copy(updatedAt = 200))
        assertEquals(listOf("new", "mid", "old"), fx.store.list().map { it.id })
    }

    // ---- update（读-改-写）----

    @Test
    fun `update_在缺记录时不新建`() {
        val fx = Fixture()
        val result = fx.store.update("nope", "MangaDex") { null }
        assertNull(result)
        assertTrue(fx.store.list().isEmpty())
    }

    @Test
    fun `update_能就地追回章节并落盘`() {
        val fx = Fixture()
        fx.store.upsert(fx.comic())
        fx.store.update("100", "MangaDex") { prev ->
            prev!!.copy(chapters = listOf(CachedChapter(num = 1, pageCount = 10, downloaded = 10)))
        }
        val reopened = CacheStore(JsonFileStore(fx.indexDir), fx.root)
        assertEquals(10, reopened.find("100", "MangaDex")?.chapters?.first()?.downloaded)
    }

    // ---- findAny ----

    @Test
    fun `findAny_优先命中当前源_否则退到最近更新的一条`() {
        val fx = Fixture()
        fx.store.upsert(fx.comic(sourceKey = "MangaDex").copy(name = "MD", updatedAt = 10))
        fx.store.upsert(fx.comic(sourceKey = "CopyManga").copy(name = "CM", updatedAt = 20))

        // 指定源存在就用它
        assertEquals("MD", fx.store.findAny("100", "MangaDex")?.name)
        // 指定源已不在：退到最近更新的一条，而不是返回 null
        assertEquals("CM", fx.store.findAny("100", "Other")?.name)
        // 传入 null 同样可用
        assertEquals("CM", fx.store.findAny("100", null)?.name)
        // 完全没缓存过
        assertNull(fx.store.findAny("999", "MangaDex"))
    }

    // ---- 磁盘统计与删除 ----

    @Test
    fun `existingPages_只认纯数字文件名并按页码升序`() {
        val fx = Fixture()
        fx.writePages("100", "MangaDex", chapter = 1, count = 12)
        // 目录里混进非页文件：不应被当成页
        fx.store.chapterDir("MangaDex", "100", 1).resolve("cover.tmp").writeText("x")
        fx.store.chapterDir("MangaDex", "100", 1).resolve("notes").writeText("x")

        val pages = fx.store.existingPages("MangaDex", "100", 1)
        assertEquals(12, pages.size)
        // 按数值升序：字典序会把 10/11/12 排到 2 前面，这里必须不是
        assertEquals((1..12).map { it.toString() }, pages.map { it.name })
        assertTrue(pages.none { it.name == "cover.tmp" || it.name == "notes" })
    }

    @Test
    fun `existingPages_在目录不存在时返回空表`() {
        val fx = Fixture()
        assertTrue(fx.store.existingPages("MangaDex", "404", 1).isEmpty())
    }

    @Test
    fun `directorySize_递归统计并忽略不存在的目录`() {
        val fx = Fixture()
        assertEquals(0L, fx.store.directorySize(File(fx.root, "nothing")))
        fx.writePages("100", "MangaDex", chapter = 1, count = 3) // 每页 "page-1-N" = 8 字节
        fx.store.coverFile("MangaDex", "100").writeText("12345")
        assertEquals(3 * 8L + 5L, fx.store.directorySize(fx.store.comicDir("MangaDex", "100")))
    }

    @Test
    fun `remove_返回被删记录并删除整个漫画目录`() {
        val fx = Fixture()
        fx.store.upsert(fx.comic())
        fx.writePages("100", "MangaDex", chapter = 1, count = 4)
        val dir = fx.store.comicDir("MangaDex", "100")
        assertTrue(dir.exists())

        val removed = fx.store.remove("100", "MangaDex")
        assertEquals("测试漫画", removed?.name)
        assertNull(fx.store.find("100", "MangaDex"))
        assertTrue(fx.store.deleteComicFiles("MangaDex", "100"))
        assertFalse("目录应被真删掉", dir.exists())
    }

    @Test
    fun `deleteChapterFiles_只删指定章节`() {
        val fx = Fixture()
        fx.writePages("100", "MangaDex", chapter = 1, count = 2)
        fx.writePages("100", "MangaDex", chapter = 2, count = 2)

        assertTrue(fx.store.deleteChapterFiles("MangaDex", "100", 1))
        assertFalse(fx.store.chapterDir("MangaDex", "100", 1).exists())
        assertTrue("别的章不能被牵连", fx.store.chapterDir("MangaDex", "100", 2).exists())
    }

    // ---- 章节状态语义 ----

    @Test
    fun `章节完整与部分的判定`() {
        // 下满
        assertTrue(CachedChapter(num = 1, pageCount = 20, downloaded = 20).complete)
        assertFalse(CachedChapter(num = 1, pageCount = 20, downloaded = 20).partial)
        // 下了一半
        assertFalse(CachedChapter(num = 1, pageCount = 20, downloaded = 5).complete)
        assertTrue(CachedChapter(num = 1, pageCount = 20, downloaded = 5).partial)
        // 一页都没有：既不算完整也不算部分（下载页据此显示为未缓存）
        assertFalse(CachedChapter(num = 1, pageCount = 20, downloaded = 0).complete)
        assertFalse(CachedChapter(num = 1, pageCount = 20, downloaded = 0).partial)
        // 旧数据没有登记页数：有页即视为完整
        assertTrue(CachedChapter(num = 1, pageCount = 0, downloaded = 3).complete)
    }

    @Test
    fun `漫画未下完的判定_含连载缺章`() {
        val done = CachedChapter(num = 1, pageCount = 10, downloaded = 10)
        val half = CachedChapter(num = 2, pageCount = 10, downloaded = 4)

        // 单行本、已下完
        assertFalse(CachedComic("1", "S", "A", chapters = listOf(done), totalChapters = 1).incomplete)
        // 单行本、只下了一半
        assertTrue(CachedComic("1", "S", "A", chapters = listOf(half), totalChapters = 1).incomplete)
        // 连载 5 章但只缓存了 2 章：即使两章都下满也算未下完
        assertTrue(
            CachedComic(
                "1", "S", "A",
                chapters = listOf(done.copy(num = 1), done.copy(num = 2)),
                totalChapters = 5,
            ).incomplete,
        )
        // 连载全下完
        assertFalse(
            CachedComic(
                "1", "S", "A",
                chapters = (1..5).map { done.copy(num = it) },
                totalChapters = 5,
            ).incomplete,
        )
        // 没有任何章节：不标未完成，避免空记录刷红点
        assertFalse(CachedComic("1", "S", "A", chapters = emptyList()).incomplete)
    }

    @Test
    fun `可离线阅读的章节号集合只含真的有页的章`() {
        val comic = CachedComic(
            "1", "S", "A",
            chapters = listOf(
                CachedChapter(num = 1, pageCount = 10, downloaded = 10),
                CachedChapter(num = 2, pageCount = 10, downloaded = 0),
                CachedChapter(num = 3, pageCount = 10, downloaded = 3),
            ),
        )
        assertEquals(setOf(1, 3), comic.downloadedChapterNums())
    }

    @Test
    fun `rebuildChapter_以磁盘真实页数为准`() {
        val fx = Fixture()
        val prev = CachedChapter(num = 1, name = "第一话", pageCount = 20, downloaded = 20)
        fx.writePages("100", "MangaDex", chapter = 1, count = 6)
        val pages = fx.store.existingPages("MangaDex", "100", 1)

        val rebuilt = rebuildChapter(prev, 1, pages)
        // 名字与登记页数沿用旧记录，落盘数按磁盘实测
        assertEquals("第一话", rebuilt.name)
        assertEquals(20, rebuilt.pageCount)
        assertEquals(6, rebuilt.downloaded)
        assertTrue(rebuilt.partial)
    }

    // ---- 展示 ----

    @Test
    fun `大小格式化覆盖各量级`() {
        assertEquals("512B", CacheStore.displaySize(512))
        assertEquals("1.00KB", CacheStore.displaySize(1024))
        assertEquals("1.00MB", CacheStore.displaySize(1024L * 1024))
        assertEquals("1.00GB", CacheStore.displaySize(1024L * 1024 * 1024))
    }
}
