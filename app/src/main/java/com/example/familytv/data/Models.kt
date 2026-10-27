package com.example.familytv.data

data class Channel(
    val id: String,          // فريد وثابت (للمفضلة والمفاتيح)
    val num: Int,            // رقم القناة للعرض (من Xtream إن وُجد)
    val name: String,
    val logo: String,
    val url: String,         // لقنوات Xtream: رابط بدون امتداد، يُضاف الامتداد وقت التشغيل
    val group: String,
    val xtream: Boolean = false
)

/** user/pass غير فارغين = حساب Xtream (url هو الخادم). وإلا فهو رابط M3U أو ملف محلي. */
/** حساب دخول للتطبيق. owner = المالك (يستطيع إدارة المستخدمين). */
data class AppUser(val name: String, val salt: String, val hash: String, val owner: Boolean = false)

data class Playlist(val name: String, val url: String, val user: String = "", val pass: String = "")

/** حلقة مسلسل (Xtream). url كامل بامتداده. */
data class Episode(val id: String, val season: Int, val num: Int, val title: String, val url: String)

/** موضع توقف في فيلم أو حلقة. ch هو العنصر القابل للتشغيل كما هو، فيعمل الاستئناف دون إعادة تحميل القوائم. */
data class WatchProgress(val ch: Channel, val pos: Long, val dur: Long, val at: Long)

/**
 * عضو مسجّل على خادم Supabase. التواريخ بالميلي ثانية (0 = لا يوجد).
 * deviceBound = الحساب مرتبط بجهاز. online = أرسل نبضة خلال آخر 3 دقائق.
 */
data class Member(
    val name: String, val created: Long, val expires: Long, val disabled: Boolean, val by: String,
    val deviceBound: Boolean, val boundAt: Long, val lastSeen: Long,
    val watching: String, val minutes: Int, val online: Boolean
) {
    fun expired(now: Long = System.currentTimeMillis()): Boolean = expires in 1..now
}

/** بيانات اشتراك Xtream كما يرسلها الخادم (user_info). */
data class XtreamInfo(
    val status: String, val created: Long?, val expires: Long?,
    val active: Int, val max: Int, val trial: Boolean
)
