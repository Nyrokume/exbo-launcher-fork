package dev.launcherfork

import dev.launcherfork.data.exbo.isClientRuntimeProcess
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class InstalledGameLauncherTest {
    @Test
    fun runtimeBinMatchIgnoresCaseQuotesAndSlashes() {
        val bin = Path.of("C:/Games/stalcraft/win64/java/bin")
        assertTrue(isClientRuntimeProcess("C:\\Games\\stalcraft\\win64\\java\\bin\\stalzone.exe", bin))
        assertTrue(isClientRuntimeProcess("\"c:/games/stalcraft/win64/java/bin/java.exe\"", bin))
        assertTrue(isClientRuntimeProcess("C:\\Games\\stalcraft\\win64\\java\\bin\\ExensLauncher.exe", bin))
        assertFalse(isClientRuntimeProcess("C:\\Program Files\\Java\\bin\\java.exe", bin))
        assertFalse(isClientRuntimeProcess("C:\\Games\\stalcraft\\win64\\java\\bin-extra\\java.exe", bin))
        assertFalse(isClientRuntimeProcess(null, bin))
        assertFalse(isClientRuntimeProcess("   ", bin))
    }
}
