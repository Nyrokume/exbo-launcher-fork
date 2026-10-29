; EXBO Launcher (STALZONE) per-user setup.
; The window is the JavaFX installer (dev.launcherfork.install.SetupWindowKt):
; folder, desktop shortcut, startup shortcut, then Next. This script extracts the
; payload, opens that window, and on Next runs the same exe silently to copy files.
; The game itself is downloaded later from inside the launcher.
;
; Fork-specific on purpose (do not "fix" these toward Program Files or HKLM):
;   - RequestExecutionLevel user, default %LOCALAPPDATA%\LauncherFork, no UAC.
;   - The final folder always ends with \LauncherFork.
;   - Registry is HKCU\Software\LauncherFork and the per-user uninstall key only.
;     HKCU\SOFTWARE\EXBO (EXBO_SESSION) and %APPDATA%\EXBO are never written or deleted.
;   - A same-named shortcut is replaced or removed only when its target is this install.
;   - Authenticode is not applied: this repo has no certificate (TODO).
;
; Integrity: every shipped file is SHA-256 checked (packaging/verify-payload.ps1)
; in the staging directory before the old tree is replaced, and again after copy.
; A mismatch, a missing file, or PowerShell/Get-FileHash failing aborts the install
; (exit 3). File size is not an integrity signal. CRCCheck force covers the exe.
;
; Build (defines): VERSION, VERSION_QUAD, OUTFILE, SRCDIR, UNINSTALL_LIST, COPY_LIST, ICON,
; MANIFEST (sha256 lines), ESTIMATED_KB. Wizard art and the checker scripts sit
; next to this file. makensis on Linux can compile the exe; the wizard runs on Windows.
;
; Not matched, because it is not in this repo or in the public install article
; and is not taken from a closed installer binary: official sidebar artwork,
; an installer EULA page, the official default directory, and the official
; publisher string inside that binary. Game desktop shortcuts are created from
; the launcher (separate EXBO help article), not by this setup program.
Target x86-unicode
Unicode true
CRCCheck force
ManifestDPIAware true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "nsDialogs.nsh"
!include "WinMessages.nsh"
!include "FileFunc.nsh"
!insertmacro GetParameters
!insertmacro GetOptions

!ifndef VERSION
  !error "VERSION is required"
!endif
!ifndef VERSION_QUAD
  !error "VERSION_QUAD is required"
!endif
!ifndef OUTFILE
  !error "OUTFILE is required"
!endif
!ifndef SRCDIR
  !error "SRCDIR is required"
!endif
!ifndef UNINSTALL_LIST
  !error "UNINSTALL_LIST is required"
!endif
!ifndef ICON
  !error "ICON is required"
!endif
!ifndef MANIFEST
  !error "MANIFEST is required"
!endif
!ifndef COPY_LIST
  !error "COPY_LIST is required"
!endif
!ifndef ESTIMATED_KB
  !error "ESTIMATED_KB is required"
!endif
!ifndef ADD_MODULES
  !error "ADD_MODULES is required"
!endif

!define APP "LauncherFork"
!define PRODUCT "EXBO Launcher"
!define PUBLISHER "EXBO"
!define START_FOLDER "EXBO Launcher"
!define LNK_APP "EXBO Launcher.lnk"
!define LNK_UNINST "Uninstall EXBO Launcher.lnk"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP}"
!define MARKER ".launcherfork-install"
; makensis chdirs to this script, so these names stay next to installer.nsi.
!define VERIFY_PS1 "verify-payload.ps1"
!define SHORTCUT_PS1 "shortcut-target.ps1"

Var DesktopShortcut
Var StartupShortcut
Var Payload
Var ProgressFile

