package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.data.exbo.ExboApi
import dev.launcherfork.data.exbo.ExboAuthService
import dev.launcherfork.data.exbo.ExboBackend
import dev.launcherfork.data.exbo.ExboRealmGate
import dev.launcherfork.data.exbo.ExboServerRepository
import dev.launcherfork.data.local.SettingsFile
import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.ExboRealm
import dev.launcherfork.domain.chooseExboRealm
import dev.launcherfork.i18n.Strings
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.screens.auth.InitialAuthController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ExboRealmTest {
    @Test
    fun roamingFoldersStayOnTheInstallerNames() {
        assertEquals("EXBO", ExboPaths.roamingRoot().fileName.toString())
        assertEquals("EXBO", ExboPaths.roamingRoot(ExboRealm.RU).fileName.toString())
        assertEquals("Exbo_East", ExboPaths.roamingRoot(ExboRealm.GLOBAL).fileName.toString())
        assertEquals("SOFTWARE\\EXBO", ExboRealm.RU.registryKey)
        assertEquals("SOFTWARE\\Exbo_East", ExboRealm.GLOBAL.registryKey)
    }

    @Test
    fun aMissingEuInstallDoesNotReplaceTheRuOne() {
        assertEquals(ExboRealm.RU, chooseExboRealm(ExboRealm.GLOBAL) { it == ExboRealm.RU })
        assertEquals(ExboRealm.GLOBAL, chooseExboRealm(ExboRealm.GLOBAL) { it == ExboRealm.GLOBAL })
        assertEquals(ExboRealm.RU, chooseExboRealm(null) { it == ExboRealm.RU })
        assertEquals(ExboRealm.GLOBAL, chooseExboRealm(null) { it == ExboRealm.GLOBAL })
        assertEquals(ExboRealm.RU, chooseExboRealm(ExboRealm.fromName("nope")) { false })
        assertEquals(ExboRealm.GLOBAL, ExboRealm.fromName("eu"))
        assertEquals(ExboRealm.RU, ExboRealm.fromName("ru"))
    }

    @Test
    fun aNewerRuClientWinsOverASavedGlobalRealm() {
        val both: (ExboRealm) -> Boolean = { true }
        val touched: (ExboRealm) -> Long? = {
            when (it) {
                ExboRealm.RU -> 200L
                ExboRealm.GLOBAL -> 50L
            }
        }
        assertEquals(ExboRealm.RU, chooseExboRealm(ExboRealm.GLOBAL, both, touched))
        assertEquals(
            ExboRealm.GLOBAL,
            chooseExboRealm(ExboRealm.GLOBAL, both, touched, pinRequested = true),
        )
        assertEquals(ExboRealm.GLOBAL, chooseExboRealm(ExboRealm.GLOBAL, both))
        assertEquals(ExboRealm.RU, chooseExboRealm(null, both, touched))
    }

    @Test
    fun globalSessionIsStoredApartFromTheRuKey() {
        val registry = MemoryRegistry()
        val root = Files.createTempDirectory("exbo-global-realm")
        val api = ExboApi(ExboRealm.GLOBAL, root, registry)
        api.remember("eu-user", "sess")
        assertEquals("eu-user", registry.readValues(ExboRealm.GLOBAL.registryKey)["EXBO_LOGIN"])
        assertTrue(registry.readValues(ExboRealm.RU.registryKey).isEmpty())
        assertEquals(root, api.dataRoot)
        root.toFile().deleteRecursively()
    }

    @Test
    fun switchingRealmFollowsThatInstallAndRefusesAMissingOne() {
        val registry = MemoryRegistry()
        val ruRoot = Files.createTempDirectory("exbo-realm-ru")
        val euRoot = Files.createTempDirectory("exbo-realm-eu")
        val ru = ExboApi(ExboRealm.RU, ruRoot, registry)
        val eu = ExboApi(ExboRealm.GLOBAL, euRoot, registry)
        eu.remember("east", null)
        val backend = ExboBackend(ru)
        val auth = ExboAuthService(backend)
        val servers = ExboServerRepository(ru)
        val settings = Files.createTempDirectory("exbo-realm-settings").resolve("settings.properties")
        val gate = ExboRealmGate(
            backend,
            auth,
            servers,
            settings,
            present = { true },
            open = { realm -> if (realm == ExboRealm.GLOBAL) eu else ru },
        )
        assertTrue(gate.select(ExboRealm.GLOBAL))
        assertEquals(ExboRealm.GLOBAL, gate.current)
        assertEquals(euRoot, backend.api.dataRoot)
        assertEquals("east", auth.savedLogin)
        assertEquals("global", SettingsFile.read(settings, ExboRealm.SETTINGS_KEY))
        val refused = ExboRealmGate(
            backend,
            auth,
            servers,
            settings,
            present = { it == ExboRealm.GLOBAL },
            open = { eu },
        )
        assertFalse(refused.select(ExboRealm.RU))
        assertEquals(ExboRealm.GLOBAL, backend.api.realm)
        assertEquals("global", SettingsFile.read(settings, ExboRealm.SETTINGS_KEY))
        ruRoot.toFile().deleteRecursively()
        euRoot.toFile().deleteRecursively()
    }

    @Test
    fun loginHasNoRealmSwitch() = FxTestSupport.onFx {
        val app = AppContext.mock(authDelayMs = 0)
        val registry = MemoryRegistry()
        val root = Files.createTempDirectory("exbo-realm-login")
        val api = ExboApi(ExboRealm.RU, root, registry)
        val backend = ExboBackend(api)
        val auth = ExboAuthService(backend)
        val servers = ExboServerRepository(api)
        val settings = Files.createTempDirectory("exbo-realm-login-settings").resolve("settings.properties")
        val gate = ExboRealmGate(backend, auth, servers, settings, present = { it == ExboRealm.RU }, open = { api })
        val ctx = UiContext(
            app.services, NoopNavigator, Strings.bundle("ru"), "ru", app.clientStates,
            false, app.desk, true, gate,
        )
        val form = Fxml.load<InitialAuthController>(ctx, "auth_panel.fxml").controller
        assertTrue(form.root.lookup("#realmRow") == null)
        assertTrue(form.root.lookupAll(".zone-realm").isEmpty())
        root.toFile().deleteRecursively()
        Unit
    }

    @Test
    fun switchToAdoptsTheRegistryLoginWhenAutoAuthHasNoSession() {
        val registry = MemoryRegistry()
        val root = Files.createTempDirectory("exbo-switch-to")
        val api = ExboApi(ExboRealm.RU, root, registry)
        val auth = ExboAuthService(api)
        registry.writeValues(ExboRealm.RU.registryKey, mapOf("EXBO_LOGIN" to "Nyrokume"))
        assertFalse(auth.switchTo("Nyrokume"), "auto-auth cannot run without EXBO_SESSION")
        assertEquals("Nyrokume", auth.currentSession?.login)
        assertEquals("Nyrokume", auth.savedLogin)
        root.toFile().deleteRecursively()
    }
}
