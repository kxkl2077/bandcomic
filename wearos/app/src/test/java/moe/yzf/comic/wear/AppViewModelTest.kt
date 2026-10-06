package moe.yzf.comic.wear

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import moe.yzf.comic.wear.data.model.BookEntry
import moe.yzf.comic.wear.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * ViewModel 的状态流接线验证。
 *
 * ViewModel 内部用 `Dispatchers.IO` 读存储、再 `withContext(Dispatchers.Main)` 回写
 * StateFlow，因此这里用「推进主 Looper + 有界等待」的方式观察结果，
 * 而不是假设它同步完成。
 */
@RunWith(AndroidJUnit4::class)
class AppViewModelTest {

    private val app: ComicWearApp get() = ApplicationProvider.getApplicationContext()

    private fun awaitUntil(timeoutMs: Long = 8000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun 初始化后源列表与当前源被填充() {
        val vm = AppViewModel(app.container)
        awaitUntil { vm.sources.value.isNotEmpty() && vm.currentSource.value != null }

        assertEquals(listOf("MangaDex"), vm.sources.value.map { it.key })
        assertNotNull(vm.currentSource.value)
        assertEquals("MangaDex", vm.currentSource.value!!.key)
    }

    @Test
    fun 书架写入后进度可查且可删除() {
        val vm = AppViewModel(app.container)
        awaitUntil { vm.currentSource.value != null }

        val entry = BookEntry(
            id = "comic-1",
            sourceKey = "MangaDex",
            name = "测试漫画",
            pageCount = 20,
            totalChapters = 3,
            chapter = 2,
            page = 5,
            updatedAt = System.currentTimeMillis(),
        )
        vm.upsertShelf(entry)
        awaitUntil { vm.shelf.value.any { it.id == "comic-1" } }

        val progress = vm.progressOf("comic-1", "MangaDex")
        assertNotNull(progress)
        assertEquals(2, progress!!.chapter)
        assertEquals(5, progress.page)

        // 换一个源 key 应查不到（进度是按源隔离的）。
        assertNull(vm.progressOf("comic-1", "Other"))

        vm.removeFromShelf("comic-1", "MangaDex")
        awaitUntil { vm.shelf.value.none { it.id == "comic-1" } }
        assertTrue(vm.shelf.value.isEmpty())
    }

    @Test
    fun 搜索历史记录与清空() {
        val vm = AppViewModel(app.container)
        vm.recordSearch("naruto")
        awaitUntil { vm.searchHistory.value.contains("naruto") }
        assertTrue(vm.searchHistory.value.contains("naruto"))

        vm.clearSearchHistory()
        awaitUntil { vm.searchHistory.value.isEmpty() }
        assertTrue(vm.searchHistory.value.isEmpty())
    }

    @Test
    fun 设置更新会反映到状态流() {
        val vm = AppViewModel(app.container)
        vm.updateSettings { it.copy(imageSize = 800) }
        awaitUntil { vm.settings.value.imageSize == 800 }
        assertEquals(800, vm.settings.value.imageSize)

        // 还原，避免影响其它用例。
        vm.updateSettings { it.copy(imageSize = 480) }
        awaitUntil { vm.settings.value.imageSize == 480 }
    }

    @Test
    fun 切换源会更新当前源() {
        val vm = AppViewModel(app.container)
        awaitUntil { vm.currentSource.value != null }

        vm.setUsingSource("MangaDex")
        awaitUntil { vm.currentSource.value?.key == "MangaDex" }
        assertEquals("MangaDex", vm.currentSource.value!!.key)
    }

    @Test
    fun 疑似漫画ID的输入判断跟随当前源的_idType() {
        val repo = app.container.repository
        // 默认源是内置 MangaDex，其 ID 形态是 UUID（上游 sourceConfig.js 的 ID_TYPES 规则）：
        // 纯数字不再被当成 ID，否则会拿着 12345 去请求一个必然 404 的详情。
        assertTrue(!repo.looksLikeComicId("12345"))
        assertTrue(!repo.looksLikeComicId("1"))
        assertTrue(!repo.looksLikeComicId("12a"))
        assertTrue(!repo.looksLikeComicId(""))
        // MangaDex 的 UUID 才是真 ID，此前会被错当成关键词丢去搜索。
        assertTrue(repo.looksLikeComicId("595e3a7a-c762-4f87-90ee-a7dc0dabef91"))
    }
}
