package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.ServerBlockState
import dev.launcherfork.stalzone.FirewallNotElevated
import dev.launcherfork.stalzone.NodeProbe
import dev.launcherfork.stalzone.RankedTarget
import dev.launcherfork.stalzone.RoxyCatalog
import dev.launcherfork.stalzone.ServerBlock
import dev.launcherfork.stalzone.StalzonePaths
import dev.launcherfork.stalzone.TunnelAddresses
import dev.launcherfork.stalzone.TunnelCache
import dev.launcherfork.stalzone.TunnelCatalog
import dev.launcherfork.stalzone.TunnelCatalogs
import dev.launcherfork.stalzone.TunnelEndpoint
import dev.launcherfork.stalzone.TunnelFirewall
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
import dev.launcherfork.ui.motion.Motion
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Group
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
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.SVGPath
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Tests point [fetch] and [probe] at fakes. Launch does not use this object. */
internal object TunnelServices {
    var directory: Path? = null
    var inline: Boolean = false
    /**
     * Mock and showcase read [TunnelCatalogs.bundled]. Live leaves this false and keeps the HTTPS list.
     * Read by the default [fetch] at call time, so a test that replaces [fetch] is left alone.
     */
    var mockCatalog: Boolean = false
    /**
     * Opening a region probes it before any row is drawn. A miss is not listed.
     * Tests leave this false so the catalog stays visible without a socket.
     */
    var probeOnLoad: Boolean = true
    var fetch: (TunnelRegion) -> RoxyCatalog = { region -> defaultFetch(region) }
    var probe: (TunnelEndpoint) -> TunnelProbeOutcome = { endpoint -> TunnelProbe.measure(endpoint) }
    var clock: () -> Instant = { Instant.now() }

    /**
     * Firewall write used by the tunnel page. The list is reduced with [ServerBlock.uniqueValidIpv4]
     * again inside [defaultApply]. Tests replace this; production calls [TunnelFirewall.apply].
     */
    var applyBlocks: (List<String>) -> Result<String> = { ips -> defaultApply(ips) }
    var clearBlocks: () -> Result<Int> = { TunnelFirewall.system().clear() }

    /**
     * Runs on the firewall worker before the write is allowed to start.
     * Tests hold it to overlap two commits. Production leaves it empty.
     */
    var beforeFirewallWrite: () -> Unit = {}

    fun overridesFile(): Path = (directory ?: StalzonePaths.jvmWrapperDir()).resolve("overrides.json")

    fun statsFile(): Path = (directory ?: StalzonePaths.jvmWrapperDir()).resolve("cache").resolve("tunnel_stats.json")

    fun reset() {
        directory = null
        inline = false
        mockCatalog = false
        probeOnLoad = true
        fetch = { region -> defaultFetch(region) }
        probe = { endpoint -> TunnelProbe.measure(endpoint) }
        clock = { Instant.now() }
        applyBlocks = { ips -> defaultApply(ips) }
        clearBlocks = { TunnelFirewall.system().clear() }
        beforeFirewallWrite = {}
    }

    /** Existing firewall entry: invalid addresses never become rules. */
    private fun defaultApply(ips: List<String>): Result<String> {
        val unique = ServerBlock.uniqueValidIpv4(ips)
        if (unique.isEmpty()) return Result.failure(IllegalArgumentException("No valid IPv4 addresses to block"))
        return TunnelFirewall.system().apply(unique)
    }

    private fun defaultFetch(region: TunnelRegion): RoxyCatalog =
        if (mockCatalog) TunnelCatalogs.bundled(region) ?: error("mock catalog ${region.id}")
        else TunnelCatalogs.fetch(region)
}

