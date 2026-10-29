package com.example.familytv

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.familytv.data.Channel
import com.example.familytv.data.Tmdb
import com.example.familytv.data.TmdbDetail
import com.example.familytv.data.TmdbItem
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

private const val TMDB_NOTICE = "يستخدم هذا المنتج واجهة TMDB البرمجية لكنه غير معتمد أو مصدّق من TMDB."

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * تبويب البحث: يبحث في مكتبتك (أفلام ومسلسلات حساب Xtream) وفي TMDB للمعلومات
 * (بوستر، قصة، تقييم، إعلان، ومنصات المشاهدة القانونية). لا يجلب روابط تشغيل من الإنترنت.
 */
@Composable
fun DiscoverScreen(vm: MainViewModel, onTab: (Int) -> Unit, onFullscreen: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val key by vm.tmdbKey.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<TmdbItem?>(null) }
    var keyDialog by remember { mutableStateOf(false) }
    var webQuery by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.player.pause() }
    LaunchedEffect(Unit) { vm.loadMovies(); vm.loadSeries() }

    val library = remember(query, vm.movies, vm.series) {
        val q = norm(query)
        if (q.length < 2) emptyList() else (vm.movies + vm.series).filter { norm(it.name).contains(q) }.take(40)
    }

    fun openLibrary(ch: Channel) {
        if (ch.id.startsWith("s")) { vm.openSeries(ch); onTab(3) }
        else { vm.play(ch, addToRecent = false); onFullscreen() }
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        if (key.isBlank()) { keyDialog = true; return }
        scope.launch {
            loading = true; error = null; searched = true
            try {
                results = Tmdb.search(key, q)
            } catch (e: Exception) {
                results = emptyList()
                error = "تعذر البحث:  ${e.message ?: e.javaClass.simpleName}"
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("اسم فيلم أو مسلسل") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                modifier = Modifier.weight(1f)
            )
            PillButton("بحث", icon = Icons.Default.Search) { doSearch() }
            PillButton("Google", brush = null) {
                if (query.isNotBlank()) webQuery = query.trim()
            }
        }

        if (key.isBlank()) {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "لعرض الملصقات والقصص وأماكن المشاهدة، أضف مفتاح TMDB مجانياً.",
                    color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)
                )
                PillButton("إضافة مفتاح", onClick = { keyDialog = true })
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (library.isNotEmpty()) {
                item { SectionTitle("في مكتبتك (${library.size})") }
                items(library, key = { "lib_" + it.id }) { ch ->
                    ResultRow(title = ch.name, subtitle = if (ch.id.startsWith("s")) "مسلسل" else "فيلم", poster = ch.logo) { openLibrary(ch) }
                }
            }
            if (loading) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Pal.blue) } }
            }
            error?.let { msg -> item { Text(msg, color = Pal.pink, fontSize = 14.sp) } }
            if (!loading && error == null && searched && results.isEmpty()) {
                item { Text("لا توجد نتائج على الإنترنت.", color = Pal.textDim) }
            }
            if (results.isNotEmpty()) {
                item { SectionTitle("نتائج الإنترنت (${results.size})") }
                items(results, key = { "tmdb_" + it.type + it.id }) { r ->
                    val sub = buildString {
                        append(if (r.type == "movie") "فيلم" else "مسلسل")
                        if (r.year.isNotBlank()) append("  ·  ${r.year}")
                        if (r.rating > 0) append("  ·  ★ ${"%.1f".format(r.rating)}")
                    }
                    ResultRow(title = r.title, subtitle = sub, poster = r.poster) { picked = r }
                }
            }
            item { Text(TMDB_NOTICE, color = Pal.textDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }

    picked?.let { item ->
        DetailDialog(vm, item, key, onTab, onFullscreen) { picked = null }
    }
    if (keyDialog) TmdbKeyDialog(vm) { keyDialog = false }
    webQuery?.let { q -> GoogleDialog(q) { webQuery = null } }
}

/**
 * نتائج Google الحقيقية داخل التطبيق (متصفح WebView). روابط المواقع الأخرى لا تُفتح هنا:
 * تُمرَّر إلى متصفح الهاتف، فيبقى هذا المتصفح مخصصاً لصفحات Google فقط.
 */
