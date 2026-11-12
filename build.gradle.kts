import dev.nucleusframework.desktop.application.dsl.NativeImageMarch
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("dev.nucleusframework") version "3.0.0-dev-202611061347"
}

group = "com.sasami.vlc"
version = "1.0.4"

kotlin { jvmToolchain(21) }

val nucleusVersion = "3.0.0-dev-202611061347"

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation("dev.nucleusframework:nucleus.nucleus-application:$nucleusVersion")
    implementation("dev.nucleusframework:nucleus.decorated-window-tao:$nucleusVersion")
    implementation("dev.nucleusframework:nucleus.graalvm-runtime:$nucleusVersion")
    implementation("io.github.vinceglb:filekit-dialogs:0.16.0")
    implementation("uk.co.caprica:vlcj:4.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

nucleus.application {
    mainClass = "com.sasami.vlc.MainKt"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        toolchain { version = "25.0.2" }
        imageName = "CineK"
        march = NativeImageMarch.COMPATIBILITY
        // JNA reads Windows' native code page (Cp1252, Cp932, etc.) during bootstrap.
        allCharsets = true
        nativeImageConfigBaseDir.set(layout.projectDirectory.dir("src/main/resources/META-INF/native-image/com.sasami.vlc/CineK"))
        buildArgs.add("--exact-reachability-metadata=com.sasami.vlc,uk.co.caprica.vlcj")
        // Live-channel playlists and network media use URLConnection with runtime URLs.
        buildArgs.add("--enable-url-protocols=http,https")
    }
    nativeDistributions {
        providers.gradleProperty("desktopOutputDir").orNull?.let {
            outputBaseDir.set(project.layout.projectDirectory.dir(it))
        }
        targetFormats(TargetFormat.Msi)
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

tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (name == "run") {
        systemProperty("jna.library.path", project.layout.projectDirectory.dir("resources/common/vlc").asFile.absolutePath)
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

// Capture app/VLC dynamic access on a real run, without requiring the C++ compiler.
// Set GRAALVM_HOME and use -PtraceNative=true; generated files are review inputs.
tasks.withType<JavaExec>().matching { it.name in setOf("desktopSmoke", "startupSmoke") }.configureEach {
    if (providers.gradleProperty("traceNative").orNull == "true") {
        val graalHome = providers.environmentVariable("GRAALVM_HOME").get()
        setExecutable(file("$graalHome/bin/java.exe").absolutePath)
        val metadataDir = layout.buildDirectory.dir("native-agent").get().asFile.apply { mkdirs() }
        jvmArgs("-agentlib:native-image-agent=config-merge-dir=${metadataDir.absolutePath},access-filter-file=${file("native-agent-filter.json").absolutePath}")
    }
}
