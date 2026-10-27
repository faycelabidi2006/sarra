package com.example.familytv

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
