package moe.yzf.comic.wear.ui.cache

import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.CachedComic
import moe.yzf.comic.wear.data.store.CacheStore
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphTrash
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll
import moe.yzf.comic.wear.ui.common.toast
import java.io.File

/**
 * 本地漫画（缓存入口），对应原版 pages/offline/offline.ux 的「本地漫画」标签页。
 *
 * 原版这一页是「本地漫画 + 阅读历史」双标签；Wear 版把阅读历史拆成了独立页，
 * 这里只保留缓存部分，并加上顶部一行总占用。每一项显示封面、名称、大小，
 * 未下完的挂一个「未下载完」标记（对应原版封面右上角的红色三角）。
 * 左滑删除改成列表内的垃圾桶图标 + 二次点击确认，与源管理页一致。
 */
@Composable
fun CacheScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpen: (comicId: String, chapter: Int) -> Unit,
) {
    val context = LocalContext.current
    val cached by viewModel.cachedComics.collectAsState()
    var armedDelete by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refreshCache() }

    val totalSize = cached.sumOf { it.size }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PageHeader(
                title = stringResource(R.string.cache_title),
                now = rememberClockText(),
                onBack = onBack,
            )

            if (cached.isNotEmpty()) {
                androidx.wear.compose.material3.Text(
                    text = stringResource(
                        R.string.cache_used,
                        CacheStore.displaySize(totalSize),
                    ),
                    color = Palette.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (cached.isEmpty()) {
                CenterMessage(stringResource(R.string.cache_empty))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().rotaryScroll(listState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                ) {
                    items(cached, key = { it.sourceKey + "/" + it.id }) { comic ->
                        CachedRow(
                            comic = comic,
                            coverFile = viewModel.cacheCoverFile(comic),
                            armed = armedDelete == comic.sourceKey + "/" + comic.id,
                            onOpen = {
                                // 从第一章已缓存的章开始读；一章都没下完就不进阅读器
                                val firstChapter = comic.downloadedChapterNums().minOrNull()
                                if (firstChapter == null) {
                                    context.toast(context.getString(R.string.cache_incomplete))
                                } else {
                                    onOpen(comic.id, firstChapter)
                                }
                            },
                            onDelete = {
                                val key = comic.sourceKey + "/" + comic.id
                                if (armedDelete == key) {
                                    armedDelete = null
                                    viewModel.deleteCached(comic.id, comic.sourceKey)
                                    context.toast(context.getString(R.string.cache_deleted))
                                } else {
                                    armedDelete = key
                                    context.toast(context.getString(R.string.cache_delete_confirm))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedRow(
    comic: CachedComic,
    coverFile: File?,
    armed: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(Dim.pillRadius)
    Row(
        modifier =
            Modifier
                .padding(top = Dim.settingGap)
                .fillMaxWidth(0.8f)
                .height(Dim.cardH)
                .clip(shape)
                .background(Palette.Surface80)
                .clickable { onOpen() }
                .padding(horizontal = Dim.sourceItemPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 封面：优先读本地文件（离线也能显示），没有则回落源地址
        Box(
            modifier =
                Modifier
                    .size(width = Dim.thumbW, height = Dim.thumbH)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.CoverPlaceholder),
        ) {
            val model: Any? = coverFile?.takeIf { it.exists() } ?: comic.cover.ifBlank { null }
            if (model != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = model),
                    contentDescription = comic.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (comic.incomplete) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(8.dp)
                            .background(Palette.Accent),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.wear.compose.material3.Text(
                text = comic.name.ifBlank { comic.id },
                color = Palette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.wear.compose.material3.Text(
                text = stringResource(R.string.cache_size) + " " + CacheStore.displaySize(comic.size),
                color = Palette.TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
            )
            if (comic.incomplete) {
                androidx.wear.compose.material3.Text(
                    text = stringResource(R.string.cache_incomplete),
                    color = Palette.Accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        GlyphTrash(
            color = if (armed) Palette.Accent else Palette.TextSecondary,
            size = 16.dp,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onDelete() }
                    .padding(2.dp),
        )
    }
}
