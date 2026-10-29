package com.example.familytv

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.VisualTransformation
import com.example.familytv.data.Playlist
import com.example.familytv.data.Repository
import com.example.familytv.data.XtreamException
import com.example.familytv.data.XtreamParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onPlay: () -> Unit = {}) {
    val external by vm.external.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val trustAll by vm.trustAll.collectAsStateWithLifecycle()
    val liveSort by vm.liveSort.collectAsStateWithLifecycle()
    val subSize by vm.subSize.collectAsStateWithLifecycle()
    val subStyle by vm.subStyle.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val toast = { msg: String -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    val recordDir by vm.recordDir.collectAsStateWithLifecycle()
    val saveLabel = remember(recordDir) { RecStorage.describe(ctx, recordDir) }

    AppBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(44.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                Text("الإعدادات", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
            val tiles = listOf(
                Triple("xtream", "إضافة حساب Xtream", Icons.Default.Add),
                Triple("findxtream", "البحث عن Xtream جديد", Icons.Default.Search),
                Triple("add", "إضافة قائمة تشغيل (M3U)", Icons.Default.Add),
                Triple("public", "قنوات عامة مجانية", Icons.Default.List),
                Triple("parent", "الرقابة الأبوية", Icons.Default.Lock),
                Triple("change", "تغيير القائمة", Icons.Default.List),
                Triple("hide", "إخفاء فئات البث المباشر", Icons.Default.Close),
                Triple("hidevod", "إخفاء فئات الأفلام", Icons.Default.Close),
                Triple("hideser", "إخفاء فئات المسلسلات", Icons.Default.Close),
                Triple("sort", "ترتيب القنوات: " + when (liveSort) { "az" -> "تصاعدي"; "za" -> "تنازلي"; else -> "افتراضي" }, Icons.Default.List),
                Triple("layout", "التخطيط: " + when (layout) { "phone" -> "هاتف"; "tv" -> "تلفاز"; else -> "تلقائي" }, Icons.Default.Settings),
                Triple("subsize", "حجم الترجمة: " + when (subSize) { "small" -> "صغير"; "large" -> "كبير"; else -> "متوسط" }, Icons.Default.Settings),
                Triple("substyle", "نمط الترجمة: " + when (subStyle) { "yellow" -> "أصفر"; "box" -> "أبيض على أسود"; else -> "أبيض" }, Icons.Default.Settings),
                Triple("refresh", "تحديث القنوات الآن", Icons.Default.PlayArrow),
                Triple("clearch", "مسح سجل القنوات", Icons.Default.Delete),
                Triple("clearwatch", "مسح سجل الأفلام والمسلسلات", Icons.Default.Delete),
                Triple("recordings", "التسجيلات", Icons.Default.DateRange),
                Triple("storage", "الحفظ في: $saveLabel", Icons.Default.Place),
                Triple("external", "المشغّل الخارجي: " + if (external) "تشغيل" else "إيقاف", Icons.Default.PlayArrow),
                Triple("format", "صيغة البث: " + format.uppercase(), Icons.Default.PlayArrow),
                Triple("tmdb", "مفتاح البحث عن الأفلام (TMDB)", Icons.Default.Search),
                Triple("tls", "HTTPS غير موثوق: " + if (trustAll) "تشغيل" else "إيقاف", Icons.Default.Lock),
                Triple("addch", "إضافة قناة", Icons.Default.Add),
                Triple("vpn", "فتح تطبيق VPN", Icons.Default.Lock)
            )
            val users by vm.users.collectAsStateWithLifecycle()
            val isOwner = users.firstOrNull { it.name == vm.sessionUser }?.owner == true
            val allTiles = tiles + listOfNotNull(
                Triple("account", "الحساب: ${vm.sessionUser ?: ""}", Icons.Default.AccountCircle),
                if (isOwner) Triple("users", "إدارة المستخدمين", Icons.Default.Person) else null,
                if (isOwner) Triple("control", "لوحة التحكم", Icons.Default.Settings) else null
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isCompact()) 2 else 4),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allTiles, key = { it.first }) { (key, label, icon) ->
                    SettingTile(label, icon, tileBrush(key)) {
                        when (key) {
                            "clearch" -> { vm.clearChannelHistory(); toast("تم مسح سجل القنوات") }
                            "clearwatch" -> { vm.clearWatchHistory(); toast("تم مسح سجل الأفلام والمسلسلات") }
                            "sort" -> { vm.cycleLiveSort(); toast("تم تغيير الترتيب") }
                            "layout" -> { vm.cycleLayout(); toast("تم تغيير التخطيط") }
                            "subsize" -> { vm.cycleSubSize(); toast("تم تغيير حجم الترجمة") }
                            "substyle" -> { vm.cycleSubStyle(); toast("تم تغيير نمط الترجمة") }
                            "hidevod" -> { vm.loadMovies(); if (vm.movies.isEmpty()) toast("جارٍ تحميل فئات الأفلام... اضغط مجدداً بعد لحظة") else dialog = key }
                            "hideser" -> { vm.loadSeries(); if (vm.series.isEmpty()) toast("جارٍ تحميل فئات المسلسلات... اضغط مجدداً بعد لحظة") else dialog = key }
                            "tls" -> {
                                vm.toggleTrustAll()
                                toast(if (trustAll) "HTTPS غير موثوق: متوقف" else "HTTPS غير موثوق: يعمل (أقل أماناً). جارٍ إعادة التحميل...")
                            }
                            "refresh" -> { vm.refreshNow(); toast("جارٍ تحديث القنوات...") }
                            "external" -> {
                                vm.setExternal(!external)
                                toast(if (external) "المشغّل الخارجي: متوقف" else "المشغّل الخارجي: يعمل (تُفتح القنوات في تطبيق آخر)")
                            }
                            "format" -> {
                                vm.toggleFormat()
                                toast("صيغة البث: " + (if (format == "ts") "M3U8" else "TS"))
                            }
                            "hide" -> { if (vm.channels.isEmpty()) toast("حمّل قائمة تشغيل أولاً") else dialog = key }
                            else -> dialog = key
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "add" -> AddPlaylistDialog(vm, onClose = { dialog = null })
        "xtream" -> XtreamDialog(vm, onClose = { dialog = null })
        "findxtream" -> XtreamDiscoveryDialog(onClose = { dialog = null })
        "public" -> PublicListsDialog(vm, onClose = { dialog = null })
        "storage" -> StorageDialog(vm, onClose = { dialog = null })
        "recordings" -> RecordingsDialog(vm, onPlay = onPlay, onClose = { dialog = null })
        "account" -> AccountDialog(vm, onClose = { dialog = null })
        "users" -> UsersDialog(vm, onClose = { dialog = null })
        "control" -> ControlPanelDialog(vm, onClose = { dialog = null })
        "change" -> ChangePlaylistDialog(vm, onClose = { dialog = null })
        "hide" -> {
            val hidden by vm.hidden.collectAsStateWithLifecycle()
            GroupPickerDialog("إخفاء فئات البث المباشر", vm.channels.map { it.group }.distinct(), hidden,
                onToggle = { vm.toggleHidden(it) }, onClose = { dialog = null })
        }
        "hidevod" -> {
            val hidden by vm.hidden.collectAsStateWithLifecycle()
            val shown = remember(hidden) { hidden.filter { it.startsWith("vod|") }.map { it.removePrefix("vod|") }.toSet() }
            GroupPickerDialog("إخفاء فئات الأفلام", vm.movies.map { it.group }.distinct(), shown,
                onToggle = { vm.toggleHidden("vod|$it") }, onClose = { dialog = null })
        }
        "hideser" -> {
            val hidden by vm.hidden.collectAsStateWithLifecycle()
            val shown = remember(hidden) { hidden.filter { it.startsWith("ser|") }.map { it.removePrefix("ser|") }.toSet() }
            GroupPickerDialog("إخفاء فئات المسلسلات", vm.series.map { it.group }.distinct(), shown,
                onToggle = { vm.toggleHidden("ser|$it") }, onClose = { dialog = null })
        }
        "parent" -> ParentDialog(vm, onClose = { dialog = null })
        "tmdb" -> TmdbKeyDialog(vm, onClose = { dialog = null })
        "vpn" -> VpnDialog(onClose = { dialog = null })
        "addch" -> AddChannelDialog(vm, onClose = { dialog = null })
    }
}

private fun tileBrush(key: String): Brush = when (key) {
    "xtream", "add", "findxtream" -> Pal.accentBrush
    "parent" -> Pal.warmBrush
    "change" -> Pal.coolBrush
    "public" -> Pal.coolBrush
    "hide", "hidevod", "hideser", "format", "sort", "layout", "subsize", "substyle" -> Pal.slateBrush
    "clearch", "clearwatch" -> Pal.warmBrush
    "recordings" -> Pal.warmBrush
    "storage" -> Pal.coolBrush
    "account" -> Pal.coolBrush
    "users", "control" -> Pal.warmBrush
    else -> Pal.accentBrush
}

@Composable
private fun SettingTile(label: String, icon: ImageVector, brush: Brush, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().focusBorder(shape).glass(shape).bounceClick(onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp), maxLines = 2)
    }
}

@Composable
private fun rememberImportPicker(vm: MainViewModel, onClose: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importFile(uri) { msg ->
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                if (msg.contains("استيراد")) onClose()
            }
        }
    }
    return { picker.launch(arrayOf("*/*")) }
}

