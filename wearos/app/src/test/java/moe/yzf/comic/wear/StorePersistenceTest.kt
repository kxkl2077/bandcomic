package moe.yzf.comic.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import moe.yzf.comic.wear.data.model.BookEntry
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.store.JsonFileStore
import moe.yzf.comic.wear.data.store.LibraryStore
import moe.yzf.comic.wear.data.store.SearchHistoryPayload
import moe.yzf.comic.wear.data.store.SearchHistoryStore
import moe.yzf.comic.wear.data.store.SourceStore
import moe.yzf.comic.wear.data.store.SourcesPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 持久化层的真实文件读写验证。
 *
 * 每个用例都用一个全新的临时目录，因此「新建实例能读到上一个实例写的数据」这条
 * 才真正证明落盘成功，而不是内存里自洽。
 */
@RunWith(AndroidJUnit4::class)
class StorePersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun tempDir(): File {
        val dir = File(context.filesDir, "test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun customSource(key: String = "Demo") = ComicSource(
        key = key,
        name = "演示源",
        apiUrl = "https://demo.example",
        detailPath = "/comic/<id>",
        photoPath = "/photo/<id>/ch/<chapter>",
        searchPath = "/search/<text>/<page>",
        type = "custom",
    )

    // ---- SourceStore ----

    @Test
    fun 源与选择跨实例持久化() {
        val dir = tempDir()
        val first = SourceStore(JsonFileStore(dir))
        first.ensureLoaded()
        // 首次加载应植入内置源。
        assertEquals(listOf("MangaDex"), first.list().map { it.key })

        first.upsert(listOf(customSource()))
        first.setUsing("Demo")
        assertEquals("Demo", first.currentKey())

        // 全新实例（模拟进程重启）应读到同样的内容。
        val second = SourceStore(JsonFileStore(dir))
        assertEquals(setOf("MangaDex", "Demo"), second.list().map { it.key }.toSet())
        assertEquals("Demo", second.currentKey())
        assertEquals("https://demo.example", second.current()?.apiUrl)
    }

    @Test
    fun 删除当前源后_using_不会悬空且内置源兜底() {
        val dir = tempDir()
        val store = SourceStore(JsonFileStore(dir))
        store.ensureLoaded()
        store.upsert(listOf(customSource()))
        store.setUsing("Demo")
        assertEquals("Demo", store.currentKey())

        // 删掉正在使用的那个源：using 必须被修正到仍然存在的源上。
        store.remove("Demo")
        assertEquals(listOf("MangaDex"), store.list().map { it.key })
        assertEquals("MangaDex", store.currentKey())
        assertNotNull(store.current())

        // 把内置源也删光：必须自动恢复内置源，绝不留空集合。
        store.remove("MangaDex")
        assertTrue(store.list().isNotEmpty())
        assertNotNull(store.current())
        assertEquals("MangaDex", store.currentKey())
    }

    @Test
    fun Cookie_按源隔离并可清除() {
        val dir = tempDir()
        val store = SourceStore(JsonFileStore(dir))
        store.setCookie("MangaDex", "sid=abc")
        store.setCookie("Other", "sid=xyz")
        assertEquals("sid=abc", store.cookie("MangaDex"))
        assertEquals("sid=xyz", store.cookie("Other"))

        // 空值等于删除。
        store.setCookie("MangaDex", "")
        assertNull(store.cookie("MangaDex"))

        // 跨实例仍然保留。
        val reopened = SourceStore(JsonFileStore(dir))
        assertEquals("sid=xyz", reopened.cookie("Other"))
        assertNull(reopened.cookie("MangaDex"))
    }

    @Test
    fun 删除源会连带清掉它的_Cookie() {
        val dir = tempDir()
        val store = SourceStore(JsonFileStore(dir))
        store.ensureLoaded()
        store.upsert(listOf(customSource()))
        store.setCookie("Demo", "sid=1")
        assertEquals("sid=1", store.cookie("Demo"))

        store.remove("Demo")
        assertNull("源被删除后不应残留 Cookie", store.cookie("Demo"))
    }

    // ---- LibraryStore ----

