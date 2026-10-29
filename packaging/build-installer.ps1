# Builds the Windows x64 installer into dist\ on this machine.
#   LauncherFork_Setup_<ver>.exe      per-user NSIS wizard (bundled jlink runtime)
#   LauncherFork_portable_win64.zip   the same image: unpack and run LauncherFork.exe
#   SHA256SUMS.txt                    hashes of those two deliverables
#
# How to build (no administrator prompt):
#   1. Install Temurin JDK 21, the kind that ships jlink. The default -Jdk path is one Adoptium build.
#   2. From the repo root, in PowerShell:
#        powershell -ExecutionPolicy Bypass -File packaging\build-installer.ps1
#      Another JDK:
#        powershell -ExecutionPolicy Bypass -File packaging\build-installer.ps1 -Jdk "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
#   3. Collect dist\LauncherFork_Setup_<version>.exe and the portable zip.
#
# The script downloads OpenJFX 21 Windows jmods and NSIS 3.10 into build\packaging-cache
# (gitignored with build\). It does not install the launcher, and it does not replace or
# unregister the official ExboLauncher or write HKCU\SOFTWARE\EXBO.
#
# The setup wizard checks SHA-256 of every shipped file twice (staging, then the install folder)
# via Windows PowerShell Get-FileHash. A mismatch or a missing PowerShell aborts install.
# File size is not treated as integrity. Authenticode is a TODO: this repo has no certificate.
#
# Linux can compile the same exe with packaging/build-dist.sh (system makensis, WIN_JDK, WIN_JFX)
# but cannot click through the wizard. The wizard runs on Windows.
param(
    [string]$Jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Cache = Join-Path $Root "build\packaging-cache"
$Work = Join-Path $Root "build\dist-work"
$Dist = Join-Path $Root "dist"
$App = "LauncherFork"
$Main = "dev.launcherfork.app.MainKt"
$JfxVersion = "21.0.5"

if (-not (Test-Path (Join-Path $Jdk "bin\jlink.exe"))) {
    throw "JDK 21 with jlink not found: $Jdk"
}

New-Item -ItemType Directory -Force -Path $Cache, $Dist | Out-Null
if (Test-Path $Work) { Remove-Item -Recurse -Force $Work }
New-Item -ItemType Directory -Force -Path $Work | Out-Null

function Get-Zip($Url, $Dest) {
    if (Test-Path $Dest) { return }
    Write-Host "== download $Url"
    curl.exe -L --fail --retry 3 -o $Dest $Url
    if ($LASTEXITCODE -ne 0) { throw "download failed: $Url" }
}

$JfxZip = Join-Path $Cache "openjfx-$JfxVersion-windows-x64-jmods.zip"
$NsisZip = Join-Path $Cache "nsis-3.10.zip"
Get-Zip "https://download2.gluonhq.com/openjfx/$JfxVersion/openjfx-${JfxVersion}_windows-x64_bin-jmods.zip" $JfxZip
Get-Zip "https://sourceforge.net/projects/nsis/files/NSIS%203/3.10/nsis-3.10.zip/download" $NsisZip

$JfxDir = Join-Path $Cache "javafx-jmods-$JfxVersion"
if (-not (Test-Path (Join-Path $JfxDir "javafx.controls.jmod"))) {
    $extract = Join-Path $Cache "javafx-jmods-extract"
    if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
    Expand-Archive -Path $JfxZip -DestinationPath $extract -Force
    $found = Get-ChildItem $extract -Recurse -Filter "javafx.controls.jmod" | Select-Object -First 1
    if (-not $found) { throw "javafx.controls.jmod not in $JfxZip" }
    if (Test-Path $JfxDir) { Remove-Item -Recurse -Force $JfxDir }
    New-Item -ItemType Directory -Force -Path $JfxDir | Out-Null
    Copy-Item (Join-Path $found.DirectoryName "*.jmod") $JfxDir
}

$NsisDir = Join-Path $Cache "nsis-3.10"
$MakeNsis = Join-Path $NsisDir "makensis.exe"
if (-not (Test-Path $MakeNsis)) {
    Expand-Archive -Path $NsisZip -DestinationPath $Cache -Force
    $found = Get-ChildItem $Cache -Recurse -Filter "makensis.exe" | Select-Object -First 1
    if (-not $found) { throw "makensis.exe not in $NsisZip" }
    if ($found.DirectoryName -ne $NsisDir) {
        if (Test-Path $NsisDir) { Remove-Item -Recurse -Force $NsisDir }
        Move-Item $found.DirectoryName $NsisDir
    }
}

$versionLine = Select-String -Path (Join-Path $Root "build.gradle.kts") -Pattern '^version = "(.*)"' | Select-Object -First 1
if (-not $versionLine) { throw "version not found in build.gradle.kts" }
$Version = $versionLine.Matches[0].Groups[1].Value
if ($Version -like "*-SNAPSHOT") { throw "release version must not be a SNAPSHOT: $Version" }
$parts = $Version.Split(".")
while ($parts.Count -lt 4) { $parts += "0" }
$VersionQuad = ($parts[0..3] -join ".")

Write-Host "== app jars ($Version)"
$env:JAVA_HOME = $Jdk
$env:PATH = "$(Join-Path $Jdk 'bin');$env:PATH"
Push-Location $Root
try {
    & .\gradlew.bat stageAppJars --console=plain
    if ($LASTEXITCODE -ne 0) { throw "stageAppJars failed" }
} finally {
    Pop-Location
}

$Lib = Join-Path $Root "build\stage\lib"
$AppJar = Join-Path $Lib "exbo-launcher-fork-$Version.jar"
if (-not (Test-Path $AppJar)) { throw "missing $AppJar" }

$jdeps = & (Join-Path $Jdk "bin\jdeps.exe") --ignore-missing-deps --print-module-deps --multi-release 21 --class-path "$Lib\*" $AppJar
if ($LASTEXITCODE -ne 0) { throw "jdeps failed" }
$modules = [System.Collections.Generic.HashSet[string]]::new([string[]]($jdeps -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ }))
foreach ($extra in @("javafx.controls", "javafx.fxml", "java.management", "jdk.management", "jdk.crypto.ec")) {
    [void]$modules.Add($extra)
}
$moduleList = ($modules | Sort-Object) -join ","
Write-Host "   modules: $moduleList"

