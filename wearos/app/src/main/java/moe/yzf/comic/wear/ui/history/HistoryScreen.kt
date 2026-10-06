package moe.yzf.comic.wear.ui.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.BookEntry
import moe.yzf.comic.wear.data.net.addCoverParams
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphTrash
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.PaginationRow
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll
import moe.yzf.comic.wear.ui.common.toast

/**
 * 阅读历史页。原版把这段列表放在 pages/offline/offline.ux 的「阅读历史」标签下
 * （同一页还有「本地漫画」，而下载器不在 v1 范围），因此这里单独成页，
 * 但沿用它的列表规格：
 *   卡片可横向拖动露出删除按钮，删除需二次点击确认；
 *   点击卡片续读；底部是「上一页 / 第 N 页 / 下一页」分页。
 */
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onResume: (BookEntry) -> Unit,
) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val loading by viewModel.historyLoading.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pageSize = settings.searchPageSize.coerceAtLeast(1)

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    var page by remember { mutableIntStateOf(1) }
    val totalPages = ((history.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    LaunchedEffect(history.size, pageSize) { if (page > totalPages) page = totalPages }
    val slice = history.drop((page - 1) * pageSize).take(pageSize)

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(
            title = stringResource(R.string.history_title),
            now = rememberClockText(),
            onBack = onBack,
        )

        when {
            loading && history.isEmpty() -> CenterMessage(stringResource(R.string.history_loading))
            history.isEmpty() -> CenterMessage(stringResource(R.string.history_empty))
            else ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().rotaryScroll(listState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    items(slice, key = { it.sourceKey + "/" + it.id }) { entry ->
                        SwipeableHistoryCard(
                            entry = entry,
                            quality = settings.imageQuality,
                            usePng = settings.imageUsePng,
                            onOpen = { onResume(entry) },
                            onDelete = {
                                viewModel.deleteHistory(entry.id, entry.sourceKey)
                                context.toast(context.getString(R.string.history_deleted))
                            },
                        )
                    }
                    if (totalPages > 1) {
                        item {
                            PaginationRow(
                                page = page,
                                canPrev = page > 1,
                                canNext = page < totalPages,
                                prevLabel = stringResource(R.string.history_prev),
                                nextLabel = stringResource(R.string.history_next),
                                pageLabel = stringResource(R.string.history_page_indicator, page),
                                onPrev = { if (page > 1) page -= 1 },
                                onNext = { if (page < totalPages) page += 1 },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
        }
    }
}

/** 卡片：左滑露出右侧删除按钮，删除按钮需要二次点击（原版 createConfirmGuard）。 */
@Composable
private fun SwipeableHistoryCard(
    entry: BookEntry,
    quality: Int,
    usePng: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val revealPx = with(LocalDensity.current) { 40.dp.toPx() }
    var offsetX by remember(entry.id) { mutableFloatStateOf(0f) }
    var armed by remember(entry.id) { mutableStateOf(false) }
    val animated by animateFloatAsState(offsetX, label = "swipe")

    Box(
        modifier =
            Modifier
                .padding(horizontal = 8.dp, vertical = Dim.cardGap / 2)
                .fillMaxWidth()
                .height(Dim.cardH),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 背后的删除按钮
        Box(
            modifier =
                Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Dim.cardRadius))
                    .background(Palette.Surface)
                    .clickable {
                        if (armed) {
                            onDelete()
                        } else {
                            armed = true
                            context.toast(context.getString(R.string.history_delete_confirm))
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            GlyphTrash(
                color = if (armed) Palette.Accent else Palette.TextSecondary,
                size = 16.dp,
            )
        }

        // 前面的卡片
        Row(
            modifier =
                Modifier
                    .offset { IntOffset(animated.roundToInt(), 0) }
                    .fillMaxWidth()
                    .height(Dim.cardH)
                    .clip(RoundedCornerShape(Dim.cardRadius))
                    .background(Palette.Surface)
                    .pointerInput(entry.id) {
                        detectHorizontalDragGestures { _, drag ->
                            offsetX = (offsetX + drag).coerceIn(-revealPx, 0f)
                        }
                    }
                    .clickable {
                        if (offsetX < 0f) offsetX = 0f else onOpen()
                    }
                    .padding(horizontal = Dim.cardPadH, vertical = Dim.cardPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Dim.thumbW, Dim.thumbH)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Palette.CoverPlaceholder),
            ) {
                if (entry.cover.isNotBlank()) {
                    AsyncImage(
                        model = addCoverParams(entry.cover, quality, usePng),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(Dim.thumbGap))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.wear.compose.material3.Text(
                    text = entry.name,
                    color = Palette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                androidx.wear.compose.material3.Text(
                    text = relativeTimeText(context, entry.updatedAt),
                    color = Palette.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
                androidx.wear.compose.material3.Text(
                    text = progressText(context, entry),
                    color = Palette.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun relativeTimeText(context: android.content.Context, time: Long): String {
    if (time <= 0L) return ""
    val diff = System.currentTimeMillis() - time
    val minutes = diff / 60_000L
    return when {
        minutes < 1L -> context.getString(R.string.time_just_now)
        minutes < 60L -> context.getString(R.string.time_minutes_ago, minutes.toInt())
        minutes < 60L * 24L -> context.getString(R.string.time_hours_ago, (minutes / 60L).toInt())
        else -> context.getString(R.string.time_days_ago, (minutes / (60L * 24L)).toInt())
    }
}

private fun progressText(context: android.content.Context, entry: BookEntry): String =
    if (entry.totalChapters > 1) {
        context.getString(R.string.history_chapter, entry.chapter.toString()) +
            " · " +
            context.getString(R.string.history_page, entry.page.toString())
    } else {
        context.getString(R.string.history_page, entry.page.toString())
    }
