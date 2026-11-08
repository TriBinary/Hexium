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
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SkyTrack

/**
 * Draws the sky screen and its notes, in world space, from the audio clock, as [TrackRenderer] does the
 * ground. A note flies toward the screen down the depth axis at the note speed and is judged where it
 * meets it. Every size is the judge's own hit area from [SkyTrack], so what's drawn is what counts.
 * Notes face the player as flat octagons just around their hit circle: sky taps orange (L) or blue
 * (R), flicks magenta. A slide is a trail of beads down its path, with a ring where it crosses the
 * screen now, brighter while its button is down. A tap or flick coming up also shows a faint ring on
 * the screen where it will land.
 *
 * Like the ground, nothing here writes depth, so each layer is submitted in its own order, drawn in turn
 * after the ground's: notes, then the pane in front of them, then marks on the screen. Geometry
 * callbacks are made once and reused, so nothing allocates per frame.
 */
object SkyRenderer {
	/** Notes are behind the pane, so they go first and show through it. Marks on the screen go last. */
	private const val NOTE_ORDER = 4
	private const val PANE_ORDER = 5
	private const val MARK_ORDER = 6

	private const val PANE = 0x12FFFFFF
	private const val FRAME = 0x50FFFFFF
	private val FRAME_WIDTH = 0.08 * StageGeometry.SCALE

	private const val TAP_L = 0xE8FF8C1A.toInt()
	private const val TAP_R = 0xE83C8CFF.toInt()
	private const val FLICK = 0xE8E040E0.toInt()
	private const val SLIDE_HEAD_HELD = 0xFFFFFFFF.toInt()

	/** A slide's beads are this much of its hit radius, so the path reads as a line. */
	private const val BEAD_SCALE = 0.35

	/** One bead every this many blocks down the path. */
	private val BEAD_SPACING = 0.75 * StageGeometry.SCALE

	/** How far away a coming note starts to show its landing ring. */
	private const val LANDING_RING_DISTANCE = 24.0
	private val RING_WIDTH = 0.12 * StageGeometry.SCALE

	/** Marks sit this far in front of the screen, so they draw over what's on it. */
	private const val MARK_LIFT = 0.02

	/** The last stretch, where notes fade in instead of popping up. */
	private const val FADE_LENGTH = 10.0

	/** A note stays this long after its last window, so it's judged before it goes. */
	private const val KEEP_AFTER_WINDOW_MS = 100.0

	/**
	 * Corners of an octagon with flat sides facing each axis, counter-clockwise, far enough out that its
	 * sides touch a circle of radius 1, so a note drawn this way covers all of its hit circle.
	 */
	private val OCTAGON_REACH = 1.0 / Math.cos(Math.toRadians(22.5))
	private val OCTAGON_COS = DoubleArray(8) { OCTAGON_REACH * Math.cos(Math.toRadians(22.5 + 45.0 * it)) }
	private val OCTAGON_SIN = DoubleArray(8) { OCTAGON_REACH * Math.sin(Math.toRadians(22.5 + 45.0 * it)) }

	private var cameraX = 0.0
	private var cameraY = 0.0
	private var cameraZ = 0.0
	private var floorY = 0.0
	private var songMs = 0.0
	private var drawMs = 0.0
	private var blocksPerSecond = 0.0

	/** For the game test: how many sky notes, beads included, the last frame drew. */
	var notesDrawn = 0
		private set

