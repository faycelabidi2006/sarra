package com.example.familytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * بحث بالاسم في قائمة iptv-org العامة (قنوات منشورة علناً). لا يبحث في الإنترنت المفتوح.
 * القائمة الكاملة تُحمَّل مرة واحدة وتُخزَّن محلياً ثم تُجدَّد كل 7 أيام.
 */
object ChannelSearch {
    private val sources = listOf(
        "https://iptv-org.github.io/iptv/index.m3u",
        "https://cdn.jsdelivr.net/gh/iptv-org/iptv@gh-pages/index.m3u"
    )
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    @Volatile private var memory: List<Channel>? = null

    private suspend fun ensure(ctx: Context): List<Channel> = withContext(Dispatchers.IO) {
        memory?.let { return@withContext it }
        val cache = File(ctx.filesDir, "iptvorg_all.m3u")
        val stale = !cache.exists() || System.currentTimeMillis() - cache.lastModified() > MAX_AGE_MS
        if (stale) {
            var err: Exception? = null
            var ok = false
            for (src in sources) {
                try {
                    val conn = Net.open(src, 10_000, 60_000)
                    try {
                        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                        val tmp = File(ctx.filesDir, "iptvorg_all.tmp")
                        conn.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                        tmp.renameTo(cache)
                        ok = true
                    } finally { conn.disconnect() }
                } catch (e: Exception) { err = e }
                if (ok) break
            }
            if (!ok && !cache.exists()) throw IOException("تعذر تنزيل قاعدة بيانات القنوات (${err?.message ?: "خطأ غير معروف"}).")
        }
        val list = cache.bufferedReader().use { M3uParser.parse(it) }
        memory = list
        list
    }

    suspend fun search(ctx: Context, query: String, limit: Int = 60): List<Channel> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return ensure(ctx).asSequence()
            .filter { it.name.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true) }
            .distinctBy { it.url }
            .take(limit)
            .toList()
    }
}
