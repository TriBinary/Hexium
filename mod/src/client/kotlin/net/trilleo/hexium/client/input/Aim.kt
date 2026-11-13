package net.trilleo.hexium.client.input

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
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
			turn(player, plane.yaw.toFloat(), plane.pitch.toFloat())
		}
		if (plane.aim(player.x, player.eyeY, player.z, player.yRot.toDouble(), player.xRot.toDouble())) {
			x = plane.x.toFloat()
			y = plane.y.toFloat()
		}
	}

	/** Turns the camera to look at a place on the screen, given as the chart's screen coordinates. */
	fun lookToward(screenX: Double, screenY: Double) {
		val minecraft = Minecraft.getInstance()
		val player = minecraft.player ?: return
		val level = minecraft.level ?: return
		val plane = planeFor((level.minY + StageGeometry.FLOOR_THICKNESS).toDouble())
		plane.lookToward(player.x, player.eyeY, player.z, screenX, screenY)
		// On the camera's own whole turn, as the limit keeps it.
		turn(player, (player.yRot + Math.IEEEremainder(plane.yaw - player.yRot, 360.0)).toFloat(), plane.pitch.toFloat())
	}

	/** The previous frame's look moves by as much, so the camera doesn't swing back between ticks. */
	private fun turn(player: LocalPlayer, yaw: Float, pitch: Float) {
		player.yRotO += yaw - player.yRot
		player.xRotO += pitch - player.xRot
		player.yRot = yaw
		player.xRot = pitch
	}

	/** Turns the camera [share] of the way, 0 to 1, toward looking at a place on the screen. */
	fun easeToward(screenX: Double, screenY: Double, share: Double) {
		val minecraft = Minecraft.getInstance()
		val player = minecraft.player ?: return
		val level = minecraft.level ?: return
		val plane = planeFor((level.minY + StageGeometry.FLOOR_THICKNESS).toDouble())
		plane.lookToward(player.x, player.eyeY, player.z, screenX, screenY)
		val yaw = player.yRot + Math.IEEEremainder(plane.yaw - player.yRot, 360.0) * share
		val pitch = player.xRot + (plane.pitch - player.xRot) * share
		turn(player, yaw.toFloat(), pitch.toFloat())
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
