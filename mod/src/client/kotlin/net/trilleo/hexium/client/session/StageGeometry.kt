package net.trilleo.hexium.client.session

/** Where things stand on the stage. Starting values from docs/DESIGN.md, all tunable. */
object StageGeometry {
	const val FLOOR_THICKNESS = 1

	/** The middle of a block, so the lanes can sit evenly either side of the player. */
	const val STAND_X = 0.5
	const val STAND_Z = 0.5

	/** Facing +Z, down the lanes. */
	const val REST_YAW = 0f

	/** Looking at the middle of the screen, 3 blocks up and 8 away, from eyes 1.62 up. */
	const val REST_PITCH = -9.8f

	/** Where the screen's bottom edge is, 8 blocks in front of the player. */
	const val JUDGEMENT_Z = STAND_Z + 8.0

	const val LANE_WIDTH = 2.0

	/** How far up the lanes the track runs from the judgement line. Notes appear at the far end. */
	const val TRACK_LENGTH = 64.0

	/** How far the track carries on past the line, so a late note still has floor under it. */
	const val TRACK_BEFORE_LINE = 4.0

	/** Facing +Z, the player's left is +X, so the first lane is on that side. */
	fun laneCentreX(lane: Int, laneCount: Int): Double = STAND_X + ((laneCount - 1) / 2.0 - lane) * LANE_WIDTH
}
