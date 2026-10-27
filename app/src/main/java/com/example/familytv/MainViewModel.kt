package com.example.familytv

import android.app.Application
import android.net.TrafficStats
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.familytv.data.AppUser
import com.example.familytv.data.Auth
import com.example.familytv.data.Channel
import com.example.familytv.data.ApiException
import com.example.familytv.data.Member
import com.example.familytv.data.SbConfig
import com.example.familytv.data.SupabaseApi
import com.example.familytv.data.XtreamInfo
import org.json.JSONObject
import com.example.familytv.data.Playlist
import com.example.familytv.data.Prefs
import com.example.familytv.data.Repository
import com.example.familytv.data.WatchProgress
import com.example.familytv.data.XtreamException
import com.example.familytv.data.XtreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    var current by mutableStateOf<Channel?>(null); private set
    var playError by mutableStateOf(false); private set
    var playErrorInfo by mutableStateOf(""); private set
    var unlocked by mutableStateOf<Set<String>>(emptySet()); private set

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                androidx.media3.datasource.DefaultDataSource.Factory(
                    app,
                    androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
                        // يُجلب العميل عند كل طلب حتى يلتقط تغيير خيار الشهادات دون إعادة تشغيل
                        object : okhttp3.Call.Factory {
                            override fun newCall(request: okhttp3.Request): okhttp3.Call =
                                com.example.familytv.data.Net.client().newCall(request)
                        }
                    ).setUserAgent(com.example.familytv.data.Net.USER_AGENT)
                )
            )
        )
        .build()

    // ---- صورة داخل صورة: تضبطها MainActivity وتقرؤها شاشة التشغيل
    @Volatile var inFullscreen = false
    @Volatile var pipEntered = false
    /** تُضبط عند الخروج من ملء الشاشة، وتستهلكها شاشة البث المباشر لإعادة التركيز إلى القناة الجارية. */
    var focusRestore by mutableStateOf(false)
    var pipActive by mutableStateOf(false)

    // ---- مؤقت النوم: يوقف التشغيل بعد مدة ثم تُغلق شاشة التشغيل
    var sleepEndsAt by mutableStateOf(0L); private set
    var sleepFired by mutableStateOf(0); private set
    private var sleepJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) { sleepEndsAt = 0L; return }
        val ms = minutes * 60_000L
        sleepEndsAt = System.currentTimeMillis() + ms
        sleepJob = viewModelScope.launch {
            kotlinx.coroutines.delay(ms)
            saveProgressNow()
            player.pause()
            sleepEndsAt = 0L
            sleepFired++
            Toast.makeText(app, "مؤقت النوم: تم إيقاف التشغيل", Toast.LENGTH_LONG).show()
        }
    }

    // ---- الحلقة التالية تلقائياً: قائمة حلقات المسلسل الذي بدأ منه التشغيل
    private var queueShow: Channel? = null
    private var queue by mutableStateOf<List<com.example.familytv.data.Episode>>(emptyList())

    /** الحلقة التي تلي الحلقة الجارية، أو null (قناة، فيلم، آخر حلقة، أو استئناف من "تابع المشاهدة"). */
    val nextEpisode: com.example.familytv.data.Episode?
        get() {
            val i = queue.indexOfFirst { "e${it.id}" == current?.id }
            return if (i >= 0) queue.getOrNull(i + 1) else null
        }

    fun playNext() {
        val show = queueShow ?: return
        nextEpisode?.let { playEpisode(show, it) }
    }

    // متابعة المشاهدة: تُحفظ مواضع الأفلام والحلقات فقط (معرّفها m123 أو e456)، لا القنوات المباشرة
    private val vodId = Regex("^[me]\\d+$")
    private var tracking = false

    // قياس جودة التدفق
    private val stallTimes = ArrayList<Long>()
    private var wasReady = false
    private var bufferingSince = 0L
    private var lastRx = -1L
    private var lastRxTime = 0L
    private var smoothMbps: Float? = null

    private fun <T> flow(f: Flow<T>, init: T) = f.stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val playlists = flow(Prefs.playlists(app), emptyList<Playlist>())
    val activeIndex = flow(Prefs.activeIndex(app), 0)
    val favorites = flow(Prefs.favorites(app), emptySet<String>())
    val hidden = flow(Prefs.hidden(app), emptySet<String>())
    val locked = flow(Prefs.locked(app), emptySet<String>())
    val recent = flow(Prefs.recent(app), emptyList<String>())
    val progress = flow(Prefs.progress(app), emptyList<WatchProgress>())
    val pin = flow(Prefs.pin(app), "")
    val external = flow(Prefs.external(app), false)
    val format = flow(Prefs.format(app), "ts")
    val quality = flow(Prefs.quality(app), 0)
    val liveSort = flow(Prefs.liveSort(app), "default")
    val subSize = flow(Prefs.subSize(app), "medium")
    val subStyle = flow(Prefs.subStyle(app), "white")
    val layout = flow(Prefs.layout(app), "auto")

    private fun next(cur: String, order: List<String>) = order[(order.indexOf(cur) + 1).mod(order.size)]
    fun cycleLiveSort() { viewModelScope.launch { Prefs.setLiveSort(app, next(liveSort.value, listOf("default", "az", "za"))) } }
    fun cycleSubSize() { viewModelScope.launch { Prefs.setSubSize(app, next(subSize.value, listOf("small", "medium", "large"))) } }
    fun cycleSubStyle() { viewModelScope.launch { Prefs.setSubStyle(app, next(subStyle.value, listOf("white", "yellow", "box"))) } }
    fun cycleLayout() { viewModelScope.launch { Prefs.setLayout(app, next(layout.value, listOf("auto", "phone", "tv"))) } }
    fun clearChannelHistory() { viewModelScope.launch { Prefs.clearRecentChannels(app) } }
    fun clearWatchHistory() { viewModelScope.launch { Prefs.clearWatchProgress(app) } }

    fun setQuality(height: Int) { viewModelScope.launch { Prefs.setQuality(app, height) } }

    /** يحدّ جودة الفيديو للبث المتعدد الجودات (HLS). يسري فوراً دون إعادة تشغيل. */
    private fun applyQuality(h: Int) {
        val b = player.trackSelectionParameters.buildUpon()
        if (h <= 0) {
            b.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).setMaxVideoBitrate(Int.MAX_VALUE)
        } else {
            b.setMaxVideoSize(Int.MAX_VALUE, h)
            b.setMaxVideoBitrate(
                when {
                    h >= 1080 -> Int.MAX_VALUE
                    h >= 720 -> 3_500_000
                    h >= 480 -> 1_500_000
                    h >= 360 -> 900_000
                    else -> 450_000
                }
            )
        }
        player.trackSelectionParameters = b.build()
    }
    val recordDir = flow(Prefs.recordDir(app), "")
    val trustAll = flow(Prefs.trustAll(app), false)
    val tmdbKey = flow(Prefs.tmdbKey(app), "")
    fun setTmdbKey(v: String) { viewModelScope.launch { Prefs.setTmdbKey(app, v) } }

    fun toggleTrustAll() {
        viewModelScope.launch {
            val v = !trustAll.value
            Prefs.setTrustAll(app, v)
            com.example.familytv.data.Net.setTrustAll(v)
            load(currentPlaylist)
        }
    }

    val users = flow(Prefs.users(app), emptyList<AppUser>())

    var authReady by mutableStateOf(false); private set
    var sessionUser by mutableStateOf<String?>(null); private set
    private var failures = 0
    private var lockedUntil = 0L

    init {
        viewModelScope.launch {
            Prefs.ensureOwner(app)
            val saved = Prefs.session(app).first()
            val local = Prefs.users(app).first()
            // عضو الخادم: تكفي جلسة محفوظة، ثم يتحقق منها الخادم فوراً بعد فتح التطبيق
            sessionUser = local.firstOrNull { it.name == saved }?.name
                ?: saved.takeIf { it.isNotBlank() && Prefs.memberToken(app).first().isNotBlank() }
            authReady = true
            if (sessionUser != null && local.none { it.name == sessionUser }) memberCheck()
        }
    }

    private fun loginOk(name: String, stay: Boolean) {
        failures = 0
        sessionUser = name
        viewModelScope.launch { Prefs.setSession(app, if (stay) name else "") }
    }

    private fun loginFailed(now: Long) {
        failures++
        if (failures >= 5) { lockedUntil = now + 30_000; failures = 0 }
    }

    /** معرّف ثابت للجهاز (بصمة ANDROID_ID). لا يتغير بحذف بيانات التطبيق، ولا يُنقل بالنسخ الاحتياطي. */
    private suspend fun deviceId(): String = withContext(Dispatchers.IO) {
        val raw = android.provider.Settings.Secure
            .getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: Prefs.deviceFallback(app)
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("familytv:$raw".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun dateText(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    /** onResult(null) = نجاح، وإلا رسالة الخطأ. */
    fun login(name: String, pass: String, stay: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                onResult("محاولات كثيرة. أعد المحاولة بعد ${(lockedUntil - now) / 1000 + 1} ثانية")
                return@launch
            }
            val typed = name.trim()
            val u = Prefs.users(app).first().firstOrNull { it.name.equals(typed, ignoreCase = true) }

            // 1) حسابات الجهاز (المالك والمستخدمون المضافون محلياً)
            if (u != null) {
                if (withContext(Dispatchers.Default) { Auth.verify(pass, u) }) {
                    Prefs.setMemberToken(app, "")
                    loginOk(u.name, stay)
                    onResult(null)
                } else { loginFailed(now); onResult("اسم المستخدم أو كلمة المرور غير صحيحة") }
                return@launch
            }

            // 2) أعضاء الخادم: الحساب مرتبط بجهاز واحد
            if (!SbConfig.configured) {
                loginFailed(now)
                onResult("اسم المستخدم أو كلمة المرور غير صحيحة")
                return@launch
            }
            val dev = deviceId()
            val r = try {
                withContext(Dispatchers.IO) {
                    SupabaseApi.rpc("fm_login", JSONObject().put("p_name", typed).put("p_pass", pass).put("p_device", dev))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                onResult("خطأ في الخادم (${e.message}). تحقق من إعداد Supabase."); return@launch
            } catch (e: Exception) {
                onResult("تعذر الوصول إلى الخادم. تحقق من اتصال الإنترنت."); return@launch
            }
            if (r.optBoolean("ok", false)) {
                Prefs.setMemberToken(app, r.getString("token"))
                Prefs.setMemberLastOk(app, now)
                loginOk(r.getString("name"), stay)
                onResult(null)
                return@launch
            }
            when (r.optString("code")) {
                "not_registered" -> { loginFailed(now); onResult("اسم المستخدم هذا غير مسجل") }
                "wrong_password" -> { loginFailed(now); onResult("كلمة المرور غير صحيحة") }
                "locked" -> onResult("محاولات خاطئة كثيرة على هذا الحساب. أعد المحاولة بعد دقائق.")
                "disabled" -> onResult("هذا الحساب معطّل")
                "expired" -> onResult("انتهى الاشتراك في ${dateText(r.optLong("expires", now))}")
                "other_device" -> onResult("هذا الحساب مرتبط بجهاز آخر. اطلب من المالك إعادة ضبطه.")
                else -> onResult("تعذر تسجيل الدخول (${r.optString("code")})")
            }
        }
    }

    private fun kick(msg: String) {
        Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        logout()
    }

    /**
     * نبضة عضو الخادم (عند الفتح وكل دقيقة): يتحقق الخادم من الجلسة والجهاز والاشتراك ويسجّل الحضور.
     * عند الرفض يُخرج العضو. وعند انقطاع الإنترنت يُسمح 72 ساعة ثم يُطلب الاتصال.
     */
    private suspend fun memberCheck() {
        val me = sessionUser ?: return
        if (Prefs.users(app).first().any { it.name == me }) return
        val token = Prefs.memberToken(app).first()
        if (token.isBlank() || !SbConfig.configured) { kick("يرجى تسجيل الدخول مجدداً."); return }
        val watching = current?.name ?: ""
        val playing = player.isPlaying
        val dev = deviceId()
        val now = System.currentTimeMillis()
        try {
            val r = withContext(Dispatchers.IO) {
                SupabaseApi.rpc(
                    "fm_check",
                    JSONObject().put("p_name", me).put("p_device", dev).put("p_token", token)
                        .put("p_watching", watching).put("p_playing", playing)
                )
            }
            if (r.optBoolean("ok", false)) {
                Prefs.setMemberLastOk(app, now)
            } else {
                kick(
                    when (r.optString("code")) {
                        "disabled" -> "تم تعطيل هذا الحساب."
                        "expired" -> "انتهى اشتراكك."
                        "revoked" -> "أعاد المالك ضبط هذا الحساب أو أنه مستخدم على جهاز آخر."
                        else -> "انتهى وصولك."
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (now - Prefs.memberLastOk(app).first() > 72 * 3_600_000L) {
                kick("اتصل بالإنترنت للتحقق من اشتراكك.")
            }
        }
    }

    fun logout() {
        setSleepTimer(0)
        saveProgressNow()
        player.stop()
        player.clearMediaItems()
        current = null
        sessionUser = null
        viewModelScope.launch { Prefs.setSession(app, ""); Prefs.setMemberToken(app, "") }
    }

    fun changePassword(old: String, new: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val list = Prefs.users(app).first()
            val me = list.firstOrNull { it.name == sessionUser }
            if (me == null) { onResult("لم تسجل الدخول"); return@launch }
            if (new.length < 4) { onResult("كلمة المرور الجديدة قصيرة جداً (4 أحرف على الأقل)"); return@launch }
            if (!withContext(Dispatchers.Default) { Auth.verify(old, me) }) {
                onResult("كلمة المرور الحالية غير صحيحة"); return@launch
            }
            val salt = Auth.newSalt()
            val h = withContext(Dispatchers.Default) { Auth.hash(new, salt) }
            Prefs.saveUsers(app, list.map { if (it.name == me.name) it.copy(salt = salt, hash = h) else it })
            onResult("تم تغيير كلمة المرور")
        }
    }

    fun addUser(name: String, pass: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val list = Prefs.users(app).first()
            val me = list.firstOrNull { it.name == sessionUser }
            val n = name.trim()
            when {
                me == null || !me.owner -> onResult("المالك فقط يمكنه إضافة مستخدمين")
                n.length < 3 -> onResult("اسم المستخدم قصير جداً (3 أحرف على الأقل)")
                pass.length < 4 -> onResult("كلمة المرور قصيرة جداً (4 أحرف على الأقل)")
                list.any { it.name.equals(n, ignoreCase = true) } -> onResult("اسم المستخدم موجود مسبقاً")
                else -> {
                    val salt = Auth.newSalt()
                    val h = withContext(Dispatchers.Default) { Auth.hash(pass, salt) }
                    Prefs.saveUsers(app, list + AppUser(n, salt, h, owner = false))
                    onResult("تمت إضافة المستخدم")
                }
            }
        }
    }

    fun removeUser(name: String) {
        viewModelScope.launch {
            val list = Prefs.users(app).first()
            val me = list.firstOrNull { it.name == sessionUser }
            if (me != null && me.owner && name != me.name) {
                Prefs.saveUsers(app, list.filter { it.name != name || it.owner })
            }
        }
    }

    // ---------- لوحة التحكم (للمالك فقط) ----------

    val adminSecret = flow(Prefs.adminSecret(app), "")
    fun setAdminSecret(v: String) { viewModelScope.launch { Prefs.setAdminSecret(app, v) } }

    private val isOwnerNow: Boolean
        get() = users.value.firstOrNull { it.name == sessionUser }?.owner == true

    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var membersBusy by mutableStateOf(false); private set
    var membersError by mutableStateOf(""); private set

    class XtreamStat(val playlist: Playlist, val info: XtreamInfo?, val error: String?)
    var xtreamStats by mutableStateOf<List<XtreamStat>>(emptyList()); private set
    var statsLoading by mutableStateOf(false); private set

    /** يستدعي دالة مالك على الخادم ويضيف السر. السر الخاطئ يُقفل 10 دقائق بعد 5 محاولات. */
    private suspend fun admin(fn: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!SbConfig.configured) throw ApiException("عيّن رابط Supabase والمفتاح في data/Server.kt ثم أعد البناء.")
        val secret = adminSecret.value.trim()
        if (secret.isEmpty()) throw ApiException("أدخل سر المشرف أولاً.")
        SupabaseApi.rpc(fn, args.put("p_secret", secret))
    }

    private fun explain(code: String) = when (code) {
        "denied" -> "سر المشرف خاطئ (5 محاولات خاطئة تقفله 10 دقائق)."
        "exists" -> "اسم المستخدم موجود مسبقاً"
        "not_found" -> "المستخدم غير موجود"
        "bad_name" -> "اسم المستخدم: من 3 إلى 32 حرفاً أو رقماً أو . _ - فقط"
        "weak_password" -> "كلمة المرور قصيرة جداً (6 أحرف على الأقل)"
        else -> "خطأ في الخادم ($code)"
    }

    private fun networkError(e: Exception) = when (e) {
        is ApiException -> if (e.message?.startsWith("HTTP") == true) "خطأ في الخادم (${e.message}). شغّل supabase/setup.sql أولاً." else e.message ?: "خطأ"
        else -> "تعذر الوصول إلى الخادم: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun parseMembers(a: org.json.JSONArray): List<Member> = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Member(
            name = o.getString("name"), created = o.optLong("created", 0L), expires = o.optLong("expires", 0L),
            disabled = o.optBoolean("disabled", false), by = o.optString("by", ""),
            deviceBound = o.optBoolean("bound", false), boundAt = o.optLong("bound_at", 0L),
            lastSeen = o.optLong("last_seen", 0L), watching = o.optString("watching", ""),
            minutes = o.optInt("minutes", 0), online = o.optBoolean("online", false)
        )
    }

    /** يحمّل قائمة الأعضاء. يعيد رسالة خطأ أو null. */
    private suspend fun loadMembers(): String? {
        return try {
            val r = admin("fm_admin_list", JSONObject())
            if (r.optBoolean("ok", false)) { members = parseMembers(r.getJSONArray("members")); null }
            else explain(r.optString("code"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            networkError(e)
        }
    }

    fun refreshMembers() {
        if (!isOwnerNow) return
        viewModelScope.launch {
            membersBusy = true
            membersError = loadMembers() ?: ""
            membersBusy = false
        }
    }

    private fun adminAction(fn: String, args: JSONObject, onDone: (Boolean, String) -> Unit) {
        if (!isOwnerNow) { onDone(false, "المالك فقط يمكنه تنفيذ هذا"); return }
        viewModelScope.launch {
            membersBusy = true
            try {
                val r = admin(fn, args)
                if (r.optBoolean("ok", false)) {
                    onDone(true, "تم الحفظ")
                    membersError = loadMembers() ?: ""
                } else onDone(false, explain(r.optString("code")))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone(false, networkError(e))
            }
            membersBusy = false
        }
    }

    /** days = 0 يعني بلا انتهاء. كل الأعضاء المولَّدين مستخدمون عاديون؛ المالك الوحيد هو حساب الجهاز. */
    fun addMember(name: String, pass: String, days: Int, onDone: (Boolean, String) -> Unit) {
        val n = name.trim()
        viewModelScope.launch {
            if (Prefs.users(app).first().any { it.name.equals(n, ignoreCase = true) }) {
                onDone(false, "اسم المستخدم موجود مسبقاً")
                return@launch
            }
            adminAction(
                "fm_admin_add",
                JSONObject().put("p_name", n).put("p_pass", pass).put("p_days", days).put("p_by", sessionUser ?: ""),
                onDone
            )
        }
    }

    fun extendMember(name: String, days: Int, onDone: (Boolean, String) -> Unit) =
        adminAction("fm_admin_extend", JSONObject().put("p_name", name).put("p_days", days), onDone)

    fun setMemberDisabled(name: String, disabled: Boolean, onDone: (Boolean, String) -> Unit) =
        adminAction("fm_admin_disable", JSONObject().put("p_name", name).put("p_disabled", disabled), onDone)

    fun deleteMember(name: String, onDone: (Boolean, String) -> Unit) =
        adminAction("fm_admin_delete", JSONObject().put("p_name", name), onDone)

    /** يفك ارتباط الحساب بجهازه: يُقطع الجهاز القديم، ويرتبط الحساب بأول جهاز يدخل بعد ذلك. */
    fun resetDevice(name: String, onDone: (Boolean, String) -> Unit) =
        adminAction("fm_admin_reset_device", JSONObject().put("p_name", name), onDone)

    /** المتصلون الآن وتاريخا البداية والانتهاء لكل حساب Xtream محفوظ. */
    fun loadXtreamStats() {
        if (!isOwnerNow) return
        viewModelScope.launch {
            statsLoading = true
            com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
            xtreamStats = playlists.value.filter { it.user.isNotBlank() }.map { pl ->
                try {
                    XtreamStat(pl, Repository.xtreamInfo(pl.url, pl.user, pl.pass), null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: XtreamException) {
                    XtreamStat(pl, null, e.message)
                } catch (e: Exception) {
                    XtreamStat(pl, null, "تعذر الوصول إلى الخادم")
                }
            }
            statsLoading = false
        }
    }

    private var currentPlaylist: Playlist? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playError = true
                playErrorInfo = "${error.errorCodeName}  ${error.cause?.message ?: ""}".trim()
            }
            override fun onPlaybackStateChanged(state: Int) {
                val now = System.currentTimeMillis()
                when (state) {
                    Player.STATE_READY -> { playError = false; wasReady = true; bufferingSince = 0L }
                    Player.STATE_BUFFERING -> {
                        if (bufferingSince == 0L) bufferingSince = now
                        if (wasReady) stallTimes.add(now)
                    }
                    Player.STATE_ENDED -> {
                        wasReady = false
                        if (tracking) current?.let { c -> viewModelScope.launch { Prefs.removeProgress(app, c.id) } }
                        if (nextEpisode != null) playNext()
                    }
                    else -> wasReady = false
                }
            }
        })
        viewModelScope.launch { quality.collect { applyQuality(it) } }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                if (player.isPlaying) saveProgressNow()
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
                refreshQuietly()
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                memberCheck()
            }
        }
        viewModelScope.launch {
            combine(Prefs.playlists(app), Prefs.activeIndex(app)) { l, i -> l.getOrNull(i) }
                .distinctUntilChanged()
                .collectLatest { pl -> currentPlaylist = pl; load(pl) }
        }
    }

    /** تحديث صامت: لا يمسح القنوات الحالية ولا يعرض شاشة تحميل، ويبقى القديم إن فشل. */
    private suspend fun refreshQuietly() {
        val pl = currentPlaylist ?: return
        if (pl.user.isNotBlank() || pl.url.startsWith("file://")) return
        try {
            val list = Repository.loadPlaylist(app, pl.url)
            if (list.isNotEmpty() && currentPlaylist == pl) channels = list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { /* نبقي القائمة الحالية */ }
    }

    private suspend fun load(pl: Playlist?) {
        com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
        error = null
        if (pl == null) { channels = emptyList(); loading = false; return }
        loading = true
        try {
            val list = when {
                pl.user.isNotBlank() -> Repository.loadXtream(pl.url, pl.user, pl.pass)
                com.example.familytv.data.IptvOrg.isPack(pl.url) ->
                    com.example.familytv.data.IptvOrg.load(app) { partial ->
                        if (currentPlaylist == pl) { channels = partial; loading = false }
                    }
                else -> Repository.loadPlaylist(app, pl.url)
            }
            channels = list
            if (list.isEmpty()) error = "لم يُعثر على قنوات. تحقق من الحساب أو الرابط."
        } catch (e: CancellationException) {
            throw e
        } catch (e: XtreamException) {
            channels = emptyList()
            error = e.message
        } catch (e: Exception) {
            channels = emptyList()
            error = "تعذر التحميل: ${e.message ?: e.javaClass.simpleName}"
        }
        loading = false
    }

    // أفلام Xtream (VOD)
    var movies by mutableStateOf<List<Channel>>(emptyList()); private set
    var moviesLoading by mutableStateOf(false); private set
    var moviesError by mutableStateOf<String?>(null); private set
    private var moviesFor: Playlist? = null

    fun loadMovies(force: Boolean = false) {
        val pl = currentPlaylist
        if (pl == null || pl.user.isBlank()) {
            movies = emptyList(); moviesError = null; moviesFor = null
            return
        }
        if (!force && moviesFor == pl && movies.isNotEmpty()) return
        if (moviesLoading && !force) return
        viewModelScope.launch {
            moviesLoading = true
            moviesError = null
            try {
                com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
                val list = Repository.loadXtreamMovies(pl.url, pl.user, pl.pass)
                if (currentPlaylist == pl) {
                    movies = list
                    moviesFor = pl
                    if (list.isEmpty()) moviesError = "لا توجد أفلام في هذا الحساب."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamException) {
                movies = emptyList(); moviesError = e.message
            } catch (e: Exception) {
                movies = emptyList(); moviesError = "تعذر تحميل الأفلام: ${e.message ?: e.javaClass.simpleName}"
            }
            moviesLoading = false
        }
    }

    // مسلسلات Xtream
    var series by mutableStateOf<List<Channel>>(emptyList()); private set
    var seriesLoading by mutableStateOf(false); private set
    var seriesError by mutableStateOf<String?>(null); private set
    private var seriesFor: Playlist? = null

    var openSeries by mutableStateOf<Channel?>(null); private set
    var episodes by mutableStateOf<List<com.example.familytv.data.Episode>>(emptyList()); private set
    var episodesLoading by mutableStateOf(false); private set
    var episodesError by mutableStateOf<String?>(null); private set

    fun loadSeries(force: Boolean = false) {
        val pl = currentPlaylist
        if (seriesFor != pl) { series = emptyList(); openSeries = null; seriesFor = null }
        if (pl == null || pl.user.isBlank()) { seriesError = null; return }
        if (!force && seriesFor == pl && series.isNotEmpty()) return
        if (seriesLoading && !force) return
        viewModelScope.launch {
            seriesLoading = true
            seriesError = null
            try {
                com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
                val list = Repository.loadXtreamSeries(pl.url, pl.user, pl.pass)
                if (currentPlaylist == pl) {
                    series = list
                    seriesFor = pl
                    if (list.isEmpty()) seriesError = "لا توجد مسلسلات في هذا الحساب."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamException) {
                series = emptyList(); seriesError = e.message
            } catch (e: Exception) {
                series = emptyList(); seriesError = "تعذر تحميل المسلسلات: ${e.message ?: e.javaClass.simpleName}"
            }
            seriesLoading = false
        }
    }

    fun openSeries(ch: Channel) {
        openSeries = ch
        loadEpisodes(ch)
    }

    fun closeSeries() { openSeries = null; episodes = emptyList(); episodesError = null }

    fun loadEpisodes(ch: Channel) {
        val pl = currentPlaylist ?: return
        viewModelScope.launch {
            episodesLoading = true
            episodesError = null
            episodes = emptyList()
            try {
                val list = Repository.loadXtreamEpisodes(pl.url, pl.user, pl.pass, ch.url)
                if (openSeries?.id == ch.id) {
                    episodes = list
                    if (list.isEmpty()) episodesError = "لا توجد حلقات لهذا المسلسل."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamException) {
                episodesError = e.message
            } catch (e: Exception) {
                episodesError = "تعذر تحميل الحلقات: ${e.message ?: e.javaClass.simpleName}"
            }
            episodesLoading = false
        }
    }

    /** يشغّل حلقة: نبني Channel مؤقتاً كي يعمل المشغّل نفسه. */
    fun playEpisode(show: Channel, ep: com.example.familytv.data.Episode) {
        play(
            Channel(
                id = "e${ep.id}", num = ep.num,
                name = "${show.name}  S${ep.season}E${ep.num}",
                logo = show.logo, url = ep.url, group = show.group, xtream = false
            ),
            addToRecent = false
        )
        queueShow = show
        queue = episodes
    }

    fun retry() { viewModelScope.launch { load(currentPlaylist) } }

    /** تحديث يدوي فوري. */
    fun refreshNow() { viewModelScope.launch { load(currentPlaylist) } }

    /** رابط التشغيل النهائي (لقنوات Xtream يُضاف الامتداد حسب الصيغة المختارة). */
    fun urlFor(ch: Channel): String = if (ch.xtream) "${ch.url}.${format.value}" else ch.url

    /** يحفظ موضع التوقف الحالي إن كان فيلماً أو حلقة معروفة المدة. */
    fun saveProgressNow() {
        val ch = current ?: return
        if (!tracking) return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur < 5 * 60_000L || player.isCurrentMediaItemLive) return
        val pos = player.currentPosition
        if (pos < 30_000L) return
        val wp = WatchProgress(ch, pos, dur, System.currentTimeMillis())
        viewModelScope.launch { Prefs.saveProgress(app, wp) }
    }

    fun removeProgress(id: String) { viewModelScope.launch { Prefs.removeProgress(app, id) } }

    fun play(ch: Channel, addToRecent: Boolean = true) {
        saveProgressNow()   // موضع العنصر السابق قبل الانتقال
        tracking = vodId.matches(ch.id)
        val startAt = if (tracking) progress.value.firstOrNull { it.ch.id == ch.id }?.pos ?: 0L else 0L
        if (startAt > 0L) {
            val m = startAt / 60_000L
            Toast.makeText(app, "استئناف من %d:%02d".format(m, (startAt / 1000L) % 60), Toast.LENGTH_SHORT).show()
        }
        current = ch
        playError = false
        stallTimes.clear()
        wasReady = false
        bufferingSince = System.currentTimeMillis()
        player.setMediaItem(MediaItem.fromUri(urlFor(ch)), startAt)
        player.prepare()
        player.playWhenReady = true
        if (addToRecent) viewModelScope.launch { Prefs.addRecent(app, ch.id) }
    }

    /** سرعة التدفق التقريبية (ميغابت/ثانية) من حركة بيانات التطبيق نفسه. */
    private fun measureMbps(): Float? {
        val rx = TrafficStats.getUidRxBytes(Process.myUid())
        if (rx == TrafficStats.UNSUPPORTED.toLong()) return null
        val now = SystemClock.elapsedRealtime()
        var result: Float? = null
        if (lastRx >= 0 && now > lastRxTime) {
            val seconds = (now - lastRxTime) / 1000f
            val inst = (rx - lastRx).toFloat() * 8f / seconds / 1_000_000f
            val prev = smoothMbps
            smoothMbps = if (prev == null) inst else prev * 0.6f + inst * 0.4f
            result = smoothMbps
        }
        lastRx = rx
        lastRxTime = now
        return result
    }

    /**
     * يقدّر قوة التدفق من حالة المشغّل: حجم ما حُمّل مسبقاً، وعدد مرات التقطّع
     * في آخر 30 ثانية، ووقت الانتظار الحالي.
     */
    fun computeSignal(): Signal {
        val mbps = measureMbps()
        if (current == null) return Signal(-1, "", null)
        if (playError) return Signal(0, "لا إشارة", null)

        val now = System.currentTimeMillis()
        val state = player.playbackState
        if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) {
            val since = if (bufferingSince > 0L) bufferingSince else now
            return if (now - since > 8_000) Signal(0, "لا إشارة", mbps) else Signal(1, "جارٍ الاتصال", mbps)
        }

        val bufferSeconds = player.totalBufferedDuration / 1000f
        var level = when {
            bufferSeconds >= 8f -> 4
            bufferSeconds >= 5f -> 3
            bufferSeconds >= 2.5f -> 2
            else -> 1
        }
        stallTimes.removeAll { now - it > 30_000 }
        val stalls = stallTimes.size
        if (stalls >= 3) level = minOf(level, 1) else if (stalls >= 1) level = minOf(level, 2)

        val label = when (level) {
            4 -> "ممتازة"
            3 -> "جيدة"
            2 -> "ضعيفة"
            else -> "ضعيفة جداً"
        }
        return Signal(level, label, mbps)
    }

    fun setRecordDir(v: String) = viewModelScope.launch { Prefs.setRecordDir(app, v) }

    fun toggleFormat() {
        viewModelScope.launch {
            Prefs.setFormat(app, if (format.value == "ts") "m3u8" else "ts")
            current?.let { play(it) }
        }
    }

    fun addXtream(name: String, server: String, user: String, pass: String) = viewModelScope.launch {
        Prefs.addPlaylist(app, name.ifBlank { "Xtream" }, Repository.normalizeServer(server), user.trim(), pass.trim())
    }

    /** يستورد ملف قائمة (M3U) أو ملفاً نصياً فيه بيانات Xtream من تخزين الجهاز. */
    fun importFile(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { doImport(uri) }
            } catch (e: Exception) {
                "تعذرت قراءة الملف"
            }
            onDone(msg)
        }
    }

    private fun doImport(uri: Uri): String {
        val cr = app.contentResolver
        val displayName = cr.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "مستورد"
        val name = displayName.substringBeforeLast('.').ifBlank { "مستورد" }

        val dest = File(app.filesDir, "imported_${System.currentTimeMillis()}.m3u")
        cr.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: return "تعذر فتح الملف"

        val head = dest.bufferedReader().use { r ->
            val buf = CharArray(4096)
            val n = r.read(buf)
            if (n > 0) String(buf, 0, n) else ""
        }
        if (head.contains("#EXTINF") || head.trimStart().startsWith("#EXTM3U")) {
            viewModelScope.launch { Prefs.addPlaylist(app, name, "file://" + dest.absolutePath) }
            return "تم استيراد القائمة"
        }

        if (dest.length() > 64 * 1024) { dest.delete(); return "ملف غير معروف" }
        val text = dest.readText()
        dest.delete()

        val c = XtreamParser.parse(text)
        if (c != null) {
            viewModelScope.launch { Prefs.addPlaylist(app, name, Repository.normalizeServer(c.server), c.user, c.pass) }
            return "تم استيراد حساب Xtream"
        }
        val link = Regex("""https?://\S+""").findAll(text)
            .map { it.value.trim('"', ',', '}', ' ') }
            .firstOrNull { it.contains("get.php") || it.contains(".m3u") }
        if (link != null) {
            viewModelScope.launch { Prefs.addPlaylist(app, name, link) }
            return "تم استيراد القائمة"
        }
        return "ملف غير معروف. المتوقع قائمة M3U أو ملف نصي فيه الخادم واسم المستخدم وكلمة المرور."
    }

    fun updatePlaylist(i: Int, pl: Playlist) = viewModelScope.launch { Prefs.updatePlaylist(app, i, pl) }

    fun unlock(group: String) { unlocked = unlocked + group }

    fun toggleFavorite(id: String) = viewModelScope.launch { Prefs.toggleFavorite(app, id) }
    fun toggleHidden(g: String) = viewModelScope.launch { Prefs.toggleHidden(app, g) }
    fun toggleLocked(g: String) = viewModelScope.launch { Prefs.toggleLocked(app, g) }
    fun clearRecent() = viewModelScope.launch { Prefs.clearRecent(app) }
    fun setPin(p: String) = viewModelScope.launch { Prefs.setPin(app, p) }
    fun setExternal(v: Boolean) = viewModelScope.launch { Prefs.setExternal(app, v) }
    fun addPlaylist(name: String, url: String) = viewModelScope.launch { Prefs.addPlaylist(app, name, url) }
    fun setActive(i: Int) = viewModelScope.launch { Prefs.setActive(app, i) }
    fun removePlaylist(i: Int) = viewModelScope.launch { Prefs.removePlaylist(app, i) }

    /** يضيف قناة يدوية إلى قائمة "My channels" ويعرضها (ينشئ القائمة إن لم تكن موجودة). */
    fun addCustomChannel(name: String, url: String, group: String, logo: String, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            val isNew = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.familytv.data.MyChannels.add(app, name, url, group, logo)
            }
            if (isNew) {
                val fileUrl = com.example.familytv.data.MyChannels.url(app)
                val pls = Prefs.playlists(app).first()
                val idx = pls.indexOfFirst { it.url == fileUrl }
                when {
                    idx < 0 -> Prefs.addPlaylist(app, com.example.familytv.data.MyChannels.PLAYLIST_NAME, fileUrl)
                    Prefs.activeIndex(app).first() != idx -> Prefs.setActive(app, idx)
                    else -> load(currentPlaylist)
                }
            }
            onDone(isNew)
        }

    override fun onCleared() {
        player.release()
    }
}