Name "${PRODUCT} ${VERSION}"
Caption "$(CAPTION)"
UninstallCaption "$(UNCAPTION)"
BrandingText "${PUBLISHER}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\${APP}"
InstallDirRegKey HKCU "Software\${APP}" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${VERSION_QUAD}"
VIAddVersionKey /LANG=1049 "ProductName" "${PRODUCT}"
VIAddVersionKey /LANG=1049 "CompanyName" "${PUBLISHER}"
VIAddVersionKey /LANG=1049 "FileDescription" "Установка лаунчера EXBO"
VIAddVersionKey /LANG=1049 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1049 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1049 "LegalCopyright" "${PUBLISHER}"
VIAddVersionKey /LANG=1049 "Comments" "STALZONE"
VIAddVersionKey /LANG=1033 "ProductName" "${PRODUCT}"
VIAddVersionKey /LANG=1033 "CompanyName" "${PUBLISHER}"
VIAddVersionKey /LANG=1033 "FileDescription" "${PRODUCT} Setup"
VIAddVersionKey /LANG=1033 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "LegalCopyright" "${PUBLISHER}"
VIAddVersionKey /LANG=1033 "Comments" "STALZONE"

!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_ABORTWARNING_TEXT "$(ABORT_TEXT)"

!define MUI_UNCONFIRMPAGE_TEXT_TOP "$(UNCONFIRM_TOP)"
!define MUI_UNCONFIRMPAGE_TEXT_LOCATION "$(UNCONFIRM_LOC)"
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Russian"
!insertmacro MUI_LANGUAGE "English"

