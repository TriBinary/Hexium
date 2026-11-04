package net.trilleo.hexium.client.debug

/**
 * How old a lane key's SDL timestamp is when the judge gets the key. Judged times come from the stamp, so
 * this doesn't shift them, and it can't see the keyboard's own delay. What it does show is whether stamps
 * are real: at a low frame rate they should spread from 0 up to a frame. Always near 0 means they're only
 * taken when the frame reads its events, so timing is really down to the frame. Doesn't allocate.
 */
class InputDelay {
	private var totalMs = 0.0
	private var count = 0L

	var minMs = 0.0
		private set
	var maxMs = 0.0
		private set

	val averageMs: Double
		get() = if (count == 0L) 0.0 else totalMs / count

	fun record(delayNs: Long) {
		val ms = delayNs / NANOS_PER_MS
		if (count == 0L || ms < minMs) minMs = ms
		if (count == 0L || ms > maxMs) maxMs = ms
		totalMs += ms
		count++
	}

	private companion object {
		const val NANOS_PER_MS = 1_000_000.0
	}
}
