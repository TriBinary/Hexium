package net.trilleo.hexium.core.replay

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** [replay] is null when [error] says why the file was rejected. */
class ReplayReadResult internal constructor(val replay: Replay?, val error: String?)

/** Reads and writes replay files (docs/REPLAY_FORMAT.md). Reading never throws. */
object ReplayFile {
	private val MAGIC = byteArrayOf('H'.code.toByte(), 'X'.code.toByte(), 'R'.code.toByte(), 'P'.code.toByte())
	private const val FORMAT_VERSION = 1
	private const val HASH_BYTES = 32
	private const val HEADER_BYTES = 4 + 4 + 4 + HASH_BYTES + 8 + 4
	private const val PLAIN_EVENT_BYTES = 1 + 8
	private const val AIM_EVENT_BYTES = PLAIN_EVENT_BYTES + 4 + 4
	private const val MAX_UNPACKED_BYTES = HEADER_BYTES + Replay.MAX_EVENTS * AIM_EVENT_BYTES

	private fun hasPosition(event: Int) = event == ReplayEvent.AIM.ordinal || event == ReplayEvent.BUTTON_DOWN.ordinal

	private fun isButton(event: Int) = event == ReplayEvent.BUTTON_DOWN.ordinal || event == ReplayEvent.BUTTON_UP.ordinal

	fun write(replay: Replay): ByteArray {
		var size = HEADER_BYTES
		for (i in 0 until replay.eventCount) {
			size += if (hasPosition(replay.event(i).ordinal)) AIM_EVENT_BYTES else PLAIN_EVENT_BYTES
		}
		val buffer = ByteBuffer.allocate(size)
		buffer.put(MAGIC)
		buffer.putInt(FORMAT_VERSION)
		buffer.putInt(replay.judgeVersion)
		buffer.put(replay.chartHash)
		buffer.putDouble(replay.judgementOffsetMs)
		buffer.putInt(replay.eventCount)
		for (i in 0 until replay.eventCount) {
			val event = replay.event(i).ordinal
			buffer.put(((event shl 4) or replay.index(i)).toByte())
			buffer.putDouble(replay.timeMs(i))
			if (hasPosition(event)) {
				buffer.putFloat(replay.x(i))
				buffer.putFloat(replay.y(i))
			}
		}
		val out = ByteArrayOutputStream()
		GZIPOutputStream(out).use { it.write(buffer.array()) }
		return out.toByteArray()
	}

	fun read(file: ByteArray): ReplayReadResult {
		val bytes = try {
			GZIPInputStream(ByteArrayInputStream(file)).use { it.readNBytes(MAX_UNPACKED_BYTES + 1) }
		} catch (e: IOException) {
			return rejected("is not a compressed replay file: ${e.message}")
		}
		if (bytes.size > MAX_UNPACKED_BYTES) return rejected("is too large: it unpacks past $MAX_UNPACKED_BYTES bytes")
		return try {
			parse(ByteBuffer.wrap(bytes))
		} catch (e: BufferUnderflowException) {
			rejected("is cut short")
		}
	}

	private fun parse(buffer: ByteBuffer): ReplayReadResult {
		val magic = ByteArray(MAGIC.size)
		buffer.get(magic)
		if (!magic.contentEquals(MAGIC)) return rejected("is not a Hexium replay")
		val formatVersion = buffer.getInt()
		if (formatVersion != FORMAT_VERSION) return rejected("uses format version $formatVersion, this Hexium reads $FORMAT_VERSION")
		val judgeVersion = buffer.getInt()
		val hash = ByteArray(HASH_BYTES)
		buffer.get(hash)
		val offsetMs = buffer.getDouble()
		if (!offsetMs.isFinite()) return rejected("has a judgement offset that isn't finite")
		val count = buffer.getInt()
		if (count < 0 || count > Replay.MAX_EVENTS) return rejected("claims $count events, the limit is ${Replay.MAX_EVENTS}")
		if (count.toLong() * PLAIN_EVENT_BYTES > buffer.remaining()) return rejected("is cut short")

		val events = ByteArray(count)
		val indexes = ByteArray(count)
		val times = DoubleArray(count)
		val xs = FloatArray(count)
		val ys = FloatArray(count)
		var previous = Double.NEGATIVE_INFINITY
		for (i in 0 until count) {
			val head = buffer.get().toInt() and 0xFF
			val event = head ushr 4
			val index = head and 0x0F
			if (event >= Replay.EVENTS.size) return rejected("event $i has unknown kind $event")
			if (isButton(event) && index > 1) return rejected("event $i has unknown button $index")
			if (event == ReplayEvent.AIM.ordinal && index != 0) return rejected("event $i is an aim sample with index $index")
			val time = buffer.getDouble()
			if (!time.isFinite()) return rejected("event $i has a time that isn't finite")
			if (time < previous) return rejected("event $i goes back in time")
			previous = time
			events[i] = event.toByte()
			indexes[i] = index.toByte()
			times[i] = time
			if (hasPosition(event)) {
				xs[i] = buffer.getFloat()
				ys[i] = buffer.getFloat()
				if (!xs[i].isFinite() || !ys[i].isFinite()) return rejected("event $i has a position that isn't finite")
			}
		}
		if (buffer.hasRemaining()) return rejected("has ${buffer.remaining()} bytes after the last event")
		return ReplayReadResult(Replay(judgeVersion, hash, offsetMs, count, events, indexes, times, xs, ys), null)
	}

	private fun rejected(why: String) = ReplayReadResult(null, "This replay $why")
}
