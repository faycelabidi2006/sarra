package com.example.familytv

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import java.util.Locale

@Composable
fun FullscreenScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val activity = ctx as? android.app.Activity
        vm.inFullscreen = true
        val controller: WindowInsetsControllerCompat? = activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        // الفيديو أفقي دائماً، ثم نعيد الاتجاه الحر عند الخروج
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            vm.inFullscreen = false
            vm.focusRestore = true   // عند العودة نعيد التركيز إلى القناة الجارية
            vm.saveProgressNow()
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    val tracks = rememberTracks(vm.player)
    var showTracks by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    val qualitySel by vm.quality.collectAsStateWithLifecycle()
    val subSize by vm.subSize.collectAsStateWithLifecycle()
    val subStyle by vm.subStyle.collectAsStateWithLifecycle()

    // عند انتهاء مؤقت النوم نغلق شاشة التشغيل (الشاشة تبقى مضاءة أثناء التشغيل، فلا معنى لبقائها مفتوحة)
    val sleepStart = remember { vm.sleepFired }
    LaunchedEffect(vm.sleepFired) { if (vm.sleepFired != sleepStart) onBack() }
    val sleepLeft by produceState(0L, vm.sleepEndsAt) {
        while (vm.sleepEndsAt > 0L) {
            value = ((vm.sleepEndsAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1L)
            kotlinx.coroutines.delay(15_000L)
        }
        value = 0L
    }

    // صورة داخل صورة: عند العودة للتطبيق نلغي العلامة، وعند إغلاق النافذة الصغيرة (يصل onStop دون onResume) نوقف التشغيل
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.pipEntered = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (vm.pipEntered) { vm.pipEntered = false; vm.player.pause() }
    }

    // التركيز يبدأ على المشغّل نفسه، فيُظهر OK أدوات التحكم بدل أن ينفّذ زر الرجوع
    val playerFocus = remember { FocusRequester() }
    val pvRef = remember { arrayOfNulls<PlayerView>(1) }
    LaunchedEffect(Unit) { runCatching { playerFocus.requestFocus() } }

    val hasChoices = options(tracks, C.TRACK_TYPE_AUDIO).size > 1 || options(tracks, C.TRACK_TYPE_TEXT).isNotEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .focusable()
            .onKeyEvent { e ->
                // onKeyEvent وليس onPreviewKeyEvent: العناصر المركَّز عليها (السهم والأزرار) تعالج OK أولاً
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && !vm.pipActive) {
                    if (e.type == KeyEventType.KeyUp) {
                        pvRef[0]?.let { pv -> if (pv.isControllerFullyVisible) pv.hideController() else pv.showController() }
                    }
                    true
                } else false
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = true; keepScreenOn = true; player = vm.player
                    // المشغّل لا يسرق التركيز من Compose
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    pvRef[0] = this
                }
            },
            update = {
                it.player = vm.player; it.useController = !vm.pipActive
                it.subtitleView?.let { sv ->
                    sv.setFractionalTextSize(when (subSize) { "small" -> 0.04f; "large" -> 0.075f; else -> 0.0533f })
                    val white = android.graphics.Color.WHITE
                    val yellow = android.graphics.Color.YELLOW
                    val clear = android.graphics.Color.TRANSPARENT
                    val black = android.graphics.Color.BLACK
                    sv.setStyle(
                        when (subStyle) {
                            "yellow" -> CaptionStyleCompat(yellow, clear, clear, CaptionStyleCompat.EDGE_TYPE_OUTLINE, black, null)
                            "box" -> CaptionStyleCompat(white, android.graphics.Color.argb(200, 0, 0, 0), clear, CaptionStyleCompat.EDGE_TYPE_NONE, black, null)
                            else -> CaptionStyleCompat(white, clear, clear, CaptionStyleCompat.EDGE_TYPE_OUTLINE, black, null)
                        }
                    )
                }
            },
            onRelease = { it.player = null; pvRef[0] = null }
        )

        if (!vm.pipActive) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopStart)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
        )
        Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text(vm.current?.name ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            if (!vm.playError && vm.current != null) LiveBadge()
        }

        Row(
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChoices) PillButton("الصوت / الترجمة", brush = null) { showTracks = true }
            PillButton(if (qualitySel > 0) "الجودة ${qualitySel}p" else "الجودة", brush = null) { showQuality = true }
            PillButton(if (sleepLeft > 0L) "المؤقت ${sleepLeft}د" else "مؤقت النوم", brush = null) { showSleep = true }
            RecordButton(vm)
            SignalMeter(vm)
        }

        if (vm.nextEpisode != null) {
            PillButton(
                "الحلقة التالية",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 96.dp)
            ) { vm.playNext() }
        }
        }

        if (vm.playError) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE612162A), RoundedCornerShape(22.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("البث غير متاح", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(vm.playErrorInfo, color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("إعادة المحاولة") { vm.current?.let { vm.play(it) } }
                    PillButton("رجوع", brush = null, onClick = onBack)
                }
            }
        }
    }

    if (showTracks) TrackDialog(vm.player, tracks) { showTracks = false }
    if (showSleep) SleepDialog(vm) { showSleep = false }
    if (showQuality) QualityDialog(vm, tracks) { showQuality = false }
}


