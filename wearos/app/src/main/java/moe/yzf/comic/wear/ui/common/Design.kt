package moe.yzf.comic.wear.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 视觉令牌。数值逐条取自原版 Vela 快应用（小米「腕上漫画」v2.3）的 .ux / CSS。
 *
 * 换算依据：原版 manifest 的 config.designWidth = "device-width"，CSS px 与设备像素
 * 1:1；目标手表 466x466 @320dpi，密度 2.0，因此 dp = 原版 px / 2。
 */
object Palette {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF262626) // 卡片 / 输入框底色
    val Surface80 = Color(0xCC262626) // rgba(38,38,38,.8) 源列表项
    val Shade = Color(0x99262626) // rgba(38,38,38,.6) 阅读设置面板
    val CoverPlaceholder = Color(0xFF1A1A1A)
    val Border = Color(0x0FFFFFFF) // rgba(255,255,255,.06)
    val BorderStrong = Color(0x3DFFFFFF) // rgba(255,255,255,.24)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextChip = Color(0xD9FFFFFF) // .85
    val TextSecondary = Color(0x99FFFFFF) // .6
    val TextTertiary = Color(0x73FFFFFF) // .45
    val TextFaint = Color(0x8CFFFFFF) // .55
    val Accent = Color(0xFF4FC3F7) // 进度环 / 进度条
    val AccentLayer = Color(0x26FFFFFF) // rgba(255,255,255,.15)
    val Label = Color(0xFFAAAAAA) // .grey 详情行标签
    val VersionGrey = Color(0xFF999999) // .re
}

/** 尺寸令牌，单位 dp（= 原版 px / 2）。 */
object Dim {
    // 页面通用
    val edge = 6.dp // base.css .leftButton/.rightButton: 6px
    val headerTop = 5.dp // .time top: 10px
    val titleGap = 3.dp // .title margin-top: 7px
    val icon = 16.dp // .iconfont font-size: 32px

    // 首页
    val sourceTop = 44.dp // .source-selector top: 88px
    val inputW = 128.dp // .input-line width: 256px
    val inputH = 27.dp // .input-line height: 54px
    val chipRadius = 12.dp // .history-chip border-radius: 24px
    val chipPadH = 5.dp // .history-chip padding: 2px 10px
    val chipPadV = 1.dp
    val chipGap = 4.dp // .history-chip margin-right: 8px
    val tipWidth = 128.dp // .home-tip width: 256px

    // 列表卡片（搜索结果 / 阅读历史）
    val cardH = 60.dp // .result-item height: 120px
    val cardRadius = 12.dp // 24px
    val cardPadH = 10.dp // .result-content padding: 12px 20px
    val cardPadV = 6.dp
    val cardGap = 5.dp // .result-item margin-bottom: 10px
    val thumbW = 30.dp // .result-image width: 60px
    val thumbH = 40.dp // .result-image height: 80px
    val thumbGap = 5.dp // .result-text-content margin-left: 10px

    // 详情 / 源列表的胶囊卡片
    val pillRadius = 18.dp // 36px
    val pillPadH = 10.dp
    val pillPadV = 5.dp
    val hairline = 1.5.dp // 3px
    val sourceItemH = 42.dp // edit .result-item height: 84px
    val sourceItemPad = 10.dp // padding: 20px

    // 关于 / 设置
    val settingH = 56.dp // .setting-item height: 112px
    val settingPadH = 10.dp // padding: 14px 20px
    val settingPadV = 7.dp
    val settingGap = 5.dp // margin-top: 10px
    val infoW = 166.dp // #info / #copyright width: 332px
    val logoW = 38.dp // #logo width: 76px

    // 阅读器设置面板（圆屏：宽 67%、高 67%）
    val shadeFraction = 0.67f
    val pagerIcon = 22.dp // 圆屏左右翻页键
}

// ---------------------------------------------------------------------------
// 图标：全部用 Canvas 绘制，避免引入 material-icons 字体依赖，尺寸也完全可控。
// ---------------------------------------------------------------------------

private fun DrawScope.chevron(color: Color, dir: Int, thickness: Float = 0.16f) {
    val w = this.size.width
    val h = this.size.height
    val sw = w * thickness
    val cx = w / 2f
    val cy = h / 2f
    val dx = w * 0.18f
    val dy = h * 0.28f
    when (dir) {
        0 -> { // 左
            drawLine(color, Offset(cx + dx, cy - dy), Offset(cx - dx, cy), sw, StrokeCap.Round)
            drawLine(color, Offset(cx - dx, cy), Offset(cx + dx, cy + dy), sw, StrokeCap.Round)
        }
        1 -> { // 右
            drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy), sw, StrokeCap.Round)
            drawLine(color, Offset(cx + dx, cy), Offset(cx - dx, cy + dy), sw, StrokeCap.Round)
        }
        else -> { // 下
            drawLine(color, Offset(cx - dy, cy - dx), Offset(cx, cy + dx), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, cy + dx), Offset(cx + dy, cy - dx), sw, StrokeCap.Round)
        }
    }
}

