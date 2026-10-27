package com.example.familytv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytv.data.IptvOrg

/**
 * قوائم القنوات العامة المجانية المنشورة علناً من مشروع iptv-org.
 * الروابط ثابتة النمط: /countries/<رمز الدولة>.m3u و /languages/<رمز اللغة>.m3u و /categories/<التصنيف>.m3u
 */
private const val BASE = "https://iptv-org.github.io/iptv"

private data class PublicList(val label: String, val url: String)

private val countries = listOf(
    "ae" to "الإمارات", "sa" to "السعودية", "qa" to "قطر",
    "kw" to "الكويت", "bh" to "البحرين", "om" to "عُمان",
    "eg" to "مصر", "jo" to "الأردن", "lb" to "لبنان",
    "sy" to "سوريا", "iq" to "العراق", "ps" to "فلسطين",
    "ye" to "اليمن", "ly" to "ليبيا", "tn" to "تونس",
    "dz" to "الجزائر", "ma" to "المغرب", "sd" to "السودان",
    "so" to "الصومال", "mr" to "موريتانيا",
    "tr" to "تركيا", "ir" to "إيران", "pk" to "باكستان", "in" to "الهند",
    "us" to "الولايات المتحدة", "uk" to "المملكة المتحدة", "ca" to "كندا",
    "fr" to "فرنسا", "de" to "ألمانيا", "es" to "إسبانيا", "it" to "إيطاليا",
    "pt" to "البرتغال", "nl" to "هولندا", "br" to "البرازيل", "ru" to "روسيا"
).map { (code, name) -> PublicList(name, "$BASE/countries/$code.m3u") }

private val languages = listOf(
    "ara" to "العربية", "eng" to "الإنجليزية", "fra" to "الفرنسية", "spa" to "الإسبانية",
    "deu" to "الألمانية", "tur" to "التركية", "fas" to "الفارسية", "urd" to "الأردية",
    "hin" to "الهندية", "ita" to "الإيطالية", "por" to "البرتغالية", "rus" to "الروسية",
    "kur" to "الكردية"
).map { (code, name) -> PublicList(name, "$BASE/languages/$code.m3u") }

private val categories = listOf(
    "news" to "أخبار", "sports" to "رياضة", "movies" to "أفلام",
    "series" to "مسلسلات", "kids" to "أطفال", "music" to "موسيقى",
    "documentary" to "وثائقي", "education" to "تعليم",
    "religious" to "ديني", "family" to "عائلة",
    "entertainment" to "ترفيه", "general" to "عام",
    "culture" to "ثقافة", "cooking" to "طبخ", "travel" to "سفر",
    "science" to "علوم", "comedy" to "كوميديا", "weather" to "طقس"
).map { (id, name) -> PublicList(name, "$BASE/categories/$id.m3u") }

@Composable
fun PublicListsDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val saved by vm.playlists.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    val tabs = listOf("الدولة", "اللغة", "التصنيف")
    val all = when (tab) { 0 -> countries; 1 -> languages; else -> categories }
    val shown = all.filter { it.label.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("قنوات عامة مجانية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "قوائم منشورة علناً من مشروع iptv-org. قد تكون بعض القنوات متوقفة أو محجوبة جغرافياً.",
                    style = MaterialTheme.typography.bodySmall
                )
                val packAdded = saved.any { IptvOrg.isPack(it.url) }
                PillButton(
                    text = if (packAdded) "تمت إضافة حزمة العرب + أوروبا (تُحدَّث تلقائياً)" else "إضافة حزمة العرب + أوروبا (تحديث تلقائي)",
                    icon = if (packAdded) null else Icons.Default.Add,
                    brush = if (packAdded) null else Pal.accentBrush,
                    enabled = !packAdded,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.addPlaylist(IptvOrg.PACK_NAME, IptvOrg.PACK_URL)
                        Toast.makeText(ctx, "جارٍ تحميل القنوات من جميع الدول العربية وفرنسا وإسبانيا وإيطاليا وبلجيكا والمملكة المتحدة...", Toast.LENGTH_LONG).show()
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, t ->
                        PillButton(
                            text = t,
                            brush = if (tab == i) Pal.accentBrush else null,
                            onClick = { tab = i; query = "" }
                        )
                    }
                }
                OutlinedTextField(
                    query, { query = it }, label = { Text("بحث") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.url }) { item ->
                        val added = saved.any { it.url == item.url }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 1)
                            PillButton(
                                text = if (added) "تمت الإضافة" else "إضافة",
                                icon = if (added) null else Icons.Default.Add,
                                brush = if (added) null else Pal.accentBrush,
                                enabled = !added,
                                onClick = {
                                    vm.addPlaylist(item.label.substringBefore(" ("), item.url)
                                    Toast.makeText(ctx, "تمت الإضافة: ${item.label}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    if (shown.isEmpty()) item { Text("لا توجد نتائج.") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}
