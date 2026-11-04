package net.trilleo.hexium.client.play

import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageGeometry
import net.trilleo.hexium.core.chart.GroundTrack

/**
 * Draws the ground track, in world space, from the audio clock. Positions are worked out fresh from the
 * time each frame, so nothing here can drift, and the geometry callbacks are made once and reused.
 *
 * With classic transparency this render type doesn't write depth and sorts its quads by their centres,
 * so long floor quads would paint over notes behind their middle. Each layer is submitted in its own
 * order, drawn in turn. Improved transparency takes another path, which the game test screenshots.
 */
object TrackRenderer {
	private const val FLOOR_ORDER = 1
	private const val BODY_ORDER = 2
	private const val NOTE_ORDER = 3

	private const val FLOOR_LIFT = 0.01
	private const val LINE_LIFT = 0.02
	private const val BODY_LIFT = 0.04
	private const val DIVIDER_WIDTH = 0.06
	private const val LINE_DEPTH = 0.3
	private const val NOTE_WIDTH = 2.6
	private const val NOTE_DEPTH = 0.8
	private const val NOTE_HEIGHT = 0.4
	private const val BODY_WIDTH = 2.0
	private const val GLOW_LENGTH = 16.0

	/** The last stretch of the track, where notes fade in instead of popping up. */
	private const val FADE_LENGTH = 10.0

	/** A note stays this long after its Attack window, so it's judged before it goes. */
	private const val KEEP_AFTER_WINDOW_MS = 100.0

	private const val LANE_LIGHT = 0x1CFFFFFF
	private const val LANE_DARK = 0x0CFFFFFF
	private const val DIVIDER = 0x70FFFFFF
	private const val LINE = 0xFFFFD040.toInt()
	private const val GLOW = 0x60FFD040
	private const val BODY = 0x5C30E0C0
	private const val BODY_HELD = 0xB040FFD8.toInt()
	private const val TAP_TOP = 0xFFF2F6FF.toInt()
	private const val TAP_FRONT = 0xFFB4BED8.toInt()
	private const val TAP_SIDE = 0xFF8C96B0.toInt()
	private const val HEAD_TOP = 0xFF40FFD8.toInt()
	private const val HEAD_FRONT = 0xFF20B898.toInt()
	private const val HEAD_SIDE = 0xFF18907A.toInt()

	private var cameraX = 0.0
	private var cameraY = 0.0
	private var cameraZ = 0.0
	private var floorY = 0.0
	private var songMs = 0.0
	private var drawMs = 0.0
	private var blocksPerSecond = 0.0

	/** For the game test: how many frames were drawn, and how many taps and hold heads the last one had. */
	var framesDrawn = 0L
		private set
	var notesDrawn = 0
		private set

	private val floorLayer = SubmitNodeCollector.CustomGeometryRenderer { _, buffer -> HexiumSession.play()?.let { drawFloor(it, buffer) } }
	private val bodyLayer = SubmitNodeCollector.CustomGeometryRenderer { _, buffer -> HexiumSession.play()?.let { drawBodies(it, buffer) } }
	private val noteLayer = SubmitNodeCollector.CustomGeometryRenderer { _, buffer -> HexiumSession.play()?.let { drawNotes(it, buffer) } }

