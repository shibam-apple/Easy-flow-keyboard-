package com.easyflow.keyboard.text

import android.content.Context

class TranscriptRefinementPipeline(context: Context) {
    private val deterministic = FlowTextProcessor()
    private val gemma = GemmaTextRefiner(context)

    val hasLocalLlm: Boolean get() = gemma.isAvailable
    suspend fun prewarm() = gemma.prewarm()

    suspend fun refine(raw: String, confidence: Float, context: WritingContext): ProcessedText {
        val baseline = deterministic.process(raw, confidence, context)
        if (!gemma.isAvailable) return baseline

        val candidate = runCatching { gemma.refine(raw, context) }.getOrNull()
            ?.trim()?.removeSurrounding("\"")
            ?: return baseline
        if (!isSafe(raw, candidate)) return baseline.copy(
            changes = baseline.changes + "Gemma fallback",
            requiresReview = true,
        )

        val polished = deterministic.process(candidate, confidence, context)
        return polished.copy(
            raw = raw,
            changes = (listOf("Gemma local cleanup") + polished.changes).distinct(),
        )
    }

    private fun isSafe(raw: String, candidate: String): Boolean {
        if (candidate.isBlank() || candidate.length !in (raw.length / 2).coerceAtLeast(1)..(raw.length * 2 + 80)) return false
        val numbers = Regex("\\b\\d+(?:[.:]\\d+)?\\b")
        if (numbers.findAll(raw).map { it.value }.toList() != numbers.findAll(candidate).map { it.value }.toList()) return false
        val forbidden = listOf("raw transcript:", "corrected transcript:", "here is", "as an ai")
        return forbidden.none { candidate.contains(it, ignoreCase = true) }
    }
}
