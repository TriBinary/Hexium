package net.trilleo.hexium.core.aim

import java.lang.management.ManagementFactory
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenPlaneTest {
	private val tolerance = 1e-9

	/** A 12 by 6 screen standing on y 0, 6 blocks down +Z from the eye at (0, 3, 0). Its left edge is on +X. */
	private val plane = ScreenPlane(leftX = 6.0, rightX = -6.0, bottomY = 0.0, topY = 6.0, planeZ = 6.0)

	private fun degrees(radians: Double) = Math.toDegrees(radians)

	@Test
	fun lookingStraightAheadHitsTheMiddle() {
		assertTrue(plane.aim(0.0, 3.0, 0.0, 0.0, 0.0))
		assertEquals(0.5, plane.x, tolerance)
		assertEquals(0.5, plane.y, tolerance)
	}

	/** Minecraft's yaw turns toward -X as it grows, and the screen's right edge is on -X. */
	@Test
	fun turningRightMovesTheAimRight() {
		assertTrue(plane.aim(0.0, 3.0, 0.0, 45.0, 0.0))
		assertEquals(1.0, plane.x, tolerance)
		assertEquals(0.5, plane.y, tolerance)
		plane.aim(0.0, 3.0, 0.0, -45.0, 0.0)
		assertEquals(0.0, plane.x, tolerance)
	}

	/** Minecraft's pitch looks down as it grows. */
	@Test
	fun lookingDownMovesTheAimDown() {
		plane.aim(0.0, 3.0, 0.0, 0.0, degrees(atan(0.5)))
		assertEquals(0.0, plane.y, tolerance)
		plane.aim(0.0, 3.0, 0.0, 0.0, -degrees(atan(0.5)))
		assertEquals(1.0, plane.y, tolerance)
	}

	@Test
	fun theEyesOwnPlaceCounts() {
		plane.aim(3.0, 6.0, 3.0, 0.0, 0.0)
		assertEquals(0.25, plane.x, tolerance)
		assertEquals(1.0, plane.y, tolerance)
	}

	@Test
	fun anAimOffTheScreenStillGivesItsPlace() {
		plane.aim(0.0, 3.0, 0.0, 60.0, 0.0)
		assertTrue(plane.x > 1.0)
	}

	@Test
	fun lookingAwayFromTheScreenHitsNothing() {
		assertFalse(plane.aim(0.0, 3.0, 0.0, 180.0, 0.0))
		assertTrue(plane.x.isNaN() && plane.y.isNaN())
		assertFalse(plane.aim(0.0, 3.0, 0.0, 0.0, 90.0))
		assertFalse(plane.aim(0.0, 3.0, 0.0, 90.0, 0.0))
	}

	@Test
	fun aWholeTurnMakesNoDifference() {
		plane.aim(0.0, 3.0, 0.0, 20.0 + 720.0, 10.0)
		val x = plane.x
		val y = plane.y
		plane.aim(0.0, 3.0, 0.0, 20.0, 10.0)
		assertEquals(x, plane.x, 1e-9)
		assertEquals(y, plane.y, 1e-9)
	}

	@Test
	fun aLookInsideTheLimitIsLeftAlone() {
		assertFalse(plane.clampLook(0.0, 3.0, 0.0, 10.0, 5.0, 0.1, 0.1))
		assertEquals(10.0, plane.yaw, tolerance)
		assertEquals(5.0, plane.pitch, tolerance)
	}

	@Test
	fun aLookPastAnEdgeStopsAtTheEdgePlusTheMargin() {
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, 60.0, 0.0, 0.1, 0.2))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(1.1, plane.x, 1e-9)
		assertEquals(0.5, plane.y, 1e-9)
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, 0.0, -80.0, 0.1, 0.2))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(0.5, plane.x, 1e-9)
		assertEquals(1.2, plane.y, 1e-9)
	}

	@Test
	fun aCornerIsClampedOnBothAxes() {
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, -70.0, 70.0, 0.1, 0.2))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(-0.1, plane.x, 1e-9)
		assertEquals(-0.2, plane.y, 1e-9)
	}

	/** The camera's yaw keeps counting past a whole turn, so a clamp mustn't make it jump back by 360. */
	@Test
	fun aClampedYawStaysOnTheSameTurn() {
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, 720.0 + 60.0, 0.0, 0.1, 0.1))
		assertTrue(plane.yaw > 700.0 && plane.yaw < 780.0, "yaw ${plane.yaw}")
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, -720.0 - 60.0, 0.0, 0.1, 0.1))
		assertTrue(plane.yaw < -700.0 && plane.yaw > -780.0, "yaw ${plane.yaw}")
	}

	/** One frame's turn can go past where the look misses the screen at all. It still stops at the edge it went past. */
	@Test
	fun turningSoFarTheLookMissesStopsAtTheEdgeItWentPast() {
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, 120.0, 0.0, 0.1, 0.1))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(1.1, plane.x, 1e-9)
		assertEquals(0.5, plane.y, 1e-9)
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, -100.0, -30.0, 0.1, 0.1))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(-0.1, plane.x, 1e-9)
		assertEquals(1.1, plane.y, 1e-9)
	}

	/** Straight up or straight behind has no side to go to, so it stays level with the eye on that axis. */
	@Test
	fun lookingStraightAwayIsTurnedBackInLineWithTheEye() {
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, 180.0, 0.0, 0.1, 0.1))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(0.5, plane.x, 1e-9)
		assertEquals(0.5, plane.y, 1e-9)
		assertTrue(plane.clampLook(0.0, 3.0, 0.0, 3030.0, -90.0, 0.1, 0.1))
		plane.aim(0.0, 3.0, 0.0, plane.yaw, plane.pitch)
		assertEquals(0.5, plane.x, 1e-9)
		assertEquals(1.1, plane.y, 1e-9)
	}

	@Test
	fun lookingTowardAPointHitsIt() {
		val random = Random(7)
		repeat(200) {
			val x = random.nextDouble(-0.2, 1.2)
			val y = random.nextDouble(-0.2, 1.2)
			plane.lookToward(1.0, 4.0, -1.0, x, y)
			assertTrue(plane.aim(1.0, 4.0, -1.0, plane.yaw, plane.pitch))
			assertEquals(x, plane.x, 1e-9)
			assertEquals(y, plane.y, 1e-9)
		}
	}

	/** The bottom right corner is 6 blocks right, 3 down and 6 ahead: half right and a little down, in Minecraft's angles. */
	@Test
	fun lookingTowardAPointGivesMinecraftsAngles() {
		plane.lookToward(0.0, 3.0, 0.0, 1.0, 0.0)
		assertEquals(45.0, plane.yaw, 1e-9)
		assertEquals(degrees(atan(3.0 / sqrt(72.0))), plane.pitch, 1e-9)
		// Minecraft's own view vector for those angles points at the corner.
		val yaw = Math.toRadians(plane.yaw)
		val pitch = Math.toRadians(plane.pitch)
		val length = sqrt(81.0)
		assertEquals(-6.0 / length, -Math.sin(yaw) * Math.cos(pitch), 1e-9)
		assertEquals(-3.0 / length, -Math.sin(pitch), 1e-9)
		assertEquals(6.0 / length, Math.cos(yaw) * Math.cos(pitch), 1e-9)
	}

	@Test
	fun aimingAndClampingDoNotAllocate() {
		val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		val thread = Thread.currentThread().threadId()
		var sum = 0.0
		val before = threads.getThreadAllocatedBytes(thread)
		for (i in 0 until 100_000) {
			val yaw = (i % 200) - 100.0
			val pitch = (i % 120) - 60.0
			if (plane.aim(0.0, 3.0, 0.0, yaw, pitch)) sum += plane.x + plane.y
			plane.clampLook(0.0, 3.0, 0.0, yaw, pitch, 0.1, 0.1)
			sum += plane.yaw + plane.pitch
		}
		val allocated = threads.getThreadAllocatedBytes(thread) - before
		assertTrue(sum.isFinite())
		assertTrue(allocated < 16 * 1024, "allocated $allocated bytes")
	}
}
