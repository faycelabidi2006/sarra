package com.example.familytv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** level: -1 لا توجد قناة، 0 منقطع، 1 إلى 4 عدد الأعمدة المضاءة. */
data class Signal(val level: Int, val label: String, val mbps: Float?)

/** مؤشر قوة التدفق بأربعة أعمدة: أخضر جيد، برتقالي ضعيف، أحمر منقطع أو ضعيف جداً. */
@Composable
fun SignalMeter(vm: MainViewModel, modifier: Modifier = Modifier) {
    var signal by remember { mutableStateOf(Signal(-1, "", null)) }
    LaunchedEffect(vm.current?.id) {
        while (true) {
            signal = vm.computeSignal()
            delay(1000)
        }
    }

    val target = when {
        signal.level >= 3 -> Pal.green
        signal.level == 2 -> Pal.orange
        signal.level >= 0 -> Pal.live
        else -> Pal.textDim
    }
    val color by animateColorAsState(target, tween(300), label = "signalColor")

    Row(
        modifier
            .clip(CircleShape)
            .background(Pal.glass)
            .border(1.dp, Pal.stroke, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.height(18.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 1..4) {
                val barColor by animateColorAsState(
                    if (i <= signal.level) target else Color(0x33FFFFFF),
                    tween(300), label = "bar$i"
                )
                Box(
                    Modifier
                        .width(5.dp)
                        .height((6 + (i - 1) * 4).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }
        if (signal.level >= 0) {
            Text(
                signal.label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp)
            )
            signal.mbps?.let {
                Text("%.1f Mbps".format(it), color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Text("لا يوجد بث", color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