$Win = Join-Path $Work "win\$App"
Write-Host "== jlink runtime"
& (Join-Path $Jdk "bin\jlink.exe") `
    --module-path "$Jdk\jmods;$JfxDir" `
    --add-modules $moduleList `
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 `
    --output (Join-Path $Win "runtime")
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }
if (-not (Test-Path (Join-Path $Win "runtime\bin\javaw.exe"))) { throw "runtime is missing javaw.exe" }

New-Item -ItemType Directory -Force -Path (Join-Path $Win "app") | Out-Null
Copy-Item (Join-Path $Lib "*.jar") (Join-Path $Win "app")
$Ico = Join-Path $Root "docs\design\icon\icon.ico"
Copy-Item $Ico (Join-Path $Win "$App.ico")
Copy-Item $Ico (Join-Path $Work "$App.ico")

Write-Host "== launcher stub"
& $MakeNsis -V2 `
    "-DOUTFILE=$(Join-Path $Win "$App.exe")" `
    "-DICON=$(Join-Path $Work "$App.ico")" `
    "-DVERSION=$Version" `
    "-DVERSION_QUAD=$VersionQuad" `
    "-DADD_MODULES=$moduleList" `
    (Join-Path $Root "packaging\launcher.nsi")
if ($LASTEXITCODE -ne 0) { throw "launcher stub failed" }

$cmd = @"
@echo off
rem Fallback launcher (same as LauncherFork.exe). The console closes after start.
start "" "%~dp0runtime\bin\javaw.exe" -Dprism.text=native -Dprism.lcdtext=true --add-modules $moduleList %* -cp "%~dp0app\*" $Main
"@
Set-Content -Path (Join-Path $Win "$App.cmd") -Value $cmd -Encoding ascii

$readme = @"
EXBO Launcher $Version
STALZONE

Separate from ExboLauncher.exe. It signs in, updates and starts the game client already stored under %APPDATA%\EXBO.
The setup program installs per-user into %LOCALAPPDATA%\LauncherFork and does not ask for administrator rights.
Removing this launcher does not delete the game client or HKCU session values (EXBO_SESSION).

Run LauncherFork.exe (or LauncherFork.cmd). The bundled Java runtime is in runtime\.
JVM options (for example -Dlauncher.lang=en) can be passed on the command line and are given to the JVM.
"@
Set-Content -Path (Join-Path $Win "README.txt") -Value $readme -Encoding ascii

