package net.trilleo.hexium.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.tutorial.TutorialSteps
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.trilleo.hexium.client.input.LaneInput
import net.trilleo.hexium.client.play.PlaySettings
import net.trilleo.hexium.client.play.TrackRenderer
import net.trilleo.hexium.client.play.PlaySession
import net.trilleo.hexium.client.screen.HexiumMenuScreen
import net.trilleo.hexium.client.screen.StageMenuScreen
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageGeometry
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.TimingMap
import net.trilleo.hexium.core.judge.ClearLamp
import net.trilleo.hexium.core.judge.Judgement
import net.trilleo.hexium.core.replay.ReplayJudge
import org.lwjgl.sdl.SDLTimer

/** Runs in the real game. See docs/DEVELOPMENT.md. */
class HexiumClientGameTest : FabricClientGameTest {
	override fun runTest(context: ClientGameTestContext) {
		context.waitForScreen(TitleScreen::class.java)
		context.takeScreenshot("title")
		// Songs play on a real audio device. Keep them quiet.
		context.runOnClient<RuntimeException> { it.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0) }
		theStageLocksThePlayerAndLeavesCleanly(context)
		aChartPlaysToTheEndAndItsReplayMatches(context)
		notesDrawWithImprovedTransparency(context)
		normalWorldsAreUntouched(context)
	}

	private fun theStageLocksThePlayerAndLeavesCleanly(context: ClientGameTestContext) {
		// Things a player can bring along, which the game test framework otherwise turns off.
		context.runOnClient<RuntimeException> { minecraft ->
			minecraft.options.tutorialStep = TutorialSteps.FIND_TREE
			minecraft.options.cameraType = CameraType.THIRD_PERSON_BACK
			minecraft.gui.hud.toggle()
		}
		context.clickScreenButton("hexium.title_button")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.takeScreenshot("menu")
		context.clickScreenButton("hexium.difficulty.basic")
		context.waitFor({ it.player != null && it.level != null && it.gui.screen() == null }, LOAD_TICKS)
		// The stand spot arrives from the server after joining.
		context.waitFor({ it.player!!.x == 0.5 && it.player!!.z == 0.5 }, LOAD_TICKS)
		context.waitTicks(SETTLE_TICKS)

		context.runOnClient<RuntimeException> { minecraft ->
			check(HexiumSession.active()) { "no session on the stage" }
			check(minecraft.gameMode!!.playerMode == GameType.ADVENTURE) { "not in adventure mode" }
			val server = minecraft.singleplayerServer!!
			check(!server.gameRules.get(GameRules.ADVANCE_TIME)) { "time is running" }
			check(!server.gameRules.get(GameRules.SPAWN_MOBS)) { "mobs can spawn" }
			check(!server.gameRules.get(GameRules.ADVANCE_WEATHER)) { "weather is running" }
			check(server.gameRules.get(GameRules.RANDOM_TICK_SPEED) == 0) { "random ticks are on" }
			check(minecraft.level!!.difficulty == Difficulty.PEACEFUL) { "not on Peaceful" }
			val player = minecraft.player!!
			check(player.y == minecraft.level!!.minY + 1.0) { "not standing on the floor: y ${player.y}" }
			check(player.yRot == StageGeometry.REST_YAW && player.xRot == StageGeometry.REST_PITCH) {
				"not in the rest view: ${player.yRot}, ${player.xRot}"
			}
			check(minecraft.options.cameraType == CameraType.FIRST_PERSON) { "not in first person" }
			check(!minecraft.gui.hud.isHidden) { "the HUD is hidden" }
		}
		context.takeScreenshot("stage")

		val start = position(context)
		val input = context.input
		input.holdKey { it.keyUp }
		input.holdKey { it.keyLeft }
		input.holdKey { it.keyJump }
		input.holdKey { it.keyShift }
		// Sampled each tick, since a jump would be back on the floor by the end.
		repeat(HOLD_TICKS) {
			context.waitTick()
			check(position(context) == start) { "the player moved from $start to ${position(context)}" }
			context.runOnClient<RuntimeException> { check(!it.player!!.isShiftKeyDown) { "the player is sneaking" } }
		}
		input.releaseKey { it.keyUp }
		input.releaseKey { it.keyLeft }
		input.releaseKey { it.keyJump }
		input.releaseKey { it.keyShift }

		input.pressKey { it.keyHotbarSlots[3] }
		input.pressKey { it.keyInventory }
		input.scroll(1.0)
		context.waitTicks(SETTLE_TICKS)
		context.runOnClient<RuntimeException> { minecraft ->
			check(minecraft.player!!.inventory.selectedSlot == 0) { "a hotbar key or the wheel worked" }
			check(minecraft.gui.screen() == null) { "the inventory opened" }
		}

		// Vanilla ignores the first movement after grabbing the mouse.
		input.moveCursor(TURN_PIXELS, 0.0)
		context.waitTicks(SETTLE_TICKS)
		val yawBefore = context.computeOnClient<Float, RuntimeException> { it.player!!.yRot }
		input.moveCursor(TURN_PIXELS, 0.0)
		context.waitTicks(SETTLE_TICKS)
		check(context.computeOnClient<Float, RuntimeException> { it.player!!.yRot } != yawBefore) { "the camera didn't turn" }

		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.takeScreenshot("stage_menu")
		context.clickScreenButton("hexium.stage.leave")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.runOnClient<RuntimeException> { minecraft ->
			check(!HexiumSession.active()) { "the session outlived the stage" }
			check(minecraft.level == null) { "still in the stage" }
			check(minecraft.options.tutorialStep == TutorialSteps.FIND_TREE) { "the stage ended the player's tutorial" }
			check(minecraft.options.cameraType == CameraType.THIRD_PERSON_BACK) { "the camera view wasn't put back" }
			check(minecraft.gui.hud.isHidden) { "the HUD setting wasn't put back" }
			minecraft.options.tutorialStep = TutorialSteps.NONE
			minecraft.options.cameraType = CameraType.FIRST_PERSON
			minecraft.gui.hud.toggle()
		}
		context.clickScreenButton("gui.back")
		context.waitForScreen(TitleScreen::class.java)
	}

	private fun aChartPlaysToTheEndAndItsReplayMatches(context: ClientGameTestContext) {
		context.clickScreenButton("hexium.title_button")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("hexium.difficulty.basic")
		context.waitFor({ HexiumSession.play()?.state == PlaySession.State.PLAYING }, LOAD_TICKS)
		val play = HexiumSession.play()!!
		pauseAndCheckTimeStops(context, play)

		val script = script(play)
		var next = 0
		// Ticks are too coarse to press from, so it presses ahead, stamped with the note's own time,
		// the way SDL stamps a real key. So every note should land dead on.
		val bot = { song: Double ->
			while (next < script.size && script[next].atMs <= song + BOT_LOOKAHEAD_MS) {
				val nowNs = SDLTimer.SDL_GetTicksNS()
				val aheadMs = script[next].atMs - play.clock.songTimeAt(nowNs)
				LaneInput.push(script[next].lane, script[next].down, nowNs + (aheadMs * NANOS_PER_MS).toLong())
				next++
			}
		}
		context.waitFor({
			bot(play.clock.songTimeMs)
			play.clock.songTimeMs >= NOTES_IN_VIEW_MS
		}, SONG_TICKS)
		context.takeScreenshot("notes")
		context.runOnClient<RuntimeException> { check(TrackRenderer.notesDrawn > 0) { "no notes drawn on the way to the first one" } }
		context.waitFor({
			bot(play.clock.songTimeMs)
			play.clock.songTimeMs >= PAUSE_AT_MS
		}, SONG_TICKS)
		pauseAndCheckTimeStops(context, play)
		pauseSettingsChange(context, play)
		context.waitFor({
			bot(play.clock.songTimeMs)
			play.clock.songTimeMs >= HOLD_IN_PLAY_MS
		}, SONG_TICKS)
		context.takeScreenshot("hold")
		context.waitFor({
			bot(play.clock.songTimeMs)
			play.state == PlaySession.State.FINISHED
		}, SONG_TICKS)
		context.takeScreenshot("finished")
		context.runOnClient<RuntimeException> {
			val judge = play.judge
			check(next == script.size) { "the play ended before the script did" }
			check(judge.judgedCount == judge.objectCount) { "${judge.judgedCount} of ${judge.objectCount} judged" }
			for (i in 0 until judge.judgedCount) {
				val delta = judge.judgedDeltaMs(i)
				if (judge.judgedIsPress(i)) check(Math.abs(delta) < MAX_BOT_ERROR_MS) { "a press was $delta ms off" }
			}
			check(judge.clearLamp == ClearLamp.ALL_JUSTICE_CRITICAL) { "lamp ${judge.clearLamp}, score ${judge.score}" }
			check(judge.score == PERFECT_SCORE) { "score ${judge.score}" }
			check(judge.count(Judgement.MISS) == 0) { "${judge.count(Judgement.MISS)} Misses from a bot that hit everything" }
			check(judge.score > 0) { "no score" }
			val replay = play.recorder.toReplay()
			check(replay.eventCount > judge.objectCount) { "only ${replay.eventCount} events recorded" }
			val again = ReplayJudge.rejudge(play.chartFile, replay).judge!!
			check(again.score == judge.score && again.maxCombo == judge.maxCombo) {
				"the replay scored ${again.score}, live scored ${judge.score}"
			}
		}
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.clickScreenButton("hexium.stage.leave")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("gui.back")
		context.waitForScreen(TitleScreen::class.java)
	}

	/** The Fabulous preset's transparency draws on another path, which can only be judged by eye. */
	private fun notesDrawWithImprovedTransparency(context: ClientGameTestContext) {
		context.runOnClient<RuntimeException> { it.options.improvedTransparency().set(true) }
		context.clickScreenButton("hexium.title_button")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("hexium.difficulty.basic")
		context.waitFor({ HexiumSession.play()?.state == PlaySession.State.PLAYING }, LOAD_TICKS)
		val play = HexiumSession.play()!!
		context.waitFor({ play.clock.songTimeMs >= HOLD_ON_THE_WAY_MS }, SONG_TICKS)
		context.takeScreenshot("improved_transparency")
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.clickScreenButton("hexium.stage.leave")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("gui.back")
		context.waitForScreen(TitleScreen::class.java)
		context.runOnClient<RuntimeException> { it.options.improvedTransparency().set(false) }
	}

	private fun pauseAndCheckTimeStops(context: ClientGameTestContext, play: PlaySession) {
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.waitFor({ play.state == PlaySession.State.PAUSED }, SETTLE_TICKS)
		val pausedAt = context.computeOnClient<Double, RuntimeException> { play.clock.songTimeMs }
		context.waitTicks(SETTLE_TICKS)
		context.runOnClient<RuntimeException> {
			check(play.clock.songTimeMs == pausedAt) { "time moved from $pausedAt to ${play.clock.songTimeMs} while paused" }
		}
		context.setScreen { null }
		context.waitFor({ play.state == PlaySession.State.PLAYING && play.clock.songTimeMs > pausedAt }, SETTLE_TICKS)
	}

	/** Note speed and offset A are for the eye: the judge never hears of them, which the perfect score below shows. */
	private fun pauseSettingsChange(context: ClientGameTestContext, play: PlaySession) {
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.waitFor({ play.state == PlaySession.State.PAUSED }, SETTLE_TICKS)
		// This finds the first row's button, the note speed. Offset A has the same buttons on the next row.
		context.clickScreenButton("hexium.stage.more")
		context.waitTick()
		context.runOnClient<RuntimeException> {
			check(PlaySettings.noteSpeed > PlaySettings.DEFAULT_NOTE_SPEED) { "the note speed button did nothing" }
			PlaySettings.changeVisualOffset(VISUAL_OFFSET_STEPS)
		}
		context.takeScreenshot("pause_menu")
		context.setScreen { null }
		context.waitFor({ play.state == PlaySession.State.PLAYING }, SETTLE_TICKS)
	}

	private class Press(val atMs: Double, val lane: Int, val down: Boolean)

	/** A bot that plays every note. */
	private fun script(play: PlaySession): List<Press> {
		val timing = TimingMap(play.chart.offsetMs, play.chart.bpm)
		val presses = ArrayList<Press>()
		for (note in play.chart.notes) {
			when (note) {
				is Note.Tap -> {
					presses += Press(timing.beatToMs(note.t), note.lane, true)
					presses += Press(timing.beatToMs(note.t) + TAP_LENGTH_MS, note.lane, false)
				}
				is Note.Hold -> {
					presses += Press(timing.beatToMs(note.t), note.lane, true)
					presses += Press(timing.beatToMs(note.end), note.lane, false)
				}
				else -> error("Sky note in a ground chart")
			}
		}
		return presses.sortedBy { it.atMs }
	}

	private fun normalWorldsAreUntouched(context: ClientGameTestContext) {
		context.worldBuilder().create().use { world ->
			world.connection.waitForChunksRender()
			context.runOnClient<RuntimeException> { check(!HexiumSession.active()) { "a session in a normal world" } }
			val trackFrames = context.computeOnClient<Long, RuntimeException> { TrackRenderer.framesDrawn }
			val start = position(context)
			context.input.holdKey { it.keyUp }
			context.waitTicks(HOLD_TICKS)
			context.input.releaseKey { it.keyUp }
			check(position(context) != start) { "the player can't move in a normal world" }
			context.input.pressKey { it.keyHotbarSlots[3] }
			context.waitTicks(SETTLE_TICKS)
			context.runOnClient<RuntimeException> {
				check(it.player!!.inventory.selectedSlot == 3) { "the hotbar keys don't work in a normal world" }
			}
			context.input.scroll(1.0)
			context.waitTicks(SETTLE_TICKS)
			context.runOnClient<RuntimeException> {
				check(it.player!!.inventory.selectedSlot != 3) { "the wheel doesn't work in a normal world" }
			}
			context.runOnClient<RuntimeException> {
				check(TrackRenderer.framesDrawn == trackFrames) { "the track was drawn in a normal world" }
			}
			context.input.pressKey { it.keyInventory }
			context.waitForScreen(InventoryScreen::class.java)
			context.setScreen { null }
			context.runOnClient<RuntimeException> { it.pauseGame(false) }
			context.waitFor({ it.gui.screen()?.javaClass?.simpleName == "PauseScreen" }, LOAD_TICKS)
			context.setScreen { null }
		}
	}

	private fun position(context: ClientGameTestContext): Triple<Double, Double, Double> =
		context.computeOnClient<Triple<Double, Double, Double>, RuntimeException> { minecraft: Minecraft ->
			val player = minecraft.player!!
			Triple(player.x, player.y, player.z)
		}

	private companion object {
		const val LOAD_TICKS = 1200
		const val SETTLE_TICKS = 20
		const val HOLD_TICKS = 40
		const val TURN_PIXELS = 200.0
		const val TAP_LENGTH_MS = 30.0
		const val SONG_TICKS = 1200
		const val BOT_LOOKAHEAD_MS = 120.0
		const val NANOS_PER_MS = 1_000_000.0
		const val MAX_BOT_ERROR_MS = 5.0
		const val PERFECT_SCORE = 1_010_000

		/** Between notes in basic.json. */
		const val PAUSE_AT_MS = 3250.0

		const val VISUAL_OFFSET_STEPS = 10

		/** A hold and taps are in view, with the taps in the lanes either side of it. */
		const val HOLD_ON_THE_WAY_MS = 3400.0

		/** The first note in basic.json is at 2000 ms. */
		const val NOTES_IN_VIEW_MS = 1500.0

		/** During its first hold, 4000 to 5000 ms in the second lane. */
		const val HOLD_IN_PLAY_MS = 4500.0
	}
}
