// Game rules: chart model, timing, judge, scoring, replays.
// Plain Kotlin, no Minecraft code, shared by the mod, server and tools.
plugins {
	id("org.jetbrains.kotlin.jvm")
}

dependencies {
	// Chart and settings JSON, tree API only. In the mod it comes from Fabric Language Kotlin.
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${providers.gradleProperty("kotlinx_serialization_version").get()}")

	testImplementation(kotlin("test"))
	testImplementation("net.jqwik:jqwik:${providers.gradleProperty("jqwik_version").get()}")
}

tasks.test {
	// The tests read the example charts, the spec's examples and the mod's test pack.
	inputs.dir(rootProject.file("docs/examples"))
	inputs.file(rootProject.file("docs/CHART_FORMAT.md"))
	inputs.dir(rootProject.file("mod/src/client/resources/hexium_packs"))

	// Regenerates the golden replays after a deliberate change (docs/DEVELOPMENT.md).
	systemProperty("hexium.updateGolden", providers.systemProperty("hexium.updateGolden").getOrElse("false"))
}