LangString CAPTION ${LANG_RUSSIAN} "Установка лаунчера «EXBO»"
LangString CAPTION ${LANG_ENGLISH} "${PRODUCT} Setup"
LangString UNCAPTION ${LANG_RUSSIAN} "Удаление лаунчера «EXBO»"
LangString UNCAPTION ${LANG_ENGLISH} "Uninstall ${PRODUCT}"
LangString ABORT_TEXT ${LANG_RUSSIAN} "Прервать установку лаунчера «EXBO»?"
LangString ABORT_TEXT ${LANG_ENGLISH} "Stop installing ${PRODUCT}?"
LangString WELCOME_TITLE ${LANG_RUSSIAN} "Установка лаунчера «EXBO»"
LangString WELCOME_TITLE ${LANG_ENGLISH} "${PRODUCT} Setup"
LangString WELCOME_TEXT ${LANG_RUSSIAN} "Этот мастер установит лаунчер «EXBO» для STALZONE.$\r$\n$\r$\nСначала выберите папку лаунчера и нажмите «Далее». Дождитесь окончания установки. Игра скачивается уже из лаунчера.$\r$\n$\r$\nНажмите «Далее»."
LangString WELCOME_TEXT ${LANG_ENGLISH} "This wizard installs ${PRODUCT} for STALZONE.$\r$\n$\r$\nChoose the launcher folder and click Next, then wait until installation finishes. The game is downloaded from the launcher.$\r$\n$\r$\nClick Next."
LangString DIR_TITLE ${LANG_RUSSIAN} "Папка лаунчера"
LangString DIR_TITLE ${LANG_ENGLISH} "Launcher folder"
LangString DIR_SUB ${LANG_RUSSIAN} "Выберите папку и нажмите «Далее»."
LangString DIR_SUB ${LANG_ENGLISH} "Choose a folder and click Next."
LangString DIR_TOP ${LANG_RUSSIAN} "Выберите папку, в которую будет установлен лаунчер, и нажмите «Далее».$\r$\n$\r$\nЕсли путь не заканчивается на \${APP}, мастер добавит эту папку. Лаунчер не ставится в каталог игры и не заменяет ExboLauncher."
LangString DIR_TOP ${LANG_ENGLISH} "Choose the folder where the launcher will be installed, then click Next.$\r$\n$\r$\nIf the path does not end with \${APP}, the wizard adds that folder. The launcher is not placed in the game directory and does not replace ExboLauncher."
LangString DIR_DEST ${LANG_RUSSIAN} "Папка установки"
LangString DIR_DEST ${LANG_ENGLISH} "Destination folder"
LangString DIR_BROWSE ${LANG_RUSSIAN} "Изменить папку"
LangString DIR_BROWSE ${LANG_ENGLISH} "Change Folder"
LangString DIR_ADJUSTED ${LANG_RUSSIAN} "Лаунчер будет установлен в:$\r$\n$\r$\n$INSTDIR$\r$\n$\r$\nПапка \${APP} добавлена автоматически, чтобы не задеть каталог игры."
LangString DIR_ADJUSTED ${LANG_ENGLISH} "The launcher will be installed in:$\r$\n$\r$\n$INSTDIR$\r$\n$\r$\nThe \${APP} folder was added so the game directory is left alone."
LangString TASKS_TITLE ${LANG_RUSSIAN} "Ярлыки"
LangString TASKS_TITLE ${LANG_ENGLISH} "Shortcuts"
LangString TASKS_SUB ${LANG_RUSSIAN} "Меню «Пуск» и рабочий стол."
LangString TASKS_SUB ${LANG_ENGLISH} "Start Menu and desktop."
LangString TASKS_LABEL ${LANG_RUSSIAN} "Ярлык лаунчера будет в меню «Пуск». Ярлык игры на рабочем столе по-прежнему создаётся из самого лаунчера."
LangString TASKS_LABEL ${LANG_ENGLISH} "The launcher shortcut is added to the Start Menu. A desktop shortcut for the game is still created from the launcher itself."
LangString TASKS_DESKTOP ${LANG_RUSSIAN} "Создать ярлык лаунчера на рабочем столе"
LangString TASKS_DESKTOP ${LANG_ENGLISH} "Create a desktop shortcut for the launcher"
LangString INST_TITLE ${LANG_RUSSIAN} "Установка лаунчера"
LangString INST_TITLE ${LANG_ENGLISH} "Installing the launcher"
LangString INST_SUB ${LANG_RUSSIAN} "Подождите, пока установка завершится. Это может занять некоторое время."
LangString INST_SUB ${LANG_ENGLISH} "Wait until installation finishes. This can take a while."
LangString FINISH_TITLE ${LANG_RUSSIAN} "Лаунчер установлен"
LangString FINISH_TITLE ${LANG_ENGLISH} "Launcher installed"
LangString FINISH_TEXT ${LANG_RUSSIAN} "Лаунчер «EXBO» установлен.$\r$\n$\r$\nНажмите «Готово». Игру можно скачать после входа в лаунчер."
LangString FINISH_TEXT ${LANG_ENGLISH} "${PRODUCT} is installed.$\r$\n$\r$\nClick Finish. You can download the game after you sign in."
LangString FINISH_RUN ${LANG_RUSSIAN} "Запустить лаунчер «EXBO»"
LangString FINISH_RUN ${LANG_ENGLISH} "Launch ${PRODUCT}"
LangString UNCONFIRM_TOP ${LANG_RUSSIAN} "Будет удалён лаунчер «EXBO» из папки ниже и его ярлыки.$\r$\n$\r$\nИгра STALZONE, каталог данных EXBO и сессия в реестре (EXBO_SESSION) не удаляются."
LangString UNCONFIRM_TOP ${LANG_ENGLISH} "This removes ${PRODUCT} from the folder below, and its shortcuts.$\r$\n$\r$\nThe STALZONE game, EXBO data folders, and the registry session (EXBO_SESSION) are not removed."
LangString UNCONFIRM_LOC ${LANG_RUSSIAN} "Папка лаунчера"
LangString UNCONFIRM_LOC ${LANG_ENGLISH} "Launcher folder"
LangString MSG_FOREIGN ${LANG_RUSSIAN} "В папке $INSTDIR уже есть runtime или app, но это не установка лаунчера «EXBO». Выберите другую папку."
LangString MSG_FOREIGN ${LANG_ENGLISH} "$INSTDIR already contains runtime or app, but it is not an ${PRODUCT} installation. Choose another folder."
LangString MSG_NOT_OURS ${LANG_RUSSIAN} "В $INSTDIR не найден лаунчер «EXBO». Удаление отменено."
LangString MSG_NOT_OURS ${LANG_ENGLISH} "No ${PRODUCT} installation was found in $INSTDIR. Uninstall cancelled."
LangString MSG_INTEGRITY ${LANG_RUSSIAN} "Проверка целостности файлов лаунчера не прошла (SHA-256). Установка остановлена. Повторите загрузку установщика."
LangString MSG_INTEGRITY ${LANG_ENGLISH} "The launcher files failed the SHA-256 integrity check. Setup stopped. Download the installer again."
LangString MSG_COPY ${LANG_RUSSIAN} "Не удалось скопировать файлы лаунчера. Установка остановлена."
LangString MSG_COPY ${LANG_ENGLISH} "The launcher files could not be copied. Setup stopped."
LangString DETAIL_DIR ${LANG_RUSSIAN} "Папка установки:"
LangString DETAIL_DIR ${LANG_ENGLISH} "Install folder:"
LangString DETAIL_VERIFY ${LANG_RUSSIAN} "Проверка SHA-256 файлов лаунчера..."
LangString DETAIL_VERIFY ${LANG_ENGLISH} "Checking SHA-256 of the launcher files..."
LangString SEC_NAME ${LANG_RUSSIAN} "Лаунчер «EXBO»"
LangString SEC_NAME ${LANG_ENGLISH} "${PRODUCT}"
LangString SHORTCUT_COMMENT ${LANG_RUSSIAN} "Лаунчер «EXBO» — STALZONE"
LangString SHORTCUT_COMMENT ${LANG_ENGLISH} "${PRODUCT} — STALZONE"
LangString SHORTCUT_KEPT ${LANG_RUSSIAN} "Ярлык «${PRODUCT}» ведёт в другую программу и не был заменён."
LangString SHORTCUT_KEPT ${LANG_ENGLISH} "The «${PRODUCT}» shortcut points at another program and was left in place."

