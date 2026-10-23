package dev.launcherfork.stalzone

import java.util.concurrent.TimeUnit

/**
 * Outbound Windows firewall rules, the same ones `server_block.rs` creates:
 * `STALZONE-SB-{TCP|UDP}-{ip-with-dashes}`, direction outbound, ports 29450–29460, action block.
 *
 * Applying them needs an administrator token. This process never relaunches itself elevated:
 * without that token the call fails and the launcher shows the error.
 */
class TunnelFirewall(
    private val elevated: () -> Boolean = ::windowsElevated,
    private val shell: (String) -> Result<String> = ::runPowershell,
) {
    fun apply(ips: List<String>): Result<String> {
        val unique = ServerBlock.uniqueValidIpv4(ips)
        if (unique.isEmpty()) return Result.failure(IllegalArgumentException("No valid IPv4 addresses to block"))
        if (!elevated()) return Result.failure(FirewallNotElevated())
        clearRules()
        val applied = shell(applyScript(unique))
        if (applied.isFailure) {
            clearRules()
            return Result.failure(applied.exceptionOrNull() ?: IllegalStateException("firewall"))
        }
        return Result.success("Blocked ${unique.size} IP address(es) on ports $PORT_RANGE")
    }

    fun clear(): Result<Int> {
        if (!elevated()) return Result.failure(FirewallNotElevated())
        return clearRules()
    }

    fun status(): FirewallStatus {
        val out = shell(STATUS_SCRIPT).getOrNull() ?: return FirewallStatus(false, 0)
        val count = out.lineSequence().mapNotNull { it.trim().toIntOrNull() }.lastOrNull() ?: 0
        return FirewallStatus(count > 0, count)
    }

    private fun clearRules(): Result<Int> {
        val out = shell(CLEAR_SCRIPT).getOrElse { return Result.failure(it) }
        val removed = out.lineSequence().mapNotNull { it.trim().toIntOrNull() }.lastOrNull()
            ?: return Result.failure(IllegalStateException(out.ifBlank { "unexpected output" }))
        return Result.success(removed)
    }

    companion object {
        const val PREFIX = "STALZONE-SB-"
        const val PORT_RANGE = "29450-29460"

        fun system(): TunnelFirewall = TunnelFirewall()

        fun ruleName(protocol: String, ip: String): String =
            "$PREFIX$protocol-${ip.trim().replace('.', '-')}"

        fun applyScript(ips: List<String>): String = ips.flatMap { ip ->
            listOf("TCP", "UDP").map { protocol ->
                val name = ruleName(protocol, ip)
                "New-NetFirewallRule -DisplayName '$name' -Name '$name' -Direction Outbound " +
                    "-RemoteAddress '$ip' -Action Block -Protocol $protocol -RemotePort $PORT_RANGE " +
                    "-Enabled True -Profile Any -ErrorAction Stop"
            }
        }.joinToString("; ")

        val CLEAR_SCRIPT = """
            ${'$'}removed = 0
            Get-NetFirewallRule -ErrorAction SilentlyContinue |
              Where-Object { ${'$'}_.DisplayName -like '$PREFIX*' } |
              ForEach-Object {
                Remove-NetFirewallRule -Name ${'$'}_.Name -ErrorAction SilentlyContinue
                if (-not (Get-NetFirewallRule -Name ${'$'}_.Name -ErrorAction SilentlyContinue)) { ${'$'}removed++ }
              }
            ${'$'}left = @(Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { ${'$'}_.DisplayName -like '$PREFIX*' }).Count
            if (${'$'}left -gt 0) { Write-Error "STALZONE-SB rules remain: ${'$'}left"; exit 1 }
            Write-Output ${'$'}removed
        """.trimIndent()

        val STATUS_SCRIPT =
            "${'$'}r = @(Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { ${'$'}_.DisplayName -like '$PREFIX*' -and ${'$'}_.Enabled -eq 'True' }); Write-Output ${'$'}r.Count"

        val ELEVATED_SCRIPT =
            "if (([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { 'yes' } else { 'no' }"
    }
}

class FirewallNotElevated : Exception("administrator")

data class FirewallStatus(val active: Boolean, val ruleCount: Int)

internal fun windowsElevated(): Boolean {
    val os = System.getProperty("os.name").orEmpty()
    if (!os.contains("Windows", ignoreCase = true)) return false
    val out = runPowershell(TunnelFirewall.ELEVATED_SCRIPT).getOrNull()?.trim().orEmpty()
    return out.lineSequence().any { it.trim().equals("yes", ignoreCase = true) }
}

internal fun runPowershell(script: String, timeoutSec: Long = 25): Result<String> {
    return try {
        val process = ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-NoLogo",
            "-NonInteractive",
            "-WindowStyle",
            "Hidden",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            script,
        ).start()
        val output = StringBuilder()
        val errors = StringBuilder()
        val outThread = Thread({ output.append(process.inputStream.bufferedReader().readText()) }, "tunnel-fw-out").apply { isDaemon = true }
        val errThread = Thread({ errors.append(process.errorStream.bufferedReader().readText()) }, "tunnel-fw-err").apply { isDaemon = true }
        outThread.start()
        errThread.start()
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return Result.failure(IllegalStateException("timeout"))
        }
        outThread.join(1_000)
        errThread.join(1_000)
        if (process.exitValue() != 0) {
            val detail = errors.toString().trim().ifEmpty { output.toString().trim() }.ifEmpty { "exit ${process.exitValue()}" }
            Result.failure(IllegalStateException(detail.lineSequence().first().take(160)))
        } else {
            Result.success(output.toString().trim())
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
