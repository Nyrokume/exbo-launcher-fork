package dev.launcherfork.ui.screens.settings

import dev.launcherfork.domain.ServerBlockState
import dev.launcherfork.domain.SettingsStore
import dev.launcherfork.stalzone.FirewallNotElevated
import dev.launcherfork.stalzone.PingResult
import dev.launcherfork.stalzone.PingTarget
import dev.launcherfork.stalzone.ServerBlock
import dev.launcherfork.stalzone.TunnelCatalog
import dev.launcherfork.stalzone.TunnelFirewall
import dev.launcherfork.stalzone.TunnelPing
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.toggleClass
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.RadioButton
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.shape.SVGPath

/**
 * Tests point these at fakes so a click never opens a socket or a firewall rule.
 * The running launcher uses [TunnelPing] and [TunnelFirewall.system].
 */
internal object TunnelServices {
    var firewall: () -> TunnelFirewall = { TunnelFirewall.system() }

    var ping: (List<PingTarget>, Long, (List<PingResult>, Int, Int) -> Unit) -> Unit =
        { targets, timeoutMs, onChunk -> TunnelPing.ping(targets, timeoutMs, onChunk) }

    fun reset() {
        firewall = { TunnelFirewall.system() }
        ping = { targets, timeoutMs, onChunk -> TunnelPing.ping(targets, timeoutMs, onChunk) }
    }
}

/**
 * Tunnel page. The lock on a row only marks a pending choice. [tunnelApply] is what writes
 * `sbBlocked` and the Windows firewall. Ping only measures. Best and Clear change the pending
 * locks for the open region. This process never starts an elevated copy of itself.
 */