Function .onInit
  SetShellVarContext current
  StrCpy $DesktopShortcut "1"
  StrCpy $StartupShortcut "1"
  StrCpy $Payload ""
  StrCpy $ProgressFile ""
  ReadEnvStr $Payload "LAUNCHERFORK_PAYLOAD"
  ReadEnvStr $ProgressFile "LAUNCHERFORK_PROGRESS"
  ClearErrors
  ${GetParameters} $0
  ${GetOptions} $0 "/DESKTOP=" $1
  ${IfNot} ${Errors}
    StrCpy $DesktopShortcut $1
  ${EndIf}
  ClearErrors
  ${GetOptions} $0 "/STARTUP=" $1
  ${IfNot} ${Errors}
    StrCpy $StartupShortcut $1
  ${EndIf}
  ClearErrors
  IfSilent done
  Call ShowCustomInstaller
  Quit
  done:
FunctionEnd

Function un.onInit
  SetShellVarContext current
FunctionEnd

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
  ${If} $0 != "\${APP}"
    StrCpy $INSTDIR "$INSTDIR\${APP}"
  ${EndIf}
  Pop $0
FunctionEnd

; One copy of the payload in the exe. A silent install that was given /PAYLOAD= reuses it.
Function ExtractPayload
  ${If} $Payload != ""
    Return
  ${EndIf}
  InitPluginsDir
  CreateDirectory "$PLUGINSDIR\payload"
  SetOutPath "$PLUGINSDIR\payload"
  File /r "${SRCDIR}\*.*"
  StrCpy $Payload "$PLUGINSDIR\payload"
FunctionEnd

