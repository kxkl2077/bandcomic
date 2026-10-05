package moe.yzf.comic.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import moe.yzf.comic.wear.data.store.AppSettings
import moe.yzf.comic.wear.data.store.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 设置项的真实 DataStore 读写验证，含越界值钳制。 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    @After
    fun restoreDefaults() = runBlocking {
        // DataStore 是进程级单例，把状态还原以免影响其它用例。
        store.update { AppSettings() }
    }

    @Test
    fun 更新后可读回并跨实例保持() = runBlocking {
        store.update {
            it.copy(
                imageSize = 640,
                imageQuality = 80,
                showCoverInSearch = false,
                imageUsePng = true,
                preload = false,
                keepDefaultZoom = true,
                searchPageSize = 20,
            )
        }

        val read = store.snapshot()
        assertEquals(640, read.imageSize)
        assertEquals(80, read.imageQuality)
        assertFalse(read.showCoverInSearch)
        assertTrue(read.imageUsePng)
        assertFalse(read.preload)
        assertTrue(read.keepDefaultZoom)
        assertEquals(20, read.searchPageSize)
    }

    @Test
    fun 越界值被钳制到合法区间() {
        val low = AppSettings(imageSize = 1, imageQuality = 0, searchPageSize = 0)
        assertEquals(80, low.safeImageSize)
        assertEquals(1, low.safeQuality)
        assertEquals(1, low.safePageSize)

        val high = AppSettings(imageSize = 99999, imageQuality = 999, searchPageSize = 999)
        assertEquals(1440, high.safeImageSize)
        assertEquals(100, high.safeQuality)
        assertEquals(50, high.safePageSize)
    }

    @Test
    fun 默认值与快应用版对齐() {
        val s = AppSettings()
        assertEquals(480, s.imageSize)
        assertEquals(50, s.imageQuality)
        assertTrue(s.showCoverInSearch)
        assertFalse(s.keepDefaultZoom)
        assertFalse(s.imageUsePng)
        assertTrue(s.preload)
        assertEquals(10, s.searchPageSize)
    }
}
