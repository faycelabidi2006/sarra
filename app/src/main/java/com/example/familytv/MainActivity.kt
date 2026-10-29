package com.example.familytv

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    // نفس نسخة الـ ViewModel التي يستخدمها AppRoot (المفتاح الافتراضي واحد)
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addOnPictureInPictureModeChangedListener { info -> vm.pipActive = info.isInPictureInPictureMode }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Pal.violet,
                    onPrimary = Color.White,
                    secondary = Pal.blue,
                    background = Pal.bgBottom,
                    surface = Color(0xFF12162A),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF1C2140),
                    onSurfaceVariant = Color(0xCCFFFFFF),
                    surfaceContainerHigh = Color(0xFF181D36),
                    error = Color(0xFFFF6B81)
                )
            ) { AppRoot() }
        }
    }

    /** عند الضغط على زر الرئيسية أثناء مشاهدة ملء الشاشة نتحول إلى نافذة صغيرة بدل إيقاف الفيديو. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!vm.inFullscreen || !vm.player.isPlaying) return
        vm.pipEntered = true
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (e: Exception) {
            vm.pipEntered = false
        }
    }
}