Write-Host "== payload SHA-256 (checked again by the installer; size is not enough)"
$manifest = Join-Path $Work "payload.sha256"
$shaLines = New-Object System.Collections.Generic.List[string]
$payloadBytes = [int64]0
foreach ($file in (Get-ChildItem $Win -Recurse -File | Sort-Object FullName)) {
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
    if ($hash -notmatch '^[0-9a-f]{64}$') { throw "sha256 failed for $($file.FullName)" }
    $rel = $file.FullName.Substring($Win.Length).TrimStart('\').Replace('/', '\')
    $shaLines.Add("$hash *$rel")
    $payloadBytes += $file.Length
}
if ($shaLines.Count -lt 1) { throw "payload is empty" }
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllLines($manifest, $shaLines, $utf8)
$estimatedKb = [int][Math]::Ceiling($payloadBytes / 1KB)
if ($estimatedKb -lt 1) { throw "payload size is empty" }
Write-Host "   $($shaLines.Count) files, $estimatedKb KB"

Write-Host "== per-file copy list (no wildcard copy)"
$copyList = Join-Path $Work "copy_payload.nsh"
$copyLines = New-Object System.Collections.Generic.List[string]
$payloadFiles = @(Get-ChildItem $Win -Recurse -File | Sort-Object FullName)
$payloadTotal = $payloadFiles.Count
$payloadIndex = 0
foreach ($file in $payloadFiles) {
    $payloadIndex++
    $rel = $file.FullName.Substring($Win.Length).TrimStart('\').Replace('/', '\').Replace('$', '$$')
    $dir = Split-Path $rel -Parent
    if ($dir) { $copyLines.Add("  CreateDirectory `"`$INSTDIR\$dir`"") }
    $copyLines.Add("  Push `"c $payloadIndex $payloadTotal $rel`"")
    $copyLines.Add("  Call ProgressLine")
    $copyLines.Add("  CopyFiles /SILENT `"`$Payload\$rel`" `"`$INSTDIR\$rel`"")
}
if ($copyLines.Count -lt 1) { throw "copy list is empty" }
Set-Content -Path $copyList -Value $copyLines -Encoding ascii

Write-Host "== uninstall list"
$unlist = Join-Path $Work "uninstall_files.nsh"
$lines = New-Object System.Collections.Generic.List[string]
Get-ChildItem $Win -Recurse -File | Sort-Object FullName | ForEach-Object {
    $rel = $_.FullName.Substring($Win.Length).TrimStart('\').Replace('/', '\').Replace('$', '$$')
    $lines.Add("  Delete `"`$INSTDIR\$rel`"")
}
Get-ChildItem $Win -Recurse -Directory |
    Sort-Object { $_.FullName.Split('\').Count } -Descending |
    ForEach-Object {
        $rel = $_.FullName.Substring($Win.Length).TrimStart('\').Replace('/', '\').Replace('$', '$$')
        $lines.Add("  RMDir `"`$INSTDIR\$rel`"")
    }
Set-Content -Path $unlist -Value $lines -Encoding ascii

Write-Host "== portable zip"
$zip = Join-Path $Dist "${App}_portable_win64.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $Win -DestinationPath $zip -CompressionLevel Optimal

Write-Host "== NSIS installer"
$setup = Join-Path $Dist "${App}_Setup_$Version.exe"
if (Test-Path $setup) { Remove-Item -Force $setup }
& $MakeNsis -V2 `
    "-DOUTFILE=$setup" `
    "-DSRCDIR=$Win" `
    "-DUNINSTALL_LIST=$unlist" `
    "-DCOPY_LIST=$copyList" `
    "-DICON=$(Join-Path $Work "$App.ico")" `
    "-DMANIFEST=$manifest" `
    "-DESTIMATED_KB=$estimatedKb" `
    "-DVERSION=$Version" `
    "-DVERSION_QUAD=$VersionQuad" `
    "-DADD_MODULES=$moduleList" `
    (Join-Path $Root "packaging\installer.nsi")
if ($LASTEXITCODE -ne 0) { throw "installer failed" }

Get-FileHash -Algorithm SHA256 $setup, $zip | ForEach-Object {
    "{0}  {1}" -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf)
} | Set-Content -Path (Join-Path $Dist "SHA256SUMS.txt") -Encoding ascii

Get-ChildItem $Dist | Select-Object Name, Length | Format-Table -AutoSize
Write-Host "installer: $setup"