	fun register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(::collect)
	}

	private fun collect(context: LevelRenderContext) {
		if (!HexiumSession.active()) return
		val play = HexiumSession.play() ?: return
		val level = Minecraft.getInstance().level ?: return
		val camera = context.levelState().cameraRenderState.pos
		cameraX = camera.x
		cameraY = camera.y
		cameraZ = camera.z
		floorY = (level.minY + StageGeometry.FLOOR_THICKNESS).toDouble()
		songMs = play.clock.songTimeMs
		drawMs = GroundTrack.drawTimeMs(songMs, PlaySettings.visualOffsetMs.toDouble())
		blocksPerSecond = PlaySettings.noteSpeed.toDouble()
		val collector = context.submitNodeCollector()
		val pose = context.poseStack()
		val type = RenderTypes.debugQuads()
		collector.order(FLOOR_ORDER).submitCustomGeometry(pose, type, floorLayer)
		if (!play.notesMoving) return
		collector.order(BODY_ORDER).submitCustomGeometry(pose, type, bodyLayer)
		collector.order(NOTE_ORDER).submitCustomGeometry(pose, type, noteLayer)
	}

	private fun drawFloor(play: PlaySession, buffer: VertexConsumer) {
		framesDrawn++
		val laneCount = play.chart.laneCount
		val near = -StageGeometry.TRACK_BEFORE_LINE
		val far = StageGeometry.TRACK_LENGTH
		for (lane in 0 until laneCount) {
			val centre = StageGeometry.laneCentreX(lane, laneCount)
			val half = StageGeometry.LANE_WIDTH / 2.0
			val color = if (lane % 2 == 0) LANE_LIGHT else LANE_DARK
			flat(buffer, centre, half, near, far, FLOOR_LIFT, color, color and 0xFFFFFF)
			if (play.isLaneDown(lane)) flat(buffer, centre, half, 0.0, GLOW_LENGTH, FLOOR_LIFT, GLOW, GLOW and 0xFFFFFF)
			flat(buffer, centre + half, DIVIDER_WIDTH / 2.0, near, far, FLOOR_LIFT, DIVIDER, DIVIDER and 0xFFFFFF)
		}
		flat(buffer, StageGeometry.laneCentreX(laneCount - 1, laneCount) - StageGeometry.LANE_WIDTH / 2.0, DIVIDER_WIDTH / 2.0, near, far, FLOOR_LIFT, DIVIDER, DIVIDER and 0xFFFFFF)
		flat(buffer, StageGeometry.STAND_X, laneCount * StageGeometry.LANE_WIDTH / 2.0, -LINE_DEPTH / 2.0, LINE_DEPTH / 2.0, LINE_LIFT, LINE, LINE)
	}

	private fun drawBodies(play: PlaySession, buffer: VertexConsumer) {
		val track = play.track
		val laneCount = play.chart.laneCount
		var i = track.firstEndingFrom(drawMs)
		while (i < track.size) {
			val start = GroundTrack.distance(track.startMs(i), drawMs, blocksPerSecond)
			if (start > StageGeometry.TRACK_LENGTH) break
			if (track.isHold(i)) {
				// A body runs down to the line and stops there, shortening as it's played.
				val near = maxOf(start, 0.0)
				val far = minOf(GroundTrack.distance(track.endMs(i), drawMs, blocksPerSecond), StageGeometry.TRACK_LENGTH)
				if (far > near) {
					val lane = track.lane(i)
					val color = if (start <= 0.0 && play.isLaneDown(lane)) BODY_HELD else BODY
					val x = StageGeometry.laneCentreX(lane, laneCount)
					// Split where the fade starts, or it would spread down the whole of a long body.
					val fadeStart = StageGeometry.TRACK_LENGTH - FADE_LENGTH
					if (near < fadeStart && far > fadeStart) {
						flat(buffer, x, BODY_WIDTH / 2.0, near, fadeStart, BODY_LIFT, color, color)
						flat(buffer, x, BODY_WIDTH / 2.0, fadeStart, far, BODY_LIFT, color, faded(color, far))
					} else {
						flat(buffer, x, BODY_WIDTH / 2.0, near, far, BODY_LIFT, faded(color, near), faded(color, far))
					}
				}
			}
			i++
		}
	}

	private fun drawNotes(play: PlaySession, buffer: VertexConsumer) {
		val track = play.track
		val laneCount = play.chart.laneCount
		// The judge works in song time and notes are drawn in draw time, so keep a note for whichever is behind.
		val config = play.judgeChart.config
		val oldest = minOf(drawMs, songMs) - maxOf(config.attackMs, config.easyAttackMs) - KEEP_AFTER_WINDOW_MS
		var drawn = 0
		var i = track.firstEndingFrom(oldest)
		while (i < track.size) {
			val distance = GroundTrack.distance(track.startMs(i), drawMs, blocksPerSecond)
			if (distance > StageGeometry.TRACK_LENGTH) break
			// Only a hold can leave its start behind and still be here.
			if (track.startMs(i) >= oldest && distance >= -StageGeometry.TRACK_BEFORE_LINE && !play.isHeadJudged(track.chartNote(i))) {
				val x = StageGeometry.laneCentreX(track.lane(i), laneCount)
				if (track.isHold(i)) block(buffer, x, distance, HEAD_TOP, HEAD_FRONT, HEAD_SIDE) else block(buffer, x, distance, TAP_TOP, TAP_FRONT, TAP_SIDE)
				drawn++
			}
			i++
		}
		notesDrawn = drawn
	}

	/** A note's alpha at [distance] up the track. */
	private fun faded(color: Int, distance: Double): Int {
		val left = (StageGeometry.TRACK_LENGTH - distance) / FADE_LENGTH
		if (left >= 1.0) return color
		val alpha = ((color ushr 24) * left.coerceAtLeast(0.0)).toInt()
		return (alpha shl 24) or (color and 0xFFFFFF)
	}

	/** A flat quad along the track, from [near] to [far] up it, with each end's colour. */
	private fun flat(buffer: VertexConsumer, x: Double, halfWidth: Double, near: Double, far: Double, lift: Double, nearColor: Int, farColor: Int) {
		val left = (x + halfWidth - cameraX).toFloat()
		val right = (x - halfWidth - cameraX).toFloat()
		val y = (floorY + lift - cameraY).toFloat()
		val nearZ = (StageGeometry.JUDGEMENT_Z + near - cameraZ).toFloat()
		val farZ = (StageGeometry.JUDGEMENT_Z + far - cameraZ).toFloat()
		buffer.addVertex(left, y, nearZ).setColor(nearColor)
		buffer.addVertex(right, y, nearZ).setColor(nearColor)
		buffer.addVertex(right, y, farZ).setColor(farColor)
		buffer.addVertex(left, y, farZ).setColor(farColor)
	}

	/** A note centred on [distance]. Only the faces the camera can see, since nothing here writes depth. */
	private fun block(buffer: VertexConsumer, x: Double, distance: Double, top: Int, front: Int, side: Int) {
		val left = (x + NOTE_WIDTH / 2.0 - cameraX).toFloat()
		val right = (x - NOTE_WIDTH / 2.0 - cameraX).toFloat()
		val low = (floorY + BODY_LIFT - cameraY).toFloat()
		val high = (floorY + BODY_LIFT + NOTE_HEIGHT - cameraY).toFloat()
		val nearZ = (StageGeometry.JUDGEMENT_Z + distance - NOTE_DEPTH / 2.0 - cameraZ).toFloat()
		val farZ = (StageGeometry.JUDGEMENT_Z + distance + NOTE_DEPTH / 2.0 - cameraZ).toFloat()
		val topColor = faded(top, distance)
		val frontColor = faded(front, distance)
		val sideColor = faded(side, distance)
		if (left < 0f) quad(buffer, left, low, nearZ, left, high, nearZ, left, high, farZ, left, low, farZ, sideColor)
		if (right > 0f) quad(buffer, right, low, nearZ, right, high, nearZ, right, high, farZ, right, low, farZ, sideColor)
		if (nearZ > 0f) quad(buffer, left, low, nearZ, right, low, nearZ, right, high, nearZ, left, high, nearZ, frontColor)
		quad(buffer, left, high, nearZ, right, high, nearZ, right, high, farZ, left, high, farZ, topColor)
	}

	private fun quad(
		buffer: VertexConsumer,
		x0: Float, y0: Float, z0: Float,
		x1: Float, y1: Float, z1: Float,
		x2: Float, y2: Float, z2: Float,
		x3: Float, y3: Float, z3: Float,
		color: Int,
	) {
		buffer.addVertex(x0, y0, z0).setColor(color)
		buffer.addVertex(x1, y1, z1).setColor(color)
		buffer.addVertex(x2, y2, z2).setColor(color)
		buffer.addVertex(x3, y3, z3).setColor(color)
	}
}
