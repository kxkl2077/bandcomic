package moe.yzf.comic.wear.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.common.Dim
import moe.yzf.comic.wear.ui.common.GlyphChevron
import moe.yzf.comic.wear.ui.common.PageHeader
import moe.yzf.comic.wear.ui.common.Palette
import moe.yzf.comic.wear.ui.common.rememberClockText
import moe.yzf.comic.wear.ui.common.rotaryScroll

private val PAGE_SIZE_OPTIONS = (1..10).map { it.toString() }
private val QUALITY_OPTIONS = (30..70 step 5).map { it.toString() }
private val IMAGE_SIZE_OPTIONS = (300..700 step 20).map { it.toString() }

/**
 * 关于页，对应原版 pages/about/about.ux。原版把「关于信息」与「设置」放在同一页，
 * 这里保持同样的结构：
 *   #info 卡片（应用名 + 版本号，圆角 20px → 10dp，宽 332px → 166dp）
 *   #copyright（作者 / 感谢）
 *   .settings 列表：每项高 112px → 56dp、圆角 36px → 18dp、#262626 底，
 *   左侧标题 32px/16sp 加粗 + 说明 28px/14sp 白色 60%，右侧是控件。
 *
 * 数值项的可选值与顺序与原版完全一致（每页个数 1..10、质量 30..70 步长 5、
 * 尺寸 300..700 步长 20）。原版把滚轮 Picker 内联在行里，圆屏上改成
 * 「‹ 当前值 ›」步进更好点按，取值与语义不变。
 */
@Composable
fun AboutScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val versionText =
        remember {
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.versionName}(${info.longVersionCode})"
            }.getOrDefault("")
        }

    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(title = stringResource(R.string.about), now = rememberClockText(), onBack = onBack)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .rotaryScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cardShape = RoundedCornerShape(10.dp)
            Row(
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .width(Dim.infoW)
                        .clip(cardShape)
                        .background(Palette.Surface)
                        .border(Dim.hairline, Palette.Border, cardShape)
                        .padding(horizontal = Dim.pillPadH, vertical = Dim.pillPadV),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Palette.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = versionText, color = Palette.VersionGrey, fontSize = 12.sp)
                }
            }

            Column(modifier = Modifier.width(Dim.infoW).padding(top = 4.dp)) {
                CreditRow(stringResource(R.string.author), "小鱼yuzifu")
                CreditRow(stringResource(R.string.thanks), "OrPudding · NEORUAA · 无源流沙")
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.setting),
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(Dim.infoW).padding(start = 4.dp, bottom = 4.dp),
            )

            SettingPickerRow(
                title = stringResource(R.string.settings_search_page_size),
                info = stringResource(R.string.settings_info_search_page_size),
                options = PAGE_SIZE_OPTIONS,
                current = settings.searchPageSize.toString(),
                onPick = { v ->
                    v.toIntOrNull()?.let { n -> viewModel.updateSettings { it.copy(searchPageSize = n) } }
                },
            )
            SettingPickerRow(
                title = stringResource(R.string.settings_image_quality),
                info = stringResource(R.string.settings_info_image_quality),
                options = QUALITY_OPTIONS,
                current = settings.imageQuality.toString(),
                onPick = { v ->
                    v.toIntOrNull()?.let { n -> viewModel.updateSettings { it.copy(imageQuality = n) } }
                },
            )
            SettingPickerRow(
                title = stringResource(R.string.settings_image_size),
                info = stringResource(R.string.settings_info_image_size),
                options = IMAGE_SIZE_OPTIONS,
                current = settings.imageSize.toString(),
                onPick = { v ->
                    v.toIntOrNull()?.let { n -> viewModel.updateSettings { it.copy(imageSize = n) } }
                },
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_show_cover),
                info = stringResource(R.string.settings_info_show_cover),
                checked = settings.showCoverInSearch,
                onChange = { v -> viewModel.updateSettings { it.copy(showCoverInSearch = v) } },
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_keep_zoom),
                info = stringResource(R.string.settings_info_keep_zoom),
                checked = settings.keepDefaultZoom,
                onChange = { v -> viewModel.updateSettings { it.copy(keepDefaultZoom = v) } },
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_use_png),
                info = stringResource(R.string.settings_info_use_png),
                checked = settings.imageUsePng,
                onChange = { v -> viewModel.updateSettings { it.copy(imageUsePng = v) } },
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_preload),
                info = stringResource(R.string.settings_info_preload),
                checked = settings.preload,
                onChange = { v -> viewModel.updateSettings { it.copy(preload = v) } },
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Palette.VersionGrey, fontSize = 11.sp)
        Text(
            text = value,
            color = Palette.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 设置行外壳（原版 .setting-item）。 */
@Composable
private fun SettingRow(title: String, info: String, control: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Dim.pillRadius)
    Row(
        modifier =
            Modifier
                .padding(top = Dim.settingGap)
                .width(Dim.infoW)
                .height(Dim.settingH)
                .clip(shape)
                .background(Palette.Surface)
                .padding(horizontal = Dim.settingPadH, vertical = Dim.settingPadV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = info,
                color = Palette.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        control()
    }
}

/** 开关（原版 .switch：高 36px → 18dp，flex-basis 72px → 36dp）。 */
@Composable
private fun WearSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            Modifier
                .width(36.dp)
                .height(18.dp)
                .clip(shape)
                .background(if (checked) Palette.Accent else Palette.Surface80)
                .border(Dim.hairline, Palette.Border, shape)
                .clickable { onChange(!checked) }
                .padding(1.5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .size(15.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (checked) Palette.Background else Palette.TextSecondary),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    info: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(title = title, info = info) { WearSwitch(checked = checked, onChange = onChange) }
}

/** 数值设置：右侧「‹ 当前值 ›」步进，取值来自原版同一组选项。 */
@Composable
private fun SettingPickerRow(
    title: String,
    info: String,
    options: List<String>,
    current: String,
    onPick: (String) -> Unit,
) {
    val index = options.indexOf(current).coerceAtLeast(0)
    val canPrev = index > 0
    val canNext = index < options.size - 1

    SettingRow(title = title, info = info) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .then(if (canPrev) Modifier.clickable { onPick(options[index - 1]) } else Modifier)
                        .padding(2.dp),
            ) {
                GlyphChevron(
                    dir = 0,
                    color = if (canPrev) Palette.TextPrimary else Palette.TextTertiary,
                    size = 14.dp,
                )
            }
            Text(
                text = current,
                color = Palette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(36.dp),
            )
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .then(if (canNext) Modifier.clickable { onPick(options[index + 1]) } else Modifier)
                        .padding(2.dp),
            ) {
                GlyphChevron(
                    dir = 1,
                    color = if (canNext) Palette.TextPrimary else Palette.TextTertiary,
                    size = 14.dp,
                )
            }
        }
    }
}
