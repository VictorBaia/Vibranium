package com.music.bitchord.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** Keeps fatal exceptions across process restarts and exports available Android exit traces. */
internal object CrashDiagnostics {
    private const val MAX_SAVED_CRASH_BYTES = 512 * 1024L
    private const val MAX_EXIT_TRACE_BYTES = 512 * 1024
    private val handlingCrash = AtomicBoolean(false)
    private const val CRASH_FILE = "crash-diagnostics.txt"

    fun install(context: Context) {
        val application = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (handlingCrash.compareAndSet(false, true)) {
                runCatching {
                    val file = File(application.filesDir, CRASH_FILE)
                    if (file.length() > MAX_SAVED_CRASH_BYTES) file.delete()
                    FileOutputStream(file, true).bufferedWriter().use { writer ->
                        writer.appendLine("=== ${timestamp(System.currentTimeMillis())} ===")
                        writer.appendLine("Thread: ${thread.name}")
                        writer.appendLine(Log.getStackTraceString(error))
                        writer.appendLine()
                    }
                }
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    suspend fun export(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        val report = buildReport(context)
        checkNotNull(context.contentResolver.openOutputStream(destination)) { "Cannot open export destination" }
            .bufferedWriter()
            .use { it.write(report) }
    }

    private fun buildReport(context: Context): String = buildString {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Vibranium diagnostics — ${timestamp(System.currentTimeMillis())}")
        appendLine("App: ${packageInfo.versionName} (${if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()

        appendLine("=== Saved fatal exceptions ===")
        appendLine(File(context.filesDir, CRASH_FILE).takeIf { it.isFile }?.readText().orEmpty().ifBlank { "None saved." })

        if (Build.VERSION.SDK_INT >= 30) {
            appendLine("=== Recent process exits ===")
            val manager = context.getSystemService(ActivityManager::class.java)
            val exits = runCatching {
                manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            }.getOrDefault(emptyList())
            if (exits.isEmpty()) appendLine("No process exit records available.")
            exits.forEach { exit ->
                appendLine("${timestamp(exit.timestamp)} | reason=${exit.reason} | status=${exit.status} | ${exit.description.orEmpty()}")
                if (exit.reason == android.app.ApplicationExitInfo.REASON_ANR ||
                    exit.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                    exit.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
                ) {
                    runCatching { exit.traceInputStream?.use { readBounded(it, MAX_EXIT_TRACE_BYTES) } }
                        .getOrNull()?.takeIf(String::isNotBlank)?.let { appendLine(it) }
                }
            }
        }

        appendLine("=== Current threads ===")
        Thread.getAllStackTraces().entries.take(40).forEach { (thread, frames) ->
            appendLine("Thread: ${thread.name} (${thread.state})")
            frames.take(80).forEach { appendLine("  at $it") }
        }
    }

    private fun readBounded(stream: java.io.InputStream, limit: Int): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun timestamp(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(value))
}
