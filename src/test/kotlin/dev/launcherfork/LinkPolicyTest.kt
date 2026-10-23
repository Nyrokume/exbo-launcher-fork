package dev.launcherfork

import dev.launcherfork.domain.CommunityLinks
import dev.launcherfork.domain.ExternalLinks
import dev.launcherfork.domain.LinkPolicy
import dev.launcherfork.ui.components.BrandGlyphs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkPolicyTest {
    @Test
    fun communityLinksAreAllowed() {
        CommunityLinks.all.forEach { assertTrue(LinkPolicy.isAllowed(it.url), it.url) }
    }

    @Test
    fun xAndTwitterAreNotAllowed() {
        assertFalse("x.com" in LinkPolicy.allowedHosts)
        assertFalse("twitter.com" in LinkPolicy.allowedHosts)
        listOf("https://x.com/stalzone_game", "https://twitter.com/stalzone_game", "https://mobile.twitter.com/a")
            .forEach { assertFalse(LinkPolicy.isAllowed(it), it) }
    }

    @Test
    fun communityMenuHasNoX() {
        assertEquals(listOf("discord", "youtube", "reddit", "telegram"), CommunityLinks.all.map { it.id })
        assertTrue(CommunityLinks.all.none { "x.com" in it.url || "twitter" in it.url.lowercase() })
        assertNull(BrandGlyphs.path("x"))
    }

    @Test
    fun subdomainIsAllowed() {
        assertTrue(LinkPolicy.isAllowed("https://www.youtube.com/watch?v=abc"))
        assertTrue(LinkPolicy.isAllowed("https://stalzone.com"))
    }

    @Test
    fun unsafeInputIsRejected() {
        listOf(
            "file:///C:/Windows/System32/calc.exe", "http://stalzone.com", "https://evil.com",
            "https://stalzone.com.evil.com", "https://user@stalzone.com", "C:\\foo.exe",
            "javascript:alert(1)", "", "/relative/path", "https://notstalzone.com", "https://[bad",
        ).forEach { assertFalse(LinkPolicy.isAllowed(it), it) }
    }

    @Test
    fun exboRuRegistrationAndRestoreAreAllowed() {
        assertTrue("exbo.ru" in LinkPolicy.allowedHosts)
        listOf(ExternalLinks.REGISTER, ExternalLinks.RESTORE, "https://www.exbo.ru/index.php?do=register")
            .forEach { assertTrue(LinkPolicy.isAllowed(it), it) }
    }

    @Test
    fun exboGamesAndExboNetAreAllowed() {
        assertTrue("exbo.games" in LinkPolicy.allowedHosts)
        assertTrue("exbo.net" in LinkPolicy.allowedHosts)
        listOf(ExternalLinks.LICENSE, ExternalLinks.PRIVACY, "https://exbo.net/", "https://www.exbo.games/legal/x")
            .forEach { assertTrue(LinkPolicy.isAllowed(it), it) }
    }

    @Test
    fun externalLinksAreAllowed() {
        assertEquals("https://stalzone.net/referral", ExternalLinks.REFERRAL)
        assertTrue("stalzone.net" in LinkPolicy.allowedHosts)
        listOf(ExternalLinks.LICENSE, ExternalLinks.PRIVACY, ExternalLinks.ACCOUNT, ExternalLinks.REFERRAL)
            .forEach { assertTrue(LinkPolicy.isAllowed(it), it) }
        assertFalse(LinkPolicy.isAllowed("https://stalzone.net.evil.com/referral"))
    }

    @Test
    fun vkPatchnoteTopicsAreAllowed() {
        assertTrue("vk.ru" in LinkPolicy.allowedHosts)
        assertTrue("vk.com" in LinkPolicy.allowedHosts)
        listOf(
            "https://vk.ru/@stalcraft_official-patchnout-04022026-wg97538",
            "https://vk.com/@stalcraft_official-patchnout-04022026-wg97538",
            "https://m.vk.ru/@stalcraft_official",
        ).forEach { assertTrue(LinkPolicy.isAllowed(it), it) }
    }
}
