package dev.launcherfork.ui.screens.main.modals

import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.motion.Motion
import javafx.animation.PauseTransition
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TextField
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.text.Text
import javafx.util.Duration

/** Base for the full-window modal layers of main.fxml (each one has its own FXML in main/modals/). */
abstract class ModalController(protected val ctx: UiContext) {
    /** The full-window layer of this modal (MainController blocks the screen under it while it is visible). */
    abstract val layer: Node
    var onConfirm: () -> Unit = {}
    var onCancel: () -> Unit = {}
    /** Node that gets the focus when the modal opens; null = the first focusable node of [layer]. */
    open val initialFocus: Node? get() = null

    /**
     * The dim layer fades in and the card (.modal-box) rises 8 px from 98 % scale ([NORMAL]); closing fades the layer out
     * ([FAST]), the layer stays visible (input-blocking) until the fade ends (ui/motion/Motion).
     */
    fun show() {
        showing = true
        Motion.fadeIn(layer)
        card?.let { Motion.popIn(it, dy = Motion.SLIDE, scale = Motion.POP_SCALE) }
    }

    fun hide() {
        if (!showing && !layer.isVisible) return
        showing = false
        Motion.fadeOut(layer)
    }

    private var showing = false
    /** Logical state: true from [show] to [hide] (the layer may still be fading out for a moment after [hide]). */
    val isShowing: Boolean get() = showing && layer.isVisible

    /** The modal's card (the first .modal-box in [layer]); null for card-less layers (loading spinner). */
    private val card: Node? get() = layer.lookup(".modal-box")

    protected fun wire(confirm: Button?, cancel: Button?) {
        // UPPERCASE buttons (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
        listOfNotNull(confirm, cancel).forEach { it.text = it.text.uppercase(java.util.Locale.forLanguageTag(ctx.lang)) }
        confirm?.setOnAction { hide(); onConfirm() }
        cancel?.setOnAction { hide(); onCancel() }
    }
}

/**
 * main/modals/logout_pane.fxml: "Выход из аккаунта" confirmation. Opened only by the profile menu item
 * "Выйти из аккаунта" (MainController wires ProfileController.onLogout to [show]); closing the window never opens
 * it. Enter = log out ([logoutButtonRight]), Esc = cancel (MainController's Esc closes modals), focus starts on
 * Cancel ([logoutButtonLeft]) so a stray Space does not log out.
 */
class LogoutModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var logoutPane: AnchorPane
    @FXML lateinit var logoutCard: VBox
    @FXML lateinit var logoutTitle: Label
    @FXML lateinit var logoutDesc: Label
    @FXML lateinit var logoutButtonRight: Button
    @FXML lateinit var logoutButtonLeft: Button
    override val layer: Node get() = logoutPane
    override val initialFocus: Node get() = logoutButtonLeft

    @FXML
    fun initialize() {
        wire(confirm = logoutButtonRight, cancel = logoutButtonLeft)
        // filter, not handler: Enter means "log out" even while Cancel has the focus
        logoutPane.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            if (isShowing && e.code == KeyCode.ENTER) { e.consume(); logoutButtonRight.fire() }
        }
    }
}

/** main/modals/update_pane.fxml */
class UpdateModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var updatePane: AnchorPane
    @FXML lateinit var updateTitle: Label
    @FXML lateinit var updateDesc: Label
    @FXML lateinit var updateYes: Button
    @FXML lateinit var updateNo: Button
    override val layer: Node get() = updatePane

    @FXML fun initialize() = wire(updateYes, updateNo)
}

/** main/modals/license_pane.fxml */
class LicenseModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var licensePane: AnchorPane
    @FXML lateinit var licenseFirst: CheckBox
    @FXML lateinit var licenseSecond: CheckBox
    @FXML lateinit var licenseRef: Text
    @FXML lateinit var confidentialityRef: Text
    @FXML lateinit var licenseYes: Button
    @FXML lateinit var licenseNo: Button
    @FXML lateinit var licenseLoading: AnchorPane
    override val layer: Node get() = licensePane

    @FXML
    fun initialize() {
        wire(licenseYes, licenseNo)
        licenseYes.disableProperty().bind(licenseFirst.selectedProperty().and(licenseSecond.selectedProperty()).not())
    }
}

