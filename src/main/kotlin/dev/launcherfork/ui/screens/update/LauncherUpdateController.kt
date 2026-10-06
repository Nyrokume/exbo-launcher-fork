package dev.launcherfork.ui.screens.update

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.UpdatePhase
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.Messages
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.UpdatePanel
import dev.launcherfork.ui.components.WindowChrome
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.fx
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane

/**
 * update.fxml: the launcher self-update window. In the fork it only plays a fake progress
 * from [dev.launcherfork.domain.UpdateService] and then continues to [onFinished].
 */
class LauncherUpdateController(private val ctx: UiContext) : Disposable, UpdatePanel {
    @FXML lateinit var root: Pane
    @FXML lateinit var statusLabel: Label
    @FXML lateinit var errorFirst: Label
    @FXML lateinit var errorSecond: Label
    @FXML lateinit var header: ImageView
    @FXML lateinit var closeButton: Button
    @FXML lateinit var hideButton: Button
    @FXML lateinit var rollingBar: ProgressIndicator
    /** Only in update_panel.fxml (zone style): thin bar instead of the spinner; indeterminate while checking. */
    @FXML var progressBar: ProgressBar? = null

    private var job: Cancellable? = null
    private var stopped = false

    @FXML
    fun initialize() {
        WindowChrome.wireButtons(ctx.navigator, closeButton, hideButton)
        errorFirst.text = ""
        errorSecond.text = ""
        statusLabel.text = ctx.t(Messages.key(UpdatePhase.CHECKING))
        rollingBar.progress = ProgressIndicator.INDETERMINATE_PROGRESS
        progressBar?.progress = ProgressIndicator.INDETERMINATE_PROGRESS
    }

    /** Starts the fake update; [done] runs on the FX thread. */
    override fun start(done: () -> Unit) {
        rollingBar.isVisible = progressBar == null
        job = ctx.services.updates.checkLauncherUpdate(object : ProgressListener {
            override fun onPhase(phase: UpdatePhase) = fx {
                if (stopped) return@fx
                statusLabel.text = ctx.t(Messages.key(phase))
                if (phase != UpdatePhase.DOWNLOADING) progressBar?.progress = ProgressIndicator.INDETERMINATE_PROGRESS
            }

            override fun onProgress(progress: Progress) = fx {
                // the original shows an indeterminate spinner + text, not a determinate pie
                if (stopped) return@fx
                progressBar?.progress = progress.fraction.coerceIn(0.0, 1.0)
                statusLabel.text = ctx.t(Messages.key(UpdatePhase.DOWNLOADING)) +
                    "${formatBytes(progress.doneBytes)} / ${formatBytes(progress.totalBytes)}"
            }

            override fun onFinished() = fx {
                if (stopped) return@fx
                stopped = true
                job = null
                done()
            }
        })
    }

    /** Cancels the fake update; no callback reaches the UI afterwards. */
    fun stop() {
        stopped = true
        job?.cancel()
        job = null
    }

    override fun dispose() = stop()
}
