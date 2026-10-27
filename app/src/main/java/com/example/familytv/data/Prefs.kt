package com.example.familytv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "family_tv")

object Prefs {
    private val PLAYLISTS = stringPreferencesKey("playlists")
    private val ACTIVE = intPreferencesKey("active")
    private val FAV = stringSetPreferencesKey("favorites")
    private val HIDDEN = stringSetPreferencesKey("hidden_groups")
    private val LOCKED = stringSetPreferencesKey("locked_groups")
    private val RECENT = stringPreferencesKey("recent")
    private val PIN = stringPreferencesKey("pin")
    private val EXTERNAL = booleanPreferencesKey("external_player")
    private val FORMAT = stringPreferencesKey("stream_format")
    private val USERS = stringPreferencesKey("app_users")
    private val SESSION = stringPreferencesKey("session_user")
    private val RECORD_DIR = stringPreferencesKey("record_dir")
    private val TRUST_ALL = booleanPreferencesKey("trust_all_tls")
    private val TMDB_KEY = stringPreferencesKey("tmdb_key")
    private val PROGRESS = stringPreferencesKey("watch_progress")
    private val ADMIN_SECRET = stringPreferencesKey("admin_secret")
    private val MEMBER_TOKEN = stringPreferencesKey("member_token")
    private val MEMBER_LAST_OK = longPreferencesKey("member_last_ok")
    private val DEVICE_FALLBACK = stringPreferencesKey("device_fallback")
    private val QUALITY = intPreferencesKey("max_video_height")
    private val LIVE_SORT = stringPreferencesKey("live_sort")
    private val SUB_SIZE = stringPreferencesKey("sub_size")
    private val SUB_STYLE = stringPreferencesKey("sub_style")
    private val LAYOUT = stringPreferencesKey("layout_mode")

