package moe.yzf.comic.wear.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.BookEntry
import moe.yzf.comic.wear.data.net.addImageParams
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphCheck
import moe.yzf.comic.wear.ui.common.GlyphChevron
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.TitleText
import moe.yzf.comic.wear.ui.common.errorText
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.toast
import java.io.File

/** 一页的来源：本地缓存文件，或在线图片地址。 */
private sealed interface PageSrc {
    data class Local(val file: File) : PageSrc

    data class Remote(val url: String) : PageSrc
}

/**
 * 阅读器，对应原版 pages/photo/photo.ux。
 *
 * 与原版的对应关系：
 *   页面切换   原版靠「滚到边缘后再滚一次」，Wear 上用 Pager 的左右滑动 + 表冠吸附
 *   顶栏       .time + 「第 N 页」，点图片任意处置为显隐（原版 showit）
 *   进度       圆屏画一圈全屏圆弧（#4fc3f7，底环 rgba(255,255,255,.15)）
 *   左右翻页   圆屏左右边缘的 ‹ ›
 *   设置面板   圆屏底部居中的 ✓ 展开，面板底色 rgba(38,38,38,.6)、圆角 36px → 18dp
 *   缩放/亮度  面板里的两条滑杆（原版 slider），缩放 0..100 映射 1.0x..3.0x
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
    val pagerState = rememberPagerState(pageCount = { urls.size.coerceAtLeast(0) })
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(0f) } // 0..100
    var bright by remember { mutableFloatStateOf(50f) }

    // 离开页面时落一次阅读进度（对应原版 onHide / onDestroy 的 flush）
    val page = pagerState.currentPage + 1
    androidx.compose.runtime.DisposableEffect(comicId, chapter, page, urls.size) {
        onDispose {
            if (urls.isNotEmpty()) {
                viewModel.upsertShelf(
                    BookEntry(
                        id = "online_${viewModel.currentSource.value?.key ?: ""}_$comicId",
                        sourceKey = viewModel.currentSource.value?.key.orEmpty(),
                        name = comicName.ifBlank { comicId },
                        cover = detail?.cover.orEmpty(),
                        pageCount = detail?.pageCount ?: urls.size,
                        totalChapters = totalChapters,
                        chapter = chapter,
                        page = page,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
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
                val zoom = 1f + scale / 100f * 2f
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = scale == 0f,
                    // 「相邻页预加载」设置真正生效：多留一页在合成范围内
                    beyondViewportPageCount = if (settings.preload) 1 else 0,
                ) { index ->
                    ReaderPage(
                        src = urls[index],
                        width = settings.imageSize,
                        quality = settings.imageQuality,
                        usePng = settings.imageUsePng,
                        zoom = zoom,
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
                                    if (pagerState.currentPage > 0) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    }
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
                                    if (pagerState.currentPage < urls.size - 1) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    }
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
                        pageCount = urls.size,
                        scale = scale,
                        brightness = bright,
                        onChapter = { delta ->
                            val next = chapter + delta
                            if (next in 1..totalChapters) {
                                chapter = next
                                if (!settings.keepDefaultZoom) scale = 0f
                                scope.launch { pagerState.scrollToPage(0) }
                            }
                        },
                        onPage = { target ->
                            val t = (target - 1).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
                            scope.launch { pagerState.scrollToPage(t) }
                        },
                        onScale = { scale = it },
                        onBrightness = { v ->
                            bright = v
                            context.findActivity()?.window?.let { w ->
                                val lp = w.attributes
                                lp.screenBrightness = (v / 100f).coerceIn(0.05f, 1f)
                                w.attributes = lp
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(
    src: PageSrc,
    width: Int,
    quality: Int,
    usePng: Boolean,
    zoom: Float,
    onTap: () -> Unit,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    // 本地页直接交给 Coil 读 File；在线页才拼图片参数
    val model: Any =
        when (src) {
            is PageSrc.Local -> src.file
            is PageSrc.Remote -> addImageParams(src.url, width, quality, usePng)
        }
    val painter = rememberAsyncImagePainter(model = model)
    val state = painter.state.collectAsState().value

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(zoom) {
                    if (zoom > 1f) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            pan += drag
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
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            },
                )
        }
    }
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
 */
@Composable
private fun SettingsShade(
    chapter: Int,
    totalChapters: Int,
    page: Int,
    pageCount: Int,
    scale: Float,
    brightness: Float,
    onChapter: (Int) -> Unit,
    onPage: (Int) -> Unit,
    onScale: (Float) -> Unit,
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
                        modifier = Modifier.weight(1f),
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
            )

            Subtitle(stringResource(R.string.photo_size_change))
            SliderRow(value = scale, onChange = onScale)

            Subtitle(stringResource(R.string.photo_brightness))
            SliderRow(value = brightness, onChange = onBrightness)
        }
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
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
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
            modifier = Modifier.width(64.dp),
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
private fun SliderRow(value: Float, onChange: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphChevron(
            dir = 0,
            color = Palette.TextPrimary,
            size = 16.dp,
            modifier = Modifier.clickable { onChange((value - 5f).coerceIn(0f, 100f)) },
        )
        androidx.wear.compose.material3.Text(
            text = value.toInt().toString(),
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
            modifier = Modifier.clickable { onChange((value + 5f).coerceIn(0f, 100f)) },
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
