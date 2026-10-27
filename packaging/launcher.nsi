; Tiny GUI launcher: LauncherFork.exe starts the bundled runtime's javaw (no console window).
; Built by packaging/build-dist.sh:  makensis -DVERSION=... -DOUTFILE=... -DICON=... launcher.nsi
Target x86-unicode  ; 32-bit stub, runs on 64-bit Windows. The bundled runtime is 64-bit.
!include "FileFunc.nsh"

!ifndef ADD_MODULES
  !define ADD_MODULES "javafx.controls,javafx.fxml"
!endif

Name "EXBO Launcher"
OutFile "${OUTFILE}"
Icon "${ICON}"
SilentInstall silent
RequestExecutionLevel user
SetCompressor /SOLID lzma

VIProductVersion "${VERSION_QUAD}"
VIAddVersionKey /LANG=1049 "ProductName" "EXBO Launcher"
VIAddVersionKey /LANG=1049 "CompanyName" "EXBO"
VIAddVersionKey /LANG=1049 "FileDescription" "Лаунчер EXBO"
VIAddVersionKey /LANG=1049 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1049 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1049 "LegalCopyright" "EXBO"
VIAddVersionKey /LANG=1049 "Comments" "STALZONE"
VIAddVersionKey /LANG=1033 "ProductName" "EXBO Launcher"
VIAddVersionKey /LANG=1033 "CompanyName" "EXBO"
VIAddVersionKey /LANG=1033 "FileDescription" "EXBO Launcher"
VIAddVersionKey /LANG=1033 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "LegalCopyright" "EXBO"
VIAddVersionKey /LANG=1033 "Comments" "STALZONE"

Section
  ; command-line arguments are JVM options (e.g. -Dlauncher.lang=en), so they go before -cp
  ${GetParameters} $0
  SetOutPath "$EXEDIR"
  IfFileExists "$EXEDIR\runtime\bin\javaw.exe" +3
    MessageBox MB_ICONSTOP "Рядом с лаунчером нет runtime\bin\javaw.exe.$\r$\nruntime\bin\javaw.exe was not found next to the launcher."
    Abort
  Exec '"$EXEDIR\runtime\bin\javaw.exe" -Dprism.text=native -Dprism.lcdtext=true --add-modules ${ADD_MODULES} $0 -cp "$EXEDIR\app\*" dev.launcherfork.app.MainKt'
SectionEnd
