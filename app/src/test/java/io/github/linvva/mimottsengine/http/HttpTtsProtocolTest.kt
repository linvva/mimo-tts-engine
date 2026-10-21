package io.github.linvva.mimottsengine.http

import io.github.linvva.mimottsengine.data.VoicePresets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class HttpTtsProtocolTest {
    @Test fun speedUsesLegadoScaleWithoutCompression() {
        listOf("5" to .5f, "10" to 1f, "15" to 1.5f, "20" to 2f, "85" to 8.5f).forEach { (raw, speed) ->
            assertEquals(speed, HttpTtsRequest.parse("/tts?speed=$raw").speed)
        }
        assertEquals(1f, HttpTtsRequest.parse("/tts").speed)
        listOf("", "NaN", "Infinity", "-Infinity", "4.9", "85.1", "fast").forEach { raw ->
            assertEquals(400, assertThrows(HttpTtsException::class.java) {
                HttpTtsRequest.parse("/tts?speed=$raw").speed
            }.statusCode)
        }
    }

    @Test fun requestDecodesChineseAndValidatesStream() {
        val request = HttpTtsRequest.parse("/tts?voice=%E8%8C%89%E8%8E%89&text=a%2Bb&stream=1")
        assertEquals("茉莉", request.query["voice"])
        assertEquals("a+b", request.query["text"])
        assertTrue(request.streaming)
        assertFalse(HttpTtsRequest.parse("/tts").streaming)
        assertFalse(HttpTtsRequest.parse("/tts?stream=0").streaming)
        assertThrows(HttpTtsException::class.java) { HttpTtsRequest.parse("/tts?stream=true").streaming }
        assertThrows(HttpTtsException::class.java) { HttpTtsRequest.parse("/tts?text=%xx") }
    }

    @Test fun configHasStableIdsAllVoicesAndEscapedDefault() {
        val unusualVoice = "茉莉\"\\测试"
        val release = Json.parseToJsonElement(legadoConfig(unusualVoice, false, updatedAt = 123)).jsonObject
        assertEquals("87650001", release.getValue("id").jsonPrimitive.content)
        assertEquals("5", release.getValue("speed").jsonPrimitive.content)
        assertEquals("2", release.getValue("synthesisThreadCount").jsonPrimitive.content)
        assertEquals("123", release.getValue("lastUpdateTime").jsonPrimitive.content)
        val url = release.getValue("url").jsonPrimitive.content
        assertTrue(url.contains("currentToneID || ${kotlinx.serialization.json.JsonPrimitive(unusualVoice)}"))
        assertTrue(url.endsWith("&stream=1"))
        val speakers = Json.parseToJsonElement(release.getValue("speakersJson").jsonPrimitive.content).jsonArray
        assertEquals(VoicePresets.map { it.id }, speakers.map { it.jsonObject.getValue("toneID").jsonPrimitive.content })
        assertEquals(VoicePresets.map { it.label }, speakers.map { it.jsonObject.getValue("speakerName").jsonPrimitive.content })
        val debug = Json.parseToJsonElement(legadoConfig("冰糖", true, false, 124)).jsonObject
        assertEquals("87650002", debug.getValue("id").jsonPrimitive.content)
        assertEquals("124", debug.getValue("lastUpdateTime").jsonPrimitive.content)
        assertTrue(debug.getValue("url").jsonPrimitive.content.endsWith("&stream=0"))
    }

    @Test fun adtsHeaderDescribesAacLc24kMonoAndFrameLength() {
        listOf(1, 255, 256, 8184).forEach { size ->
            val payload = ByteArray(size) { it.toByte() }
            val frame = adtsFrame(payload)
            val header = frame.map { it.toInt() and 255 }
            assertEquals(0xff, header[0])
            assertEquals(0xf1, header[1])
            assertEquals(1, header[2] shr 6) // AAC-LC profile minus one.
            assertEquals(6, (header[2] shr 2) and 15) // 24 kHz frequency index.
            assertEquals(1, ((header[2] and 1) shl 2) or (header[3] shr 6))
            assertEquals(frame.size, ((header[3] and 3) shl 11) or (header[4] shl 3) or (header[5] shr 5))
            assertEquals(0, header[6] and 3) // One raw data block per ADTS frame.
            assertArrayEquals(payload, frame.copyOfRange(7, frame.size))
        }
        assertThrows(IllegalArgumentException::class.java) { adtsFrame(ByteArray(8185)) }
    }

    @Test fun chunkedSuccessHasNoInventedLengthAndFailureHasNoEndMarker() {
        val output = ByteArrayOutputStream()
        val response = HttpTtsResponse(output)
        response.audioChunk(byteArrayOf())
        assertFalse(response.started)
        assertEquals(0, output.size())
        response.audioChunk("abc".toByteArray())
        val partial = output.toString("US-ASCII")
        assertTrue(partial.contains("Content-Type: audio/aac\r\n"))
        assertTrue(partial.contains("Transfer-Encoding: chunked\r\n"))
        assertFalse(partial.contains("Content-Length"))
        assertTrue(partial.endsWith("3\r\nabc\r\n"))
        assertFalse(partial.endsWith("0\r\n\r\n"))
        response.finishAudio()
        assertTrue(output.toString("US-ASCII").endsWith("0\r\n\r\n"))
    }
}
