package com.example.familytv.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * عنوان مشروع Supabase والمفتاح العام (anon / publishable). عدّلهما قبل بناء التطبيق.
 * المفتاح العام ليس سراً، والأمان في الخادم: الجداول مغلقة، والوصول عبر دوال تتحقق من كل طلب (supabase/setup.sql).
 */
object SbConfig {
    const val URL = "https://YOUR_PROJECT_REF.supabase.co"
    const val ANON_KEY = "YOUR_SUPABASE_ANON_KEY"

    val configured: Boolean get() = !URL.contains("YOUR_PROJECT_REF") && !ANON_KEY.startsWith("YOUR_")
}

class ApiException(message: String) : Exception(message)

object SupabaseApi {
    /** يستدعي دالة RPC ويعيد JSON الناتج. IOException = تعذّر الاتصال، ApiException = ردّ خطأ من الخادم. */
    fun rpc(fn: String, args: JSONObject): JSONObject {
        val c = URL("${SbConfig.URL}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("apikey", SbConfig.ANON_KEY)
            // المفاتيح الجديدة (sb_publishable_...) ليست JWT فلا تُرسل في Authorization
            if (!SbConfig.ANON_KEY.startsWith("sb_")) c.setRequestProperty("Authorization", "Bearer ${SbConfig.ANON_KEY}")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "FamilyTvApp/1.0")
            c.doOutput = true
            c.outputStream.use { it.write(args.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException("HTTP $code")
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }
}

/** مولّد أسماء المستخدمين وكلمات المرور (أحرف واضحة بلا l/i/o/0/1). 10 خانات عشوائية ≈ 50 بت. */
object CodeGen {
    private const val ALPHA = "abcdefghjkmnpqrstuvwxyz23456789"
    private val rnd = SecureRandom()
    private fun rand(n: Int) = buildString { repeat(n) { append(ALPHA[rnd.nextInt(ALPHA.length)]) } }
    fun username() = "u" + rand(6)
    fun password() = rand(5) + "-" + rand(5)
}
