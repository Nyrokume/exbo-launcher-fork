package dev.launcherfork.ui.screens.update

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.WindowChrome
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.fx
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane

/**
 * update.fxml: the launcher self-update window. In the fork it only plays a fake progress
 * from [dev.launcherfork.domain.UpdateService] and then continues to [onFinished].
 */
class LauncherUpdateController(private val ctx: UiContext) {
    @FXML lateinit var root: Pane
    @FXML lateinit var statusLabel: Label
    @FXML lateinit var errorFirst: Label
    @FXML lateinit var errorSecond: Label
    @FXML lateinit var header: ImageView
    @FXML lateinit var closeButton: Button
    @FXML lateinit var hideButton: Button
    @FXML lateinit var rollingBar: ProgressIndicator

    private var job: Cancellable? = null

    @FXML
    fun initialize() {
        WindowChrome.wireButtons(ctx.navigator, closeButton, hideButton)
        errorFirst.text = ""
        errorSecond.text = ""
        statusLabel.text = ctx.t("auth.checking_for_updates")
        rollingBar.progress = ProgressIndicator.INDETERMINATE_PROGRESS
    }

    /** Starts the fake update; [done] runs on the FX thread. */
    fun start(done: () -> Unit) {
        rollingBar.isVisible = true
        job = ctx.services.updates.checkLauncherUpdate(object : ProgressListener {
            override fun onStatus(statusKey: String) = fx {
                statusLabel.text = ctx.t(statusKey)
            }

            override fun onProgress(progress: Progress) = fx {
                // the original shows an indeterminate spinner + text, not a determinate pie
                statusLabel.text = ctx.t("auth.downloading_update") +
                    "${formatBytes(progress.doneBytes)} / ${formatBytes(progress.totalBytes)}"
            }

            override fun onFinished() = fx { done() }
        })
    }

    fun stop() {
        job?.cancel()
    }
}
