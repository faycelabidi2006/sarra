package com.example.familytv.data

import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** ردّ بسيط يحافظ على نفس واجهة HttpURLConnection التي يستخدمها باقي الكود. */
class NetResponse(private val resp: Response) {
    val responseCode: Int get() = resp.code
    val inputStream: InputStream get() = resp.body?.byteStream() ?: throw IOException("استجابة فارغة")
    fun getHeaderField(name: String): String? = resp.header(name)
    fun disconnect() { try { resp.close() } catch (_: Exception) {} }
}

object Net {
    /** User-Agent مقبول عند أغلب خوادم IPTV (بعضها يحجب الأسماء غير المعروفة). */
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    @Volatile private var trustAll = false
    @Volatile private var cached: OkHttpClient? = null

    /**
     * DNS: نجرّب أولاً DNS النظام. إذا فشل أو أعاد عنواناً (مثل حجب مزوّد الإنترنت)
     * نستعمل DNS مشفّر عبر HTTPS (Cloudflare ثم Google) فلا يستطيع المزوّد التلاعب بالنتيجة.
     * ملاحظة: هذا يحلّ الحجب على مستوى DNS فقط، لا الحجب على مستوى عنوان IP أو فحص الحزم.
     */
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }

    private fun doh(url: String, vararg ips: String) = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
        .build()

    private val cloudflare: DnsOverHttps by lazy { doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1") }
    private val google: DnsOverHttps by lazy { doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4") }

    /** بحث DNS مشفّر (Cloudflare ثم Google). */
    fun secureLookup(host: String): List<InetAddress> =
        try { cloudflare.lookup(host) } catch (_: Exception) { google.lookup(host) }

    private fun buildDns(): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = try { Dns.SYSTEM.lookup(hostname) } catch (_: UnknownHostException) { emptyList() }
            // عناوين محلية/صفرية = علامة حجب شائعة، فنتجاهلها
            val usable = system.filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }
            if (usable.isNotEmpty() && !hostname.endsWith(".local")) return usable
            return try { secureLookup(hostname) } catch (_: Exception) {
                if (system.isNotEmpty()) system else throw UnknownHostException(hostname)
            }
        }
    }

    @Synchronized
    fun client(): OkHttpClient {
        cached?.let { return it }
        val b = OkHttpClient.Builder()
            .dns(buildDns())
            .followRedirects(true)
            .followSslRedirects(true) // يتبع التحويل بين http و https
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
        if (trustAll) {
            @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { _, _ -> true }
        }
        return b.build().also { cached = it }
    }

    /** يفتح الاتصال (GET) ويتبع التحويلات. نفس التوقيع القديم. */
    fun open(url: String, connectMs: Int = 15_000, readMs: Int = 30_000): NetResponse {
        val c = client().newBuilder()
            .connectTimeout(connectMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        return NetResponse(c.newCall(Request.Builder().url(url).build()).execute())
    }

    /** خيار يختاره المستخدم صراحةً: قبول شهادات HTTPS غير الموثوقة. أقل أماناً، مغلق افتراضياً. */
    @Synchronized
    fun setTrustAll(allow: Boolean) {
        if (trustAll != allow) {
            trustAll = allow
            cached = null
        }
    }

    /** يحوّل الخطأ التقني إلى سبب مفهوم. */
    fun explain(e: Throwable): String {
        val msg = (e.message ?: "").trim()
        return when {
            e is UnknownHostException ->
                "DNS: تعذر تحليل اسم الخادم. قد يكون العنوان خاطئاً أو أن مزوّد الإنترنت يحجبه."
            e is java.net.SocketTimeoutException ->
                "انتهت المهلة: لم يردّ الخادم. غالباً عنوان الخادم محجوب على شبكتك (جرّب VPN) أو أن الخادم متوقف."
            e is java.net.ConnectException && msg.contains("refused", true) ->
                "تم رفض الاتصال: المنفذ خاطئ أو الخادم متوقف."
            e is java.net.ConnectException ->
                "تعذر الاتصال بالخادم. قد يكون المنفذ خاطئاً أو محجوباً."
            e is javax.net.ssl.SSLException ->
                "مشكلة في HTTPS أو الشهادة. جرّب http:// بدلاً منه أو فعّل «HTTPS غير موثوق» من الإعدادات."
            msg.contains("reset", true) || msg.contains("unexpected end of stream", true) ->
                "قطعت الشبكة الاتصال (reset). وهذا شائع عند حجب مزوّد الخدمة."
            msg.startsWith("HTTP 403") -> "رفض الخادم الطلب (HTTP 403): محجوب لبلدك أو عنوانك، أو أن الحساب غير مسموح."
            msg.startsWith("HTTP 404") -> "غير موجود (HTTP 404): عنوان الخادم أو المنفذ خاطئ."
            msg.startsWith("HTTP 401") -> "غير مصرّح (HTTP 401): اسم المستخدم أو كلمة المرور خاطئة."
            msg.startsWith("HTTP 5") -> "في الخادم مشكلة داخلية ($msg). أعد المحاولة لاحقاً."
            msg.startsWith("HTTP") -> "ردّ الخادم بخطأ ($msg)."
            else -> "تعذر الوصول إلى الخادم: ${e.javaClass.simpleName} $msg".trim()
        }
    }

    /**
     * فحص مرحلي يوضح أين يتعطل الاتصال: DNS النظام، DNS المشفّر، ثم اتصال TCP بالمنفذ.
     * يُستدعى من خيط خلفي فقط.
     */
    fun diagnose(server: String): String {
        val url = try { server.trim().let { if (it.startsWith("http", true)) it else "http://$it" }.toHttpUrl() }
                  catch (_: Exception) { return "عنوان الخادم غير صالح." }
        val host = url.host
        val port = url.port
        val out = StringBuilder()

        val sys = try { Dns.SYSTEM.lookup(host) } catch (e: Exception) { null }
        val sysBad = sys == null || sys.isEmpty() || sys.all { it.isAnyLocalAddress || it.isLoopbackAddress }
        out.append("1) DNS (عادي): ").append(
            if (sys == null) "فشل" else if (sysBad) "إجابة مشبوهة (${sys.joinToString { it.hostAddress ?: "" }})" else "OK (${sys.first().hostAddress})"
        ).append('\n')

        val sec = try { secureLookup(host) } catch (e: Exception) { null }
        out.append("2) DNS (آمن): ").append(
            if (sec == null || sec.isEmpty()) "فشل" else "OK (${sec.first().hostAddress})"
        ).append('\n')

        val ip = (if (!sysBad) sys else sec)?.firstOrNull()
        if (ip == null) {
            out.append("3) اتصال TCP: تم التخطي (لا يوجد عنوان)\n")
            out.append("=> تعذر تحليل الاسم. تحقق من العنوان.")
        } else {
            val tcp = try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, port), 8000) }
                "OK"
            } catch (e: java.net.SocketTimeoutException) { "TIMEOUT" }
            catch (e: Exception) { "فشل (${e.message ?: e.javaClass.simpleName})" }
            out.append("3) اتصال TCP بـ ${ip.hostAddress}:$port: $tcp\n")
            out.append(
                when {
                    tcp == "OK" && sysBad -> "=> يجري التلاعب بـ DNS لكن DNS الآمن يحل المشكلة."
                    tcp == "OK" -> "=> الخادم متاح. إذا استمر فشل الدخول فتحقق من اسم المستخدم وكلمة المرور."
                    tcp == "TIMEOUT" -> "=> عنوان الخادم أو منفذه لا يستجيب من شبكتك. غالباً هذا حجب ويلزم VPN أو وكيل."
                    else -> "=> الاتصال مرفوض أو مقطوع. تحقق من المنفذ أو أن الشبكة تحجبه."
                }
            )
        }
        return out.toString()
    }
}
