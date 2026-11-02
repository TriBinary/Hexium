import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// Plugins are loaded once here and applied in the modules that need them.
plugins {
	id("net.fabricmc.fabric-loom") apply false
	id("org.jetbrains.kotlin.jvm") apply false
}

val modVersion = providers.gradleProperty("mod_version").get()

subprojects {
	group = rootProject.group
	version = modVersion

	repositories {
		mavenCentral()
	}

	plugins.withType<JavaPlugin> {
		extensions.configure<JavaPluginExtension> {
			sourceCompatibility = JavaVersion.VERSION_25
			targetCompatibility = JavaVersion.VERSION_25
		}
		tasks.withType<JavaCompile>().configureEach {
			options.release = 25
		}
		tasks.withType<Test>().configureEach {
			useJUnitPlatform()
		}
	}

	plugins.withId("org.jetbrains.kotlin.jvm") {
		extensions.configure<KotlinJvmProjectExtension> {
			compilerOptions {
				jvmTarget = JvmTarget.JVM_25
			}
		}
	}
}
