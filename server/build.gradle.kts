// The online server: login, chart sharing, leaderboards (Milestone 7).
plugins {
	id("org.jetbrains.kotlin.jvm")
	application
}

dependencies {
	implementation(project(":core"))
	testImplementation(kotlin("test"))
}

application {
	mainClass = "net.trilleo.hexium.server.MainKt"
}
