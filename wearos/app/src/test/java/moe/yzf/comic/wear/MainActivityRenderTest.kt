package moe.yzf.comic.wear

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 真实 Activity 的组合渲染验证。
 *
 * 启动的是清单里声明的 `MainActivity`（不是测试替身），覆盖
 * `ComicWearTheme` → `AppViewModel` → `SwipeDismissableNavHost` → 首页
 * 的完整启动链路，确认新的「搜索优先」首页在圆形屏下能组合出来。
 *
 * 断言文案一律通过 `activity.getString` 取，与资源保持一致；
 * qualifiers 固定 zh-rCN，顺带验证中文资源真的能解析。
 *
 * sdk 固定为 34：Compose 测试在 Robolectric 下会走 Espresso 的 `onIdle`，
 * 而它反射调用 `android.hardware.input.InputManager.getInstance()`——
 * 该方法在 API 37 上已不存在，会抛 NoSuchMethodException。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w227dp-h227dp-xxhdpi")
class MainActivityRenderTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun assertTextPresent(text: String) {
        val nodes = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
        assertTrue("应渲染出文案「$text」，实际未找到", nodes.isNotEmpty())
    }

    @Test
    fun 启动后组合完成且未崩溃() {
        rule.waitForIdle()
        rule.onRoot().assertExists()
        assertFalse("Activity 不应在启动后立即结束", rule.activity.isFinishing)
    }

    @Test
    fun 首页渲染应用名标题() {
        rule.waitForIdle()
        assertTextPresent(rule.activity.getString(R.string.app_name))
    }

    @Test
    fun 首页渲染输入占位与当前源() {
        rule.waitForIdle()
        assertTextPresent(rule.activity.getString(R.string.home_placeholder))
        assertTextPresent(rule.activity.getString(R.string.current_source))
    }

    @Test
    fun 首页是搜索优先而不是书架() {
        rule.waitForIdle()
        // 空历史时展示引导文案，说明首页确实落在 index 形态
        assertTextPresent(rule.activity.getString(R.string.home_tip))
    }

    @Test
    fun 首页底部渲染缓存与历史两个入口() {
        rule.waitForIdle()
        // 缓存入口与阅读历史入口都带文字标签，避免圆屏上图标语义不明；
        // GlyphDownload 也在这条路径上被真实绘制一次。
        assertTextPresent(rule.activity.getString(R.string.cache_title))
        assertTextPresent(rule.activity.getString(R.string.history_title))
    }
}
