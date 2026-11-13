package net.trilleo.hexium.client.debug

/**
 * How old a lane key's SDL timestamp is when the judge gets the key. Judged times come from the stamp unless
 * it's older than the judge's current time, which [lateKeys] counts, so age alone doesn't shift them. It can't
 * see the keyboard's own delay. It also hints at whether stamps are real: at a low frame rate they should
 * spread from about 0 up to a frame. Always near 0 means they're only taken when the frame reads its events,
 * so timing is really down to the frame. Doesn't allocate.
 */
class InputDelay {
	private var totalMs = 0.0
	private var count = 0L

	var minMs = 0.0
		private set
	var maxMs = 0.0
		private set

	/** Keys older than the judge's current time. */
	var lateKeys = 0

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
