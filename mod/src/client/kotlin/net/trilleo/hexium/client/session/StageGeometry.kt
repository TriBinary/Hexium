package net.trilleo.hexium.client.session

import net.minecraft.world.entity.Avatar
import net.trilleo.hexium.core.judge.JudgementConfig

/** Where things stand on the stage. Starting values from docs/DESIGN.md, all tunable. */
object StageGeometry {
	const val FLOOR_THICKNESS = 1

	/** The middle of a block, so the lanes can sit evenly either side of the player. */
	const val STAND_X = 0.5
	const val STAND_Z = 0.5

	/** Facing +Z, down the lanes. */
	const val REST_YAW = 0f

	/** How far above the floor the player stands, on a platform, so the lanes don't flatten toward the horizon. */
	const val STAND_HEIGHT = 3

	/** How far the platform reaches each side of the block the player is on. */
	const val PLATFORM_REACH = 1

	/** Where the player's eyes are above the floor: on the platform, at a standing player's eye height, 4.62 blocks. */
	val EYE_HEIGHT = STAND_HEIGHT + Avatar.DEFAULT_EYE_HEIGHT.toDouble()

	/**
	 * The sky screen stands on the floor at the judgement line, twice as tall as the eyes are high, so the
	 * player's eyes are level with its middle: 9.24 blocks.
	 */
	val SCREEN_HEIGHT = 2.0 * EYE_HEIGHT

	/** From the judge's own aspect, so hit circles stay round on the screen: 18.48 blocks. */
	val SCREEN_WIDTH = SCREEN_HEIGHT * JudgementConfig.DEFAULT.screenAspect

	/** Four of these make the track as wide as the sky screen, 4.62 blocks. */
	val LANE_WIDTH = SCREEN_WIDTH / 4

	/** How much bigger the stage is than the 12 by 6 screen it started with. Sizes of drawn things grow by this. */
	val SCALE = SCREEN_HEIGHT / 6.0

	/**
	 * Where the screen's bottom edge is: as far in front of the player as the screen is tall, 9.24 blocks,
	 * so its sides are 45 degrees either side of straight ahead and its top and bottom 26.6 degrees up and down.
	 */
	val JUDGEMENT_Z = STAND_Z + SCREEN_HEIGHT

	/** Looking straight at the middle of the screen, which is level with the eyes. */
	const val REST_PITCH = 0f

	/** How far past the screen's edges, in blocks, the camera can look before it stops. */
	val AIM_MARGIN = 1.0 * SCALE

	/** How far up the lanes the track runs from the judgement line. Notes appear at the far end. */
	const val TRACK_LENGTH = 64.0

	/** Where the track starts, just in front of the platform. */
	const val TRACK_START_Z = 3.0

	/** How far the track carries on past the line, so a late note still has floor under it. */
	val TRACK_BEFORE_LINE = JUDGEMENT_Z - TRACK_START_Z

	/** Facing +Z, the player's left is +X, so the first lane is on that side. */
	fun laneCentreX(lane: Int, laneCount: Int): Double = STAND_X + ((laneCount - 1) / 2.0 - lane) * LANE_WIDTH
}
