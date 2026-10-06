package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.components.ServerEntryController
import dev.launcherfork.ui.components.formatCount
import javafx.css.PseudoClass
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * Games list: the online count is always visible under the game name (green dot + «1 284 онлайн»), grey dot +
 * «не в сети» / «offline» when the count is unknown. The value comes from ServerRepository.watchOnline (mock: 1284)
 * and follows its changes. Layout is checked in every theme at the minimum window size with localToScene bounds.
 */
class OnlineCountTest {
    private fun Node.sceneBounds(): Bounds = localToScene(layoutBounds)

    private val nbsp = '\u00A0'
    private val stalzone = MockData.servers.single()

    /** One games-list card in a scene with the given theme, bound to [server] of [app]. */
    private fun withEntry(
        lang: String = "ru",
        theme: String = "zone",
        app: AppContext = AppContext.mock(language = lang, authDelayMs = 0),
        server: dev.launcherfork.domain.Server = stalzone,
        check: (ServerEntryController) -> Unit,
    ) {
        try {
            FxTestSupport.onFx {
                val ctx = FxTestSupport.context(lang, app)
                val loaded = Fxml.load<ServerEntryController>(ctx, "server_entry.fxml")
                Scene(loaded.root as Parent, 253.0, 50.0).also { applyTheme(it, theme) }
                loaded.controller.bind(server, app.clientStates.state(server))
                loaded.root.applyCss(); loaded.root.layout()
                try { check(loaded.controller) } finally { loaded.controller.unbind() }
            }
        } finally {
            app.shutdown()
        }
    }

    private fun applyTheme(scene: Scene, theme: String) {
        val sheets = mutableListOf(Theme.stylesheet("theme.css"))
        if (theme == "shadcn") sheets += Theme.stylesheet("theme-shadcn.css")
        if (theme == "zone") { Theme.zoneFonts; sheets += Theme.stylesheet("theme-zone.css") }
        sheets += Theme.stylesheet("lang_ru.css")
        scene.stylesheets.setAll(sheets)
    }

    private fun Region.fill(): Color = background.fills.first().fill as Color

    @Test
    fun countsUseTheLocaleGrouping() {
        assertEquals("1${nbsp}284", formatCount(1284, "ru"))
        assertEquals("1,284", formatCount(1284, "en"))
        assertEquals("12${nbsp}345${nbsp}678", formatCount(12_345_678, "ru"))
        assertEquals("999", formatCount(999, "en"))
    }

    @Test
    fun mockFeedsTheCountThatUsedToBeTheTooltip() {
        assertEquals(1284, stalzone.players)
        assertEquals(1284, stalzone.onlineCount)
        assertNull(stalzone.copy(online = false).onlineCount)
        assertNull(stalzone.copy(players = -1).onlineCount)
    }

    @Test
    fun russianEntryShowsTheFormattedCount() = withEntry(lang = "ru") { e ->
        assertEquals("1${nbsp}284 онлайн", e.onlineText.text)
        assertTrue(e.onlineRow.isVisible && e.onlineRow.isManaged)
        assertFalse(e.onlineRow.styleClass.contains("offline"))
        assertEquals("STALZONE, 1${nbsp}284 онлайн", e.root.accessibleText)
    }

    @Test
    fun englishEntryShowsTheFormattedCount() = withEntry(lang = "en") { e ->
        assertEquals("1,284 online", e.onlineText.text)
        assertFalse(e.onlineRow.styleClass.contains("offline"))
    }

    @Test
    fun onlineTextIsSmallMutedRoboto() = listOf("exbo", "shadcn", "zone").forEach { theme ->
        withEntry(theme = theme) { e ->
            assertEquals("Roboto", e.onlineText.font.family, "$theme: online line font")
            assertEquals(11.0, e.onlineText.font.size, 0.01, "$theme: online line size")
            assertTrue(e.onlineText.font.size < e.name.font.size, "$theme: the name stays the bigger line")
            assertEquals(6.0, e.onlineDot.width, 0.01); assertEquals(6.0, e.onlineDot.height, 0.01)
            val dot = e.onlineDot.fill()
            assertTrue(dot.green > dot.red + 0.2 && dot.green > dot.blue, "$theme: online dot must be green, got $dot")
        }
    }