// ---------- جودة الفيديو ----------

private fun playingHeight(t: Tracks): Int {
    for (g in t.groups) if (g.type == C.TRACK_TYPE_VIDEO) {
        for (i in 0 until g.length) if (g.isTrackSelected(i)) return g.getTrackFormat(i).height
    }
    return 0
}

private fun videoVariants(t: Tracks): Int =
    t.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.sumOf { g -> (0 until g.length).count { g.isTrackSupported(it) } }

@Composable
private fun QualityDialog(vm: MainViewModel, tracks: Tracks, onClose: () -> Unit) {
    val sel by vm.quality.collectAsStateWithLifecycle()
    val fmt by vm.format.collectAsStateWithLifecycle()
    val now = playingHeight(tracks)
    val variants = videoVariants(tracks)
    val options = listOf(
        0 to "تلقائي (أفضل جودة متاحة)", 1080 to "1080p", 720 to "720p",
        480 to "480p", 360 to "360p", 240 to "240p (إنترنت ضعيف)"
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("جودة الفيديو") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (now > 0) Text("قيد التشغيل: ${now}p", color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                options.forEach { (h, label) -> ChoiceRow(label, selected = sel == h) { vm.setQuality(h) } }
                if (variants <= 1) {
                    val tsXtream = vm.current?.xtream == true && fmt == "ts"
                    Text(
                        if (tsXtream) "بث TS هذا له جودة واحدة فقط. بدّل إلى M3U8: كثير من الخوادم توفر عدة جودات فيه."
                        else "هذا البث له جودة واحدة فقط، لذلك لا يؤثر الاختيار عليه.",
                        color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
                    )
                    if (tsXtream) PillButton("التبديل إلى M3U8", modifier = Modifier.padding(top = 8.dp)) { vm.toggleFormat(); onClose() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}


// ---------- مؤقت النوم ----------

@Composable
private fun SleepDialog(vm: MainViewModel, onClose: () -> Unit) {
    val options = listOf(0 to "إيقاف", 15 to "15 دقيقة", 30 to "30 دقيقة", 60 to "60 دقيقة", 90 to "90 دقيقة")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("مؤقت النوم") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    ChoiceRow(label, selected = minutes == 0 && vm.sleepEndsAt == 0L) {
                        vm.setSleepTimer(minutes)
                        onClose()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}


// ---------- اختيار الصوت والترجمة ----------

private data class TrackOpt(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

@Composable
private fun rememberTracks(player: Player): Tracks {
    var t by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { t = tracks }
        }
        player.addListener(l)
        t = player.currentTracks
        onDispose { player.removeListener(l) }
    }
    return t
}

private fun trackLabel(f: Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let {
        Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).ifBlank { it }
    }
    val base = f.label ?: lang ?: "مسار $n"
    val ch = when {
        f.channelCount == 6 -> " (5.1)"
        f.channelCount == 8 -> " (7.1)"
        f.channelCount > 2 -> " (${f.channelCount}ch)"
        else -> ""
    }
    return base + ch
}

private fun options(t: Tracks, type: Int): List<TrackOpt> {
    var n = 0
    return t.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            n++
            TrackOpt(g, i, trackLabel(g.getTrackFormat(i), n), g.isTrackSelected(i))
        }
    }
}

@Composable
private fun TrackDialog(player: Player, tracks: Tracks, onClose: () -> Unit) {
    val audio = options(tracks, C.TRACK_TYPE_AUDIO)
    val text = options(tracks, C.TRACK_TYPE_TEXT)
    var subsOff by remember { mutableStateOf(text.none { it.selected }) }

    fun pick(o: TrackOpt) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(o.group.type, false)
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
            .build()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("الصوت / الترجمة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (audio.size > 1) {
                    Text("الصوت", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    audio.forEach { o -> ChoiceRow(o.label, o.selected) { pick(o) } }
                }
                if (text.isNotEmpty()) {
                    Text("الترجمة", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    ChoiceRow("إيقاف", subsOff) {
                        subsOff = true
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    }
                    text.forEach { o -> ChoiceRow(o.label, o.selected && !subsOff) { subsOff = false; pick(o) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusBorder().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