/** main/modals/offline_pane.fxml */
class OfflineModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var offlinePane: AnchorPane
    @FXML lateinit var offlineTitle: Label
    @FXML lateinit var offlineDesc: Label
    @FXML lateinit var offlineButton: Button
    @FXML lateinit var offlineProgress: ProgressIndicator
    override val layer: Node get() = offlinePane

    @FXML
    fun initialize() {
        offlineButton.text = offlineButton.text.uppercase(java.util.Locale.forLanguageTag(ctx.lang))
        offlineButton.setOnAction {
            // fake reconnect: spinner for a second, then "connected"
            offlineButton.isVisible = false
            offlineProgress.isVisible = true
            PauseTransition(Duration.seconds(1.0)).apply {
                setOnFinished {
                    offlineProgress.isVisible = false
                    offlineButton.isVisible = true
                    hide()
                }
            }.play()
        }
    }
}

/** main/modals/download_warn_pane.fxml */
class DownloadWarnModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var downloadWarnPane: AnchorPane
    @FXML lateinit var downloadWarnYes: Button
    @FXML lateinit var downloadWarnNo: Button
    override val layer: Node get() = downloadWarnPane

    @FXML fun initialize() = wire(downloadWarnYes, downloadWarnNo)
}

/** main/modals/defender_pane.fxml */
class DefenderModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var defenderPane: AnchorPane
    @FXML lateinit var defenderTitle: Label
    @FXML lateinit var defenderDesc: Label
    @FXML lateinit var defenderYes: Button
    @FXML lateinit var defenderNo: Button
    @FXML lateinit var defenderRemember: CheckBox
    override val layer: Node get() = defenderPane

    @FXML
    fun initialize() {
        wire(defenderYes, defenderNo)
        defenderDesc.text = ctx.t("main.modal.defender.desc", "C:\\Games\\EXBO")
    }
}

/** main/modals/exception_pane.fxml - also the simple in-window error dialog of the fork. */
class ExceptionModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var exceptionPane: AnchorPane
    @FXML lateinit var exceptionTitle: Label
    @FXML lateinit var exceptionDesc: Label
    @FXML lateinit var exceptionButton: Button
    override val layer: Node get() = exceptionPane

    @FXML fun initialize() = wire(exceptionButton, null)

    fun show(title: String, description: String) {
        exceptionTitle.text = title
        exceptionDesc.text = description
        show()
    }
}

/** main/modals/loading_pane.fxml */
class LoadingModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var loadingPane: BorderPane
    @FXML lateinit var loadingIndicator: ProgressIndicator
    override val layer: Node get() = loadingPane
}

/**
 * main/modals/deletion_pane.fxml: flow layout like logout; Esc = cancel, focus starts on Cancel ([deletionNo]).
 * Also the confirmation of every destructive game action ([ask]: reinstall, delete, verify of a running game).
 */
class DeletionModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var deletionPane: AnchorPane
    @FXML lateinit var deletionCard: VBox
    @FXML lateinit var deletionTitle: Label
    @FXML lateinit var deletionDesc: Label
    @FXML lateinit var deletionYes: Button
    @FXML lateinit var deletionNo: Button
    override val layer: Node get() = deletionPane
    override val initialFocus: Node get() = deletionNo

    @FXML fun initialize() = wire(deletionYes, deletionNo)

    /**
     * Shows the confirmation with these texts; [action] runs only on the red confirm button. Cancel / Esc run nothing
     * (the previous action is dropped, so a later confirm can never run a stale one).
     */
    fun ask(title: String, text: String, confirm: String, cancel: String, action: () -> Unit) {
        val locale = java.util.Locale.forLanguageTag(ctx.lang)
        deletionTitle.text = title
        deletionDesc.text = text
        deletionYes.text = confirm.uppercase(locale)
        deletionNo.text = cancel.uppercase(locale)
        onConfirm = { onConfirm = {}; action() }
        onCancel = { onConfirm = {} }
        show()
    }
}

/** main/modals/install_pane.fxml */
class InstallModalController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var installPane: AnchorPane
    @FXML lateinit var installTitle: Label
    @FXML lateinit var installDir: TextField
    @FXML lateinit var installDirLabel: Label
    @FXML lateinit var installLinkCheckBox: CheckBox
    @FXML lateinit var installChangeFolder: Button
    @FXML lateinit var installYes: Button
    @FXML lateinit var installNo: Button
    override val layer: Node get() = installPane

    @FXML
    fun initialize() {
        wire(installYes, installNo)
        installChangeFolder.text = installChangeFolder.text.uppercase(java.util.Locale.forLanguageTag(ctx.lang))
    }

    fun show(path: String, requiredBytes: String, freeBytes: String) {
        installDir.text = path
        installDirLabel.text = ctx.t("main.modal.install.text", requiredBytes, freeBytes)
        show()
    }
}
