package moe.yzf.comic.wear.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.about.AboutScreen
import moe.yzf.comic.wear.ui.cache.CacheScreen
import moe.yzf.comic.wear.ui.detail.DetailScreen
import moe.yzf.comic.wear.ui.download.DownloadScreen
import moe.yzf.comic.wear.ui.history.HistoryScreen
import moe.yzf.comic.wear.ui.home.HomeScreen
import moe.yzf.comic.wear.ui.input.InputScreen
import moe.yzf.comic.wear.ui.reader.ReaderScreen
import moe.yzf.comic.wear.ui.search.SearchScreen
import moe.yzf.comic.wear.ui.sources.SourcesScreen
import androidx.compose.ui.platform.LocalContext

object Routes {
    const val HOME = "home"
    const val INPUT = "input"
    const val ABOUT = "about"
    const val SOURCES = "sources"
    const val HISTORY = "history"
    const val CACHE = "cache"

    private const val SEARCH = "search"
    private const val DETAIL = "detail"
    private const val READER = "reader"
    private const val DOWNLOAD = "download"

    fun search(keyword: String): String = "$SEARCH/${Uri.encode(keyword)}"

    fun detail(id: String): String = "$DETAIL/${Uri.encode(id)}"

    fun reader(id: String, chapter: Int): String = "$READER/${Uri.encode(id)}/$chapter"

    fun download(id: String): String = "$DOWNLOAD/${Uri.encode(id)}"

    const val SEARCH_PATTERN = "$SEARCH/{keyword}"
    const val DETAIL_PATTERN = "$DETAIL/{id}"
    const val READER_PATTERN = "$READER/{id}/{chapter}"
    const val DOWNLOAD_PATTERN = "$DOWNLOAD/{id}"

    const val ARG_KEYWORD = "keyword"
    const val ARG_ID = "id"
    const val ARG_CHAPTER = "chapter"
}

/**
 * 导航图。起始页是「搜索优先」的首页（原版 pages/index），不再是书架。
 *
 * 用 `SwipeDismissableNavHost` 而不是普通 NavHost：Wear OS 上「从左边缘右滑返回」
 * 是系统级交互预期，这个宿主把它接进导航栈。
 */
@Composable
fun ComicWearNavHost(viewModel: AppViewModel) {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenEdit = { navController.navigate(Routes.SOURCES) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenCache = { navController.navigate(Routes.CACHE) },
                onOpenSearch = { keyword -> navController.navigate(Routes.search(keyword)) },
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                onNeedInput = {
                    // 对应原版 openIme()：先放好初值与标题，再进输入页
                    viewModel.prepareInput("", context.getString(R.string.home_placeholder))
                    navController.navigate(Routes.INPUT)
                },
            )
        }

        composable(Routes.INPUT) {
            InputScreen(
                label = viewModel.inputLabel,
                seed = viewModel.inputSeed,
                onFinish = { text, confirmed ->
                    viewModel.setInputResult(text, confirmed)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.SEARCH_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_KEYWORD) { type = NavType.StringType }),
        ) { entry ->
            SearchScreen(
                viewModel = viewModel,
                keyword = entry.arguments?.getString(Routes.ARG_KEYWORD).orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
            )
        }

        composable(
            route = Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
            DetailScreen(
                viewModel = viewModel,
                comicId = id,
                onBack = { navController.popBackStack() },
                onRead = { chapter -> navController.navigate(Routes.reader(id, chapter)) },
                onDownload = { navController.navigate(Routes.download(id)) },
            )
        }

        composable(
            route = Routes.READER_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_CHAPTER) { type = NavType.IntType },
            ),
        ) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
            val chapter = entry.arguments?.getInt(Routes.ARG_CHAPTER) ?: 1
            ReaderScreen(
                viewModel = viewModel,
                comicId = id,
                startChapter = chapter,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onResume = { entry -> navController.navigate(Routes.reader(entry.id, entry.chapter)) },
            )
        }

        composable(Routes.SOURCES) {
            SourcesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAdd = {
                    viewModel.prepareInput("", context.getString(R.string.edit_input))
                    navController.navigate(Routes.INPUT)
                },
            )
        }

        composable(Routes.CACHE) {
            CacheScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpen = { comicId, chapter ->
                    navController.navigate(Routes.reader(comicId, chapter))
                },
            )
        }

        composable(
            route = Routes.DOWNLOAD_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            DownloadScreen(
                viewModel = viewModel,
                comicId = entry.arguments?.getString(Routes.ARG_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
