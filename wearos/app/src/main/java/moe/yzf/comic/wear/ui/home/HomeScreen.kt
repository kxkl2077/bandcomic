package moe.yzf.comic.wear.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphChevron
import moe.yzf.comic.wear.ui.common.GlyphCycle
import moe.yzf.comic.wear.ui.common.GlyphDownload
import moe.yzf.comic.wear.ui.common.GlyphMore
import moe.yzf.comic.wear.ui.common.GlyphPencil
import moe.yzf.comic.wear.ui.common.GlyphTrash
import moe.yzf.comic.wear.ui.common.HistoryChip
import moe.yzf.comic.wear.ui.common.InputPill
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.toast

/**
 * 首页，严格对应原版 pages/index/index.ux。
 *
 * 原版首页**不是书架**，而是一个「搜索优先」的落地页：
 *   时钟 → 应用名（圆屏点击进「关于」）→ 「当前源: X」+ 换源/编辑 →
 *   居中输入胶囊（点击进入输入页）→ 搜索历史词条（横向滚动，最多 5 条 + 清空）
 *   → 底部居中「更多」按钮（进阅读历史）。
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenAbout: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onNeedInput: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by viewModel.searchHistory.collectAsState()
    val source by viewModel.currentSource.collectAsState()
    val sources by viewModel.sources.collectAsState()
    var clearArmed by remember { mutableStateOf(false) }

    val submit: (String) -> Unit = { raw ->
        val keyword = raw.trim()
        if (keyword.isEmpty()) {
            context.toast(context.getString(R.string.tip_need_input))
        } else {
            viewModel.recordSearch(keyword)
            if (viewModel.looksLikeComicId(keyword)) onOpenDetail(keyword) else onOpenSearch(keyword)
        }
    }

    // 输入页确认后回到首页：直接发起搜索
    // （对应原版 index.ux 在 onShow 里消费 global.__imeResult 的逻辑）
    val inputResult by viewModel.inputResult.collectAsState()
    LaunchedEffect(inputResult) {
        val result = inputResult ?: return@LaunchedEffect
        viewModel.consumeInputResult()
        if (result.confirmed) submit(result.text)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PageHeader(
                title = stringResource(R.string.app_name),
                now = moe.yzf.comic.wear.ui.common.rememberClockText(),
                onTitleClick = onOpenAbout,
            )

            // .source-selector：圆屏 absolute top 88px → 44dp，紧跟标题之后
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.wear.compose.material3.Text(
                    text = stringResource(R.string.current_source) + (source?.name ?: ""),
                    color = Palette.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                // 换源仅在存在多个源时可点，对应原版 Object.keys(API_SETTING).length > 2
                if (sources.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    GlyphCycle(
                        color = Palette.TextPrimary,
                        size = Dim.icon,
                        modifier =
                            Modifier.clickable {
                                viewModel.cycleSource()
                            },
                    )
                }
                Spacer(Modifier.width(6.dp))
                GlyphPencil(
                    color = Palette.TextPrimary,
                    size = Dim.icon,
                    modifier = Modifier.clickable { onOpenEdit() },
                )
            }

            // .line：输入区在余下空间居中
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.wear.compose.material3.Text(
                        text = stringResource(R.string.home_placeholder),
                        color = Palette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    InputPill(
                        value = "",
                        placeholder = stringResource(R.string.home_placeholder),
                        onClick = onNeedInput,
                    )
                    if (history.isNotEmpty()) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(top = Dim.chipGap)
                                    .width(Dim.inputW + 20.dp)
                                    .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            history.take(5).forEach { word ->
                                HistoryChip(text = word, onClick = { submit(word) })
                                Spacer(Modifier.width(Dim.chipGap))
                            }
                            Spacer(Modifier.weight(1f))
                            // 清空搜索历史需要二次点击确认（原版 createConfirmGuard）
                            GlyphTrash(
                                color = if (clearArmed) Palette.Accent else Palette.TextTertiary,
                                size = 12.dp,
                                modifier =
                                    Modifier.clickable {
                                        if (clearArmed) {
                                            clearArmed = false
                                            scope.launch { viewModel.clearSearchHistory() }
                                        } else {
                                            clearArmed = true
                                            context.toast(context.getString(R.string.search_history_clear))
                                        }
                                    },
                            )
                        }
                    } else {
                        androidx.wear.compose.material3.Text(
                            text = stringResource(R.string.home_tip),
                            color = Palette.TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(Dim.tipWidth).padding(top = Dim.chipGap),
                        )
                    }
                }
            }
        }

        // 圆屏底部：两个带文字的入口。
        // 原版这一处只有一个图标按钮（更多），现在多了一个目的地，
        // 圆屏底部越往下可用弦宽越窄，横排两个放不下，因此竖排并补上文字，
        // 避免图标语义不明。
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BottomEntry(
                glyph = { GlyphDownload(color = Palette.TextPrimary, size = 13.dp) },
                label = stringResource(R.string.cache_title),
                onClick = onOpenCache,
            )
            Spacer(Modifier.height(4.dp))
            BottomEntry(
                glyph = { GlyphMore(color = Palette.TextPrimary, size = 13.dp) },
                label = stringResource(R.string.history_title),
                onClick = onOpenHistory,
            )
        }
    }
}

/** 底部入口胶囊：小图标 + 文字。 */
@Composable
private fun BottomEntry(glyph: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Palette.Surface80)
                .clickable { onClick() }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph()
        Spacer(Modifier.width(4.dp))
        androidx.wear.compose.material3.Text(
            text = label,
            color = Palette.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
