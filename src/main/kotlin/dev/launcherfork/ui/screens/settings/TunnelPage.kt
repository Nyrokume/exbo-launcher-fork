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
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.OverrunStyle
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.StringConverter
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
 * once. «Измерить снова» and «Найти лучший сервер» are the only other measurements.
 */
class TunnelPage(
    private val ctx: UiContext,
    private val regionGroup: ToggleGroup,
    private val regionButtons: Map<RadioButton, TunnelRegion>,
    private val tunnelNow: Label,
    private val tunnelPools: VBox,
    private val tunnelMeasure: Button,
    private val tunnelFindBest: Button,
    private val tunnelGameChoice: Button,
    private val tunnelMode: ComboBox<TunnelSearchMode>,
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
    private var roundTotal = 0
    private var roundDone = 0
    private var showBest = false
    private var bestTargets: List<TunnelTarget> = emptyList()
    private var again: (() -> Unit)? = null
    private var suppressMode = false
    private var suppressRegion = false

    fun bind() {
        reloadFiles()
        tunnelNow.textOverrun = OverrunStyle.ELLIPSIS
        tunnelMeasure.tooltip = Tooltip(ctx.t("settings.tunnel.measure.hint"))
        tunnelFindBest.tooltip = Tooltip(ctx.t("settings.tunnel.find.hint"))
        tunnelGameChoice.tooltip = Tooltip(ctx.t("settings.tunnel.game.hint"))
        tunnelMeasure.setOnAction { again?.invoke() }
        tunnelFindBest.setOnAction { measureSearch() }
        tunnelGameChoice.setOnAction { chooseGame() }
        tunnelMode.converter = object : StringConverter<TunnelSearchMode>() {
            override fun toString(mode: TunnelSearchMode?): String = mode?.let { modeLabel(it) }.orEmpty()
            override fun fromString(text: String?): TunnelSearchMode = TunnelSearchMode.PING
        }
        tunnelMode.setCellFactory {
            object : ListCell<TunnelSearchMode>() {
                override fun updateItem(item: TunnelSearchMode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else modeLabel(item)
                }
            }
        }
        tunnelMode.items.setAll(TunnelSearchMode.entries)
        tunnelMode.valueProperty().addListener { _, _, mode ->
            if (suppressMode || mode == null || mode == settings.mode) return@addListener
            val previous = settings
            settings = settings.withMode(mode)
            if (!persist()) settings = previous
            render()
        }
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

    private fun openRegion(region: TunnelRegion) {
        selected = region
        showBest = false
        bestTargets = emptyList()
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

    private fun chooseGame() {
        if (settingsError != null) return
        val previous = settings
        settings = settings.withoutOverride(selected)
        if (!persist()) settings = previous
        render()
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

    private fun measurePool(poolName: String) {
        val catalog = catalogs[selected] ?: return
        val pool = catalog.pools.firstOrNull { it.name == poolName } ?: return
        again = { measurePool(poolName) }
        showBest = false
        startRound(pool.tunnels.map { TunnelTarget(selected, pool.name, it) }) {}
    }

    private fun measureSearch() {
        val catalog = catalogs[selected] ?: return
        val targets = catalog.targets(selected) { settings.isExcluded(selected, it) }
        again = { measureSearch() }
        if (targets.isEmpty()) {
            showBest = true
            bestTargets = emptyList()
            render()
            return
        }
        showBest = true
        bestTargets = targets
        startRound(targets) {}
    }

    private fun cancelRound() {
        probeGen++
        measuring = false
        roundTotal = 0
        roundDone = 0
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
        roundTotal = targets.size
        roundDone = 0
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
            roundDone++
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
        suppressMode = true
        suppressRegion = true
        try {
            regionButtons.forEach { (button, region) -> button.isSelected = region == selected }
            if (tunnelMode.value != settings.mode) tunnelMode.value = settings.mode
        } finally {
            suppressMode = false
            suppressRegion = false
        }
        val override = settings.override(selected)
        tunnelNow.text = when {
            measuring -> ctx.t("settings.tunnel.progress", roundDone, roundTotal)
            override != null -> listOf(override.pool, override.name).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { override.address }
            else -> ctx.t("settings.tunnel.game_now")
        }
        tunnelNow.styleClass.remove("err")
        if (settingsError != null || catalogError != null) tunnelNow.styleClass.add("err")
        val locked = settingsError != null
        tunnelMeasure.isDisable = again == null || measuring
        tunnelFindBest.isDisable = measuring || catalogs[selected] == null
        tunnelGameChoice.isDisable = locked
        tunnelMode.isDisable = locked
        paintPools(locked)
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
                if (showBest) tunnelPools.children += bestBlock(catalog, locked)
                for (pool in catalog.pools) tunnelPools.children += poolBlock(pool, locked)
            }
        }
        if (scroll != null) scroll.vvalue = saved
    }

    private fun bestBlock(catalog: RoxyCatalog, locked: Boolean): VBox {
        val now = TunnelServices.clock()
        val ranked = cache.best(bestTargets, probes, settings.mode, catalog.clientToTunnelRttWeight, now)
        val limited = cache.limitedTargets(bestTargets, probes, now)
        val title = Label(ctx.t("settings.tunnel.best")).apply { styleClass.add("pool-title") }
        val rows = VBox().apply { styleClass.add("tunnel-list") }
        ranked.forEachIndexed { index, result ->
            rows.children += nodeButton(result.target, probes[result.target.key()] ?: NodeProbe(), locked, "${index + 1}. ")
        }
        for (target in limited) {
            if (ranked.any { it.target.key() == target.key() }) continue
            rows.children += nodeButton(target, probes[target.key()] ?: NodeProbe(), locked, forceLocked = true)
        }
        if (rows.children.isEmpty()) rows.children += note(ctx.t("settings.tunnel.none"))
        return VBox(title, rows).apply { styleClass.addAll("pool", "tunnel-best") }
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
        }
        val list = VBox().apply { styleClass.add("tunnel-list") }
        pool.tunnels.forEach { endpoint ->
            list.children += nodeButton(TunnelTarget(selected, pool.name, endpoint), probes[TunnelTarget(selected, pool.name, endpoint).key()] ?: NodeProbe(), locked)
        }
        return VBox(4.0, head, list).apply { styleClass.add("pool") }
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
            accessibleText = "${target.endpoint.name}. ${ctx.t("settings.tunnel.legend")}: $client, $server, $sum"
            tooltip = Tooltip("${target.endpoint.address}\n${ctx.t("settings.tunnel.legend")}: $client · $server · $sum")
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
