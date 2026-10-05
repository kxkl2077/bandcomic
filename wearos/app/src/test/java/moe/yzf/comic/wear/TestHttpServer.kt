package moe.yzf.comic.wear

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 单测用的极简 HTTP/1.1 服务端。
 *
 * 刻意不引第三方依赖（OkHttp 的 MockWebServer 需要新增坐标并核对版本），
 * 又完全掌控响应，因此可以精确注入「第 N 次才成功」「固定 404」「先睡眠再响应」
 * 这类故障场景，同时让被测代码走的是真实的 OkHttp + Socket 路径。
 *
 * 每个连接单独开线程，避免一个慢响应把其它请求串住。
 */
class TestHttpServer {

    data class Response(
        val status: Int = 200,
        val contentType: String = "text/plain",
        val body: ByteArray = ByteArray(0),
    )

    private val server = ServerSocket(0)
    private val routes = ConcurrentHashMap<String, () -> Response>()
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private var acceptThread: Thread? = null

    val port: Int get() = server.localPort

    /** 注册一个固定响应的路由。 */
    fun on(path: String, response: Response) {
        routes[path] = { response }
    }

    /** 注册一个动态路由（可读计数、可睡眠、可先失败后成功）。 */
    fun on(path: String, handler: () -> Response) {
        routes[path] = handler
    }

    /** 该路径被请求过几次；未请求过返回 0。 */
    fun hitCount(path: String): Int = hits[path]?.get() ?: 0

    fun json(path: String, body: String) {
        on(path, Response(200, "application/json", body.toByteArray()))
    }

    fun png(path: String, bytes: ByteArray) {
        on(path, Response(200, "image/png", bytes))
    }

    fun start() {
        acceptThread = Thread({
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    return@Thread
                }
                Thread { handle(socket) }.start()
            }
        }, "test-http-accept").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        runCatching { server.close() }
    }

    private fun handle(socket: Socket) {
        runCatching {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                // 吃掉请求头，避免客户端还在写时我们就开始回包
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
                hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()

                val response = routes[path]?.invoke()
                    ?: Response(404, "text/plain", "not found".toByteArray())

                val out = s.getOutputStream()
                out.write(
                    (
                        "HTTP/1.1 ${response.status} ${statusText(response.status)}\r\n" +
                            "Content-Type: ${response.contentType}\r\n" +
                            "Content-Length: ${response.body.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                out.write(response.body)
                out.flush()
            }
        }
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "Status"
    }
}