class TunnelPage(
    private val ctx: UiContext,
    private val store: SettingsStore,
    private val regionGroup: ToggleGroup,
    private val regionRu: RadioButton,
    private val regionEu: RadioButton,
    private val regionNa: RadioButton,
    private val regionSea: RadioButton,
    private val tunnelNow: Label,
    private val tunnelPools: VBox,
    private val tunnelPage: VBox,
    private val tunnelPing: Button,
    private val tunnelAutoBest: Button,
    private val tunnelUnblock: Button,
    private val tunnelApply: Button,
) {
    private var suppress = false
    private var pinging = false
    private var busy = false
    private var statusError = false
    private var statusMessage = ""
    private var pingDone = 0
    private var pingTotal = 0
    private var pingGen = 0
    private var fwGen = 0

    /** What the locks show. Region switches keep this until Apply or a failed Apply. */
    private val pendingBlocked = linkedSetOf<String>()
    private var pendingPreferred: Map<String, String> = emptyMap()

    fun bind() {
        loadPendingFromStore()
        regionGroup.selectedToggleProperty().addListener { _, _, selected ->
            if (suppress || selected == null) return@addListener
            val id = regionId(selected)
            val current = stored()
            if (current.region != id) save(current.copy(region = id))
            render()
        }
        tunnelPing.setOnAction { onPing() }
        tunnelAutoBest.setOnAction { onAutoBest() }
        tunnelUnblock.setOnAction { onClear() }
        tunnelApply.setOnAction { onApply() }
        tunnelPing.tooltip = Tooltip(ctx.t("settings.tunnel.ping.hint"))
        tunnelAutoBest.tooltip = Tooltip(ctx.t("settings.tunnel.autobest.hint", ServerBlock.AUTO_BEST_TOP_N))
        tunnelUnblock.tooltip = Tooltip(ctx.t("settings.tunnel.unblock.hint"))
        tunnelApply.tooltip = Tooltip(ctx.t("settings.tunnel.apply.hint"))
        tunnelNow.textOverrun = OverrunStyle.ELLIPSIS
        render()
    }

    fun refresh() {
        render()
    }

    /** Saved blocks become the locks. The store is left as it was. */
    private fun loadPendingFromStore() {
        val current = stored()
        pendingBlocked.clear()
        pendingBlocked.addAll(current.blocked)
        pendingPreferred = current.preferredByPool
    }

    private fun stored(): ServerBlockState = ServerBlock.prune(TunnelCatalog.servers(), store.load().serverBlock)

    private fun save(next: ServerBlockState) {
        store.save(store.load().copy(serverBlock = next.copy(mode = ServerBlock.MODE)))
    }

    private fun serversIn(region: String): List<TunnelCatalog.Server> =
        TunnelCatalog.servers().filter { it.region == region }

    private fun applied(): List<String> = stored().blocked

    private fun dirty(): Boolean = pendingBlocked.toSet() != applied().toSet()

    private fun render() {
        val current = stored()
        suppress = true
        try {
            selectRegion(current.region)
            renderPools(current)
        } finally {
            suppress = false
        }
        showStatus()
        updateButtons()
    }

    private fun renderPools(current: ServerBlockState) {
        tunnelPools.children.clear()
        val regionServers = serversIn(current.region)
        val pools = TunnelCatalog.regions().firstOrNull { it.id == current.region }?.pools.orEmpty()
        for (pool in pools) {
            val inPool = regionServers.filter { it.pool == pool.name }
            val title = Label(pool.name).apply { styleClass.add("pool-title") }
            val meta = Label(ctx.t("settings.tunnel.pool_meta", inPool.size)).apply { styleClass.add("pool-meta") }
            val head = HBox(title, meta).apply {
                styleClass.add("pool-head")
                alignment = Pos.CENTER_LEFT
            }
            val rows = VBox().apply {
                styleClass.addAll("tunnel-list", "zone-radius-md")
                maxWidth = Double.MAX_VALUE
            }
            inPool.forEachIndexed { index, server ->
                rows.children.add(serverRow(server, current, first = index == 0, last = index == inPool.lastIndex))
            }
            tunnelPools.children.add(VBox(head, rows).apply {
                styleClass.add("pool")
                maxWidth = Double.MAX_VALUE
            })
        }
    }

    private fun serverRow(server: TunnelCatalog.Server, current: ServerBlockState, first: Boolean, last: Boolean): HBox {
        val locked = server.id in pendingBlocked
        val best = !locked && pendingPreferred[server.pool] == server.id
        val lock = lockControl(server, locked)
        val name = Label(server.name).apply {
            styleClass.add("tunnel-row")
            maxWidth = Double.MAX_VALUE
            minWidth = 0.0
            alignment = Pos.CENTER_LEFT
            textOverrun = OverrunStyle.ELLIPSIS
            isMouseTransparent = true
        }
        HBox.setHgrow(name, Priority.ALWAYS)
        val ping = Label(pingText(server.id, current.pings)).apply {
            styleClass.add("tunnel-ping")
            val level = pingClass(server.id, current.pings)
            if (level.isNotEmpty()) styleClass.add(level)
            minWidth = Region.USE_PREF_SIZE
            isMouseTransparent = true
            accessibleText = if (server.id in current.pings && current.pings[server.id] == null) {
                ctx.t("settings.tunnel.fail")
            } else {
                text
            }
        }
        return HBox(lock, name, ping).apply {
            styleClass.add("tunnel-line")
            if (locked) styleClass.add("blocked")
            if (best) styleClass.add("best")
            if (first) styleClass.add("first")
            if (last) styleClass.add("last")
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
    }

    /** Closed lock = pending block. Open lock = this server stays allowed. The firewall is not touched. */
    private fun lockControl(server: TunnelCatalog.Server, locked: Boolean): Button {
        val glyph = SVGPath().apply {
            content = if (locked) LOCK_CLOSED else LOCK_OPEN
            styleClass.add("tunnel-lock-glyph")
            isMouseTransparent = true
        }
        return Button().apply {
            styleClass.add("tunnel-lock")
            if (locked) styleClass.add("locked")
            graphic = glyph
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER
            isMnemonicParsing = false
            minWidth = 26.0
            prefWidth = 26.0
            maxWidth = 26.0
            minHeight = 26.0
            prefHeight = 26.0
            maxHeight = 26.0
            userData = server.id
            accessibleText = ctx.t(if (locked) "settings.tunnel.lock.locked" else "settings.tunnel.lock.open")
            tooltip = Tooltip(ctx.t(if (locked) "settings.tunnel.lock.hint_locked" else "settings.tunnel.lock.hint_open"))
            setOnAction { togglePending(server.id) }
        }
    }

    /** One server, pending only. Apply is what commits. */
    private fun togglePending(id: String) {
        if (busy) return
        if (!pendingBlocked.add(id)) pendingBlocked.remove(id)
        statusError = false
        statusMessage = ""
        render()
    }

    /** Unlocks the open region in the pending set. Other regions and the firewall stay. */
    private fun onClear() {
        if (busy) return
        val scope = serversIn(stored().region)
        val scopeIds = scope.map { it.id }.toSet()
        val scopePools = scope.map { it.pool }.toSet()
        pendingBlocked.removeAll(scopeIds)
        pendingPreferred = pendingPreferred.filterKeys { it !in scopePools }
        statusError = false
        statusMessage = ""
        render()
    }

    private fun onPing(after: (() -> Unit)? = null) {
        if (busy) return
        val scope = serversIn(stored().region)
        if (scope.isEmpty()) return
        val gen = ++pingGen
        pinging = true
        busy = true
        statusError = false
        statusMessage = ""
        pingDone = 0
        pingTotal = scope.size
        updateButtons()
        showStatus()
        val targets = scope.map { PingTarget(it.id, it.host, it.port) }
        Thread({
            try {
                TunnelServices.ping(targets, ServerBlock.PING_TIMEOUT_MS) { results, done, total ->
                    Platform.runLater {
                        if (gen != pingGen || tunnelNow.scene == null) return@runLater
                        val current = stored()
                        val pings = current.pings.toMutableMap()
                        for (row in results) pings[row.id] = row.ms
                        save(current.copy(pings = pings))
                        pingDone = done
                        pingTotal = total
                        renderPools(stored())
                        showStatus()
                    }
                }
                Platform.runLater {
                    if (gen != pingGen || tunnelNow.scene == null) return@runLater
                    finishPing()
                    pinging = false
                    busy = false
                    if (after != null) after() else render()
                }
            } catch (err: Exception) {
                Platform.runLater {
                    if (tunnelNow.scene == null) return@runLater
                    pinging = false
                    busy = false
                    statusError = true
                    statusMessage = ctx.t("settings.tunnel.failed", err.message ?: "ping")
                    render()
                }
            }
        }, "tunnel-ping").apply { isDaemon = true }.start()
    }

    /** Ping writes measurements only. Locks and firewall rules stay as they were. */
    private fun finishPing() {
        val scope = serversIn(stored().region)
        val current = stored()
        val ok = scope.count { it.id in current.pings && current.pings[it.id] != null }
        statusError = ok == 0
        statusMessage = if (ok == 0) ctx.t("settings.tunnel.ping_none") else ""
    }

    private fun onAutoBest() {
        if (busy) return
        val scope = serversIn(stored().region)
        val pools = scope.map { it.pool }.distinct().size
        val scopeIds = scope.map { it.id }
        if (ServerBlock.poolsWithValidPing(stored().pings, scope) < pools) {
            onPing { finishAutoBest(scopeIds) }
        } else {
            finishAutoBest(scopeIds)
        }
    }

    /**
     * Best server in each pool of this region, then the top [ServerBlock.AUTO_BEST_TOP_N] at or under
     * [ServerBlock.PING_ACCEPTABLE_MAX_MS]. A miss or a slower ping is not a winner. Other regions stay.
     * The locks update immediately; the firewall waits for Apply.
     */
    private fun finishAutoBest(scopeIds: List<String>) {
        val scope = TunnelCatalog.servers().filter { it.id in scopeIds }
        val picked = ServerBlock.pickBestPerRegionTopN(stored().pings, scope)
        if (picked.allowed.isEmpty()) {
            statusError = true
            statusMessage = ctx.t("settings.tunnel.best_none")
            render()
            return
        }
        val nextBlocked = ServerBlock.blockedExcept(scope, picked.allowed)
        val merged = ServerBlock.mergeBlockedForScope(pendingBlocked.toList(), scope.map { it.id }, nextBlocked)
        pendingBlocked.clear()
        pendingBlocked.addAll(merged)
        pendingPreferred = ServerBlock.mergePreferredForScope(pendingPreferred, scope, picked.preferredByPool)
        statusError = false
        statusMessage = ""
        render()
    }

    /**
     * Writes the pending locks. Newly locked hosts gain `STALZONE-SB-*` rules and newly unlocked hosts
     * lose them, because [TunnelFirewall.apply] replaces the whole set and [TunnelFirewall.clear] drops it.
     * Without an administrator token nothing is saved and the locks return to the last applied list.
     * This never relaunches the process elevated.
     */
    private fun onApply() {
        if (busy || !dirty()) return
        val desired = pendingBlocked.toList()
        val desiredPreferred = pendingPreferred
        val hosts = ServerBlock.resolveBlockedHosts(TunnelCatalog.servers(), desired)
        if (desired.isNotEmpty() && hosts.isEmpty()) {
            rollbackPending()
            statusError = true
            statusMessage = ctx.t("settings.tunnel.no_hosts")
            render()
            return
        }
        val gen = ++fwGen
        busy = true
        statusError = false
        statusMessage = ""
        render()
        val fw = TunnelServices.firewall()
        Thread({
            val result = try {
                if (hosts.isEmpty()) fw.clear().map { "ok" } else fw.apply(hosts)
            } catch (err: Exception) {
                Result.failure(err)
            }
            Platform.runLater {
                if (gen != fwGen || tunnelNow.scene == null) return@runLater
                busy = false
                result.onSuccess {
                    save(stored().copy(blocked = desired, preferredByPool = desiredPreferred))
                    pendingBlocked.clear()
                    pendingBlocked.addAll(desired)
                    pendingPreferred = desiredPreferred
                    statusError = false
                    statusMessage = ""
                }.onFailure { err ->
                    rollbackPending()
                    statusError = true
                    statusMessage = errorText(err)
                }
                render()
            }
        }, "tunnel-firewall").apply { isDaemon = true }.start()
    }

    private fun rollbackPending() {
        val saved = stored()
        pendingBlocked.clear()
        pendingBlocked.addAll(saved.blocked)
        pendingPreferred = saved.preferredByPool
    }

    private fun showStatus() {
        val text = when {
            pinging -> ctx.t("settings.tunnel.pinging", pingDone, pingTotal)
            busy -> ctx.t("settings.tunnel.applying")
            statusMessage.isNotEmpty() -> statusMessage
            applied().isEmpty() -> ctx.t("settings.tunnel.auto")
            else -> ctx.t("settings.tunnel.blocked", applied().size)
        }
        tunnelNow.text = text
        val hint = if (applied().isEmpty()) {
            ctx.t("settings.tunnel.auto.hint")
        } else {
            ctx.t("settings.tunnel.blocked", applied().size)
        }
        tunnelNow.tooltip = Tooltip(hint)
        if (statusError && "err" !in tunnelNow.styleClass) tunnelNow.styleClass.add("err")
        if (!statusError) tunnelNow.styleClass.remove("err")
    }

    private fun updateButtons() {
        val regionLocked = serversIn(stored().region).any { it.id in pendingBlocked }
        val changed = dirty()
        tunnelPing.isDisable = busy
        tunnelAutoBest.isDisable = busy
        tunnelUnblock.isDisable = busy || !regionLocked
        tunnelApply.isDisable = busy || !changed
        tunnelApply.toggleClass("primary", changed && !busy)
    }

    private fun errorText(err: Throwable): String {
        if (err is FirewallNotElevated) return ctx.t("settings.tunnel.admin")
        val detail = err.message?.lineSequence()?.firstOrNull()?.take(120).orEmpty()
        return ctx.t("settings.tunnel.failed", detail.ifBlank { "error" })
    }

    private fun pingText(id: String, pings: Map<String, Int?>): String = when {
        pinging && id !in pings -> ctx.t("settings.tunnel.pending")
        id !in pings -> "—"
        pings[id] == null -> ctx.t("settings.tunnel.fail")
        else -> pings[id].toString()
    }

    private fun pingClass(id: String, pings: Map<String, Int?>): String {
        if (id !in pings) return ""
        val ms = pings[id] ?: return "miss"
        return when {
            ServerBlock.isVeryBadPing(ms) -> "very-bad"
            ms <= 60 -> "good"
            ms <= ServerBlock.PING_ACCEPTABLE_MAX_MS -> "mid"
            else -> "bad"
        }
    }

    private fun regionId(selected: Toggle): String = when (selected) {
        regionEu -> "EU"
        regionNa -> "NA"
        regionSea -> "SEA"
        else -> "RU"
    }

    private fun selectRegion(id: String) {
        val button = when (id) {
            "EU" -> regionEu
            "NA" -> regionNa
            "SEA" -> regionSea
            else -> regionRu
        }
        if (regionGroup.selectedToggle != button) button.isSelected = true
    }

    private companion object {
        const val LOCK_CLOSED = "M6.2,7.4 V5.5 A1.8,1.8 0 0 1 9.8,5.5 V7.4 M3.4,7.4 H12.6 V13.8 H3.4 Z"
        const val LOCK_OPEN = "M8.4,7.4 V5.0 A1.8,1.8 0 0 1 12.0,5.0 V2.6 M3.4,7.4 H12.6 V13.8 H3.4 Z"
    }
}
