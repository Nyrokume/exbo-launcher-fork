package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.domain.ExternalLinks
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.domain.RegisterError
import dev.launcherfork.domain.RegisterResult
import dev.launcherfork.domain.RegistrationField
import dev.launcherfork.domain.RegistrationRequest
import dev.launcherfork.domain.RegistrationRules
import dev.launcherfork.i18n.Strings
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.Navigator
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.screens.auth.AgeGateController
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.RegisterController
import javafx.stage.Window
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Navigator that records calls (registration steps navigate between panels). */
class RecordingNavigator : Navigator {
    val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
    override fun showLauncherUpdate(afterUpdate: () -> Unit) { calls += "update" }
    override fun showAuth() { calls += "auth" }
    override fun showMailAuth() { calls += "mail" }
    override fun showTotpAuth() { calls += "totp" }
    override fun showAgeGate(birthDate: LocalDate?) { calls += "age:${birthDate ?: ""}" }
    override fun showRegister(birthDate: LocalDate) { calls += "register:$birthDate" }
    override fun showRegistered(login: String) { calls += "registered:$login" }
    override fun showMain() { calls += "main" }
    override fun switchLanguage(lang: String) = Unit
    override fun minimize() = Unit
    override fun close() = Unit
    override val window: Window? = null
}

fun recordingContext(nav: RecordingNavigator, lang: String = "ru", authDelayMs: Long = 0): UiContext {
    val app = AppContext.mock(language = lang, speed = 50.0, authDelayMs = authDelayMs)
    return UiContext(app.services, nav, Strings.bundle(lang), lang, app.clientStates, false)
}

class RegistrationTest {
    private val today = LocalDate.of(2026, 10, 6)

    // ---------- rules ----------

    @Test
    fun emailUsernamePasswordRules() {
        listOf("a@b.cc", "player.one+tag@mail.ru", "x_y@sub.domain.org").forEach { assertTrue(RegistrationRules.isEmailValid(it), it) }
        listOf("", "plain", "a@b", "a@b.c", "a b@c.dd", "a@@b.cc", "@b.cc", "a".repeat(45) + "@b.ccc").forEach {
            assertFalse(RegistrationRules.isEmailValid(it), it)
        }
        listOf("abc", "Player_1", "a".repeat(16), "___").forEach { assertTrue(RegistrationRules.isUsernameValid(it), it) }
        listOf("ab", "a".repeat(17), "игрок", "with space", "dash-name", "dot.name", "").forEach {
            assertFalse(RegistrationRules.isUsernameValid(it), it)
        }
        assertTrue(RegistrationRules.isPasswordValid("12345678"))
        assertFalse(RegistrationRules.isPasswordValid("1234567"))
    }

    @Test
    fun validateListsInvalidFieldsInFormOrder() {
        assertEquals(emptyList<RegistrationField>(), RegistrationRules.validate(" a@b.cc ", " player ", "secret12", true))
        assertEquals(
            listOf(RegistrationField.EMAIL, RegistrationField.USERNAME, RegistrationField.PASSWORD, RegistrationField.LICENSE),
            RegistrationRules.validate("bad", "x", "short", false),
        )
        assertEquals(listOf(RegistrationField.LICENSE), RegistrationRules.validate("a@b.cc", "player", "secret12", false))
    }

    @Test
    fun ageIsCountedInFullYears() {
        assertEquals(18, RegistrationRules.MIN_AGE)
        assertTrue(RegistrationRules.isOldEnough(LocalDate.of(2008, 10, 6), today), "18th birthday today")
        assertFalse(RegistrationRules.isOldEnough(LocalDate.of(2008, 10, 7), today), "18th birthday tomorrow")
        assertEquals(26, RegistrationRules.age(LocalDate.of(2000, 7, 26), today))
        assertTrue(RegistrationRules.age(LocalDate.of(2027, 1, 1), today) < 0)
        assertEquals(29, RegistrationRules.daysIn(2, 2000))
        assertEquals(28, RegistrationRules.daysIn(2, 2001))
        assertEquals(29, RegistrationRules.daysIn(2, null), "no year yet: 29 February stays choosable")
        assertNull(RegistrationRules.dateOf(31, 4, 2000))
        assertNull(RegistrationRules.dateOf(29, 2, 2001))
        assertNull(RegistrationRules.dateOf(1, null, 2000))
        assertEquals(LocalDate.of(2000, 7, 26), RegistrationRules.dateOf(26, 7, 2000))
        val years = RegistrationRules.years(today)
        assertEquals(2026, years.first())
        assertEquals(1926, years.last())
    }

