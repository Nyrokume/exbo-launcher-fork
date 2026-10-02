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
                        val scene = Scene(loaded.root, Theme.MAIN_WIDTH, Theme.MAIN_HEIGHT, Color.TRANSPARENT)
                        Theme.apply(scene, lang)
                        loaded.root.applyCss()
                        loaded.root.layout()
                    }
                }
            }
        }
    }

    @TestFactory
    fun mainFxmlKeepsOriginalFxIds(): List<DynamicTest> {
        // fx:ids of the original main.fxml that must survive the split into fx:include files (sample of ~40)
        val ids = listOf(
            "root", "main", "background", "defaultStatusPane", "clientSettingsButton", "progressBar", "downloadButton",
            "downloadingStatus", "newsBg", "serverDesc", "pagesPane", "serversPane", "settingsButton", "articleBg",
            "articleCloseButton", "articleCaption", "advancedNewsPane", "firstNewsPane", "firstNewsBg", "firstNewsDescPane",
            "secondNewsDesc", "discord", "vk", "bigLogo", "mainLink", "clientSettingsPane", "linksContainer", "support",
            "profile", "forum", "settingsPane", "topCenter", "loginButton", "rightCorner", "leftCorner", "logoutPane",
            "logoutButtonRight", "updatePane", "updateYes", "licensePane", "licenseFirst", "licenseLoading", "offlinePane",
            "offlineProgress", "downloadWarnPane", "defenderPane", "defenderRemember", "exceptionPane", "exceptionDesc",
            "loadingPane", "loadingIndicator", "deletionPane", "installPane", "installDir", "closeButton", "hideButton",
        )
        val present = fxIdsReachableFrom("main.fxml")
        return ids.map { id -> DynamicTest.dynamicTest(id) { assertTrue(id in present, "fx:id '$id' is missing from main.fxml + includes") } }
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
}
