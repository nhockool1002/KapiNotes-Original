package com.kapi.notes.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LOG_BYTES = 256 * 1024
private const val KEEP_LINES_ON_TRIM = 1500

/**
 * Plain-text, append-only log of what the Drive sync flow actually did step by step - the sync
 * itself already has a 45s timeout so the UI never hangs, but when it DOES time out or fail,
 * there was previously no way to tell which step got stuck (listing the remote file? downloading?
 * the upload?) or what the underlying exception was, short of attaching a debugger. This writes
 * every step plus any exception's class/message to a small on-device file the user can share from
 * Settings ("Chia sẻ log đồng bộ") straight into a bug report or support chat.
 */
@Singleton
class SyncLogger @Inject constructor(@ApplicationContext context: Context) {

    private val logFile: File = File(context.filesDir, "logs/sync_log.txt").apply {
        parentFile?.mkdirs()
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(message: String) {
        runCatching {
            logFile.appendText("[${dateFormat.format(Date())}] $message\n")
            if (logFile.length() > MAX_LOG_BYTES) trim()
        }
    }

    fun logError(step: String, error: Throwable) {
        log("LỖI tại bước \"$step\": ${error::class.java.simpleName} - ${error.message}")
    }

    fun file(): File = logFile

    private fun trim() {
        val kept = logFile.readLines().takeLast(KEEP_LINES_ON_TRIM)
        logFile.writeText(kept.joinToString("\n", postfix = "\n"))
    }
}