    @Test
    fun mockRegister() {
        val auth = MockAuthService(delayMs = 0)
        val adult = LocalDate.of(2000, 7, 26)
        fun req(user: String, email: String = "$user@mail.ru", birth: LocalDate = adult) = RegistrationRequest(email, user, "secret12", birth, false)
        assertEquals(RegisterResult.Success("newbie"), auth.register(req("newbie")))
        assertEquals(RegisterResult.Failure(RegisterError.USERNAME_TAKEN), auth.register(req("taken", "other@mail.ru")))
        assertEquals(RegisterResult.Failure(RegisterError.EMAIL_TAKEN), auth.register(req("fresh", "taken@mail.ru")))
        assertEquals(RegisterResult.Failure(RegisterError.UNDER_AGE), auth.register(req("kid", birth = LocalDate.now().minusYears(10))))
        // the real-run mock waits like a server (500 ms by default)
        val slow = MockAuthService()
        val t0 = System.nanoTime()
        slow.register(req("newbie"))
        assertTrue((System.nanoTime() - t0) / 1_000_000 >= 450)
    }

    // ---------- age gate ----------

    private fun ageGate(nav: RecordingNavigator, lang: String = "ru") = FxTestSupport.onFx {
        Fxml.load<AgeGateController>(recordingContext(nav, lang), "age_gate_panel.fxml").controller.also { it.today = today }
    }

    @Test
    fun ageGateContinueNeedsAllThreeParts() {
        val nav = RecordingNavigator()
        val c = ageGate(nav)
        FxTestSupport.onFx {
            assertTrue(c.continueButton.isDisabled)
            assertEquals(12, c.monthBox.items.size)
            assertEquals("Июль", c.monthBox.converter.toString(7))
            assertEquals(2026, c.yearBox.items.first())
            c.dayBox.value = 26
            c.monthBox.value = 7
            assertTrue(c.continueButton.isDisabled)
            c.onContinue()
            assertTrue(nav.calls.isEmpty(), "grey button does nothing")
            c.yearBox.value = 2000
            assertFalse(c.continueButton.isDisabled)
            c.onContinue()
            assertEquals(listOf("register:2000-07-26"), nav.calls)
        }
    }

    @Test
    fun ageGateRejectsTooYoungAndClearsOnChange() {
        val nav = RecordingNavigator()
        val c = ageGate(nav)
        FxTestSupport.onFx {
            c.dayBox.value = 7; c.monthBox.value = 10; c.yearBox.value = 2008 // 18 tomorrow
            c.onContinue()
            assertEquals("Регистрация доступна только с 18 лет", c.errorFirst.text)
            assertTrue(c.dayBox.pseudoClassStates.any { it.pseudoClassName == "error" })
            assertTrue(nav.calls.isEmpty())
            c.dayBox.value = 6 // 18 today
            assertTrue(c.errorFirst.text.isNullOrEmpty())
            assertFalse(c.yearBox.pseudoClassStates.any { it.pseudoClassName == "error" })
            c.onContinue()
            assertEquals(listOf("register:2008-10-06"), nav.calls)
        }
    }

    @Test
    fun ageGateDaysFollowTheMonthAndBackGoesToLogin() {
        val nav = RecordingNavigator()
        val c = ageGate(nav, "en")
        FxTestSupport.onFx {
            assertEquals(31, c.dayBox.items.size)
            c.dayBox.value = 31
            c.monthBox.value = 4
            assertEquals(30, c.dayBox.items.size)
            assertNull(c.dayBox.value, "31 April does not exist")
            c.monthBox.value = 2; c.yearBox.value = 2001
            assertEquals(28, c.dayBox.items.size)
            assertEquals("February", c.monthBox.converter.toString(2))
            c.preset(LocalDate.of(1999, 12, 31))
            assertEquals(LocalDate.of(1999, 12, 31), c.birthDate)
            c.onBack()
            assertEquals(listOf("auth"), nav.calls)
        }
    }

    // ---------- registration form ----------

    private fun form(nav: RecordingNavigator) = FxTestSupport.onFx {
        Fxml.load<RegisterController>(recordingContext(nav), "register_panel.fxml").controller.also { it.birthDate = LocalDate.of(2000, 7, 26) }
    }

    private fun RegisterController.fill(email: String = "new@mail.ru", user: String = "newbie", pass: String = "secret12", license: Boolean = true) {
        emailField.text = email; usernameField.text = user; passwordField.text = pass; licenseCheckBox.isSelected = license
    }

    @Test
    fun createButtonNeedsFieldsAndLicense() {
        val c = form(RecordingNavigator())
        FxTestSupport.onFx {
            assertTrue(c.createButton.isDisabled)
            c.fill(license = false)
            assertTrue(c.createButton.isDisabled, "license is required")
            c.newsletterCheckBox.isSelected = true
            assertTrue(c.createButton.isDisabled, "newsletter is optional and does not unlock it")
            c.licenseCheckBox.isSelected = true
            assertFalse(c.createButton.isDisabled)
            c.passwordField.text = ""
            assertTrue(c.createButton.isDisabled)
        }
    }

