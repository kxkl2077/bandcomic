package moe.yzf.comic.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

/**
 * 应用配色：延续快应用版的黑底白字风格，主色用漫画红做强调。
 * 圆屏与方屏共用同一套语义色，靠 ScreenScaffold 处理安全区。
 */
private val ComicWearColorScheme: ColorScheme
    get() = ColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFFF5A5F),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF1A0000),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF4A1416),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD8),
        secondary = androidx.compose.ui.graphics.Color(0xFFBBBBBB),
        onSecondary = androidx.compose.ui.graphics.Color(0xFF111111),
        secondaryContainer = androidx.compose.ui.graphics.Color(0xFF2A2A2E),
        onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE6E6E6),
        background = androidx.compose.ui.graphics.Color(0xFF000000),
        onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        surfaceContainer = androidx.compose.ui.graphics.Color(0xFF16161A),
        onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFAAAAAA),
        outline = androidx.compose.ui.graphics.Color(0xFF3A3A40),
        error = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
        onError = androidx.compose.ui.graphics.Color(0xFF2A0000),
    )

@Composable
fun ComicWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ComicWearColorScheme,
        typography = Typography(),
        content = content,
    )
}
