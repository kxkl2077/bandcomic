package moe.yzf.comic.wear.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.download.DownloadRequest
import moe.yzf.comic.wear.data.download.DownloadState
import moe.yzf.comic.wear.data.model.CachedChapter
import moe.yzf.comic.wear.data.model.ComicDetail
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.errorText
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll
import moe.yzf.comic.wear.ui.common.toast

/** 章节格子的三种既定底色，取自原版 download.ux 的 `.dl-full` / `.dl-partial` / `.selected`。 */
private val DlFullBg = Color(0xFF17394D)
private val DlFullBorder = Color(0xFF4FC3F7)
private val DlPartialBg = Color(0xFF4D3A17)
private val DlPartialBorder = Color(0xFFFFA726)
private val DlSelectedBg = Color(0xFF4CAF50)
private val DlIdleBorder = Color(0x3DFFFFFF)

/**
 * 缓存下载页，对应原版 pages/download/download.ux。
 *
 * 两个阶段：
 * 1. **选章**——3 列章节网格，格子自带本地状态（蓝=已下完、橙=下了一半），
 *    选中变绿；底部「全选/反选」与「开始下载(N)」。
 * 2. **下载**——顶栏显示「下载漫画(第 i/N 章) - 章名」，中间显示进度与百分比，
 *    圆屏在外圈画进度弧（原版 #process + .progress-arc）。
 *
 * 与原版一致的取舍：**离开页面即取消下载**，已落盘的页保留；
 * 中断的章在下次进入时显示为橙色「部分缓存」。
 */
