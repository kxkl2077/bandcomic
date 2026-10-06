package moe.yzf.comic.wear.ui.sources

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.CenterMessage
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphPlus
import moe.yzf.comic.wear.ui.common.GlyphTrash
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll
import moe.yzf.comic.wear.ui.common.toast

private val DOMAIN_REGEX = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?$")
private val IPV4_REGEX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?$")

/**
 * 漫画源管理页，对应原版 pages/edit/edit.ux。
 *
 * 列表项：高 84px → 42dp、圆角 36px → 18dp、底色 rgba(38,38,38,.8)、内边距 20px → 10dp，
 * 左侧源名加粗，右侧删除图标（内置源不显示）。点击某项即切换为当前源。
 * 底部居中的加号进入输入页，校验通过后拉取 `{api}/config` 并写入。
 */
@Composable
fun SourcesScreen(viewModel: AppViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsState()
    val current by viewModel.currentSource.collectAsState()
    var armedDelete by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // 输入页确认后回到本页：校验 → 拉取 config → 提示（与原版 edit.ux 流程一致）
    val inputResult by viewModel.inputResult.collectAsState()
    LaunchedEffect(inputResult) {
        val result = inputResult ?: return@LaunchedEffect
        viewModel.consumeInputResult()
        if (!result.confirmed) return@LaunchedEffect
        val normalized =
            result.text.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        when {
            normalized.isEmpty() ->
                context.toast(context.getString(R.string.edit_validation_empty))

            !DOMAIN_REGEX.matches(normalized) && !IPV4_REGEX.matches(normalized) ->
                context.toast(context.getString(R.string.edit_validation_domain))

            else ->
                viewModel.addSource(normalized).fold(
                    onSuccess = { r ->
                        val base =
                            context.getString(
                                if (r.insecure) R.string.edit_added_http else R.string.edit_added,
                            )
                        context.toast("$base ${r.names.joinToString("、")}")
                    },
                    onFailure = { e -> context.toast(editErrorText(context, e)) },
                )
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PageHeader(
                title = stringResource(R.string.edit_title),
                now = rememberClockText(),
                onBack = onBack,
            )

            if (sources.isEmpty()) {
                CenterMessage(stringResource(R.string.error_source_deleted))
            } else {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .rotaryScroll(listState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                ) {
                    items(sources, key = { it.key }) { source ->
                        SourceRow(
                            source = source,
                            isCurrent = source.key == current?.key,
                            armed = armedDelete == source.key,
                            onSelect = { viewModel.setUsingSource(source.key) },
                            onDelete = {
                                // 二次点击确认，对应原版 createConfirmGuard
                                if (armedDelete == source.key) {
                                    armedDelete = null
                                    viewModel.deleteSource(source.key)
                                    context.toast(context.getString(R.string.edit_deleted))
                                } else {
                                    armedDelete = source.key
                                    context.toast(context.getString(R.string.edit_delete_confirm))
                                }
                            },
                        )
                    }
                }
            }
        }

        GlyphPlus(
            color = Palette.TextPrimary,
            size = 20.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAdd() }
                    .padding(6.dp),
        )
    }
}

@Composable
private fun SourceRow(
    source: ComicSource,
    isCurrent: Boolean,
    armed: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(Dim.pillRadius)
    Row(
        modifier =
            Modifier
                .padding(top = Dim.settingGap)
                .fillMaxWidth(0.8f)
                .height(Dim.sourceItemH)
                .clip(shape)
                .background(Palette.Surface80)
                .clickable { onSelect() }
                .padding(horizontal = Dim.sourceItemPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.wear.compose.material3.Text(
            text = source.name,
            color = if (isCurrent) Palette.Accent else Palette.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!source.builtin) {
            Spacer(Modifier.width(6.dp))
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
}

/** 把请求异常映射成 edit.ux 里那套文案。 */
private fun editErrorText(context: android.content.Context, e: Throwable): String {
    val type = (e as? moe.yzf.comic.wear.data.net.ApiException)?.type
    return when (type) {
        moe.yzf.comic.wear.data.net.ApiErrorType.SSL -> context.getString(R.string.edit_ssl_error)
        moe.yzf.comic.wear.data.net.ApiErrorType.DOMAIN -> context.getString(R.string.edit_domain_error)
        moe.yzf.comic.wear.data.net.ApiErrorType.TIMEOUT -> context.getString(R.string.edit_timeout_error)
        moe.yzf.comic.wear.data.net.ApiErrorType.CONNECTION -> context.getString(R.string.edit_connection_error)
        else -> context.getString(R.string.edit_unknown_request) + " " + (e.message.orEmpty())
    }
}
