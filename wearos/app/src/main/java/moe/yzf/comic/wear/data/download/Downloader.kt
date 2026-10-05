package moe.yzf.comic.wear.data.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.yzf.comic.wear.data.model.CachedChapter
import moe.yzf.comic.wear.data.model.CachedComic
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.net.ApiErrorType
import moe.yzf.comic.wear.data.net.ApiException
import moe.yzf.comic.wear.data.net.ComicApi
import moe.yzf.comic.wear.data.net.addImageParams
import moe.yzf.comic.wear.data.store.CacheStore
import java.io.File

/** 一次缓存任务：把 [chapters] 这些章下到本地。 */
data class DownloadRequest(
    val comicId: String,
    val source: ComicSource,
    val comicName: String,
    val cover: String,
    val totalChapters: Int,
    val chapters: List<Int>,
)

/** 下载进度，供界面渲染。 */
sealed interface DownloadState {
    data object Idle : DownloadState

    data class Running(
        val chapter: Int,
        /** 本批第几章（1 起）。 */
        val queueIndex: Int,
        val queueTotal: Int,
        /** 当前章已落盘页数。 */
        val page: Int,
        val pageCount: Int,
        val chapterName: String,
    ) : DownloadState {
        val percent: Int
            get() = if (pageCount > 0) (page * 100 / pageCount) else 0
    }

    /** 全部结束（含部分失败）。[failed] 为失败的章节号。 */
    data class Finished(val success: Int, val total: Int, val failed: List<Int>) : DownloadState

    data object Cancelled : DownloadState
}

/**
 * 正文缓存下载器。
 *
 * 行为对齐原版 `pages/download/download.ux`：
 * - **串行**逐页下载，手环性能有限，并发只会更卡；
 * - 每页最多重试 [MAX_RETRY] 次；4xx（408/429 除外）是确定性错误，不重试；
 * - 单页失败先记账不中断，整章下完后串行补一次；
 * - 整章失败只记进 [DownloadState.Finished.failed]，继续下一章；
 * - 已存在且非空的页文件直接跳过，重复下载不重复拉取。
 *
 * 与原版一致的取舍：下载只在页面前台时进行，离开页面即取消（原版
 * `onDestroy`/`onBackPress` 都会 `cancelDownload`）。中断留下的残缺章
 * 由 `CachedChapter.downloaded < pageCount` 表达，下次进选择页显示为「部分缓存」。
 */