; Opens the custom installer window. That window runs this exe again with /S to copy.
Function ShowCustomInstaller
  Call ExtractPayload
  ReadRegStr $1 HKCU "Software\${APP}" "InstallDir"
  ${If} $1 == ""
    StrCpy $1 "$LOCALAPPDATA\${APP}"
  ${EndIf}
  ExecWait '"$Payload\runtime\bin\javaw.exe" -Dprism.text=native -Dprism.lcdtext=true --add-modules ${ADD_MODULES} -cp "$Payload\app\*" dev.launcherfork.install.SetupWindowKt --dir "$1" --setup "$EXEPATH" --payload "$Payload"'
FunctionEnd

; In: $R8 = directory to check. Out: $R7 is "0" only when every manifest hash matches.
Function VerifyTree
  Push $R0
  Push $R1
  Push $R2
  Push $R8
  Push $R9
  StrCpy $R7 "2"
  StrCpy $R1 "PAYLOAD_ROOT"
  StrCpy $R2 $R8
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  ${If} $R0 S== "0"
    Goto verify_done
  ${EndIf}
  StrCpy $R1 "PAYLOAD_MANIFEST"
  StrCpy $R2 "$PLUGINSDIR\payload.sha256"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  ${If} $R0 S== "0"
    Goto verify_clear
  ${EndIf}
  StrCpy $R1 "PAYLOAD_PROGRESS"
  StrCpy $R2 $ProgressFile
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "$PLUGINSDIR\verify-payload.ps1"'
  Pop $R7
  Pop $R2
  ${If} $R7 S== "error"
  ${OrIf} $R7 S== ""
    StrCpy $R7 "2"
  ${EndIf}
  verify_clear:
  StrCpy $R1 "PAYLOAD_ROOT"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  StrCpy $R1 "PAYLOAD_MANIFEST"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  StrCpy $R1 "PAYLOAD_PROGRESS"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  verify_done:
  Pop $R9
  Pop $R8
  Pop $R2
  Pop $R1
  Pop $R0
FunctionEnd

; Appends the line on the stack to $ProgressFile (one byte per character, same as the verify script).
Function ProgressLine
  Exch $R9
  Push $R8
  ${If} $ProgressFile != ""
    ClearErrors
    FileOpen $R8 "$ProgressFile" a
    ${IfNot} ${Errors}
      FileSeek $R8 0 END
      FileWrite $R8 "$R9$\r$\n"
      FileClose $R8
    ${EndIf}
  ${EndIf}
  Pop $R8
  Pop $R9
FunctionEnd

; In: $R8 = .lnk path, $R9 = expected target. Out: $R7 as shortcut-target.ps1
; (0 ours, 1 someone else, 3 missing, anything else = do not touch).
Function ShortcutState
  Push $R0
  Push $R1
  Push $R2
  ${IfNot} ${FileExists} "$R8"
    StrCpy $R7 "3"
    Goto shortcut_done
  ${EndIf}
  Push $R8
  Push $R9
  StrCpy $R7 "2"
  StrCpy $R1 "LNK_PATH"
  StrCpy $R2 $R8
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  StrCpy $R1 "LNK_EXPECT"
  StrCpy $R2 $R9
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "$PLUGINSDIR\shortcut-target.ps1"'
  Pop $R7
  Pop $R2
  ${If} $R7 S== "error"
  ${OrIf} $R7 S== ""
    StrCpy $R7 "2"
  ${EndIf}
  StrCpy $R1 "LNK_PATH"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  StrCpy $R1 "LNK_EXPECT"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  Pop $R9
  Pop $R8
  shortcut_done:
  Pop $R2
  Pop $R1
  Pop $R0
FunctionEnd

; Create $R8 -> $R9 when the link is missing or already ours. Never replace a foreign target.
Function CreateOwnedShortcut
  Push $R7
  Call ShortcutState
  ${If} $R7 S== "0"
  ${OrIf} $R7 S== "3"
    SetOutPath "$INSTDIR"
    ClearErrors
    CreateShortcut "$R8" "$R9" "" "$INSTDIR\${APP}.ico" 0 SW_SHOWNORMAL "" "$(SHORTCUT_COMMENT)"
  ${Else}
    DetailPrint "$(SHORTCUT_KEPT) $R8"
  ${EndIf}
  Pop $R7
