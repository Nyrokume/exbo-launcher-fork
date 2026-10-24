package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.RamBar
import dev.launcherfork.ui.components.RamScale
import dev.launcherfork.ui.motion.Motion
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import dev.launcherfork.stalzone.ExboSession
import dev.launcherfork.stalzone.ExboSessions
import dev.launcherfork.stalzone.ExboValue
import dev.launcherfork.stalzone.JvmPresets
import dev.launcherfork.stalzone.JvmProfile
import dev.launcherfork.stalzone.ScopeSens
import dev.launcherfork.stalzone.TunnelRegion
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.AnchorPane
import javafx.scene.shape.SVGPath
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.RowConstraints
import javafx.scene.layout.VBox
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * settings.fxml, bound to [dev.launcherfork.domain.SettingsStore].
 * Память, Поведение, Обновление and Язык are one scrollable column.
 * The header switch opens JVM, the tunnel, sensitivity and account swap. The tunnel page fills the
 * settings viewport and scrolls the server list inside itself. Sensitivity fills that same viewport:
 * a compact base row, then the scope table across the remaining width and height. The profile menu opens accounts.
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
    @FXML lateinit var jvmStatus: Label
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
    @FXML lateinit var tunnelMode: VBox
    @FXML lateinit var sessionScroll: ScrollPane
    @FXML lateinit var sessionCards: VBox
    @FXML lateinit var accountsRow: HBox
    @FXML lateinit var sessionName: TextField
    @FXML lateinit var captureSession: Button
    @FXML lateinit var baseSens: TextField
    @FXML lateinit var calcSens: Button
    @FXML lateinit var writeSens: Button
    @FXML lateinit var sensChips: GridPane
    @FXML lateinit var sensNote: Label
    @FXML lateinit var clientStatus: Label
    @FXML lateinit var languageGroup: ToggleGroup
    @FXML lateinit var langRu: RadioButton
    @FXML lateinit var langEn: RadioButton

    private val store get() = ctx.services.settings

    /**
     * Section last asked for. The gear's home is the whole column (`ram`, and the other column ids).
     * `jvm`, `tunnel`, `sens` and `accounts` are the pages with a nav button.
     */
    var currentSection: String = "ram"
        private set

    /** Fired after [showSection], including the initial column. MainController paints the nav accent from this. */
    var onSection: (String) -> Unit = {}

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
        sessionCards.minHeight = Region.USE_PREF_SIZE
        scroll.viewportBoundsProperty().addListener { _, _, _ -> applyPageFill() }
        showSection("ram")
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

    /** Gear home is the settings column. `true` opens JVM. */
    fun showClient(client: Boolean = true) = showSection(if (client) "jvm" else "ram")

    /**
     * `ram`, `behaviour`, `updates` and `language` are the one column (a named id scrolls that block into view).
     * `jvm`, `tunnel`, `sens` and `accounts` each replace the column. Tunnel fills the viewport; its server list
     * scrolls inside the page.
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
        onSection(id)
    }

    /** True when the settings column is open (the gear closes settings from here). */
    fun atTop(): Boolean = currentSection in columnSections

    /** Blocks of the one settings column. JVM, tunnel, sensitivity and account swap are separate pages. */
    private val columnSections = setOf("ram", "behaviour", "updates", "language")

    private val toolSections = setOf("jvm", "tunnel", "sens", "accounts")

    private val columnNodes: List<Node>
        get() = listOf(gameSection, behaviourSection, updatesSection, languageSection)

    private val toolNodes: Map<String, Node>
        get() = mapOf(
            "jvm" to jvmPage,
            "tunnel" to tunnelPage,
            "sens" to sensPage,
            "accounts" to accountsPage,
        )

    private fun columnTarget(id: String): Node = when (id) {
        "behaviour" -> behaviourSection
        "updates" -> updatesSection
        "language" -> languageSection
        else -> gameSection
    }

    /**
     * Tunnel and sensitivity occupy the settings viewport. The pool list scrolls in [tunnelScroll];
     * the scope table grows inside the sensitivity card. Account cards use the space above the name row.
     * When they fit, the row sits directly under them. When they do not, the list scrolls and the row stays on screen.
     * Cards are measured after CSS: a card just built by a save has no skin yet, so its pref height is 0
     * and the list would keep the old height, clipping the new card into the name row.
     */
    private fun applyPageFill() {
        if (!::tunnelScroll.isInitialized || !::sessionScroll.isInitialized || !::sensPage.isInitialized) return
        val viewport = scroll.viewportBounds.height
        if (viewport <= 1.0) {
            resetHeight(settingsContent)
            resetHeight(tunnelPage)
            resetHeight(accountsPage)
            resetHeight(sensPage)
            resetScroll(tunnelScroll, 120.0)
            resetScroll(sessionScroll, 96.0)
            return
        }
        val pad = settingsContent.padding.top + settingsContent.padding.bottom
        val inner = (viewport - pad).coerceAtLeast(0.0)
        when (currentSection) {
            "tunnel" -> {
                resetHeight(accountsPage)
                resetHeight(sensPage)
                resetScroll(sessionScroll, 96.0)
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
                resetScroll(tunnelScroll, 120.0)
                val available = (inner - accountsChrome()).coerceAtLeast(96.0)
                val wanted = sessionListHeight()
                sessionScroll.minHeight = 96.0
                sessionScroll.prefHeight = wanted.coerceIn(96.0, available)
                sessionScroll.maxHeight = available
                if (wanted > available + 1.0) {
                    settingsContent.minHeight = viewport
                    settingsContent.prefHeight = viewport
                    settingsContent.maxHeight = viewport
                    accountsPage.minHeight = inner
                    accountsPage.prefHeight = inner
                    accountsPage.maxHeight = inner
                } else {
                    resetHeight(settingsContent)
                    resetHeight(accountsPage)
                }
            }
            "sens" -> {
                resetHeight(tunnelPage)
                resetHeight(accountsPage)
                resetScroll(tunnelScroll, 120.0)
                resetScroll(sessionScroll, 96.0)
                settingsContent.minHeight = viewport
                settingsContent.prefHeight = viewport
                settingsContent.maxHeight = viewport
                sensPage.minHeight = inner
                sensPage.prefHeight = inner
                sensPage.maxHeight = inner
            }
            else -> {
                resetHeight(settingsContent)
                resetHeight(tunnelPage)
                resetHeight(accountsPage)
                resetHeight(sensPage)
                resetScroll(tunnelScroll, 120.0)
                resetScroll(sessionScroll, 96.0)
            }
        }
    }

    private fun tunnelChrome(): Double {
        val fixed = tunnelPage.children.filter { it !== tunnelScroll && it.isManaged }
        val heights = fixed.sumOf { (it as? Region)?.prefHeight(-1.0) ?: 0.0 }
        val gaps = tunnelPage.spacing * (tunnelPage.children.count { it.isManaged } - 1).coerceAtLeast(0)
        return heights + gaps
    }

    private fun accountsChrome(): Double {
        val heading = (accountsPage.children.firstOrNull() as? Region)?.prefHeight(-1.0) ?: 0.0
        return heading + accountsRow.prefHeight(-1.0) + accountsPage.spacing * 2
    }

    /** Pref height of the session list after the new cards have skins. Before CSS a control's pref height is 0. */
    private fun sessionListHeight(): Double {
        if (sessionCards.scene != null) sessionCards.applyCss()
        val width = sessionScroll.width.takeIf { it > 1.0 } ?: -1.0
        return sessionCards.prefHeight(width) + sessionScroll.padding.top + sessionScroll.padding.bottom
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

    private fun bindClient() {
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
            tunnelMode,
        )
        tunnels.bind()
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
            val base = parseBase() ?: return@setOnAction
            runClient {
                val values = ctx.desk.writeSensitivity(base)
                fillSens(base, values)
                ctx.t("stalzone.sens.written")
            }
        }
    }

    private fun buildPresets() {
        val totalGb = RamOptions.totalGb(totalRamMb)
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        jvmGrid.children.clear()
        JvmPresets.all.forEachIndexed { index, preset ->
            val built = JvmPresets.build(preset.id, totalGb, totalGb, threads, threads)
            val heap = if (built == null) ctx.t("settings.jvm.unavailable") else ctx.t("settings.jvm.heap", built.heapGb)
            val title = Label("${preset.version} · $heap").apply {
                styleClass.add("jvm-preset-title")
                textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
                maxWidth = Double.MAX_VALUE
            }
            val card = Button().apply {
                styleClass.add("jvm-preset")
                contentDisplay = ContentDisplay.GRAPHIC_ONLY
                alignment = Pos.CENTER_LEFT
                graphic = title
                userData = preset.id
                isDisable = built == null
                maxWidth = Double.MAX_VALUE
                prefHeight = 58.0
                minHeight = 58.0
                maxHeight = 58.0
                accessibleText = "${preset.version}. ${ctx.t(preset.hintKey)}"
                setOnAction {
                    if (built == null) return@setOnAction
                    runCatching { ctx.desk.activatePreset(preset.id, built.json) }
                        .onSuccess {
                            refreshJvmStatus()
                            note(ctx.t("settings.jvm.saved", preset.version))
                        }
                        .onFailure { note(it.message ?: it.javaClass.simpleName) }
                }
            }
            GridPane.setHgrow(card, Priority.ALWAYS)
            GridPane.setFillWidth(card, true)
            jvmGrid.add(card, index % 2, index / 2)
        }
    }

    private fun refreshClient() {
        refreshJvmStatus()
        refreshSessions()
        if (::tunnels.isInitialized) tunnels.refresh()
    }

    private fun refreshSessions() {
        val saved = runCatching { ctx.desk.sessions() }.getOrDefault(emptyList())
        val live = runCatching { ctx.desk.liveAccount() }.getOrDefault(emptyMap())
        val open = openSessionKeyIds()
        if (saved.isEmpty()) {
            sessionCards.children.setAll(Label(ctx.t("settings.accounts.empty")).apply {
                styleClass.add("settings-note")
                isWrapText = true
            })
        } else {
            sessionCards.children.setAll(saved.map { sessionCard(it, live, it.id in open) })
        }
        if (sessionCards.scene != null) sessionCards.applyCss()
        applyPageFill()
    }

    private fun openSessionKeyIds(): Set<Long> = sessionCards.children
        .filterIsInstance<VBox>()
        .mapNotNull { card ->
            val id = card.userData as? Long ?: return@mapNotNull null
            val open = card.lookupAll(".session-keys-toggle").filterIsInstance<ToggleButton>().any { it.isSelected }
            if (open) id else null
        }
        .toSet()

    private fun sessionCard(session: ExboSession, live: Map<String, String>, keysOpen: Boolean): VBox {
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
        val heading = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
            children.add(title)
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
        }
        val keysBox = VBox(6.0).apply {
            styleClass.add("session-keys")
            isVisible = keysOpen
            isManaged = keysOpen
            maxWidth = Double.MAX_VALUE
            if (stored.isEmpty()) {
                children.add(Label(ctx.t("settings.accounts.no_keys")).apply {
                    styleClass.add("settings-note")
                    isWrapText = true
                })
            } else {
                stored.forEach { children.add(keyBlock(it)) }
            }
        }
        val keysLabel = ctx.t("settings.accounts.keys")
        val keysToggle = ToggleButton().apply {
            styleClass.addAll("settings-mini", "session-keys-toggle")
            graphic = sessionKeyGlyph()
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER
            accessibleText = keysLabel
            tooltip = Tooltip(keysLabel)
            isMnemonicParsing = false
            isSelected = keysOpen
            selectedProperty().addListener { _, _, on ->
                keysBox.isVisible = on
                keysBox.isManaged = on
                applyPageFill()
            }
        }
        val apply = Button(ctx.t("stalzone.accounts.apply")).apply {
            styleClass.addAll("settings-mini", "primary", "session-apply")
            isMnemonicParsing = false
            minWidth = Region.USE_PREF_SIZE
            setOnAction {
                runClient {
                    ctx.desk.apply(session)
                    ctx.t("stalzone.accounts.applied", session.name)
                }
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
        val actions = HBox(keysToggle, apply, delete).apply {
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
        return VBox(8.0, head, keysBox).apply {
            styleClass.addAll("session-card", "card", "zone-radius-md")
            if (current) styleClass.add("selected")
            maxWidth = Double.MAX_VALUE
            minHeight = Region.USE_PREF_SIZE
            userData = session.id
        }
    }

    /** Key, not the tunnel padlock. About 17 px wide; the hit box is the 34 px settings action. */
    private fun sessionKeyGlyph(): SVGPath = SVGPath().apply {
        content = SESSION_KEY
        styleClass.add("session-key-glyph")
        isMouseTransparent = true
    }

    private fun keyBlock(entry: ExboValue): VBox {
        val name = Label(entry.name).apply { styleClass.add("session-key-name") }
        val text = Label(entry.value).apply {
            styleClass.add("session-key-value")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        return VBox(1.0, name, text).apply { maxWidth = Double.MAX_VALUE }
    }

    private fun refreshJvmStatus() {
        val active = runCatching { ctx.desk.activeProfile() }.getOrNull()
        jvmStatus.text = ctx.t("settings.jvm.active", jvmCaption(active))
        jvmGrid.children.filterIsInstance<Button>().forEach { card ->
            val on = card.userData == active
            if (on && "selected" !in card.styleClass) card.styleClass.add("selected")
            if (!on) card.styleClass.remove("selected")
        }
    }

    /** Registry id → a label. `8khz` is the tuned profile, preset ids show their version (v1.1.2), anything else is kept as-is. */
    private fun jvmCaption(active: String?): String = when {
        active.isNullOrBlank() -> ctx.t("settings.jvm.none")
        active == JvmProfile.RECOMMENDED -> ctx.t("settings.jvm.profile")
        else -> JvmPresets.all.firstOrNull { it.id == active }?.version ?: active
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
        val locale = Locale.forLanguageTag(ctx.lang)
        val parts = values.entries.map { (mag, v) -> sensPart(mag, v, locale) }
        val columns = sensColumnCount()
        val intWidth = sensIntWidth(parts)
        val rows = (parts.size + columns - 1) / columns
        sensChips.columnConstraints.setAll(List(columns) {
            ColumnConstraints().apply {
                percentWidth = 100.0 / columns
                hgrow = Priority.ALWAYS
                minWidth = 0.0
                isFillWidth = true
            }
        })
        sensChips.rowConstraints.setAll(List(rows) {
            RowConstraints().apply {
                vgrow = Priority.ALWAYS
                minHeight = 40.0
                isFillHeight = true
            }
        })
        sensChips.children.clear()
        parts.forEachIndexed { index, part ->
            val cell = sensCell(part, intWidth)
            GridPane.setHgrow(cell, Priority.ALWAYS)
            GridPane.setVgrow(cell, Priority.ALWAYS)
            GridPane.setFillWidth(cell, true)
            GridPane.setFillHeight(cell, true)
            sensChips.add(cell, index % columns, index / columns)
        }
        sensChips.accessibleText = ctx.t("stalzone.sens.base_line", String.format(locale, "%.2f", base))
    }

    /** Equal columns that stay inside the card: four at the 900-wide window, fewer only if the card is narrower. */
    private fun sensColumnCount(): Int {
        val width = when {
            sensChips.width > 1 -> sensChips.width
            sensPage.width > 1 -> (sensPage.width - 28).coerceAtLeast(0.0)
            else -> 0.0
        }
        return when {
            width <= 1 || width >= 520 -> 4
            width >= 380 -> 3
            else -> 2
        }
    }

    private fun sensPart(mag: Double, value: Double, locale: Locale): SensPart {
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val shown = ScopeSens.magKey(mag).replace('.', sep)
        val text = String.format(locale, "%.2f", value)
        val cut = text.lastIndexOf(sep)
        return if (cut < 0) SensPart("$shown×", text, "")
        else SensPart("$shown×", text.substring(0, cut), text.substring(cut))
    }

    /** Width of the widest integer part, in the theme font, so the decimals share one vertical line. */
    private fun sensIntWidth(parts: List<SensPart>): Double {
        if (parts.isEmpty()) return 0.0
        val probes = parts.map { part ->
            Label(part.intPart).apply {
                styleClass.add("sens-int")
                alignment = Pos.CENTER_RIGHT
            }
        }
        sensChips.children.setAll(probes)
        sensChips.applyCss()
        val width = probes.maxOf { it.prefWidth(-1.0) }
        sensChips.children.clear()
        return width.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    }

    private fun sensCell(part: SensPart, intWidth: Double): HBox {
        val mag = Label(part.mag).apply { styleClass.add("sens-mag") }
        val intLabel = Label(part.intPart).apply {
            styleClass.add("sens-int")
            alignment = Pos.CENTER_RIGHT
            if (intWidth > 0.0) {
                minWidth = intWidth
                prefWidth = intWidth
            }
        }
        val frac = Label(part.frac).apply { styleClass.add("sens-frac") }
        val value = HBox(intLabel, frac).apply {
            alignment = Pos.CENTER_RIGHT
            spacing = 0.0
        }
        val gap = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        return HBox(mag, gap, value).apply {
            styleClass.addAll("sens-cell", "zone-radius-md")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
            maxHeight = Double.MAX_VALUE
            accessibleText = "${part.mag} ${part.intPart}${part.frac}"
        }
    }

    private data class SensPart(val mag: String, val intPart: String, val frac: String)

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
        /**
         * Horizontal key: round bow, shaft, teeth. Longer side is about 17 px before the stroke.
         * Stroke colour follows the mini-button text (normal, hover, open) in layout.css.
         */
        const val SESSION_KEY =
            "M10.89,8.15 A4.87,4.87 0 1 1 1.15,8.15 A4.87,4.87 0 1 1 10.89,8.15 " +
            "M8.15,8.15 A2.125,2.125 0 1 1 3.90,8.15 A2.125,2.125 0 1 1 8.15,8.15 " +
            "M10.89,8.15 H15.94 M12.57,8.15 V11.16 M14.26,8.15 V12.57 M15.94,8.15 V10.80"
    }
}
