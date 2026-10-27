package com.example.familytv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.familytv.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.familytv.data.Net
import com.example.familytv.data.NetResponse
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecInfo(val channel: String, val startedAt: Long, val path: String, val bytes: Long)

/** حالة التسجيل الجاري (تقرأها الواجهة). */
object RecordingState {
    val info = MutableStateFlow<RecInfo?>(null)
    val message = MutableStateFlow<String?>(null)
}

/** ينسخ البث إلى OutputStream: بث TS مباشر، أو HLS (m3u8) غير مشفّر. */
object StreamRecorder {
    @Volatile private var aborted = false
    @Volatile private var conn: NetResponse? = null

    fun abort() {
        aborted = true
        try { conn?.disconnect() } catch (_: Exception) {}
    }

    private fun open(url: String): NetResponse {
        val c = Net.open(url, 15_000, 20_000)
        conn = c
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            throw IOException("ردّ الخادم برمز HTTP $code")
        }
        return c
    }

    /** عملية حاجبة: تُستدعى من خيط خلفي. الإيقاف اليدوي عبر abort() ليس خطأً. */
    fun record(url: String, out: OutputStream, maxMillis: Long, lowSpace: () -> Boolean, onBytes: (Long) -> Unit) {
        aborted = false
        try {
            if (url.substringBefore('?').lowercase().endsWith(".m3u8")) recordHls(url, out, maxMillis, lowSpace, onBytes)
            else recordRaw(url, out, maxMillis, lowSpace, onBytes)
        } catch (e: IOException) {
            if (!aborted) throw e
        }
    }

    private fun recordRaw(url: String, out: OutputStream, maxMillis: Long, lowSpace: () -> Boolean, onBytes: (Long) -> Unit) {
        val c = open(url)
        try {
            val started = SystemClock.elapsedRealtime()
            var total = 0L
            var lastReport = 0L
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (!aborted) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReport >= 1000) {
                        lastReport = now
                        onBytes(total)
                        if (lowSpace()) throw IOException("مساحة التخزين شبه ممتلئة")
                    }
                    if (now - started >= maxMillis) break
                }
            }
            onBytes(total)
        } finally {
            c.disconnect()
        }
    }

    private fun httpText(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun resolve(base: String, rel: String): String = URL(URL(base), rel).toString()

    private fun recordHls(url: String, out: OutputStream, maxMillis: Long, lowSpace: () -> Boolean, onBytes: (Long) -> Unit) {
        var mediaUrl = url
        var text = httpText(mediaUrl)

        if (text.contains("#EXT-X-STREAM-INF")) {
            var bestBw = -1L
            var bestUri: String? = null
            val lines = text.lines()
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (uri != null && bw > bestBw) { bestBw = bw; bestUri = uri.trim() }
                }
            }
            val chosen = bestUri ?: throw IOException("لم يُعثر على جودة قابلة للتشغيل")
            mediaUrl = resolve(mediaUrl, chosen)
            text = httpText(mediaUrl)
        }
        if (Regex("#EXT-X-KEY:.*METHOD=(?!NONE)").containsMatchIn(text)) {
            throw IOException("لا يمكن تسجيل البث المشفّر")
        }
        if (text.contains("#EXT-X-MAP")) throw IOException("لا يمكن تسجيل هذا النوع من البث (fMP4) حالياً")

        val seen = LinkedHashSet<String>()
        var total = 0L
        var failures = 0
        val started = SystemClock.elapsedRealtime()

        while (!aborted && SystemClock.elapsedRealtime() - started < maxMillis) {
            for (line in text.lines()) {
                if (aborted) break
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                val seg = resolve(mediaUrl, l)
                if (!seen.add(seg)) continue
                val c = open(seg)
                try {
                    c.inputStream.use { input ->
                        val n = input.copyTo(out)
                        total += n
                    }
                } finally {
                    c.disconnect()
                }
                onBytes(total)
                if (lowSpace()) throw IOException("مساحة التخزين شبه ممتلئة")
            }
            while (seen.size > 300) {
                val iter = seen.iterator()
                iter.next()
                iter.remove()
            }
            if (text.contains("#EXT-X-ENDLIST")) break

            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6L
            var waited = 0L
            while (!aborted && waited < target * 500) {
                Thread.sleep(250)
                waited += 250
            }
            try {
                text = httpText(mediaUrl)
                failures = 0
            } catch (e: IOException) {
                if (aborted) break
                failures++
                if (failures >= 5) throw e
            }
        }
    }
}

/** خدمة أمامية: تُبقي التسجيل يعمل حتى لو أُغلقت الشاشة أو انتقلت لتطبيق آخر. */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.example.familytv.REC_START"
        const val ACTION_STOP = "com.example.familytv.REC_STOP"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_URL = "url"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 42
        private const val MAX_MILLIS = 3 * 60 * 60 * 1000L   // ثلاث ساعات كحد أقصى للتسجيل الواحد

        fun start(ctx: Context, name: String, url: String) {
            val i = Intent(ctx, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_URL, url)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "التسجيل", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent?.getStringExtra(EXTRA_NAME) ?: "قناة"
                val url = intent?.getStringExtra(EXTRA_URL)
                if (url == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    ServiceCompat.startForeground(
                        this, NOTIF_ID, buildNotification(name, "جارٍ التسجيل..."),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    RecordingState.message.value = "تعذر بدء التسجيل"
                    stopSelf()
                    return START_NOT_STICKY
                }
                startRecording(name, url)
            }
            ACTION_STOP -> if (job?.isActive == true) StreamRecorder.abort() else stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(name: String, text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("تسجيل: $name")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "إيقاف", stop)
            .build()
    }

    private fun startRecording(name: String, url: String) {
        if (job?.isActive == true) return
        val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "channel" }.take(40)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val fileName = "${safe}_$stamp.ts"

        val nm = getSystemService(NotificationManager::class.java)
        var lastNotify = 0L

        job = scope.launch {
            var message: String? = null
            var target: RecTarget? = null
            try {
                val choice = Prefs.recordDir(this@RecordingService).first()
                val t = RecStorage.open(this@RecordingService, choice, fileName)
                target = t
                RecordingState.info.value = RecInfo(name, System.currentTimeMillis(), t.path, 0L)
                t.out.use { out ->
                    StreamRecorder.record(url, out, MAX_MILLIS, t.lowSpace) { bytes ->
                        RecordingState.info.value = RecordingState.info.value?.copy(bytes = bytes)
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 5000) {
                            lastNotify = now
                            nm.notify(NOTIF_ID, buildNotification(name, "${bytes / 1_000_000} MB"))
                        }
                    }
                }
                message = if (t.size() > 0) "تم حفظ التسجيل: ${t.name}" else "لم يتم تسجيل شيء"
            } catch (e: Exception) {
                message = "توقف التسجيل: ${e.message ?: "انقطع الاتصال"}"
            } finally {
                RecordingState.info.value = null
                target?.let { if (it.size() == 0L) it.delete() }
                RecordingState.message.value = message ?: "توقف التسجيل"
                ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        StreamRecorder.abort()
        scope.cancel()
        RecordingState.info.value = null
        super.onDestroy()
    }
}