class Downloader(
    private val api: ComicApi,
    private val cache: CacheStore,
    /** 重试间隔；单测里设为 0 以免拖慢用例。 */
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    /**
     * 执行一次缓存任务。调用方负责在离开页面时取消所在协程；
     * 取消后 [state] 置为 [DownloadState.Cancelled]，已下好的页保留。
     */
    suspend fun run(request: DownloadRequest, imageSize: Int, imageQuality: Int, usePng: Boolean) {
        val failed = mutableListOf<Int>()
        try {
            ensureRegistered(request)
            downloadCover(request)

            request.chapters.forEachIndexed { index, chapterNum ->
                // OkHttp 的 execute() 是阻塞调用，协程取消打不断它，
                // 因此在每一章的边界显式检查一次：离开页面能在一页之内停下来，
                // 而不是等整批下完。
                currentCoroutineContext().ensureActive()
                val chapterName = downloadOneChapter(
                    request = request,
                    chapterNum = chapterNum,
                    queueIndex = index + 1,
                    imageSize = imageSize,
                    imageQuality = imageQuality,
                    usePng = usePng,
                )
                // 返回 null 表示这一章整章失败（图片列表都拿不到），记一次即可
                if (chapterName == null) failed.add(chapterNum)
            }

            finalize(request)
            _state.value = DownloadState.Finished(
                success = request.chapters.size - failed.size,
                total = request.chapters.size,
                failed = failed.distinct().sorted(),
            )
        } catch (e: CancellationException) {
            // 已下好的页保留，索引里那一章的 downloaded 反映真实落盘数
            runCatching { finalize(request) }
            _state.value = DownloadState.Cancelled
            throw e
        }
    }

    fun reset() {
        _state.value = DownloadState.Idle
    }

    // ---- 内部实现 ----

    /** 先登记漫画本体，下载中断时缓存页也能列出这本（对应原版 ensureComicRegistered）。 */
    private fun ensureRegistered(request: DownloadRequest) {
        cache.update(request.comicId, request.source.key) { prev ->
            (prev ?: CachedComic(
                id = request.comicId,
                sourceKey = request.source.key,
                name = request.comicName,
                totalChapters = request.totalChapters,
            )).copy(
                name = request.comicName.ifBlank { prev?.name.orEmpty() },
                cover = request.cover.ifBlank { prev?.cover.orEmpty() },
                totalChapters = maxOf(request.totalChapters, 1),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /** 封面失败不算整本失败：封面只是列表展示，正文才是内容。 */
    private suspend fun downloadCover(request: DownloadRequest) {
        if (request.cover.isBlank()) return
        val target = cache.coverFile(request.source.key, request.comicId)
        if (target.exists() && target.length() > 0) return
        try {
            val url = addImageParams(request.cover, COVER_WIDTH, COVER_QUALITY, false)
            writeAtomically(target, fetchWithRetry(request.source.key, url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 封面拉不到只是列表里少张图，不影响正文下载
        }
    }

    /** 下载一章；返回章名，整章失败返回 null。 */
    private suspend fun downloadOneChapter(
        request: DownloadRequest,
        chapterNum: Int,
        queueIndex: Int,
        imageSize: Int,
        imageQuality: Int,
        usePng: Boolean,
    ): String? {
        val sourceKey = request.source.key

        // 1) 取图片列表；拿不到就整章失败
        val images = try {
            api.parseChapterImages(
                api.getJson(sourceKey, api.buildPhotoUrl(request.source, request.comicId, chapterNum)),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        val urls = images.urls
        if (urls.isEmpty()) return null

        // 2) 登记章节（pageCount 用接口页数，downloaded 留给扫描回填）
        cache.update(request.comicId, sourceKey) { prev ->
            val chapters = prev?.chapters.orEmpty().toMutableList()
            val index = chapters.indexOfFirst { it.num == chapterNum }
            val entry = CachedChapter(
                num = chapterNum,
                name = images.title,
                pageCount = urls.size,
                downloaded = if (index >= 0) chapters[index].downloaded else 0,
            )
            if (index >= 0) chapters[index] = entry else chapters.add(entry)
            (prev ?: return@update null).copy(
                chapters = chapters.sortedBy { it.num },
                updatedAt = System.currentTimeMillis(),
            )
        }

        val dir = cache.chapterDir(sourceKey, request.comicId, chapterNum)
        if (!dir.exists()) dir.mkdirs()

        // 3) 串行逐页；失败先记账
        val pending = mutableListOf<Int>()
        var done = 0
        for (page in 1..urls.size) {
            // 同理：逐页检查取消，避免一页卡住就整批停不下来
            currentCoroutineContext().ensureActive()
            val target = cache.pageFile(sourceKey, request.comicId, chapterNum, page)
            if (target.exists() && target.length() > 0) {
                done++
                publish(request, chapterNum, queueIndex, done, urls.size, images.title)
                continue
            }
            try {
                val url = addImageParams(urls[page - 1], imageSize, imageQuality, usePng)
                writeAtomically(target, fetchWithRetry(sourceKey, url))
                done++
            } catch (e: CancellationException) {
                syncChapter(request, chapterNum)
                throw e
            } catch (e: Throwable) {
                pending.add(page)
            }
            publish(request, chapterNum, queueIndex, done, urls.size, images.title)
        }

        // 4) 失败页串行补一次；仍失败才算这章没下完
        for (page in pending) {
            try {
                val url = addImageParams(urls[page - 1], imageSize, imageQuality, usePng)
                val target = cache.pageFile(sourceKey, request.comicId, chapterNum, page)
                writeAtomically(target, fetchWithRetry(sourceKey, url))
                done++
                publish(request, chapterNum, queueIndex, done, urls.size, images.title)
            } catch (e: CancellationException) {
                syncChapter(request, chapterNum)
                throw e
            } catch (e: Throwable) {
                // 记账即可，最终以磁盘为准
            }
        }

        // 5) 以磁盘真实文件数回写，而不是用计数——用户中途退出/补下载失败都能自愈
        syncChapter(request, chapterNum)
        return images.title
    }

    private fun publish(
        request: DownloadRequest,
        chapterNum: Int,
        queueIndex: Int,
        done: Int,
        pageCount: Int,
        chapterName: String,
    ) {
        _state.value = DownloadState.Running(
            chapter = chapterNum,
            queueIndex = queueIndex,
            queueTotal = request.chapters.size,
            page = done,
            pageCount = pageCount,
            chapterName = chapterName,
        )
    }

    /** 扫描磁盘真实页数回写索引。 */
    private fun syncChapter(request: DownloadRequest, chapterNum: Int) {
        val pages = cache.existingPages(request.source.key, request.comicId, chapterNum)
        cache.update(request.comicId, request.source.key) { prev ->
            val chapters = prev?.chapters.orEmpty().toMutableList()
            val index = chapters.indexOfFirst { it.num == chapterNum }
            val entry = CachedChapter(
                num = chapterNum,
                name = if (index >= 0) chapters[index].name else "",
                pageCount = if (index >= 0) chapters[index].pageCount else pages.size,
                downloaded = pages.size,
            )
            if (index >= 0) chapters[index] = entry else chapters.add(entry)
            (prev ?: return@update null).copy(
                chapters = chapters.sortedBy { it.num },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * 整批结束（含取消）时的收尾：按磁盘真实文件数重扫每一章，再回写占用大小。
     *
     * 必须以磁盘为准而不是以计数为准——取消发生在页与页之间时，最后写进索引的
     * 章状态可能是取消前的旧值；重扫一次才能保证「界面上显示已缓存几页」与
     * 实际落盘一致，也让下次下载不会重复拉取已有的页。
     */
    private fun finalize(request: DownloadRequest) {
        request.chapters.forEach { chapterNum -> syncChapter(request, chapterNum) }
        val size = cache.directorySize(cache.comicDir(request.source.key, request.comicId))
        cache.update(request.comicId, request.source.key) { prev ->
            prev?.copy(size = size, updatedAt = System.currentTimeMillis())
        }
    }

    /**
     * 带重试的二进制下载。4xx（408/429 除外）是确定性错误，重试没有意义，直接抛出——
     * 对应原版 `retryOnFail` 的判定。
     */
    private suspend fun fetchWithRetry(sourceKey: String, url: String): ByteArray {
        var last: Throwable? = null
        for (attempt in 0..MAX_RETRY) {
            try {
                return api.fetchBytes(sourceKey, url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                last = e
                val deterministic =
                    e.type == ApiErrorType.HTTP &&
                        e.httpCode in 400..499 &&
                        e.httpCode != 408 &&
                        e.httpCode != 429
                if (deterministic) throw e
                if (attempt < MAX_RETRY) delay(retryDelayMs)
            } catch (e: Throwable) {
                last = e
                if (attempt < MAX_RETRY) delay(retryDelayMs)
            }
        }
        throw last ?: ApiException(ApiErrorType.UNKNOWN, 0, "download failed")
    }

    /** 先写 `.tmp` 再改名：中断只会留下临时文件，不会留下半张图。 */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw java.io.IOException("cannot replace ${target.name}")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private companion object {
        const val MAX_RETRY = 3
        const val RETRY_DELAY_MS = 1000L
        const val COVER_WIDTH = 80
        const val COVER_QUALITY = 60
    }
}
