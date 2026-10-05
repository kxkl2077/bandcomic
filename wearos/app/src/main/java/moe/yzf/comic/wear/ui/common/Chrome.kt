package moe.yzf.comic.wear.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** 每秒对齐分钟边界的时钟，对应原版每页顶部的 .time（HH:MM）。 */
@Composable
fun rememberClockText(): String {
    var text by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            text = currentTimeText()
        }
    }
    return text
}

private fun currentTimeText(): String {
    val c = Calendar.getInstance()
    return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/**
 * 每页统一的页头：.time（24px/2 = 12sp，白色 60%）+ .title（32px/2 = 16sp 粗体白色）。
 *
 * 原版对**圆形屏**隐藏四角按钮，改为点击标题返回（`.title` 上绑定 routeBack），
 * 并在标题前放一个 &#xe685; 图标。这里保持一致：只有在 [onBack] 非空时才画返回箭头。
 */
@Composable
fun PageHeader(
    title: String,
    now: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    titleTextAlign: TextAlign = TextAlign.Center,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimeText(text = now, color = Palette.TextSecondary, top = Dim.headerTop)
        // 标题热区：有返回就画 ‹ 并返回；否则像首页那样只让标题本身可点（进「关于」）
        val action = onBack ?: onTitleClick
        val titleModifier =
            if (action != null) {
                Modifier
                    .padding(top = Dim.titleGap)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { action() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            } else {
                Modifier.padding(top = Dim.titleGap)
            }
        Row(
            modifier = titleModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                GlyphChevron(dir = 0, color = Palette.TextPrimary, size = Dim.icon)
                Spacer(Modifier.width(4.dp))
            }
            TitleText(text = title, weight = true, textAlign = titleTextAlign)
        }
    }
}

/** .time：font-size 24px → 12sp */
@Composable
fun TimeText(text: String, color: androidx.compose.ui.graphics.Color, top: androidx.compose.ui.unit.Dp = 0.dp) {
    androidx.wear.compose.material3.Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = top),
    )
}

/** .title：font-size 32px → 16sp */
@Composable
fun TitleText(
    text: String,
    weight: Boolean = false,
    color: androidx.compose.ui.graphics.Color = Palette.TextPrimary,
    textAlign: TextAlign = TextAlign.Center,
) {
    androidx.wear.compose.material3.Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = if (weight) FontWeight.Bold else FontWeight.Normal,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 首页/编辑页共用的输入框（原版 .input-line）：
 * 256x54px → 128x27dp，底色 #262626，1.5dp 描边 rgba(255,255,255,.06)，全圆角。
 */
@Composable
fun InputPill(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            modifier
                .width(Dim.inputW)
                .height(Dim.inputH)
                .clip(shape)
                .background(Palette.Surface)
                .border(Dim.hairline, Palette.Border, shape)
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.wear.compose.material3.Text(
            text = value.ifEmpty { placeholder },
            color = if (value.isEmpty()) Palette.TextSecondary else Palette.TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** 搜索历史词条（原版 .history-chip）：11sp，白 85%，#262626，12dp 圆角。 */
@Composable
fun HistoryChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dim.chipRadius)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(Palette.Surface)
                .border(Dim.hairline, Palette.Border, shape)
                .clickable { onClick() }
                .padding(horizontal = Dim.chipPadH, vertical = Dim.chipPadV),
    ) {
        androidx.wear.compose.material3.Text(
            text = text,
            color = Palette.TextChip,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/**
 * 列表卡片（原版 .result-item + .result-content）：
 * 高 120px → 60dp，圆角 24px → 12dp，底色 #262626，内边距 12px20px → 6dp10dp。
 */
@Composable
fun ListCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dim.cardRadius)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dim.cardH)
                .clip(shape)
                .background(Palette.Surface)
                .clickable { onClick() }
                .padding(horizontal = Dim.cardPadH, vertical = Dim.cardPadV),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** 分页控件（原版 search.ux 的 .pagination-controls）：上一页 / 第 N 页 / 下一页。 */
@Composable
fun PaginationRow(
    page: Int,
    canPrev: Boolean,
    canNext: Boolean,
    prevLabel: String,
    nextLabel: String,
    pageLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerButton(prevLabel, canPrev, onPrev)
        androidx.wear.compose.material3.Text(
            text = pageLabel,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        PagerButton(nextLabel, canNext, onNext)
    }
}

@Composable
private fun PagerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            Modifier
                .width(56.dp)
                .height(22.dp)
                .clip(shape)
                .background(if (enabled) Palette.Surface else Palette.Surface.copy(alpha = 0.4f))
                .border(Dim.hairline, Palette.Border, shape)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.wear.compose.material3.Text(
            text = label,
            color = if (enabled) Palette.TextPrimary else Palette.TextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 居中的空态 / 加载 / 错误文案。 */
@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.wear.compose.material3.Text(
            text = text,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** 页面根容器：黑底 + 页头 + 内容区，对应原版 .demo-page。 */
@Composable
fun WearPage(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val now = rememberClockText()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Palette.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(title = title, now = now, onBack = onBack)
        content()
    }
}

@Composable
fun LocalizedText(
    resId: Int,
    color: androidx.compose.ui.graphics.Color = Palette.TextPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    androidx.wear.compose.material3.Text(
        text = stringResource(resId),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
