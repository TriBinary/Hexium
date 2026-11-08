package net.trilleo.hexium.client.input

import net.minecraft.client.Minecraft
import net.trilleo.hexium.client.session.StageGeometry
import net.trilleo.hexium.core.aim.ScreenPlane

/**
 * Where the crosshair meets the sky screen, and the camera limit that keeps it near there. The camera is
 * the player's own rotation (real camera rotation, docs/DESIGN.md), so the look is read from the player
 * and, past the screen's edges plus a margin, set back on it. Call [update] once per frame, after vanilla
 * has turned the camera with that frame's mouse movement. Doesn't allocate.
 */
object Aim {
	private var plane: ScreenPlane? = null
	private var planeFloorY = Double.NaN

	/** The crosshair at the last [update], in the chart's screen coordinates. Mid-screen until then. */
	var x = 0.5f
		private set
	var y = 0.5f
		private set

	/** Keeps the camera within the limit, then reads where it aims. */
	fun update() {
		val minecraft = Minecraft.getInstance()
		val player = minecraft.player ?: return
		val level = minecraft.level ?: return
		val floorY = (level.minY + StageGeometry.FLOOR_THICKNESS).toDouble()
		val plane = planeFor(floorY)
		val yaw = player.yRot
		val pitch = player.xRot
		if (plane.clampLook(player.x, player.eyeY, player.z, yaw.toDouble(), pitch.toDouble(), StageGeometry.AIM_MARGIN / StageGeometry.SCREEN_WIDTH, StageGeometry.AIM_MARGIN / StageGeometry.SCREEN_HEIGHT)) {
			val newYaw = plane.yaw.toFloat()
			val newPitch = plane.pitch.toFloat()
			// The previous frame's look moves by as much, so the camera doesn't swing back between ticks.
			player.yRotO += newYaw - yaw
			player.xRotO += newPitch - pitch
			player.yRot = newYaw
			player.xRot = newPitch
		}
		if (plane.aim(player.x, player.eyeY, player.z, player.yRot.toDouble(), player.xRot.toDouble())) {
			x = plane.x.toFloat()
			y = plane.y.toFloat()
		}
	}

	/** Back to mid-screen, for a new play. */
	fun reset() {
		x = 0.5f
		y = 0.5f
	}

	private fun planeFor(floorY: Double): ScreenPlane {
		val existing = plane
		if (existing != null && floorY == planeFloorY) return existing
		val halfWidth = StageGeometry.SCREEN_WIDTH / 2.0
		val made = ScreenPlane(
			leftX = StageGeometry.STAND_X + halfWidth,
			rightX = StageGeometry.STAND_X - halfWidth,
			bottomY = floorY,
			topY = floorY + StageGeometry.SCREEN_HEIGHT,
			planeZ = StageGeometry.JUDGEMENT_Z,
		)
		plane = made
		planeFloorY = floorY
		return made
	}
}
