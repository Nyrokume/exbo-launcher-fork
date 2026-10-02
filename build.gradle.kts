plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "dev.launcherfork"
version = "0.1.0-SNAPSHOT"

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

tasks.test {
    useJUnitPlatform()
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("prism.order", "sw")
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
