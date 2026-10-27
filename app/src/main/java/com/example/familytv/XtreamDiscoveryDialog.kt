package com.example.familytv

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Discover publicly advertised Xtream providers/trials; does not harvest credentials. */
@Composable
fun XtreamDiscoveryDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.88f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("البحث عن Xtream جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ابحث عن مزوّدي Xtream الذين يعلنون خدماتهم علنًا أو يقدّمون تجربة رسمية. لا يجمع التطبيق كلمات مرور مسرّبة ولا يختبر حسابات الآخرين.")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("اسم الخدمة أو البلد (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PillButton(
                    text = "البحث عن مزوّدين وتجارب رسمية",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val terms = (if (query.isBlank()) "Xtream Codes official provider free trial" else "Xtream Codes ${query.trim()} official provider free trial")
                        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(terms))
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: Exception) { Toast.makeText(ctx, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show() }
                    }
                )
                Text("بعد الحصول على بيانات دخول من المزوّد، استخدم زر «إضافة حساب Xtream» بشكل منفصل لاختبارها وتحميل المحتوى.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}
