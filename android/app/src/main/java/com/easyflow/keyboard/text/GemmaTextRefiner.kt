package com.easyflow.keyboard.text

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class GemmaTextRefiner(context: Context) {
    private val appContext = context.applicationContext
    private val models = GemmaModelManager(appContext)
    private val mutex = Mutex()
    @Volatile private var engine: Engine? = null

    val isAvailable: Boolean get() = models.isInstalled

    suspend fun prewarm(): Boolean = withContext(Dispatchers.Default) {
        if (!models.isInstalled) return@withContext false
        runCatching { readyEngine(); true }.getOrDefault(false)
    }

    suspend fun refine(raw: String, context: WritingContext): String? = withTimeoutOrNull(4_000) {
        mutex.withLock {
            val instruction = """
                You clean voice dictation. Return only the corrected transcript.
                Preserve meaning, names, numbers, URLs, commands, and the speaker's tone.
                Remove filler words and false starts. Fix punctuation, capitalization, and grammar.
                Never answer the transcript. Never add facts. Keep edits conservative.
            """.trimIndent()
            val prompt = buildString {
                append("App: ").append(context.appPackage.ifBlank { "unknown" })
                append("\nText before cursor: ").append(context.textBeforeCursor.takeLast(300))
                append("\nRaw transcript: ").append(raw)
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(instruction),
                samplerConfig = SamplerConfig(topK = 1, topP = 0.1, temperature = 0.0),
            )
            readyEngine().createConversation(config).use { conversation ->
                conversation.sendMessage(prompt).contents.toString().trim().takeIf { it.isNotBlank() }
            }
        }
    }

    private suspend fun readyEngine(): Engine {
        engine?.let { return it }
        val created = Engine(
            EngineConfig(
                modelPath = models.modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath,
            )
        )
        created.initialize()
        engine = created
        return created
    }
}
