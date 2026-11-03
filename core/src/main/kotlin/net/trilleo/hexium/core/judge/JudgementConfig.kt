package net.trilleo.hexium.core.judge

/** The windows, weights and sizes the judge and score use (docs/JUDGEMENT.md). Nothing else may hold these numbers. */
data class JudgementConfig(
	val justiceCriticalMs: Double = 33.33,
	val justiceMs: Double = 66.67,
	val attackMs: Double = 83.33,
	/** The Attack window for BASIC and ADVANCED. */
	val easyAttackMs: Double = 100.0,
	/** A press this early, but outside the Attack window, uses up the note as a Miss. */
	val earlyMissMs: Double = 150.0,

	val tickJusticeCriticalMs: Double = 50.0,
	val tickJusticeMs: Double = 100.0,
	val tickAttackMs: Double = 150.0,
	val ticksPerBeat: Int = 4,

	val flickJusticeCriticalMs: Double = 83.33,
	val flickJusticeMs: Double = 166.67,
	/** In screen heights per second. */
	val flickMinSpeed: Double = 1.0,
	val flickDirectionToleranceDegrees: Double = 45.0,

	/** A note's hit radius at `size` 1, as a fraction of the screen's height. */
	val baseHitRadius: Double = 0.08,
	/** The screen's width divided by its height. */
	val screenAspect: Double = 2.0,

	val perfectScoreBase: Long = 1_000_000,
	val justiceCriticalWeightPercent: Int = 101,
	val justiceWeightPercent: Int = 100,
	val attackWeightPercent: Int = 50,
	val missWeightPercent: Int = 0,
) {
	companion object {
		val DEFAULT = JudgementConfig()

		/** Raise this whenever a change could alter a replay's result (docs/REPLAY_FORMAT.md). */
		const val JUDGE_VERSION: Int = 1
	}
}
