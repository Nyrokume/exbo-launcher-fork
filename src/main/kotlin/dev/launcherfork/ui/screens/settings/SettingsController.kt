package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.RamBar
import dev.launcherfork.ui.components.RamScale
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.control.Tooltip
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

/**
 * settings.fxml: one-screen list (groups ОПЕРАТИВНАЯ ПАМЯТЬ / ПОВЕДЕНИЕ / ОБНОВЛЕНИЕ / ЯЗЫК) bound to [dev.launcherfork.domain.SettingsStore].
 * Every change is saved immediately. The language radio rebuilds the main screen via the navigator.
 */
class SettingsController(private val ctx: UiContext) {
    @FXML lateinit var settingsRoot: AnchorPane
    @FXML lateinit var scroll: ScrollPane
    @FXML lateinit var settingsContent: VBox
    @FXML lateinit var gameSection: VBox
    @FXML lateinit var updatesSection: VBox
    @FXML lateinit var languageSection: VBox
    @FXML lateinit var behaviourSection: VBox
    @FXML lateinit var ramCard: VBox
    @FXML lateinit var behaviourCard: VBox
    @FXML lateinit var exitRow: HBox
    @FXML lateinit var p2pRow: HBox
    @FXML lateinit var limitsRow: HBox
    @FXML lateinit var languageRow: HBox
    @FXML lateinit var exitLabel: Label
    @FXML lateinit var p2pLabel: Label
    @FXML lateinit var columns: HBox
    @FXML lateinit var gameHeading: Label
    @FXML lateinit var updatesHeading: Label
    @FXML lateinit var languageHeading: Label
    @FXML lateinit var behaviourHeading: Label

    @FXML lateinit var ramBar: RamBar
    @FXML lateinit var ramSummary: Label
    @FXML lateinit var ramWarning: Label
    @FXML lateinit var ramScale: RamScale
    @FXML lateinit var exitGroup: ToggleGroup
    @FXML lateinit var exitOnStart: RadioButton
    @FXML lateinit var exitOnGameExit: RadioButton
    @FXML lateinit var exitNever: RadioButton

    @FXML lateinit var downloadLimitField: TextField
    @FXML lateinit var uploadLimitField: TextField
    @FXML lateinit var downloadLimitBox: HBox
    @FXML lateinit var uploadLimitBox: HBox
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

        bindRam(s)

        bindRadios(exitGroup, mapOf(exitOnStart to ExitMode.ON_GAME_START, exitOnGameExit to ExitMode.ON_GAME_EXIT, exitNever to ExitMode.NEVER), s.exitMode) { v ->
            update { it.copy(exitMode = v) }
        }
        bindRadios(p2pGroup, mapOf(p2pAlways to P2pMode.ALWAYS, p2pNotMobile to P2pMode.EXCEPT_MOBILE, p2pNever to P2pMode.NEVER), s.p2pMode) { v ->
            update { it.copy(p2pMode = v) }
        }

        // the rows of ПОВЕДЕНИЕ carry short captions; the original long wording is the tooltip
        exitLabel.tooltip = Tooltip(ctx.t("settings.exit"))
        p2pLabel.tooltip = Tooltip(ctx.t("settings.p2p"))

        Tooltip.install(limitsRow, Tooltip(ctx.t("settings.speed_limit.note")))
        bindLimit(downloadLimitBox, downloadLimitField, "settings.limit.download", s.downloadLimitKb) { v -> update { it.copy(downloadLimitKb = v) } }
        bindLimit(uploadLimitBox, uploadLimitField, "settings.limit.upload", s.uploadLimitKb) { v -> update { it.copy(uploadLimitKb = v) } }

        bindToggle(defenderRow, defenderToggle, s.defenderHint) { v -> update { it.copy(defenderHint = v) } }
        bindToggle(startOnDownloadedRow, startOnDownloadedToggle, s.startOnDownloaded) { v -> update { it.copy(startOnDownloaded = v) } }
        bindToggle(trayRow, trayToggle, s.hideToTray) { v -> update { it.copy(hideToTray = v) } }
        bindToggle(autostartRow, autostartToggle, s.runOnStartUp) { v -> update { it.copy(runOnStartUp = v) } }

