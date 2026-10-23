package dev.launcherfork.ui.screens.stalzone

import dev.launcherfork.stalzone.ExboSession
import dev.launcherfork.stalzone.JvmProfile
import dev.launcherfork.stalzone.ScopeSens
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.screens.main.modals.ModalController
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.VBox

/**
 * Profile-menu dialog for the JVM profile, saved EXBO sessions and the sensitivity table.
 * Buttons are the only writers. Opening the dialog only reads.
 */
class StalzoneDeskController(ctx: UiContext) : ModalController(ctx) {
    @FXML lateinit var deskRoot: AnchorPane
    @FXML lateinit var deskTitle: Label
    @FXML lateinit var jvmGroup: VBox
    @FXML lateinit var accountsGroup: VBox
    @FXML lateinit var sensGroup: VBox
    @FXML lateinit var closeDesk: Button
    @FXML lateinit var jvmSummary: Label
    @FXML lateinit var activateJvm: Button
    @FXML lateinit var sessions: ListView<ExboSession>
    @FXML lateinit var sessionName: TextField
    @FXML lateinit var captureSession: Button
    @FXML lateinit var applySession: Button
    @FXML lateinit var deleteSession: Button
    @FXML lateinit var baseSens: TextField
    @FXML lateinit var calcSens: Button
    @FXML lateinit var writeSens: Button
    @FXML lateinit var sensTable: Label
    @FXML lateinit var status: Label

    override val layer: Node get() = deskRoot
    override val initialFocus: Node get() = closeDesk

    private val desk = ctx.desk

    @FXML
    fun initialize() {
        sessions.setCellFactory {
            object : javafx.scene.control.ListCell<ExboSession>() {
                override fun updateItem(item: ExboSession?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) null else item.name
                }
            }
        }
        wire(null, closeDesk)
        activateJvm.setOnAction {
            runAction {
                desk.activateTunedProfile()
                val gb = ctx.services.settings.load().ramMb?.div(1024)
                if (gb != null) desk.syncHeap(gb)
                ctx.t("stalzone.jvm.done")
            }
        }
        captureSession.setOnAction {
            runAction {
                desk.captureCurrent(sessionName.text.trim())
                sessionName.clear()
                ctx.t("stalzone.accounts.saved")
            }
        }
        applySession.setOnAction {
            val selected = sessions.selectionModel.selectedItem
            if (selected == null) status.text = ctx.t("stalzone.accounts.pick")
            else runAction { desk.apply(selected); ctx.t("stalzone.accounts.applied", selected.name) }
        }
        deleteSession.setOnAction {
            val selected = sessions.selectionModel.selectedItem ?: return@setOnAction
            runAction { desk.delete(selected.id); ctx.t("stalzone.accounts.removed") }
        }
        calcSens.setOnAction { showTable(parseBase()) }
        writeSens.setOnAction {
            val base = parseBase() ?: return@setOnAction
            runAction {
                val values = desk.writeSensitivity(base)
                sensTable.text = formatTable(base, values)
                ctx.t("stalzone.sens.written")
            }
        }
    }

    fun openJvm() = open("jvm", ctx.t("stalzone.jvm.heading"))

    fun openAccounts() = open("accounts", ctx.t("stalzone.accounts.heading"))

    fun openSens() = open("sens", ctx.t("stalzone.sens.heading"))

    private fun open(section: String, title: String) {
        fun show(group: VBox, on: Boolean) {
            group.isVisible = on
            group.isManaged = on
        }
        show(jvmGroup, section == "jvm")
        show(accountsGroup, section == "accounts")
        show(sensGroup, section == "sens")
        deskTitle.text = title
        refresh()
        show()
    }

    fun refresh() {
        try {
            val active = desk.activeProfile() ?: "—"
            jvmSummary.text = ctx.t("stalzone.jvm.summary", JvmProfile.RECOMMENDED, active)
            val items = desk.sessions()
            sessions.items.setAll(items)
        } catch (e: Exception) {
            status.text = e.message ?: e.javaClass.simpleName
        }
    }

    private fun parseBase(): Double? {
        val raw = baseSens.text.trim().replace(',', '.')
        val value = raw.toDoubleOrNull()
        if (value == null || value <= 0.0) {
            status.text = ctx.t("stalzone.sens.bad")
            return null
        }
        return value
    }

    private fun showTable(base: Double?) {
        if (base == null) return
        sensTable.text = formatTable(base, ScopeSens.snapshot(base))
        status.text = ctx.t("stalzone.sens.ready")
    }

    private fun formatTable(base: Double, values: Map<Double, Double>): String =
        values.entries.joinToString("   ") { (mag, v) -> "${ScopeSens.magKey(mag)}x ${"%.2f".format(v)}" }
            .let { ctx.t("stalzone.sens.base_line", "%.2f".format(base)) + "\n" + it }

    private fun runAction(block: () -> String) {
        try {
            status.text = block()
            refresh()
        } catch (e: Exception) {
            status.text = e.message ?: e.javaClass.simpleName
        }
    }
}
