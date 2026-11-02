package net.trilleo.hexium

import net.minecraft.resources.Identifier

/** Mod-wide constants. Hexium is client-only, so the entrypoint is HexiumClient. */
object Hexium {
	const val MOD_ID: String = "hexium"

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
