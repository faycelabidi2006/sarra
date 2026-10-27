package com.example.familytv.data

import java.io.BufferedReader

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashMap<String, Int>()
        var name = ""; var logo = ""; var group = "أخرى"; var chno = 0; var pending = false
        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pending = true
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"] ?: ""
                    group = attrs["group-title"]?.ifBlank { null } ?: "أخرى"
                    chno = attrs["tvg-chno"]?.toIntOrNull() ?: 0
                    name = line.substringAfterLast(",").trim().ifBlank { attrs["tvg-name"] ?: "قناة" }
                }
                line.isNotEmpty() && !line.startsWith("#") && pending -> {
                    val base = (line + name).hashCode().toString()
                    val n = seen.merge(base, 1, Int::plus)!!
                    out.add(
                        Channel(
                            id = if (n == 1) base else "${base}_$n",
                            num = if (chno > 0) chno else out.size + 1,
                            name = name, logo = logo, url = line, group = group
                        )
                    )
                    pending = false
                }
            }
        }
        return out
    }
}
