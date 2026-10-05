package moe.yzf.comic.wear.di

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import moe.yzf.comic.wear.data.download.Downloader
import moe.yzf.comic.wear.data.net.ComicApi
import moe.yzf.comic.wear.data.repo.ComicRepository
import moe.yzf.comic.wear.data.store.CacheStore
import moe.yzf.comic.wear.data.store.JsonFileStore
import moe.yzf.comic.wear.data.store.LibraryStore
import moe.yzf.comic.wear.data.store.SearchHistoryStore
import moe.yzf.comic.wear.data.store.SettingsStore
import moe.yzf.comic.wear.data.store.SourceStore
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 手写依赖容器：应用规模小，不引入 DI 框架。
 * 所有单例都在这里装配，Activity 通过 `(application as ComicWearApp).container` 取用。
 */
class AppContainer(private val context: Context) {

    private val fileStore = JsonFileStore(File(context.filesDir, "store"))

    val sourceStore = SourceStore(fileStore)
    val libraryStore = LibraryStore(fileStore)
    val searchHistoryStore = SearchHistoryStore(fileStore)
    val settingsStore = SettingsStore(context)

    /** 本地漫画缓存：索引在 store/ 下，正文文件在 files/comics/ 下。 */
    val cacheStore = CacheStore(fileStore, File(context.filesDir, "comics"))

    /** 日志用的「损坏文件已重置」回调，由 Application 注入实现。 */
    var onStorageRecovered: ((String) -> Unit)? = null

    /**
     * 协议文档第 4 节要求的 User-Agent。公开只读，便于契约测试直接断言其形状。
     */
    val userAgent: String = buildUserAgent(context)

    /**
     * 共享 OkHttp 客户端。
     * 拦截器统一注入协议文档第 4 节要求的 User-Agent 与当前源的 Cookie，
     * Coil 的图片请求复用同一实例，因此正文图片也自动带上凭证。
     */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val key = sourceStore.currentKey()
            val builder = chain.request().newBuilder()
                .header("User-Agent", userAgent)
            sourceStore.cookie(key)?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
            chain.proceed(builder.build())
        }
        .build()

    val api = ComicApi(
        client = httpClient,
        userAgent = { userAgent },
        cookie = { key -> sourceStore.cookie(key) },
        json = fileStore.json,
    )

    val repository = ComicRepository(api, sourceStore)

    val downloader = Downloader(api, cacheStore)

    init {
        // 启动期同步加载，避免首次网络请求在 OkHttp 线程里做文件 IO。
        sourceStore.ensureLoaded()
        libraryStore.ensureLoaded()
        searchHistoryStore.ensureLoaded()
        cacheStore.ensureLoaded()
    }

    /** Coil 图片加载器：复用共享客户端 + 磁盘缓存，已看过的页面离线可读。 */
    fun installImageLoader() {
        SingletonImageLoader.setSafe { appContext ->
            ImageLoader.Builder(appContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(appContext.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        }
    }

    private companion object {
        /**
         * UA 形状对齐协议文档第 4 节：
         * `packageName(versionName(versionCode))/product/brand/osType/osVersionName/osVersionCode/language/region`
         * Wear OS 侧 osType 为 Android，osVersionName/osVersionCode 取系统版本与 API 级别。
         */
        fun buildUserAgent(context: Context): String {
            val packageName = context.packageName
            val versionName = runCatching {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "1.0.0"
            val versionCode = runCatching {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                PackageInfoCompat.getLongVersionCode(info)
            }.getOrDefault(1L)
            val locale = Locale.getDefault()
            return listOf(
                "$packageName($versionName($versionCode))",
                Build.MODEL.orEmpty(),
                Build.BRAND.orEmpty(),
                "Android",
                Build.VERSION.RELEASE.orEmpty(),
                Build.VERSION.SDK_INT.toString(),
                locale.language.orEmpty(),
                locale.country.orEmpty(),
            ).joinToString("/")
        }
    }
}
