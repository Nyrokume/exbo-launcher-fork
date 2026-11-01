package dev.launcherfork

import dev.launcherfork.data.exbo.exboTorrentPath
import dev.launcherfork.data.exbo.exboJoinPath
import dev.launcherfork.data.exbo.parseServerCatalog
import dev.launcherfork.data.exbo.sidebarIcon
import dev.launcherfork.data.exbo.exboMailCodePath
import dev.launcherfork.data.exbo.exboTotpPath
import dev.launcherfork.data.exbo.exboReverseAnswer
import dev.launcherfork.data.exbo.exboSecretPath
import dev.launcherfork.data.exbo.exboPasswordHashPath
import dev.launcherfork.data.exbo.exboPasswordMaterial
import dev.launcherfork.data.exbo.exboPasswordPath
import dev.launcherfork.data.exbo.exboPowPassHash
import dev.launcherfork.data.exbo.exboSessionPath
import dev.launcherfork.data.exbo.exboSha1Hex
import dev.launcherfork.data.exbo.parseAuthReply
import dev.launcherfork.data.exbo.parseServerList
import dev.launcherfork.domain.GameDescription
import dev.launcherfork.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.exists

class ExboProtocolTest {
    @Test
    fun sha1HexMatchesTheOfficialBigIntegerEncoding() {
        val hello = "hello".toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(hello)
        assertEquals(java.math.BigInteger(1, digest).toString(16), exboSha1Hex(hello))
        var n = 0
        while (n < 20_000) {
            val raw = java.security.MessageDigest.getInstance("SHA-1").digest(n.toString().toByteArray())
            if ((raw[0].toInt() and 0xff) < 0x10) {
                val hex = exboSha1Hex(n.toString().toByteArray())
                assertEquals(java.math.BigInteger(1, raw).toString(16), hex)
                assertTrue(hex.length < 40)
                return
            }
            n++
        }
        error("no sha1 with a leading zero in the search")
    }

    @Test
    fun passwordAndSessionPathsKeepTheOfficialParameterOrder() {
        val pass = exboPasswordPath("a b", "p&w", "abc", "ff", "id")
        assertTrue(pass.startsWith("/auth?state=true&login=a+b&pass=p%26w&bootstrap=abc&hwid=ff&id=id&protocol_version=3&start=true"))
        val hashed = exboPasswordHashPath("a b", "p&w", "\$2y\$10\$abc", "abc", "ff", "id")
        assertEquals(
            "/auth?state=true&login=a+b&pass=p%26w&passhash=%242y%2410%24abc&bootstrap=abc&hwid=ff&id=id&protocol_version=3",
            hashed,
        )
        val session = exboSessionPath("name", "sess", "abc", "ff", "id")
        assertEquals(
            "/auth?state=false&login=name&session=sess&bootstrap=abc&hwid=ff&protocol_version=3&id=id",
            session,
        )
    }

    @Test
    fun authReplyKeepsOnlyARealToken() {
        val reply = parseAuthReply("""{"type":"NEW_PLACE","token":null,"trueLogin":"player","session":null}""")
        assertEquals("NEW_PLACE", reply.type)
        assertEquals(null, reply.token)
        assertEquals("player", reply.trueLogin)
        val challenge = parseAuthReply("""{"type":"POW_BCRYPT","secretHash":"abc","token":null}""")
        assertEquals("POW_BCRYPT", challenge.type)
        assertEquals("abc", challenge.secretHash)
        val banned = parseAuthReply("""{"type":"BANNED","timeLeft":282,"token":null}""")
        assertEquals("BANNED", banned.type)
        assertEquals(282, banned.timeLeft)
    }

    @Test
    fun passwordMaterialIsTheDoubleMd5TheOfficialClientSends() {
        assertEquals("696d29e0940a4957748fe3fc9efd22a3", exboPasswordMaterial("password"))
    }

    @Test
    fun powHashUsesTheInstalledBcrypt() {
        val appData = System.getenv("APPDATA")
        org.junit.jupiter.api.Assumptions.assumeTrue(!appData.isNullOrBlank())
        val jar = java.nio.file.Path.of(appData, "EXBO", "launcher", "launcher.jar")
        org.junit.jupiter.api.Assumptions.assumeTrue(jar.exists())
        val hash = exboPowPassHash("password", "......................", jar)
        assertTrue(hash.startsWith("\$2y\$10\$"))
        assertEquals(hash, exboPowPassHash("password", "......................", jar))
    }

    @Test
    fun reverseAnswerFindsTheFiveDigitString() {
        val secret = exboSha1Hex("10000".toByteArray())
        assertEquals("10000", exboReverseAnswer(secret))
        assertEquals(
            "/auth?state=true&login=name&answer=10000&bootstrap=abc&hwid=ff&id=id&new_protocol=true&protocol_version=3",
            exboSecretPath("name", "10000", "abc", "ff", "id"),
        )
    }

    @Test
    fun secondFactorPathsMatchTheOfficialQueries() {
        assertEquals(
            "/auth?secret=338410&login=name&id=id&protocol_version=3",
            exboTotpPath("name", "338410", "id"),
        )
        assertEquals(
            "/auth?code=268419249&login=name&id=id&protocol_version=3&hwid=ff",
            exboMailCodePath("name", "268419249", "ff", "id"),
        )
    }

