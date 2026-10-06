package moe.yzf.comic.wear.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.BookEntry
import moe.yzf.comic.wear.data.net.addImageParams
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphCheck
import moe.yzf.comic.wear.ui.common.GlyphChevron
import moe.yzf.comic.wear.ui.common.GlyphClose
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.TitleText
import moe.yzf.comic.wear.ui.common.errorText
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll
import java.io.File
import kotlin.math.roundToInt

/** 一页的来源：本地缓存文件，或在线图片地址。 */
private sealed interface PageSrc {
    data class Local(val file: File) : PageSrc

    data class Remote(val url: String) : PageSrc
}

/** 缩放范围。 */
private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 3f
private const val ZOOM_STEP = 0.1f

/**
 * 阅读进度落盘前先等这么久。
 * 翻页过程中不断取消重启，只有停下来才真正写一次盘。
 */
private const val PROGRESS_DEBOUNCE_MS = 600L

/** 数字直达浮层要选的是章节还是页码。 */
private enum class PickerTarget { Chapter, Page }

/**
 * 阅读器，对应原版 pages/photo/photo.ux。
 *
 * 与原版的对应关系：
 *   页面切换   原版靠「滚到边缘后再滚一次」，Wear 上用 Pager 的左右滑动 + 表冠吸附
 *   顶栏       .time + 「第 N 页」，点图片任意处置为显隐（原版 showit）
 *   进度       圆屏画一圈全屏圆弧（#4fc3f7，底环 rgba(255,255,255,.15)）
 *   左右翻页   圆屏左右边缘的 ‹ ›
 *   设置面板   圆屏底部居中的 ✓ 展开，面板底色 rgba(38,38,38,.6)、圆角 36px → 18dp
 *   缩放/亮度  面板里的两条滑杆（原版 slider）
 *
 * 相对原版补的三处：
 *   1. **双指捏合缩放**，单指平移；原版只能靠滑杆一格一格点。
 *   2. **章节/页码直达**：点面板里的章节号或页码弹出全量网格，点哪去哪；
 *      原版只有 ±1，百来章要按上百次。
 *   3. 离开阅读器时**还原窗口亮度**；原版把亮度写在窗口上且不还原，
 *      会把整个应用一直压在阅读时调暗的亮度上。
 *
 * 流畅度上的三处针对性处理（都不改变行为，只减少每帧工作量与 I/O）：
 *   a. 缩放值放进 MutableFloatState，只在绘制阶段读（graphicsLayer），
 *      捏合时只重绘不重组；否则每一帧都会重组整屏（含 Pager 与所有页）。
 *   b. 阅读进度去抖 600ms 再落盘，翻 40 页不再写 40 次 library.json。
 *   c. Coil 关掉 crossfade：翻页要求「立刻看到」，淡入反而显得慢。
 */
