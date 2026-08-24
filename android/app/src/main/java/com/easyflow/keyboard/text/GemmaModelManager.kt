package com.easyflow.keyboard.text

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class GemmaModelManager(context: Context) {
    companion object {
        const val MODEL_NAME = "Gemma 3 1B · int4"
        const val MODEL_PAGE = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        private const val PREFS = "gemma_refiner"
        private const val HASH = "model_sha256"
        private const val MIN_BYTES = 480L * 1024L * 1024L
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val modelFile = File(appContext.filesDir, "models/gemma3-1b-it.litertlm")

    val isInstalled: Boolean
        get() = modelFile.isFile && modelFile.length() >= MIN_BYTES && preferences.getString(HASH, null) != null

    val installedSizeMb: Long get() = modelFile.length() / (1024L * 1024L)
    val fingerprint: String get() = preferences.getString(HASH, "").orEmpty().take(10)

    suspend fun import(uri: Uri, onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            modelFile.parentFile?.mkdirs()
            val pending = File(modelFile.parentFile, "${modelFile.name}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val expected = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected model could not be opened" }
                FileOutputStream(pending).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        if (expected > 0) onProgress(((copied * 100) / expected).toInt().coerceIn(0, 99))
                    }
                    output.fd.sync()
                }
            }
            require(copied >= MIN_BYTES) { "That file is too small to be the Gemma 3 1B int4 model" }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (modelFile.exists()) modelFile.delete()
            require(pending.renameTo(modelFile)) { "Could not finish installing the model" }
            preferences.edit().putString(HASH, sha).apply()
            onProgress(100)
        }
    }
}
