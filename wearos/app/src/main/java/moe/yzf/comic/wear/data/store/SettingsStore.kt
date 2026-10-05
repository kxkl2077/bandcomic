package moe.yzf.comic.wear.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 应用设置。默认值与快应用版 `global.APP_SETTING` 对齐，
 * 但去掉了 Vela 专属项（ifLVGL 预解码、网桥优先）——见 docs/WEAROS_PORT.md。
 */
data class AppSettings(
    val imageSize: Int = 480,
    val imageQuality: Int = 50,
    val showCoverInSearch: Boolean = true,
    val keepDefaultZoom: Boolean = false,
    val imageUsePng: Boolean = false,
    val preload: Boolean = true,
    val searchPageSize: Int = 10,
) {
    /** 图片质量钳制到 1..100，避免漫画源收到非法值。 */
    val safeQuality: Int get() = imageQuality.coerceIn(1, 100)

    /** 请求宽度钳制到 80..1440，兼顾圆屏可视面积与流量。 */
    val safeImageSize: Int get() = imageSize.coerceIn(80, 1440)

    val safePageSize: Int get() = searchPageSize.coerceIn(1, 50)
}

// 顶层委托，保证同一进程内只有一个 DataStore 实例。
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "comic_wear_settings",
)

private val KEY_IMAGE_SIZE = intPreferencesKey("image_size")
private val KEY_IMAGE_QUALITY = intPreferencesKey("image_quality")
private val KEY_SHOW_COVER = booleanPreferencesKey("show_cover_in_search")
private val KEY_KEEP_ZOOM = booleanPreferencesKey("keep_default_zoom")
private val KEY_USE_PNG = booleanPreferencesKey("image_use_png")
private val KEY_PRELOAD = booleanPreferencesKey("image_preload")
private val KEY_PAGE_SIZE = intPreferencesKey("search_page_size")

class SettingsStore(private val context: Context) {

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            imageSize = prefs[KEY_IMAGE_SIZE] ?: defaults.imageSize,
            imageQuality = prefs[KEY_IMAGE_QUALITY] ?: defaults.imageQuality,
            showCoverInSearch = prefs[KEY_SHOW_COVER] ?: defaults.showCoverInSearch,
            keepDefaultZoom = prefs[KEY_KEEP_ZOOM] ?: defaults.keepDefaultZoom,
            imageUsePng = prefs[KEY_USE_PNG] ?: defaults.imageUsePng,
            preload = prefs[KEY_PRELOAD] ?: defaults.preload,
            searchPageSize = prefs[KEY_PAGE_SIZE] ?: defaults.searchPageSize,
        )
    }

    suspend fun snapshot(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(snapshot())
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_IMAGE_SIZE] = next.imageSize
            prefs[KEY_IMAGE_QUALITY] = next.imageQuality
            prefs[KEY_SHOW_COVER] = next.showCoverInSearch
            prefs[KEY_KEEP_ZOOM] = next.keepDefaultZoom
            prefs[KEY_USE_PNG] = next.imageUsePng
            prefs[KEY_PRELOAD] = next.preload
            prefs[KEY_PAGE_SIZE] = next.searchPageSize
        }
    }
}