    @Test
    fun offlineServerShowsAGreyDotAndNotOnline() = listOf("ru" to "не в сети", "en" to "offline").forEach { (lang, text) ->
        listOf(stalzone.copy(online = false), stalzone.copy(players = -1)).forEach { server ->
            withEntry(lang = lang, theme = "zone", server = server) { e ->
                assertEquals(text, e.onlineText.text, "$lang: $server")
                assertTrue(e.onlineRow.styleClass.contains("offline"))
                assertTrue(e.onlineRow.isVisible, "the line stays, so the entry height does not jump")
                val dot = e.onlineDot.fill()
                assertEquals(dot.red, dot.green, 0.02, "offline dot must be grey, got $dot")
                assertEquals(dot.green, dot.blue, 0.02, "offline dot must be grey, got $dot")
            }
        }
    }

    @Test
    fun nullOrNegativeValueSwitchesToOffline() = withEntry { e ->
        e.onlineCount.set(null)
        assertEquals("не в сети", e.onlineText.text)
        assertTrue(e.onlineRow.styleClass.contains("offline"))
        e.onlineCount.set(42)
        assertEquals("42 онлайн", e.onlineText.text)
        assertFalse(e.onlineRow.styleClass.contains("offline"))
        e.onlineCount.set(-3)
        assertEquals("не в сети", e.onlineText.text)
    }

    @Test
    fun labelFollowsThePropertyAndTheRepository() {
        val app = AppContext.mock(authDelayMs = 0)
        val repo = app.services.servers as MockServerRepository
        withEntry(app = app) { e ->
            e.onlineCount.set(1300)
            assertEquals("1${nbsp}300 онлайн", e.onlineText.text)
            // back from the property to the model value
            repo.setOnline(stalzone.id, 1284)
            assertEquals("1${nbsp}284 онлайн", e.onlineText.text)
        }
        // the repository pushes from a background thread: the label hops to the FX thread
        val app2 = AppContext.mock(authDelayMs = 0)
        val repo2 = app2.services.servers as MockServerRepository
        val ctx = FxTestSupport.context("ru", app2)
        val e = FxTestSupport.onFx {
            Fxml.load<ServerEntryController>(ctx, "server_entry.fxml").controller.also { it.bind(stalzone, app2.clientStates.state(stalzone)) }
        }
        try {
            Thread { repo2.setOnline(stalzone.id, 1290) }.apply { start(); join() }
            assertTrue(FxTestSupport.waitFx { e.onlineText.text == "1${nbsp}290 онлайн" }, "label did not follow: ${FxTestSupport.onFx { e.onlineText.text }}")
            Thread { repo2.setOnline(stalzone.id, null) }.apply { start(); join() }
            assertTrue(FxTestSupport.waitFx { e.onlineText.text == "не в сети" })
            // a detached card no longer listens
            FxTestSupport.onFx { e.unbind() }
            Thread { repo2.setOnline(stalzone.id, 7) }.apply { start(); join() }
            Thread.sleep(100)
            assertEquals("не в сети", FxTestSupport.onFx { e.onlineText.text })
        } finally {
            app2.shutdown()
        }
        // a rebuilt card (language switch, new scene) starts from the repository's current value, not the snapshot
        val app3 = AppContext.mock(authDelayMs = 0)
        (app3.services.servers as MockServerRepository).setOnline(stalzone.id, 2001)
        withEntry(app = app3) { e3 -> assertEquals("2${nbsp}001 онлайн", e3.onlineText.text) }
    }

    /**
     * Race from the PR #7 review: a tick may queue `Platform.runLater` just before the card is re-bound. That stale
     * update must not land on the new binding (the old server's count flashing on the new one).
     */
    @Test
    fun staleUpdateQueuedBeforeRebindIsDropped() {
        val listeners = java.util.concurrent.ConcurrentHashMap<String, (Int?) -> Unit>()
        val repo = object : dev.launcherfork.domain.ServerRepository {
            override fun servers() = MockData.servers
            override fun watchOnline(server: dev.launcherfork.domain.Server, listener: (Int?) -> Unit): dev.launcherfork.domain.Cancellable {
                listeners[server.id] = listener
                listener(server.onlineCount)
                return dev.launcherfork.domain.Cancellable {}
            }
        }
        val base = AppContext.mock(authDelayMs = 0)
        val app = AppContext(base.services.copy(servers = repo))
        val a = stalzone.copy(id = "a", players = 100)
        val b = stalzone.copy(id = "b", players = 5)
        val ctx = FxTestSupport.context("ru", app)
        val e = FxTestSupport.onFx {
            Fxml.load<ServerEntryController>(ctx, "server_entry.fxml").controller.also { it.bind(a, app.clientStates.state(a)) }
        }
        try {
            assertEquals(100, FxTestSupport.onFx { e.onlineCount.get() })
            FxTestSupport.onFx {
                // the tick of server A queues its runLater (FX thread is busy right here) ...
                Thread { listeners.getValue("a")(999) }.apply { start(); join() }
                // ... and before it runs, the card is re-bound to server B
                e.bind(b, app.clientStates.state(b))
            }
            Thread.sleep(100)
            assertEquals(5, FxTestSupport.onFx { e.onlineCount.get() }, "stale count of server A landed on server B")
            assertEquals("5 онлайн", FxTestSupport.onFx { e.onlineText.text })
            // the current binding still updates from the background
            Thread { listeners.getValue("b")(6) }.apply { start(); join() }
            assertTrue(FxTestSupport.waitFx { e.onlineCount.get() == 6 })
            // and after unbind nothing queued earlier gets through either
            FxTestSupport.onFx {
                Thread { listeners.getValue("b")(77) }.apply { start(); join() }
                e.unbind()
            }
            Thread.sleep(100)
            assertEquals(6, FxTestSupport.onFx { e.onlineCount.get() })
        } finally {
            FxTestSupport.onFx { e.unbind() }
            app.shutdown(); base.shutdown()
        }
    }

