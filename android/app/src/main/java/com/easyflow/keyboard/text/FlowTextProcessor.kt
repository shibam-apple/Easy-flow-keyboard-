package com.easyflow.keyboard.text

import java.util.Locale

data class ProcessedText(
    val raw: String,
    val text: String,
    val confidence: Float,
    val changes: List<String>,
    val requiresReview: Boolean
)

class FlowTextProcessor(private val dictionary: PersonalDictionary = PersonalDictionary.EMPTY) {
    private val fillers = Regex("(?i)(^|[\\s,])(um+|uh+|erm+|hmm+)(?=[\\s,.!?]|$)")
    private val backtrack = Regex("(?i)\\b(.{1,80}?)(?:[, ]+)(?:actually|sorry|I mean|rather)(?:[, ]+)(.{1,80}?)(?=\\.|,|$)")
    private val spaces = Regex("\\s+")

    fun process(rawInput: String, asrConfidence: Float = .7f): ProcessedText {
        val changes = mutableListOf<String>()
        var text = rawInput.trim()
        val withoutFillers = text.replace(fillers, " ").replace(spaces, " ").trim()
        if (withoutFillers != text) { text = withoutFillers; changes += "Removed filler words" }

        val match = backtrack.find(text)
        if (match != null) {
            val before = match.groupValues[1].trim(); val replacement = match.groupValues[2].trim()
            val sharedPrefix = before.substringBeforeLast(' ', "")
            text = text.replaceRange(match.range, listOf(sharedPrefix, replacement).filter { it.isNotBlank() }.joinToString(" "))
            changes += "Applied spoken correction"
        }

        val dictionaryResult = dictionary.apply(text)
        if (dictionaryResult != text) { text = dictionaryResult; changes += "Applied personal dictionary" }
        text = formatSpokenStructure(text).also { if (it != text) changes += "Formatted structure" }
        text = punctuate(text)
        val risk = semanticRisk(rawInput, text)
        val confidence = (asrConfidence - risk + if (changes.isEmpty()) .08f else 0f).coerceIn(0f, 1f)
        return ProcessedText(rawInput, text, confidence, changes.distinct(), confidence < .66f || risk > .18f)
    }

    private fun formatSpokenStructure(input: String): String {
        var text = input.replace(Regex("(?i)\\bnew paragraph\\b"), "\n\n")
        text = text.replace(Regex("(?i)\\bnew line\\b"), "\n")
        val items = text.split(Regex("(?i)\\b(?:first|second|third|next|finally)[, ]+"))
        if (items.size > 2) text = items.filter { it.isNotBlank() }.mapIndexed { i, item -> "${i + 1}. ${item.trim()}" }.joinToString("\n")
        return text
    }

    private fun punctuate(input: String): String {
        var text = input.trim().replace(Regex("\\s+([,.!?])"), "$1")
        if (text.isBlank()) return text
        text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        if (text.last() !in ".!?") text += "."
        return text
    }

    private fun semanticRisk(raw: String, cleaned: String): Float {
        val rawNumbers = Regex("\\b\\d+(?:[.:]\\d+)?\\b").findAll(raw).map { it.value }.toSet()
        val cleanNumbers = Regex("\\b\\d+(?:[.:]\\d+)?\\b").findAll(cleaned).map { it.value }.toSet()
        val numberRisk = if (rawNumbers == cleanNumbers) 0f else .35f
        val lengthRisk = if (raw.isNotBlank() && cleaned.length < raw.length * .55f) .2f else 0f
        return numberRisk + lengthRisk
    }
}

class PersonalDictionary(private val entries: Map<String, String>) {
    fun apply(input: String): String = entries.entries.fold(input) { text, (spoken, written) ->
        text.replace(Regex("(?i)\\b${Regex.escape(spoken)}\\b"), written)
    }
    companion object { val EMPTY = PersonalDictionary(emptyMap()) }
}
