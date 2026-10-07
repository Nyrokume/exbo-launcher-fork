package dev.launcherfork.app

import javafx.application.Application

fun main(args: Array<String>) {
    // Windows: DirectWrite (`prism.text=native`) + LCD ClearType. The default T2K rasterizer + the old
    // subset Roboto (≈60 KB / 470 glyphs) made UI text soft and rough at 11–15 px. Override with
    // -Dprism.text=t2k / -Dprism.lcdtext=false if needed.
    if (System.getProperty("prism.text") == null) {
        System.setProperty("prism.text", "native")
    }
    if (System.getProperty("prism.lcdtext") == null) {
        System.setProperty("prism.lcdtext", "true")
    }
    Application.launch(LauncherApp::class.java, *args)
}
