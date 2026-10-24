plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "com.sasami.vlc"
version = "1.0.2"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation("uk.co.caprica:vlcj:4.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}

compose.desktop {
    application {
        mainClass = "com.sasami.vlc.MainKt"
        // The launcher draws this natively before the JVM starts loading classes; AWT
        // closes it when the main window is shown. resources/common is copied to
        // $APPDIR/resources, and the JDK picks splash@125pct.png and so on by DPI.
        jvmArgs += "-splash:\$APPDIR/resources/splash.png"
        nativeDistributions {
            providers.gradleProperty("desktopOutputDir").orNull?.let {
                outputBaseDir.set(project.layout.projectDirectory.dir(it))
            }
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "CineK"
            packageVersion = project.version.toString()
            // Map resources so that extra native files (vlc DLLs) are bundled
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // Declare JVM modules required by Compose, JNA/vlcj, and coroutines
            modules("java.desktop", "java.instrument", "java.management", "java.naming", "java.sql", "java.xml", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icon.ico"))
            }
            linux {
                iconFile.set(project.file("icon.png"))
            }
        }
    }
}

tasks.withType<JavaExec> {
    if (name == "run") {
        systemProperty("jna.library.path", project.layout.projectDirectory.dir("resources/common/vlc").asFile.absolutePath)
        // $APPDIR only exists in the packaged launcher; the last -splash option wins.
        jvmArgs("-splash:" + project.layout.projectDirectory.file("resources/common/splash.png").asFile.absolutePath)
    }
}
// Pure behavior checks do not load native VLC or touch the user's configuration.
tasks.register<JavaExec>("verifyDesktopFeatures") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sasami.vlc.FeatureChecks")
}
tasks.named("check") { dependsOn("verifyDesktopFeatures") }

// Opt-in integration run: opens the real Compose window and uses the bundled VLC.
tasks.register<JavaExec>("desktopSmoke") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sasami.vlc.DesktopSmoke")
}

// Verifies file-association startup through the actual main(args) entry point.
tasks.register<JavaExec>("startupSmoke") {
    dependsOn(tasks.testClasses)
    mustRunAfter("desktopSmoke")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sasami.vlc.StartupSmoke")
}
