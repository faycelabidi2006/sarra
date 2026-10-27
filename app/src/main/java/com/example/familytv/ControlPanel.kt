package com.example.familytv

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytv.data.CodeGen
import com.example.familytv.data.Member
import com.example.familytv.data.SbConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val panelProps = DialogProperties(usePlatformDefaultWidth = false)
private val OK = Color(0xFF7BE07B)
private const val DAY = 86_400_000L

private fun date(ms: Long?): String =
    if (ms == null || ms <= 0L) "-" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

private fun ago(ms: Long): String {
    if (ms <= 0L) return "أبداً"
    val m = (System.currentTimeMillis() - ms) / 60_000L
    return when {
        m < 1 -> "الآن"
        m < 60 -> "منذ $m دقيقة"
        m < 48 * 60 -> "منذ ${m / 60} ساعة"
        else -> "منذ ${m / (24 * 60)} يوماً"
    }
}

private fun daysLeft(ms: Long?): String {
    if (ms == null || ms <= 0L) return "بلا انتهاء"
    val d = (ms - System.currentTimeMillis()) / DAY
    return if (ms < System.currentTimeMillis()) "منتهٍ" else "باقي $d يوماً"
}

/** لوحة تحكم المالك: توليد حسابات، وأعضاء الخادم، وإحصاءات اشتراك Xtream. لا تُعرض لغير المالك. */
@Composable
fun ControlPanelDialog(vm: MainViewModel, onClose: () -> Unit) {
    val users by vm.users.collectAsStateWithLifecycle()
    val owner = users.firstOrNull { it.name == vm.sessionUser }?.owner == true
    if (!owner) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val secret by vm.adminSecret.collectAsStateWithLifecycle()
    var secretDraft by remember(secret) { mutableStateOf(secret) }
    var uname by remember { mutableStateOf("") }
    var upass by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(30) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadXtreamStats() }
    LaunchedEffect(secret) { if (secret.isNotBlank()) vm.refreshMembers() }

    val report: (Boolean, String) -> Unit = { ok, m -> msgOk = ok; msg = m }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = panelProps,
        title = { Text("لوحة التحكم") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ---- 1) الخادم
                Section("الخادم (Supabase)")
                Text(
                    if (SbConfig.configured) "متصل بـ: ${SbConfig.URL.removePrefix("https://")}"
                    else "غير مُعدّ. عدّل URL وANON_KEY في data/Server.kt ثم أعد بناء التطبيق.",
                    fontSize = 13.sp,
                    color = if (SbConfig.configured) Color.Unspecified else MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    secretDraft, { secretDraft = it }, label = { Text("سر المشرف (هذا الجهاز فقط)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("حفظ السر") { vm.setAdminSecret(secretDraft); msgOk = true; msg = "تم حفظ السر على هذا الجهاز" }
                    PillButton("تحديث", brush = null) { vm.refreshMembers(); vm.loadXtreamStats() }
                }

                // ---- 2) توليد حساب
                Section("إنشاء بيانات دخول")
                OutlinedTextField(uname, { uname = it; msg = "" }, label = { Text("اسم المستخدم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(upass, { upass = it; msg = "" }, label = { Text("كلمة المرور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 أيام", 30 to "30 يوماً", 90 to "90 يوماً", 365 to "سنة واحدة", 0 to "بلا انتهاء").forEach { (d, label) ->
                        PillButton(label, brush = if (days == d) Pal.accentBrush else null) { days = d }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("إنشاء", brush = null) { uname = CodeGen.username(); upass = CodeGen.password(); msg = "" }
                    PillButton("حفظ على الخادم", enabled = !vm.membersBusy) { vm.addMember(uname, upass, days, report) }
                    PillButton("نسخ", brush = null, enabled = uname.isNotBlank() && upass.isNotBlank()) {
                        val until = if (days > 0) date(System.currentTimeMillis() + days * DAY) else "بلا انتهاء"
                        clip.setText(AnnotatedString("اسم المستخدم: $uname\nكلمة المرور: $upass\nصالح حتى: $until"))
                        Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
                    }
                }
                Text("انسخ كلمة المرور الآن. يُخزَّن تجزئتها فقط لذلك لا يمكن عرضها مرة أخرى. يرتبط الحساب بأول جهاز يسجل الدخول.", fontSize = 12.sp)
                if (msg.isNotEmpty()) Text(msg, color = if (msgOk) OK else MaterialTheme.colorScheme.error, fontSize = 13.sp)

                // ---- 3) الأعضاء
                Section("الأعضاء (${vm.members.size})  -  المتصلون الآن: ${vm.members.count { it.online }}")
                if (vm.membersBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                if (vm.membersError.isNotEmpty()) Text(vm.membersError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                if (vm.members.isEmpty() && !vm.membersBusy && vm.membersError.isEmpty()) Text("لا يوجد أعضاء بعد.", fontSize = 13.sp)
                vm.members.forEach { m ->
                    MemberCard(
                        m, busy = vm.membersBusy, confirmDelete = pendingDelete == m.name,
                        onExtend = { vm.extendMember(m.name, 30, report) },
                        onToggle = { vm.setMemberDisabled(m.name, !m.disabled, report) },
                        onResetDevice = { vm.resetDevice(m.name, report) },
                        onDelete = {
                            if (pendingDelete == m.name) { pendingDelete = null; vm.deleteMember(m.name, report) }
                            else pendingDelete = m.name
                        }
                    )
                }

                // ---- 4) اشتراك Xtream
                Section("اشتراكات Xtream")
                if (vm.statsLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                if (vm.xtreamStats.isEmpty() && !vm.statsLoading) Text("لا يوجد حساب Xtream محفوظ.", fontSize = 13.sp)
                vm.xtreamStats.forEach { st ->
                    val shape = RoundedCornerShape(14.dp)
                    Column(Modifier.fillMaxWidth().glass(shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(st.playlist.name + "  (" + st.playlist.user + ")", fontWeight = FontWeight.SemiBold)
                        val i = st.info
                        if (i != null) {
                            Text("الحالة: ${i.status.ifBlank { "-" }}${if (i.trial) "  (تجريبي)" else ""}", fontSize = 13.sp)
                            Text("المتصلون الآن: ${i.active} من ${i.max}", fontSize = 13.sp)
                            Text("البداية: ${date(i.created)}", fontSize = 13.sp)
                            Text("النهاية: ${date(i.expires)}  (${daysLeft(i.expires)})", fontSize = 13.sp)
                        } else {
                            Text(st.error ?: "خطأ", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun MemberCard(
    m: Member, busy: Boolean, confirmDelete: Boolean,
    onExtend: () -> Unit, onToggle: () -> Unit, onResetDevice: () -> Unit, onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val status = when {
        m.disabled -> "معطّل"
        m.expired() -> "منتهٍ"
        else -> "فعّال"
    }
    Column(Modifier.fillMaxWidth().glass(shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(status, color = if (status == "فعّال") OK else MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Text("البداية: ${date(m.created)}   النهاية: ${date(m.expires)}  (${daysLeft(m.expires)})", fontSize = 13.sp)
        Text(
            if (m.online) "متصل الآن" + (if (m.watching.isNotBlank()) "  -  يشاهد: ${m.watching}" else "")
            else "آخر ظهور: ${ago(m.lastSeen)}",
            color = if (m.online) OK else Color.Unspecified, fontSize = 13.sp
        )
        Text(
            (if (m.deviceBound) "الجهاز: مرتبط منذ ${date(m.boundAt)}" else "الجهاز: غير مرتبط بعد") +
                "   المشاهدة: ${m.minutes / 60} س ${m.minutes % 60} د",
            fontSize = 13.sp
        )
        if (m.by.isNotBlank()) Text("أنشأه: ${m.by}", fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+30 يوماً", brush = null, enabled = !busy && m.expires > 0L, onClick = onExtend)
            PillButton("إعادة ضبط الجهاز", brush = null, enabled = !busy && m.deviceBound, onClick = onResetDevice)
            PillButton(if (m.disabled) "تفعيل" else "تعطيل", brush = null, enabled = !busy, onClick = onToggle)
            PillButton(if (confirmDelete) "اضغط مجدداً للحذف" else "حذف", brush = null, enabled = !busy, onClick = onDelete)
        }
    }
}
