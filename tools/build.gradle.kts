// Command-line tools: chart lint, replay re-judge, replay dump.
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

tasks.named<JavaExec>("run") {
	// So paths in --args are relative to the repository, as the docs show them.
	workingDir = rootProject.projectDir
}

tasks.test {
	// The tests lint the example charts.
	inputs.dir(rootProject.file("docs/examples"))
}
