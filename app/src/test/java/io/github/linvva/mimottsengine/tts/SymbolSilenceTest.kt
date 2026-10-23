package io.github.linvva.mimottsengine.tts

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SymbolSilenceTest {
    @Test fun onlySymbolsUseSilenceButWordsAndAllUnicodeNumbersRemainReadable() {
        listOf("……", " “……” ", "...", "——", "！？\n，。", "🙂✨", "\u200B…\uFEFF").forEach {
            assertTrue(it, isSymbolOnlyText(it))
        }
        listOf("", " \n\t", "他说……", "嗯……", "…hello", "……123", "……𠀀", "……Ⅷ", "……½", "……〇").forEach {
            assertFalse(it, isSymbolOnlyText(it))
        }
    }

    @Test fun wavContainsRequestedSilenceWithOneSampleForZeroPause() {
        listOf(0, 100, 300, 2000, 10000).forEach { durationMs ->
            val wav = symbolSilenceWav(durationMs)
            val input = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            fun tag(): String = ByteArray(4).also { input.get(it) }.toString(Charsets.US_ASCII)
            val size = (durationMs * 24_000 / 1000).coerceAtLeast(1) * 2
            assertEquals(44 + size, wav.size)
            assertEquals("RIFF", tag())
            assertEquals(wav.size - 8, input.int)
            assertEquals("WAVE", tag())
            assertEquals("fmt ", tag())
            assertEquals(16, input.int)
            assertEquals(1, input.short.toInt())
            assertEquals(1, input.short.toInt())
            assertEquals(24_000, input.int)
            assertEquals(48_000, input.int)
            assertEquals(2, input.short.toInt())
            assertEquals(16, input.short.toInt())
            assertEquals("data", tag())
            assertEquals(size, input.int)
            assertTrue(wav.drop(44).all { it == 0.toByte() })
            assertArrayEquals(symbolSilencePcm(durationMs), wav.copyOfRange(44, wav.size))
        }
    }
}
