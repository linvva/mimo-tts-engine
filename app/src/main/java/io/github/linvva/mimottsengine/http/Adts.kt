package io.github.linvva.mimottsengine.http

internal fun adtsFrame(payload: ByteArray): ByteArray {
    val size = payload.size + 7
    require(size <= 0x1fff) { "AAC 帧过大" }
    // MPEG-4 AAC-LC, 24000 Hz (index 6), mono, no CRC.
    return byteArrayOf(
        0xff.toByte(), 0xf1.toByte(), 0x58,
        (0x40 or (size shr 11)).toByte(),
        (size shr 3).toByte(),
        (((size and 7) shl 5) or 0x1f).toByte(),
        0xfc.toByte(),
    ) + payload
}
