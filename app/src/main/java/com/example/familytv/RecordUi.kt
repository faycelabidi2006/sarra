package com.example.familytv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytv.data.Channel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val wideRec = DialogProperties(usePlatformDefaultWidth = false)

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** زر تسجيل: "Record" عادي، وأحمر "Stop" مع الوقت أثناء التسجيل. */
@Composable
fun RecordButton(vm: MainViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val rec by RecordingState.info.collectAsStateWithLifecycle()
    val r = rec
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(r != null) {
        while (r != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val cur = vm.current
    val start: () -> Unit = {
        cur?.let { RecordingService.start(ctx, it.name, if (it.xtream) "${it.url}.ts" else it.url) }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    val begin: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else start()
    }

    PillButton(
        text = if (r != null) "■ إيقاف   ${formatElapsed(now - r.startedAt)}" else "● تسجيل",
        modifier = modifier,
        brush = if (r != null) Brush.horizontalGradient(listOf(Pal.live, Color(0xFFFF7A45))) else null,
        enabled = r != null || cur != null
    ) {
        if (r != null) RecordingService.stop(ctx) else begin()
    }
}

/** اختيار مكان حفظ التسجيلات: ذاكرة الهاتف، أو بطاقة SD / فلاشة USB، أو أي مجلد. */
@Composable
fun StorageDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val choice by vm.recordDir.collectAsStateWithLifecycle()
    val volumes = remember { RecStorage.volumes(ctx) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // يبقى الاختيار صالحاً لهذه الجلسة على الأقل
            }
            vm.setRecordDir("tree:$uri")
        }
    }
    val chooseFolder = {
        try {
            picker.launch(null)
        } catch (e: Exception) {
            Toast.makeText(ctx, "لا يحتوي هذا الجهاز على منتقي مجلدات. استخدم إحدى وحدات التخزين المعروضة أعلاه.", Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideRec,
        title = { Text("مكان حفظ التسجيلات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                volumes.forEachIndexed { i, v ->
                    val value = if (i == 0) "" else "dir:${v.dir.absolutePath}"
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setRecordDir(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = choice == value, onClick = { vm.setRecordDir(value) })
                        Column(Modifier.weight(1f)) {
                            Text(v.label)
                            Text(
                                "%.1f جيجابايت متاحة  -  %s".format(v.freeBytes / 1_073_741_824.0, v.dir.absolutePath),
                                fontSize = 11.sp, color = Pal.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { chooseFolder() }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = choice.startsWith("tree:"), onClick = { chooseFolder() })
                    Column(Modifier.weight(1f)) {
                        Text("اختيار مجلد آخر...")
                        Text(
                            if (choice.startsWith("tree:")) RecStorage.describe(ctx, choice)
                            else "أي مجلد على الهاتف أو بطاقة SD أو قرص USB",
                            fontSize = 11.sp, color = Pal.textDim
                        )
                    }
                }
                Text(
                    "التسجيلات في مجلدات التطبيق تُحذف عند إزالة التطبيق. أما المجلد الذي تختاره بنفسك فيبقى.",
                    fontSize = 11.sp, color = Pal.textDim, modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("تم") } }
    )
}

@Composable
fun RecordingsDialog(vm: MainViewModel, onPlay: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val rec by RecordingState.info.collectAsStateWithLifecycle()
    val choice by vm.recordDir.collectAsStateWithLifecycle()
    val items = remember(refresh, rec, choice) { RecStorage.list(ctx, choice) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val hint = RecStorage.pathHint(ctx, choice)

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideRec,
        title = { Text("التسجيلات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "الموقع: ${RecStorage.describe(ctx, choice)}" + if (hint.isNotEmpty()) "  ($hint)" else "",
                    fontSize = 11.sp, color = Pal.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (items.isEmpty()) {
                    Text("لا توجد تسجيلات بعد. اضغط تسجيل أثناء مشاهدة قناة.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(items, key = { it.path }) { it ->
                            val mb = it.size / 1_048_576.0
                            val size = if (mb >= 1024) "%.2f جيجابايت".format(mb / 1024) else "%.0f ميجابايت".format(mb)
                            val recording = rec?.path == it.path
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "$size  -  ${fmt.format(Date(it.modified))}" + if (recording) "  -  جارٍ التسجيل..." else "",
                                        fontSize = 12.sp, color = Pal.textDim
                                    )
                                }
                                TextButton(onClick = {
                                    val ch = Channel(
                                        id = "rec_${it.name}", num = 0, name = it.name.substringBeforeLast('.'),
                                        logo = "", url = it.uri, group = "تسجيل"
                                    )
                                    vm.play(ch, addToRecent = false)
                                    onClose()
                                    onPlay()
                                }) { Text("تشغيل") }
                                IconButton(onClick = {
                                    if (!recording) { it.delete(); refresh++ }
                                }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}
