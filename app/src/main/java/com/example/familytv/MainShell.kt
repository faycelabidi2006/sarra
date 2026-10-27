package com.example.familytv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.familytv.data.WatchProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainShell(vm: MainViewModel, onSettings: () -> Unit, onFullscreen: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // ملاحظة: داخل NavHost يكون مالك دورة الحياة هو "المدخل" وليس الـ Activity، فيصل ON_STOP
    // حين نفتح شاشة ملء الشاشة فوقه. لذلك لا نوقف التشغيل إذا كان ملء الشاشة هو سبب الإيقاف.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.saveProgressNow()
        if (!vm.inFullscreen) vm.player.pause()
    }
    // استعادة التركيز تخص تبويب البث المباشر فقط؛ إن عدنا إلى تبويب آخر نلغي العلم
    LaunchedEffect(tab) { if (tab != 1) vm.focusRestore = false }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                tab = tab, onTab = { tab = it },
                query = query,
                onQuery = { query = it; if (it.isNotBlank()) tab = 1 },
                searchFocus = searchFocus,
                onSettings = onSettings
            )
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 30 }).togetherWith(fadeOut(tween(120)))
                },
                label = "tab"
            ) { t ->
                when (t) {
                    0 -> HomeTab(vm, onTab = { tab = it }, onSettings = onSettings, onFullscreen = onFullscreen)
                    1 -> LiveTvScreen(vm, query, searchFocus, onFullscreen)
                    2 -> MoviesScreen(vm, onFullscreen)
                    3 -> SeriesScreen(vm, onFullscreen)
                    else -> DiscoverScreen(vm, onTab = { tab = it }, onFullscreen = onFullscreen)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    tab: Int, onTab: (Int) -> Unit,
    query: String, onQuery: (String) -> Unit,
    searchFocus: FocusRequester, onSettings: () -> Unit
) {
    val tabs = listOf("الرئيسية", "البث المباشر", "الأفلام", "المسلسلات", "اكتشف")
    if (isCompact()) {
        // هاتف عمودي: صف علوي (شعار + بحث + إعدادات) وتحته التبويبات قابلة للتمرير
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                SearchPill(query, onQuery, searchFocus, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(42.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onSettings),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White) }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).glass(CircleShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tabs.forEachIndexed { i, t -> TabPill(t, selected = tab == i) { onTab(i) } }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))

            Row(Modifier.glass(CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { i, t -> TabPill(t, selected = tab == i) { onTab(i) } }
            }
            Spacer(Modifier.width(14.dp))
            SearchPill(query, onQuery, searchFocus)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onSettings),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White) }
        }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "tabBg")
    val textColor by animateColorAsState(if (selected) Color.White else Pal.textDim, tween(250), label = "tabText")
    Box(
        Modifier
            .focusBorder(CircleShape)
            .clip(CircleShape)
            .background(Pal.accentBrush, CircleShape, alpha = bgAlpha)
            .bounceClick(onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchPill(
    query: String, onQuery: (String) -> Unit, focus: FocusRequester,
    modifier: Modifier = Modifier.width(250.dp)
) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Pal.blue),
        modifier = modifier
            .height(42.dp)
            .focusRequester(focus)
            .focusBorder(CircleShape),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().glass(CircleShape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Pal.textDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("ابحث في القنوات", color = Pal.textDim, fontSize = 15.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
private fun HomeTab(vm: MainViewModel, onTab: (Int) -> Unit, onSettings: () -> Unit, onFullscreen: () -> Unit) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val resume by vm.progress.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    val byId = remember(vm.channels) { vm.channels.associateBy { it.id } }
    val recents = recent.mapNotNull { byId[it] }
    val subtitle = if (playlists.isEmpty()) "أضف حساباً للبدء"
    else "${playlists.getOrNull(active)?.name ?: ""}  -  ${vm.channels.size} قناة"

    val compact = isCompact()
    Column(
        Modifier
            .fillMaxSize()
            .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Entrance(0) {
            Column {
                Text("ماذا تريد أن تشاهد؟", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Pal.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Entrance(1) {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HeroTile("البث المباشر", "القنوات الآن", Icons.Default.PlayArrow, Pal.accentBrush, Modifier.weight(1f)) { onTab(1) }
                        HeroTile("الأفلام", "عند الطلب", Icons.Default.Star, Pal.warmBrush, Modifier.weight(1f)) { onTab(2) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HeroTile("المسلسلات", "عند الطلب", Icons.Default.List, Pal.coolBrush, Modifier.weight(1f)) { onTab(3) }
                        HeroTile("الإعدادات", "الحسابات والمزيد", Icons.Default.Settings, Pal.slateBrush, Modifier.weight(1f)) { onSettings() }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeroTile("البث المباشر", "القنوات الآن", Icons.Default.PlayArrow, Pal.accentBrush, Modifier.weight(1f)) { onTab(1) }
                    HeroTile("الأفلام", "عند الطلب", Icons.Default.Star, Pal.warmBrush, Modifier.weight(1f)) { onTab(2) }
                    HeroTile("المسلسلات", "عند الطلب", Icons.Default.List, Pal.coolBrush, Modifier.weight(1f)) { onTab(3) }
                    HeroTile("الإعدادات", "الحسابات والمزيد", Icons.Default.Settings, Pal.slateBrush, Modifier.weight(1f)) { onSettings() }
                }
            }
        }
        if (resume.isNotEmpty()) {
            Entrance(2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("متابعة المشاهدة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(resume, key = { it.ch.id }) { wp ->
                            ResumeCard(wp, onPlay = { vm.play(wp.ch, addToRecent = false); onFullscreen() }, onRemove = { vm.removeProgress(wp.ch.id) })
                        }
                    }
                }
            }
        }
        if (playlists.isEmpty()) {
            Entrance(2) {
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "لا توجد قنوات بعد. أضف حساب Xtream أو رابط قائمة تشغيل.",
                        color = Color.White, modifier = Modifier.weight(1f)
                    )
                    PillButton("فتح الإعدادات", onClick = onSettings)
                }
            }
        } else if (recents.isNotEmpty()) {
            Entrance(2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("القنوات الأخيرة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recents, key = { it.id }) { ch ->
                            Row(
                                Modifier
                                    .width(230.dp)
                                    .focusBorder(RoundedCornerShape(16.dp))
                                    .glass(RoundedCornerShape(16.dp))
                                    .bounceClick { vm.play(ch); onTab(1) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(Pal.accentBrush),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                                Text(
                                    ch.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(wp: WatchProgress, onPlay: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val frac = (wp.pos.toFloat() / wp.dur.toFloat()).coerceIn(0f, 1f)
    val leftMin = ((wp.dur - wp.pos) / 60_000L).toInt().coerceAtLeast(1)
    Column(Modifier.width(200.dp).focusBorder(shape).glass(shape).clip(shape).bounceClick(onPlay)) {
        Box(Modifier.fillMaxWidth().height(112.dp).background(Pal.glassStrong)) {
            if (wp.ch.logo.isNotBlank()) {
                AsyncImage(
                    model = wp.ch.logo, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            Icon(
                Icons.Default.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp)
                    .clip(CircleShape).background(Color(0x99000000)).bounceClick(onRemove),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        LinearProgressIndicator(
            progress = { frac }, color = Pal.blue, trackColor = Color(0x33FFFFFF),
            modifier = Modifier.fillMaxWidth().height(3.dp)
        )
        Column(Modifier.padding(10.dp)) {
            Text(wp.ch.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("باقي $leftMin د", color = Pal.textDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HeroTile(
    title: String, subtitle: String, icon: ImageVector, brush: Brush,
    modifier: Modifier, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .height(124.dp)
            .focusBorder(shape)
            .clip(shape)
            .background(brush)
            .bounceClick(onClick)
            .padding(16.dp)
    ) {
        Icon(
            icon, contentDescription = null, tint = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(96.dp).align(Alignment.BottomEnd).offset(x = 22.dp, y = 22.dp)
        )
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ComingSoon(name: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier.glass(RoundedCornerShape(24.dp)).padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "يتطلب حساباً يحتوي على محتوى عند الطلب. قريباً في التحديث القادم.",
                color = Pal.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
