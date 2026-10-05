package moe.yzf.comic.wear.data.net

import java.net.URLEncoder

/**
 * 图片 URL 参数拼接，移植自快应用版 `src/components/imageUrl.js`。
 *
 * 行为对齐要点：
 * 1. fragment 原样保留在末尾（快应用版用它给固件临时文件命名）；
 * 2. 同名参数已存在时原位替换，而不是重复追加；
 * 3. 编码方式对齐 JS `encodeURIComponent`，空格输出 `%20` 而非 `+`。
 *
 * 与快应用版的差异：不追加 `ifLVGL` / `.bin` 后缀——LVGL 预解码是 Vela 固件
 * 专属能力，Wear OS 侧由 Coil 直接解码原图，见 docs/WEAROS_PORT.md。
 */
fun addUrlParam(url: String, key: String, value: String): String {
    if (url.isEmpty()) return url

    val hashIndex = url.indexOf('#')
    val hash = if (hashIndex >= 0) url.substring(hashIndex) else ""
    val baseUrl = if (hashIndex >= 0) url.substring(0, hashIndex) else url

    val existing = Regex("[?&]" + Regex.escape(key) + "=").find(baseUrl)
    if (existing != null) {
        val valueStart = existing.range.last + 1
        var valueEnd = baseUrl.indexOf('&', valueStart)
        if (valueEnd < 0) valueEnd = baseUrl.length
        return baseUrl.substring(0, valueStart) + encode(value) + baseUrl.substring(valueEnd) + hash
    }

    // 修复快应用版的边界缺陷：地址以 ? 或 & 结尾时不再多插一个分隔符
    val separator = when {
        baseUrl.endsWith("?") || baseUrl.endsWith("&") -> ""
        baseUrl.contains('?') -> "&"
        else -> "?"
    }
    return baseUrl + separator + key + "=" + encode(value) + hash
}

private fun encode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** 正文图片参数：`width` + `quality`，开启 PNG 解析时附 `ifPNG=1`。 */
fun addImageParams(url: String, width: Int, quality: Int, usePng: Boolean): String {
    var result = addUrlParam(url, "width", width.toString())
    result = addUrlParam(result, "quality", quality.toString())
    if (usePng) {
        result = addUrlParam(result, "ifPNG", "1")
    }
    return result
}

/** 封面参数：固定 `width=80`，质量跟随设置（协议文档第 8.2 节）。 */
fun addCoverParams(url: String, quality: Int, usePng: Boolean): String =
    addImageParams(url, 80, quality, usePng)

/** 对齐 JS `encodeURIComponent` 的 URL 编码，供搜索关键词与路径占位符替换使用。 */
fun encodeUriComponent(value: String): String = encode(value)
