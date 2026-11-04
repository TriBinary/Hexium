package net.trilleo.hexium.client.play

/** The player's view settings. Changed from the pause screen, and forgotten on quitting for now. */
object PlaySettings {
	const val DEFAULT_NOTE_SPEED = 25
	private const val MIN_NOTE_SPEED = 10
	private const val MAX_NOTE_SPEED = 120
	private const val NOTE_SPEED_STEP = 5
	private const val MAX_VISUAL_OFFSET_MS = 500
	private const val VISUAL_OFFSET_STEP_MS = 5

	/** Blocks per second. It changes how far away notes appear, never when they arrive. */
	var noteSpeed = DEFAULT_NOTE_SPEED
		private set

	/** Offset A in docs/JUDGEMENT.md. Positive draws notes later. Judging doesn't see it. */
	var visualOffsetMs = 0
		private set

	fun changeNoteSpeed(steps: Int) {
		noteSpeed = (noteSpeed + steps * NOTE_SPEED_STEP).coerceIn(MIN_NOTE_SPEED, MAX_NOTE_SPEED)
	}

	fun changeVisualOffset(steps: Int) {
		visualOffsetMs = (visualOffsetMs + steps * VISUAL_OFFSET_STEP_MS).coerceIn(-MAX_VISUAL_OFFSET_MS, MAX_VISUAL_OFFSET_MS)
	}
}
