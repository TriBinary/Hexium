package net.trilleo.hexium.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.tutorial.TutorialSteps
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.trilleo.hexium.client.screen.HexiumMenuScreen
import net.trilleo.hexium.client.screen.StageMenuScreen
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageGeometry

/** Runs in the real game. See docs/DEVELOPMENT.md. */
class HexiumClientGameTest : FabricClientGameTest {
	override fun runTest(context: ClientGameTestContext) {
		context.waitForScreen(TitleScreen::class.java)
		context.takeScreenshot("title")
		theStageLocksThePlayerAndLeavesCleanly(context)
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

	private fun normalWorldsAreUntouched(context: ClientGameTestContext) {
		context.worldBuilder().create().use { world ->
			world.connection.waitForChunksRender()
			context.runOnClient<RuntimeException> { check(!HexiumSession.active()) { "a session in a normal world" } }
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
	}
}
