package com.example.familytv.data

import android.net.Uri

data class XtreamCreds(val server: String, val user: String, val pass: String)

/** يستخرج بيانات Xtream من نص: رابط get.php، أو server/username/password، أو ثلاث كلمات متتالية. */
object XtreamParser {
    fun parse(text: String): XtreamCreds? {
        // 1) رابط get.php أو player_api فيه username و password
        val link = Regex("""https?://\S+""").findAll(text)
            .map { it.value.trim('"', ',', '}', ' ') }
            .firstOrNull { it.contains("username=") && it.contains("password=") }
        if (link != null) {
            val u = Uri.parse(link)
            val user = u.getQueryParameter("username")
            val pass = u.getQueryParameter("password")
            val scheme = u.scheme
            val auth = u.authority
            if (user != null && pass != null && scheme != null && auth != null) {
                return XtreamCreds("$scheme://$auth", user, pass)
            }
        }

        // 2) حقول مسمّاة (نص عادي أو JSON)
        fun field(vararg keys: String): String? =
            Regex("""(?i)(?<![A-Za-z_])"?(?:${keys.joinToString("|")})"?\s*[:=]\s*"?([^"\s,}]+)""")
                .find(text)?.groupValues?.get(1)
        val server = field("server", "host", "url", "portal")
        val user = field("username", "user")
        val pass = field("password", "pass")
        if (server != null && user != null && pass != null) return XtreamCreds(server, user, pass)

        // 3) ثلاث كلمات: الخادم ثم المستخدم ثم كلمة المرور
        val t = text.trim().split(Regex("\\s+"))
        if (t.size == 3 && t[0].startsWith("http")) return XtreamCreds(t[0], t[1], t[2])
        return null
    }
}