@Composable
fun ReaderScreen(
    viewModel: AppViewModel,
    comicId: String,
    startChapter: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var chapter by remember { mutableIntStateOf(startChapter) }
    var pages by remember { mutableStateOf<List<PageSrc>>(emptyList()) }
    var failed by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }
    var chromeVisible by remember { mutableStateOf(true) }
    var shadeVisible by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    val detail = remember(comicId) { viewModel.peekDetail(comicId) }
    val totalChapters = remember(comicId) { detail?.totalChapters ?: 1 }
    val comicName = remember(comicId) { detail?.name ?: "" }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(comicId, chapter, retry) {
        loading = true
        failed = null
        pages = emptyList()
        // 本地优先：这一章缓存过就直接读磁盘，断网也能看
        val local = viewModel.localPages(comicId, chapter)
        if (local.isNotEmpty()) {
            pages = local.map { PageSrc.Local(it) }
            loading = false
            return@LaunchedEffect
        }
        viewModel.repository.chapterImages(comicId, chapter).fold(
            onSuccess = {
                pages = it.urls.map { url -> PageSrc.Remote(url) }
                loading = false
            },
            onFailure = {
                failed = it
                loading = false
            },
        )
    }

    val urls = pages
    val pagerState = rememberPagerState(pageCount = { urls.size })
    val scope = rememberCoroutineScope()

    // 缩放的唯一真相。用 MutableFloatState 而不是普通 Float：
    // 这样它的变化只在「真正读它的地方」生效，捏合时可以只走绘制阶段。
    val zoomState = remember { mutableFloatStateOf(ZOOM_MIN) }
    // 非当前页读这个恒为 1x 的状态。
    // beyondViewportPageCount 会把相邻页一起组合，如果它们和当前页共用同一个缩放状态对象，
    // 捏合时就会「横向排开的所有页一起放大」——曾经就是这个 bug。
    val flatZoom = remember { mutableFloatStateOf(ZOOM_MIN) }
    // 只有「是否已放大」这个布尔量参与组合，跨越 1x 边界时才会重组 Pager
    val zoomed by remember { derivedStateOf { zoomState.floatValue > ZOOM_MIN } }
    var bright by remember { mutableFloatStateOf(50f) }

    // 换章后页数会先变成 0 再变成 N，必须等页面真正加载完再回到第 1 页，
    // 否则 Pager 会停在新章节里一个不存在的页码上。
    LaunchedEffect(chapter, urls.size) {
        if (urls.isNotEmpty()) pagerState.scrollToPage(0)
    }

    // 亮度是写在 Activity 窗口上的，离开阅读器必须还原，
    // 否则会把整个应用一直压在阅读时调暗的亮度上。
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val original = activity?.window?.attributes?.screenBrightness
        onDispose {
            val window = activity?.window
            if (window != null && original != null) {
                val lp = window.attributes
                lp.screenBrightness = original
                window.attributes = lp
            }
        }
    }

    val page = pagerState.currentPage + 1

    // 落一次阅读进度。所有值都在调用时从 state 现读，所以这个 lambda 永远是最新的。
    val flushProgress: () -> Unit = {
        val list = pages
        if (list.isNotEmpty()) {
            viewModel.upsertShelf(
                BookEntry(
                    id = "online_${viewModel.currentSource.value?.key ?: ""}_$comicId",
                    sourceKey = viewModel.currentSource.value?.key.orEmpty(),
                    name = comicName.ifBlank { comicId },
                    cover = detail?.cover.orEmpty(),
                    pageCount = detail?.pageCount ?: list.size,
                    totalChapters = totalChapters,
                    chapter = chapter,
                    page = pagerState.currentPage + 1,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
    val latestFlush = rememberUpdatedState(flushProgress)

    // 翻页会不断重启这个 effect，只有停下来 600ms 才真正写盘一次
    LaunchedEffect(comicId, chapter, page, urls.size) {
        if (urls.isEmpty()) return@LaunchedEffect
        delay(PROGRESS_DEBOUNCE_MS)
        latestFlush.value()
    }

    // 离开当前章（换章或退出阅读器）时补一次最终值，保证进度不丢
    DisposableEffect(comicId, chapter) {
        onDispose { latestFlush.value() }
    }

    fun goToChapter(next: Int) {
        if (next !in 1..totalChapters || next == chapter) return
        chapter = next
        if (!settings.keepDefaultZoom) zoomState.floatValue = ZOOM_MIN
    }

    /**
     * 跳到第 [index] 页（0 基）。翻页前先把缩放收回 1x。
     *
     * 两个原因：放大时 Pager 的滑动被 `userScrollEnabled` 关掉，翻页只能靠这里的动画，
     * 而相邻页是 1x，落位后当前页却会吃缩放值，动画结束会跳一下；
     * 而且「每页都从适应屏幕开始」比「上一页的缩放跟着下一页走」更好预期。
     * 注意这只处理翻页——换章的缩放由 keepDefaultZoom 决定，两者互不干扰。
     */
    fun goToPage(index: Int) {
        val target = index.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        if (target != pagerState.currentPage) zoomState.floatValue = ZOOM_MIN
        scope.launch { pagerState.animateScrollToPage(target) }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        when {
            loading -> CenterMessage(stringResource(R.string.loading_info))
            failed != null || urls.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CenterMessage(errorText(failed))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Palette.Surface)
                                .clickable { retry += 1 }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        androidx.wear.compose.material3.Text(
                            text = stringResource(R.string.action_retry),
                            color = Palette.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 放大后把左右滑动让给平移，未放大时才交给 Pager 翻页
                    userScrollEnabled = !zoomed,
                    // 「相邻页预加载」设置真正生效：多留一页在合成范围内
                    beyondViewportPageCount = if (settings.preload) 1 else 0,
                ) { index ->
                    // 只有当前页吃缩放值，相邻页恒为 1x —— 这是「所有页一起放大」的根因修复
                    val pageZoom = if (index == pagerState.currentPage) zoomState else flatZoom
                    ReaderPage(
                        src = urls[index],
                        width = settings.imageSize,
                        quality = settings.imageQuality,
                        usePng = settings.imageUsePng,
                        zoomState = pageZoom,
                        onSetZoom = { target ->
                            zoomState.floatValue = target.coerceIn(ZOOM_MIN, ZOOM_MAX)
                        },
                        onTap = {
                            if (shadeVisible) shadeVisible = false else chromeVisible = !chromeVisible
                        },
                    )
                }

                // 圆屏进度圆弧（原版 .progress-arc，total-angle 360deg）
                ArcProgress(
                    percent = if (urls.isNotEmpty()) page.toFloat() / urls.size else 0f,
                    modifier = Modifier.fillMaxSize(),
                )

                if (chromeVisible || shadeVisible) {
                    ReaderChrome(
                        page = page,
                        total = urls.size,
                        now = rememberClockText(),
                        onBack = onBack,
                    )
                    // 圆屏左右边缘翻页
                    GlyphChevron(
                        dir = 0,
                        color = Palette.TextPrimary,
                        size = Dim.pagerIcon,
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = Dim.edge)
                                .clickable {
                                    if (pagerState.currentPage > 0) goToPage(pagerState.currentPage - 1)
                                },
                    )
                    GlyphChevron(
                        dir = 1,
                        color = Palette.TextPrimary,
                        size = Dim.pagerIcon,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = Dim.edge)
                                .clickable {
                                    if (pagerState.currentPage < urls.size - 1) goToPage(pagerState.currentPage + 1)
                                },
                    )
                    // 圆屏底部居中的 ✓ → 设置面板（原版 check.png → toggleSettings）
                    GlyphCheck(
                        color = Palette.TextPrimary,
                        size = 20.dp,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = Dim.edge)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { shadeVisible = !shadeVisible }
                                .padding(4.dp),
                    )
                }

                if (shadeVisible) {
                    SettingsShade(
                        chapter = chapter,
                        totalChapters = totalChapters,
                        page = page,
                        zoom = zoomState.floatValue,
                        brightness = bright,
                        onChapter = { delta -> goToChapter(chapter + delta) },
                        onPickChapter = { picker = PickerTarget.Chapter },
                        onPickPage = { picker = PickerTarget.Page },
                        onPage = { target -> goToPage(target - 1) },
                        // 滑杆按 0.1 档走，避免出现 1.9000001 这种显示
                        onZoom = { v ->
                            zoomState.floatValue =
                                ((v * 10f).roundToInt() / 10f).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        },
                        onBrightness = { v ->
                            bright = v
                            activity?.window?.let { w ->
                                val lp = w.attributes
                                lp.screenBrightness = (v / 100f).coerceIn(0.05f, 1f)
                                w.attributes = lp
                            }
                        },
                    )
                }

                picker?.let { target ->
                    val isChapter = target == PickerTarget.Chapter
                    NumberPicker(
                        title =
                            stringResource(
                                if (isChapter) R.string.photo_chapter_pick else R.string.photo_page_jump,
                            ),
                        current = if (isChapter) chapter else page,
                        total = if (isChapter) totalChapters else urls.size,
                        onSelect = { num ->
                            picker = null
                            if (isChapter) {
                                goToChapter(num)
                            } else {
                                goToPage(num - 1)
                            }
                        },
                        onDismiss = { picker = null },
                    )
                }
            }
        }
    }
}

