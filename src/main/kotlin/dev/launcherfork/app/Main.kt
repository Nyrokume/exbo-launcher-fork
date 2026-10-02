package dev.launcherfork.app

import javafx.application.Application

fun main(args: Array<String>) {
    // grayscale text antialiasing, as the original launcher (Main.start)
    System.setProperty("prism.lcdtext", "false")
    Application.launch(LauncherApp::class.java, *args)
}
