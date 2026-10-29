package com.example.familytv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytv.data.Channel

/** تبويب المسلسلات: شبكة المسلسلات، ثم شاشة المسلسل (المواسم والحلقات). لحسابات Xtream فقط. */
@Composable
fun SeriesScreen(vm: MainViewModel, onFullscreen: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val hiddenAll by vm.hidden.collectAsStateWithLifecycle()
    val hiddenSer = remember(hiddenAll) { hiddenAll.filter { it.startsWith("ser|") }.map { it.removePrefix("ser|") }.toSet() }
    val pl = playlists.getOrNull(active)
    val isXtream = pl != null && pl.user.isNotBlank()
    val open = vm.openSeries

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.player.pause() }
    LaunchedEffect(pl) { vm.loadSeries() }
    BackHandler(enabled = open != null) { vm.closeSeries() }

    when {
        !isXtream -> MoviesMessage("المسلسلات تحتاج إلى حساب Xtream. افتح الإعدادات وأضف حساباً (رابط M3U العادي لا يحتوي على قائمة مسلسلات منفصلة).") {}
        open != null -> SeriesDetail(vm, open, onFullscreen)
        vm.seriesLoading && vm.series.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Pal.blue) }
        vm.seriesError != null -> MoviesMessage(vm.seriesError ?: "") {
            PillButton("إعادة المحاولة", onClick = { vm.loadSeries(force = true) })
        }
        else -> PosterBrowser(vm.series, "مسلسلات", favs, { vm.toggleFavorite(it) }, hiddenGroups = hiddenSer) { vm.openSeries(it) }
    }
}

@Composable
private fun SeriesDetail(vm: MainViewModel, show: Channel, onFullscreen: () -> Unit) {
    val eps = vm.episodes
    val seasons = remember(eps) { eps.map { it.season }.distinct().sorted() }
    var picked by rememberSaveable(show.id) { mutableStateOf<Int?>(null) }
    val season = picked?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val shown = remember(eps, season) { eps.filter { it.season == season } }

    Column(Modifier.fillMaxSize().padding(horizontal = if (isCompact()) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick { vm.closeSeries() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            Text(
                show.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        when {
            vm.episodesLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Pal.blue) }
            vm.episodesError != null -> MoviesMessage(vm.episodesError ?: "") {
                PillButton("إعادة المحاولة", onClick = { vm.loadEpisodes(show) })
                PillButton("رجوع", brush = null, onClick = { vm.closeSeries() })
            }
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it }) { s ->
                        PillButton("الموسم $s", brush = if (season == s) Pal.accentBrush else null, onClick = { picked = s })
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown, key = { it.id }) { ep ->
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier.fillMaxWidth().focusBorder(shape).glass(shape)
                                .bounceClick { vm.playEpisode(show, ep); onFullscreen() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Text(
                                "ح${ep.num}  ${ep.title}", color = Color.White, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
