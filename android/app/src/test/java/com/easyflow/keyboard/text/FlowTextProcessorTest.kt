package com.easyflow.keyboard.text

import org.junit.Assert.*
import org.junit.Test

class FlowTextProcessorTest {
    private val processor = FlowTextProcessor()

    @Test fun removesFillersAndPunctuates() {
        val result = processor.process("um I will uh send it tonight", .9f)
        assertEquals("I will send it tonight.", result.text)
        assertTrue("Removed filler words" in result.changes)
    }

    @Test fun preservesNumbersAndFlagsRisk() {
        val result = processor.process("meet at 2 actually 3", .72f)
        assertTrue(result.text.contains("3"))
        assertTrue("Applied spoken correction" in result.changes)
    }

    @Test fun formatsParagraphCommand() {
        assertEquals("Hello\n\nWorld.", processor.process("hello new paragraph world", .9f).text)
    }
}
