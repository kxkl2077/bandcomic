package moe.yzf.comic.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.yzf.comic.wear.ui.AppViewModel
import moe.yzf.comic.wear.ui.nav.ComicWearNavHost
import moe.yzf.comic.wear.ui.theme.ComicWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ComicWearApp).container
        setContent {
            ComicWearTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModel.Factory(container),
                )
                ComicWearNavHost(appViewModel)
            }
        }
    }
}
