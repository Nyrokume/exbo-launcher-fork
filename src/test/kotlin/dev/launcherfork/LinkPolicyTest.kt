package dev.launcherfork

import dev.launcherfork.domain.CommunityLinks
import dev.launcherfork.domain.LinkPolicy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkPolicyTest {
    @Test
    fun communityLinksAreAllowed() {
        CommunityLinks.all.forEach { assertTrue(LinkPolicy.isAllowed(it.url), it.url) }
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
}