    /** NIT from the review: the app stops the repository through the interface, no cast to the mock. */
    @Test
    fun appShutdownClosesAnyServerRepository() {
        var closed = 0
        val repo = object : dev.launcherfork.domain.ServerRepository {
            override fun servers() = MockData.servers
            override fun close() { closed++ }
        }
        val base = AppContext.mock(authDelayMs = 0)
        AppContext(base.services.copy(servers = repo)).shutdown()
        base.shutdown()
        assertEquals(1, closed)
        // the default close() is a no-op, the mock's stops its drift timer and can be called twice
        object : dev.launcherfork.domain.ServerRepository { override fun servers() = MockData.servers }.close()
        val ticking = MockServerRepository(tickMs = 10)
        val h = ticking.watchOnline(stalzone) {}
        ticking.close(); ticking.close()
        h.cancel()
    }

    @Test
    fun mockDriftsSlightlyWhenTickingAndStaysFixedOtherwise() {
        val fixed = MockServerRepository()
        val seen = CopyOnWriteArrayList<Int?>()
        val h = fixed.watchOnline(stalzone) { seen += it }
        assertEquals(listOf<Int?>(1284), seen)
        h.cancel()

        val ticking = MockServerRepository(tickMs = 10, random = Random(7))
        val values = CopyOnWriteArrayList<Int?>()
        val handle = ticking.watchOnline(stalzone) { values += it }
        try {
            val deadline = System.currentTimeMillis() + 3_000
            while (values.size < 5 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertTrue(values.size >= 5, "no ticks: $values")
            assertEquals(1284, values.first())
            values.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(b!! - a!!) <= 6, "jump $a -> $b") }
        } finally {
            handle.cancel()
            ticking.close()
        }
    }

    @Test
    fun stringsInEveryBundle() {
        val expected = mapOf(
            "messages.properties" to ("%s онлайн" to "не в сети"),
            "messages_ru.properties" to ("%s онлайн" to "не в сети"),
            "messages_en.properties" to ("%s online" to "offline"),
        )
        expected.forEach { (file, texts) ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            assertEquals(texts.first, props.getProperty("main.games.online"), file)
            assertEquals(texts.second, props.getProperty("main.games.offline"), file)
        }
    }

    // ---------------- layout: main window, every theme, minimum window size ----------------

    private fun withMainAtMinSize(lang: String = "ru", check: (StageNavigator, String) -> Unit) {
        val app = AppContext.mock(language = lang, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            StageNavigator(Stage(StageStyle.UNDECORATED), app, lang).also {
                it.exit = {}; it.showMain(); it.mainStage.show()
                it.mainStage.width = Theme.WINDOW_MIN_WIDTH
                it.mainStage.height = Theme.WINDOW_MIN_HEIGHT
            }
        }
        try {
            listOf("exbo", "shadcn", "zone").forEach { theme ->
                FxTestSupport.onFx {
                    val scene = nav.mainStage.scene
                    applyTheme(scene, theme)
                    scene.root.run { applyCss(); layout(); layout() }
                    check(nav, theme)
                }
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    @Test
    fun onlineLineSitsUnderTheNameInsideTheEntryAtMinimumSize() = listOf("ru", "en").forEach { lang ->
        withMainAtMinSize(lang) { nav, theme ->
            val where = "$theme/$lang"
            val scene = nav.mainStage.scene
            assertEquals(Theme.WINDOW_MIN_WIDTH, scene.width, 1.0, "$where: window not at min width")
            assertEquals(Theme.WINDOW_MIN_HEIGHT, scene.height, 1.0, "$where: window not at min height")
            val list = nav.mainController!!.serversListController
            val e = list.entryControllers.single()
            assertEquals(if (lang == "ru") "1${nbsp}284 онлайн" else "1,284 online", e.onlineText.text, where)

            val entry = e.root.sceneBounds()
            val name = e.name.sceneBounds()
            val row = e.onlineRow.sceneBounds()
            val text = e.onlineText.sceneBounds()
            val dot = e.onlineDot.sceneBounds()
            val icon = e.image.sceneBounds()

            // a little taller than the old one-line 40 px entry
            assertTrue(entry.height in 46.0..54.0, "$where: entry height ${entry.height}")
            // the online line belongs to the entry, under the name, left-aligned with it
            assertTrue(e.onlineRow.parent.parent === e.root, "$where: online line is not inside the entry")
            assertTrue(row.minY >= name.maxY - 0.5, "$where: line $row overlaps the name $name")
            assertTrue(row.minY - name.maxY <= 6.0, "$where: line $row hangs too far below the name $name")
            assertEquals(name.minX, dot.minX, 0.5, "$where: dot $dot not aligned with the name $name")
            assertTrue(text.minX > dot.maxX + 3.0, "$where: text $text sticks to the dot $dot")
            assertEquals(text.centerY, dot.centerY, 1.5, "$where: dot $dot not centred on the text $text")
            // nothing clipped: the whole line is inside the entry, the entry inside the list viewport, the window
            listOf("name" to name, "line" to row, "text" to text, "dot" to dot).forEach { (what, b) ->
                assertTrue(b.minX >= entry.minX && b.maxX <= entry.maxX && b.minY >= entry.minY && b.maxY <= entry.maxY,
                    "$where: $what $b is outside the entry $entry")
            }
            val viewport = list.serversPane.lookup(".viewport").sceneBounds()
            assertTrue(entry.minY >= viewport.minY - 0.5 && entry.maxY <= viewport.maxY + 0.5 &&
                entry.minX >= viewport.minX - 0.5 && entry.maxX <= viewport.maxX + 0.5,
                "$where: entry $entry clipped by the list viewport $viewport")
            assertTrue(viewport.maxY <= scene.height && entry.maxX <= list.sidebar.sceneBounds().maxX, "$where: outside the sidebar")
            assertTrue(text.maxX <= e.downloadingStatus.sceneBounds().minX + 0.5, "$where: text runs into the right column")
            // the icon stays vertically centred on the two lines
            assertEquals(entry.centerY, icon.centerY, 0.5, "$where: icon $icon not centred in the entry $entry")
            val block = (name.minY + row.maxY) / 2
            assertEquals(icon.centerY, block, 1.5, "$where: icon centre ${icon.centerY} vs text block centre $block")
            // the red marker spans the whole (taller) entry and is coloured while selected; hover still fills the row
            val marker = e.cursor
            assertEquals(entry.height, marker.height, 0.5, "$where: marker height")
            assertTrue(e.root.styleClass.contains("selected"))
            assertNotEquals(Color.TRANSPARENT, marker.fill(), "$where: selected marker is transparent")
            val hover = PseudoClass.getPseudoClass("hover")
            e.root.pseudoClassStateChanged(hover, true); e.root.applyCss()
            val hoverFill = e.root.background?.fills?.firstOrNull()?.fill
            e.root.pseudoClassStateChanged(hover, false); e.root.applyCss()
            assertTrue(hoverFill != null && hoverFill != Color.TRANSPARENT, "$where: hover does not fill the entry ($hoverFill)")
            // while downloading the progress bar joins the block: under the online line, all of it inside the entry
            e.progress.isVisible = true; e.progress.isManaged = true
            scene.root.run { applyCss(); layout() }
            val bar = e.progress.sceneBounds()
            val nameNow = e.name.sceneBounds()
            val rowNow = e.onlineRow.sceneBounds()
            assertTrue(bar.minY >= rowNow.maxY - 0.5 && rowNow.minY >= nameNow.maxY - 0.5, "$where: name $nameNow, line $rowNow, bar $bar")
            assertTrue(nameNow.minY >= entry.minY && bar.maxY <= entry.maxY, "$where: downloading block $nameNow..$bar outside the entry $entry")
            e.progress.isVisible = false; e.progress.isManaged = false
            scene.root.run { applyCss(); layout() }
        }
    }
}