FunctionEnd

Function DeleteOwnedShortcut
  Push $R7
  Call ShortcutState
  ${If} $R7 S== "0"
    Delete "$R8"
  ${EndIf}
  Pop $R7
FunctionEnd

; Drops shipped files, our marker, and our HKCU keys. Does not touch SOFTWARE\EXBO.
Function RollbackPayload
  !include "${UNINSTALL_LIST}"
  Delete "$INSTDIR\${MARKER}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "${UNINST_KEY}"
  DeleteRegKey HKCU "Software\${APP}"
FunctionEnd

Section "$(SEC_NAME)"
  SetShellVarContext current
  InitPluginsDir
  Call EnsureAppSubdir
  DetailPrint "$(DETAIL_DIR) $INSTDIR"

  ${If} ${FileExists} "$INSTDIR\${MARKER}"
  ${ElseIf} ${FileExists} "$INSTDIR\runtime\*.*"
  ${OrIf} ${FileExists} "$INSTDIR\app\*.*"
    MessageBox MB_ICONSTOP "$(MSG_FOREIGN)" /SD IDOK
    SetErrorLevel 2
    Quit
  ${EndIf}

  SetOutPath "$PLUGINSDIR"
  File "/oname=payload.sha256" "${MANIFEST}"
  File "/oname=verify-payload.ps1" "${VERIFY_PS1}"
  File "/oname=shortcut-target.ps1" "${SHORTCUT_PS1}"
  Call ExtractPayload

  Push "p 1"
  Call ProgressLine
  DetailPrint "$(DETAIL_VERIFY)"
  StrCpy $R8 "$Payload"
  Call VerifyTree
  ${If} $R7 S!= "0"
    DetailPrint "SHA-256: $R7"
    MessageBox MB_ICONSTOP "$(MSG_INTEGRITY)" /SD IDOK
    SetErrorLevel 3
    Quit
  ${EndIf}

  ${If} ${FileExists} "$INSTDIR\${MARKER}"
    RMDir /r "$INSTDIR\runtime"
    RMDir /r "$INSTDIR\app"
  ${EndIf}

  CreateDirectory "$INSTDIR"
  Push "p 2"
  Call ProgressLine
  ClearErrors
  !include "${COPY_LIST}"
  ${If} ${Errors}
    Call RollbackPayload
    MessageBox MB_ICONSTOP "$(MSG_COPY)" /SD IDOK
    SetErrorLevel 3
    Quit
  ${EndIf}

  Push "p 3"
  Call ProgressLine
  DetailPrint "$(DETAIL_VERIFY)"
  StrCpy $R8 "$INSTDIR"
  Call VerifyTree
  ${If} $R7 S!= "0"
    DetailPrint "SHA-256: $R7"
    Call RollbackPayload
    MessageBox MB_ICONSTOP "$(MSG_INTEGRITY)" /SD IDOK
    SetErrorLevel 3
    Quit
  ${EndIf}

  SetOutPath "$INSTDIR"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  FileOpen $0 "$INSTDIR\${MARKER}" w
  FileWrite $0 "${PRODUCT} ${VERSION}$\r$\n"
  FileClose $0

  Push "p 4"
  Call ProgressLine
  CreateDirectory "$SMPROGRAMS\${START_FOLDER}"
  StrCpy $R8 "$SMPROGRAMS\${START_FOLDER}\${LNK_APP}"
  StrCpy $R9 "$INSTDIR\${APP}.exe"
  Call CreateOwnedShortcut
  StrCpy $R8 "$SMPROGRAMS\${START_FOLDER}\${LNK_UNINST}"
  StrCpy $R9 "$INSTDIR\Uninstall.exe"
  Call CreateOwnedShortcut
  ${If} $DesktopShortcut S== "1"
    StrCpy $R8 "$DESKTOP\${LNK_APP}"
    StrCpy $R9 "$INSTDIR\${APP}.exe"
    Call CreateOwnedShortcut
  ${Else}
    StrCpy $R8 "$DESKTOP\${LNK_APP}"
    StrCpy $R9 "$INSTDIR\${APP}.exe"
    Call DeleteOwnedShortcut
  ${EndIf}
  ${If} $StartupShortcut S== "1"
    StrCpy $R8 "$SMSTARTUP\${LNK_APP}"
    StrCpy $R9 "$INSTDIR\${APP}.exe"
    Call CreateOwnedShortcut
  ${Else}
    StrCpy $R8 "$SMSTARTUP\${LNK_APP}"
    StrCpy $R9 "$INSTDIR\${APP}.exe"
    Call DeleteOwnedShortcut
  ${EndIf}

  WriteRegStr HKCU "Software\${APP}" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "${PRODUCT}"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "${PUBLISHER}"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP}.ico"
  WriteRegStr HKCU "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "${UNINST_KEY}" "Comments" "STALZONE"
  WriteRegDWORD HKCU "${UNINST_KEY}" "EstimatedSize" ${ESTIMATED_KB}
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

  InitPluginsDir
  SetOutPath "$PLUGINSDIR"
  File "/oname=shortcut-target.ps1" "${SHORTCUT_PS1}"

  StrCpy $R8 "$DESKTOP\${LNK_APP}"
  StrCpy $R9 "$INSTDIR\${APP}.exe"
  Call un.DeleteOwnedShortcut
  StrCpy $R8 "$SMPROGRAMS\${START_FOLDER}\${LNK_APP}"
  StrCpy $R9 "$INSTDIR\${APP}.exe"
  Call un.DeleteOwnedShortcut
  StrCpy $R8 "$SMPROGRAMS\${START_FOLDER}\${LNK_UNINST}"
  StrCpy $R9 "$INSTDIR\Uninstall.exe"
  Call un.DeleteOwnedShortcut
  StrCpy $R8 "$SMSTARTUP\${LNK_APP}"
  StrCpy $R9 "$INSTDIR\${APP}.exe"
  Call un.DeleteOwnedShortcut
  RMDir "$SMPROGRAMS\${START_FOLDER}"

  ; exactly what the installer shipped; directories only if empty
  !include "${UNINSTALL_LIST}"
  Delete "$INSTDIR\${MARKER}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "${UNINST_KEY}"
  DeleteRegKey HKCU "Software\${APP}"
  ; SOFTWARE\EXBO and the game tree are intentionally not touched.
