package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.brightenOnHover
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.ui.screens.settings.entries.CheckBoxEntryController
import dev.launcherfork.ui.screens.settings.entries.NumericEntryController
import dev.launcherfork.ui.screens.settings.entries.PathEntryController
import dev.launcherfork.ui.screens.settings.entries.ProgressionEntryController
import dev.launcherfork.ui.screens.settings.entries.RadioEntryController
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane

/**
 * settings.fxml: categories with entries, laid out with the same offsets as the original
 * SettingsController (title x=14, lines 407px, entries x=20). Values come from [dev.launcherfork.domain.SettingsStore].
 */
class SettingsController(private val ctx: UiContext) {
    @FXML lateinit var settingsRoot: AnchorPane
    @FXML lateinit var scroll: ScrollPane

    var onNotAvailable: () -> Unit = {}
    private val store get() = ctx.services.settings

    @FXML
    fun initialize() {
        rebuild()
    }

    private fun update(change: (LauncherSettings) -> LauncherSettings) = store.save(change(store.load()))

    private fun rebuild() {
        val s = store.load()
        val categories = listOf(
            ctx.t("settings.category.game") to listOf(
                progression(ctx.t("settings.ram"), s.ramFraction, { "${heapMb(it)} MB" }) { v -> update { it.copy(ramFraction = v) } },
                radio(ctx.t("settings.exit"), listOf("settings.exit.game_start", "settings.exit.game_exit", "settings.exit.never").map(ctx::t), s.exitMode) { v -> update { it.copy(exitMode = v) } },
                checkBox(ctx.t("settings.start_on_downloaded"), s.startOnDownloaded) { v -> update { it.copy(startOnDownloaded = v) } },
                path(ctx.t("settings.choose_root"), s.gameRoot),
            ),
            ctx.t("settings.category.updates") to listOf(
                radio(ctx.t("settings.p2p"), listOf("settings.p2p.always", "settings.p2p.not_mobile", "settings.p2p.never").map(ctx::t), s.p2pMode) { v -> update { it.copy(p2pMode = v) } },
                numeric(ctx.t("settings.download_limit"), s.downloadLimitKb) { v -> update { it.copy(downloadLimitKb = v) } },
                numeric(ctx.t("settings.upload_limit"), s.uploadLimitKb) { v -> update { it.copy(uploadLimitKb = v) } },
                checkBox(ctx.t("settings.defender"), s.defenderHint) { v -> update { it.copy(defenderHint = v) } },
            ),
            ctx.t("settings.category.others") to listOf(
                checkBox(ctx.t("settings.run_on_start"), s.runOnStartUp) { v -> update { it.copy(runOnStartUp = v) } },
                checkBox(ctx.t("settings.tray"), s.hideToTray) { v -> update { it.copy(hideToTray = v) } },
                radio(ctx.t("fork.settings.language"), listOf("Русский", "English"), if (ctx.lang == "en") 1 else 0) { v ->
                    val lang = if (v == 1) "en" else "ru"
                    update { it.copy(language = lang) }
                    ctx.navigator.switchLanguage(lang)
                },
            ),
        )

        val nodes = mutableListOf<Node>()
        var y = -4.0
        for ((name, entries) in categories) {
            nodes += Label(name).apply { styleClass += listOf("settings-category", "font-display"); layoutX = 14.0; layoutY = y }
            y += 32
            nodes += line("line_settings_main.png", y)
            y += 7
            entries.forEachIndexed { i, node ->
                if (i > 0) {
                    nodes += line("line_settings_group.png", y)
                    y += 1
                }
                node.layoutX = 20.0
                node.layoutY = y
                y += node.prefHeight(500.0)
                nodes += node
            }
            y += 24
        }
        nodes += Button(ctx.t("main.open_logs")).apply {
            styleClass.setAll("logs-button")
            setPrefSize(318.0, 46.0)
            layoutX = 20.0
            layoutY = y
            brightenOnHover()
            setOnAction { onNotAvailable() }
        }
        scroll.content = Pane().apply { children.setAll(nodes); setPrefSize(520.0, y + 60) }
    }

    private fun line(image: String, y: Double) = ImageView(loadImage(Theme.IMAGE_ROOT + image)).apply {
        fitHeight = 1.0; fitWidth = 407.0; layoutX = 14.0; layoutY = y
    }

    private fun heapMb(v: Double): Int = (3584 * v).toInt() + 512

    private fun <C> entry(path: String, init: (C) -> Unit): Parent =
        Fxml.load<C>(ctx, path).also { init(it.controller) }.root

    private fun checkBox(caption: String, value: Boolean, onChange: (Boolean) -> Unit) =
        entry<CheckBoxEntryController>("settings/check_box_entry.fxml") { it.bind(caption, value, onChange) }

    private fun numeric(caption: String, value: Int, onChange: (Int) -> Unit) =
        entry<NumericEntryController>("settings/numeric_entry.fxml") { it.bind(caption, value, onChange) }

    private fun progression(caption: String, value: Double, format: (Double) -> String, onChange: (Double) -> Unit) =
        entry<ProgressionEntryController>("settings/progression_entry.fxml") { it.bind(caption, value, format, onChange) }

    private fun radio(caption: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) =
        entry<RadioEntryController>("settings/radio_entry.fxml") { it.bind(caption, options, selected, onChange) }

    private fun path(caption: String, value: String) =
        entry<PathEntryController>("settings/path_entry.fxml") { it.bind(caption, value); it.onChooseFolder = { onNotAvailable() } }
}
