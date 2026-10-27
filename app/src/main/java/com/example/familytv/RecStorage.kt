package com.example.familytv

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

data class Volume(val label: String, val dir: File, val freeBytes: Long)

/** وجهة التسجيل المفتوحة للكتابة (ملف عادي أو مجلد اختاره المستخدم). */
class RecTarget(
    val out: OutputStream,
    val path: String,
    val lowSpace: () -> Boolean,
    val size: () -> Long,
    val delete: () -> Unit,
    val name: String
)

data class RecItem(
    val name: String,
    val size: Long,
    val modified: Long,
    val uri: String,
    val path: String,
    val delete: () -> Boolean
)

/**
 * اختيار مكان الحفظ، مخزَّن كنص:
 *  ""            = ذاكرة الهاتف (مجلد التطبيق)
 *  "dir:<path>"  = مجلد التطبيق على وحدة تخزين خارجية (SD أو USB) مكتشفة تلقائياً
 *  "tree:<uri>"  = أي مجلد يختاره المستخدم عبر منتقي الملفات
 */
object RecStorage {
    private const val MIN_FREE = 300L * 1024 * 1024

    fun volumes(ctx: Context): List<Volume> {
        val result = ArrayList<Volume>()
        val dirs = ctx.getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
        dirs.forEachIndexed { i, d ->
            if (d == null) return@forEachIndexed
            val removable = try { Environment.isExternalStorageRemovable(d) } catch (e: Exception) { i > 0 }
            val id = d.absolutePath.split("/").getOrNull(2) ?: ""
            val label = if (i == 0 && !removable) "ذاكرة الهاتف" else "وحدة تخزين خارجية ($id)"
            result.add(Volume(label, File(d, "التسجيلات"), d.usableSpace))
        }
        if (result.isEmpty()) {
            result.add(Volume("ذاكرة الهاتف", File(ctx.filesDir, "التسجيلات"), ctx.filesDir.usableSpace))
        }
        return result
    }

    private fun fileDir(ctx: Context, choice: String): File =
        if (choice.startsWith("dir:")) File(choice.removePrefix("dir:")) else volumes(ctx).first().dir

    fun describe(ctx: Context, choice: String): String = when {
        choice.startsWith("tree:") ->
            DocumentFile.fromTreeUri(ctx, Uri.parse(choice.removePrefix("tree:")))?.name?.let { "المجلد: $it" } ?: "مجلد مخصص"
        choice.startsWith("dir:") ->
            volumes(ctx).firstOrNull { it.dir.absolutePath == choice.removePrefix("dir:") }?.label ?: "وحدة تخزين خارجية"
        else -> volumes(ctx).first().label
    }

    fun pathHint(ctx: Context, choice: String): String =
        if (choice.startsWith("tree:")) "" else fileDir(ctx, choice).absolutePath

    fun open(ctx: Context, choice: String, fileName: String): RecTarget {
        val unavailable = "مجلد الحفظ غير متاح. أعد توصيل وحدة التخزين أو غيّر المجلد."
        if (choice.startsWith("tree:")) {
            val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(choice.removePrefix("tree:")))
            if (tree == null || !tree.canWrite()) throw IOException(unavailable)
            val doc = tree.createFile("video/mp2t", fileName) ?: throw IOException("تعذر إنشاء الملف في هذا المجلد.")
            val out = ctx.contentResolver.openOutputStream(doc.uri, "w")
                ?: throw IOException("تعذر فتح الملف للكتابة.")
            return RecTarget(out, doc.uri.toString(), { false }, { doc.length() }, { doc.delete() }, doc.name ?: fileName)
        }
        val dir = fileDir(ctx, choice)
        dir.mkdirs()
        if (!dir.isDirectory || !dir.canWrite()) throw IOException(unavailable)
        val file = File(dir, fileName)
        return RecTarget(
            FileOutputStream(file), file.absolutePath,
            { dir.usableSpace < MIN_FREE }, { file.length() }, { file.delete() }, file.name
        )
    }

    fun list(ctx: Context, choice: String): List<RecItem> {
        if (choice.startsWith("tree:")) {
            val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(choice.removePrefix("tree:"))) ?: return emptyList()
            return tree.listFiles()
                .filter { it.isFile && it.length() > 0 && (it.name ?: "").endsWith(".ts", ignoreCase = true) }
                .map { d ->
                    RecItem(d.name ?: "recording", d.length(), d.lastModified(), d.uri.toString(), d.uri.toString()) { d.delete() }
                }
                .sortedByDescending { it.modified }
        }
        val dir = fileDir(ctx, choice)
        val files = dir.listFiles { f -> f.isFile && f.length() > 0 && f.name.endsWith(".ts", ignoreCase = true) }
            ?: emptyArray<File>()
        return files
            .sortedByDescending { it.lastModified() }
            .map { f -> RecItem(f.name, f.length(), f.lastModified(), Uri.fromFile(f).toString(), f.absolutePath) { f.delete() } }
    }
}
