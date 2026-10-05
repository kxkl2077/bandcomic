package moe.yzf.comic.wear.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.yzf.comic.wear.R
import moe.yzf.comic.wear.data.net.ApiErrorType
import moe.yzf.comic.wear.data.net.ApiException

/** 把 [ApiException] 映射成原版 error.* 的文案。 */
@Composable
fun errorText(t: Throwable?): String {
    val type = (t as? ApiException)?.type
    return stringResource(
        when (type) {
            ApiErrorType.TIMEOUT -> R.string.error_network
            ApiErrorType.SSL -> R.string.error_network
            ApiErrorType.DOMAIN -> R.string.error_network
            ApiErrorType.CONNECTION -> R.string.error_network
            ApiErrorType.HTTP -> R.string.error_invalid_response
            ApiErrorType.PARSE -> R.string.error_invalid_response
            else -> R.string.error_unknown
        },
    )
}

/** 搜索链路专用：区分「网络连接失败」与「请求失败」，对应原版 search.* 两个键。 */
@Composable
fun searchErrorText(t: Throwable?): String {
    val type = (t as? ApiException)?.type
    return stringResource(
        if (type == ApiErrorType.TIMEOUT) R.string.search_network_error else R.string.search_request_error,
    )
}
