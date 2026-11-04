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
}