    @Test
    fun formatErrorsPaintFieldsUntilEdited() {
        val nav = RecordingNavigator()
        val c = form(nav)
        FxTestSupport.onFx {
            c.fill(email = "not-an-email", user = "ab", pass = "short")
            c.onCreate()
            assertEquals("Введите корректный адрес электронной почты", c.errorFirst.text)
            assertTrue(c.isMarked(c.emailField) && c.isMarked(c.usernameField) && c.isMarked(c.passwordField))
            c.emailField.text = "new@mail.ru"
            assertFalse(c.isMarked(c.emailField))
            assertTrue(c.isMarked(c.usernameField), "other fields stay red")
            c.onCreate()
            assertEquals("Имя пользователя: от 3 до 16 символов, латиница, цифры и _", c.errorFirst.text)
            c.usernameField.text = "newbie"
            c.onCreate()
            assertEquals("Пароль должен быть не короче 8 символов", c.errorFirst.text)
            assertTrue(nav.calls.isEmpty())
        }
    }

    @Test
    fun eyeShowsPasswordAndTextClickTicksBox() {
        val c = form(RecordingNavigator())
        FxTestSupport.onFx {
            c.passwordField.text = "secret12"
            assertFalse(c.passwordVisibleField.isVisible)
            c.passwordToggle.isSelected = true
            assertTrue(c.passwordVisibleField.isVisible)
            assertEquals("secret12", c.passwordVisibleField.text)
            assertEquals(2, c.licenseText.children.count { it is javafx.scene.control.Hyperlink }, "license + privacy links")
        }
    }

    @Test
    fun successReturnsToLoginWithUsername() {
        val nav = RecordingNavigator()
        val c = form(nav)
        FxTestSupport.onFx { c.fill(); c.onCreate() }
        assertTrue(FxTestSupport.waitFx { nav.calls.isNotEmpty() })
        assertEquals(listOf("registered:newbie"), nav.calls.toList())
    }

    @Test
    fun takenUsernameIsShownOnTheField() {
        val nav = RecordingNavigator()
        val c = form(nav)
        FxTestSupport.onFx { c.fill(user = "taken"); c.onCreate() }
        assertTrue(FxTestSupport.waitFx { !c.errorFirst.text.isNullOrEmpty() })
        FxTestSupport.onFx {
            assertEquals("Имя занято", c.errorFirst.text)
            assertTrue(c.isMarked(c.usernameField))
            assertFalse(c.root.isDisable, "form unlocked after the call")
            assertTrue(nav.calls.isEmpty())
            c.onBack()
            assertEquals(listOf("age:2000-07-26"), nav.calls, "back keeps the date for the age gate")
        }
    }

    // ---------- login panel entry / exit ----------

    @Test
    fun loginCreateLinkOpensAgeGateAndRegisteredPrefills() {
        val nav = RecordingNavigator()
        val c = FxTestSupport.onFx { Fxml.load<InitialAuthController>(recordingContext(nav), "auth_panel.fxml").controller }
        FxTestSupport.onFx {
            assertFalse(c.noticeLabel!!.isVisible)
            assertFalse(c.noticeLabel!!.isManaged)
            c.registerButton.fire()
            assertEquals(listOf("age:"), nav.calls)
            c.passwordField.text = "old"
            c.showRegistered("newbie")
            assertEquals("newbie", c.loginField.text)
            assertEquals("", c.passwordField.text)
            assertEquals("Аккаунт newbie создан. Введите пароль, чтобы войти.", c.noticeLabel!!.text)
            assertTrue(c.noticeLabel!!.isVisible && c.noticeLabel!!.isManaged)
            c.passwordField.text = "secret"
            assertEquals("", c.noticeLabel!!.text, "notice hides once the password is typed")
            assertFalse(c.noticeLabel!!.isVisible)
        }
    }

    @Test
    fun liveBackendOnRegisterShowsAgeGateAndDoesNotOpenRegister() {
        val nav = RecordingNavigator()
        val opened = mutableListOf<String>()
        val app = AppContext.mock(authDelayMs = 0, links = LinkOpener { opened += it })
        val ctx = UiContext(app.services, nav, Strings.bundle("ru"), "ru", app.clientStates, false, app.desk, true)
        val zone = FxTestSupport.onFx { Fxml.load<InitialAuthController>(ctx, "auth_panel.fxml").controller }
        val legacy = FxTestSupport.onFx { Fxml.load<InitialAuthController>(ctx, "initial_auth.fxml").controller }
        FxTestSupport.onFx {
            zone.registerButton.fire()
            legacy.onRegister()
            assertEquals(listOf("age:", "age:"), nav.calls.toList())
            assertTrue(opened.none { it == ExternalLinks.REGISTER }, "register stays in-app: $opened")
            zone.onRestore()
            assertEquals(listOf(ExternalLinks.RESTORE), opened.toList(), "password restore still opens in the browser")
        }
    }
}