	private val paneLayer = SubmitNodeCollector.CustomGeometryRenderer { _, buffer -> if (HexiumSession.play() != null) drawPane(buffer) }
	private val noteLayer = SubmitNodeCollector.CustomGeometryRenderer { _, buffer -> HexiumSession.play()?.let { drawNotes(it, buffer) } }
	private val markLayer = SubmitNodeCollector.CustomGeometryRenderer { _, buffer -> HexiumSession.play()?.let { drawMarks(it, buffer) } }

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
		collector.order(PANE_ORDER).submitCustomGeometry(pose, type, paneLayer)
		if (!play.notesMoving || play.sky.size == 0) {
			notesDrawn = 0
			return
		}
		collector.order(NOTE_ORDER).submitCustomGeometry(pose, type, noteLayer)
		collector.order(MARK_ORDER).submitCustomGeometry(pose, type, markLayer)
	}

	private fun drawPane(buffer: VertexConsumer) {
		val left = StageGeometry.STAND_X + StageGeometry.SCREEN_WIDTH / 2.0
		val right = StageGeometry.STAND_X - StageGeometry.SCREEN_WIDTH / 2.0
		val bottom = floorY
		val top = floorY + StageGeometry.SCREEN_HEIGHT
		val z = StageGeometry.JUDGEMENT_Z
		rect(buffer, left, bottom, right, top, z, PANE)
		rect(buffer, left, top - FRAME_WIDTH, right, top, z, FRAME)
		rect(buffer, left, bottom, left - FRAME_WIDTH, top, z, FRAME)
		rect(buffer, right + FRAME_WIDTH, bottom, right, top, z, FRAME)
	}

	/** Song time, minus offset B, before which no slide is drawn: during the rewind after a pause, what was already played, as with hold bodies. */
	private fun hiddenBeforeMs(play: PlaySession): Double =
		if (play.state == PlaySession.State.REWINDING) play.playedUntilMs - play.judgementOffsetMs else Double.NEGATIVE_INFINITY

	/** The oldest note time still drawn. The judge works in song time minus B and notes are drawn in draw time, so whichever is behind. */
	private fun oldestMs(play: PlaySession): Double {
		val config = play.judgeChart.config
		val window = maxOf(config.attackMs, config.easyAttackMs, config.flickJusticeMs)
		return minOf(drawMs, songMs - play.judgementOffsetMs) - window - KEEP_AFTER_WINDOW_MS
	}

	private fun drawNotes(play: PlaySession, buffer: VertexConsumer) {
		val sky = play.sky
		val oldest = oldestMs(play)
		val hidden = hiddenBeforeMs(play)
		var drawn = 0
		var i = sky.firstEndingFrom(oldest)
		while (i < sky.size) {
			val start = sky.startMs(i)
			if (GroundTrack.distance(start, drawMs, blocksPerSecond) > StageGeometry.TRACK_LENGTH) break
			if (sky.kind(i) == SkyTrack.Kind.SLIDE) {
				drawn += drawBeads(play, buffer, i, hidden)
			} else if (start >= oldest && !play.isHeadJudged(sky.chartNote(i))) {
				// A late note waits on the screen rather than flying past it.
				val distance = maxOf(GroundTrack.distance(start, drawMs, blocksPerSecond), 0.0)
				val x = worldX(sky.x(i, start))
				val y = worldY(sky.y(i, start))
				val z = StageGeometry.JUDGEMENT_Z + distance
				val radius = worldRadius(sky.radius(i))
				octagon(buffer, x, y, z, radius, faded(noteColor(sky, i), distance))
				drawn++
			}
			i++
		}
		notesDrawn = drawn
	}

	/** The part of a slide still to come, as beads down its path. Returns how many it drew. */
	private fun drawBeads(play: PlaySession, buffer: VertexConsumer, i: Int, hidden: Double): Int {
		val sky = play.sky
		val from = maxOf(sky.startMs(i), drawMs, hidden)
		val until = minOf(sky.endMs(i), drawMs + StageGeometry.TRACK_LENGTH * 1000.0 / blocksPerSecond)
		if (until < from) return 0
		val stepMs = BEAD_SPACING * 1000.0 / blocksPerSecond
		val radius = worldRadius(sky.radius(i)) * BEAD_SCALE
		val color = noteColor(sky, i)
		var drawn = 0
		// Beads sit on a fixed grid in time, so they don't crawl as the slide moves.
		var t = sky.startMs(i) + Math.ceil((from - sky.startMs(i)) / stepMs) * stepMs
		while (t <= until) {
			val distance = GroundTrack.distance(t, drawMs, blocksPerSecond)
			octagon(buffer, worldX(sky.x(i, t)), worldY(sky.y(i, t)), StageGeometry.JUDGEMENT_Z + distance, radius, faded(color, distance))
			drawn++
			t += stepMs
		}
		return drawn
	}

	private fun drawMarks(play: PlaySession, buffer: VertexConsumer) {
		val sky = play.sky
		val oldest = oldestMs(play)
		val hidden = hiddenBeforeMs(play)
		val markZ = StageGeometry.JUDGEMENT_Z - MARK_LIFT
		var i = sky.firstEndingFrom(oldest)
		while (i < sky.size) {
			val start = sky.startMs(i)
			val distance = GroundTrack.distance(start, drawMs, blocksPerSecond)
			if (distance > StageGeometry.TRACK_LENGTH) break
			val radius = worldRadius(sky.radius(i))
			when (sky.kind(i)) {
				SkyTrack.Kind.SLIDE -> {
					// Where the slide crosses the screen now, from just before it starts until it ends.
					val now = drawMs.coerceIn(start, sky.endMs(i))
					if (distance <= LANDING_RING_DISTANCE && drawMs <= sky.endMs(i) && now >= hidden) {
						val held = play.isButtonDown(sky.button(i)!!) && drawMs >= start
						val color = if (held) SLIDE_HEAD_HELD else ringColor(noteColor(sky, i), distance)
						ring(buffer, worldX(sky.x(i, now)), worldY(sky.y(i, now)), markZ, radius, color)
					}
				}
				else -> if (start >= oldest && distance > 0.0 && distance <= LANDING_RING_DISTANCE && !play.isHeadJudged(sky.chartNote(i))) {
					ring(buffer, worldX(sky.x(i, start)), worldY(sky.y(i, start)), markZ, radius, ringColor(noteColor(sky, i), distance))
				}
			}
			i++
		}
	}

	private fun noteColor(sky: SkyTrack, i: Int): Int = when (sky.button(i)) {
		SkyButton.L -> TAP_L
		SkyButton.R -> TAP_R
		null -> FLICK
	}

	/** A landing ring, fainter the further away its note still is. */
	private fun ringColor(color: Int, distance: Double): Int {
		val near = 1.0 - (distance / LANDING_RING_DISTANCE).coerceIn(0.0, 1.0)
		val alpha = (0x30 + (0xC0 - 0x30) * near).toInt()
		return (alpha shl 24) or (color and 0xFFFFFF)
	}

	/** Screen x, 0 on the left (+X, the player's left facing down the lanes), to world x. */
	private fun worldX(x: Double): Double = StageGeometry.STAND_X + (0.5 - x) * StageGeometry.SCREEN_WIDTH

	private fun worldY(y: Double): Double = floorY + y * StageGeometry.SCREEN_HEIGHT

	/** A hit radius, in screen heights and round on the screen, in blocks. */
	private fun worldRadius(radius: Double): Double = radius * StageGeometry.SCREEN_HEIGHT

	private fun faded(color: Int, distance: Double): Int {
		val left = (StageGeometry.TRACK_LENGTH - distance) / FADE_LENGTH
		if (left >= 1.0) return color
		val alpha = ((color ushr 24) * left.coerceAtLeast(0.0)).toInt()
		return (alpha shl 24) or (color and 0xFFFFFF)
	}

	private fun octagon(buffer: VertexConsumer, x: Double, y: Double, z: Double, radius: Double, color: Int) {
		// Three quads: the middle band and a cap either side.
		corners(buffer, x, y, z, radius, 0, 1, 2, 3, color)
		corners(buffer, x, y, z, radius, 0, 3, 4, 7, color)
		corners(buffer, x, y, z, radius, 7, 4, 5, 6, color)
	}

	private fun corners(buffer: VertexConsumer, x: Double, y: Double, z: Double, radius: Double, a: Int, b: Int, c: Int, d: Int, color: Int) {
		val fz = (z - cameraZ).toFloat()
		vertex(buffer, x + radius * OCTAGON_COS[a], y + radius * OCTAGON_SIN[a], fz, color)
		vertex(buffer, x + radius * OCTAGON_COS[b], y + radius * OCTAGON_SIN[b], fz, color)
		vertex(buffer, x + radius * OCTAGON_COS[c], y + radius * OCTAGON_SIN[c], fz, color)
		vertex(buffer, x + radius * OCTAGON_COS[d], y + radius * OCTAGON_SIN[d], fz, color)
	}

	/** An octagonal outline whose outside edge is [radius]. */
	private fun ring(buffer: VertexConsumer, x: Double, y: Double, z: Double, radius: Double, color: Int) {
		// A tiny note keeps a ring half its size rather than one turned inside out.
		val inner = maxOf(radius - RING_WIDTH / OCTAGON_REACH, radius * 0.5)
		val fz = (z - cameraZ).toFloat()
		for (k in 0 until 8) {
			val n = (k + 1) % 8
			vertex(buffer, x + radius * OCTAGON_COS[k], y + radius * OCTAGON_SIN[k], fz, color)
			vertex(buffer, x + radius * OCTAGON_COS[n], y + radius * OCTAGON_SIN[n], fz, color)
			vertex(buffer, x + inner * OCTAGON_COS[n], y + inner * OCTAGON_SIN[n], fz, color)
			vertex(buffer, x + inner * OCTAGON_COS[k], y + inner * OCTAGON_SIN[k], fz, color)
		}
	}

	/** An upright rectangle across the screen plane at [z], between two corners. */
	private fun rect(buffer: VertexConsumer, x0: Double, y0: Double, x1: Double, y1: Double, z: Double, color: Int) {
		val fz = (z - cameraZ).toFloat()
		vertex(buffer, x0, y0, fz, color)
		vertex(buffer, x1, y0, fz, color)
		vertex(buffer, x1, y1, fz, color)
		vertex(buffer, x0, y1, fz, color)
	}

	private fun vertex(buffer: VertexConsumer, x: Double, y: Double, cameraRelativeZ: Float, color: Int) {
		buffer.addVertex((x - cameraX).toFloat(), (y - cameraY).toFloat(), cameraRelativeZ).setColor(color)
	}
}
