package net.trilleo.hexium.client.play

import net.trilleo.hexium.client.input.Aim
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.core.chart.SkyTrack
import kotlin.math.exp

/**
 * The comfort settings: the play's own mouse sensitivity and FOV, and view assist. They only change how
 * the camera feels, so they live in the input layer, never in judging. Outside a play everything is
 * vanilla's own. Doesn't allocate.
 */
object Comfort {
	/** View assist only eases back when no sky note is due for this long. */
	private const val QUIET_MS = 1500.0

	/** How fast each step of view assist eases back, as the share of the way left covered per second. */
	private const val ASSIST_RATE_PER_STEP = 0.4

	/** Rest view: the middle of the sky screen. */
	private const val REST_AIM = 0.5

	/** A mouse turn, scaled by the play's own sensitivity on top of vanilla's. */
	@JvmStatic
	fun scaleTurn(delta: Double): Double {
		if (HexiumSession.play() == null) return delta
		return delta * PlaySettings.mouseSensitivity / 100.0
	}

	/** The FOV, which during a play is the play's own. */
	@JvmStatic
	fun fov(vanilla: Float): Float {
		if (HexiumSession.play() == null) return vanilla
		return PlaySettings.playFov.toFloat()
	}

	/**
	 * Eases the camera toward the rest view, by as much as [elapsedMs] of song time allows, if view assist
	 * is on and no sky note can still be judged or is due within [QUIET_MS] of [songMs].
	 */
	fun assist(play: PlaySession, songMs: Double, elapsedMs: Double) {
		val strength = PlaySettings.viewAssist
		if (strength == 0 || elapsedMs <= 0.0 || !isQuiet(play, songMs)) return
		val share = 1.0 - exp(-strength * ASSIST_RATE_PER_STEP * elapsedMs / 1000.0)
		Aim.easeToward(REST_AIM, REST_AIM, share)
	}

	/**
	 * True if no sky note is due within [QUIET_MS] after [songMs], and none before it can still be
	 * judged: the judge runs offset B behind the song, and a note stays open for its late window.
	 */
	fun isQuiet(play: PlaySession, songMs: Double): Boolean {
		val config = play.judgeChart.config
		val lateWindow = maxOf(config.attackMs, config.easyAttackMs, config.flickJusticeMs)
		val sky: SkyTrack = play.sky
		val next = sky.firstEndingFrom(songMs - play.judgementOffsetMs - lateWindow)
		return next == sky.size || sky.startMs(next) > songMs + QUIET_MS
	}
}