@Composable
fun DownloadScreen(
    viewModel: AppViewModel,
    comicId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val downloadState by viewModel.downloadState.collectAsState()

    var detail by remember { mutableStateOf<ComicDetail?>(null) }
    var failed by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var cachedNums by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var cachedByNum by remember { mutableStateOf<Map<Int, CachedChapter>>(emptyMap()) }

    val sourceKey = viewModel.currentSource.collectAsState().value?.key.orEmpty()

    LaunchedEffect(comicId, sourceKey) {
        // 详情复用搜索页/详情页取过的对象，避免重复请求
        val stashed = viewModel.peekDetail(comicId)
        if (stashed != null) {
            detail = stashed
            loading = false
        } else {
            viewModel.repository.detail(comicId).fold(
                onSuccess = {
                    detail = it
                    loading = false
                },
                onFailure = {
                    failed = it
                    loading = false
                },
            )
        }
        val cached = viewModel.cachedComic(comicId, sourceKey)
        cachedNums = viewModel.cachedChapterNums(comicId, sourceKey)
        cachedByNum = cached?.chapters.orEmpty().associateBy { it.num }
    }

    // 离开页面即取消：对应原版 onDestroy / onBackPress 的 cancelDownload
    DisposableEffect(comicId) {
        onDispose { viewModel.cancelDownload() }
    }

    // 整批结束后刷新缓存列表并提示结果
    LaunchedEffect(downloadState) {
        when (val s = downloadState) {
            is DownloadState.Finished -> {
                viewModel.refreshCache()
                cachedNums = viewModel.cachedChapterNums(comicId, sourceKey)
                cachedByNum = viewModel.cachedComic(comicId, sourceKey)?.chapters.orEmpty()
                    .associateBy { it.num }
                val msg = when {
                    s.failed.isEmpty() -> context.getString(R.string.download_all_complete)
                    s.failed.size == s.total -> context.getString(R.string.download_all_failed)
                    else ->
                        context.getString(
                            R.string.download_summary_with_failed,
                            s.success,
                            s.total,
                            s.failed.joinToString("、"),
                        )
                }
                context.toast(msg)
            }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        val loaded = detail
        when {
            loading -> CenterMessage(stringResource(R.string.loading_info))
            loaded == null -> CenterMessage(errorText(failed))
            else -> {
                val running = downloadState as? DownloadState.Running

                // 圆屏外圈进度弧：整批的完成比例
                if (running != null) {
                    val overall =
                        ((running.queueIndex - 1).toFloat() +
                            running.percent / 100f) / running.queueTotal.coerceAtLeast(1)
                    DownloadArc(percent = overall, modifier = Modifier.fillMaxSize())
                }

                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PageHeader(
                        title = buildString {
                            append(context.getString(R.string.download_title))
                            if (running != null) {
                                append(
                                    context.getString(
                                        R.string.download_queue,
                                        running.queueIndex,
                                        running.queueTotal,
                                    ),
                                )
                                if (running.chapterName.isNotBlank()) {
                                    append(" - ")
                                    append(running.chapterName)
                                }
                            }
                        },
                        now = rememberClockText(),
                        onBack = onBack,
                    )

                    if (running != null) {
                        Downloading(
                            chapter = running.chapter,
                            page = running.page,
                            pageCount = running.pageCount,
                            percent = running.percent,
                        )
                    } else {
                        ChapterSelect(
                            totalChapters = loaded.totalChapters.coerceAtLeast(1),
                            selected = selected,
                            cachedNums = cachedNums,
                            cachedByNum = cachedByNum,
                            finished = downloadState as? DownloadState.Finished,
                            onToggle = { num ->
                                selected = if (num in selected) selected - num else selected + num
                            },
                            onToggleAll = {
                                val total = loaded.totalChapters.coerceAtLeast(1)
                                selected =
                                    if (selected.size == total) emptySet()
                                    else (1..total).toSet()
                            },
                            onStart = {
                                val source = viewModel.currentSource.value
                                when {
                                    selected.isEmpty() ->
                                        context.toast(
                                            context.getString(R.string.download_select_at_least_one),
                                        )

                                    source == null ->
                                        context.toast(context.getString(R.string.error_source_deleted))

                                    else -> {
                                        viewModel.resetDownloadState()
                                        viewModel.startDownload(
                                            DownloadRequest(
                                                comicId = comicId,
                                                source = source,
                                                comicName = loaded.name,
                                                cover = loaded.cover,
                                                totalChapters = loaded.totalChapters,
                                                chapters = selected.sorted(),
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 选章网格 + 底部两个按钮。 */
@Composable
private fun ChapterSelect(
    totalChapters: Int,
    selected: Set<Int>,
    cachedNums: Set<Int>,
    cachedByNum: Map<Int, CachedChapter>,
    finished: DownloadState.Finished?,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val all = remember(totalChapters) { (1..totalChapters).toList() }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(0.78f)
                    .rotaryScroll(gridState),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(all) { num ->
                ChapterCell(
                    num = num,
                    chapter = cachedByNum[num],
                    cached = num in cachedNums,
                    selected = num in selected,
                    onClick = { onToggle(num) },
                )
            }
        }

        if (finished != null) {
            androidx.wear.compose.material3.Text(
                text =
                    if (finished.failed.isEmpty()) {
                        context.getString(R.string.download_all_complete)
                    } else {
                        context.getString(
                            R.string.download_summary_with_failed,
                            finished.success,
                            finished.total,
                            finished.failed.joinToString("、"),
                        )
                    },
                color = Palette.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                text =
                    if (selected.size == totalChapters) {
                        stringResource(R.string.download_deselect_all)
                    } else {
                        stringResource(R.string.download_select_all)
                    },
                onClick = onToggleAll,
            )
            Spacer(Modifier.width(8.dp))
            PillButton(
                text = stringResource(R.string.download_start) + "(${selected.size})",
                onClick = onStart,
            )
        }
    }
}

/** 单个章节格子：已下完=蓝、下了一半=橙、选中=绿（选中覆盖缓存态，与原版 CSS 顺序一致）。 */
@Composable
private fun ChapterCell(
    num: Int,
    chapter: CachedChapter?,
    cached: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val complete = chapter?.complete == true || (cached && chapter == null)
    val partial = chapter?.partial == true
    val bg = when {
        selected -> DlSelectedBg
        complete -> DlFullBg
        partial -> DlPartialBg
        else -> Palette.Surface
    }
    val border = when {
        selected -> DlSelectedBg
        complete -> DlFullBorder
        partial -> DlPartialBorder
        else -> DlIdleBorder
    }

    Column(
        modifier =
            Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .border(1.5.dp, border, RoundedCornerShape(8.dp))
                .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.wear.compose.material3.Text(
            text = stringResource(R.string.photo_chapter),
            color = Palette.TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        androidx.wear.compose.material3.Text(
            text = num.toString(),
            color = Palette.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 下载中：页码 + 百分比。 */
@Composable
private fun Downloading(chapter: Int, page: Int, pageCount: Int, percent: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.wear.compose.material3.Text(
            text = stringResource(R.string.photo_chapter) + " $chapter",
            color = Palette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        androidx.wear.compose.material3.Text(
            text = stringResource(R.string.download_downloading),
            color = Palette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
        androidx.wear.compose.material3.Text(
            text = stringResource(R.string.download_page, page, pageCount) + "  $percent%",
            color = Palette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 圆屏外圈进度弧，复用阅读器的观感。 */
@Composable
private fun DownloadArc(percent: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val d = size.minDimension - inset * 2
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        drawArc(
            color = Palette.AccentLayer,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = androidx.compose.ui.geometry.Size(d, d),
            style = Stroke(stroke),
        )
        drawArc(
            color = Palette.Accent,
            startAngle = -90f,
            sweepAngle = 360f * percent.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = androidx.compose.ui.geometry.Size(d, d),
            style = Stroke(stroke),
        )
    }
}

/** 底部胶囊按钮（原版 .select-buttons > .button）。 */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Palette.Surface)
                .border(1.5.dp, DlIdleBorder, RoundedCornerShape(percent = 50))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        androidx.wear.compose.material3.Text(
            text = text,
            color = Palette.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
