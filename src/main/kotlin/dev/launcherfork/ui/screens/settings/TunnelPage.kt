package dev.launcherfork.ui.screens.settings

import dev.launcherfork.stalzone.NodeProbe
import dev.launcherfork.stalzone.RankedTarget
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
 * «Обновить» reloads the catalog and keeps only nodes that answer. A node that does not answer
 * leaves the pool; a later answer puts it back.
 */
class TunnelPage(
    private val ctx: UiContext,
    private val regionGroup: ToggleGroup,
    private val regionButtons: Map<RadioButton, TunnelRegion>,
    private val tunnelPools: VBox,
    private val tunnelMeasure: Button,
    private val tunnelFindBest: Button,
    private val tunnelRefresh: Button,
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
    private var refreshing = false
    private val hidden = HashSet<String>()
    private val awaiting = HashSet<String>()
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
        tunnelRefresh.tooltip = Tooltip(ctx.t("settings.tunnel.refresh.hint"))
        tunnelMeasure.setOnAction { measureOpen() }
        tunnelFindBest.setOnAction { findBest() }
        tunnelRefresh.setOnAction { refreshServers() }
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
        refreshing = false
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
            startRound(targets) { applyWinner(targets) }
            return
        }
        val pending = targets.filter { probes[it.key()]?.received != true }
        if (pending.isNotEmpty()) startRound(pending) { applyWinner(targets) }
        else applyWinner(targets)
    }

    private fun applyWinner(targets: List<TunnelTarget>) {
        if (settingsError != null) return
        val winner = cache.best(
            targets,
            probes,
            settings.mode,
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
        hidden.addAll(awaiting)
        awaiting.clear()
        probeGen++
        measuring = false
    }

    /**
     * Reloads the open region. Servers that were not in the previous list stay hidden until they
     * answer. A miss, this round or the last saved sample, drops the node from the pool.
     */
    private fun refreshServers() {
        if (measuring || refreshing || catalogs[selected] == null) return
        setModeOpen(false)
        cancelRound()
        val region = selected
        catalogGen++
        val gen = catalogGen
        refreshing = true
        render()
        val deliver = deliver@{ result: Result<RoxyCatalog> ->
            if (gen != catalogGen || selected != region) {
                refreshing = false
                return@deliver
            }
            refreshing = false
            result.onSuccess { fresh ->
                val known = catalogs[region]?.pools.orEmpty().flatMap { it.tunnels }.map { it.address }.toSet()
                catalogs[region] = fresh
                catalogError = null
                val targets = regionTargets(fresh)
                for (target in targets) {
                    if (target.endpoint.address !in known) awaiting += target.key()
                }
                startRound(targets) {}
            }
            result.onFailure {
                if (catalogs[region] == null) catalogError = ctx.t("settings.tunnel.load_failed")
                render()
            }
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
            when (outcome) {
                is TunnelProbeOutcome.Reply -> {
                    hidden.remove(target.key())
                    awaiting.remove(target.key())
                }
                TunnelProbeOutcome.NoReply -> {
                    hidden.add(target.key())
                    awaiting.remove(target.key())
                }
            }
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
        val busy = measuring || refreshing || !catalogReady
        tunnelMeasure.isDisable = busy
        tunnelFindBest.isDisable = busy
        tunnelRefresh.isDisable = busy
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
                val best = recommendation(catalog)
                val blocks = catalog.pools.mapNotNull { poolBlock(it, locked, best?.target?.endpoint?.address) }
                if (blocks.isEmpty()) tunnelPools.children += note(ctx.t("settings.tunnel.no_working"))
                else {
                    tunnelPools.children += metricsLegend(best)
                    tunnelPools.children += scaleLegend()
                    tunnelPools.children += columnLegend()
                    tunnelPools.children.addAll(blocks)
                }
            }
        }
        if (scroll != null) scroll.vvalue = saved
    }

    /** What the three columns are, which one the current mode minimizes, and who would win now. */
    private fun metricsLegend(best: RankedTarget?): Label {
        val how = when (settings.mode) {
            TunnelSearchMode.PING -> "settings.tunnel.metrics.ping"
            TunnelSearchMode.CLIENT_RTT -> "settings.tunnel.metrics.client"
            TunnelSearchMode.SERVER_RTT -> "settings.tunnel.metrics.server"
            TunnelSearchMode.STABILITY -> "settings.tunnel.metrics.stability"
        }
        val text = buildString {
            append(ctx.t("settings.tunnel.metrics"))
            append(' ')
            append(ctx.t(how))
            if (best != null) {
                append(' ')
                append(ctx.t("settings.tunnel.winner", best.target.endpoint.name))
            }
        }
        return Label(text).apply {
            styleClass.add("tunnel-metrics")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
    }

    /** Green under 50 ms, amber under 100, red above. */
    private fun scaleLegend(): HBox {
        fun chip(key: String, kind: String) = Label(ctx.t(key)).apply { styleClass.addAll("tunnel-scale", kind) }
        return HBox(
            12.0,
            chip("settings.tunnel.scale.good", "good"),
            chip("settings.tunnel.scale.mid", "mid"),
            chip("settings.tunnel.scale.bad", "bad"),
        ).apply {
            styleClass.add("tunnel-scale-row")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
    }

    /** Column names over the three figures: hop to the tunnel, hop to the game, their sum. */
    private fun columnLegend(): HBox {
        val decision = decisionKind()
        fun caption(key: String, kind: String) = Label(ctx.t(key)).apply {
            styleClass.addAll("tunnel-legend", kind)
            if (kind == decision) styleClass.add("decision")
            alignment = Pos.CENTER_RIGHT
            tooltip = Tooltip(ctx.t("$key.hint"))
            minWidth = VALUE_COLUMN
            prefWidth = VALUE_COLUMN
            maxWidth = VALUE_COLUMN
        }
        val gap = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        return HBox(
            8.0,
            gap,
            caption("settings.tunnel.col.client", "client"),
            caption("settings.tunnel.col.server", "server"),
            caption("settings.tunnel.col.sum", "sum"),
        ).apply {
            styleClass.add("tunnel-legend-row")
            alignment = Pos.CENTER_RIGHT
            maxWidth = Double.MAX_VALUE
        }
    }

    /** The column [findBest] minimizes. Stability still reads the ping column; the note says why. */
    private fun decisionKind(): String = when (settings.mode) {
        TunnelSearchMode.CLIENT_RTT -> "client"
        TunnelSearchMode.SERVER_RTT -> "server"
        else -> "sum"
    }

    /** The node [findBest] would write: lowest score among replies that are not at the limit. */
    private fun recommendation(catalog: RoxyCatalog): RankedTarget? {
        val targets = catalog.targets(selected) { settings.isExcluded(selected, it) }
        return cache.best(targets, probes, settings.mode, TunnelServices.clock(), limit = 1).firstOrNull()
    }

    /** A miss this session, a node still waiting on refresh, or a saved timeout. */
    private fun inactive(address: String): Boolean {
        val key = TunnelOverrides.cacheKey(selected, address)
        val view = probes[key]
        if (view != null && view.received) return view.failed
        if (key in hidden || key in awaiting) return true
        val last = cache.endpoints[key]?.samples?.maxByOrNull { it.measuredAt } ?: return false
        return last.timedOut
    }

    private fun poolBlock(pool: dev.launcherfork.stalzone.TunnelPool, locked: Boolean, bestAddress: String?): VBox? {
        val visible = pool.tunnels.filter { !inactive(it.address) }
        if (visible.isEmpty()) return null
        val excluded = settings.isExcluded(selected, pool.name)
        val title = Label(pool.name).apply {
            styleClass.add("pool-title")
            cursor = javafx.scene.Cursor.HAND
            tooltip = Tooltip(ctx.t("settings.tunnel.open"))
            setOnMouseClicked { measurePool(pool.name) }
        }
        val meta = Label(
            if (excluded) ctx.t("settings.tunnel.pool_excluded", visible.size)
            else ctx.t("settings.tunnel.pool_meta", visible.size),
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
        visible.forEach { endpoint ->
            val target = TunnelTarget(selected, pool.name, endpoint)
            list.children += nodeButton(
                target,
                probes[target.key()] ?: NodeProbe(),
                locked,
                recommended = endpoint.address == bestAddress && !excluded,
            )
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
        recommended: Boolean = false,
    ): Button {
        val shown = figures(target, view)
        val name = Label(prefix + target.endpoint.name).apply {
            styleClass.add("tunnel-row")
            textOverrun = OverrunStyle.ELLIPSIS
            maxWidth = Double.MAX_VALUE
        }
        HBox.setHgrow(name, Priority.ALWAYS)
        val (client, server, sum) = rttText(shown)
        val decision = decisionKind()
        val parts = ArrayList<javafx.scene.Node>()
        parts += name
        if (recommended) {
            parts += Label(ctx.t("settings.tunnel.pick")).apply { styleClass.add("tunnel-pick") }
        }
        parts += rtt(client, "client", if (shown.received && !shown.failed) shown.clientMs else null, decision)
        parts += rtt(server, "server", if (shown.received && !shown.failed) shown.serverMs else null, decision)
        parts += rtt(sum, "sum", if (shown.received && !shown.failed && !shown.limitReached) shown.totalMs else null, decision)
        val row = HBox(8.0, *parts.toTypedArray()).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        val chosen = settings.override(target.region)?.address == target.endpoint.address
        val clientName = ctx.t("settings.tunnel.col.client")
        val serverName = ctx.t("settings.tunnel.col.server")
        val sumName = ctx.t("settings.tunnel.col.sum")
        val quality = qualityText(target, shown)
        return Button().apply {
            styleClass.add("tunnel-node")
            if (chosen) styleClass.add("selected")
            if (recommended) styleClass.add("best")
            if (view.limitReached || forceLocked) styleClass.add("limited")
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER_LEFT
            graphic = row
            maxWidth = Double.MAX_VALUE
            userData = target.endpoint.address
            isDisable = locked || forceLocked || !view.selectable
            accessibleText = "${target.endpoint.name}. $clientName $client, $serverName $server, $sumName $sum"
            tooltip = Tooltip("${target.endpoint.address}\n$clientName $client, $serverName $server, $sumName $sum$quality")
            setOnAction { select(target) }
        }
    }

    /** The row shows the same hour average that ranking uses. One fresh sample is that sample. */
    private fun figures(target: TunnelTarget, view: NodeProbe): NodeProbe {
        if (!view.received || view.failed || view.limitReached) return view
        val metrics = cache.metrics(target.region, target.endpoint.address, TunnelServices.clock()) ?: return view
        if (metrics.successes == 0) return view
        return view.copy(clientMs = metrics.clientRtt, serverMs = metrics.serverRtt)
    }

    private fun qualityText(target: TunnelTarget, view: NodeProbe): String {
        if (!view.received || view.failed) return ""
        val metrics = cache.metrics(target.region, target.endpoint.address, TunnelServices.clock()) ?: return ""
        if (metrics.samples == 0) return ""
        val loss = String.format(java.util.Locale.US, "%.0f%%", metrics.lossRate * 100.0)
        return "\n" + ctx.t("settings.tunnel.quality", loss, TunnelOverrides.formatMs(metrics.jitter))
    }

    private fun rttText(view: NodeProbe): Triple<String, String, String> = when {
        !view.received -> Triple("—", "—", "—")
        view.failed -> Triple("—", "—", ctx.t("settings.tunnel.no_reply"))
        view.limitReached -> Triple(TunnelOverrides.formatMs(view.clientMs), TunnelOverrides.formatMs(view.serverMs), ctx.t("settings.tunnel.limit"))
        else -> Triple(TunnelOverrides.formatMs(view.clientMs), TunnelOverrides.formatMs(view.serverMs), TunnelOverrides.formatMs(view.totalMs))
    }

    private fun rtt(text: String, kind: String, ms: Double?, decision: String) = Label(text).apply {
        styleClass.addAll("tunnel-ping", kind)
        if (ms != null && ms >= 0.0) styleClass.add(TunnelOverrides.latencyBand(ms))
        if (kind == decision) styleClass.add("decision")
        alignment = Pos.CENTER_RIGHT
        minWidth = VALUE_COLUMN
        prefWidth = VALUE_COLUMN
        maxWidth = VALUE_COLUMN
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
    }

    private companion object {
        const val VALUE_COLUMN = 64.0
    }
}
