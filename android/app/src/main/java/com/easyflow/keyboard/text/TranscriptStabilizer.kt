package com.easyflow.keyboard.text

class TranscriptStabilizer {
    private var last = ""
    private var stablePrefix = ""

    fun update(candidate: String): String {
        val a = last.split(' '); val b = candidate.trim().split(' ')
        var common = 0
        while (common < a.size && common < b.size && a[common].equals(b[common], true)) common++
        if (common > 0) stablePrefix = b.take(common).joinToString(" ")
        last = candidate.trim()
        return listOf(stablePrefix, b.drop(common).joinToString(" ")).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun reset() { last = ""; stablePrefix = "" }
}
