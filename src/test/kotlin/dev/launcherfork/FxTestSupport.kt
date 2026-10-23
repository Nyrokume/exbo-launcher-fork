package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.i18n.Strings
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Navigator
import dev.launcherfork.ui.UiContext
import javafx.application.Platform
import javafx.stage.Window
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Starts the JavaFX toolkit once (headless Monocle, see build.gradle.kts) and runs code on the FX thread. */
object FxTestSupport {
    @Volatile private var started = false

    @Synchronized
    fun start() {
        if (started) return
        val latch = CountDownLatch(1)
        try {
            Platform.startup { latch.countDown() }
        } catch (_: IllegalStateException) {
            latch.countDown() // already running
        }
        check(latch.await(20, TimeUnit.SECONDS)) { "JavaFX toolkit did not start" }
        // tests show and close real windows (AuthWindowTest): closing the last one must not stop the toolkit
        Platform.setImplicitExit(false)
        Theme.loadFonts()
        started = true
    }

    fun <T> onFx(block: () -> T): T {
        start()
        val result = AtomicReference<Result<T>>()
        val latch = CountDownLatch(1)
        Platform.runLater {
            result.set(runCatching(block))
            latch.countDown()
        }
        check(latch.await(30, TimeUnit.SECONDS)) { "FX task timed out" }
        return result.get().getOrThrow()
    }

    fun context(lang: String = "ru", app: AppContext = AppContext.mock(language = lang, speed = 50.0, authDelayMs = 0)): UiContext =
        UiContext(app.services, NoopNavigator, Strings.bundle(lang), lang, app.clientStates, app.devMode, app.desk, app.liveBackend)

    /** Polls [condition] on the FX thread until it holds or [timeoutMs] passes. */
    fun waitFx(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition)) return true
            Thread.sleep(20)
        }
        return onFx(condition)
    }
}

object NoopNavigator : Navigator {
    override fun showLauncherUpdate(afterUpdate: () -> Unit) = Unit
    override fun showAuth() = Unit
    override fun showMailAuth() = Unit
    override fun showMain() = Unit
    override fun switchLanguage(lang: String) = Unit
    override fun minimize() = Unit
    override fun close() = Unit
    override val window: Window? = null
}
