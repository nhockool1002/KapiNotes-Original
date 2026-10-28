package com.kapi.notes.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [MediaRecorder]/[MediaPlayer] for the "Ghi âm đính kèm" feature - records a
 * lecture to an AAC file under `filesDir/audio` while the user takes notes, and plays one clip
 * back on demand. Playback is manual seek only: syncing playback position to the stroke that was
 * being written at that timestamp is out of scope for this pass.
 */
@Singleton
class AudioRecorderManager @Inject constructor(@ApplicationContext private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentOutputFile: File? = null
    private var recordingStartMs: Long = 0L

    fun startRecording(): File {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setOutputFile(file.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        currentOutputFile = file
        recordingStartMs = System.currentTimeMillis()
        return file
    }

    /** Stops the in-progress recording and returns the finished file plus its duration in ms. */
    fun stopRecording(): Pair<File, Long>? {
        val mr = recorder ?: return null
        return try {
            mr.stop()
            mr.release()
            recorder = null
            val file = currentOutputFile ?: return null
            val durationMs = System.currentTimeMillis() - recordingStartMs
            currentOutputFile = null
            file to durationMs
        } catch (e: RuntimeException) {
            mr.release()
            recorder = null
            currentOutputFile = null
            null
        }
    }

    fun play(filePath: String, onCompletion: () -> Unit) {
        stopPlayback()
        val mp = MediaPlayer()
        mp.setDataSource(filePath)
        mp.setOnCompletionListener { onCompletion() }
        mp.prepare()
        mp.start()
        player = mp
    }

    fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }
}
