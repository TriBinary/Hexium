package net.trilleo.hexium.core.replay

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPOutputStream

internal val HASH = ByteArray(32) { it.toByte() }

internal fun gzip(bytes: ByteArray): ByteArray {
	val out = ByteArrayOutputStream()
	GZIPOutputStream(out).use { it.write(bytes) }
	return out.toByteArray()
}

/** Builds a file's contents by hand, separately from the writer, so these tests pin the format itself. */
internal fun fileBytes(
	magic: String = "HXRP",
	formatVersion: Int = 1,
	judgeVersion: Int = 1,
	hash: ByteArray = HASH,
	offsetMs: Double = 12.5,
	eventCount: Int? = null,
	events: List<ByteArray> = emptyList(),
): ByteArray {
	val size = events.sumOf { it.size }
	val buffer = ByteBuffer.allocate(magic.length + 4 + 4 + hash.size + 8 + 4 + size)
	buffer.put(magic.toByteArray(Charsets.US_ASCII))
	buffer.putInt(formatVersion)
	buffer.putInt(judgeVersion)
	buffer.put(hash)
	buffer.putDouble(offsetMs)
	buffer.putInt(eventCount ?: events.size)
	for (event in events) buffer.put(event)
	return buffer.array()
}

internal fun event(kind: Int, index: Int, timeMs: Double): ByteArray =
	ByteBuffer.allocate(9).put(((kind shl 4) or index).toByte()).putDouble(timeMs).array()

internal fun aimEvent(kind: Int, index: Int, timeMs: Double, x: Float, y: Float): ByteArray =
	ByteBuffer.allocate(17).put(((kind shl 4) or index).toByte()).putDouble(timeMs).putFloat(x).putFloat(y).array()
