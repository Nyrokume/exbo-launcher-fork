package dev.launcherfork

import dev.launcherfork.UiTestKit.THEMES
import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.UiTestKit.key
import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.mouse
import dev.launcherfork.UiTestKit.preview
import dev.launcherfork.UiTestKit.relayout
import dev.launcherfork.UiTestKit.resize
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.CommunityLinks
import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.FolderOpener
import dev.launcherfork.domain.GameFolders
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.state.ClientState
import dev.launcherfork.ui.state.GameAction
import dev.launcherfork.ui.state.GameActions
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Properties

/**
 * Game-actions button next to ИГРАТЬ and its dropdown: a square icon button as tall as the CTA, the menu floats above
 * it (unmanaged, never moves the bottom row), rows enabled by client state, destructive rows ask first (cancel = no
 * action) and the CTA is disabled while an operation runs, the folder row goes through FolderOpener with the game
 * folder from the settings (never LinkOpener).
 */
class GameActionsMenuTest {
    private val folders = mutableListOf<Path>()
    private val links = mutableListOf<String>()

    private fun withMain(lang: String = "ru", check: (StageNavigator, MainController) -> Unit) {
        val app = AppContext.mock(language = lang, speed = 50.0, authDelayMs = 0, links = LinkOpener { links += it },
            folders = FolderOpener { folders.add(it) })
        val nav = mainOnScreen(app, lang)
        try {
            FxTestSupport.onFx { relayout(nav) }
            check(nav, nav.mainController!!)
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    private fun MainController.menu() = defaultStatusPaneController.gameActionsController
    private fun MainController.item(a: GameAction) = menu().items.getValue(a)
    private fun MainController.state() = store.state(selected!!).get()

    @Test
    fun menuFloatsAboveTheButtonWithoutMovingTheBottomRow() = withMain { nav, main ->
        THEMES.forEach { theme ->
            listOf(Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT, Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT).forEach { (w, h) ->
                FxTestSupport.onFx { applyTheme(nav.mainStage.scene, theme, "ru") }
                resize(nav, w, h)
                FxTestSupport.onFx { checkFloating(nav, main, "$theme ${w.toInt()}x${h.toInt()}", theme, w) }
            }
        }
        resize(nav, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
    }

    private fun checkFloating(nav: StageNavigator, main: MainController, where: String, theme: String, w: Double) {
        val scene = nav.mainStage.scene
        val status = main.defaultStatusPaneController
        val nodes = listOf(status.downloadButton, status.gameActionsButton, status.progressBar, status.downloadingStatus)
        val closed = nodes.map { it.sceneBounds() }
        val play = status.downloadButton.sceneBounds()
        val btn = status.gameActionsButton.sceneBounds()
        assertEquals(play.height, btn.height, 0.5, "$where: actions button not as tall as ИГРАТЬ")
        assertEquals(btn.width, btn.height, 0.5, "$where: actions button not square")
        assertTrue(btn.maxX <= play.minX, "$where: actions button not left of ИГРАТЬ")
        val glyph = status.gameActionsGlyph.sceneBounds()
        assertEquals(btn.centerX, glyph.centerX, 1.0, "$where: glyph off-centre"); assertEquals(btn.centerY, glyph.centerY, 1.0)
        assertTrue(glyph.width in 12.0..22.0, "$where: glyph $glyph")
        checkEllipsis(status.gameActionsButton, status.gameActionsGlyph, theme, where)

        main.menu().show()
        relayout(nav)
        assertFalse(main.menu().gameActionsMenu.isManaged, "$where: the menu must not take part in the layout")
        assertTrue("open" in status.gameActionsButton.styleClass, "$where: button not marked open")
        nodes.zip(closed).forEach { (n, c) -> assertEquals(c, n.sceneBounds(), "$where: ${n.id} moved when the menu opened") }
        val menu = main.menu().gameActionsMenu.sceneBounds()
        assertTrue(menu.width > 180 && menu.height > 150, "$where: menu not laid out $menu")
        assertTrue(menu.maxY <= btn.minY && btn.minY - menu.maxY <= 14, "$where: menu $menu not right above the button $btn")
        assertEquals(btn.maxX, menu.maxX, 1.0, "$where: menu not right-aligned with the button")
        assertTrue(menu.minX >= 0 && menu.minY >= 0 && menu.maxX <= scene.width && menu.maxY <= scene.height, "$where: menu outside")
        // nothing clips it and it can be clicked (every ancestor's bounds cover it)
        generateSequence(main.menu().gameActionsMenu.parent) { it.parent }.forEach { p ->
            assertNull(p.clip, "$where: ${p.id ?: p} clips the menu")
            assertTrue(p.localToScene(p.boundsInLocal).contains(menu.centerX, menu.centerY), "$where: ${p.id ?: p} does not cover the menu")
        }
        // rows: icon left, all icons in one column, inside the card
        val icons = main.menu().items.values.map { it.lookup(".ga-icon")!!.sceneBounds() }
        icons.forEach { i ->
            assertEquals(icons[0].minX, i.minX, 0.5, "$where: icons not in one column")
            assertEquals(16.0, i.width, 0.5); assertTrue(menu.contains(i), "$where: icon outside the card")
        }
        if (w == Theme.WINDOW_WIDTH) preview(scene, "game-actions-$theme")
        main.hideMenus()
        relayout(nav)
        assertFalse(main.menu().isShown)
    }

    /**
     * The actions glyph is a small horizontal ellipsis: three equal 2.5 px round dots, 3 px apart, centred; muted at
     * rest (the theme's -muted-foreground), -foreground on hover / focus / open. The button is a ghost square.
     */
    private fun checkEllipsis(button: javafx.scene.control.Button, glyph: javafx.scene.shape.SVGPath, theme: String, where: String) {
        val dots = Regex("""M([\d.]+),([\d.]+) A([\d.]+),([\d.]+) 0 1,1 ([\d.]+),([\d.]+) A[\d.]+,[\d.]+ 0 1,1 [\d.]+,[\d.]+ Z""")
            .findAll(glyph.content).map { m -> m.groupValues.drop(1).map(String::toDouble) }.toList()
        assertEquals(3, dots.size, "$where: expected three dots in '${glyph.content}'")
        dots.forEach { (x, y, rx, ry, x2, y2) ->
            assertEquals(1.25, rx, 1e-9); assertEquals(1.25, ry, 1e-9)     // round, Ø2.5
            assertEquals(x, x2, 1e-9); assertEquals(y + 2.5, y2, 1e-9)      // a full circle
            assertEquals(0.0, y, 1e-9)                                     // all on one horizontal line
        }
        val gaps = dots.zipWithNext { a, b -> (b[0] - 1.25) - (a[0] + 1.25) }
        assertEquals(listOf(3.0, 3.0), gaps, "$where: dots not 3 px apart")
        assertNull(glyph.stroke, "$where: dots must be filled, not stroked")
        assertEquals(13.5, glyph.layoutBounds.width, 0.01); assertEquals(2.5, glyph.layoutBounds.height, 0.01)
        val quiet = !button.isHover && !button.isFocused && "open" !in button.styleClass
        assertEquals(token(theme, if (quiet) "-muted-foreground" else "-foreground"), glyph.fill, "$where: dot colour")
        // ghost: no tile fill at rest, thin border
        assertTrue("tile" !in button.styleClass, "$where: the actions button should be a ghost square, not a filled tile")
        if (quiet) assertTrue(button.background == null || button.background.fills.all { it.fill == javafx.scene.paint.Color.TRANSPARENT },
            "$where: ghost button has a fill ${button.background?.fills?.map { it.fill }}")
    }

    private operator fun <T> List<T>.component6() = this[5]

    private fun token(theme: String, name: String): javafx.scene.paint.Color {
        val file = mapOf("exbo" to "theme.css", "zone" to "theme-zone.css", "shadcn" to "theme-shadcn.css").getValue(theme)
        val css = javaClass.getResource("/dev/launcherfork/ui/style/$file")!!.readText()
        val hex = Regex("""(?m)^\s*${Regex.escape(name)}:\s*([^;]+);""").find(css)!!.groupValues[1].trim()
        return javafx.scene.paint.Color.web(hex)
    }

    @Test
    fun rowsFollowGameActionsAndTheClientState() = withMain { _, main ->
        FxTestSupport.onFx {
            val menu = main.menu()
            assertEquals(GameAction.entries, menu.items.keys.toList())
            assertEquals(listOf("Проверить файлы", "Открыть папку игры", "Открыть папку логов", "Настройки игры", "Переустановить", "Удалить игру"),
                menu.items.values.map { it.text })
            assertEquals(listOf(GameAction.REINSTALL, GameAction.DELETE), menu.items.filter { "danger" in it.value.styleClass }.keys.toList())
            assertEquals("STALZONE", menu.gameActionsCaption.text)
            fun enabled() = menu.items.filter { !it.value.isDisable }.keys
            assertEquals(ClientState.Ready, main.state())
            assertEquals(GameAction.entries.toSet(), enabled())
            main.store.state(main.selected!!).set(ClientState.NotInstalled)
            assertEquals(setOf(GameAction.OPEN_LOGS, GameAction.SETTINGS), enabled(), "not installed: logs + settings")
            main.store.state(main.selected!!).set(ClientState.Running)
            assertEquals(setOf(GameAction.VERIFY, GameAction.OPEN_FOLDER, GameAction.OPEN_LOGS, GameAction.SETTINGS), enabled())
            main.store.state(main.selected!!).set(ClientState.Paused(0.4, 10))
            assertEquals(setOf(GameAction.OPEN_FOLDER, GameAction.OPEN_LOGS, GameAction.SETTINGS, GameAction.DELETE), enabled())
            main.store.state(main.selected!!).set(ClientState.Busy(ClientOperation.VERIFY, 0.3))
            assertEquals(setOf(GameAction.OPEN_FOLDER, GameAction.OPEN_LOGS, GameAction.SETTINGS), enabled())
            main.store.state(main.selected!!).set(ClientState.Ready)
        }
    }

    @Test
    fun destructiveRowsAskFirstAndCancelDoesNothing() = withMain { _, main ->
        listOf(GameAction.DELETE to "main.confirm.delete", GameAction.REINSTALL to "main.confirm.reinstall").forEach { (action, _) ->
            FxTestSupport.onFx {
                val modal = main.deletionPaneController
                main.menu().show()
                main.item(action).fire()
                assertFalse(main.menu().isShown, "a pick closes the menu")
                assertTrue(modal.isShowing, "$action: no confirmation")
                assertEquals(ClientState.Ready, main.state(), "$action started before the confirmation")
                assertTrue(GameActions.needsConfirmation(action, ClientState.Ready))
                modal.deletionNo.fire()
                assertFalse(modal.isShowing)
                assertEquals(ClientState.Ready, main.state(), "$action: cancel must not start it")
                // Esc cancels too
                main.item(action).fire()
                assertTrue(modal.isShowing)
                key(main.root, KeyCode.ESCAPE)
                assertFalse(modal.isShowing)
                assertEquals(ClientState.Ready, main.state(), "$action: Esc must not start it")
                assertFalse(main.defaultStatusPaneController.downloadButton.isDisable)
            }
        }
        // the confirmation texts name the action; confirm starts it and ИГРАТЬ is disabled until it ends
        FxTestSupport.onFx {
            main.item(GameAction.DELETE).fire()
            val modal = main.deletionPaneController
            assertEquals("Удалить игру", modal.deletionTitle.text)
            assertEquals("УДАЛИТЬ", modal.deletionYes.text)
            assertEquals("ОТМЕНА", modal.deletionNo.text)
            modal.deletionYes.fire()
            val s = main.state()
            assertTrue(s is ClientState.Busy && s.operation == ClientOperation.DELETE, "confirm starts the deletion: $s")
            assertTrue(main.defaultStatusPaneController.downloadButton.isDisable, "ИГРАТЬ must be disabled during the operation")
            assertEquals("УДАЛЕНИЕ", main.defaultStatusPaneController.downloadButton.text)
        }
        assertTrue(FxTestSupport.waitFx { main.state() == ClientState.NotInstalled }, "mock deletion did not finish")
        FxTestSupport.onFx {
            assertFalse(main.defaultStatusPaneController.downloadButton.isDisable)
            assertEquals("СКАЧАТЬ ИГРУ", main.defaultStatusPaneController.downloadButton.text)
            // a stale confirm after cancel can never run: open + cancel, then the confirm button does nothing new
            main.store.state(main.selected!!).set(ClientState.Ready)
            main.item(GameAction.REINSTALL).fire()
            main.deletionPaneController.deletionNo.fire()
            main.deletionPaneController.onConfirm()
            assertEquals(ClientState.Ready, main.state())
        }
    }

    @Test
    fun verifyStartsAtOnceButAsksWhileTheGameRuns() = withMain { _, main ->
        FxTestSupport.onFx {
            main.item(GameAction.VERIFY).fire()
            assertFalse(main.deletionPaneController.isShowing, "verify of an idle client needs no confirmation")
            assertTrue(main.state() is ClientState.Busy)
            assertTrue(main.defaultStatusPaneController.downloadButton.isDisable)
            assertEquals("ПРОВЕРКА", main.defaultStatusPaneController.downloadButton.text)
        }
        assertTrue(FxTestSupport.waitFx { main.state() == ClientState.Ready })
        FxTestSupport.onFx { main.store.state(main.selected!!).set(ClientState.Running) }
        FxTestSupport.onFx {
            main.item(GameAction.VERIFY).fire()
            assertTrue(main.deletionPaneController.isShowing, "the game runs: ask first")
            assertEquals("Проверить файлы", main.deletionPaneController.deletionTitle.text)
            main.deletionPaneController.deletionNo.fire()
            assertEquals(ClientState.Running, main.state(), "cancel keeps the game running")
            main.item(GameAction.VERIFY).fire()
            main.deletionPaneController.deletionYes.fire()
            assertTrue(main.state() is ClientState.Busy)
        }
    }

    @Test
    fun openFolderUsesFolderOpenerWithTheGameFolderNeverLinkOpener() = withMain { _, main ->
        FxTestSupport.onFx {
            main.item(GameAction.OPEN_FOLDER).fire()
            val expected = GameFolders.dirOf(LauncherSettings().gameRoot, main.selected!!)
            assertEquals(listOf(expected), folders)
            assertTrue(expected.endsWith(Path.of("Games", "EXBO", main.selected!!.id)), "$expected")
            assertTrue(links.isEmpty(), "the folder must not go through LinkOpener: $links")
            assertFalse(main.deletionPaneController.isShowing)
            assertEquals(ClientState.Ready, main.state())
        }
    }

    @Test
    fun openLogsUsesFolderOpenerWithExboRuntimeLogs() = withMain { _, main ->
        FxTestSupport.onFx {
            folders.clear()
            main.item(GameAction.OPEN_LOGS).fire()
            assertEquals(listOf(ExboPaths.runtimeLogs()), folders)
            assertTrue(links.isEmpty(), "logs must not go through LinkOpener: $links")
            assertFalse(main.deletionPaneController.isShowing)
        }
    }

    @Test
    fun settingsRowOpensTheSettings() = withMain { _, main ->
        FxTestSupport.onFx {
            main.item(GameAction.SETTINGS).fire()
            assertTrue(main.settingsPane.isVisible)
            assertFalse(main.homePane.isVisible)
        }
    }

    @Test
    fun menuClosesOnEscOutsidePressAndOtherMenus() = withMain { nav, main ->
        FxTestSupport.onFx {
            val status = main.defaultStatusPaneController
            status.gameActionsButton.fire()
            assertTrue(main.menu().isShown, "the button opens the menu")
            status.gameActionsButton.fire()
            assertFalse(main.menu().isShown, "and closes it")
            main.menu().show(); relayout(nav)
            key(main.root, KeyCode.ESCAPE)
            assertFalse(main.menu().isShown, "Esc closes it")
            main.menu().show(); relayout(nav)
            val far = main.centerPane.sceneBounds()
            mouse(main.centerPane, MouseEvent.MOUSE_PRESSED, far.minX + 20, far.minY + 20)
            assertFalse(main.menu().isShown, "an outside press closes it")
            main.menu().show()
            main.profileBarController.showMenu()
            assertFalse(main.menu().isShown, "one menu at a time (profile)")
            main.menu().show()
            assertFalse(main.profileBarController.isMenuShown, "one menu at a time (game actions)")
            main.headerController.showCommunityMenu()
            assertFalse(main.menu().isShown, "one menu at a time (communities)")
            // keyboard: Enter on the button opens with the first enabled row focused, Down moves, Esc returns to the button
            main.hideMenus()
            status.gameActionsButton.requestFocus()
            key(status.gameActionsButton, KeyCode.ENTER)
            assertTrue(main.menu().isShown)
            assertSame(main.item(GameAction.VERIFY), nav.mainStage.scene.focusOwner)
            key(main.item(GameAction.VERIFY), KeyCode.DOWN)
            assertSame(main.item(GameAction.OPEN_FOLDER), nav.mainStage.scene.focusOwner)
            key(main.root, KeyCode.ESCAPE)
            assertSame(status.gameActionsButton, nav.mainStage.scene.focusOwner)
        }
    }

    @Test
    fun stringsInEveryBundleAndNoX() {
        val keys = GameAction.entries.map { it.textKey } + listOf("main.actions.button", "main.actions.menu.a11y",
            "main.client_button.verifying", "main.client_button.reinstalling", "main.client_button.deleting", "main.confirm.cancel") +
            ClientOperation.entries.flatMap { op -> val k = "main.confirm.${op.name.lowercase()}"; listOf("$k.title", "$k.text", "$k.confirm", "main.status.busy.${op.name.lowercase()}") }
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            assertNull(props.getProperty("main.client.tools"), "$file: stale tools key")
        }
        assertTrue(CommunityLinks.all.none { it.id == "x" || "x.com" in it.url || "twitter" in it.url })
    }
}
