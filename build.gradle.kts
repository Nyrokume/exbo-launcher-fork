plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "dev.launcherfork"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Headless JavaFX (Monocle) for tests: no X server needed
    testRuntimeOnly("org.testfx:openjfx-monocle:21.0.2")
}

application {
    mainClass.set("dev.launcherfork.app.MainKt")
    applicationDefaultJvmArgs = listOf("-Dprism.lcdtext=false")
}

tasks.named<JavaExec>("run") {
    // forward -Dlauncher.* (e.g. -Dlauncher.lang=en, -Dlauncher.screenshots=docs/screenshots)
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("launcher.")) systemProperty(k.toString(), v)
    }
}

// ./gradlew showcase: looping presentation of the redesign with mock data that keeps nothing (docs/SHOWCASE.md);
// -Dlauncher.showcase.size=1920x1080, -Dlauncher.showcase.captions=false etc. are forwarded like for `run`
tasks.register<JavaExec>("showcase") {
    group = "application"
    description = "Runs the launcher in showcase mode (looping presentation, mock data, nothing saved)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.launcherfork.app.MainKt")
    jvmArgs("-Dprism.lcdtext=false")
    systemProperty("launcher.showcase", "true")
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("launcher.")) systemProperty(k.toString(), v)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("prism.order", "sw")
    systemProperty("java.awt.headless", "true")
    // no motion in tests: every transition applies its end state at once, so layout / contrast checks never see a
    // half-finished frame (MotionTest switches it on for itself)
    systemProperty("launcher.animations", "off")
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// App jar + non-JavaFX runtime deps (Kotlin stdlib) for jlink-based packaging, see packaging/build-dist.sh.
// JavaFX itself comes from platform jmods linked into the runtime image, so its jars are excluded here.
// Sync (not Copy): stale jars from older versions must not end up in the release.
tasks.register<Sync>("stageAppJars") {
    description = "Syncs the app jar and its non-JavaFX runtime dependencies to build/stage/lib"
    from(tasks.jar)
    from(configurations.runtimeClasspath.map { cp -> cp.filter { !it.name.startsWith("javafx-") } })
    into(layout.buildDirectory.dir("stage/lib"))
}
