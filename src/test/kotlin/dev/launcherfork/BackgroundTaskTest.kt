package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.AuthService
import dev.launcherfork.domain.UserSession
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.MailAuthController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BackgroundTaskTest {
    @Test
    fun backgroundDeliversFailureInsteadOfDying() {
        val result = AtomicReference<Result<Int>>()
        val latch = CountDownLatch(1)
        background({ error("boom") }, ui = { it() }) { r: Result<Int> ->
            result.set(r)
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "onResult must be called even if the task throws")
        assertTrue(result.get().isFailure)
        assertEquals("boom", result.get().exceptionOrNull()?.message)
    }

    @Test
    fun backgroundDeliversSuccess() {
        val result = AtomicReference<Result<Int>>()
        val latch = CountDownLatch(1)
        background({ 42 }, ui = { it() }) { r -> result.set(r); latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(42, result.get().getOrThrow())
    }

    private object ThrowingAuth : AuthService {
        override val savedLogin: String? = null
        override val currentSession: UserSession? = null
        override fun login(login: String, password: String, remember: Boolean): AuthResult = throw java.io.IOException("connection reset")
        override fun confirmCode(code: String): AuthResult = throw IllegalStateException("boom")
        override fun logout() = Unit
    }

    private fun throwingContext() = AppContext.mock(authDelayMs = 0).let { app ->
        FxTestSupport.context(app = AppContext(app.services.copy(auth = ThrowingAuth)))
    }

    @Test
    fun loginExceptionUnlocksAuthFormAndShowsServerError() {
        val ctx = throwingContext()
        val c = FxTestSupport.onFx {
            Fxml.load<InitialAuthController>(ctx, "initial_auth.fxml").controller.apply {
                loginField.text = "player"
                passwordField.text = "secret"
                onEnter()
                assertTrue(root.isDisable, "form is locked while the call runs")
            }
        }
        assertTrue(FxTestSupport.waitFx { !c.root.isDisable }, "auth window stayed locked after login() threw")
        FxTestSupport.onFx {
            assertFalse(c.rollingBar.isVisible)
            assertEquals(ctx.t("auth.error.server_error"), c.errorFirst.text)
        }
    }

    @Test
    fun confirmCodeExceptionUnlocksMailForm() {
        val ctx = throwingContext()
        val c = FxTestSupport.onFx {
            Fxml.load<MailAuthController>(ctx, "mail_auth.fxml").controller.apply {
                codeField.text = "123456"
                enter()
            }
        }
        assertTrue(FxTestSupport.waitFx { !c.root.isDisable })
        FxTestSupport.onFx { assertEquals(ctx.t("auth.error.server_error"), c.errorFirst.text) }
    }

    @Test
    fun mailCodeFieldAcceptsAtMostSixDigits() {
        val c = FxTestSupport.onFx { Fxml.load<MailAuthController>(FxTestSupport.context(), "mail_auth.fxml").controller }
        FxTestSupport.onFx {
            c.codeField.text = ""
            c.codeField.appendText("123456789")
            assertTrue(c.codeField.text.length <= MailAuthController.CODE_LENGTH)
            c.codeField.text = ""
            "1234567".forEach { c.codeField.appendText(it.toString()) }
            assertEquals("123456", c.codeField.text)
            c.codeField.appendText("a")
            assertEquals("123456", c.codeField.text)
        }
    }
}