    @Test
    fun joinGamePathCarriesTokenLoginAndProtocol() {
        assertEquals("/joinGame?token=a&login=b&protocol_version=3", exboJoinPath("a", "b"))
    }

    @Test
    fun torrentPathCarriesIdTokenAndLogin() {
        assertEquals("/torrentData?id=stalcraft&token=a&login=b", exboTorrentPath("a", "b", "stalcraft"))
    }

    @Test
    fun serverListUnwrapsTheTokenedDataString() {
        val body = """
            {"success":true,"data":"[{\"id\":\"stalcraft\",\"caption\":\"STALCRAFT\",\"onlineCurrent\":42,\"onlineMax\":2000,\"totalSize\":18,\"desc\":[\"lead\",\"sub\",\"body\",\"end\"],\"icon\":\"https://example.com/icon.png\",\"background\":\"bg.jpg\",\"httpSeeds\":[\"https://cdn.example/art\"],\"exclusions\":[],\"pingTarget\":\"\",\"runCommand\":\"\",\"version\":\"1\",\"info\":{}}]","preventRelog":false}
        """.trim()
        val root = Files.createTempDirectory("exbo-servers")
        try {
            val servers = parseServerList(body, root)
            assertEquals(1, servers.size)
            val server = servers.single()
            assertEquals("stalcraft", server.id)
            assertEquals("STALCRAFT", server.caption)
            assertEquals(42, server.players)
            assertTrue(server.online)
            assertEquals(18L, server.clientSizeBytes)
            assertEquals("https://example.com/icon.png", server.iconUri.toString())
            assertEquals("https://cdn.example/art/bg.jpg", server.backgroundUri.toString())
            assertFalse(server.installed)
            assertFalse(root.resolve("runtime").resolve("stalcraft").exists())
            val catalog = parseServerCatalog(body, root)
            assertEquals(listOf("https://cdn.example/art"), catalog.httpSeeds["stalcraft"])
            assertEquals("1", catalog.versions["stalcraft"])
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun serverListKeepsEveryDownloadableClientAndItsOwnIcon() {
        val body = """
            {"success":true,"data":"[{\"id\":\"stalcraft\",\"caption\":\"STALZONE\",\"onlineCurrent\":15367,\"totalSize\":18,\"desc\":[\"lead\",\"sub\",\"body\",\"end\"],\"icon\":\"https://cdn.example/files?sig=zone\",\"background\":\"bg.jpg\",\"httpSeeds\":[\"https://cdn.example/art\"],\"version\":\"zone-1\",\"runCommand\":\"<java>/bin/game\"},{\"id\":\"stalcraft_ots\",\"caption\":\"STALZONE OTS\",\"onlineCurrent\":4497,\"totalSize\":17,\"desc\":[\"ots lead\",\"sub\",\"body\",\"end\"],\"icon\":\"https://cdn.example/files?sig=ots\",\"background\":\"launcherbg/stalcraft_pts.jpg\",\"httpSeeds\":[\"https://cdn.example/ots\"],\"version\":\"ots-1\",\"runCommand\":\"<java>/bin/ots\"}]"}
        """.trim()
        val root = Files.createTempDirectory("exbo-servers-all")
        try {
            val catalog = parseServerCatalog(body, root)
            assertEquals(listOf("stalcraft", "stalcraft_ots"), catalog.servers.map { it.id })
            assertEquals(listOf("STALZONE", "STALZONE OTS"), catalog.servers.map { it.caption })
            assertEquals(listOf(15367, 4497), catalog.servers.map { it.players })
            assertEquals("zone-1", catalog.versions["stalcraft"])
            assertEquals("ots-1", catalog.versions["stalcraft_ots"])
            assertEquals(listOf("https://cdn.example/art"), catalog.httpSeeds["stalcraft"])
            assertEquals(listOf("https://cdn.example/ots"), catalog.httpSeeds["stalcraft_ots"])
            assertEquals("<java>/bin/ots", catalog.runCommands["stalcraft_ots"])
            val zone = catalog.servers[0]
            val ots = catalog.servers[1]
            assertEquals(GameDescription.Keys("game.stalzone"), zone.description)
            assertEquals(GameDescription.Keys("game.stalzone.ots"), ots.description)
            val global = parseServerCatalog(
                """{"success":true,"data":"[{\"id\":\"stalcraft_global\",\"caption\":\"STALCRAFT\",\"desc\":[\"lead\"]}]"}""",
                root,
            ).servers.single()
            assertEquals(GameDescription.Keys("game.stalzone"), global.description)
            assertEquals("sig=zone", zone.iconUri.query)
            assertEquals("sig=ots", ots.iconUri.query)
            assertNotEquals(zone.iconUri, ots.iconUri)
            assertEquals(zone.iconUri, sidebarIcon(zone))
            assertEquals(ots.iconUri, sidebarIcon(ots))
            assertEquals(Theme.gameIconUri(), sidebarIcon(ots.copy(iconUri = java.net.URI("http://cdn.example/ots.png"))))
            val bare = zone.copy(iconUri = Theme::class.java.getResource("/dev/launcherfork/mock/servers/game-stalzone.png")!!.toURI())
            assertEquals(Theme.gameIconUri(), sidebarIcon(bare))
            assertEquals(bare.iconUri, sidebarIcon(ots.copy(iconUri = bare.iconUri)))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
