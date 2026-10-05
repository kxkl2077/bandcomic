package moe.yzf.comic.wear.ui.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 对应原版各页面里的 prompt.showToast —— 网络错误、校验失败、删除确认等
 * 一律用瞬时提示，保证与原版一致的反馈方式。
 */
fun Context.toast(message: String) {
    if (message.isBlank()) return
    val app = applicationContext
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }
}
