package net.trilleo.hexium.core.aim

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sky screen as a flat plane across +Z, at [planeZ], turning a look into the crosshair's place on it
 * and back. Places are the chart's normalised screen coordinates: x from 0 at [leftX] to 1 at [rightX],
 * y from 0 at [bottomY] to 1 at [topY], and either may be off the screen. Angles are Minecraft's, in
 * degrees: yaw 0 looks down +Z and turns toward -X as it grows, pitch looks down as it grows.
 *
 * This is how the mod reads aim, not a judging rule: replays hold the places, never the angles. Results
 * go in [x], [y], [yaw] and [pitch] instead of new objects, so nothing allocates. One thread only.
 */
class ScreenPlane(
	private val leftX: Double,
	private val rightX: Double,
	private val bottomY: Double,
	private val topY: Double,
	private val planeZ: Double,
) {
	/** Where the last [aim] hit, or NaN if it looked away from the screen. */
	var x = Double.NaN
		private set
	var y = Double.NaN
		private set

	/** The look from the last [clampLook] or [lookToward]. */
	var yaw = 0.0
		private set
	var pitch = 0.0
		private set

	/** The last [aim]'s look, across and up, for a look that missed. */
	private var lookX = 0.0
	private var lookY = 0.0

	/** Where a look from the eye meets the plane, into [x] and [y]. False if it looks along or away from it. */
	fun aim(eyeX: Double, eyeY: Double, eyeZ: Double, yaw: Double, pitch: Double): Boolean {
		val yawRadians = Math.toRadians(yaw)
		val pitchRadians = Math.toRadians(pitch)
		val along = cos(pitchRadians)
		val dirX = -sin(yawRadians) * along
		val dirY = -sin(pitchRadians)
		val dirZ = cos(yawRadians) * along
		lookX = dirX
		lookY = dirY
		val distance = planeZ - eyeZ
		if (dirZ < MIN_FORWARD || distance <= 0.0) {
			x = Double.NaN
			y = Double.NaN
			return false
		}
		val t = distance / dirZ
		x = (leftX - (eyeX + dirX * t)) / (leftX - rightX)
		y = (eyeY + dirY * t - bottomY) / (topY - bottomY)
		return true
	}

	/** The look from the eye toward a place on the plane, into [yaw] and [pitch]. Yaw is in -180 to 180. */
	fun lookToward(eyeX: Double, eyeY: Double, eyeZ: Double, screenX: Double, screenY: Double) {
		val dx = leftX + (rightX - leftX) * screenX - eyeX
		val dy = bottomY + (topY - bottomY) * screenY - eyeY
		val dz = planeZ - eyeZ
		yaw = Math.toDegrees(atan2(-dx, dz))
		pitch = Math.toDegrees(atan2(-dy, sqrt(dx * dx + dz * dz)))
	}

	/**
	 * Keeps a look on the screen, plus [marginX] and [marginY] past its edges in screen units, into [yaw]
	 * and [pitch]. A look past an edge stops at it. One turned so far it misses the plane, which one frame's
	 * turn can do, stops at the edges it went past, and stays level with the eye on an axis it didn't turn on.
	 * The yaw stays on the same whole turn as [yaw] given, since the camera's keeps counting. Returns true
	 * if the look had to change.
	 */
	fun clampLook(eyeX: Double, eyeY: Double, eyeZ: Double, yaw: Double, pitch: Double, marginX: Double, marginY: Double): Boolean {
		val hits = aim(eyeX, eyeY, eyeZ, yaw, pitch)
		val placeX = if (hits) x else pastEdge(lookX * (rightX - leftX), (leftX - eyeX) / (leftX - rightX), marginX)
		val placeY = if (hits) y else pastEdge(lookY * (topY - bottomY), (eyeY - bottomY) / (topY - bottomY), marginY)
		val clampedX = placeX.coerceIn(-marginX, 1.0 + marginX)
		val clampedY = placeY.coerceIn(-marginY, 1.0 + marginY)
		if (hits && clampedX == x && clampedY == y) {
			this.yaw = yaw
			this.pitch = pitch
			return false
		}
		lookToward(eyeX, eyeY, eyeZ, clampedX, clampedY)
		this.yaw = yaw + wrapDegrees(this.yaw - yaw)
		return true
	}

	/**
	 * For a look that misses the plane, on one axis: [towardOne] is how fast the look heads toward that
	 * axis's 1 edge. It stops past the edge it heads for, or at the eye's own place, [eye], if it hardly
	 * heads either way.
	 */
	private fun pastEdge(towardOne: Double, eye: Double, margin: Double): Double = when {
		towardOne > MIN_FORWARD -> 1.0 + margin
		towardOne < -MIN_FORWARD -> -margin
		else -> eye
	}

	private fun wrapDegrees(degrees: Double): Double {
		var d = degrees % 360.0
		if (d >= 180.0) d -= 360.0
		if (d < -180.0) d += 360.0
		return d
	}

	private companion object {
		/** A look this close to along the plane is treated as missing it, so its place can't overflow. */
		const val MIN_FORWARD = 1e-6
	}
}