@Composable
private fun GoogleDialog(query: String, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pal.bgTop)) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(40.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick {
                        val w = web
                        if (w != null && w.canGoBack()) w.goBack() else onClose()
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                Text("Google: $query", color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White) }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(true)

                        // آخر لمسة حقيقية: صفحات Google تفتح الروابط بالـ JavaScript، وAndroid لا يعتبرها نقرة دائماً
                        var lastTouch = 0L
                        setOnTouchListener { _, e ->
                            if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_DOWN) {
                                lastTouch = System.currentTimeMillis()
                            }
                            false
                        }
                        fun recentTouch() = System.currentTimeMillis() - lastTouch < 1500

                        webChromeClient = object : WebChromeClient() {
                            // روابط target="_blank": نسمح بها فقط بعد لمسة منك، ونوجّه الهدف لمتصفح الهاتف
                            override fun onCreateWindow(
                                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
                            ): Boolean {
                                if (view == null || resultMsg == null) return false
                                if (!(isUserGesture || recentTouch())) return false // نافذة منبثقة آلية: ممنوعة
                                val temp = WebView(view.context)
                                var handled = false
                                fun handle(u: Uri?, v: WebView) {
                                    if (handled) return
                                    handled = true
                                    if (u != null) openResult(c, u)
                                    v.stopLoading()
                                    v.post { v.destroy() }
                                }
                                temp.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                        handle(r.url, v); return true
                                    }
                                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                                        handle(url?.let { Uri.parse(it) }, v)
                                    }
                                }
                                val transport = resultMsg.obj as WebView.WebViewTransport
                                transport.webView = temp
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val u = request.url
                                if (isAdUrl(u)) return true // روابط الإعلانات: ممنوعة دائماً
                                val user = request.hasGesture() || recentTouch()
                                val host = u.host ?: ""
                                if (host.contains("google.")) {
                                    // google.com/url?q=الهدف : نفتح الهدف مباشرة دون التحويلة، وفقط بعد لمسة منك
                                    if (u.path == "/url") {
                                        if (user) openResult(c, u)
                                        return true
                                    }
                                    return false
                                }
                                // موقع خارجي: بعد لمسة منك فقط، وأي تحويلة آلية تُمنع
                                if (user && !request.isRedirect) openResult(c, u)
                                return true
                            }

                            // نمنع تحميل نطاقات الإعلانات أصلاً (أسرع وأخف)
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                return if (isAdUrl(request.url)) {
                                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                } else null
                            }

                            // الإعلانات داخل صفحة النتائج نفسها تُخفى بالـ CSS والـ JavaScript
                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript(AD_HIDE_JS, null)
                            }
                        }
                        web = this
                        loadUrl("https://www.google.com/search?q=" + Uri.encode(query) + "&hl=ar")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ResultRow(title: String, subtitle: String, poster: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().focusBorder(shape).glass(shape).bounceClick(onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 44.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)).background(Pal.glassStrong)) {
            if (poster.isNotBlank()) {
                AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Pal.textDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DetailDialog(
    vm: MainViewModel, item: TmdbItem, key: String,
    onTab: (Int) -> Unit, onFullscreen: () -> Unit, onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var detail by remember(item) { mutableStateOf<TmdbDetail?>(null) }
    var detailError by remember(item) { mutableStateOf<String?>(null) }
    LaunchedEffect(item) {
        try { detail = Tmdb.details(key, item) } catch (e: Exception) { detailError = e.message ?: e.javaClass.simpleName }
    }

    val t1 = norm(item.title)
    val t2 = norm(item.original)
    val pool = if (item.type == "movie") vm.movies else vm.series
    val matches = remember(item, pool) {
        pool.filter {
            val n = norm(it.name)
            (t1.length >= 3 && n.contains(t1)) || (t2.length >= 3 && n.contains(t2))
        }.take(5)
    }
    val titleLine = if (item.year.isNotBlank()) "${item.title} (${item.year})" else item.title

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(titleLine) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.poster.isNotBlank()) {
                        AsyncImage(
                            model = item.poster, contentDescription = item.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 100.dp, height = 150.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.rating > 0) Text("★ ${"%.1f".format(item.rating)} / 10", fontWeight = FontWeight.SemiBold)
                        Text(
                            item.overview.ifBlank { "لا يوجد وصف متاح." },
                            fontSize = 13.sp, maxLines = 8, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val d = detail
                when {
                    d != null -> Text(
                        if (d.providers.isEmpty()) "أين تشاهد (${d.country}): لا توجد معلومات بث لمنطقتك."
                        else "أين تشاهد (${d.country}): " + d.providers.joinToString(", "),
                        fontSize = 13.sp
                    )
                    detailError != null -> Text("تعذر تحميل التفاصيل: $detailError", fontSize = 13.sp)
                    else -> Text("جارٍ تحميل التفاصيل...", fontSize = 13.sp)
                }
                if (matches.isNotEmpty()) {
                    Text("في مكتبتك:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    matches.forEach { m ->
                        PillButton(
                            (if (m.id.startsWith("s")) "فتح: " else "تشغيل: ") + m.name,
                            icon = Icons.Default.PlayArrow
                        ) {
                            onClose()
                            if (m.id.startsWith("s")) { vm.openSeries(m); onTab(3) }
                            else { vm.play(m, addToRecent = false); onFullscreen() }
                        }
                    }
                } else {
                    Text("غير موجود في مكتبتك.", fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } },
        dismissButton = {
            val trailer = detail?.trailerKey
            if (trailer != null) {
                TextButton(onClick = {
                    openUrl(ctx, "https://www.youtube.com/watch?v=$trailer")
                }) { Text("الإعلان") }
            }
        }
    )
}

@Composable
fun TmdbKeyDialog(vm: MainViewModel, onClose: () -> Unit) {
    val saved by vm.tmdbKey.collectAsStateWithLifecycle()
    var text by remember(saved) { mutableStateOf(saved) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("مفتاح TMDB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "مفتاح API مجاني (v3). أنشئ حساباً على themoviedb.org ثم افتح Settings > API لطلبه.",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim() },
                    label = { Text("مفتاح API") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(TMDB_NOTICE, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { vm.setTmdbKey(text); onClose() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

private fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "لا يوجد تطبيق يفتح هذا الرابط", Toast.LENGTH_SHORT).show()
    }
}

/** يفتح رابط نتيجة في متصفح الهاتف، ويفكّ تحويلة google.com/url?q= ويرفض روابط الإعلانات. */
private fun openResult(c: Context, u: Uri): Boolean {
    if (isAdUrl(u)) return false
    var target = u
    if ((u.host ?: "").contains("google.") && u.path == "/url") {
        val t = u.getQueryParameter("q") ?: u.getQueryParameter("url") ?: return false
        if (!t.startsWith("http")) return false
        target = Uri.parse(t)
        if (isAdUrl(target)) return false
    }
    openUrl(c, target.toString())
    return true
}

private val AD_HOSTS = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "googletagservices.com", "adservice.google.", "pagead2.", "adsense."
)

private fun isAdUrl(u: Uri): Boolean {
    val host = u.host ?: return false
    if (AD_HOSTS.any { host.contains(it) }) return true
    val path = u.path ?: ""
    return host.contains("google.") && (path.startsWith("/aclk") || path.startsWith("/pagead") || path.startsWith("/ads/"))
}

/**
 * يخفي إعلانات صفحة نتائج Google: بمحددات CSS المعروفة، ثم بمسح أي عنصر وسمه "Sponsored" أو "إعلان".
 * Google يغيّر بنية صفحته من وقت لآخر، فقد يفلت بعض الإعلانات أو يحتاج هذا المقطع تحديثاً.
 */
private const val AD_HIDE_JS = """
(function () {
  if (window.__adHide) return;
  window.__adHide = true;
  var css = '#tads,#tadsb,#bottomads,[data-text-ad],[data-ta-slot],.uEierd,.ads-ad,.pla-unit,' +
            '.commercial-unit-desktop-top,.commercial-unit-desktop-rhs,.commercial-unit-mobile-top,' +
            '[aria-label="Ads"],[aria-label="Sponsored"],[aria-label="إعلانات"]{display:none!important}';
  var st = document.createElement('style');
  st.textContent = css;
  (document.head || document.documentElement).appendChild(st);

  var labels = ['sponsored', 'ads', 'إعلان', 'إعلانات', 'ممول', 'ممولة', 'sponsorisé', 'anuncio', 'gesponsert'];
  function sweep() {
    var nodes = document.querySelectorAll('span, div[role="heading"]');
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      var t = (n.textContent || '').trim().toLowerCase();
      if (t.length === 0 || t.length > 12 || n.children.length > 0) continue;
      if (labels.indexOf(t) === -1) continue;
      var box = n.closest('[data-text-ad],.uEierd,[data-hveid],#tads,#tadsb,#bottomads');
      if (box) box.style.setProperty('display', 'none', 'important');
    }
  }
  var timer = null;
  new MutationObserver(function () {
    if (timer) return;
    timer = setTimeout(function () { timer = null; sweep(); }, 300);
  }).observe(document.documentElement, { childList: true, subtree: true });
  sweep();
})();
"""