/**
 * 单页：图片本体 + 手势。
 *
 * 手势分工必须分得很清，否则会和 Pager 抢事件：
 *   - **两根手指** → 捏合缩放（同时按两指质心平移），事件全部吞掉；
 *   - **一根手指且已放大** → 平移，吞掉事件（此时 Pager 的滑动已被 userScrollEnabled 关掉）；
 *   - **一根手指且未放大** → 什么都不做、什么都不吞，把左右滑动让给 Pager 翻页。
 *
 * 两个必须守住的点：
 *   1. `pointerInput` 的 key 是 `Unit`，**绝不能 key 在缩放值上**——
 *      缩放实时在变，一旦 key 变了手势识别器会在捏合过程中被重启，手感直接断掉。
 *      所以最新的缩放与回调都通过 [rememberUpdatedState] / state 对象取。
 *   2. 缩放只在 `graphicsLayer` 这个绘制阶段的块里读，**不要在组合里读**，
 *      这样捏合只触发重绘，不会每帧重组整屏。
 */
@Composable
private fun ReaderPage(
    src: PageSrc,
    width: Int,
    quality: Int,
    usePng: Boolean,
    zoomState: State<Float>,
    onSetZoom: (Float) -> Unit,
    onTap: () -> Unit,
) {
    // 本地页直接交给 Coil 读 File；在线页才拼图片参数
    val model: Any =
        when (src) {
            is PageSrc.Local -> src.file
            is PageSrc.Remote -> addImageParams(src.url, width, quality, usePng)
        }
    val painter = rememberAsyncImagePainter(model = model)
    val state = painter.state.collectAsState().value

    // 这些 state 对象在重组间保持稳定；手势 lambda 只捕获它们，不捕获具体数值
    val pan = remember { mutableStateOf(Offset.Zero) }
    val container = remember { mutableStateOf(IntSize.Zero) }
    val latestZoom = rememberUpdatedState(zoomState)
    val latestOnSetZoom = rememberUpdatedState(onSetZoom)

    // 缩回 1x 时把平移归零；否则把平移夹回合法范围。
    // 用 snapshotFlow 而不是把缩放当 LaunchedEffect 的 key —— 后者会在组合里读状态。
    LaunchedEffect(Unit) {
        snapshotFlow { zoomState.value }.collect { z ->
            pan.value =
                if (z <= ZOOM_MIN) {
                    Offset.Zero
                } else {
                    clampPan(pan.value, Offset.Zero, container.value, z)
                }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { container.value = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinching = false
                        var startSpread = 0f
                        var startZoom = ZOOM_MIN
                        var lastCentroid = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            val centroid = pressed.centroid()
                            val current = latestZoom.value.value

                            if (pressed.size >= 2) {
                                val spread = pressed.spread(centroid)
                                if (!pinching) {
                                    // 刚拿到第二根手指：记基准，这一帧不算比例，避免跳变
                                    pinching = true
                                    startSpread = spread
                                    startZoom = current
                                } else if (startSpread > 0f && spread > 0f) {
                                    // 用「起点缩放 × 起点间距比值」而不是逐帧累乘，
                                    // 免得受重组延迟影响把误差累积起来
                                    latestOnSetZoom.value(startZoom * (spread / startSpread))
                                }
                                if (lastCentroid != Offset.Zero) {
                                    pan.value =
                                        clampPan(pan.value, centroid - lastCentroid, container.value, current)
                                }
                                pressed.forEach { it.consume() }
                                lastCentroid = centroid
                            } else {
                                if (pinching || current > ZOOM_MIN) {
                                    if (lastCentroid != Offset.Zero) {
                                        pan.value =
                                            clampPan(pan.value, centroid - lastCentroid, container.value, current)
                                    }
                                    pressed.forEach { it.consume() }
                                }
                                // 单指且未放大：不消费，交给 Pager 翻页
                                lastCentroid = centroid
                            }
                        }
                    }
                }
                .clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is AsyncImagePainter.State.Error -> CenterMessage(stringResource(R.string.error_network))
            else ->
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // 只在这里读缩放：绘制阶段生效，不触发重组
                                val z = zoomState.value
                                scaleX = z
                                scaleY = z
                                translationX = pan.value.x
                                translationY = pan.value.y
                            },
                )
        }
    }
}

