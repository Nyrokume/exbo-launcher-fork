package dev.launcherfork

import dev.launcherfork.stalzone.RoxyCatalog
import dev.launcherfork.stalzone.TunnelCatalogs
import dev.launcherfork.stalzone.TunnelRegion

/** The same classpath snapshot [TunnelCatalogs.bundled] serves to the mock launcher. Tests never open a socket. */
object TunnelFixtures {
    fun catalog(region: TunnelRegion): RoxyCatalog =
        requireNotNull(TunnelCatalogs.bundled(region)) { "fixture ${region.id}" }
}
