package com.example.familytv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XtreamException(message: String) : Exception(message)

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

object Repository {

    fun normalizeServer(s: String): String {
        val t = s.trim().trimEnd('/')
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    /** يحمّل قائمة M3U من رابط أو ملف محلي، ويحتفظ بنسخة محلية عند انقطاع الإنترنت. */
    suspend fun loadPlaylist(ctx: Context, url: String): List<Channel> = withContext(Dispatchers.IO) {
        if (IptvOrg.isPack(url)) return@withContext IptvOrg.load(ctx)
        if (url.startsWith("file://")) {
            return@withContext File(url.removePrefix("file://")).bufferedReader().use { M3uParser.parse(it) }
        }
        val cache = File(ctx.filesDir, "pl_${url.hashCode()}.m3u")
        try {
            val conn = Net.open(url)
            try {
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> cache.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            if (!cache.exists()) throw e
        }
        cache.bufferedReader().use { M3uParser.parse(it) }
    }

    /**
     * يتصل بواجهة Xtream (player_api.php) ويعيد القنوات المباشرة بترتيب التصنيفات
     * وأرقام القنوات كما يرسلها الخادم.
     */
    suspend fun loadXtream(server: String, user: String, pass: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(server)
            val u = Uri.encode(user)
            val p = Uri.encode(pass)
            val api = "$base/player_api.php?username=$u&password=$p"
            try {
                val ui = JSONObject(httpGet(api)).optJSONObject("user_info")
                if (ui == null || ui.optInt("auth", 0) != 1) {
                    throw XtreamException("فشل تسجيل الدخول. تحقق من الخادم واسم المستخدم وكلمة المرور.")
                }
                val status = ui.str("status")
                if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
                    throw XtreamException("حالة الاشتراك: $status")
                }

                val catOrder = HashMap<String, Int>()
                val catName = HashMap<String, String>()
                val cats = JSONArray(httpGet("$api&action=get_live_categories"))
                for (i in 0 until cats.length()) {
                    val o = cats.getJSONObject(i)
                    val id = o.str("category_id")
                    catOrder[id] = i
                    catName[id] = o.str("category_name").ifBlank { "أخرى" }
                }

                val streams = JSONArray(httpGet("$api&action=get_live_streams"))
                val pairs = ArrayList<Pair<Int, Channel>>(streams.length())
                for (i in 0 until streams.length()) {
                    val o = streams.getJSONObject(i)
                    val sid = o.str("stream_id")
                    if (sid.isEmpty()) continue
                    val cid = o.str("category_id")
                    val ch = Channel(
                        id = "x$sid",
                        num = o.optInt("num", i + 1),
                        name = o.str("name").ifBlank { "قناة $sid" },
                        logo = o.str("stream_icon"),
                        url = "$base/live/$u/$p/$sid",
                        group = catName[cid] ?: "أخرى",
                        xtream = true
                    )
                    pairs.add((catOrder[cid] ?: Int.MAX_VALUE) to ch)
                }
                pairs.sortedWith(compareBy<Pair<Int, Channel>>({ it.first }, { it.second.num })).map { it.second }
            } catch (e: JSONException) {
                throw XtreamException("استجابة غير متوقعة من الخادم.")
            }
        }

    /**
     * أفلام حساب Xtream (VOD): تُعاد كـ Channel بحيث يعمل المشغّل نفسه معها.
     * رابط الفيلم كامل بامتداده (xtream = false) فلا يُضاف له امتداد وقت التشغيل.
     */
    suspend fun loadXtreamMovies(server: String, user: String, pass: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(server)
            val u = Uri.encode(user)
            val p = Uri.encode(pass)
            val api = "$base/player_api.php?username=$u&password=$p"
            try {
                val catOrder = HashMap<String, Int>()
                val catName = HashMap<String, String>()
                val cats = JSONArray(httpGet("$api&action=get_vod_categories"))
                for (i in 0 until cats.length()) {
                    val o = cats.getJSONObject(i)
                    val id = o.str("category_id")
                    catOrder[id] = i
                    catName[id] = o.str("category_name").ifBlank { "أخرى" }
                }

                val streams = JSONArray(httpGet("$api&action=get_vod_streams"))
                class Row(val order: Int, val added: Long, val ch: Channel)
                val rows = ArrayList<Row>(streams.length())
                for (i in 0 until streams.length()) {
                    val o = streams.getJSONObject(i)
                    val sid = o.str("stream_id")
                    if (sid.isEmpty()) continue
                    val cid = o.str("category_id")
                    val ext = o.str("container_extension").ifBlank { "mp4" }
                    val ch = Channel(
                        id = "m$sid",
                        num = i + 1,
                        name = o.str("name").ifBlank { "فيلم $sid" },
                        logo = o.str("stream_icon"),
                        url = "$base/movie/$u/$p/$sid.$ext",
                        group = catName[cid] ?: "أخرى",
                        xtream = false
                    )
                    rows.add(Row(catOrder[cid] ?: Int.MAX_VALUE, o.str("added").toLongOrNull() ?: 0L, ch))
                }
                rows.sortedWith(compareBy<Row>({ it.order }, { -it.added })).map { it.ch }
            } catch (e: JSONException) {
                throw XtreamException("لا توجد قائمة أفلام في هذا الحساب أو أن الخادم لم يرد بشكل صحيح.")
            }
        }

    /** مسلسلات حساب Xtream: كل مسلسل يُعاد كـ Channel (id = "s<series_id>"، وurl = رقم المسلسل فقط). */
    suspend fun loadXtreamSeries(server: String, user: String, pass: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(server)
            val api = "$base/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}"
            try {
                val catOrder = HashMap<String, Int>()
                val catName = HashMap<String, String>()
                val cats = JSONArray(httpGet("$api&action=get_series_categories"))
                for (i in 0 until cats.length()) {
                    val o = cats.getJSONObject(i)
                    val id = o.str("category_id")
                    catOrder[id] = i
                    catName[id] = o.str("category_name").ifBlank { "أخرى" }
                }
                val list = JSONArray(httpGet("$api&action=get_series"))
                class Row(val order: Int, val modified: Long, val ch: Channel)
                val rows = ArrayList<Row>(list.length())
                for (i in 0 until list.length()) {
                    val o = list.getJSONObject(i)
                    val sid = o.str("series_id")
                    if (sid.isEmpty()) continue
                    val cid = o.str("category_id")
                    val ch = Channel(
                        id = "s$sid",
                        num = i + 1,
                        name = o.str("name").ifBlank { "مسلسل $sid" },
                        logo = o.str("cover"),
                        url = sid,
                        group = catName[cid] ?: "أخرى",
                        xtream = false
                    )
                    rows.add(Row(catOrder[cid] ?: Int.MAX_VALUE, o.str("last_modified").toLongOrNull() ?: 0L, ch))
                }
                rows.sortedWith(compareBy<Row>({ it.order }, { -it.modified })).map { it.ch }
            } catch (e: JSONException) {
                throw XtreamException("لا توجد قائمة مسلسلات في هذا الحساب أو أن الخادم لم يرد بشكل صحيح.")
            }
        }

    /** حلقات مسلسل واحد. الخادم يعيدها إما كـ JSON object مفتاحه رقم الموسم، أو كمصفوفة مصفوفات. */
    suspend fun loadXtreamEpisodes(server: String, user: String, pass: String, seriesId: String): List<Episode> =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(server)
            val u = Uri.encode(user)
            val p = Uri.encode(pass)
            val api = "$base/player_api.php?username=$u&password=$p"
            try {
                val root = JSONObject(httpGet("$api&action=get_series_info&series_id=${Uri.encode(seriesId)}"))
                val out = ArrayList<Episode>()
                fun addAll(arr: JSONArray, seasonHint: Int?) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.str("id")
                        if (id.isEmpty()) continue
                        val ext = o.str("container_extension").ifBlank { "mp4" }
                        val season = o.str("season").toIntOrNull() ?: seasonHint ?: 1
                        val num = o.str("episode_num").toIntOrNull() ?: (i + 1)
                        out.add(
                            Episode(
                                id = id, season = season, num = num,
                                title = o.str("title").ifBlank { "حلقة $num" },
                                url = "$base/series/$u/$p/$id.$ext"
                            )
                        )
                    }
                }
                val eps = root.opt("episodes")
                if (eps is JSONObject) {
                    val keys = eps.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        eps.optJSONArray(k)?.let { addAll(it, k.toIntOrNull()) }
                    }
                } else if (eps is JSONArray) {
                    for (i in 0 until eps.length()) {
                        val inner = eps.optJSONArray(i)
                        if (inner != null) addAll(inner, i + 1)
                    }
                }
                out.sortedWith(compareBy<Episode>({ it.season }, { it.num }))
            } catch (e: JSONException) {
                throw XtreamException("تعذرت قراءة حلقات هذا المسلسل.")
            }
        }

    /** يختبر الدخول دون تحميل القنوات، ويعيد ملخص الحساب (الحالة وتاريخ الانتهاء وعدد الاتصالات). */
    suspend fun checkXtream(server: String, user: String, pass: String): String =
        withContext(Dispatchers.IO) {
            val api = "${normalizeServer(server)}/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}"
            try {
                val ui = JSONObject(httpGet(api)).optJSONObject("user_info")
                if (ui == null || ui.optInt("auth", 0) != 1) {
                    throw XtreamException("فشل تسجيل الدخول: الخادم أو اسم المستخدم أو كلمة المرور غير صحيحة.")
                }
                val exp = ui.str("exp_date").toLongOrNull()
                val expText = if (exp != null) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(exp * 1000)) else "بلا انتهاء"
                "تم الدخول بنجاح. الحالة:  ${ui.str("status")}. الانتهاء: $expText. الاتصالات: ${ui.str("active_cons")}/${ui.str("max_connections")}"
            } catch (e: JSONException) {
                throw XtreamException("لم يردّ الخادم كخادم Xtream. تحقق من العنوان والمنفذ.")
            }
        }

    /** بيانات الاشتراك الكاملة: الحالة والبداية والانتهاء وعدد المتصلين الآن من الحد الأقصى. */
    suspend fun xtreamInfo(server: String, user: String, pass: String): XtreamInfo =
        withContext(Dispatchers.IO) {
            val api = "${normalizeServer(server)}/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}"
            try {
                val ui = JSONObject(httpGet(api)).optJSONObject("user_info")
                if (ui == null || ui.optInt("auth", 0) != 1) {
                    throw XtreamException("فشل تسجيل الدخول: الخادم أو اسم المستخدم أو كلمة المرور غير صحيحة.")
                }
                XtreamInfo(
                    status = ui.str("status"),
                    created = ui.str("created_at").toLongOrNull()?.times(1000L),
                    expires = ui.str("exp_date").toLongOrNull()?.times(1000L),
                    active = ui.str("active_cons").toIntOrNull() ?: 0,
                    max = ui.str("max_connections").toIntOrNull() ?: 0,
                    trial = ui.str("is_trial") == "1"
                )
            } catch (e: JSONException) {
                throw XtreamException("لم يردّ الخادم كخادم Xtream.")
            }
        }

    private fun httpGet(url: String): String {
        val conn = Net.open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
