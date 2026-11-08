package net.trilleo.hexium.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import java.nio.file.Files
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.tutorial.TutorialSteps
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.trilleo.hexium.client.input.Aim
import net.trilleo.hexium.client.input.LaneInput
import net.trilleo.hexium.client.input.SkyInput
import net.trilleo.hexium.client.play.PlayHud
import net.trilleo.hexium.client.play.PlaySettings
import net.trilleo.hexium.client.play.TrackRenderer
import net.trilleo.hexium.client.play.PlaySession
import net.trilleo.hexium.client.screen.CalibrationScreen
import net.trilleo.hexium.client.screen.HexiumMenuScreen
import net.trilleo.hexium.client.screen.ResultsScreen
import net.trilleo.hexium.client.screen.StageMenuScreen
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageGeometry
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.TimingMap
import net.trilleo.hexium.core.judge.ClearLamp
import net.trilleo.hexium.core.judge.Judgement
import net.trilleo.hexium.core.replay.ReplayFile
import net.trilleo.hexium.core.replay.ReplayEvent
import net.trilleo.hexium.core.replay.ReplayJudge
import net.trilleo.hexium.core.settings.PlayerSettings
import org.lwjgl.sdl.SDLTimer

/** Runs in the real game. See docs/DEVELOPMENT.md. */
class HexiumClientGameTest : FabricClientGameTest {
	override fun runTest(context: ClientGameTestContext) {
		context.waitForScreen(TitleScreen::class.java)
		context.takeScreenshot("title")
		// The game folder stays between runs, and so do its settings.
		context.runOnClient<RuntimeException> { PlaySettings.reset() }
		// Songs play on a real audio device. Keep them quiet.
		context.runOnClient<RuntimeException> { it.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0) }
		theStageLocksThePlayerAndLeavesCleanly(context)
		aChartPlaysToTheEndAndItsReplayMatches(context)
		notesDrawWithImprovedTransparency(context)
		calibrationSuggestsAndSavesOffsets(context)
		normalWorldsAreUntouched(context)
	}

	/** A bot taps late by known amounts, so the right answers are known too. */
	private fun calibrationSuggestsAndSavesOffsets(context: ClientGameTestContext) {
		context.clickScreenButton("hexium.title_button")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("hexium.calibration.title")
		context.waitForScreen(CalibrationScreen::class.java)
		// Songs are kept silent in these tests, and this step won't start without sound.
		context.takeScreenshot("calibration_no_sound")
		context.clickScreenButton("gui.cancel")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.runOnClient<RuntimeException> { it.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(QUIET_VOLUME) }
		context.clickScreenButton("hexium.calibration.title")
		context.waitForScreen(CalibrationScreen::class.java)
		context.takeScreenshot("calibration_intro")

		// Leaving part way lets the keys go back to normal.
		context.clickScreenButton("hexium.calibration.start")
		context.waitFor({ LaneInput.capturing }, SETTLE_TICKS)
		context.clickScreenButton("gui.cancel")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.runOnClient<RuntimeException> {
			check(!LaneInput.capturing) { "cancelling left the screen reading keys" }
			check(!HexiumSession.active()) { "cancelling left a session" }
		}
		context.clickScreenButton("hexium.calibration.title")
		context.waitForScreen(CalibrationScreen::class.java)
		val screen = context.computeOnClient<CalibrationScreen, RuntimeException> { it.gui.screen() as CalibrationScreen }

		// Uneven taps are sent back to try again.
		context.clickScreenButton("hexium.calibration.start")
		context.waitFor({ screen.step == CalibrationScreen.Step.LISTENING && screen.clock!!.songTimeMs >= CLOCK_SETTLED_MS }, COUNTDOWN_TICKS)
		context.runOnClient<RuntimeException> { pushTaps(screen, TAPS_FOR_A_STEP) { beat -> if (beat % 2 == 0) -UNEVEN_MS else UNEVEN_MS } }
		context.waitFor({ screen.step == CalibrationScreen.Step.LISTEN_INTRO }, SETTLE_TICKS)

		context.clickScreenButton("hexium.calibration.start")
		context.waitFor({ screen.step == CalibrationScreen.Step.LISTENING && screen.clock!!.songTimeMs >= CLOCK_SETTLED_MS }, COUNTDOWN_TICKS)
		context.runOnClient<RuntimeException> { pushTaps(screen, TAPS_FOR_A_STEP) { TAP_LATE_TO_SOUND_MS } }
		context.waitFor({ screen.step == CalibrationScreen.Step.WATCH_INTRO }, SETTLE_TICKS)

		context.clickScreenButton("hexium.calibration.start")
		context.waitFor({ screen.step == CalibrationScreen.Step.WATCHING && screen.clock!!.songTimeMs >= CLOCK_SETTLED_MS }, COUNTDOWN_TICKS)
		context.takeScreenshot("calibration_notes")
		context.runOnClient<RuntimeException> { pushTaps(screen, TAPS_FOR_A_STEP) { TAP_LATE_TO_NOTES_MS } }
		context.waitFor({ screen.step == CalibrationScreen.Step.REVIEW }, SETTLE_TICKS)
		context.takeScreenshot("calibration_review")
		context.runOnClient<RuntimeException> {
			val judgement = screen.suggestedJudgementOffsetMs
			val visual = screen.suggestedVisualOffsetMs
			check(Math.abs(judgement - TAP_LATE_TO_SOUND_MS) <= MAX_CALIBRATION_ERROR_MS) { "suggested B $judgement" }
			check(Math.abs(visual - (TAP_LATE_TO_SOUND_MS - TAP_LATE_TO_NOTES_MS)) <= MAX_CALIBRATION_ERROR_MS) { "suggested A $visual" }
		}
		val (visual, judgement) = context.computeOnClient<Pair<Int, Int>, RuntimeException> { screen.suggestedVisualOffsetMs to screen.suggestedJudgementOffsetMs }
		context.clickScreenButton("hexium.calibration.save")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.runOnClient<RuntimeException> {
			check(!HexiumSession.active()) { "closing calibration left a session" }
			check(PlaySettings.visualOffsetMs == visual && PlaySettings.judgementOffsetMs == judgement) { "the offsets weren't kept" }
			PlaySettings.load()
			check(PlaySettings.visualOffsetMs == visual && PlaySettings.judgementOffsetMs == judgement) { "the offsets weren't saved to the file" }
		}

		// A play takes B from the settings and its replay records it.
		context.clickScreenButton("hexium.difficulty.basic")
		context.waitFor({ HexiumSession.play()?.state == PlaySession.State.PLAYING }, LOAD_TICKS)
		context.runOnClient<RuntimeException> {
			val replay = HexiumSession.play()!!.recorder.toReplay()
			check(replay.judgementOffsetMs == judgement.toDouble()) { "the replay has B ${replay.judgementOffsetMs}" }
			it.pauseGame(false)
		}
		context.waitForScreen(StageMenuScreen::class.java)
		context.clickScreenButton("hexium.stage.leave")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("gui.back")
		context.waitForScreen(TitleScreen::class.java)
		context.runOnClient<RuntimeException> {
			PlaySettings.reset()
			it.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0)
		}
	}

	/** Each tap is stamped on a beat plus its error, ahead of time, the way SDL stamps a real key. */
	private fun pushTaps(screen: CalibrationScreen, count: Int, errorMs: (Int) -> Int) {
		val clock = screen.clock!!
		val now = SDLTimer.SDL_GetTicksNS()
		val firstBeat = Math.ceil(clock.songTimeAt(now) / CLICK_BEAT_MS).toInt() + 1
		for (i in 0 until count) {
			val beat = firstBeat.coerceAtLeast(0) + i
			val aheadMs = beat * CLICK_BEAT_MS + errorMs(beat) - clock.songTimeAt(now)
			LaneInput.push(0, true, now + (aheadMs * NANOS_PER_MS).toLong())
			LaneInput.push(0, false, now + (aheadMs * NANOS_PER_MS).toLong())
		}
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
		context.waitFor({ it.player!!.x == 0.5 && it.player!!.z == 0.5 && it.player!!.y > it.level!!.minY + StageGeometry.FLOOR_THICKNESS }, LOAD_TICKS)
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
			val standY = minecraft.level!!.minY + StageGeometry.FLOOR_THICKNESS + StageGeometry.STAND_HEIGHT
			check(player.y == standY.toDouble()) { "not on the platform: y ${player.y}" }
			check(player.onGround()) { "standing on nothing" }
			val floorY = minecraft.level!!.minY + StageGeometry.FLOOR_THICKNESS
			val toMiddleOfScreen = Math.toDegrees(Math.atan((player.eyeY - floorY - SCREEN_MIDDLE_HEIGHT) / (StageGeometry.JUDGEMENT_Z - player.z)))
			check(Math.abs(toMiddleOfScreen - StageGeometry.REST_PITCH) < MAX_PITCH_ERROR) { "the rest view isn't on the screen's middle, which is $toMiddleOfScreen down" }
			val reach = StageGeometry.PLATFORM_REACH
			check(!minecraft.level!!.getBlockState(BlockPos(reach, floorY, reach)).isAir) { "the platform has no corner" }
			check(minecraft.level!!.getBlockState(BlockPos(reach + 1, floorY, reach + 1)).isAir) { "the platform is too wide" }
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
		// Sampled each tick, since a jump would be back on the platform by the end.
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

		// A big swing, a frame at a time as a real mouse moves, stops at the sky screen's edge plus the margin.
		swing(context, SWING_STEP_PIXELS, -SWING_STEP_PIXELS)
		context.takeScreenshot("camera_limit")
		context.runOnClient<RuntimeException> {
			val edgeX = 1.0 + StageGeometry.AIM_MARGIN / StageGeometry.SCREEN_WIDTH
			val edgeY = 1.0 + StageGeometry.AIM_MARGIN / StageGeometry.SCREEN_HEIGHT
			check(StageGeometry.SCREEN_WIDTH == StageGeometry.LANE_WIDTH * 4 && StageGeometry.SCREEN_HEIGHT == 6.0) { "the sky screen isn't 12 by 6" }
			check(Math.abs(Aim.x - edgeX) < MAX_AIM_ERROR && Math.abs(Aim.y - edgeY) < MAX_AIM_ERROR) { "the camera stopped at ${Aim.x}, ${Aim.y}" }
		}
		swing(context, -SWING_STEP_PIXELS, SWING_STEP_PIXELS)
		context.runOnClient<RuntimeException> {
			val edgeX = -StageGeometry.AIM_MARGIN / StageGeometry.SCREEN_WIDTH
			val edgeY = -StageGeometry.AIM_MARGIN / StageGeometry.SCREEN_HEIGHT
			check(Math.abs(Aim.x - edgeX) < MAX_AIM_ERROR && Math.abs(Aim.y - edgeY) < MAX_AIM_ERROR) { "the camera stopped at ${Aim.x}, ${Aim.y}" }
		}

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

	private fun swing(context: ClientGameTestContext, stepX: Double, stepY: Double) {
		repeat(SWING_STEPS) {
			context.input.moveCursor(stepX, stepY)
			context.waitTick()
		}
		context.waitTicks(SETTLE_TICKS)
	}

	private fun aChartPlaysToTheEndAndItsReplayMatches(context: ClientGameTestContext) {
		context.clickScreenButton("hexium.title_button")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("hexium.difficulty.basic")
		context.waitFor({ HexiumSession.play()?.state == PlaySession.State.PLAYING }, LOAD_TICKS)
		val play = HexiumSession.play()!!
		// Off the middle, so a click's place shows it comes from the camera. Vanilla ignores the first move after grabbing the mouse.
		context.input.moveCursor(AIM_TURN_PIXELS, AIM_TURN_PIXELS)
		context.waitTicks(AIM_TURN_TICKS)
		context.input.moveCursor(AIM_TURN_PIXELS, AIM_TURN_PIXELS)
		context.waitTicks(AIM_TURN_TICKS)
		val aimed = context.computeOnClient<Pair<Float, Float>, RuntimeException> { Aim.x to Aim.y }
		check(Math.abs(aimed.first - 0.5f) > MIN_AIM_TURN && Math.abs(aimed.second - 0.5f) > MIN_AIM_TURN) { "the camera didn't turn off the middle: $aimed" }
		// A tap and a click pushed between frames, as SDL delivers them, must not be lost to the pause right after.
		context.runOnClient<RuntimeException> {
			val now = SDLTimer.SDL_GetTicksNS()
			LaneInput.push(TAP_BEFORE_PAUSE_LANE, true, now)
			LaneInput.push(TAP_BEFORE_PAUSE_LANE, false, now)
			SkyInput.push(SkyButton.R, true, now)
			SkyInput.push(SkyButton.R, false, now)
		}
		pauseAndCheckTimeStops(context, play, "lead_in")
		context.runOnClient<RuntimeException> {
			val replay = play.recorder.toReplay()
			check((0 until replay.eventCount).any { replay.event(it) == ReplayEvent.LANE_DOWN && replay.index(it) == TAP_BEFORE_PAUSE_LANE }) {
				"a tap just before the pause was lost"
			}
			// Pressed where the crosshair was.
			check(
				(0 until replay.eventCount).any {
					replay.event(it) == ReplayEvent.BUTTON_DOWN && replay.index(it) == SkyButton.R.ordinal &&
						Math.abs(replay.x(it) - aimed.first) < MAX_AIM_ERROR && Math.abs(replay.y(it) - aimed.second) < MAX_AIM_ERROR
				},
			) { "a click just before the pause was lost or put in the wrong place" }
			check(
				(0 until replay.eventCount).any {
					replay.event(it) == ReplayEvent.AIM && Math.abs(replay.x(it) - aimed.first) < MAX_AIM_ERROR && Math.abs(replay.y(it) - aimed.second) < MAX_AIM_ERROR
				},
			) { "no aim sample where the camera pointed" }
		}

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
		pauseAndCheckTimeStops(context, play, "countdown")
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
		context.waitForScreen(ResultsScreen::class.java)
		context.takeScreenshot("results")
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
			val saved = play.savedReplay ?: error("the replay wasn't saved")
			val replay = ReplayFile.read(Files.readAllBytes(saved)).replay ?: error("the saved replay can't be read")
			check(replay.eventCount > judge.objectCount) { "only ${replay.eventCount} events recorded" }
			val again = ReplayJudge.rejudge(play.chartFile, replay).judge!!
			check(again.score == judge.score && again.maxCombo == judge.maxCombo) {
				"the replay scored ${again.score}, live scored ${judge.score}"
			}
		}
		retryAndLeave(context, play)
	}

	/** Retry starts the chart again without leaving the stage, from the results and from the pause screen. */
	private fun retryAndLeave(context: ClientGameTestContext, finished: PlaySession) {
		context.clickScreenButton("hexium.results.retry")
		context.waitFor({ HexiumSession.play() !== finished && HexiumSession.play()?.state == PlaySession.State.PLAYING }, SETTLE_TICKS)
		val second = HexiumSession.play()!!
		context.runOnClient<RuntimeException> {
			check(second.judge.judgedCount == 0 && second.clock.songTimeMs < 0.0) { "a retry didn't start from the top" }
		}
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.clickScreenButton("hexium.results.retry")
		context.waitFor({ HexiumSession.play() !== second && HexiumSession.play()?.state == PlaySession.State.PLAYING }, SETTLE_TICKS)
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
		context.runOnClient<RuntimeException> { PlayHud.debugShown = true }
		context.waitTicks(DEBUG_REFRESH_TICKS)
		context.takeScreenshot("debug_overlay")
		context.runOnClient<RuntimeException> { PlayHud.debugShown = false }
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.clickScreenButton("hexium.stage.leave")
		context.waitForScreen(HexiumMenuScreen::class.java)
		context.clickScreenButton("gui.back")
		context.waitForScreen(TitleScreen::class.java)
		context.runOnClient<RuntimeException> { it.options.improvedTransparency().set(false) }
	}

	private fun pauseAndCheckTimeStops(context: ClientGameTestContext, play: PlaySession, screenshot: String) {
		context.runOnClient<RuntimeException> { it.pauseGame(false) }
		context.waitForScreen(StageMenuScreen::class.java)
		context.waitFor({ play.state == PlaySession.State.PAUSED }, SETTLE_TICKS)
		val pausedAt = context.computeOnClient<Double, RuntimeException> { play.clock.songTimeMs }
		context.waitTicks(SETTLE_TICKS)
		context.runOnClient<RuntimeException> {
			check(play.clock.songTimeMs == pausedAt) { "time moved from $pausedAt to ${play.clock.songTimeMs} while paused" }
		}
		val judgedAtPause = context.computeOnClient<Int, RuntimeException> { play.judge.judgedCount }
		context.setScreen { null }
		// Resuming goes back 3 seconds, and judging only reopens at the pause point.
		context.waitFor({ play.state == PlaySession.State.REWINDING }, SETTLE_TICKS)
		context.runOnClient<RuntimeException> {
			check(play.clock.songTimeMs < pausedAt - MIN_REWIND_MS) { "resumed at ${play.clock.songTimeMs} from $pausedAt" }
		}
		context.takeScreenshot(screenshot)
		context.waitFor({ play.state == PlaySession.State.PLAYING }, COUNTDOWN_TICKS)
		context.runOnClient<RuntimeException> {
			check(play.clock.songTimeMs >= pausedAt) { "judging reopened at ${play.clock.songTimeMs}, before $pausedAt" }
			check(play.judge.judgedCount == judgedAtPause) { "something was judged during the countdown" }
		}
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
			check(PlaySettings.noteSpeed > PlayerSettings.DEFAULT_NOTE_SPEED) { "the note speed button did nothing" }
			PlaySettings.changeVisualOffset(VISUAL_OFFSET_STEPS)
		}
		context.takeScreenshot("pause_menu")
		context.setScreen { null }
		context.waitFor({ play.state == PlaySession.State.REWINDING }, SETTLE_TICKS)
		context.waitFor({ play.state == PlaySession.State.PLAYING }, COUNTDOWN_TICKS)
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

		/** About 15 degrees a step at the default sensitivity, far enough in all to turn well past every edge. */
		const val SWING_STEP_PIXELS = 100.0
		const val SWING_STEPS = 30
		const val MAX_AIM_ERROR = 0.002

		/** A few degrees right and down at the default sensitivity, a little way into the lead-in. */
		const val AIM_TURN_PIXELS = 20.0
		const val AIM_TURN_TICKS = 3
		const val MIN_AIM_TURN = 0.02f
		const val TAP_LENGTH_MS = 30.0
		const val SONG_TICKS = 1200
		const val BOT_LOOKAHEAD_MS = 120.0
		const val NANOS_PER_MS = 1_000_000.0
		const val MAX_BOT_ERROR_MS = 5.0
		const val PERFECT_SCORE = 1_010_000

		/** Between notes in basic.json. */
		const val PAUSE_AT_MS = 3250.0

		const val COUNTDOWN_TICKS = 100
		const val DEBUG_REFRESH_TICKS = 10
		const val QUIET_VOLUME = 0.01
		const val TAPS_FOR_A_STEP = 18
		const val CLICK_BEAT_MS = 500.0
		const val UNEVEN_MS = 150
		const val TAP_LATE_TO_SOUND_MS = 40
		const val TAP_LATE_TO_NOTES_MS = 10
		const val CLOCK_SETTLED_MS = 1000.0
		const val MAX_CALIBRATION_ERROR_MS = 4
		const val TAP_BEFORE_PAUSE_LANE = 3
		const val MIN_REWIND_MS = 2000.0

		const val SCREEN_MIDDLE_HEIGHT = 3.0
		const val MAX_PITCH_ERROR = 0.1

		const val VISUAL_OFFSET_STEPS = 10

		/** A hold and taps are in view, with the taps in the lanes either side of it. */
		const val HOLD_ON_THE_WAY_MS = 3400.0

		/** The first note in basic.json is at 2000 ms. */
		const val NOTES_IN_VIEW_MS = 1500.0

		/** During its first hold, 4000 to 5000 ms in the second lane. */
		const val HOLD_IN_PLAY_MS = 4500.0
	}
}
