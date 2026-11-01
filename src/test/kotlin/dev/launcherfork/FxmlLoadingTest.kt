package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import javafx.fxml.FXML
import javafx.scene.Scene
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Loads EVERY FXML with its real controller (ru and en) and fails on:
 *  - FXMLLoader errors (unknown class/property, bad include, missing handler method, missing %key resolution),
 *  - any @FXML field left uninjected (fx:id renamed/removed in the FXML) - also in fx:include controllers.
 */
private const val FX_NS = "http://javafx.com/fxml/1"

class FxmlLoadingTest {
    // resolve via a file: the package dir also exists in build/classes, so getResource(dir) may point there
    private val uiRoot: Path = Paths.get(requireNotNull(Theme::class.java.getResource(Theme.UI_ROOT + "main.fxml")).toURI()).parent

    private fun allFxml(): List<String> = Files.walk(uiRoot).use { s ->
        s.filter { it.extension == "fxml" }.map { uiRoot.relativize(it).invariantSeparatorsPathString }.sorted().toList()
    }

    @TestFactory
    fun everyFxmlLoadsWithAllInjections(): List<DynamicTest> {
        val files = allFxml()
        assertTrue(files.size >= 20, "expected all FXML files, found $files")
        return listOf("ru", "en").flatMap { lang ->
            files.map { path ->
                DynamicTest.dynamicTest("$lang: $path") {
                    FxTestSupport.onFx {
                        val loaded = Fxml.load<Any?>(FxTestSupport.context(lang), path)
                        val controller = assertNotNull(loaded.controller, "$path has no controller")
                        assertAllInjected(controller!!, path, mutableSetOf())
                        // apply CSS + layout once in a scene with the theme, like the app does
                        val scene = Scene(loaded.root, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT, Color.TRANSPARENT)
                        Theme.apply(scene, lang)
                        loaded.root.applyCss()
                        loaded.root.layout()
                    }
                }
            }
        }
    }

    @TestFactory
    fun mainFxmlKeepsStructureIds(): List<DynamicTest> {
        // fx:ids the controllers, the screenshot tour and the docs rely on (main.fxml + every fx:include)
        val ids = listOf(
            "root", "layout", "main", "background", "backgroundShade", "homePane", "centerPane", "newsPanel", "settingsPane",
            // sidebar + game entries
            "serversList", "sidebar", "logo", "gamesLabel", "serversPane", "settingsButton",
            // profile control (top-right) + dropdown
            "profileLayer", "profileBar", "profileRoot", "loginButton", "accountName", "profileMenu", "inviteFriend", "logout",
            // header links
            "header", "linksContainer", "profile", "forum", "support",
            // bottom bar + game-actions button and its dropdown
            "defaultStatusPane", "downloadingStatus", "progressBar", "gameActionsButton", "downloadButton", "gameActions", "gameActionsMenu",
            // settings
            "settings", "scroll", "ramBar", "ramSummary", "unlockFovField", "unlockShadowsToggle", "exitOnStart", "exitNever", "downloadLimitField", "uploadLimitField", "p2pNotMobile",
            "defenderToggle", "startOnDownloadedToggle", "trayToggle", "autostartToggle", "langRu", "langEn",
            // modals (original names)
            "logoutPane", "logoutButtonRight", "updatePane", "updateYes", "licensePane", "licenseFirst", "licenseLoading", "offlinePane",
            "offlineProgress", "downloadWarnPane", "defenderPane", "defenderRemember", "exceptionPane", "exceptionDesc",
            "loadingPane", "loadingIndicator", "deletionPane", "installPane", "installDir",
        )
        val present = fxIdsReachableFrom("main.fxml")
        return ids.map { id -> DynamicTest.dynamicTest(id) { assertTrue(id in present, "fx:id '$id' is missing from main.fxml + includes") } }
    }

    @org.junit.jupiter.api.Test
    fun windowFxmlHasFrameAndOverlay() {
        val present = fxIdsReachableFrom("window.fxml")
        listOf("windowRoot", "titleBar", "dragArea", "hideButton", "maximizeButton", "closeButton", "contentHost", "overlayLayer")
            .forEach { assertTrue(it in present, "window.fxml misses fx:id '$it'") }
    }

    @org.junit.jupiter.api.Test
    fun authWindowFxmlHasFrameAndPanelHost() {
        val present = fxIdsReachableFrom("auth_window.fxml")
        listOf("authWindowRoot", "authTitleBar", "authLogo", "authDragArea", "authHideButton", "authCloseButton", "panelHost")
            .forEach { assertTrue(it in present, "auth_window.fxml misses fx:id '$it'") }
    }

