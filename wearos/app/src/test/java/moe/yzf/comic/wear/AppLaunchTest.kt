package moe.yzf.comic.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import kotlinx.coroutines.runBlocking
import moe.yzf.comic.wear.data.store.AppSettings
import moe.yzf.comic.wear.di.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动路径验证。
 *
 * 这里跑的是**真实的 Application 与依赖容器**（不是 mock）：Robolectric 会按清单
 * 实例化 `ComicWearApp`，因此下列断言等价于「手表上点开图标后 Application.onCreate
 * 会不会崩」。这是本次移植能拿到的最接近真机的运行时证据。
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    private val app: ComicWearApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun application_创建后容器已装配完毕() {
        val container: AppContainer = app.container
        assertNotNull(container.sourceStore)
        assertNotNull(container.libraryStore)
        assertNotNull(container.searchHistoryStore)
        assertNotNull(container.settingsStore)
        assertNotNull(container.api)
        assertNotNull(container.repository)
    }

    @Test
    fun 首次启动会植入内置_MangaDex_源且不悬空() {
        val store = app.container.sourceStore
        val sources = store.list()
        assertEquals(1, sources.size)

        val builtin = sources.first()
        assertEquals("MangaDex", builtin.key)
        assertEquals("https://mangadex.yzf.moe", builtin.apiUrl)
        assertTrue(builtin.builtin)

        // using 必须指向真实存在的源，否则首页取名解引用会崩（快应用版 P0-11 的教训）。
        val current = store.current()
        assertNotNull(current)
        assertEquals("MangaDex", current!!.key)
        assertEquals("MangaDex", store.currentKey())
    }

    @Test
    fun 首次启动设置取默认值() = runBlocking {
        val settings = app.container.settingsStore.snapshot()
        assertEquals(AppSettings(), settings)
        assertEquals(480, settings.safeImageSize)
        assertEquals(50, settings.safeQuality)
        assertEquals(10, settings.safePageSize)
    }

    @Test
    fun 书架与历史初始为空() {
        assertTrue(app.container.libraryStore.list().isEmpty())
        assertTrue(app.container.searchHistoryStore.list().isEmpty())
    }

    @Test
    fun UserAgent_符合协议文档的形状() {
        // 形状：pkg(versionName(versionCode))/model/brand/Android/release/sdk/lang/country
        val ua = app.container.userAgent
        val parts = ua.split("/")

        assertEquals("段数应为 8，实际 ${parts.size}: $ua", 8, parts.size)
        assertTrue("第 1 段应为 包名(版本名(版本号)): $ua", parts[0].startsWith("moe.yzf.comic.wear("))
        assertTrue("第 1 段应含版本名 1.0.0: $ua", parts[0].contains("1.0.0"))
        // 协议第 4 节：第 4 段固定为 osType，Wear OS 侧即 Android。
        assertEquals("Android", parts[3])
        val sdk = parts[5].toIntOrNull()
        assertNotNull("第 6 段应为数字 API 级别: $ua", sdk)
        assertTrue("API 级别应 >= minSdk 30，实际 $sdk", sdk!! >= 30)
    }

    @Test
    fun Coil_单例加载器已安装() {
        val loader = SingletonImageLoader.get(app)
        assertNotNull(loader)
    }
}
