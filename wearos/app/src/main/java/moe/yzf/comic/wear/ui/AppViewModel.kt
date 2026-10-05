package moe.yzf.comic.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.yzf.comic.wear.data.model.BookEntry
import moe.yzf.comic.wear.data.model.ComicDetail
import moe.yzf.comic.wear.data.model.ComicSource
import moe.yzf.comic.wear.data.store.AppSettings
import moe.yzf.comic.wear.data.repo.AddSourceResult
import moe.yzf.comic.wear.di.AppContainer

/**
 * 应用级状态：设置、当前源、书架、搜索历史。
 *
 * 只放跨页面共享的状态；页面私有的网络状态留在各自 Composable 里，
 * 避免一个巨型 ViewModel。
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsStore.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _shelf = MutableStateFlow<List<BookEntry>>(emptyList())
    val shelf: StateFlow<List<BookEntry>> = _shelf.asStateFlow()

    private val _currentSource = MutableStateFlow<ComicSource?>(null)
    val currentSource: StateFlow<ComicSource?> = _currentSource.asStateFlow()

    private val _sources = MutableStateFlow<List<ComicSource>>(emptyList())
    val sources: StateFlow<List<ComicSource>> = _sources.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val shelf = container.libraryStore.list()
            val sources = container.sourceStore.list()
            val current = container.sourceStore.current()
            val history = container.searchHistoryStore.list()
            withContext(Dispatchers.Main) {
                _shelf.value = shelf
                _sources.value = sources
                _currentSource.value = current
                _searchHistory.value = history
            }
        }
    }

    fun setUsingSource(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.sourceStore.setUsing(key)
            val sources = container.sourceStore.list()
            val current = container.sourceStore.current()
            withContext(Dispatchers.Main) {
                _sources.value = sources
                _currentSource.value = current
            }
        }
    }

    fun deleteSource(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.sourceStore.remove(key)
            val sources = container.sourceStore.list()
            val current = container.sourceStore.current()
            withContext(Dispatchers.Main) {
                _sources.value = sources
                _currentSource.value = current
            }
        }
    }

    /** 添加自定义漫画源；成功时刷新源列表。 */
    suspend fun addSource(apiUrl: String): Result<AddSourceResult> =
        container.repository.addSourceFromConfig(apiUrl).onSuccess { refreshAll() }

    fun recordSearch(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = container.searchHistoryStore.add(keyword)
            withContext(Dispatchers.Main) { _searchHistory.value = list }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = container.searchHistoryStore.clear()
            withContext(Dispatchers.Main) { _searchHistory.value = list }
        }
    }

    fun upsertShelf(entry: BookEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            container.libraryStore.upsert(entry)
            val shelf = container.libraryStore.list()
            withContext(Dispatchers.Main) { _shelf.value = shelf }
        }
    }

    fun removeFromShelf(id: String, sourceKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.libraryStore.remove(id, sourceKey)
            val shelf = container.libraryStore.list()
            withContext(Dispatchers.Main) { _shelf.value = shelf }
        }
    }

    fun progressOf(id: String, sourceKey: String): BookEntry? =
        _shelf.value.firstOrNull { it.id == id && it.sourceKey == sourceKey }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.update(transform) }
    }

    val repository get() = container.repository
    val api get() = container.api

    // ---- 首页「搜索优先」形态所需的补充状态，对应原版 index.ux 与 ime 页 ----

    /**
     * 对应原版 index.ux 的 toggleSource()：循环切到下一个源。
     * 圆屏首页「当前源」右侧的刷新图标就是这个动作。
     */
    fun cycleSource() {
        val list = _sources.value
        if (list.size <= 1) return
        val idx = list.indexOfFirst { it.key == _currentSource.value?.key }
        val next = list[((if (idx < 0) 0 else idx) + 1) % list.size]
        setUsingSource(next.key)
    }

    fun looksLikeComicId(input: String): Boolean = container.repository.looksLikeComicId(input)

    /** 原版 global.__imeText / global.__imeLabel：进输入页前先放好初值与标题。 */
    var inputSeed: String = ""
        private set

    var inputLabel: String = ""
        private set

    fun prepareInput(seed: String, label: String) {
        inputSeed = seed
        inputLabel = label
    }

    /** 原版 global.__imeResult = { text, confirmed } 的等价物。 */
    data class InputResult(val text: String, val confirmed: Boolean)

    private val _inputResult = MutableStateFlow<InputResult?>(null)
    val inputResult: StateFlow<InputResult?> = _inputResult.asStateFlow()

    fun setInputResult(text: String, confirmed: Boolean) {
        _inputResult.value = InputResult(text, confirmed)
    }

    fun consumeInputResult(): InputResult? = _inputResult.value.also { _inputResult.value = null }

    /**
     * 原版搜索页取到详情对象后直接把它交给详情页（openDetail → album），
     * 这里同样暂存一次，避免详情页重复请求同一份数据。
     */
    private var stashedDetail: ComicDetail? = null

    fun stashDetail(detail: ComicDetail) {
        stashedDetail = detail
    }

    /**
     * 搜索页取到详情后写入，详情页与阅读器都按 id 读取同一份，
     * 避免重复请求；不消费，便于阅读器继续用 totalChapters / name。
     */
    fun peekDetail(id: String): ComicDetail? = stashedDetail?.takeIf { it.itemId == id }

    // ---- 阅读历史（原版位于 offline.ux，本地漫画部分不在 v1 范围，这里单独成页） ----

    private val _history = MutableStateFlow<List<BookEntry>>(emptyList())
    val history: StateFlow<List<BookEntry>> = _history.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _historyLoading.value = true }
            val list = container.libraryStore.list()
            withContext(Dispatchers.Main) {
                _history.value = list
                _historyLoading.value = false
            }
        }
    }

    fun deleteHistory(id: String, sourceKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.libraryStore.remove(id, sourceKey)
            val list = container.libraryStore.list()
            withContext(Dispatchers.Main) {
                _history.value = list
                _shelf.value = list
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(container) as T
    }
}
