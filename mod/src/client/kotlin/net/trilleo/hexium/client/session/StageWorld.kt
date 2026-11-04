package net.trilleo.hexium.client.session

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Optional
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.Difficulty
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.WorldDimensions
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.level.storage.LevelStorageSource
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.screen.HexiumMenuScreen
import org.slf4j.LoggerFactory

/**
 * The stage: a flat floor with nothing else, made fresh for each session in Hexium's own folder, so
 * it never shows up in the vanilla world list. Gameplay never depends on the server: the world is
 * just scenery. Made in code rather than bundled, so a Minecraft update can't ask to upgrade it.
 */
object StageWorld {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
	private const val LEVEL_ID = "arena"
	private const val NOON_TICKS = 6000L

	/** What the player had set before the stage, since the keys that change these are off on it. */
	private var savedCamera: CameraType? = null
	private var savedSmoothCamera = false
	private var savedHudHidden = false

	/**
	 * Only the Hexium menu may call this. If the world can't be made, the player ends up on [parent] or,
	 * if Minecraft can't open its folder, the title screen. Either of those ends the session.
	 */
	fun enter(minecraft: Minecraft, parent: Screen) {
		val folder = FabricLoader.getInstance().gameDir.resolve("hexium").resolve("stages")
		if (!deleteOldStage(folder.resolve(LEVEL_ID))) {
			SystemToast.onWorldAccessFailure(minecraft, LEVEL_ID)
			return
		}
		HexiumSession.start()
		minecraft.musicManager.stopPlaying()
		setView(minecraft)
		val settings = LevelSettings(
			"Hexium Stage",
			GameType.ADVENTURE,
			LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, true),
			false,
			WorldDataConfiguration.DEFAULT,
		)
		WorldOpenFlows(minecraft, LevelStorageSource.createDefault(folder))
			.createFreshLevel(LEVEL_ID, settings, WorldOptions(0L, false, false), ::flatFloor, parent)
	}

	private fun setView(minecraft: Minecraft) {
		val options = minecraft.options
		savedCamera = options.cameraType
		savedSmoothCamera = options.smoothCamera
		savedHudHidden = minecraft.gui.hud.isHidden
		options.cameraType = CameraType.FIRST_PERSON
		options.smoothCamera = false
		if (savedHudHidden) minecraft.gui.hud.toggle()
	}

	/** Puts back what [enter] changed. Does nothing if there's no stage to come back from. */
	fun restoreView() {
		val camera = savedCamera ?: return
		savedCamera = null
		val minecraft = Minecraft.getInstance()
		minecraft.options.cameraType = camera
		minecraft.options.smoothCamera = savedSmoothCamera
		if (minecraft.gui.hud.isHidden != savedHudHidden) minecraft.gui.hud.toggle()
	}

	/**
	 * Leaves the stage for the Hexium menu. Ending the connection ends the session. Runs on the next
	 * pass of the task queue, since disconnecting waits for the server and draws frames meanwhile.
	 */
	fun leave(minecraft: Minecraft) {
		minecraft.schedule {
			minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE)
			minecraft.gui.setScreen(HexiumMenuScreen(TitleScreen()))
		}
	}

	fun registerEvents() {
		ClientPlayConnectionEvents.JOIN.register { _, _, client ->
			if (HexiumSession.active()) {
				client.singleplayerServer?.let { server -> server.execute { prepare(server) } }
			}
		}
		// This one can fire on the network thread.
		ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
			if (HexiumSession.active()) client.execute { HexiumSession.end() }
		}
	}

	private fun flatFloor(registries: HolderLookup.Provider): WorldDimensions {
		val floor = FlatLevelGeneratorSettings(
			Optional.of(HolderSet.direct()),
			registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID),
			emptyList(),
		).withBiomeAndLayers(
			listOf(FlatLayerInfo(StageGeometry.FLOOR_THICKNESS, Blocks.CONCRETE.pick(DyeColor.BLACK))),
			Optional.of(HolderSet.direct()),
			registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID),
		)
		return WorldPresets.createNormalWorldDimensions(registries).replaceOverworldGenerator(registries, FlatLevelSource(floor))
	}

	/** Freezes the world and puts the player on the stand spot. Runs on the server thread. */
	private fun prepare(server: MinecraftServer) {
		val rules = server.gameRules
		rules.set(GameRules.ADVANCE_TIME, false, server)
		rules.set(GameRules.ADVANCE_WEATHER, false, server)
		rules.set(GameRules.RANDOM_TICK_SPEED, 0, server)
		rules.set(GameRules.SPAWN_MOBS, false, server)
		rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server)
		val level = server.overworld()
		level.dimensionTypeRegistration().value().defaultClock().ifPresent { clock ->
			server.clockManager().setTotalTicks(clock, NOON_TICKS)
		}
		val y = level.minY + StageGeometry.FLOOR_THICKNESS.toDouble()
		for (player in server.playerList.players) {
			player.teleportTo(level, StageGeometry.STAND_X, y, StageGeometry.STAND_Z, emptySet(), StageGeometry.REST_YAW, StageGeometry.REST_PITCH, true)
		}
	}

	/** Returns false if it couldn't be cleared: a stage part way between old and new must not load. */
	private fun deleteOldStage(stage: Path): Boolean {
		if (!Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) return true
		return try {
			Files.walk(stage).use { paths ->
				for (path in paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path)
			}
			true
		} catch (e: IOException) {
			LOGGER.warn("Can't delete the old stage {}", stage, e)
			false
		} catch (e: UncheckedIOException) {
			LOGGER.warn("Can't delete the old stage {}", stage, e)
			false
		}
	}
}
