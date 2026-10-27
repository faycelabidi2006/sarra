package com.example.familytv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Pal {
    val bgTop = Color(0xFF0C1022)
    val bgBottom = Color(0xFF05060D)
    val violet = Color(0xFF7C5CFF)
    val blue = Color(0xFF2EA8FF)
    val pink = Color(0xFFFF4D8D)
    val orange = Color(0xFFFFA043)
    val teal = Color(0xFF1FD6B4)
    val green = Color(0xFF3DDC84)
    val live = Color(0xFFFF3B5C)

    val glass = Color(0x14FFFFFF)
    val glassStrong = Color(0x24FFFFFF)
    val stroke = Color(0x22FFFFFF)
    val textDim = Color(0xB3FFFFFF)

    val accentBrush = Brush.horizontalGradient(listOf(violet, blue))
    val warmBrush = Brush.horizontalGradient(listOf(pink, orange))
    val coolBrush = Brush.horizontalGradient(listOf(teal, green))
    val slateBrush = Brush.horizontalGradient(listOf(Color(0xFF5B6585), Color(0xFF353D5A)))
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.bgTop, Pal.bgBottom)))
            .drawBehind {
                val c1 = Offset(size.width * 0.08f, 0f)
                val r1 = size.width * 0.5f
                drawCircle(
                    brush = Brush.radialGradient(listOf(Pal.violet.copy(alpha = 0.32f), Color.Transparent), center = c1, radius = r1),
                    radius = r1, center = c1
                )
                val c2 = Offset(size.width, size.height)
                val r2 = size.width * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(listOf(Pal.blue.copy(alpha = 0.2f), Color.Transparent), center = c2, radius = r2),
                    radius = r2, center = c2
                )
            }
    ) { content() }
}

/** تكبير لطيف + إطار أبيض عند التحديد بالريموت أو اللمس (مهم لصناديق Android TV). */
fun Modifier.focusBorder(shape: Shape = RoundedCornerShape(16.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.04f else 1f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "focusScale"
    )
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "focusBorder")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.hasFocus }
        .border(2.dp, Color.White.copy(alpha = borderAlpha), shape)
}

/** نقرة مع ارتداد خفيف عند الضغط. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(if (strong) Pal.glassStrong else Pal.glass, shape)
        .border(1.dp, Pal.stroke, shape)

/** زر كبسولة: ملوّن بتدرج، أو زجاجي إذا كان brush = null. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    brush: Brush? = Pal.accentBrush,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = CircleShape
    val fill = if (brush != null) Modifier.background(brush, shape)
    else Modifier.background(Pal.glassStrong, shape).border(1.dp, Pal.stroke, shape)
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .focusBorder(shape)
            .clip(shape)
            .then(fill)
            .then(if (enabled) Modifier.bounceClick(onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** شارة "LIVE" بنقطة تنبض: حالة البث الجاري. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "liveAlpha"
    )
    Row(
        modifier.clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).alpha(a).background(Pal.live, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** ثلاثة أعمدة تتحرك: تدل على القناة الجارية. */
@Composable
fun NowPlayingBars(modifier: Modifier = Modifier, color: Color = Pal.blue) {
    val t = rememberInfiniteTransition(label = "bars")
    val h1 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b1")
    val h2 by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val h3 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "b3")
    Row(
        modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3).forEach { h ->
            Box(Modifier.width(3.dp).fillMaxHeight(h).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

/** ظهور متدرج: يُستخدم مرة واحدة في الصفحة الرئيسية. */
@Composable
fun Entrance(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420, delayMillis = index * 80)) +
            slideInVertically(tween(420, delayMillis = index * 80)) { it / 5 }
    ) { content() }
}

/** شعار التطبيق داخل الواجهة (نفس رسم أيقونة التشغيل). */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(24))
            .background(Brush.linearGradient(listOf(Color(0xFF090909), Color(0xFF241500), Color(0xFF090909)))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(R.drawable.scorpion_launcher),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** شاشة البداية: إطار "Scorpion TV" السينمائي، تكبير وتلاشي خفيف ثم تختفي. */
@Composable
fun SplashScreen() {
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { zoomed = true }
    val scale by animateFloatAsState(
        targetValue = if (zoomed) 1.06f else 1f,
        animationSpec = tween(1800, easing = LinearOutSlowInEasing),
        label = "splashZoom"
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painterResource(R.drawable.scorpion_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

fun openExternal(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "لم يُعثر على مشغّل خارجي", Toast.LENGTH_SHORT).show()
    }
}

/** هاتف بالوضع العمودي (أو أي شاشة ضيقة). التلفاز والهاتف الأفقي يبقيان على التخطيط العريض. */
@Composable
fun isCompact(): Boolean = when (LayoutMode.mode) {
    "phone" -> true
    "tv" -> false
    else -> LocalConfiguration.current.screenWidthDp < 600
}

/** اختيار المستخدم لشكل الواجهة: auto أو phone أو tv. حالة Compose حتى تُحدَّث الشاشات فوراً. */
object LayoutMode {
    var mode: String by androidx.compose.runtime.mutableStateOf("auto")
}

/** صف في الشاشات العريضة، وعمود في الشاشات الضيقة. w(x) يعطي weight(x) في الصف وملء العرض في العمود. */
@Composable
fun AdaptiveFields(
    spacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable (w: (Float) -> Modifier) -> Unit
) {
    if (isCompact()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) { content { Modifier.fillMaxWidth() } }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
            content { w -> Modifier.weight(w) }
        }
    }
}
