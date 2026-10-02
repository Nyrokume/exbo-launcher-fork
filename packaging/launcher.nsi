; Tiny GUI launcher: LauncherFork.exe starts the bundled runtime's javaw (no console window).
; Built by packaging/build-dist.sh:  makensis -DVERSION=... -DOUTFILE=... -DICON=... launcher.nsi
Target amd64-unicode  ; x64-only app; 64-bit stub (also lets it run without WoW64)
!include "FileFunc.nsh"

Name "LauncherFork"
OutFile "${OUTFILE}"
Icon "${ICON}"
SilentInstall silent
RequestExecutionLevel user
SetCompressor /SOLID lzma

VIProductVersion "${VERSION_QUAD}"
VIAddVersionKey "ProductName" "LauncherFork"
VIAddVersionKey "FileDescription" "LauncherFork (visual fork of the EXBO launcher UI)"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "Internal build. Contains EXBO assets."

Section
  ; command-line arguments are JVM options (e.g. -Dlauncher.lang=en), so they go before -cp
  ${GetParameters} $0
  SetOutPath "$EXEDIR"
  IfFileExists "$EXEDIR\runtime\bin\javaw.exe" +3
    MessageBox MB_ICONSTOP "runtime\bin\javaw.exe not found next to LauncherFork.exe"
    Abort
  Exec '"$EXEDIR\runtime\bin\javaw.exe" -Dprism.lcdtext=false $0 -cp "$EXEDIR\app\*" dev.launcherfork.app.MainKt'
SectionEnd
