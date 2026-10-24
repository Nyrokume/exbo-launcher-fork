package dev.launcherfork

import dev.launcherfork.stalzone.MiniJson
import dev.launcherfork.stalzone.RoxyCatalog
import dev.launcherfork.stalzone.TunnelCatalogs
import dev.launcherfork.stalzone.TunnelRegion

/** Bundled catalogs stand in for the live HTTPS list. Tests never open a socket. */
object TunnelFixtures {
    fun catalog(region: TunnelRegion): RoxyCatalog = when (region) {
        TunnelRegion.RU -> TunnelCatalogs.parse(text("servers-ru.json"))
        TunnelRegion.NEA -> TunnelCatalogs.parse(NEA)
        else -> TunnelCatalogs.parse(regional(region))
    }

    private fun regional(region: TunnelRegion): String {
        val root = MiniJson.parse(text("servers.json")) as Map<*, *>
        val pools = (root["pools"] as List<*>).filter { (it as Map<*, *>)["region"] == region.name }
        val next = linkedMapOf<String, Any?>(
            "mode" to "roxy",
            "clientToTunnelRttWeight" to (root["clientToTunnelRttWeight"] ?: 0),
            "pools" to pools,
        )
        return MiniJson.write(next)
    }

    private fun text(name: String): String =
        TunnelFixtures::class.java.getResourceAsStream("/dev/launcherfork/stalzone/$name")!!
            .readBytes().toString(Charsets.UTF_8)

    private const val NEA = """
        {"mode":"roxy","clientToTunnelRttWeight":1,"pools":[
          {"name":"NEA1","tunnels":[
            {"name":"NEA1-1","address":"203.0.113.10:29450"},
            {"name":"NEA1-2","address":"203.0.113.11:29450"}
          ]}
        ]}
    """
}
