package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.RamBar
import dev.launcherfork.ui.components.RamScale
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.motion.Motion
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import dev.launcherfork.stalzone.ExboSession
import dev.launcherfork.stalzone.ExboSessions
import dev.launcherfork.stalzone.GameOptions
import dev.launcherfork.stalzone.StalzonePaths
import dev.launcherfork.stalzone.JvmPresets
import dev.launcherfork.stalzone.ScopeSens
import dev.launcherfork.stalzone.TunnelRegion
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.RadioButton
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.control.ToggleGroup
import javafx.scene.control.OverrunStyle
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.Locale

/**
 * settings.fxml, bound to [dev.launcherfork.domain.SettingsStore].
 * Память, Поведение, Обновление and Язык are one scrollable column.
 * The header switch opens JVM, the tunnel, sensitivity, account swap and localization. The tunnel page fills the
 * settings viewport and scrolls the server list inside itself. Sensitivity fills that same viewport:
 * a compact base row, then one game-style line per scope (name, value, slider, steps). Accounts fills it too:
 * the name row is pinned under the heading and the session list takes the remaining height. Localization fills it
 * the same way: a filter row, then one line per item or weapon. The profile menu opens accounts.
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
    @FXML lateinit var gameHeading: Label
    @FXML lateinit var updatesHeading: Label
    @FXML lateinit var languageHeading: Label
    @FXML lateinit var behaviourHeading: Label

    @FXML lateinit var ramBar: RamBar
    @FXML lateinit var unlockSection: VBox
    @FXML lateinit var unlockHeading: Label
    @FXML lateinit var unlockFovRow: HBox
    @FXML lateinit var unlockFovBox: HBox
    @FXML lateinit var unlockFovField: TextField
    @FXML lateinit var unlockShadowsRow: HBox
    @FXML lateinit var unlockShadowsToggle: CheckBox
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

    @FXML lateinit var animationsRow: HBox
    @FXML lateinit var animationsToggle: CheckBox
    @FXML lateinit var trayRow: HBox
    @FXML lateinit var trayToggle: CheckBox
    @FXML lateinit var autostartRow: HBox
    @FXML lateinit var autostartToggle: CheckBox
    @FXML lateinit var launcherPage: VBox
    @FXML lateinit var clientContent: VBox
    @FXML lateinit var jvmPage: VBox
    @FXML lateinit var tunnelPage: VBox
    @FXML lateinit var tunnelScroll: ScrollPane
    @FXML lateinit var accountsPage: VBox
    @FXML lateinit var sensPage: VBox
    @FXML lateinit var locPage: VBox
    @FXML lateinit var locCard: VBox
    @FXML lateinit var locBar: HBox
    @FXML lateinit var locWeapons: RadioButton
    @FXML lateinit var locItems: RadioButton
    @FXML lateinit var locChanged: RadioButton
    @FXML lateinit var locCategory: ComboBox<LocCategoryOption>
    @FXML lateinit var locSearch: TextField
    @FXML lateinit var locWrite: Button
    @FXML lateinit var locList: ListView<LocLine>
    @FXML lateinit var locNote: Label
    @FXML lateinit var locKindGroup: ToggleGroup
    @FXML lateinit var jvmSummary: VBox
    @FXML lateinit var jvmStatusKicker: Label
    @FXML lateinit var jvmStatus: Label
    @FXML lateinit var jvmStatusHint: Label
    @FXML lateinit var jvmMachine: Label
    @FXML lateinit var jvmGrid: GridPane
    @FXML lateinit var regionGroup: ToggleGroup
    @FXML lateinit var regionRu: RadioButton
    @FXML lateinit var regionEu: RadioButton
    @FXML lateinit var regionNa: RadioButton
    @FXML lateinit var regionSea: RadioButton
    @FXML lateinit var regionNea: RadioButton
    @FXML lateinit var tunnelPools: VBox
    @FXML lateinit var tunnelMeasure: Button
    @FXML lateinit var tunnelFindBest: Button
    @FXML lateinit var tunnelRefresh: Button
    @FXML lateinit var tunnelMode: VBox
    @FXML lateinit var sessionScroll: ScrollPane
    @FXML lateinit var sessionCards: VBox
    @FXML lateinit var accountsRow: HBox
    @FXML lateinit var sessionName: TextField
    @FXML lateinit var captureSession: Button
    @FXML lateinit var baseSens: TextField
    @FXML lateinit var calcSens: Button
    @FXML lateinit var writeSens: Button
    @FXML lateinit var sensScroll: ScrollPane
    @FXML lateinit var sensList: VBox
    @FXML lateinit var sensNote: Label
    @FXML lateinit var clientStatus: Label
    @FXML lateinit var languageGroup: ToggleGroup
    @FXML lateinit var langRu: RadioButton
    @FXML lateinit var langEn: RadioButton

    private val store get() = ctx.services.settings

    /**
     * Section last asked for. The gear's home is the whole column (`ram`, and the other column ids).
     * `jvm`, `tunnel`, `sens`, `accounts` and `loc` are the pages with a nav button.
     */
    var currentSection: String = "ram"
        private set

    /** Fired after [showSection], including the initial column. MainController paints the nav accent from this. */
    var onSection: (String) -> Unit = {}

    /**
     * Fired after a session apply switches the signed-in login.
     * [reloadCatalog] is false for the live optimistic paint (token is still the previous account).
     */
    var onAccountSwitched: (login: String?, reloadCatalog: Boolean) -> Unit = { _, _ -> }

    @FXML
    fun initialize() {
        val s = store.load()

        bindRam(s)
        bindUnlock(s)

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

        // «Анимации»: interface motion (ui/motion/Motion), saved and applied at once; -Dlauncher.animations wins, then the
        // switch shows the forced value, disabled, with the reason as a tooltip
        bindToggle(animationsRow, animationsToggle, Motion.enabled) { v ->
            update { it.copy(animations = v) }
            Motion.setting = v
        }
        if (Motion.override != null) {
            animationsToggle.isDisable = true
            Tooltip.install(animationsRow, Tooltip(ctx.t("settings.animations.forced")))
        }
        bindToggle(defenderRow, defenderToggle, s.defenderHint) { v -> update { it.copy(defenderHint = v) } }
        bindToggle(startOnDownloadedRow, startOnDownloadedToggle, s.startOnDownloaded) { v -> update { it.copy(startOnDownloaded = v) } }
        bindToggle(trayRow, trayToggle, s.hideToTray) { v -> update { it.copy(hideToTray = v) } }
        bindToggle(autostartRow, autostartToggle, s.runOnStartUp) { v -> update { it.copy(runOnStartUp = v) } }
        bindClient()
        if (ctx.liveBackend) {
            val locked = ctx.t("settings.live.locked")
            autostartToggle.isDisable = true
            defenderToggle.isDisable = true
            Tooltip.install(autostartRow, Tooltip(locked))
            Tooltip.install(defenderRow, Tooltip(locked))
        }

        bindRadios(languageGroup, mapOf(langRu to "ru", langEn to "en"), if (ctx.lang == "en") "en" else "ru") { lang ->
            update { it.copy(language = lang) }
            ctx.navigator.switchLanguage(lang)
        }
        clipToBounds(sessionScroll)
        clipToBounds(sensScroll)
        sessionCards.minHeight = Region.USE_PREF_SIZE
        scroll.viewportBoundsProperty().addListener { _, _, _ -> applyPageFill() }
        showSection("ram")
    }

    /** Total memory of the computer, MB (SystemInfo; the mock reports 16 GB). */
    val totalRamMb: Long get() = ctx.services.system.totalRamMb()

    /**
     * RAM bar (segmented like the original launcher): one segment per selectable GB, value = stored MB
     * (or the legacy 0..1 fraction) rounded to whole GB and clamped to [RamOptions.MIN_GB]..[RamOptions.maxGb].
     * The ceiling is the machine's memory, and never above [RamOptions.ALLOCATION_CAP_GB]. The line above it,
     * "Выделено: N ГБ из M ГБ", follows the bar; the captions under it ([RamScale]) name the min and max;
     * the recommended value is in the tooltip. A move is stored as MB and drops the legacy fraction.
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
        // the selectable range under the bar: «2 ГБ» under the first, the ceiling under the last segment
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
        ramBar.onChange = { gb ->
            update { it.copy(ramMb = gb * RamOptions.MB_PER_GB, ramFraction = null) }
            refreshPresetHeapLabels()
        }
    }

    private fun bindUnlock(s: LauncherSettings) {
        Tooltip.install(unlockFovRow, Tooltip(ctx.t("settings.unlock.fov.hint")))
        Tooltip.install(unlockShadowsRow, Tooltip(ctx.t("settings.unlock.shadows.hint")))
        unlockFovField.textFormatter = TextFormatter<String> { c ->
            if (c.controlNewText.matches(Regex("\\d{0,3}"))) c else null
        }
        fun showFov(value: Double) {
            unlockFovField.text = value.toInt().toString()
        }
        showFov(GameOptions.clampFov(s.gameFov))
        fun commitFov() {
            val next = GameOptions.clampFov(unlockFovField.text.toDoubleOrNull() ?: GameOptions.DEFAULT_FOV)
            showFov(next)
            update { it.copy(gameFov = next) }
            writeGameUnlock()
        }
        unlockFovField.setOnAction { commitFov() }
        unlockFovField.focusedProperty().addListener { _, _, focused ->
            if (!focused) commitFov()
        }
        bindToggle(unlockShadowsRow, unlockShadowsToggle, s.gameShadowsOff) { v ->
            update { it.copy(gameShadowsOff = v) }
            writeGameUnlock()
        }
    }

    private fun writeGameUnlock() {
        if (!ctx.liveBackend) return
        val s = store.load()
        runCatching { GameOptions.applyFile(StalzonePaths.optionsFile(), s.gameFov, s.gameShadowsOff) }
    }

    /** Gear home is the settings column. `true` opens JVM. */
    fun showClient(client: Boolean = true) = showSection(if (client) "jvm" else "ram")

    /**
     * `ram`, `behaviour`, `updates` and `language` are the one column (a named id scrolls that block into view).
     * `jvm`, `tunnel`, `sens`, `accounts` and `loc` each replace the column. Tunnel, sensitivity, accounts
     * and localization fill the viewport; their lists scroll inside the page.
     */
    fun showClientSection(section: String) = showSection(section)

    fun showSection(section: String) {
        val id = when (section) {
            in columnSections, in toolSections -> section
            else -> "ram"
        }
        currentSection = id
        val column = id in columnSections
        setShown(launcherPage, column)
        setShown(clientContent, !column)
        columnNodes.forEach { setShown(it, column) }
        toolNodes.forEach { (key, node) -> setShown(node, !column && key == id) }
        if (column && id != "ram") reveal(columnTarget(id)) else scroll.vvalue = 0.0
        placeNote()
        applyPageFill()
        if (id == "tunnel" && ::tunnels.isInitialized) tunnels.onShown()
        if (id == "loc" && ::names.isInitialized) names.onShown()
        onSection(id)
    }

    /** True when the settings column is open (the gear closes settings from here). */
    fun atTop(): Boolean = currentSection in columnSections

    /** Blocks of the one settings column. JVM, tunnel, sensitivity, account swap and localization are separate pages. */
    private val columnSections = setOf("ram", "behaviour", "updates", "language")

    private val toolSections = setOf("jvm", "tunnel", "sens", "accounts", "loc")

    private val columnNodes: List<Node>
        get() = listOf(gameSection, behaviourSection, updatesSection, languageSection)

    private val toolNodes: Map<String, Node>
        get() = mapOf(
            "jvm" to jvmPage,
            "tunnel" to tunnelPage,
            "sens" to sensPage,
            "accounts" to accountsPage,
            "loc" to locPage,
        )

    private fun columnTarget(id: String): Node = when (id) {
        "behaviour" -> behaviourSection
        "updates" -> updatesSection
        "language" -> languageSection
        else -> gameSection
    }

    /**
     * Tunnel, sensitivity, accounts and localization occupy the settings viewport. The pool list scrolls in
     * [tunnelScroll]; the scope lines scroll in [sensScroll]. On accounts the name row stays under the heading and
     * [sessionScroll] takes every pixel under it, so a short list is not a small island in an empty pane. Extra cards
     * scroll inside that list. Localization pins [locList] the same way. A status line, when it is showing, keeps a
     * strip under the list.
     */
    private fun applyPageFill() {
        if (!::tunnelScroll.isInitialized || !::sessionScroll.isInitialized || !::sensPage.isInitialized || !::locPage.isInitialized) return
        val viewport = scroll.viewportBounds.height
        if (viewport <= 1.0) {
            resetHeight(settingsContent)
            resetHeight(tunnelPage)
            resetHeight(accountsPage)
            resetHeight(sensPage)
            resetHeight(locPage)
            resetScroll(tunnelScroll, 120.0)
            resetScroll(sessionScroll, 96.0)
            resetList(locList)
            return
        }
        val pad = settingsContent.padding.top + settingsContent.padding.bottom
        val inner = (viewport - pad).coerceAtLeast(0.0)
        when (currentSection) {
            "tunnel" -> {
                resetHeight(accountsPage)
                resetHeight(sensPage)
                resetHeight(locPage)
                resetScroll(sessionScroll, 96.0)
                resetList(locList)
                settingsContent.minHeight = viewport
                settingsContent.prefHeight = viewport
                settingsContent.maxHeight = viewport
                tunnelPage.minHeight = inner
                tunnelPage.prefHeight = inner
                tunnelPage.maxHeight = inner
                val list = (inner - tunnelChrome()).coerceAtLeast(120.0)
                tunnelScroll.minHeight = 120.0
                tunnelScroll.prefHeight = list
                tunnelScroll.maxHeight = list
            }
            "accounts" -> {
                resetHeight(tunnelPage)
                resetHeight(sensPage)
                resetHeight(locPage)
                resetScroll(tunnelScroll, 120.0)
                resetList(locList)
                settingsContent.minHeight = viewport
                settingsContent.prefHeight = viewport
                settingsContent.maxHeight = viewport
                val note = accountsNoteHeight()
                val page = (inner - note).coerceAtLeast(0.0)
                accountsPage.minHeight = page
                accountsPage.prefHeight = page
                accountsPage.maxHeight = page
                val list = (page - accountsChrome()).coerceAtLeast(96.0)
                sessionScroll.minHeight = 96.0
                sessionScroll.prefHeight = list
                sessionScroll.maxHeight = list
            }
            "sens" -> {
                resetHeight(tunnelPage)
                resetHeight(accountsPage)
                resetHeight(locPage)
                resetScroll(tunnelScroll, 120.0)
                resetScroll(sessionScroll, 96.0)
                resetList(locList)
                settingsContent.minHeight = viewport
                settingsContent.prefHeight = viewport
                settingsContent.maxHeight = viewport
                sensPage.minHeight = inner
                sensPage.prefHeight = inner
                sensPage.maxHeight = inner
            }
            "loc" -> {
                resetHeight(tunnelPage)
                resetHeight(accountsPage)
                resetHeight(sensPage)
                resetScroll(tunnelScroll, 120.0)
                resetScroll(sessionScroll, 96.0)
                settingsContent.minHeight = viewport
                settingsContent.prefHeight = viewport
                settingsContent.maxHeight = viewport
                locPage.minHeight = inner
                locPage.prefHeight = inner
                locPage.maxHeight = inner
                val list = (inner - locChrome()).coerceAtLeast(96.0)
                locList.minHeight = 96.0
                locList.prefHeight = list
                locList.maxHeight = list
            }
            else -> {
                resetHeight(settingsContent)
                resetHeight(tunnelPage)
                resetHeight(accountsPage)
                resetHeight(sensPage)
                resetHeight(locPage)
                resetScroll(tunnelScroll, 120.0)
                resetScroll(sessionScroll, 96.0)
                resetList(locList)
            }
        }
    }

    private fun locChrome(): Double {
        val heading = locPage.children.filter { it !== locCard && it.isManaged }.sumOf { (it as? Region)?.prefHeight(-1.0) ?: 0.0 }
        val pageGaps = locPage.spacing * (locPage.children.count { it.isManaged } - 1).coerceAtLeast(0)
        val bar = if (locBar.isManaged) locBar.prefHeight(-1.0) else 0.0
        val note = if (locNote.isManaged) locNote.prefHeight(-1.0) else 0.0
        val cardPad = locCard.padding.top + locCard.padding.bottom
        val managedInCard = locCard.children.count { it.isManaged }
        val cardGaps = locCard.spacing * (managedInCard - 1).coerceAtLeast(0)
        return heading + pageGaps + cardPad + bar + note + cardGaps
    }

    private fun resetList(list: Region) {
        list.minHeight = 0.0
        list.prefHeight = Region.USE_COMPUTED_SIZE
        list.maxHeight = Double.MAX_VALUE
    }

    private fun tunnelChrome(): Double {
        val fixed = tunnelPage.children.filter { it !== tunnelScroll && it.isManaged }
        val heights = fixed.sumOf { (it as? Region)?.prefHeight(-1.0) ?: 0.0 }
        val gaps = tunnelPage.spacing * (tunnelPage.children.count { it.isManaged } - 1).coerceAtLeast(0)
        return heights + gaps
    }

    private fun accountsChrome(): Double {
        val fixed = accountsPage.children.filter { it !== sessionScroll && it.isManaged }
        val heights = fixed.sumOf { (it as? Region)?.prefHeight(-1.0) ?: 0.0 }
        val gaps = accountsPage.spacing * (accountsPage.children.count { it.isManaged } - 1).coerceAtLeast(0)
        return heights + gaps
    }

    /** The shared status line sits under the accounts page. Count it only while that note is on this page. */
    private fun accountsNoteHeight(): Double {
        if (!::clientStatus.isInitialized || !clientStatus.isManaged) return 0.0
        if (clientStatus.scene != null) clientStatus.applyCss()
        val gap = if (::clientContent.isInitialized) clientContent.spacing else 0.0
        return clientStatus.prefHeight(-1.0) + gap
    }

    private fun resetHeight(node: Region) {
        node.minHeight = Region.USE_COMPUTED_SIZE
        node.prefHeight = Region.USE_COMPUTED_SIZE
        node.maxHeight = Region.USE_COMPUTED_SIZE
    }

    private fun resetScroll(pane: ScrollPane, min: Double) {
        pane.minHeight = min
        pane.prefHeight = Region.USE_COMPUTED_SIZE
        pane.maxHeight = Double.MAX_VALUE
    }

    /** Stops a list from painting over the row under it when the pane is shorter than its content. */
    private fun clipToBounds(node: Region) {
        val clip = javafx.scene.shape.Rectangle()
        clip.widthProperty().bind(node.widthProperty())
        clip.heightProperty().bind(node.heightProperty())
        node.clip = clip
    }

    /** Puts [node] at the top of the scroll port. Lays the page out first so a just-shown column has a height. */
    private fun reveal(node: Node) {
        fun jump() {
            val content = scroll.content ?: return
            if (node.scene == null || content.scene == null) return
            val bounds = content.sceneToLocal(node.localToScene(node.boundsInLocal))
            if (bounds.height < 1.0) return
            val maxScroll = content.boundsInLocal.height - scroll.viewportBounds.height
            scroll.vvalue = if (maxScroll <= 1.0) 0.0 else (bounds.minY / maxScroll).coerceIn(0.0, 1.0)
        }
        if (settingsRoot.scene != null) {
            settingsRoot.applyCss()
            settingsRoot.layout()
        }
        jump()
        Platform.runLater {
            if (settingsRoot.scene != null) {
                settingsRoot.applyCss()
                settingsRoot.layout()
            }
            jump()
        }
    }

    private fun setShown(node: Node, on: Boolean) {
        node.isVisible = on
        node.isManaged = on
    }

    private lateinit var tunnels: TunnelPage
    private lateinit var names: LocalizationPage

    private fun bindClient() {
        // Offline and showcase never open the live catalog. Tests that replace TunnelServices.fetch keep that fake.
        TunnelServices.mockCatalog = !ctx.liveBackend
        buildPresets()
        refreshJvmStatus()

        tunnels = TunnelPage(
            ctx,
            regionGroup,
            mapOf(
                regionRu to TunnelRegion.RU,
                regionEu to TunnelRegion.EU,
                regionNa to TunnelRegion.NA,
                regionSea to TunnelRegion.SEA,
                regionNea to TunnelRegion.NEA,
            ),
            tunnelPools,
            tunnelMeasure,
            tunnelFindBest,
            tunnelRefresh,
            tunnelMode,
        )
        tunnels.bind()
        names = LocalizationPage(ctx, locWeapons, locItems, locChanged, locCategory, locSearch, locWrite, locList, locNote)
        names.bind()
        refreshSessions()
        tunnels.refresh()

        captureSession.setOnAction {
            runClient {
                ctx.desk.captureCurrent(sessionName.text.trim())
                sessionName.clear()
                ctx.t("stalzone.accounts.saved")
            }
        }
        captureSession.tooltip = Tooltip(ctx.t("stalzone.accounts.capture"))
        writeSens.tooltip = Tooltip(ctx.t("stalzone.sens.write"))
        calcSens.setOnAction { showSens(parseBase()) }
        writeSens.setOnAction {
            val shown = sensValues()
            val base = shown?.get(1.0) ?: parseBase() ?: return@setOnAction
            runClient {
                val values = if (shown != null) ctx.desk.writeSensitivity(base, shown) else ctx.desk.writeSensitivity(base)
                fillSens(base, values)
                ctx.t("stalzone.sens.written")
            }
        }
    }

    /** Stored bar value, in whole GB. Null until the user (or an official import) has chosen one. */
    private fun chosenHeapGb(): Int? = store.load().ramMb?.let { RamOptions.fromMb(it, totalRamMb) }

    private fun presetBuilt(id: String): JvmPresets.Built? {
        val heap = chosenHeapGb()
        val machine = ctx.services.system.machine()
        if (machine != null) return JvmPresets.build(id, machine, heap)
        val totalGb = RamOptions.usableGb(totalRamMb)
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return JvmPresets.build(id, totalGb, totalGb, threads, threads, heap)
    }

    private fun buildPresets() {
        jvmGrid.children.clear()
        JvmPresets.choices.forEachIndexed { index, preset ->
            val none = preset.id == JvmPresets.NONE
            val built = if (none) null else presetBuilt(preset.id)
            val name = presetName(preset)
            val title = Label(
                if (none) name
                else presetCaption(name, if (built == null) ctx.t("settings.jvm.unavailable") else ctx.t("settings.jvm.heap", built.heapGb)),
            ).apply {
                styleClass.add("jvm-preset-title")
                textOverrun = OverrunStyle.ELLIPSIS
                isWrapText = false
                minWidth = 0.0
                maxWidth = Double.MAX_VALUE
            }
            val hint = Label(ctx.t(preset.hintKey)).apply {
                styleClass.add("jvm-preset-hint")
                isWrapText = true
                minWidth = 0.0
                maxWidth = Double.MAX_VALUE
            }
            val chosen = Label(ctx.t("settings.jvm.chosen")).apply {
                styleClass.add("jvm-chosen")
                isWrapText = true
                minWidth = 0.0
                maxWidth = Double.MAX_VALUE
                isVisible = false
                isManaged = false
            }
            val text = VBox(title, hint, chosen).apply {
                styleClass.add("jvm-preset-text")
                minWidth = 0.0
                maxWidth = Double.MAX_VALUE
            }
            HBox.setHgrow(text, Priority.ALWAYS)
            val row = HBox(10.0, Region().apply { styleClass.add("jvm-radio") }, text).apply {
                alignment = Pos.CENTER_LEFT
                maxWidth = Double.MAX_VALUE
            }
            val card = Button().apply {
                styleClass.add("jvm-preset")
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                alignment = Pos.CENTER_LEFT
                graphic = row
                userData = preset.id
                isDisable = !none && built == null
                maxWidth = Double.MAX_VALUE
                minHeight = Region.USE_PREF_SIZE
                accessibleText = "$name. ${ctx.t(preset.hintKey)}"
                setOnAction {
                    if (none) {
                        runCatching { ctx.desk.useTunedProfile(false) }
                            .onSuccess {
                                refreshJvmStatus()
                                note(ctx.t("settings.jvm.cleared"))
                            }
                            .onFailure { note(it.message ?: it.javaClass.simpleName) }
                        return@setOnAction
                    }
                    val fresh = presetBuilt(preset.id) ?: return@setOnAction
                    runCatching { ctx.desk.activatePreset(preset.id, fresh.json) }
                        .onSuccess {
                            refreshJvmStatus()
                            note(ctx.t("settings.jvm.saved", name))
                        }
                        .onFailure { note(it.message ?: it.javaClass.simpleName) }
                }
            }
            GridPane.setHgrow(card, Priority.ALWAYS)
            GridPane.setFillWidth(card, true)
            jvmGrid.add(card, index % 2, index / 2)
        }
        refreshMachineLine()
    }

    /** One line under the summary. Hidden when this screen is not bound to a real computer. */
    private fun refreshMachineLine() {
        if (!::jvmMachine.isInitialized) return
        val text = machineLine()
        jvmMachine.text = text
        jvmMachine.isVisible = text.isNotBlank()
        jvmMachine.isManaged = jvmMachine.isVisible
    }

    private fun machineLine(): String {
        val machine = ctx.services.system.machine() ?: return ""
        val ram = RamOptions.format(machine.totalGb, ctx.t("settings.unit.gb"))
        val head = listOf(machine.cpuName, machine.gpuName).filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { ctx.t("settings.jvm.machine.cpu") }
        val extras = buildList {
            if (machine.slowRam) add(ctx.t("settings.jvm.machine.slow"))
            if (machine.bigCache) add(ctx.t("settings.jvm.machine.cache"))
            if (machine.largePages) add(ctx.t("settings.jvm.machine.large"))
        }
        val line = ctx.t(
            "settings.jvm.machine",
            head,
            counted(machine.cores, "settings.jvm.machine.core"),
            counted(machine.threads, "settings.jvm.machine.thread"),
            ram,
        )
        return if (extras.isEmpty()) line else "$line · ${extras.joinToString(" · ")}"
    }

    /** `8 ядер`, `1 ядро`, `2 потока`. English uses one / many only. */
    private fun counted(n: Int, key: String): String {
        val form = if (ctx.lang != "ru") {
            if (n == 1) "one" else "many"
        } else {
            val mod100 = n % 100
            val mod10 = n % 10
            when {
                mod100 in 11..14 -> "many"
                mod10 == 1 -> "one"
                mod10 in 2..4 -> "few"
                else -> "many"
            }
        }
        return "$n ${ctx.t("$key.$form")}"
    }

    /** The bar moved: every enabled preset card names the heap the click will write. */
    private fun refreshPresetHeapLabels() {
        if (!::jvmGrid.isInitialized) return
        jvmGrid.children.filterIsInstance<Button>().forEach { card ->
            if (card.isDisable) return@forEach
            val id = card.userData as? String ?: return@forEach
            if (id == JvmPresets.NONE) return@forEach
            val built = presetBuilt(id) ?: return@forEach
            val preset = JvmPresets.choices.firstOrNull { it.id == id } ?: return@forEach
            presetTitle(card)?.text = presetCaption(presetName(preset), ctx.t("settings.jvm.heap", built.heapGb))
        }
        if (::jvmSummary.isInitialized) refreshJvmStatus()
    }

    private fun presetTitle(card: Button): Label? =
        (card.graphic as? javafx.scene.Parent)?.lookupAll(".jvm-preset-title")?.filterIsInstance<Label>()?.firstOrNull()

    private fun refreshClient() {
        refreshJvmStatus()
        refreshSessions()
        if (::tunnels.isInitialized) tunnels.refresh()
    }

    private fun refreshSessions() {
        val saved = runCatching { ctx.desk.sessions() }.getOrDefault(emptyList())
        val live = runCatching { ctx.desk.liveAccount() }.getOrDefault(emptyMap())
        if (saved.isEmpty()) {
            sessionCards.children.setAll(Label(ctx.t("settings.accounts.empty")).apply {
                styleClass.add("settings-note")
                isWrapText = true
            })
        } else {
            sessionCards.children.setAll(saved.map { sessionCard(it, live) })
        }
        if (sessionCards.scene != null) sessionCards.applyCss()
        applyPageFill()
    }

    private fun sessionCard(session: ExboSession, live: Map<String, String>): VBox {
        val stored = ExboSessions.storedValues(session.data)
        val login = stored.firstOrNull { it.name == "EXBO_LOGIN" }?.value?.takeIf { it.isNotBlank() }
        val current = ExboSessions.isCurrent(session, live)
        val title = Label(session.name).apply {
            styleClass.add("jvm-preset-title")
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
            accessibleText = listOfNotNull(
                session.name,
                login,
                if (current) ctx.t("settings.accounts.current") else null,
            ).joinToString(". ")
        }
        HBox.setHgrow(title, Priority.ALWAYS)
        val nameInput = TextField(session.name).apply {
            styleClass.addAll("limit-input", "session-name-input")
            promptText = ctx.t("stalzone.accounts.name")
            HBox.setHgrow(this, Priority.ALWAYS)
            maxWidth = Double.MAX_VALUE
            minWidth = 0.0
        }
        val nameBox = HBox(nameInput).apply {
            styleClass.addAll("settings-field", "zone-input", "zone-radius-sm", "session-rename")
            alignment = Pos.CENTER_LEFT
            HBox.setHgrow(this, Priority.ALWAYS)
            maxWidth = Double.MAX_VALUE
            minWidth = 0.0
            isVisible = false
            isManaged = false
        }
        val heading = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
            children.add(title)
            children.add(nameBox)
            if (current) {
                children.add(Label(ctx.t("settings.accounts.current")).apply {
                    styleClass.add("session-current")
                    minWidth = Region.USE_PREF_SIZE
                })
            }
        }
        val textCol = VBox().apply {
            styleClass.add("session-text")
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
            children.add(heading)
            if (login != null) {
                children.add(Label(ctx.t("settings.accounts.login", login)).apply {
                    styleClass.add("jvm-preset-hint")
                    textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
                    minWidth = 0.0
                    maxWidth = Double.MAX_VALUE
                })
            }
            HBox.setHgrow(this, Priority.ALWAYS)
            cursor = javafx.scene.Cursor.HAND
        }
        val apply = Button(ctx.t("stalzone.accounts.apply")).apply {
            styleClass.addAll("settings-mini", "primary", "session-apply")
            isMnemonicParsing = false
            minWidth = Region.USE_PREF_SIZE
            setOnAction {
                applySession(session)
            }
        }
        val delete = Button(ctx.t("stalzone.accounts.delete")).apply {
            styleClass.addAll("settings-mini", "session-delete")
            isMnemonicParsing = false
            minWidth = Region.USE_PREF_SIZE
            setOnAction {
                runClient {
                    ctx.desk.delete(session.id)
                    ctx.t("stalzone.accounts.removed")
                }
            }
        }
        var committing = false
        fun editing(): Boolean = nameBox.isManaged
        fun showEditor(on: Boolean) {
            title.isVisible = !on
            title.isManaged = !on
            nameBox.isVisible = on
            nameBox.isManaged = on
            if (on) {
                nameInput.text = session.name
                nameInput.requestFocus()
                nameInput.selectAll()
            }
        }
        fun commitRename() {
            if (committing || !editing()) return
            val next = nameInput.text.trim()
            if (next.isEmpty()) {
                note(ctx.t("settings.accounts.name_empty"))
                nameInput.requestFocus()
                return
            }
            if (next == session.name) {
                showEditor(false)
                return
            }
            committing = true
            runClient {
                check(ctx.desk.rename(session.id, next)) { ctx.t("stalzone.accounts.pick") }
                ctx.t("stalzone.accounts.renamed")
            }
        }
        textCol.setOnMouseClicked { e ->
            if (e.button == MouseButton.PRIMARY && e.clickCount == 2 && !editing()) showEditor(true)
        }
        nameInput.setOnAction { commitRename() }
        nameInput.focusedProperty().addListener { _, was, focused ->
            if (was && !focused && editing() && !committing) {
                val next = nameInput.text.trim()
                if (next.isEmpty() || next == session.name) showEditor(false) else commitRename()
            }
        }
        // filter, not a bubble handler: the text field skin consumes the key before onKeyPressed
        nameInput.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            if (e.code == KeyCode.ESCAPE && editing()) {
                showEditor(false)
                e.consume()
            }
        }
        val actions = HBox(apply, delete).apply {
            styleClass.add("session-actions")
            alignment = Pos.CENTER_RIGHT
            minWidth = Region.USE_PREF_SIZE
            maxWidth = Region.USE_PREF_SIZE
        }
        val head = HBox(textCol, actions).apply {
            styleClass.add("session-head")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        return VBox(8.0, head).apply {
            styleClass.addAll("session-card", "card", "zone-radius-md")
            if (current) styleClass.add("selected")
            maxWidth = Double.MAX_VALUE
            minHeight = Region.USE_PREF_SIZE
            userData = session.id
        }
    }

    private fun refreshJvmStatus() {
        val active = runCatching { ctx.desk.activeProfile() }.getOrNull()
        val preset = JvmPresets.find(active)
        val none = active.isNullOrBlank()
        jvmSummary.styleClass.removeAll(listOf("selected", "empty"))
        jvmSummary.styleClass.add(if (none) "empty" else "selected")
        jvmStatusKicker.text = ctx.t("settings.jvm.chosen")
        if (none) {
            jvmStatus.text = ctx.t("settings.jvm.without")
            jvmStatusHint.text = ctx.t("settings.jvm.without.hint")
        } else {
            val heap = preset?.takeIf { it.id != JvmPresets.NONE }?.let { presetBuilt(it.id) }?.heapGb
            val name = jvmCaption(active)
            jvmStatus.text = if (heap != null) presetCaption(name, ctx.t("settings.jvm.heap", heap)) else name
            jvmStatusHint.text = preset?.let { ctx.t(it.hintKey) }.orEmpty()
        }
        jvmStatusHint.isVisible = jvmStatusHint.text.isNotBlank()
        jvmStatusHint.isManaged = jvmStatusHint.isVisible
        jvmGrid.children.filterIsInstance<Button>().forEach { card ->
            val id = card.userData as? String
            val on = id != null && JvmPresets.cardSelected(id, active)
            if (on && "selected" !in card.styleClass) card.styleClass.add("selected")
            if (!on) card.styleClass.remove("selected")
            val mark = (card.graphic as? javafx.scene.Parent)?.lookupAll(".jvm-chosen")?.filterIsInstance<Label>()?.firstOrNull()
            mark?.isVisible = on
            mark?.isManaged = on
            val item = JvmPresets.find(id)
            if (item != null && id == item.id) {
                val state = if (on) ctx.t("settings.jvm.chosen") else ""
                card.accessibleText = listOf(presetName(item), ctx.t(item.hintKey), state).filter { it.isNotBlank() }.joinToString(". ")
            }
        }
    }

    /**
     * Version and the heap this click will write, e.g. `v1.1.2, 6 ГБ`.
     * Pass [JvmPresets.Preset.version] in as its own argument. A template of `$preset.version`
     * prints [JvmPresets.Preset.toString] and leaves `.version` as literal text.
     */
    private fun presetName(preset: JvmPresets.Preset): String = preset.nameKey?.let { ctx.t(it) } ?: preset.version

    private fun presetCaption(version: String, heapLabel: String): String = "$version, $heapLabel"

    /** Registry id → a label. A blank id is the without-JVM choice. Anything unknown stays as stored. */
    private fun jvmCaption(active: String?): String = when {
        active.isNullOrBlank() -> ctx.t("settings.jvm.without")
        else -> JvmPresets.find(active)?.let { presetName(it) } ?: active
    }

    private fun parseBase(): Double? {
        val value = baseSens.text.trim().replace(',', '.').toDoubleOrNull()
        if (value == null || value <= 0.0) {
            note(ctx.t("stalzone.sens.bad"))
            return null
        }
        return value
    }

    private fun showSens(base: Double?) {
        if (base == null) return
        fillSens(base, ScopeSens.snapshot(base))
        note(ctx.t("stalzone.sens.ready"))
    }

    private fun fillSens(base: Double, values: Map<Double, Double>) {
        val peak = values.values.maxOrNull() ?: 0.0
        val max = maxOf(SLIDER_MAX, peak)
        sensLock = true
        try {
            sensList.children.setAll(ScopeSens.MAGNIFICATIONS.map { mag ->
                sensLine(mag, values[mag] ?: base, max)
            })
        } finally {
            sensLock = false
        }
        baseSens.text = formatSens(base)
        sensList.accessibleText = ctx.t("stalzone.sens.base_line", formatSens(base))
    }

    /** True while rows are being built or moved together, so a slider does not rewrite the table under itself. */
    private var sensLock = false

    /** One game row: «Чувствительность в прицелах кратностью 1x», the number, the slider, the two steps. */
    private fun sensLine(mag: Double, value: Double, max: Double): HBox {
        val name = Label(ctx.t("settings.sens.row", magCaption(mag))).apply {
            styleClass.add("sens-name")
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            minWidth = 0.0
        }
        val valueLabel = Label(formatSens(value)).apply {
            styleClass.add("sens-value")
            alignment = Pos.CENTER_RIGHT
        }
        val slider = Slider(0.0, max, value.coerceIn(0.0, max)).apply {
            styleClass.add("sens-slider")
            isShowTickMarks = false
            isShowTickLabels = false
            blockIncrement = STEP
            maxWidth = Double.MAX_VALUE
            minWidth = 72.0
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        lateinit var line: HBox
        fun applyValue(next: Double, fromSlider: Boolean) {
            if (sensLock) return
            val snapped = ScopeSens.round2(next.coerceIn(STEP, slider.max))
            valueLabel.text = formatSens(snapped)
            line.accessibleText = "${name.text}. ${valueLabel.text}"
            if (!fromSlider) {
                sensLock = true
                slider.value = snapped
                sensLock = false
            }
            if (mag == 1.0) {
                baseSens.text = formatSens(snapped)
                applyBase(snapped)
            }
        }
        slider.valueProperty().addListener { _, _, next -> applyValue(next.toDouble(), fromSlider = true) }
        val steps = HBox(2.0, sensStep(left = true) { applyValue(slider.value - STEP, fromSlider = false) }, sensStep(left = false) {
            applyValue(slider.value + STEP, fromSlider = false)
        }).apply { styleClass.add("sens-steps") }
        line = HBox(12.0, name, valueLabel, slider, steps).apply {
            styleClass.add("sens-line")
            alignment = Pos.CENTER_LEFT
            userData = mag
            maxWidth = Double.MAX_VALUE
            accessibleText = "${name.text}. ${valueLabel.text}"
        }
        return line
    }

    /** 1x moved: the other scopes follow the wiki table. Their own nudges stay until the next 1x change. */
    private fun applyBase(base: Double) {
        val table = ScopeSens.snapshot(base)
        sensLock = true
        try {
            sensList.children.filterIsInstance<HBox>().forEach { row ->
                val mag = row.userData as? Double ?: return@forEach
                if (mag == 1.0) return@forEach
                val next = table[mag] ?: return@forEach
                val label = row.lookup(".sens-value") as? Label ?: return@forEach
                val slider = row.lookup(".sens-slider") as? Slider ?: return@forEach
                label.text = formatSens(next)
                slider.value = next.coerceIn(slider.min, slider.max)
                row.accessibleText = "${(row.lookup(".sens-name") as? Label)?.text.orEmpty()}. ${label.text}"
            }
        } finally {
            sensLock = false
        }
    }

    private fun sensValues(): Map<Double, Double>? {
        if (!::sensList.isInitialized || sensList.children.isEmpty()) return null
        val out = linkedMapOf<Double, Double>()
        sensList.children.filterIsInstance<HBox>().forEach { row ->
            val mag = row.userData as? Double ?: return@forEach
            val text = (row.lookup(".sens-value") as? Label)?.text ?: return@forEach
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return@forEach
            out[mag] = value
        }
        return out.takeIf { it.size == ScopeSens.MAGNIFICATIONS.size }
    }

    private fun sensStep(left: Boolean, onClick: () -> Unit): Button {
        val glyph = Region().apply {
            styleClass.add(if (left) "sens-chevron-left" else "sens-chevron-right")
            isMouseTransparent = true
        }
        return Button().apply {
            styleClass.add("sens-step")
            graphic = glyph
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            isMnemonicParsing = false
            accessibleText = ctx.t(if (left) "settings.sens.down" else "settings.sens.up")
            tooltip = Tooltip(accessibleText)
            setOnAction { onClick() }
        }
    }

    /** `1`, `1.25`, `2` plus `x`, the same wording as the game rows. */
    private fun magCaption(mag: Double): String {
        val key = ScopeSens.magKey(mag)
        val short = if (key.endsWith(".0")) key.dropLast(2) else key
        return "${short}x"
    }

    private fun formatSens(value: Double): String = String.format(Locale.US, "%.2f", ScopeSens.round2(value))

    private fun sessionLogin(session: ExboSession): String? =
        ExboSessions.storedValues(session.data).firstOrNull { it.name == "EXBO_LOGIN" }?.value?.takeIf { it.isNotBlank() }

    /**
     * Writes the snapshot to HKCU, then signs the launcher in as that login.
     * Showcase and tests switch on this thread. Live auto-auth goes off the FX thread.
     */
    private fun applySession(session: ExboSession) {
        val login = sessionLogin(session)
        var applied = false
        runClient {
            ctx.desk.apply(session)
            if (!ctx.liveBackend && login != null) ctx.services.auth.switchTo(login)
            applied = true
            ctx.t("stalzone.accounts.applied", session.name)
        }
        if (!applied) return
        onAccountSwitched(login ?: ctx.services.auth.currentSession?.login, !ctx.liveBackend)
        if (!ctx.liveBackend || login == null) return
        background({ ctx.services.auth.switchTo(login) }) { result ->
            onAccountSwitched(ctx.services.auth.currentSession?.login ?: login, true)
            if (result.getOrDefault(false) != true) note(ctx.t("stalzone.accounts.reauth_failed", login))
        }
    }

    private fun runClient(block: () -> String) {
        try {
            note(block())
            refreshClient()
        } catch (e: Exception) {
            note(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Page that produced the current note. A session line stays on accounts and does not follow JVM. */
    private var noteSection: String? = null

    private fun note(text: String) {
        clientStatus.text = text
        if (::sensNote.isInitialized) sensNote.text = text
        noteSection = currentSection
        placeNote()
    }

    /** Sensitivity messages sit under the table. Every other note stays on the page that wrote it. */
    private fun placeNote() {
        if (!::clientStatus.isInitialized) return
        val text = clientStatus.text.orEmpty()
        val here = text.isNotEmpty() && noteSection == currentSection
        val showSens = here && currentSection == "sens"
        val showShared = here && currentSection != "sens"
        clientStatus.isVisible = showShared
        clientStatus.isManaged = showShared
        if (::sensNote.isInitialized) {
            sensNote.isVisible = showSens
            sensNote.isManaged = showSens
        }
        // the accounts list is pinned to the viewport, so a note has to take its strip or it covers the last card
        if (currentSection == "accounts") applyPageFill()
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
        Motion.smoothSwitch(toggle) // the thumb slides to its side instead of jumping
        // the whole row is clickable, like on the reference
        row.setOnMouseClicked { e -> if (e.target !== toggle && !toggle.isHover && !toggle.isDisable) toggle.isSelected = !toggle.isSelected }
    }

    /** Digits only (max 7), empty means 0; saved on every edit. "Загрузка · КБ/с, 0 — без ограничений" as a tooltip. */
    private fun bindLimit(box: HBox, field: TextField, captionKey: String, value: Int, onChange: (Int) -> Unit) {
        Tooltip.install(box, Tooltip("${ctx.t(captionKey)} · ${ctx.t("settings.speed_limit.note")}"))
        field.textFormatter = TextFormatter<String> { c -> if (c.controlNewText.matches(Regex("\\d{0,7}"))) c else null }
        field.text = value.toString()
        field.textProperty().addListener { _, _, t -> onChange(t.toIntOrNull() ?: 0) }
        field.focusedProperty().addListener { _, _, focused -> if (!focused && field.text.isEmpty()) field.text = "0" }
    }

    private companion object {
        /** Game-style track: 5 sits near the middle. A larger table stretches every row together. */
        const val SLIDER_MAX = 10.0
        const val STEP = 0.01
    }
}
