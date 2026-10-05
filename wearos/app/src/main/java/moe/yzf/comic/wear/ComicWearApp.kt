package moe.yzf.comic.wear

import android.app.Application
import moe.yzf.comic.wear.di.AppContainer

class ComicWearApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.installImageLoader()
    }
}