SectionEnd

Function un.ShortcutState
  Push $R0
  Push $R1
  Push $R2
  ${IfNot} ${FileExists} "$R8"
    StrCpy $R7 "3"
    Goto unshortcut_done
  ${EndIf}
  Push $R8
  Push $R9
  StrCpy $R7 "2"
  StrCpy $R1 "LNK_PATH"
  StrCpy $R2 $R8
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  StrCpy $R1 "LNK_EXPECT"
  StrCpy $R2 $R9
  System::Call 'kernel32::SetEnvironmentVariable(t R1, t R2) i.R0'
  nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "$PLUGINSDIR\shortcut-target.ps1"'
  Pop $R7
  Pop $R2
  ${If} $R7 S== "error"
  ${OrIf} $R7 S== ""
    StrCpy $R7 "2"
  ${EndIf}
  StrCpy $R1 "LNK_PATH"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  StrCpy $R1 "LNK_EXPECT"
  System::Call 'kernel32::SetEnvironmentVariable(t R1, p 0) i.R0'
  Pop $R9
  Pop $R8
  unshortcut_done:
  Pop $R2
  Pop $R1
  Pop $R0
FunctionEnd

Function un.DeleteOwnedShortcut
  Push $R7
  Call un.ShortcutState
  ${If} $R7 S== "0"
    Delete "$R8"
  ${EndIf}
  Pop $R7
FunctionEnd