    /** The account card left the sidebar, the Profile link left the header (both are in the profile menu now). */
    @org.junit.jupiter.api.Test
    fun accountLivesOnlyInProfileMenu() {
        val sidebar = fxIdsReachableFrom("main/servers_list.fxml")
        assertTrue("loginButton" !in sidebar && "accountName" !in sidebar, "sidebar still has the account card")
        assertTrue("profile" !in fxIdsReachableFrom("main/header.fxml"), "header still has the Profile link")
        val menu = fxIdsReachableFrom("main/profile.fxml")
        assertTrue(menu.containsAll(listOf("loginButton", "accountName", "profile", "inviteFriend", "topUp", "logout")), "$menu")
    }

    /** fx:id without an explicit id becomes the CSS id; legacy #rules (lang_*.css, auth.css, common.css) must not hit new nodes. */
    @org.junit.jupiter.api.Test
    fun newFxmlDoNotReuseLegacyCssIds() {
        val legacy = setOf("enterButton", "enterSteam", "register", "restore", "folder", "close", "hide", "error", "info", "status",
            "input", "window", "login", "downloadButton", "downloadBar", "progress", "settingsButton", "clientSettingsButton",
            "logoutButtonLeft", "logoutButtonRight", "offlineButton", "licenseLeft", "licenseRight", "headerBackground")
        val legacyFiles = setOf("initial_auth.fxml", "mail_auth.fxml", "update.fxml")
        for (path in allFxml().filterNot { it in legacyFiles }) {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(uiRoot.resolve(path).toFile())
            val all = doc.getElementsByTagName("*")
            for (i in 0 until all.length) {
                val el = all.item(i) as org.w3c.dom.Element
                val cssId = el.getAttribute("id").ifEmpty { el.getAttributeNS(FX_NS, "id") }
                assertTrue(cssId !in legacy, "$path: node resolves to legacy CSS id '#$cssId' - set an explicit id")
            }
        }
    }

    /** Collects fx:id attributes of an FXML file and of everything it pulls in through fx:include. */
    private fun fxIdsReachableFrom(path: String): Set<String> {
        val file = uiRoot.resolve(path)
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file.toFile())
        val ids = mutableSetOf<String>()
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as org.w3c.dom.Element
            el.getAttributeNS(FX_NS, "id").takeIf { it.isNotEmpty() }?.let(ids::add)
            if (el.namespaceURI == FX_NS && el.localName == "include") {
                val child = file.parent.resolve(el.getAttribute("source")).normalize()
                ids += fxIdsReachableFrom(uiRoot.relativize(child).invariantSeparatorsPathString)
            }
        }
        return ids
    }

    private fun assertAllInjected(controller: Any, path: String, seen: MutableSet<Any>) {
        if (!seen.add(controller)) return
        var type: Class<*>? = controller.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (!field.isAnnotationPresent(FXML::class.java)) continue
                field.isAccessible = true
                val value = field.get(controller)
                assertNotNull(value, "$path: @FXML field ${type.simpleName}.${field.name} was not injected (missing fx:id?)")
                if (field.name.endsWith("Controller")) assertAllInjected(value, "$path -> ${field.name}", seen)
            }
            type = type.superclass
        }
    }

    @org.junit.jupiter.api.Test
    fun i18nBundlesHaveSameKeys() {
        val ru = dev.launcherfork.i18n.Strings.bundle("ru").keySet()
        val en = dev.launcherfork.i18n.Strings.bundle("en").keySet()
        assertEquals(ru, en)
    }

    /** No word mixes Latin and Cyrillic letters (the old "Aвторизация" had a Latin A). */
    @org.junit.jupiter.api.Test
    fun noMixedScriptWords() {
        val bundle = dev.launcherfork.i18n.Strings.bundle("ru")
        val mixed = bundle.keySet().flatMap { key ->
            Regex("""[\p{L}]+""").findAll(bundle.getString(key)).map { it.value }
                .filter { w -> w.any { it in 'A'..'Z' || it in 'a'..'z' } && w.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC } }
                .map { "$key: $it" }.toList()
        }
        assertTrue(mixed.isEmpty(), "mixed Latin/Cyrillic words: $mixed")
        assertEquals("EXBO Launcher: Вход", bundle.getString("title.auth"))
    }
}
