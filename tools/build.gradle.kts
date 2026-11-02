// Command-line tools: chart lint, chart convert, replay re-judge.
plugins {
	id("org.jetbrains.kotlin.jvm")
	application
}

dependencies {
	implementation(project(":core"))
	testImplementation(kotlin("test"))
}

application {
	mainClass = "net.trilleo.hexium.tools.MainKt"
}