    @Test
    fun `书架按 id 与源去重并按最近阅读倒序`() {
        val dir = tempDir()
        val store = LibraryStore(JsonFileStore(dir))

        store.upsert(BookEntry(id = "a", sourceKey = "S", name = "A", updatedAt = 100))
        store.upsert(BookEntry(id = "b", sourceKey = "S", name = "B", updatedAt = 200))
        // 同 id 同源再写一次：应更新而不是新增。
        store.upsert(BookEntry(id = "a", sourceKey = "S", name = "A2", updatedAt = 300, page = 7))

        val list = store.list()
        assertEquals(2, list.size)
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals("A2", list.first().name)
        assertEquals(7, list.first().page)

        // 同 id 但不同源：视为两条独立记录。
        store.upsert(BookEntry(id = "a", sourceKey = "T", name = "A@T", updatedAt = 50))
        assertEquals(3, store.list().size)

        // 跨实例持久化。
        val reopened = LibraryStore(JsonFileStore(dir))
        assertEquals(3, reopened.list().size)
        assertEquals(7, reopened.find("a", "S")?.page)
    }

    @Test
    fun 书架删除与清空() {
        val dir = tempDir()
        val store = LibraryStore(JsonFileStore(dir))
        store.upsert(BookEntry(id = "a", sourceKey = "S", name = "A"))
        store.upsert(BookEntry(id = "b", sourceKey = "S", name = "B"))

        store.remove("a", "S")
        assertEquals(listOf("b"), store.list().map { it.id })
        assertNull(store.find("a", "S"))

        store.clear()
        assertTrue(store.list().isEmpty())
        // 清空也要落盘。
        assertTrue(LibraryStore(JsonFileStore(dir)).list().isEmpty())
    }

    // ---- SearchHistoryStore ----

    @Test
    fun 搜索历史去重置顶且上限十条() {
        val dir = tempDir()
        val store = SearchHistoryStore(JsonFileStore(dir))

        store.add("one")
        store.add("two")
        store.add("one") // 重复：应置顶而不是产生第二条
        assertEquals(listOf("one", "two"), store.list())

        // 空白关键词不入库。
        store.add("   ")
        assertEquals(listOf("one", "two"), store.list())

        // 前后空格应被裁掉。
        store.add("  three  ")
        assertEquals(listOf("three", "one", "two"), store.list())

        // 上限 10：写 15 个不同词，只保留最近 10 个。
        (1..15).forEach { store.add("k$it") }
        val list = store.list()
        assertEquals(10, list.size)
        assertEquals("k15", list.first())

        // 跨实例持久化。
        assertEquals(list, SearchHistoryStore(JsonFileStore(dir)).list())

        store.clear()
        assertTrue(store.list().isEmpty())
    }

    // ---- JsonFileStore 的原子写与损坏自愈 ----

    @Test
    fun 写入不留下_tmp_残留文件() {
        val dir = tempDir()
        val files = JsonFileStore(dir)
        files.write("x.json", SearchHistoryPayload.serializer(), SearchHistoryPayload(listOf("a")))

        val names = dir.listFiles()!!.map { it.name }
        assertEquals(listOf("x.json"), names)
        assertFalse("不应残留 .tmp", names.any { it.endsWith(".tmp") })
    }

    @Test
    fun 文件损坏时备份原文并回落默认值() {
        val dir = tempDir()
        // 先制造一个正常文件，再写坏它。
        val files = JsonFileStore(dir)
        files.write("sources.json", SourcesPayload.serializer(), SourcesPayload())
        File(dir, "sources.json").writeText("{ this is not json ")

        val recovered = mutableListOf<String>()
        val store = SourceStore(JsonFileStore(dir), onRecovered = { recovered.add(it) })
        store.ensureLoaded()

        // 1) 回调收到通知（Application 用它打日志）。
        assertTrue("应通知文件已恢复，实际 $recovered", recovered.contains("sources.json"))
        // 2) 损坏原文被改名备份，没有直接丢数据。
        val backups = dir.listFiles()!!.filter { it.name.startsWith("sources.json.corrupt-") }
        assertEquals("应留下 1 个备份文件", 1, backups.size)
        assertTrue(backups.first().readText().contains("not json"))
        // 3) 回落并由 normalize 恢复内置源。
        assertEquals(listOf("MangaDex"), store.list().map { it.key })
        // 4) 修复后的内容已重新落盘。
        assertTrue(File(dir, "sources.json").readText().contains("MangaDex"))
    }

    @Test
    fun 空白文件按不存在处理() {
        val dir = tempDir()
        File(dir, "library.json").writeText("   ")
        val store = LibraryStore(JsonFileStore(dir))
        assertTrue(store.list().isEmpty())
        // 空白文件不算损坏，不应产生备份。
        assertTrue(dir.listFiles()!!.none { it.name.contains("corrupt") })
    }
}
