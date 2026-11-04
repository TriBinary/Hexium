package net.trilleo.hexium.core.clock

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongClockTest {
	private val frameNs = 4_000_000L

	private fun ms(ns: Long) = ns / 1_000_000.0

	@Test
	fun runsOnWallTimeBetweenAudioUpdates() {
		val clock = SongClock()
		clock.start(1000.0, 5_000_000_000L)
		assertEquals(1000.0, clock.nowMs(5_000_000_000L), 1e-6)
		assertEquals(1250.0, clock.nowMs(5_250_000_000L), 1e-6)
	}

	@Test
	fun doesNotMoveWhilePaused() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.pause(1_000_000_000L)
		assertEquals(1000.0, clock.nowMs(2_000_000_000L), 1e-6)
		clock.observe(500.0, 3_000_000_000L)
		assertEquals(1000.0, clock.nowMs(3_000_000_000L), 1e-6)
		assertEquals(false, clock.running)
	}

	@Test
	fun stepsInTheAudioPositionDontShowInTheClock() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		var wall = 0L
		var last = 0.0
		var biggestStep = 0.0
		repeat(60_000 / 4) {
			wall += frameNs
			// The raw position only moves every 20 ms.
			clock.observe(Math.floor(ms(wall) / 20.0) * 20.0, wall)
			val now = clock.nowMs(wall)
			biggestStep = maxOf(biggestStep, now - last)
			last = now
		}
		assertTrue(biggestStep < 4.5, "biggest step $biggestStep ms for a 4 ms frame")
		assertTrue(abs(last - ms(wall)) < 25.0, "clock $last at ${ms(wall)}")
	}

	/** A sound card whose clock is 0.1% fast must not pull the notes away from the music. */
	@Test
	fun followsAudioThatRunsAtADifferentRateOverThreeMinutes() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		var wall = 0L
		repeat(180_000 / 4) {
			wall += frameNs
			clock.observe(ms(wall) * 1.001, wall)
		}
		assertEquals(0, clock.resyncCount)
		assertEquals(ms(wall) * 1.001, clock.nowMs(wall), 2.0)
	}

	@Test
	fun theResultDoesNotDependOnTheFrameRate() {
		fun errorAt(frame: Long): Double {
			val clock = SongClock()
			clock.start(0.0, 0L)
			var wall = 0L
			// One pull time in, while the error is still shrinking, so a pull per frame would show.
			while (wall < 1_000_000_000L) {
				wall += frame
				clock.observe(ms(wall) + 30.0, wall)
			}
			return ms(wall) + 30.0 - clock.nowMs(wall)
		}
		assertEquals(errorAt(4_000_000L), errorAt(16_000_000L), 0.5)
		assertEquals(11.0, errorAt(4_000_000L), 1.0)
	}

	@Test
	fun jumpsForwardWhenAudioIsFarAhead() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.observe(1500.0, 1_000_000_000L)
		assertEquals(1500.0, clock.nowMs(1_000_000_000L), 1e-6)
		assertEquals(1, clock.resyncCount)
	}

	@Test
	fun neverRunsBackwardsEvenWhenAudioFallsFarBehind() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		assertEquals(1000.0, clock.nowMs(1_000_000_000L), 1e-6)
		clock.observe(500.0, 1_000_000_000L)
		assertEquals(1, clock.resyncCount)
		assertEquals(1000.0, clock.nowMs(1_000_000_000L), 1e-6)
		// Holds still until the music catches up, then moves on.
		assertEquals(1000.0, clock.nowMs(1_200_000_000L), 1e-6)
		assertEquals(1100.0, clock.nowMs(1_600_000_000L), 1e-6)
	}

	@Test
	fun neverRunsBackwardsWithNoisyAudio() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		val random = java.util.Random(42)
		var wall = 0L
		var last = Double.NEGATIVE_INFINITY
		repeat(50_000) {
			wall += 1_000_000L + random.nextInt(8_000_000)
			clock.observe(ms(wall) + (random.nextDouble() - 0.5) * 80.0, wall)
			// Not nowMs: its floor would hide a pull that reversed time.
			val now = clock.mapMs(wall)
			assertTrue(now >= last, "went from $last to $now")
			last = now
		}
	}

	/** Input stamped before this frame's time keeps its real time. The judge decides what to do with it. */
	@Test
	fun mapsOlderTimestampsWithoutHoldingThemToNow() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		assertEquals(1000.0, clock.nowMs(1_000_000_000L), 1e-6)
		assertEquals(990.0, clock.mapMs(990_000_000L), 1e-6)
	}

	@Test
	fun aSeekMayGoBackwards() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		assertEquals(5000.0, clock.nowMs(5_000_000_000L), 1e-6)
		clock.start(2000.0, 5_000_000_000L)
		assertEquals(2000.0, clock.nowMs(5_000_000_000L), 1e-6)
		assertEquals(0, clock.resyncCount)
	}

	@Test
	fun reportsDrift() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.observe(1010.0, 1_000_000_000L)
		assertEquals(10.0, clock.driftMs, 1e-6)
		clock.observe(1990.0, 2_000_000_000L)
		assertTrue(clock.driftMs < 0.0)
		assertTrue(clock.maxDriftMs >= 10.0)
	}

	@Test
	fun ignoresAudioPositionsThatArentNumbers() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.observe(Double.NaN, 1_000_000_000L)
		assertEquals(1000.0, clock.nowMs(1_000_000_000L), 1e-6)
		assertEquals(0, clock.resyncCount)
	}

	@Test
	fun resumesFromWherePauseLeftIt() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.pause(1_000_000_000L)
		clock.start(clock.nowMs(5_000_000_000L), 5_000_000_000L)
		assertEquals(1500.0, clock.nowMs(5_500_000_000L), 1e-6)
		assertEquals(0, clock.resyncCount)
	}

	@Test
	fun seekingWhilePausedStaysPausedAtTheNewTime() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.pause(5_000_000_000L)
		clock.seek(2000.0, 6_000_000_000L)
		assertEquals(false, clock.running)
		assertEquals(2000.0, clock.nowMs(7_000_000_000L), 1e-6)
		clock.start(clock.nowMs(8_000_000_000L), 8_000_000_000L)
		assertEquals(2500.0, clock.nowMs(8_500_000_000L), 1e-6)
	}

	@Test
	fun seekingWhileRunningKeepsRunningAndMayGoBackwards() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		assertEquals(5000.0, clock.nowMs(5_000_000_000L), 1e-6)
		clock.seek(2000.0, 5_000_000_000L)
		assertEquals(true, clock.running)
		assertEquals(2500.0, clock.nowMs(5_500_000_000L), 1e-6)
		assertEquals(0, clock.resyncCount)
	}

	@Test
	fun aLongGapPullsNoFurtherThanTheWholeError() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.observe(5050.0, 5_000_000_000L)
		assertEquals(5050.0, clock.mapMs(5_000_000_000L), 1e-6)
	}

	@Test
	fun aValidPositionWorksAfterOneThatIsNot() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.observe(Double.NaN, 500_000_000L)
		clock.observe(1010.0, 1_000_000_000L)
		assertEquals(10.0, clock.driftMs, 1e-6)
		assertEquals(1010.0, clock.mapMs(1_000_000_000L), 1e-6)
	}

	@Test
	fun startClearsDriftButKeepsCountingResyncs() {
		val clock = SongClock()
		clock.start(0.0, 0L)
		clock.observe(2000.0, 1_000_000_000L)
		assertEquals(1, clock.resyncCount)
		clock.start(0.0, 1_000_000_000L)
		assertEquals(0.0, clock.driftMs)
		assertEquals(0.0, clock.maxDriftMs)
		assertEquals(1, clock.resyncCount)
	}
}