/**
 * 把平移限制在「放大后多出来的那部分」之内。
 *
 * 之前完全不限制，能把图片拖到看不见；这里以容器尺寸乘 (zoom-1)/2 作为单边最大位移——
 * ContentScale.Fit 下图片未必铺满容器，所以这是个略宽松的近似，但足以防止「拖飞」。
 */
private fun clampPan(pan: Offset, delta: Offset, container: IntSize, zoom: Float): Offset {
    val maxX = (container.width * (zoom - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (container.height * (zoom - 1f) / 2f).coerceAtLeast(0f)
    return Offset(
        (pan.x + delta.x).coerceIn(-maxX, maxX),
        (pan.y + delta.y).coerceIn(-maxY, maxY),
    )
}

/** 多个手指位置的质心。 */
private fun List<PointerInputChange>.centroid(): Offset {
    var sum = Offset.Zero
    forEach { sum += it.position }
    return sum / size.toFloat()
}

/** 各手指到质心的平均距离，用来衡量捏合的张开程度。 */
private fun List<PointerInputChange>.spread(centroid: Offset): Float {
    if (size < 2) return 0f
    var sum = 0f
    forEach { sum += (it.position - centroid).getDistance() }
    return sum / size.toFloat()
}

/** 全屏进度圆弧，对应原版 .progress-arc（圆屏专属）。 */
@Composable
private fun ArcProgress(percent: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val d = size.minDimension - inset * 2
        drawArc(
            color = Palette.AccentLayer,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f),
            size = androidx.compose.ui.geometry.Size(d, d),
            style = Stroke(stroke),
        )
        drawArc(
            color = Palette.Accent,
            startAngle = -90f,
            sweepAngle = 360f * percent.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f),
            size = androidx.compose.ui.geometry.Size(d, d),
            style = Stroke(stroke),
        )
    }
}