@Composable
fun GlyphChevron(dir: Int, color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { chevron(color, dir) }
}

private fun DrawScope.cross(color: Color, thickness: Float = 0.16f) {
    val w = this.size.width
    val h = this.size.height
    val sw = w * thickness
    val m = w * 0.24f
    drawLine(color, Offset(m, m), Offset(w - m, h - m), sw, StrokeCap.Round)
    drawLine(color, Offset(w - m, m), Offset(m, h - m), sw, StrokeCap.Round)
}

@Composable
fun GlyphClose(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { cross(color) }
}

@Composable
fun GlyphPlus(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.16f
        val c = this.size.width / 2f
        val arm = this.size.width * 0.32f
        drawLine(color, Offset(c - arm, c), Offset(c + arm, c), sw, StrokeCap.Round)
        drawLine(color, Offset(c, c - arm), Offset(c, c + arm), sw, StrokeCap.Round)
    }
}

@Composable
fun GlyphCheck(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.18f
        val w = this.size.width
        val h = this.size.height
        drawLine(color, Offset(w * 0.20f, h * 0.52f), Offset(w * 0.42f, h * 0.74f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.80f, h * 0.28f), sw, StrokeCap.Round)
    }
}

/** 循环切换源：两段圆弧 + 箭头，语义对应原版 index.ux 的 &#xe628;。 */
@Composable
fun GlyphCycle(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.14f
        val r = this.size.width * 0.32f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawArc(
            color = color,
            startAngle = 40f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round),
        )
        val tip = Offset(c.x + r * 0.77f, c.y - r * 0.64f)
        drawLine(color, tip, Offset(tip.x - sw * 0.2f, tip.y + r * 0.45f), sw, StrokeCap.Round)
        drawLine(color, tip, Offset(tip.x + r * 0.45f, tip.y + sw * 0.2f), sw, StrokeCap.Round)
    }
}

/** 铅笔：对应原版 index.ux 的 &#xe607;（编辑漫画源）。 */
@Composable
fun GlyphPencil(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.14f
        val w = this.size.width
        val h = this.size.height
        drawLine(color, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.74f, h * 0.26f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.86f, h * 0.38f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.38f), Offset(w * 0.34f, h * 0.90f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.34f, h * 0.90f), sw, StrokeCap.Round)
    }
}

/** 删除：垃圾桶，对应原版 edit.ux 的 /common/listDel.png。 */
@Composable
fun GlyphTrash(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.13f
        val w = this.size.width
        val h = this.size.height
        drawLine(color, Offset(w * 0.16f, h * 0.28f), Offset(w * 0.84f, h * 0.28f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.40f, h * 0.16f), Offset(w * 0.60f, h * 0.16f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, h * 0.30f), Offset(w * 0.30f, h * 0.86f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.76f, h * 0.30f), Offset(w * 0.70f, h * 0.86f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.30f, h * 0.86f), Offset(w * 0.70f, h * 0.86f), sw, StrokeCap.Round)
    }
}

/** 更多：三个点，对应原版 index.ux 的 /common/more_s4.png。 */
@Composable
fun GlyphMore(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.width * 0.10f
        val cy = this.size.height / 2f
        val xs = listOf(0.22f, 0.5f, 0.78f).map { it * this.size.width }
        xs.forEach { x -> drawCircle(color, r, Offset(x, cy)) }
    }
}

/**
 * 缓存：向下箭头 + 承接托盘。语义对应原版 album.ux 的下载按钮，
 * 也用于首页进入本地漫画列表的入口。
 */
@Composable
fun GlyphDownload(color: Color, size: Dp = Dim.icon, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.14f
        val w = this.size.width
        val h = this.size.height
        // 箭杆
        drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.62f), sw, StrokeCap.Round)
        // 箭头
        drawLine(color, Offset(w * 0.28f, h * 0.42f), Offset(w * 0.5f, h * 0.64f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.72f, h * 0.42f), Offset(w * 0.5f, h * 0.64f), sw, StrokeCap.Round)
        // 托盘
        drawLine(color, Offset(w * 0.16f, h * 0.72f), Offset(w * 0.16f, h * 0.88f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.16f, h * 0.88f), Offset(w * 0.84f, h * 0.88f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.84f, h * 0.88f), Offset(w * 0.84f, h * 0.72f), sw, StrokeCap.Round)
    }
}