    private fun decode(s: String?): List<Playlist> {
        if (s.isNullOrBlank()) return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Playlist(o.getString("name"), o.getString("url"), o.optString("user", ""), o.optString("pass", ""))
        }
    }

    private fun encode(list: List<Playlist>): String =
        JSONArray().also { a ->
            list.forEach {
                a.put(JSONObject().put("name", it.name).put("url", it.url).put("user", it.user).put("pass", it.pass))
            }
        }.toString()

    fun playlists(ctx: Context): Flow<List<Playlist>> = ctx.dataStore.data.map { decode(it[PLAYLISTS]) }
    fun activeIndex(ctx: Context): Flow<Int> = ctx.dataStore.data.map { it[ACTIVE] ?: 0 }

    suspend fun addPlaylist(ctx: Context, name: String, url: String, user: String = "", pass: String = "") {
        ctx.dataStore.edit { p ->
            val n = decode(p[PLAYLISTS]) + Playlist(name, url, user, pass)
            p[PLAYLISTS] = encode(n)
            p[ACTIVE] = n.lastIndex
        }
    }

    suspend fun updatePlaylist(ctx: Context, i: Int, pl: Playlist) {
        ctx.dataStore.edit { p ->
            val cur = decode(p[PLAYLISTS]).toMutableList()
            if (i in cur.indices) {
                cur[i] = pl
                p[PLAYLISTS] = encode(cur)
                p[ACTIVE] = i
            }
        }
    }

    suspend fun setActive(ctx: Context, i: Int) { ctx.dataStore.edit { it[ACTIVE] = i } }

    suspend fun removePlaylist(ctx: Context, i: Int) {
        ctx.dataStore.edit { p ->
            val n = decode(p[PLAYLISTS]).filterIndexed { idx, _ -> idx != i }
            p[PLAYLISTS] = encode(n)
            p[ACTIVE] = (p[ACTIVE] ?: 0).coerceIn(0, maxOf(0, n.lastIndex))
        }
    }

    fun favorites(ctx: Context): Flow<Set<String>> = ctx.dataStore.data.map { it[FAV] ?: emptySet() }
    fun hidden(ctx: Context): Flow<Set<String>> = ctx.dataStore.data.map { it[HIDDEN] ?: emptySet() }
    fun locked(ctx: Context): Flow<Set<String>> = ctx.dataStore.data.map { it[LOCKED] ?: emptySet() }

    private suspend fun toggle(ctx: Context, key: Preferences.Key<Set<String>>, v: String) {
        ctx.dataStore.edit { p ->
            val cur = p[key] ?: emptySet()
            p[key] = if (v in cur) cur - v else cur + v
        }
    }
    suspend fun toggleFavorite(ctx: Context, id: String) = toggle(ctx, FAV, id)
    suspend fun toggleHidden(ctx: Context, g: String) = toggle(ctx, HIDDEN, g)
    suspend fun toggleLocked(ctx: Context, g: String) = toggle(ctx, LOCKED, g)

    fun recent(ctx: Context): Flow<List<String>> =
        ctx.dataStore.data.map { (it[RECENT] ?: "").split(",").filter { s -> s.isNotBlank() } }

    suspend fun addRecent(ctx: Context, id: String) {
        ctx.dataStore.edit { p ->
            val cur = (p[RECENT] ?: "").split(",").filter { it.isNotBlank() && it != id }
            p[RECENT] = (listOf(id) + cur).take(30).joinToString(",")
        }
    }
    suspend fun clearRecent(ctx: Context) { ctx.dataStore.edit { it[RECENT] = ""; it[PROGRESS] = "" } }
    suspend fun clearRecentChannels(ctx: Context) { ctx.dataStore.edit { it[RECENT] = "" } }
    suspend fun clearWatchProgress(ctx: Context) { ctx.dataStore.edit { it[PROGRESS] = "" } }

    // ---- متابعة المشاهدة (أفلام وحلقات)
    private fun decodeProgress(s: String?): List<WatchProgress> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                WatchProgress(
                    Channel(
                        id = o.getString("id"), num = o.optInt("num", 0), name = o.getString("name"),
                        logo = o.optString("logo", ""), url = o.getString("url"),
                        group = o.optString("group", ""), xtream = o.optBoolean("x", false)
                    ),
                    pos = o.getLong("pos"), dur = o.getLong("dur"), at = o.optLong("at", 0L)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun encodeProgress(list: List<WatchProgress>): String =
        JSONArray().also { a ->
            list.forEach {
                a.put(
                    JSONObject().put("id", it.ch.id).put("num", it.ch.num).put("name", it.ch.name)
                        .put("logo", it.ch.logo).put("url", it.ch.url).put("group", it.ch.group)
                        .put("x", it.ch.xtream).put("pos", it.pos).put("dur", it.dur).put("at", it.at)
                )
            }
        }.toString()

    fun progress(ctx: Context): Flow<List<WatchProgress>> = ctx.dataStore.data.map { decodeProgress(it[PROGRESS]) }

    /** يحفظ الموضع، أو يحذف العنصر إذا شارف على النهاية (آخر دقيقة ونصف أو 95%). */
    suspend fun saveProgress(ctx: Context, wp: WatchProgress) {
        ctx.dataStore.edit { p ->
            val rest = decodeProgress(p[PROGRESS]).filter { it.ch.id != wp.ch.id }
            val finished = wp.pos >= wp.dur * 0.95 || wp.dur - wp.pos < 90_000
            p[PROGRESS] = encodeProgress(if (finished) rest else (listOf(wp) + rest).take(30))
        }
    }

    suspend fun removeProgress(ctx: Context, id: String) {
        ctx.dataStore.edit { p -> p[PROGRESS] = encodeProgress(decodeProgress(p[PROGRESS]).filter { it.ch.id != id }) }
    }

    // ---- أعضاء الخادم: جلسة العضو، وسر المالك (على جهاز المالك فقط)
    fun adminSecret(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[ADMIN_SECRET] ?: "" }
    suspend fun setAdminSecret(ctx: Context, v: String) { ctx.dataStore.edit { it[ADMIN_SECRET] = v.trim() } }

    fun memberToken(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[MEMBER_TOKEN] ?: "" }
    suspend fun setMemberToken(ctx: Context, v: String) { ctx.dataStore.edit { it[MEMBER_TOKEN] = v } }

    /** آخر مرة أكد فيها الخادم صلاحية جلسة العضو (للسماح بفترة قصيرة بلا إنترنت). */
    fun memberLastOk(ctx: Context): Flow<Long> = ctx.dataStore.data.map { it[MEMBER_LAST_OK] ?: 0L }
    suspend fun setMemberLastOk(ctx: Context, v: Long) { ctx.dataStore.edit { it[MEMBER_LAST_OK] = v } }

    /** معرّف احتياطي للجهاز إذا لم يتوفر ANDROID_ID. */
    suspend fun deviceFallback(ctx: Context): String {
        var id = ""
        ctx.dataStore.edit { p ->
            id = p[DEVICE_FALLBACK] ?: java.util.UUID.randomUUID().toString().also { p[DEVICE_FALLBACK] = it }
        }
        return id
    }

    fun pin(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[PIN] ?: "" }
    suspend fun setPin(ctx: Context, pin: String) { ctx.dataStore.edit { it[PIN] = pin } }

    fun external(ctx: Context): Flow<Boolean> = ctx.dataStore.data.map { it[EXTERNAL] ?: false }
    suspend fun setExternal(ctx: Context, v: Boolean) { ctx.dataStore.edit { it[EXTERNAL] = v } }

    private fun decodeUsers(s: String?): List<AppUser> {
        if (s.isNullOrBlank()) return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            AppUser(o.getString("n"), o.getString("s"), o.getString("h"), o.optBoolean("o", false))
        }
    }

    private fun encodeUsers(list: List<AppUser>): String =
        JSONArray().also { a ->
            list.forEach { a.put(JSONObject().put("n", it.name).put("s", it.salt).put("h", it.hash).put("o", it.owner)) }
        }.toString()

    fun users(ctx: Context): Flow<List<AppUser>> = ctx.dataStore.data.map { decodeUsers(it[USERS]) }
    suspend fun saveUsers(ctx: Context, list: List<AppUser>) { ctx.dataStore.edit { it[USERS] = encodeUsers(list) } }

    /** ينشئ حساب المالك الافتراضي عند أول تشغيل. */
    suspend fun ensureOwner(ctx: Context) {
        ctx.dataStore.edit { p ->
            val cur = decodeUsers(p[USERS])
            if (cur.none { it.owner }) p[USERS] = encodeUsers(listOf(Auth.defaultOwner()) + cur)
        }
    }

    fun session(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[SESSION] ?: "" }
    suspend fun setSession(ctx: Context, name: String) { ctx.dataStore.edit { it[SESSION] = name } }

    /** مكان حفظ التسجيلات: "" أو "dir:<path>" أو "tree:<uri>" */
    fun recordDir(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[RECORD_DIR] ?: "" }
    suspend fun setRecordDir(ctx: Context, v: String) { ctx.dataStore.edit { it[RECORD_DIR] = v } }

    fun tmdbKey(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[TMDB_KEY] ?: "" }
    suspend fun setTmdbKey(ctx: Context, v: String) { ctx.dataStore.edit { it[TMDB_KEY] = v.trim() } }

    fun trustAll(ctx: Context): Flow<Boolean> = ctx.dataStore.data.map { it[TRUST_ALL] ?: false }
    suspend fun setTrustAll(ctx: Context, v: Boolean) { ctx.dataStore.edit { it[TRUST_ALL] = v } }

    /** أقصى ارتفاع للفيديو بالبكسل (1080/720/480/360/240)، و0 = تلقائي. */
    fun quality(ctx: Context): Flow<Int> = ctx.dataStore.data.map { it[QUALITY] ?: 0 }
    suspend fun setQuality(ctx: Context, v: Int) { ctx.dataStore.edit { it[QUALITY] = v } }

    /** صيغة بث Xtream: ts أو m3u8 */
    fun format(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[FORMAT] ?: "ts" }
    suspend fun setFormat(ctx: Context, v: String) { ctx.dataStore.edit { it[FORMAT] = v } }

    fun liveSort(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[LIVE_SORT] ?: "default" }
    suspend fun setLiveSort(ctx: Context, v: String) { ctx.dataStore.edit { it[LIVE_SORT] = v } }
    fun subSize(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[SUB_SIZE] ?: "medium" }
    suspend fun setSubSize(ctx: Context, v: String) { ctx.dataStore.edit { it[SUB_SIZE] = v } }
    fun subStyle(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[SUB_STYLE] ?: "white" }
    suspend fun setSubStyle(ctx: Context, v: String) { ctx.dataStore.edit { it[SUB_STYLE] = v } }
    fun layout(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[LAYOUT] ?: "auto" }
    suspend fun setLayout(ctx: Context, v: String) { ctx.dataStore.edit { it[LAYOUT] = v } }
}