        bindRadios(languageGroup, mapOf(langRu to "ru", langEn to "en"), if (ctx.lang == "en") "en" else "ru") { lang ->
            update { it.copy(language = lang) }
            ctx.navigator.switchLanguage(lang)
        }
    }

    /** Total memory of the computer, MB (SystemInfo; the mock reports 16 GB). */
    val totalRamMb: Long get() = ctx.services.system.totalRamMb()

    /**
     * RAM bar (segmented like the original launcher): one segment per GB of the computer's memory, value = stored MB
     * (or the legacy 0..1 fraction) rounded to whole GB and clamped to [RamOptions.MIN_GB]..[RamOptions.maxGb]. The
     * line above it, "Выделено: N ГБ из M ГБ", follows the bar's value; the captions under it ([RamScale]) name the min
     * and max; the recommended value is in the tooltip. A move is stored as MB and drops the legacy fraction.
     */
    private fun bindRam(s: LauncherSettings) {
        val total = totalRamMb
        val unit = ctx.t("settings.unit.gb")
        val fmt = { gb: Int -> RamOptions.format(gb, unit) }
        ramBar.formatter = fmt
        ramBar.configure(
            totalGb = RamOptions.maxGb(total), // the scale is the selectable range: no unreachable segments
            minGb = RamOptions.MIN_GB,
            maxGb = RamOptions.maxGb(total),
            recommendedGb = RamOptions.recommended(total),
            value = RamOptions.selectedGb(s, total),
        )
        // the selectable range under the bar: «2 ГБ» under the first, «16 ГБ» under the last segment (whole GB of memory)
        ramScale.setCaptions(fmt(ramBar.minGb), fmt(ramBar.maxGb))
        ramScale.bar = ramBar
        val hint = ctx.t("settings.ram.hint", fmt(ramBar.recommendedGb))
        val tooltip = Tooltip(hint)
        Tooltip.install(ramBar, tooltip)
        fun summary(gb: Int) = ctx.t("settings.ram.summary", fmt(gb), fmt(RamOptions.totalGb(total)))
        // soft note next to the summary when the OS keeps less than 2 GB; always managed (only hidden), so no layout shift
        ramWarning.text = ctx.t("settings.ram.low_system")
        fun show(gb: Int) {
            ramSummary.text = summary(gb)
            val low = RamOptions.leavesLittleForSystem(gb, total)
            ramWarning.isVisible = low
            // screen readers and the tooltip get the note too, only while it is shown
            val help = if (low) ctx.t("settings.ram.hint_low_system", hint, ramWarning.text) else hint
            ramBar.accessibleHelp = help
            tooltip.text = help
        }
        show(ramBar.value)
        // the labels always follow the bar (user moves and programmatic changes alike)
        ramBar.valueProperty.addListener { _, _, gb -> show(gb.toInt()) }
        ramBar.onChange = { gb -> update { it.copy(ramMb = gb * RamOptions.MB_PER_GB, ramFraction = null) } }
    }

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

    /** Digits only (max 7), empty means 0; saved on every edit. "Загрузка · КБ/с, 0 — без ограничений" as a tooltip. */
    private fun bindLimit(box: HBox, field: TextField, captionKey: String, value: Int, onChange: (Int) -> Unit) {
        Tooltip.install(box, Tooltip("${ctx.t(captionKey)} · ${ctx.t("settings.speed_limit.note")}"))
        field.textFormatter = TextFormatter<String> { c -> if (c.controlNewText.matches(Regex("\\d{0,7}"))) c else null }
        field.text = value.toString()
        field.textProperty().addListener { _, _, t -> onChange(t.toIntOrNull() ?: 0) }
        field.focusedProperty().addListener { _, _, focused -> if (!focused && field.text.isEmpty()) field.text = "0" }
    }
}
