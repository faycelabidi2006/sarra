package com.example.familytv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familytv.data.License
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** حقل إدخال كود التفعيل. collapsed = يظهر كرابط صغير في شاشة الدخول. */
@Composable
fun ActivationBox(vm: MainViewModel, collapsed: Boolean = false) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(!collapsed) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (!open) {
        TextButton(onClick = { open = true }) { Text("لدي كود تفعيل") }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            code, { code = it.filter { c -> c.isDigit() }; error = null },
            label = { Text("كود التفعيل") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        PillButton(if (busy) "جارٍ التفعيل..." else "تفعيل", Modifier.fillMaxWidth(), enabled = !busy && code.isNotBlank()) {
            busy = true
            vm.activateLicense(code) { err ->
                busy = false
                error = err
                if (err == null) {
                    code = ""
                    Toast.makeText(ctx, "تم التفعيل حتى ${licenseDate(vm.licenseEnd)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

fun licenseDate(ms: Long): String = when {
    ms == License.NEVER -> "بلا انتهاء"
    ms <= 0L -> "-"
    else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
}

/** تُعرض لغير المالك عند انتهاء مدة الكود. الدخول بكود جديد يعيد فتح التطبيق فوراً. */
@Composable
fun LicenseExpiredScreen(vm: MainViewModel) {
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("انتهت مدة الاستخدام", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (vm.licenseEnd > 0L) "انتهى الترخيص في ${licenseDate(vm.licenseEnd)}. أدخل كود تفعيل جديداً للمتابعة."
                else "أدخل كود التفعيل للمتابعة.",
                color = Pal.textDim, fontSize = 15.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            ActivationBox(vm)
            TextButton(onClick = { vm.logout() }) { Text("تسجيل الخروج") }
        }
    }
}