/**
 * One Roxy node per region. Opening the region loads that list. Opening a group measures its nodes
 * once. «Измерить» measures the open region and does not write an override. «Найти лучший сервер»
 * measures when this region has no fresh success, then writes the single best node.
 * The refresh icon (accessible name «Обновить») reloads the catalog. A miss drops the row.
 * A node that was not in the previous list stays hidden until it answers.
 * Opening a region probes before drawing, so a dead node is not shown.
 * The group button and each row button read «Блокировать» or «Разблокировать» and write immediately.
 * Addresses go through [ServerBlock.uniqueValidIpv4] and the existing firewall apply. There is no confirm bar.
 * Each row has a status column, «Открыт» or «Закрыт». A partial group shows «N блок». Selection is not a block.
 * [TunnelPresentation] drops CLOUD-* pools and documentation addresses before the list, Measure, or Find best.
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
    private var blockState = ServerBlockState()
    private var settingsError: String? = null
    private var firewallError: String? = null
    private val firewallGen = AtomicInteger()
    /** One firewall mutation at a time. A newer commit skips any write that has not started. */
    private val firewallGate = Any()
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
    /** True from the refresh press until that reload, including the probe round it starts, has settled. */
    private var refreshSpinning = false
    /** Lucide refresh-cw 0.544.0 (ISC): two circular arrows, 24×24 stroke grid. */
    private val refreshIcon = StackPane(Group(SVGPath().apply {
        content = "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" +
            "M21 3v5h-5" +
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" +
            "M8 16H3v5"
        styleClass.add("tunnel-refresh-glyph")
        scaleX = 16.0 / 24.0
        scaleY = 16.0 / 24.0
        isMouseTransparent = true
    })).apply {
        styleClass.add("tunnel-refresh-icon")
        setMinSize(16.0, 16.0)
        setPrefSize(16.0, 16.0)
        setMaxSize(16.0, 16.0)
        isMouseTransparent = true
    }
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
    private val firewallNote = Label().apply {
        styleClass.addAll("settings-note", "tunnel-firewall-note")
        isWrapText = true
        isVisible = false
        isManaged = false
        maxWidth = Double.MAX_VALUE
    }

    fun bind() {
        reloadFiles()
        tunnelMeasure.tooltip = Tooltip(ctx.t("settings.tunnel.measure.hint"))
        tunnelFindBest.tooltip = Tooltip(ctx.t("settings.tunnel.find.hint"))
        tunnelRefresh.text = ""
        tunnelRefresh.contentDisplay = ContentDisplay.GRAPHIC_ONLY
        tunnelRefresh.alignment = Pos.CENTER
        tunnelRefresh.graphic = refreshIcon
        tunnelRefresh.accessibleText = ctx.t("settings.tunnel.refresh")
        tunnelRefresh.tooltip = Tooltip(ctx.t("settings.tunnel.refresh.hint"))
        tunnelMeasure.setOnAction { measureOpen() }
        tunnelFindBest.setOnAction { findBest() }
        tunnelRefresh.setOnAction { refreshServers() }
        mountModeList()
        mountNotes()
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

    private fun mountNotes() {
        if (firewallNote.parent != null) return
        val page = tunnelMode.parent?.parent as? VBox ?: return
        val scroll = page.children.filterIsInstance<javafx.scene.control.ScrollPane>().firstOrNull() ?: return
        val noteAt = page.children.indexOf(scroll)
        if (noteAt >= 0) page.children.add(noteAt, firewallNote)
    }

    private fun openRegion(region: TunnelRegion) {
        setModeOpen(false)
        refreshing = false
        refreshSpinning = false
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
        // A confirming probe keeps the loading card until only answering nodes remain.
        // Tests that leave probeOnLoad false still paint the classpath snapshot at once.
        val seeded = if (TunnelServices.mockCatalog && !TunnelServices.probeOnLoad) TunnelCatalogs.bundled(region) else null
        if (seeded != null) {
            catalogs[region] = TunnelPresentation.present(seeded)
            loading = null
            catalogError = null
        } else {
            loading = region
            catalogError = null
        }
        render()
        val deliver = deliver@{ result: Result<RoxyCatalog> ->
            if (gen != catalogGen || selected != region) return@deliver
            result.onSuccess { fresh ->
                val shown = TunnelPresentation.present(fresh)
                catalogs[region] = shown
                catalogError = null
                if (TunnelServices.probeOnLoad) {
                    val targets = regionTargets(shown)
                    if (targets.isNotEmpty()) {
                        loading = region
                        render()
                        startRound(targets) {
                            if (gen == catalogGen && selected == region) loading = null
                        }
                        return@deliver
                    }
                }
                loading = null
            }
            result.onFailure {
                loading = null
                catalogError = ctx.t("settings.tunnel.load_failed")
            }
            render()
        }
        loadCatalog(region, deliver)
    }

    /** [TunnelServices.inline] delivers on this thread. Live fetch stays off the FX thread. */
    private fun loadCatalog(region: TunnelRegion, deliver: (Result<RoxyCatalog>) -> Unit) {
        val run = { runCatching { TunnelServices.fetch(region) } }
        if (TunnelServices.inline) {
            deliver(run())
        } else {
            thread(name = "tunnel-catalog", isDaemon = true) {
                val result = run()
                Platform.runLater { deliver(result) }
            }
        }
    }

    private fun reloadFiles() {
        val loaded = TunnelOverrides.loadSettings(TunnelServices.overridesFile())
        settings = loaded.first
        settingsError = loaded.second
        blockState = ctx.services.settings.load().serverBlock
        val cached = TunnelOverrides.loadCache(TunnelServices.statsFile())
        cache.endpoints.clear()
        cache.endpoints.putAll(cached.first.endpoints)
        cache.version = cached.first.version
    }

    private fun select(target: TunnelTarget) {
        if (settingsError != null) return
        if (settings.isExcluded(target.region, target.pool)) return
        if (nodeBlocked(target)) return
        val view = probes[target.key()] ?: return
        if (!view.selectable) return
        val previous = settings
        settings = settings.withOverride(
            TunnelSelection(target.region, target.pool, target.endpoint.name, target.endpoint.address),
        )
        if (!persist()) settings = previous
        render()
    }

    /** Group button writes every node in the pool, then the firewall. The same path opens them again. */
    private fun requestGroupExclusion(pool: dev.launcherfork.stalzone.TunnelPool) {
        if (settingsError != null) return
        val nodes = pool.tunnels.filter { ipv4Host(it.address) != null }
        if (nodes.isEmpty()) return
        val shown = pool.tunnels.filter { showNode(it) }
        val fullyBlocked = shown.isNotEmpty() && shown.all { nodeId(pool.name, it.name) in blockState.blocked }
        val excluding = !settings.isExcluded(selected, pool.name) && !fullyBlocked
        applyGroupExclusion(pool.name, nodes, excluding)
    }

    private fun applyGroupExclusion(pool: String, nodes: List<TunnelEndpoint>, excluding: Boolean) {
        val previous = settings
        settings = settings.withExclusion(selected, pool, excluded = excluding)
        if (!persist()) {
            settings = previous
            return
        }
        val ids = nodes.map { nodeId(pool, it.name) }.toSet()
        val next = if (excluding) (blockState.blocked + ids).distinct() else blockState.blocked.filter { it !in ids }
        if (!saveBlock(next)) {
            settings = previous
            persist()
            return
        }
        render()
        commitFirewall(next)
    }

    /** One server, no dialog. Membership changes immediately; the firewall sync uses the same host filter. */
    private fun toggleNodeBlock(target: TunnelTarget) {
        if (settingsError != null) return
        val id = nodeId(target.pool, target.endpoint.name)
        val next = if (id in blockState.blocked) blockState.blocked.filter { it != id } else (blockState.blocked + id).distinct()
        if (!saveBlock(next)) {
            render()
            return
        }
        render()
        commitFirewall(next)
    }

    private fun saveBlock(ids: List<String>): Boolean = try {
        val next = blockState.copy(blocked = ids.distinct(), mode = ServerBlock.MODE)
        ctx.services.settings.save(ctx.services.settings.load().copy(serverBlock = next))
        blockState = ctx.services.settings.load().serverBlock
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Full blocklist, not a hand-built script. Hosts come from [ServerBlock.resolveBlockedHosts]
     * (which keeps [ServerBlock.uniqueValidIpv4] only) and [TunnelFirewall.apply] filters again.
     * An empty list clears the existing rules. A group button and a row button both reach this.
     *
     * Writes are serialized. A commit that has not started is dropped when a newer one exists,
     * so a slow apply cannot put an older list back after the user has already changed it.
     */
    private fun commitFirewall(ids: List<String>) {
        val unique = ServerBlock.uniqueValidIpv4(ServerBlock.resolveBlockedHosts(knownServers(), ids))
        val gen = firewallGen.incrementAndGet()
        val run = {
            if (unique.isEmpty()) TunnelServices.clearBlocks().map { "" }
            else TunnelServices.applyBlocks(unique)
        }
        val deliver = deliver@{ result: Result<String> ->
            if (gen != firewallGen.get()) return@deliver
            firewallError = result.fold(
                onSuccess = { null },
                onFailure = { error ->
                    if (error is FirewallNotElevated) ctx.t("settings.tunnel.admin")
                    else ctx.t("settings.tunnel.failed", error.message ?: "")
                },
            )
            render()
        }
        val execute = {
            TunnelServices.beforeFirewallWrite()
            synchronized(firewallGate) {
                if (gen != firewallGen.get()) null else run()
            }
        }
        if (TunnelServices.inline) {
            execute()?.let(deliver)
        } else {
            thread(name = "tunnel-firewall", isDaemon = true) {
                val result = execute() ?: return@thread
                Platform.runLater { deliver(result) }
            }
        }
    }

    private fun knownServers(): List<TunnelCatalog.Server> {
        val live = ArrayList<TunnelCatalog.Server>()
        for ((region, catalog) in catalogs) {
            for (pool in catalog.pools) {
                for (endpoint in pool.tunnels) {
                    val parsed = TunnelAddresses.parse(endpoint.address) ?: continue
                    live += TunnelCatalog.Server(
                        id = nodeId(pool.name, endpoint.name),
                        pool = pool.name,
                        name = endpoint.name,
                        address = endpoint.address,
                        host = parsed.first,
                        port = parsed.second,
                        region = region.name,
                    )
                }
            }
        }
        val seen = live.map { it.id }.toSet()
        return live + TunnelCatalog.servers().filter { it.id !in seen }
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
            if (TunnelPresentation.unlistedPool(pool.name)) continue
            for (endpoint in pool.tunnels) {
                if (ipv4Host(endpoint.address) == null) continue
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
        val targets = candidates(catalog)
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
     * Reloads the open region. A miss drops the row. A server that was not in the previous list
     * stays hidden until it answers.
     */
    private fun refreshServers() {
        if (measuring || refreshing || catalogs[selected] == null) return
        setModeOpen(false)
        cancelRound()
        val region = selected
        catalogGen++
        val gen = catalogGen
        refreshSpinning = true
        refreshing = true
        render()
        val deliver = deliver@{ result: Result<RoxyCatalog> ->
            if (gen != catalogGen || selected != region) {
                refreshing = false
                refreshSpinning = false
                render()
                return@deliver
            }
            refreshing = false
            result.onSuccess { fresh ->
                val shown = TunnelPresentation.present(fresh)
                val known = catalogs[region]?.pools.orEmpty().flatMap { it.tunnels }.map { it.address }.toSet()
                catalogs[region] = shown
                catalogError = null
                val targets = regionTargets(shown)
                for (target in targets) {
                    if (target.endpoint.address !in known) awaiting += target.key()
                }
                startRound(targets) { refreshSpinning = false }
            }
            result.onFailure {
                refreshSpinning = false
                if (catalogs[region] == null) catalogError = ctx.t("settings.tunnel.load_failed")
            }
            render()
        }
        loadCatalog(region, deliver)
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
        Motion.spin(refreshIcon, refreshSpinning)
        modeButton.isDisable = locked
        if (locked) setModeOpen(false)
        firewallNote.text = firewallError.orEmpty()
        firewallNote.isVisible = firewallError != null
        firewallNote.isManaged = firewallError != null
        mountNotes()
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
            loading == selected -> tunnelPools.children += statusCard(
                "loading",
                "settings.tunnel.loading",
                "settings.tunnel.loading.detail",
            )
            catalog == null && catalogError != null -> tunnelPools.children += statusCard(
                "err",
                "settings.tunnel.load_failed",
                "settings.tunnel.load_failed.detail",
                retry = true,
            )
            catalog == null -> tunnelPools.children += statusCard(
                "empty",
                "settings.tunnel.empty",
                "settings.tunnel.empty.detail",
                retry = true,
            )
            else -> {
                val best = recommendation(catalog)
                val blocks = catalog.pools.mapNotNull { poolBlock(it, locked, best?.target?.endpoint?.address) }
                if (blocks.isEmpty()) {
                    tunnelPools.children += statusCard(
                        "empty",
                        "settings.tunnel.no_working",
                        "settings.tunnel.no_working.detail",
                    )
                } else {
                    tunnelPools.children.addAll(blocks)
                }
            }
        }
        if (scroll != null) scroll.vvalue = saved
    }

    /**
     * Loading, failure and an empty catalog are one card in the list, not a caption over a blank pane.
     * [retry] is «Повторить» / «Загрузить» and is the only control that starts another fetch.
     */
    private fun statusCard(kind: String, titleKey: String, detailKey: String, retry: Boolean = false): VBox {
        val title = Label(ctx.t(titleKey)).apply {
            styleClass.add("tunnel-status-title")
            isWrapText = true
            alignment = Pos.CENTER
            textAlignment = javafx.scene.text.TextAlignment.CENTER
            maxWidth = Double.MAX_VALUE
        }
        val detail = Label(ctx.t(detailKey)).apply {
            styleClass.add("tunnel-status-detail")
            isWrapText = true
            alignment = Pos.CENTER
            textAlignment = javafx.scene.text.TextAlignment.CENTER
            maxWidth = 460.0
        }
        return VBox(8.0, title, detail).apply {
            styleClass.addAll("tunnel-status", "card", "zone-radius-md", kind)
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
            val pane = tunnelPools.parent?.parent as? javafx.scene.control.ScrollPane
            val viewport = when {
                pane == null -> 0.0
                pane.height > 80.0 -> pane.height
                pane.prefHeight > 80.0 -> pane.prefHeight
                else -> 0.0
            }
            if (viewport > 80.0) {
                prefHeight = viewport - 4.0
                minHeight = prefHeight
            }
            if (retry) {
                children += Button(ctx.t(if (kind == "err") "settings.tunnel.retry" else "settings.tunnel.load")).apply {
                    styleClass.addAll("settings-mini", "tunnel-status-retry")
                    isMnemonicParsing = false
                    setOnAction {
                        catalogs.remove(selected)
                        catalogError = null
                        openRegion(selected)
                    }
                }
            }
        }
    }

    /**
     * Column captions for one pool: name | Статус | До вас | Пинг | action.
     * The same shell as each server row, inside the card, so the captions sit on the value cells.
     * The hop to the game stays in the tooltip.
     */
    private fun columnLegend(): HBox {
        val decision = decisionKind()
        fun caption(key: String, kind: String) = Label(ctx.t(key)).apply {
            styleClass.addAll("tunnel-legend", kind)
            if (kind == decision) styleClass.add("decision")
            alignment = Pos.CENTER_RIGHT
            textOverrun = OverrunStyle.ELLIPSIS
            tooltip = Tooltip(ctx.t("$key.hint"))
        }
        return columnRow(
            valueStrip(
                Region(),
                caption("settings.tunnel.col.status", "status"),
                caption("settings.tunnel.col.client", "client"),
                caption("settings.tunnel.col.sum", "sum"),
            ),
            Region().apply { styleClass.add("tunnel-action-slot") },
        ).apply {
            styleClass.add("tunnel-legend-row")
        }
    }

    /** Growing name cell, the status column, then two fixed value columns. */
    private fun valueStrip(name: Node, status: Node, client: Node, sum: Node): HBox {
        if (name is Region) {
            name.minWidth = 0.0
            name.maxWidth = Double.MAX_VALUE
        }
        if (status is Region) pinWidth(status, STATUS_COLUMN)
        if (client is Region) pinWidth(client, VALUE_COLUMN)
        if (sum is Region) pinWidth(sum, VALUE_COLUMN)
        HBox.setHgrow(name, Priority.ALWAYS)
        return HBox(COLUMN_GAP, name, status, client, sum).apply {
            styleClass.add("tunnel-values")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
    }

    /** Shared row: the value strip, then [TOGGLE_GAP], then the block-action column. */
    private fun columnRow(values: Node, action: Node): HBox {
        if (action is Region) pinWidth(action, ACTION_COLUMN)
        HBox.setHgrow(values, Priority.ALWAYS)
        return HBox(TOGGLE_GAP, values, action).apply {
            styleClass.add("tunnel-columns")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
    }

    private fun pinWidth(node: Region, width: Double) {
        node.minWidth = width
        node.prefWidth = width
        node.maxWidth = width
    }

    /** The column [findBest] minimizes. Stability still reads the ping column. */
    private fun decisionKind(): String = when (settings.mode) {
        TunnelSearchMode.CLIENT_RTT -> "client"
        TunnelSearchMode.SERVER_RTT -> "server"
        else -> "sum"
    }

    /** The node [findBest] would write: lowest score among replies that are not at the limit. */
    private fun recommendation(catalog: RoxyCatalog): RankedTarget? {
        val targets = candidates(catalog)
        return cache.best(targets, probes, settings.mode, TunnelServices.clock(), limit = 1).firstOrNull()
    }

    /** Find-best and the recommendation skip excluded pools, dead fixtures, blocked nodes and addresses with no IPv4. */
    private fun candidates(catalog: RoxyCatalog): List<TunnelTarget> =
        catalog.targets(selected) { settings.isExcluded(selected, it) || TunnelPresentation.unlistedPool(it) }
            .filter { ipv4Host(it.endpoint.address) != null && !nodeBlocked(it) }

    /**
     * Shown when the address has a valid IPv4 and the latest probe was not a miss.
     * A server that appeared in this refresh and has not answered yet stays hidden.
     */
    private fun showNode(endpoint: TunnelEndpoint): Boolean {
        val host = ipv4Host(endpoint.address)
        val key = TunnelOverrides.cacheKey(selected, endpoint.address)
        val view = probes[key]
        val history = cache.endpoints[key]
        val limited = view?.limitReached == true || history?.limitReached == true
        val closed = (view != null && view.received && view.failed) ||
            history?.samples?.maxByOrNull { it.measuredAt }?.timedOut == true
        if (host == null && (limited || closed)) return false
        if (host == null) return false
        if (key in hidden || key in awaiting) return false
        return true
    }

    private fun ipv4Host(address: String): String? {
        val host = TunnelAddresses.parse(address)?.first ?: return null
        return host.takeIf { ServerBlock.isValidIpv4(it) }
    }

    private fun nodeId(pool: String, name: String): String = "$pool::$name"

    private fun nodeBlocked(target: TunnelTarget): Boolean =
        nodeId(target.pool, target.endpoint.name) in blockState.blocked

    private fun poolBlock(pool: dev.launcherfork.stalzone.TunnelPool, locked: Boolean, bestAddress: String?): VBox? {
        val visible = pool.tunnels.filter { showNode(it) }
        if (visible.isEmpty()) return null
        val excluded = settings.isExcluded(selected, pool.name)
        val title = Label(pool.name).apply {
            styleClass.add("pool-title")
            minWidth = 0.0
            textOverrun = OverrunStyle.ELLIPSIS
            cursor = javafx.scene.Cursor.HAND
            tooltip = Tooltip(ctx.t("settings.tunnel.open"))
            setOnMouseClicked { measurePool(pool.name) }
        }
        val meta = Label(ctx.t("settings.tunnel.pool_meta", visible.size)).apply { styleClass.add("pool-meta") }
        val blockedCount = visible.count { nodeId(pool.name, it.name) in blockState.blocked }
        val fullyBlocked = visible.isNotEmpty() && blockedCount == visible.size
        val closed = excluded || fullyBlocked
        val excludeLabel = when {
            excluded -> "settings.tunnel.excluded_group"
            fullyBlocked -> "settings.tunnel.group_blocked"
            else -> "settings.tunnel.exclude_group"
        }
        val exclude = Button(ctx.t(excludeLabel)).apply {
            styleClass.add("tunnel-pool-lock")
            if (closed) styleClass.add("locked")
            isMnemonicParsing = false
            isDisable = locked
            accessibleText = text
            tooltip = Tooltip(ctx.t(if (closed) "settings.tunnel.pool_lock.shut" else "settings.tunnel.pool_lock.open"))
            setOnAction { requestGroupExclusion(pool) }
        }
        val count = if (!excluded && blockedCount in 1 until visible.size) {
            Label(ctx.t("settings.tunnel.blocked", blockedCount)).apply { styleClass.add("tunnel-block-count") }
        } else {
            null
        }
        val gap = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val head = HBox(8.0).apply {
            styleClass.add("pool-head")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
            children.addAll(listOfNotNull(title, meta, count, gap, exclude))
        }
        val list = VBox().apply {
            styleClass.add("tunnel-list")
            maxWidth = Double.MAX_VALUE
        }
        list.children += columnLegend()
        visible.forEach { endpoint ->
            val target = TunnelTarget(selected, pool.name, endpoint)
            val blocked = nodeBlocked(target)
            list.children += nodeLine(
                target,
                probes[target.key()] ?: NodeProbe(),
                locked,
                recommended = endpoint.address == bestAddress && !excluded && !blocked,
                excluded = excluded,
                blocked = blocked,
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
        val lines = list.children.filter { "tunnel-line" in it.styleClass }
        lines.forEach { it.styleClass.remove("last") }
        lines.lastOrNull()?.styleClass?.add("last")
    }

    private fun nodeLine(
        target: TunnelTarget,
        view: NodeProbe,
        locked: Boolean,
        recommended: Boolean,
        excluded: Boolean,
        blocked: Boolean,
    ): HBox {
        val select = nodeButton(target, view, locked, recommended = recommended, excluded = excluded, blocked = blocked)
        val block = Button(ctx.t(if (blocked) "settings.tunnel.unblock_node" else "settings.tunnel.block_node")).apply {
            styleClass.add("tunnel-block-action")
            if (blocked) styleClass.add("locked")
            isMnemonicParsing = false
            isDisable = locked
            accessibleText = text
            tooltip = Tooltip(ctx.t(if (blocked) "settings.tunnel.unblock_node.hint" else "settings.tunnel.block_node.hint"))
            setOnAction { toggleNodeBlock(target) }
        }
        return columnRow(select, block).apply {
            styleClass.add("tunnel-line")
            if ("selected" in select.styleClass) styleClass.add("selected")
            if ("best" in select.styleClass) styleClass.add("best")
            if ("limited" in select.styleClass) styleClass.add("limited")
            if (blocked) styleClass.add("node-blocked")
        }
    }

    private fun nodeButton(
        target: TunnelTarget,
        view: NodeProbe,
        locked: Boolean,
        prefix: String = "",
        forceLocked: Boolean = false,
        recommended: Boolean = false,
        excluded: Boolean = false,
        blocked: Boolean = false,
    ): Button {
        val shown = figures(target, view)
        val name = Label(prefix + target.endpoint.name).apply {
            styleClass.add("tunnel-row")
            textOverrun = OverrunStyle.ELLIPSIS
            minWidth = 0.0
            maxWidth = Double.MAX_VALUE
        }
        val status = Label(ctx.t(if (blocked) "settings.tunnel.lock.locked" else "settings.tunnel.lock.open")).apply {
            styleClass.add("tunnel-block-status")
            alignment = Pos.CENTER_RIGHT
            textOverrun = OverrunStyle.ELLIPSIS
        }
        val (client, server, sum) = rttText(shown)
        val decision = decisionKind()
        val stateKey = when {
            blocked -> "settings.tunnel.unblock_node"
            shown.limitReached || forceLocked -> "settings.tunnel.limit"
            excluded -> "settings.tunnel.excluded_group"
            else -> "settings.tunnel.block_node"
        }
        val row = valueStrip(
            name,
            status,
            rtt(client, "client", if (shown.received && !shown.failed) shown.clientMs else null, decision),
            rtt(sum, "sum", if (shown.received && !shown.failed && !shown.limitReached) shown.totalMs else null, decision),
        )
        val chosen = settings.override(target.region)?.address == target.endpoint.address
        val clientName = ctx.t("settings.tunnel.col.client")
        val serverName = ctx.t("settings.tunnel.col.server")
        val sumName = ctx.t("settings.tunnel.col.sum")
        val quality = qualityText(target, shown)
        val stateName = ctx.t(stateKey)
        val failNote = if (shown.received && shown.failed) "\n${ctx.t("settings.tunnel.no_reply")}" else ""
        return Button().apply {
            styleClass.add("tunnel-node")
            if (chosen) styleClass.add("selected")
            if (recommended) styleClass.add("best")
            if (blocked) styleClass.add("node-blocked")
            if (view.limitReached || forceLocked) styleClass.add("limited")
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER_LEFT
            graphic = row
            maxWidth = Double.MAX_VALUE
            userData = target.endpoint.address
            isDisable = locked || excluded || blocked || shown.limitReached || forceLocked || !view.selectable
            accessibleText = "${target.endpoint.name}. $stateName. $clientName $client, $serverName $server, $sumName $sum"
            tooltip = Tooltip("${target.endpoint.address}\n$stateName\n$clientName $client, $serverName $server, $sumName $sum$quality$failNote")
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
        view.failed -> Triple("—", "—", ctx.t("settings.tunnel.fail"))
        view.limitReached -> Triple(
            TunnelOverrides.formatMs(view.clientMs),
            TunnelOverrides.formatMs(view.serverMs),
            TunnelOverrides.formatMs(view.totalMs),
        )
        else -> Triple(TunnelOverrides.formatMs(view.clientMs), TunnelOverrides.formatMs(view.serverMs), TunnelOverrides.formatMs(view.totalMs))
    }

    private fun rtt(text: String, kind: String, ms: Double?, decision: String) = Label(text).apply {
        styleClass.addAll("tunnel-ping", kind)
        if (ms != null && ms >= 0.0) styleClass.add(TunnelOverrides.latencyBand(ms))
        if (kind == decision) styleClass.add("decision")
        alignment = Pos.CENTER_RIGHT
        textOverrun = OverrunStyle.ELLIPSIS
    }

    private fun modeLabel(mode: TunnelSearchMode): String = when (mode) {
        TunnelSearchMode.PING -> ctx.t("settings.tunnel.mode.ping")
        TunnelSearchMode.CLIENT_RTT -> ctx.t("settings.tunnel.mode.client")
        TunnelSearchMode.SERVER_RTT -> ctx.t("settings.tunnel.mode.server")
        TunnelSearchMode.STABILITY -> ctx.t("settings.tunnel.mode.stability")
    }

    private companion object {
        const val COLUMN_GAP = 8.0
        const val TOGGLE_GAP = 16.0
        const val VALUE_COLUMN = 64.0
        const val STATUS_COLUMN = 72.0
        const val ACTION_COLUMN = 132.0
    }
}

/**
 * Catalog the tunnel page is willing to show. CLOUD-* pools, RFC 5737 documentation ranges
 * and addresses with no valid IPv4 are removed before rows, Measure, or Find best see them.
 * A node that remains is still dropped after a probe miss.
 */
internal object TunnelPresentation {
    fun present(catalog: RoxyCatalog): RoxyCatalog {
        val pools = catalog.pools.mapNotNull { pool ->
            if (unlistedPool(pool.name)) return@mapNotNull null
            val tunnels = pool.tunnels.filter { endpoint -> listedAddress(endpoint.address) }
            if (tunnels.isEmpty()) null else dev.launcherfork.stalzone.TunnelPool(pool.name, tunnels)
        }
        return RoxyCatalog(catalog.mode, pools, catalog.clientToTunnelRttWeight)
    }

    /** CLOUD-RU-7 and the same CLOUD-* fixture shape never answer. */
    fun unlistedPool(name: String): Boolean {
        val key = name.trim()
        return key.equals("CLOUD-RU-7", ignoreCase = true) || key.startsWith("CLOUD-", ignoreCase = true)
    }

    private fun listedAddress(address: String): Boolean {
        val host = TunnelAddresses.parse(address)?.first ?: return false
        if (!ServerBlock.isValidIpv4(host)) return false
        return !documentationHost(host)
    }

    /** 192.0.2.0/24, 198.51.100.0/24 and 203.0.113.0/24 are not routable. */
    private fun documentationHost(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        val a = octets[0]
        val b = octets[1]
        val c = octets[2]
        return (a == 192 && b == 0 && c == 2) ||
            (a == 198 && b == 51 && c == 100) ||
            (a == 203 && b == 0 && c == 113)
    }
}
