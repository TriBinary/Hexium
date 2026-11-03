// Game rules: chart model, timing, judge, scoring, replays.
// Plain Kotlin, no Minecraft code, shared by the mod, server and tools.
plugins {
	id("org.jetbrains.kotlin.jvm")
}

dependencies {
	// Chart JSON, tree API only. In the mod it comes from Fabric Language Kotlin.
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${providers.gradleProperty("kotlinx_serialization_version").get()}")

	testImplementation(kotlin("test"))
}
