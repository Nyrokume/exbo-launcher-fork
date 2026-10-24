package dev.launcherfork.ui.screens.settings

import dev.launcherfork.stalzone.NodeProbe
import dev.launcherfork.stalzone.RoxyCatalog
import dev.launcherfork.stalzone.StalzonePaths
import dev.launcherfork.stalzone.TunnelCache
import dev.launcherfork.stalzone.TunnelCatalogs
import dev.launcherfork.stalzone.TunnelEndpoint
import dev.launcherfork.stalzone.TunnelOverrides
import dev.launcherfork.stalzone.TunnelProbe
import dev.launcherfork.stalzone.TunnelProbeOutcome
import dev.launcherfork.stalzone.TunnelRegion
import dev.launcherfork.stalzone.TunnelSearchMode
import dev.launcherfork.stalzone.TunnelSelection
import dev.launcherfork.stalzone.TunnelSettings
import dev.launcherfork.stalzone.TunnelTarget
import dev.launcherfork.stalzone.toView
import dev.launcherfork.ui.UiContext
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Tests point [fetch] and [probe] at fakes. Launch does not use this object. */
internal object TunnelServices {
    var directory: Path? = null
    var inline: Boolean = false
    var fetch: (TunnelRegion) -> RoxyCatalog = { region -> TunnelCatalogs.fetch(region) }
    var probe: (TunnelEndpoint) -> TunnelProbeOutcome = { endpoint -> TunnelProbe.measure(endpoint) }
    var clock: () -> Instant = { Instant.now() }

    fun overridesFile(): Path = (directory ?: StalzonePaths.jvmWrapperDir()).resolve("overrides.json")

    fun statsFile(): Path = (directory ?: StalzonePaths.jvmWrapperDir()).resolve("cache").resolve("tunnel_stats.json")

    fun reset() {
        directory = null
        inline = false
        fetch = { region -> TunnelCatalogs.fetch(region) }
        probe = { endpoint -> TunnelProbe.measure(endpoint) }
        clock = { Instant.now() }
    }
}

/**
 * One Roxy node per region. Opening the region loads that list. Opening a group measures its nodes
 * once. «Измерить» measures the open region and does not write an override. «Найти лучший сервер»
 * measures when this region has no fresh success, then writes the single best node.
 */
