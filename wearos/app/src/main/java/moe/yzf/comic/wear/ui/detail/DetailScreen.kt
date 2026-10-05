package moe.yzf.comic.wear.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.ComicDetail
import moe.yzf.comic.wear.data.net.addCoverParams
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.errorText
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll

/**
 * 详情页，对应原版 pages/album/album.ux。
 *
 * 封面宽度为屏宽的 3/4、比例 4:3，圆角 24px → 12dp；封面下方是
 * 「点击封面开始阅读」提示；再下面是 80% 宽的资料行 —— 每行是一个
 * 18dp 圆角、#262626 底、1.5dp rgba(255,255,255,.24) 描边的胶囊，
 * 左侧标签 #aaaaaa、右侧数值加粗。
 *
 * 搜索页已经取过详情时会直接复用（对应原版把 detail 对象传给 album 页）。
 */
@Composable
fun DetailScreen(
    viewModel: AppViewModel,
    comicId: String,
    onBack: () -> Unit,
    onRead: (chapter: Int) -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val settings by viewModel.settings.collectAsState()
    var detail by remember { mutableStateOf<ComicDetail?>(null) }
    var failed by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(comicId) {
        val stashed = viewModel.peekDetail(comicId)
        if (stashed != null) {
            detail = stashed
            loading = false
            return@LaunchedEffect
        }
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

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(
            title = stringResource(R.string.detail_title),
            now = rememberClockText(),
            onBack = onBack,
        )

        val loaded = detail
        when {
            loading -> CenterMessage(stringResource(R.string.loading_info))
            loaded == null -> CenterMessage(errorText(failed))
            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .rotaryScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 封面：宽 = 屏宽 * 3/4，高 = 宽 * 4/3
                    val coverWidth = screenWidthDp * 0.75f
                    Box(
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .width(coverWidth)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(Dim.cardRadius))
                                .background(Palette.CoverPlaceholder)
                                .clickable { onRead(1) },
                    ) {
                        if (loaded.cover.isNotBlank()) {
                            AsyncImage(
                                model = addCoverParams(loaded.cover, settings.imageQuality, settings.imageUsePng),
                                contentDescription = loaded.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    androidx.wear.compose.material3.Text(
                        text = stringResource(R.string.detail_tap_read),
                        color = Palette.TextFaint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp),
                    )

                    androidx.wear.compose.material3.Text(
                        text = loaded.name,
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp).fillMaxWidth(0.8f),
                    )

                    Column(
                        modifier = Modifier.padding(top = 5.dp).fillMaxWidth(0.8f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        // 数字 ID 显示为 GID（与原版一致），其余显示 ID
                        InfoRow(if (loaded.itemId.all { it.isDigit() }) "GID" else "ID", loaded.itemId)
                        InfoRow(stringResource(R.string.detail_page_count), loaded.pageCount.toString())
                        if (loaded.totalChapters > 1) {
                            InfoRow(
                                stringResource(R.string.detail_total_chapters),
                                loaded.totalChapters.toString(),
                            )
                        }
                        if (loaded.rate.isNotBlank()) {
                            InfoRow(stringResource(R.string.detail_rate), loaded.rate)
                        }
                        if (loaded.views.isNotBlank()) {
                            InfoRow(stringResource(R.string.detail_views), loaded.views)
                        }
                        if (loaded.tags.isNotEmpty()) {
                            InfoRow(stringResource(R.string.detail_tags), loaded.tags.joinToString(" · "))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
        }
    }
}

/** 详情资料行（原版 .detail .column）。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dim.pillRadius))
                .background(Palette.Surface)
                .padding(horizontal = Dim.pillPadH, vertical = Dim.pillPadV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.wear.compose.material3.Text(
            text = label,
            color = Palette.Label,
            fontSize = 12.sp,
            maxLines = 1,
        )
        androidx.wear.compose.material3.Text(
            text = value,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
