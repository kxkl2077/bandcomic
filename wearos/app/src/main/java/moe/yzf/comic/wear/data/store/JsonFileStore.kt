package moe.yzf.comic.wear.data.store

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 极简 JSON 文件存储。
 *
 * 两个关键性质：
 * 1. 原子写入——先写 `.tmp` 再改名，避免掉电/被杀留下半截文件；
 * 2. 损坏自愈——解析失败时把原文件改名备份并回落默认值，对应快应用版
 *    `storage.fileRecovered`（「文件损坏，已备份并重置」）的行为。
 */
class JsonFileStore(private val dir: File) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }

    fun <T> read(
        name: String,
        deserializer: DeserializationStrategy<T>,
        fallback: T,
        onRecovered: (String) -> Unit = {},
    ): T {
        val file = File(dir, name)
        if (!file.exists()) return fallback
        return try {
            val text = file.readText()
            if (text.isBlank()) fallback else json.decodeFromString(deserializer, text)
        } catch (e: Exception) {
            runCatching { file.renameTo(File(dir, "$name.corrupt-${System.currentTimeMillis()}")) }
            onRecovered(name)
            fallback
        }
    }

    fun <T> write(name: String, serializer: SerializationStrategy<T>, value: T) {
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        temp.writeText(json.encodeToString(serializer, value))
        if (target.exists() && !target.delete()) {
            throw java.io.IOException("cannot replace $name")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
}