// نافذة عريضة بدل العرض الافتراضي الضيق، لتناسب الوضع الأفقي على الهاتف
private val wideDialog = DialogProperties(usePlatformDefaultWidth = false)

@Composable
fun AddPlaylistDialog(vm: MainViewModel, editIndex: Int? = null, initial: Playlist? = null, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var scheme by remember { mutableStateOf(if (initial?.url?.startsWith("https://") == true) "https://" else "http://") }
    var rest by remember { mutableStateOf((initial?.url ?: "").removePrefix("http://").removePrefix("https://")) }
    val url = if (rest.isBlank()) "" else scheme + rest.trim()
    val applyLink: (String) -> Unit = { t ->
        val x = t.trim()
        when {
            x.startsWith("https://", ignoreCase = true) -> { scheme = "https://"; rest = x.substring(8) }
            x.startsWith("http://", ignoreCase = true) -> { scheme = "http://"; rest = x.substring(7) }
            else -> rest = x
        }
    }
    val pickFile = rememberImportPicker(vm, onClose)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text(if (editIndex != null) "تعديل قائمة التشغيل (M3U)" else "إضافة قائمة تشغيل (M3U)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editIndex == null) {
                    Button(onClick = pickFile, modifier = Modifier.fillMaxWidth().focusBorder()) {
                        Text("استيراد من ملف (M3U أو بيانات Xtream)")
                    }
                    Text("أو أدخل الرابط يدوياً:")
                }
                AdaptiveFields(8.dp) { w ->
                    OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = w(1f))
                    OutlinedTextField(rest, { applyLink(it) }, label = { Text("رابط M3U") }, prefix = { Text(scheme) }, singleLine = true, modifier = w(2f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) {
                    val pl = Playlist(name.ifBlank { "قائمة" }, url.trim())
                    if (editIndex != null) vm.updatePlaylist(editIndex, pl) else vm.addPlaylist(pl.name, pl.url)
                    onClose()
                }
            }) { Text(if (editIndex != null) "حفظ" else "إضافة") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

@Composable
fun XtreamDialog(vm: MainViewModel, editIndex: Int? = null, initial: Playlist? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf(initial?.name ?: "") }
    // البادئة http:// ثابتة أمام الخانة، ويكتب المستخدم العنوان والمنفذ فقط.
    // لو لصق عنواناً كاملاً يبدأ بـ https:// تتحول البادئة تلقائياً.
    var scheme by remember { mutableStateOf(if (initial?.url?.startsWith("https://") == true) "https://" else "http://") }
    var host by remember { mutableStateOf((initial?.url ?: "").removePrefix("http://").removePrefix("https://")) }
    val server = if (host.isBlank()) "" else scheme + host.trim()
    val applyServer: (String) -> Unit = { t ->
        val x = t.trim()
        when {
            x.startsWith("https://", ignoreCase = true) -> { scheme = "https://"; host = x.substring(8) }
            x.startsWith("http://", ignoreCase = true) -> { scheme = "http://"; host = x.substring(7) }
            else -> host = x
        }
    }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var showPass by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    var diag by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val pickFile = rememberImportPicker(vm, onClose)

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text(if (editIndex != null) "تعديل حساب Xtream" else "إضافة حساب Xtream") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editIndex == null) {
                        Button(onClick = pickFile, modifier = Modifier.weight(1f).focusBorder()) { Text("استيراد من ملف") }
                    } else {
                        Button(
                            onClick = {
                                val text = "server: ${Repository.normalizeServer(server)}\nusername: ${user.trim()}\npassword: ${pass.trim()}"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                ctx.startActivity(Intent.createChooser(send, "مشاركة الحساب"))
                            },
                            modifier = Modifier.weight(1f).focusBorder()
                        ) { Text("مشاركة البيانات") }
                    }
                    Button(
                        onClick = {
                            val c = XtreamParser.parse(clipboard.getText()?.text ?: "")
                            if (c != null) {
                                applyServer(c.server); user = c.user; pass = c.pass; result = ""
                                Toast.makeText(ctx, "تمت التعبئة من الحافظة. راجعها ثم اضغط اختبار.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(ctx, "لا تحتوي الحافظة على بيانات Xtream", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).focusBorder()
                    ) { Text("لصق من الحافظة") }
                }
                AdaptiveFields(12.dp) { w ->
                    Column(w(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            name, { name = it }, label = { Text("الاسم (اختياري)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            host, { applyServer(it); result = "" }, label = { Text("الخادم (host:port)") },
                            prefix = { Text(scheme) },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(w(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            user, { user = it; result = "" }, label = { Text("اسم المستخدم") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            pass, { pass = it; result = "" }, label = { Text("كلمة المرور") }, singleLine = true,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "إخفاء" else "إظهار") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !testing && server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
                        onClick = {
                            scope.launch {
                                testing = true
                                diag = ""
                                result = try {
                                    Repository.checkXtream(server, user.trim(), pass.trim())
                                } catch (e: XtreamException) {
                                    e.message ?: "خطأ"
                                } catch (e: Exception) {
                                    com.example.familytv.data.Net.explain(e)
                                }
                                if (!result.startsWith("تم الدخول بنجاح")) {
                                    diag = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try { com.example.familytv.data.Net.diagnose(server) } catch (_: Exception) { "" }
                                    }
                                }
                                testing = false
                            }
                        },
                        modifier = Modifier.focusBorder()
                    ) { Text(if (testing) "جارٍ الاختبار..." else "اختبار الاتصال") }
                    Text(
                        if (result.isEmpty()) "" else (if (result.startsWith("تم الدخول بنجاح")) "\u2714 " else "\u2716 ") + result,
                        color = if (result.startsWith("تم الدخول بنجاح")) Color(0xFF7BE07B) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (diag.isNotBlank()) {
                    Text(diag, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    if (editIndex != null) {
                        vm.updatePlaylist(
                            editIndex,
                            Playlist(name.ifBlank { "Xtream" }, Repository.normalizeServer(server), user.trim(), pass.trim())
                        )
                    } else {
                        vm.addXtream(name, server, user, pass)
                    }
                    onClose()
                }
            }) { Text(if (editIndex != null) "حفظ" else "دخول") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

@Composable
private fun ChangePlaylistDialog(vm: MainViewModel, onClose: () -> Unit) {
    val list by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    var editIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text("تغيير القائمة") },
        text = {
            if (list.isEmpty()) Text("لا توجد قوائم بعد.")
            else LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(list.withIndex().toList(), key = { it.index }) { (i, p) ->
                    val editable = !p.url.startsWith("file://") && !com.example.familytv.data.IptvOrg.isPack(p.url)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == active, onClick = { vm.setActive(i) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1)
                            Text(
                                if (p.user.isNotBlank()) "Xtream: ${p.user} @ ${p.url}" else if (com.example.familytv.data.IptvOrg.isPack(p.url)) "حزمة تُحدَّث تلقائياً" else "M3U",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1
                            )
                        }
                        if (editable) {
                            IconButton(onClick = { editIndex = i }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                        }
                        IconButton(onClick = { vm.removePlaylist(i) }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )

    editIndex?.let { i ->
        val p = list.getOrNull(i)
        if (p != null) {
            if (p.user.isNotBlank()) XtreamDialog(vm, i, p) { editIndex = null }
            else AddPlaylistDialog(vm, i, p) { editIndex = null }
        }
    }
}

@Composable
private fun GroupPickerDialog(
    title: String, groups: List<String>, selected: Set<String>,
    onToggle: (String) -> Unit, onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            if (groups.isEmpty()) Text("حمّل قائمة تشغيل أولاً.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(groups, key = { it }) { g ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = g in selected, onCheckedChange = { onToggle(g) })
                        Text(g, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("تم") } }
    )
}

@Composable
private fun ParentDialog(vm: MainViewModel, onClose: () -> Unit) {
    val pin by vm.pin.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    var newPin by remember { mutableStateOf("") }
    val groups = vm.channels.map { it.group }.distinct()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("الرقابة الأبوية") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                    label = { Text(if (pin.isEmpty()) "تعيين رمز سري" else "رمز سري جديد (يوجد رمز حالي)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Text("الفئات المقفلة (يلزم الرمز السري لفتحها):", modifier = Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(groups, key = { it }) { g ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g in locked, onCheckedChange = { vm.toggleLocked(g) })
                            Text(g, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (newPin.isNotEmpty()) vm.setPin(newPin); onClose() }) { Text("حفظ") }
        },
        dismissButton = {
            if (pin.isNotEmpty()) TextButton(onClick = { vm.setPin(""); onClose() }) { Text("إزالة الرمز السري") }
        }
    )
}
