package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.SegmentedBar
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlin.math.roundToInt

/**
 * settings.fxml: static layout (sections ИГРА / ОБНОВЛЕНИЕ / ПРОЧЕЕ) bound to [dev.launcherfork.domain.SettingsStore].
 * Every change is saved immediately. The language radio rebuilds the main screen via the navigator.
 */
class SettingsController(private val ctx: UiContext) {
    @FXML lateinit var settingsRoot: AnchorPane
    @FXML lateinit var scroll: ScrollPane
    @FXML lateinit var settingsContent: VBox
    @FXML lateinit var gameHeading: Label
    @FXML lateinit var updatesHeading: Label
    @FXML lateinit var othersHeading: Label

    @FXML lateinit var ramBar: SegmentedBar
    @FXML lateinit var exitGroup: ToggleGroup
    @FXML lateinit var exitOnStart: RadioButton
    @FXML lateinit var exitOnGameExit: RadioButton
    @FXML lateinit var exitNever: RadioButton

    @FXML lateinit var downloadLimitField: TextField
    @FXML lateinit var uploadLimitField: TextField
    @FXML lateinit var p2pGroup: ToggleGroup
    @FXML lateinit var p2pAlways: RadioButton
    @FXML lateinit var p2pNotMobile: RadioButton
    @FXML lateinit var p2pNever: RadioButton
    @FXML lateinit var defenderRow: HBox
    @FXML lateinit var defenderToggle: CheckBox
    @FXML lateinit var startOnDownloadedRow: HBox
    @FXML lateinit var startOnDownloadedToggle: CheckBox

    @FXML lateinit var trayRow: HBox
    @FXML lateinit var trayToggle: CheckBox
    @FXML lateinit var autostartRow: HBox
    @FXML lateinit var autostartToggle: CheckBox
    @FXML lateinit var languageGroup: ToggleGroup
    @FXML lateinit var langRu: RadioButton
    @FXML lateinit var langEn: RadioButton

    private val store get() = ctx.services.settings

    @FXML
    fun initialize() {
        val s = store.load()

        ramBar.formatter = { "${heapMb(it)}MB" }
        ramBar.value = s.ramFraction
        ramBar.onChange = { v -> update { it.copy(ramFraction = v) } }

        bindRadios(exitGroup, mapOf(exitOnStart to ExitMode.ON_GAME_START, exitOnGameExit to ExitMode.ON_GAME_EXIT, exitNever to ExitMode.NEVER), s.exitMode) { v ->
            update { it.copy(exitMode = v) }
        }
        bindRadios(p2pGroup, mapOf(p2pAlways to P2pMode.ALWAYS, p2pNotMobile to P2pMode.EXCEPT_MOBILE, p2pNever to P2pMode.NEVER), s.p2pMode) { v ->
            update { it.copy(p2pMode = v) }
        }

        bindLimit(downloadLimitField, s.downloadLimitKb) { v -> update { it.copy(downloadLimitKb = v) } }
        bindLimit(uploadLimitField, s.uploadLimitKb) { v -> update { it.copy(uploadLimitKb = v) } }

        bindToggle(defenderRow, defenderToggle, s.defenderHint) { v -> update { it.copy(defenderHint = v) } }
        bindToggle(startOnDownloadedRow, startOnDownloadedToggle, s.startOnDownloaded) { v -> update { it.copy(startOnDownloaded = v) } }
        bindToggle(trayRow, trayToggle, s.hideToTray) { v -> update { it.copy(hideToTray = v) } }
        bindToggle(autostartRow, autostartToggle, s.runOnStartUp) { v -> update { it.copy(runOnStartUp = v) } }

        bindRadios(languageGroup, mapOf(langRu to "ru", langEn to "en"), if (ctx.lang == "en") "en" else "ru") { lang ->
            update { it.copy(language = lang) }
            ctx.navigator.switchLanguage(lang)
        }
    }

    /** RAM fraction -> MB shown in the bar (same mapping as before the redesign). */
    fun heapMb(fraction: Double): Int = (3584 * fraction).roundToInt() + 512

    private fun update(change: (LauncherSettings) -> LauncherSettings) = store.save(change(store.load()))

    private fun <T> bindRadios(group: ToggleGroup, options: Map<RadioButton, T>, selected: T, onChange: (T) -> Unit) {
        options.forEach { (button, value) ->
            button.userData = value
            button.isSelected = value == selected
        }
        group.selectedToggleProperty().addListener { _, old, new ->
            if (new == null) {
                old?.isSelected = true // radio groups never become empty
                return@addListener
            }
            @Suppress("UNCHECKED_CAST")
            onChange(new.userData as T)
        }
    }

    private fun bindToggle(row: HBox, toggle: CheckBox, value: Boolean, onChange: (Boolean) -> Unit) {
        toggle.isSelected = value
        toggle.selectedProperty().addListener { _, _, v -> onChange(v) }
        // the whole row is clickable, like on the reference
        row.setOnMouseClicked { e -> if (e.target !== toggle && !toggle.isHover) toggle.isSelected = !toggle.isSelected }
    }

    /** Digits only (max 7), empty means 0; saved on every edit. */
    private fun bindLimit(field: TextField, value: Int, onChange: (Int) -> Unit) {
        field.textFormatter = TextFormatter<String> { c -> if (c.controlNewText.matches(Regex("\\d{0,7}"))) c else null }
        field.text = value.toString()
        field.textProperty().addListener { _, _, t -> onChange(t.toIntOrNull() ?: 0) }
        field.focusedProperty().addListener { _, _, focused -> if (!focused && field.text.isEmpty()) field.text = "0" }
    }
}
