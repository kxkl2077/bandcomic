package moe.yzf.comic.wear.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.SearchItem
import moe.yzf.comic.wear.data.net.addCoverParams
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.PaginationRow
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll
import moe.yzf.comic.wear.ui.common.searchErrorText
import moe.yzf.comic.wear.ui.common.toast

/**
 * 搜索结果页，对应原版 pages/search/search.ux。
 *
 * 卡片规格与原版一致：高 120px → 60dp、圆角 24px → 12dp、底色 #262626，
 * 封面 60x80px → 30x40dp，标题 24px/12sp 最多三行，副标题显示「{page}页」。
 * 点击某条时该行进入「打开中...」并先把详情取回来，再交给详情页。
 */
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    keyword: String,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()

    var page by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var hasNext by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf<Throwable?>(null) }
    var openingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(keyword, page) {
        loading = true
        failed = null
        items = emptyList()
        viewModel.repository.search(keyword, page).fold(
            onSuccess = {
                items = it.results
                hasNext = it.hasMore
                loading = false
            },
            onFailure = {
                failed = it
                loading = false
            },
        )
    }

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(
            title = stringResource(R.string.search_title) + keyword,
            now = rememberClockText(),
            onBack = onBack,
        )

        when {
            loading -> CenterMessage(stringResource(R.string.search_searching))

            failed != null ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    androidx.wear.compose.material3.Text(
                        text = stringResource(R.string.search_fail) + searchErrorText(failed),
                        color = Palette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.wear.compose.material3.Text(
                        text = stringResource(R.string.search_no_found),
                        color = Palette.TextSecondary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }

            items.isEmpty() -> CenterMessage(stringResource(R.string.search_no_found))

            else ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().rotaryScroll(listState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    items(items) { item ->
                        SearchResultCard(
                            item = item,
                            showCover = settings.showCoverInSearch,
                            quality = settings.imageQuality,
                            usePng = settings.imageUsePng,
                            opening = openingId == item.comicId,
                            onClick = {
                                if (openingId != null) return@SearchResultCard
                                openingId = item.comicId
                                scope.launch {
                                    viewModel.repository.detail(item.comicId).fold(
                                        onSuccess = { detail ->
                                            viewModel.stashDetail(detail)
                                            openingId = null
                                            onOpenDetail(item.comicId)
                                        },
                                        onFailure = {
                                            openingId = null
                                            context.toast(context.getString(R.string.error_no_comic))
                                        },
                                    )
                                }
                            },
                        )
                    }
                    item {
                        PaginationRow(
                            page = page,
                            canPrev = page > 1,
                            canNext = hasNext,
                            prevLabel = stringResource(R.string.search_prev),
                            nextLabel = stringResource(R.string.search_next),
                            pageLabel = stringResource(R.string.search_page, page),
                            onPrev = { if (page > 1) page -= 1 },
                            onNext = { if (hasNext) page += 1 },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchItem,
    showCover: Boolean,
    quality: Int,
    usePng: Boolean,
    opening: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Dim.cardRadius)
    Row(
        modifier =
            Modifier
                .padding(horizontal = 8.dp, vertical = Dim.cardGap / 2)
                .fillMaxWidth()
                .height(Dim.cardH)
                .clip(shape)
                .background(Palette.Surface)
                .alpha(if (opening) 0.5f else 1f)
                .clickable { onClick() }
                .padding(horizontal = Dim.cardPadH, vertical = Dim.cardPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            Box(
                modifier =
                    Modifier
                        .size(Dim.thumbW, Dim.thumbH)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Palette.CoverPlaceholder),
            ) {
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = addCoverParams(item.coverUrl, quality, usePng),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(Dim.thumbGap))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.wear.compose.material3.Text(
                text = item.title,
                color = Palette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.wear.compose.material3.Text(
                text =
                    if (opening) {
                        stringResource(R.string.search_opening)
                    } else {
                        stringResource(R.string.search_total_page, item.pages)
                    },
                color = Palette.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
