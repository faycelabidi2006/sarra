package com.example.familytv.data

import android.content.Context
import java.io.File

/** قنوات يضيفها المستخدم يدوياً، تُحفظ في ملف M3U محلي وتظهر كقائمة "قنواتي". */
object MyChannels {
    const val PLAYLIST_NAME = "قنواتي"

    fun file(ctx: Context) = File(ctx.filesDir, "my_channels.m3u")
    fun url(ctx: Context) = "file://" + file(ctx).absolutePath

    private fun clean(s: String) = s.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim()

    /** يضيف القناة إن لم يكن رابطها موجوداً. يرجع false إذا كانت مضافة من قبل. */
    @Synchronized
    fun add(ctx: Context, name: String, streamUrl: String, group: String = "قنواتي", logo: String = ""): Boolean {
        val f = file(ctx)
        val link = streamUrl.trim()
        if (f.exists() && f.readLines().any { it.trim() == link }) return false
        val sb = StringBuilder()
        if (!f.exists() || f.length() == 0L) sb.append("#EXTM3U\n")
        sb.append("#EXTINF:-1 tvg-logo=\"${clean(logo)}\" group-title=\"${clean(group.ifBlank { "قنواتي" })}\",${clean(name)}\n")
        sb.append(link).append('\n')
        f.appendText(sb.toString())
        return true
    }
}
