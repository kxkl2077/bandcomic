package moe.yzf.comic.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 冒烟：确认 Robolectric 能在 API 37 上跑起来并加载真实 Application。 */
@RunWith(AndroidJUnit4::class)
class RobolectricSmokeTest {

    @Test
    fun application_可在_jvm_上启动() {
        val app = ApplicationProvider.getApplicationContext<ComicWearApp>()
        assertNotNull(app)
        assertNotNull(app.container)
        assertTrue(app.container.sourceStore.list().isNotEmpty())
    }
}
