; LauncherFork per-user installer (no admin rights): %LOCALAPPDATA%\LauncherFork,
; Start menu + desktop shortcuts, uninstaller, entry in "Apps & features".
; Built by packaging/build-dist.sh:
;   makensis -DVERSION=... -DVERSION_QUAD=... -DSRCDIR=<image dir> -DUNINSTALL_LIST=<generated .nsh> \
;            -DOUTFILE=... -DICON=... installer.nsi
;
; Safety rules (the directory page lets the user pick any folder, e.g. D:\Projects):
;  - the final install dir always ends with \LauncherFork (appended if missing, also for silent /D=...);
;  - runtime\ and app\ are wiped recursively only on upgrade, i.e. when our marker file exists;
;  - an existing folder with runtime\ or app\ but without the marker is refused;
;  - the uninstaller deletes exactly the files/dirs the installer shipped (UNINSTALL_LIST) and removes
;    directories, including $INSTDIR, only if they are empty.
Target x86-unicode  ; 32-bit stub, runs on 64-bit Windows. The bundled runtime is 64-bit.
!include "MUI2.nsh"
!include "LogicLib.nsh"

!define APP "LauncherFork"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP}"
!define MARKER ".launcherfork-install"

Name "${APP} ${VERSION}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\${APP}"
InstallDirRegKey HKCU "Software\${APP}" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${VERSION_QUAD}"
VIAddVersionKey /LANG=1033 "ProductName" "${APP}"
VIAddVersionKey /LANG=1033 "FileDescription" "${APP} ${VERSION} Setup"
VIAddVersionKey /LANG=1033 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Internal build. Contains EXBO assets."

!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP}.exe"

!insertmacro MUI_PAGE_WELCOME
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE DirectoryLeave
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Russian"
!insertmacro MUI_LANGUAGE "English"

LangString MSG_FOREIGN ${LANG_RUSSIAN} "В папке $INSTDIR уже есть runtime или app, но это не установка ${APP}. Выберите другую папку."
LangString MSG_FOREIGN ${LANG_ENGLISH} "$INSTDIR already contains runtime or app, but it is not a ${APP} installation. Choose another folder."
LangString MSG_NOT_OURS ${LANG_RUSSIAN} "В $INSTDIR не найдена установка ${APP}. Удаление отменено."
LangString MSG_NOT_OURS ${LANG_ENGLISH} "No ${APP} installation found in $INSTDIR. Uninstall cancelled."

; $INSTDIR := $INSTDIR without trailing "\" + "\LauncherFork" unless it already ends with it (case-insensitive).
Function EnsureAppSubdir
  Push $0
  ${Do}
    StrCpy $0 $INSTDIR 1 -1
    ${If} $0 == "\"
    ${OrIf} $0 == "/"
      StrCpy $INSTDIR $INSTDIR -1
    ${Else}
      ${ExitDo}
    ${EndIf}
  ${Loop}
  StrLen $0 "\${APP}"
  StrCpy $0 $INSTDIR "" -$0
  ${If} $0 != "\${APP}"   ; StrCmp/== is case-insensitive
    StrCpy $INSTDIR "$INSTDIR\${APP}"
  ${EndIf}
  Pop $0
FunctionEnd

Function DirectoryLeave
  Call EnsureAppSubdir
FunctionEnd

Section "Install"
  SetShellVarContext current
  Call EnsureAppSubdir   ; silent installs (/S /D=...) skip the directory page

  ${If} ${FileExists} "$INSTDIR\${MARKER}"
    ; upgrade of our own installation: replace runtime/app cleanly
    RMDir /r "$INSTDIR\runtime"
    RMDir /r "$INSTDIR\app"
  ${ElseIf} ${FileExists} "$INSTDIR\runtime\*.*"
  ${OrIf} ${FileExists} "$INSTDIR\app\*.*"
    MessageBox MB_ICONSTOP "$(MSG_FOREIGN)" /SD IDOK
    SetErrorLevel 2
    Quit
  ${EndIf}

  SetOutPath "$INSTDIR"
  File /r "${SRCDIR}\*.*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  FileOpen $0 "$INSTDIR\${MARKER}" w
  FileWrite $0 "${APP} ${VERSION}$\r$\n"
  FileClose $0

  CreateDirectory "$SMPROGRAMS\${APP}"
  CreateShortcut "$SMPROGRAMS\${APP}\${APP}.lnk" "$INSTDIR\${APP}.exe" "" "$INSTDIR\${APP}.ico"
  CreateShortcut "$SMPROGRAMS\${APP}\Uninstall ${APP}.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortcut "$DESKTOP\${APP}.lnk" "$INSTDIR\${APP}.exe" "" "$INSTDIR\${APP}.ico"

  WriteRegStr HKCU "Software\${APP}" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "${APP}"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "launcher-fork (internal)"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP}.ico"
  WriteRegStr HKCU "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  ${IfNot} ${FileExists} "$INSTDIR\${MARKER}"
    MessageBox MB_ICONSTOP "$(MSG_NOT_OURS)" /SD IDOK
    SetErrorLevel 2
    Quit
  ${EndIf}

  Delete "$DESKTOP\${APP}.lnk"
  Delete "$SMPROGRAMS\${APP}\${APP}.lnk"
  Delete "$SMPROGRAMS\${APP}\Uninstall ${APP}.lnk"
  RMDir "$SMPROGRAMS\${APP}"

  ; exactly what the installer shipped; directories only if empty
  !include "${UNINSTALL_LIST}"
  Delete "$INSTDIR\${MARKER}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "${UNINST_KEY}"
  DeleteRegKey HKCU "Software\${APP}"
SectionEnd
