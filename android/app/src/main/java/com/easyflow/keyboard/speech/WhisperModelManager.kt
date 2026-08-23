package com.easyflow.keyboard.speech

import android.content.Context
import com.easyflow.keyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

class WhisperModelManager(context: Context) {
    val modelFile = File(context.filesDir, "models/ggml-base.en-q5_1.bin")
    val isInstalled: Boolean get() = modelFile.isFile && modelFile.length() > 50_000_000

    suspend fun download(onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            modelFile.parentFile?.mkdirs()
            val part = File(modelFile.parentFile, modelFile.name + ".part")
            val connection = (URL(BuildConfig.WHISPER_MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
            }
            val messageDigest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { networkInput ->
                DigestInputStream(networkInput.buffered(), messageDigest).use { input ->
                    part.outputStream().buffered().use { output ->
                    val total = connection.contentLengthLong.coerceAtLeast(1); val buffer = ByteArray(128 * 1024); var done = 0L
                    while (true) { val read = input.read(buffer); if (read < 0) break; output.write(buffer, 0, read); done += read; onProgress((done * 100 / total).toInt().coerceIn(0, 100)) }
                    }
                }
            }
            connection.disconnect()
            val digest = messageDigest.digest().joinToString("") { "%02x".format(it) }
            check(digest == BuildConfig.WHISPER_MODEL_SHA256) {
                part.delete()
                "Model download was incomplete. Please retry on a stable connection."
            }
            check(part.renameTo(modelFile)) { "Could not activate downloaded model" }
            modelFile
        }
    }
}