/** 顶栏：.time + 「第 N 页」，点标题返回（圆屏行为）。 */
@Composable
private fun ReaderChrome(page: Int, total: Int, now: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dim.headerTop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.wear.compose.material3.Text(
            text = now,
            color = Palette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier =
                Modifier
                    .padding(top = Dim.titleGap)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChevron(dir = 0, color = Palette.TextPrimary, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            TitleText(text = stringResource(R.string.photo_page, page), weight = true)
        }
    }
}

/**
 * 设置面板（原版 .com > .shade）。
 * 圆屏宽 67%、最大高 67%，底色 rgba(38,38,38,.6)，圆角 36px → 18dp，
 * 1.5dp rgba(255,255,255,.24) 描边；含章节切换、页码跳转、缩放、亮度。
 *
 * 章节号与页码本身可点，直接进直达浮层；左右 ‹ › 仍保留做逐格微调。
 */
@Composable
private fun SettingsShade(
    chapter: Int,
    totalChapters: Int,
    page: Int,
    zoom: Float,
    brightness: Float,
    onChapter: (Int) -> Unit,
    onPickChapter: () -> Unit,
    onPickPage: () -> Unit,
    onPage: (Int) -> Unit,
    onZoom: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(Dim.pillRadius)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(Dim.shadeFraction)
                    .clip(shape)
                    .background(Palette.Shade)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (totalChapters > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlyphChevron(
                        dir = 0,
                        color = Palette.TextPrimary,
                        size = 16.dp,
                        modifier = Modifier.clickable { onChapter(-1) },
                    )
                    androidx.wear.compose.material3.Text(
                        text = stringResource(R.string.photo_chapter) + " $chapter / $totalChapters",
                        color = Palette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onPickChapter() }
                                .padding(vertical = 2.dp),
                    )
                    GlyphChevron(
                        dir = 1,
                        color = Palette.TextPrimary,
                        size = 16.dp,
                        modifier = Modifier.clickable { onChapter(1) },
                    )
                }
                Subtitle(stringResource(R.string.photo_chapter_change))
            }

            Subtitle(stringResource(R.string.photo_page_jump))
            Stepper(
                text = stringResource(R.string.photo_page, page),
                onMinus = { onPage(page - 1) },
                onPlus = { onPage(page + 1) },
                onLabelClick = onPickPage,
            )

            Subtitle(stringResource(R.string.photo_size_change))
            // 显示真实倍率而不是 0..100 的滑杆刻度——刻度值对用户没有意义
            SliderRow(
                value = zoom,
                text = formatZoom(zoom),
                min = ZOOM_MIN,
                max = ZOOM_MAX,
                step = ZOOM_STEP,
                onChange = onZoom,
            )

            Subtitle(stringResource(R.string.photo_brightness))
            SliderRow(
                value = brightness,
                text = "${brightness.toInt()}",
                min = 0f,
                max = 100f,
                step = 5f,
                onChange = onBrightness,
            )
        }
    }
}

