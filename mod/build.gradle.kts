// The Fabric mod. Hexium is client-only: almost everything lives in src/client.
plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
}

base {
	// A plain value, not a lazy provider: Kotlin derives its module name from this,
	// and a lazy chain would capture the Project and break the configuration cache.
	archivesName = providers.gradleProperty("archives_base_name").get()
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("hexium") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// The game rules, bundled inside the mod jar (jar-in-jar).
	implementation(project(":core"))
	include(project(":core"))
}

tasks.processResources {
	val version = project.version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

java {
	// Loom attaches the sources jar to the build. It isn't part of a release.
	withSourcesJar()
}

tasks.jar {
	from(rootProject.file("LICENSE")) {
		rename { "${it}_hexium" }
	}
}