class TunnelPage(
    private val ctx: UiContext,
    private val regionGroup: ToggleGroup,
    private val regionButtons: Map<RadioButton, TunnelRegion>,
    private val tunnelPools: VBox,
    private val tunnelMeasure: Button,
    private val tunnelFindBest: Button,
    private val tunnelMode: VBox,
) {
    private var settings = TunnelSettings()
    private var settingsError: String? = null
    private val cache = TunnelCache()
    private val catalogs = linkedMapOf<TunnelRegion, RoxyCatalog>()
    private val probes = HashMap<String, NodeProbe>()
    private var selected = TunnelRegion.RU
    private var loading: TunnelRegion? = null
    private var catalogError: String? = null
    private var catalogGen = 0
    private var probeGen = 0
    private var measuring = false
    private var suppressRegion = false
    private val modeValue = Label().apply {
        styleClass.add("tunnel-mode-value")
        textOverrun = OverrunStyle.ELLIPSIS
        maxWidth = Double.MAX_VALUE
    }
    private val modeButton = Button().apply {
        styleClass.addAll("settings-mini", "tunnel-mode-button")
        contentDisplay = ContentDisplay.GRAPHIC_ONLY
        alignment = Pos.CENTER_LEFT
        maxWidth = Double.MAX_VALUE
        graphic = HBox(8.0, modeValue, Region().apply { styleClass.add("tunnel-mode-caret") }).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
            HBox.setHgrow(modeValue, Priority.ALWAYS)
        }
    }
    private val modeList = VBox().apply {
        styleClass.addAll("tunnel-mode-list", "zone-radius-sm")
        isVisible = false
        isManaged = false
        maxWidth = Double.MAX_VALUE
    }
    private val closeModeOnOutside = EventHandler<MouseEvent> { event ->
        if (!modeList.isVisible) return@EventHandler
        val target = event.target as? Node ?: return@EventHandler
        if (inside(modeButton, target) || inside(modeList, target)) return@EventHandler
        setModeOpen(false)
    }

    fun bind() {
        reloadFiles()
        tunnelMeasure.tooltip = Tooltip(ctx.t("settings.tunnel.measure.hint"))
        tunnelFindBest.tooltip = Tooltip(ctx.t("settings.tunnel.find.hint"))
        tunnelMeasure.setOnAction { measureOpen() }
        tunnelFindBest.setOnAction { findBest() }
        mountModeList()
        regionGroup.selectedToggleProperty().addListener { _, _, toggle ->
            if (suppressRegion) return@addListener
            val region = regionButtons[toggle] ?: return@addListener
            openRegion(region)
        }
        render()
    }

    fun refresh() {
        reloadFiles()
        render()
    }

    fun onShown() {
        val region = regionButtons[regionGroup.selectedToggle] ?: TunnelRegion.RU
        openRegion(region)
    }

    private fun mountModeList() {
        tunnelMode.children.setAll(modeButton)
        modeList.children.setAll(TunnelSearchMode.entries.map { mode ->
            Button(modeLabel(mode)).apply {
                styleClass.addAll("tunnel-mode-item", "zone-radius-sm")
                maxWidth = Double.MAX_VALUE
                alignment = Pos.CENTER_LEFT
                userData = mode
                setOnAction { chooseMode(mode) }
            }
        })
        val page = tunnelMode.parent?.parent as? VBox
        if (page != null && modeList.parent !== page) page.children.add(modeList)
        modeButton.setOnAction { setModeOpen(!modeList.isVisible) }
        modeButton.sceneProperty().addListener { _, previous, scene ->
            previous?.removeEventFilter(MouseEvent.MOUSE_PRESSED, closeModeOnOutside)
            scene?.addEventFilter(MouseEvent.MOUSE_PRESSED, closeModeOnOutside)
        }
        modeButton.scene?.addEventFilter(MouseEvent.MOUSE_PRESSED, closeModeOnOutside)
    }

    private fun setModeOpen(open: Boolean) {
        if (open && modeButton.isDisabled) return
        modeList.isVisible = open
        if (open) modeButton.styleClass.add("open") else modeButton.styleClass.remove("open")
        if (open) {
            placeModeList()
            modeList.toFront()
        }
    }

    private fun placeModeList() {
        val page = modeList.parent as? Region ?: return
        if (modeButton.scene == null || page.scene == null || modeButton.height < 2.0) return
        val local = page.sceneToLocal(modeButton.localToScene(0.0, modeButton.height))
        val width = modeButton.width.coerceAtLeast(148.0)
        modeList.layoutX = local.x
        modeList.layoutY = local.y + 4.0
        modeList.minWidth = width
        modeList.prefWidth = width
        modeList.maxWidth = width
        val height = modeList.prefHeight(width)
        modeList.resize(width, height)
        modeList.layout()
    }

    private fun chooseMode(mode: TunnelSearchMode) {
        setModeOpen(false)
        if (settingsError != null || mode == settings.mode) return
        val previous = settings
        settings = settings.withMode(mode)
        if (!persist()) settings = previous
        render()
    }

    private fun inside(root: Node, target: Node): Boolean =
        generateSequence(target) { it.parent }.any { it === root }

    private fun openRegion(region: TunnelRegion) {
        setModeOpen(false)
        selected = region
        cancelRound()
        catalogGen++
        val gen = catalogGen
        if (catalogs.containsKey(region)) {
            loading = null
            catalogError = null
            render()
            return
        }
        loading = region
        catalogError = null
        render()
        val deliver = deliver@{ result: Result<RoxyCatalog> ->
            if (gen != catalogGen || selected != region) return@deliver
            loading = null
            result.onSuccess {
                catalogs[region] = it
                catalogError = null
            }
            result.onFailure { catalogError = ctx.t("settings.tunnel.load_failed") }
            render()
        }
        if (TunnelServices.inline) {
            deliver(runCatching { TunnelServices.fetch(region) })
        } else {
            thread(name = "tunnel-catalog", isDaemon = true) {
                val result = runCatching { TunnelServices.fetch(region) }
                Platform.runLater { deliver(result) }
            }
        }
    }

    private fun reloadFiles() {
        val loaded = TunnelOverrides.loadSettings(TunnelServices.overridesFile())
        settings = loaded.first
        settingsError = loaded.second
        val cached = TunnelOverrides.loadCache(TunnelServices.statsFile())
        cache.endpoints.clear()
        cache.endpoints.putAll(cached.first.endpoints)
        cache.version = cached.first.version
    }

    private fun select(target: TunnelTarget) {
        if (settingsError != null) return
        val view = probes[target.key()] ?: return
        if (!view.selectable) return
        val previous = settings
        settings = settings.withOverride(
            TunnelSelection(target.region, target.pool, target.endpoint.name, target.endpoint.address),
        )
        if (!persist()) settings = previous
        render()
    }

    private fun toggleExclusion(pool: String) {
        if (settingsError != null) return
        val previous = settings
        settings = settings.withExclusion(selected, pool, excluded = !settings.isExcluded(selected, pool))
        if (!persist()) settings = previous
        render()
    }

    private fun persist(): Boolean = try {
        TunnelOverrides.saveSettings(TunnelServices.overridesFile(), settings)
        settingsError = null
        true
    } catch (_: Exception) {
        false
    }

    /** Every node on the open region, including excluded pools. A test fill only. */
    private fun regionTargets(catalog: RoxyCatalog): List<TunnelTarget> {
        val seen = HashSet<String>()
        val out = ArrayList<TunnelTarget>()
        for (pool in catalog.pools) {
            for (endpoint in pool.tunnels) {
                if (!seen.add(endpoint.address)) continue
                out += TunnelTarget(selected, pool.name, endpoint)
            }
        }
        return out
    }

    private fun measurePool(poolName: String) {
        val catalog = catalogs[selected] ?: return
        val pool = catalog.pools.firstOrNull { it.name == poolName } ?: return
        startRound(pool.tunnels.map { TunnelTarget(selected, pool.name, it) }) {}
    }

    private fun measureOpen() {
        val catalog = catalogs[selected] ?: return
        startRound(regionTargets(catalog)) {}
    }

    private fun freshSuccess(target: TunnelTarget): Boolean = probes[target.key()]?.freshSuccess == true

    /**
     * Ranks the open region and writes one address. Measures first when nothing in the candidate
     * set has a fresh success, and measures any candidate that has not been probed yet.
     */
    private fun findBest() {
        val catalog = catalogs[selected] ?: return
        val targets = catalog.targets(selected) { settings.isExcluded(selected, it) }
        if (targets.isEmpty()) return
        if (targets.none { freshSuccess(it) }) {
            startRound(targets) { applyWinner(catalog, targets) }
            return
        }
        val pending = targets.filter { probes[it.key()]?.received != true }
        if (pending.isNotEmpty()) startRound(pending) { applyWinner(catalog, targets) }
        else applyWinner(catalog, targets)
    }

    private fun applyWinner(catalog: RoxyCatalog, targets: List<TunnelTarget>) {
        if (settingsError != null) return
        val winner = cache.best(
            targets,
            probes,
            settings.mode,
            catalog.clientToTunnelRttWeight,
            TunnelServices.clock(),
            limit = 1,
        ).firstOrNull() ?: return
        val target = winner.target
        if (target.region != selected) return
        val previous = settings
        settings = settings.withOverride(
            TunnelSelection(target.region, target.pool, target.endpoint.name, target.endpoint.address),
        )
        if (!persist()) settings = previous
        render()
    }

    private fun cancelRound() {
        probeGen++
        measuring = false
    }

    private fun startRound(targets: List<TunnelTarget>, onFinished: () -> Unit) {
        if (targets.isEmpty()) {
            onFinished()
            render()
            return
        }
        probeGen++
        val gen = probeGen
        measuring = true
        val now = TunnelServices.clock()
        val ordered = cache.prioritize(targets, now)
        val limited = ordered.filter { cache.wasRecentlyLimited(it.region, it.endpoint.address, now) }.map { it.key() }.toSet()
        val delayOthers = limited.isNotEmpty()
        for (target in ordered) probes[target.key()] = NodeProbe()
        render()
        val pending = AtomicInteger(ordered.size)
        val finish = finish@{ target: TunnelTarget, outcome: TunnelProbeOutcome ->
            if (gen != probeGen) return@finish
            val at = TunnelServices.clock()
            cache.record(target, outcome, at)
            probes[target.key()] = outcome.toView()
            if (pending.decrementAndGet() == 0) {
                measuring = false
                cache.prune(at)
                runCatching { TunnelOverrides.saveCache(TunnelServices.statsFile(), cache) }
                onFinished()
            }
            render()
        }
        if (TunnelServices.inline) {
            for (target in ordered) {
                finish(target, runCatching { TunnelServices.probe(target.endpoint) }.getOrDefault(TunnelProbeOutcome.NoReply))
            }
            return
        }
        val pool = Executors.newFixedThreadPool(minOf(32, ordered.size)) { runnable ->
            Thread(runnable, "tunnel-probe").apply { isDaemon = true }
        }
        for (target in ordered) {
            pool.submit {
                if (delayOthers && target.key() !in limited) {
                    try {
                        Thread.sleep(TunnelOverrides.PRIORITY_HEAD_START_MS)
                    } catch (_: InterruptedException) {
                        return@submit
                    }
                }
                val outcome = runCatching { TunnelServices.probe(target.endpoint) }.getOrDefault(TunnelProbeOutcome.NoReply)
                Platform.runLater { finish(target, outcome) }
            }
        }
        pool.shutdown()
    }

    private fun render() {
        suppressRegion = true
        try {
            regionButtons.forEach { (button, region) -> button.isSelected = region == selected }
        } finally {
            suppressRegion = false
        }
        syncModeControl()
        val locked = settingsError != null
        val catalogReady = catalogs[selected] != null && loading != selected
        tunnelMeasure.isDisable = measuring || !catalogReady
        tunnelFindBest.isDisable = measuring || !catalogReady
        modeButton.isDisable = locked
        if (locked) setModeOpen(false)
        paintPools(locked)
    }

    private fun syncModeControl() {
        val label = modeLabel(settings.mode)
        modeValue.text = label
        modeButton.accessibleText = label
        modeList.children.filterIsInstance<Button>().forEach { item ->
            val on = item.userData == settings.mode
            if (on && "selected" !in item.styleClass) item.styleClass.add("selected")
            if (!on) item.styleClass.remove("selected")
        }
    }

    private fun paintPools(locked: Boolean) {
        val scroll = tunnelPools.parent?.parent as? javafx.scene.control.ScrollPane
        val saved = scroll?.vvalue ?: 0.0
        tunnelPools.children.clear()
        val catalog = catalogs[selected]
        when {
            loading == selected -> tunnelPools.children += note(ctx.t("settings.tunnel.loading"))
            catalog == null -> {
                tunnelPools.children += note(catalogError ?: ctx.t("settings.tunnel.loading"))
                if (catalogError != null) {
                    tunnelPools.children += Button(ctx.t("settings.tunnel.retry")).apply {
                        styleClass.add("settings-mini")
                        setOnAction {
                            catalogs.remove(selected)
                            catalogError = null
                            openRegion(selected)
                        }
                    }
                }
            }
            else -> {
                for (pool in catalog.pools) tunnelPools.children += poolBlock(pool, locked)
            }
        }
        if (scroll != null) scroll.vvalue = saved
    }

    private fun poolBlock(pool: dev.launcherfork.stalzone.TunnelPool, locked: Boolean): VBox {
        val excluded = settings.isExcluded(selected, pool.name)
        val title = Label(pool.name).apply {
            styleClass.add("pool-title")
            cursor = javafx.scene.Cursor.HAND
            tooltip = Tooltip(ctx.t("settings.tunnel.open"))
            setOnMouseClicked { measurePool(pool.name) }
        }
        val meta = Label(
            if (excluded) ctx.t("settings.tunnel.pool_excluded", pool.tunnels.size)
            else ctx.t("settings.tunnel.pool_meta", pool.tunnels.size),
        ).apply { styleClass.add("pool-meta") }
        val exclude = Button(if (excluded) ctx.t("settings.tunnel.excluded") else ctx.t("settings.tunnel.exclude")).apply {
            styleClass.add("settings-mini")
            isDisable = locked
            setOnAction { toggleExclusion(pool.name) }
        }
        val gap = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val head = HBox(title, meta, gap, exclude).apply {
            styleClass.add("pool-head")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        val list = VBox().apply {
            styleClass.add("tunnel-list")
            maxWidth = Double.MAX_VALUE
        }
        pool.tunnels.forEach { endpoint ->
            val target = TunnelTarget(selected, pool.name, endpoint)
            list.children += nodeButton(target, probes[target.key()] ?: NodeProbe(), locked)
        }
        markLastNode(list)
        return poolCard(VBox(2.0, head, list))
    }

    private fun poolCard(body: VBox) = body.apply {
        styleClass.addAll("pool", "card", "zone-radius-md")
        maxWidth = Double.MAX_VALUE
    }

    private fun markLastNode(list: VBox) {
        val nodes = list.children.filterIsInstance<Button>().filter { "tunnel-node" in it.styleClass }
        nodes.forEach { it.styleClass.remove("last") }
        nodes.lastOrNull()?.styleClass?.add("last")
    }

    private fun nodeButton(
        target: TunnelTarget,
        view: NodeProbe,
        locked: Boolean,
        prefix: String = "",
        forceLocked: Boolean = false,
    ): Button {
        val name = Label(prefix + target.endpoint.name).apply {
            styleClass.add("tunnel-row")
            textOverrun = OverrunStyle.ELLIPSIS
            maxWidth = Double.MAX_VALUE
        }
        HBox.setHgrow(name, Priority.ALWAYS)
        val (client, server, sum) = rttText(view)
        val row = HBox(8.0, name, rtt(client, "client"), rtt(server, "server"), rtt(sum, "sum")).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        val chosen = settings.override(target.region)?.address == target.endpoint.address
        return Button().apply {
            styleClass.add("tunnel-node")
            if (chosen) styleClass.add("selected")
            if (view.limitReached || forceLocked) styleClass.add("limited")
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER_LEFT
            graphic = row
            maxWidth = Double.MAX_VALUE
            userData = target.endpoint.address
            isDisable = locked || forceLocked || !view.selectable
            accessibleText = "${target.endpoint.name}. $client, $server, $sum"
            tooltip = Tooltip("${target.endpoint.address}\n$client, $server, $sum")
            setOnAction { select(target) }
        }
    }

    private fun rttText(view: NodeProbe): Triple<String, String, String> = when {
        !view.received -> Triple("—", "—", "—")
        view.failed -> Triple("—", "—", ctx.t("settings.tunnel.no_reply"))
        view.limitReached -> Triple(TunnelOverrides.formatMs(view.clientMs), TunnelOverrides.formatMs(view.serverMs), ctx.t("settings.tunnel.limit"))
        else -> Triple(TunnelOverrides.formatMs(view.clientMs), TunnelOverrides.formatMs(view.serverMs), TunnelOverrides.formatMs(view.totalMs))
    }

    private fun rtt(text: String, kind: String) = Label(text).apply {
        styleClass.addAll("tunnel-ping", kind)
        alignment = Pos.CENTER_RIGHT
        minWidth = 52.0
        prefWidth = 52.0
    }

    private fun note(text: String) = Label(text).apply {
        styleClass.add("settings-note")
        isWrapText = true
    }

    private fun modeLabel(mode: TunnelSearchMode): String = when (mode) {
        TunnelSearchMode.PING -> ctx.t("settings.tunnel.mode.ping")
        TunnelSearchMode.CLIENT_RTT -> ctx.t("settings.tunnel.mode.client")
        TunnelSearchMode.SERVER_RTT -> ctx.t("settings.tunnel.mode.server")
        TunnelSearchMode.STABILITY -> ctx.t("settings.tunnel.mode.stability")
        TunnelSearchMode.GAME -> ctx.t("settings.tunnel.mode.game")
    }
}
