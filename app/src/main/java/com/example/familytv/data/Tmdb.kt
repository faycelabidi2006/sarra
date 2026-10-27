package com.example.familytv.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** نتيجة بحث من TMDB (فيلم أو مسلسل). */
data class TmdbItem(
    val id: Int,
    val type: String,        // "movie" أو "tv"
    val title: String,
    val original: String,
    val year: String,
    val overview: String,
    val poster: String,      // رابط كامل أو فارغ
    val rating: Double
)

data class TmdbDetail(val providers: List<String>, val trailerKey: String?, val country: String)

/**
 * بيانات وصفية فقط من TMDB (بوستر، قصة، تقييم، إعلان، منصات المشاهدة القانونية). لا يوجد هنا أي رابط تشغيل.
 * يتطلب مفتاح API (v3) مجاني من حساب themoviedb.org، وذكر المصدر في التطبيق.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/w342"

    fun country(): String = Locale.getDefault().country.ifBlank { "AE" }

    private fun get(url: String): String {
        val conn = Net.open(url, 10_000, 20_000)
        try {
            val code = conn.responseCode
            if (code == 401) throw IOException("رفض TMDB المفتاح (HTTP 401). تحقق من المفتاح في الإعدادات.")
            if (code !in 200..299) throw IOException("TMDB HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun search(key: String, query: String): List<TmdbItem> = withContext(Dispatchers.IO) {
        val url = "$API/search/multi?api_key=${Uri.encode(key)}&query=${Uri.encode(query)}&language=ar&include_adult=false"
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return@withContext emptyList()
        val out = ArrayList<TmdbItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("media_type")
            if (type != "movie" && type != "tv") continue
            val title = o.optString("title").ifBlank { o.optString("name") }
            if (title.isBlank()) continue
            val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
            val posterPath = o.optString("poster_path")
            out.add(
                TmdbItem(
                    id = o.optInt("id"),
                    type = type,
                    title = title,
                    original = o.optString("original_title").ifBlank { o.optString("original_name") },
                    year = date.take(4),
                    overview = o.optString("overview"),
                    poster = if (posterPath.isNotBlank() && posterPath != "null") IMG + posterPath else "",
                    rating = o.optDouble("vote_average", 0.0)
                )
            )
        }
        out
    }

    suspend fun details(key: String, item: TmdbItem): TmdbDetail = withContext(Dispatchers.IO) {
        val cc = country()
        val url = "$API/${item.type}/${item.id}?api_key=${Uri.encode(key)}&language=ar" +
            "&append_to_response=videos,watch/providers&include_video_language=ar,en,null"
        val root = JSONObject(get(url))

        val names = LinkedHashSet<String>()
        val region = root.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(cc)
        for (kind in listOf("flatrate", "free", "ads")) {
            val arr = region?.optJSONArray(kind) ?: continue
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("provider_name")?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
        }

        var trailer: String? = null
        val vids = root.optJSONObject("videos")?.optJSONArray("results")
        if (vids != null) {
            for (pass in 0..1) {
                for (i in 0 until vids.length()) {
                    val v = vids.optJSONObject(i) ?: continue
                    if (v.optString("site") != "YouTube") continue
                    val t = v.optString("type")
                    if ((pass == 0 && t == "Trailer") || (pass == 1 && t == "Teaser")) {
                        trailer = v.optString("key").ifBlank { null }
                        break
                    }
                }
                if (trailer != null) break
            }
        }
        TmdbDetail(names.toList(), trailer, cc)
    }
}
