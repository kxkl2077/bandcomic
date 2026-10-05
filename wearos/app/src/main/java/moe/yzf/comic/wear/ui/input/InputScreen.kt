package moe.yzf.comic.wear.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphCheck
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.TimeText
import moe.yzf.comic.wear.ui.common.rememberClockText
import androidx.compose.ui.res.stringResource

/**
 * 独立输入页，对应原版 pages/ime（原版自绘键盘，这里改用系统输入法）。
 *
 * 交互与原版一致：调用方先把「初始文本 + 标题」写进 ViewModel，进入本页后
 * 自动聚焦弹出输入法；确认（输入法回车或右侧勾）回传 confirmed=true，
 * 返回键回传 confirmed=false —— 与原来 global.__imeResult 的语义完全相同。
 */
@Composable
fun InputScreen(
    label: String,
    seed: String,
    onFinish: (text: String, confirmed: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(seed) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Palette.Background)
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimeText(text = rememberClockText(), color = Palette.TextSecondary, top = Dim.headerTop)
        Text(
            text = label,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = Dim.titleGap, bottom = 4.dp),
        )

        // 与首页同款输入胶囊，只是这里可编辑
        val shape = RoundedCornerShape(percent = 50)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dim.inputH + 8.dp)
                    .clip(shape)
                    .background(Palette.Surface)
                    .border(Dim.hairline, Palette.Border, shape)
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = Palette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                cursorBrush = SolidColor(Palette.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onFinish(text, true) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_placeholder),
                    color = Palette.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Palette.Surface)
                    .border(Dim.hairline, Palette.Border, RoundedCornerShape(percent = 50))
                    .clickable { onFinish(text, true) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlyphCheck(color = Palette.TextPrimary, size = 18.dp)
        }
    }
}