private fun formatZoom(zoom: Float): String = String.format("%.1fx", zoom)

/**
 * 数字直达浮层（章节 / 页码共用）。
 *
 * 原版只能 ±1 逐章切换，133 章要点 132 次；这里给出全量网格，点哪个去哪个。
 * 视觉与下载页的章节网格保持一致（3 列、圆角、选中态），并接表冠滚动。
 * 打开时自动落到当前值所在行，省去从头翻。
 */
@Composable
private fun NumberPicker(
    title: String,
    current: Int,
    total: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = remember(total) { (1..total.coerceAtLeast(1)).toList() }
    val gridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = ((current - 1) / 3).coerceAtLeast(0),
        )
    val swallow = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(interactionSource = swallow, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(Dim.shadeFraction)
                    .fillMaxHeight(Dim.shadeFraction)
                    .clip(RoundedCornerShape(Dim.pillRadius))
                    .background(Palette.Shade)
                    // 吃掉面板内部的空白点击，免得点到面板缝里就把浮层关了
                    .clickable(interactionSource = swallow, indication = null) {}
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.width(18.dp))
                androidx.wear.compose.material3.Text(
                    text = title,
                    color = Palette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                GlyphClose(
                    color = Palette.TextPrimary,
                    size = 14.dp,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDismiss() }
                            .padding(2.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .rotaryScroll(gridState),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                // 带上 key，滚动时条目身份稳定，不会因为复用而闪一下
                items(all, key = { it }) { num ->
                    PickerCell(num = num, selected = num == current, onClick = { onSelect(num) })
                }
            }
        }
    }
}

@Composable
private fun PickerCell(num: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Palette.Accent else Palette.Surface)
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.wear.compose.material3.Text(
            text = num.toString(),
            color = if (selected) Palette.Background else Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Subtitle(text: String) {
    androidx.wear.compose.material3.Text(
        text = text,
        color = Palette.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun Stepper(
    text: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onLabelClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphChevron(
            dir = 0,
            color = Palette.TextPrimary,
            size = 16.dp,
            modifier = Modifier.clickable { onMinus() },
        )
        androidx.wear.compose.material3.Text(
            text = text,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .width(64.dp)
                    .then(
                        // 中间的数字可点：直接进直达浮层，比一下一下点箭头快得多
                        if (onLabelClick != null) {
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable { onLabelClick() }
                        } else {
                            Modifier
                        },
                    ),
        )
        GlyphChevron(
            dir = 1,
            color = Palette.TextPrimary,
            size = 16.dp,
            modifier = Modifier.clickable { onPlus() },
        )
    }
}

/** 圆屏上的滑杆：用 ‹ › 步进代替 Material Slider，避免细条在圆屏上难以点按。 */
@Composable
private fun SliderRow(
    value: Float,
    text: String,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphChevron(
            dir = 0,
            color = Palette.TextPrimary,
            size = 16.dp,
            modifier = Modifier.clickable { onChange((value - step).coerceIn(min, max)) },
        )
        androidx.wear.compose.material3.Text(
            text = text,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        GlyphChevron(
            dir = 1,
            color = Palette.TextPrimary,
            size = 16.dp,
            modifier = Modifier.clickable { onChange((value + step).coerceIn(min, max)) },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
