package com.example.familytv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
    ) { AppRootInner(vm) }
}

@Composable
private fun AppRootInner(vm: MainViewModel) {
    var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200)
        showSplash = false
    }
    if (showSplash) {
        SplashScreen()
        return
    }
    when {
        !vm.authReady -> AppBackground { }
        vm.sessionUser == null -> LoginScreen(vm)
        vm.appLocked && !vm.isOwner -> AppLockedScreen()
        vm.licenseExpired && !vm.isOwner -> LicenseExpiredScreen(vm)
        else -> {
            val ctx = LocalContext.current
            val recMessage by RecordingState.message.collectAsStateWithLifecycle()
            LaunchedEffect(recMessage) {
                recMessage?.let {
                    Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
                    RecordingState.message.value = null
                }
            }
            val layoutPref by vm.layout.collectAsStateWithLifecycle()
            androidx.compose.runtime.SideEffect { LayoutMode.mode = layoutPref }
            val nav = rememberNavController()
            NavHost(nav, startDestination = "main") {
                composable("main") {
                    MainShell(vm, onSettings = { nav.navigate("settings") }, onFullscreen = { nav.navigate("fullscreen") })
                }
                composable("settings") {
                    SettingsScreen(vm, onBack = { nav.popBackStack() }, onPlay = { nav.navigate("fullscreen") })
                }
                composable("fullscreen") { FullscreenScreen(vm, onBack = { nav.popBackStack() }) }
            }
        }
    }
}

/** شاشة كاملة تُعرض لكل من ليس مالكاً عندما يُغلق المالك التطبيق عن بُعد. */
@Composable
private fun AppLockedScreen() {
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("التطبيق مغلق مؤقتاً", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "تواصل مع إدارة التطبيق لإعادة فتحه.",
                color = Pal.textDim, fontSize = 15.sp, textAlign = TextAlign.Center
            )
        }
    }
}
