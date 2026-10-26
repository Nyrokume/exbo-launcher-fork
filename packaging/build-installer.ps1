# Builds the Windows x64 installer into dist\ on this machine.
#   LauncherFork_Setup_<ver>.exe      per-user NSIS installer (bundled jlink runtime)
#   LauncherFork_portable_win64.zip   the same image: unpack and run LauncherFork.exe
#
# Uses the Temurin 21 jmods already installed, and downloads OpenJFX 21 Windows jmods
# plus NSIS 3 into build\packaging-cache (gitignored with build\).
# Does not install into, replace, or unregister the official ExboLauncher.
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
$App $Version

Own program, separate from ExboLauncher. It signs in, updates and starts the game client already stored under %APPDATA%\EXBO. It does not replace ExboLauncher.exe.
Installed by the setup program into %LOCALAPPDATA%\LauncherFork. This folder is not the EXBO launcher directory.
INTERNAL USE ONLY: contains EXBO-owned images and the commercial Europe font. Do not redistribute.

Run LauncherFork.exe (or LauncherFork.cmd). The bundled Java runtime is in runtime\.
JVM options (for example -Dlauncher.lang=en) can be passed on the command line and are given to the JVM.
"@
Set-Content -Path (Join-Path $Win "README.txt") -Value $readme -Encoding ascii

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
    "-DICON=$(Join-Path $Work "$App.ico")" `
    "-DVERSION=$Version" `
    "-DVERSION_QUAD=$VersionQuad" `
    (Join-Path $Root "packaging\installer.nsi")
if ($LASTEXITCODE -ne 0) { throw "installer failed" }

Get-FileHash -Algorithm SHA256 $setup, $zip | ForEach-Object {
    "{0}  {1}" -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf)
} | Set-Content -Path (Join-Path $Dist "SHA256SUMS.txt") -Encoding ascii

Get-ChildItem $Dist | Select-Object Name, Length | Format-Table -AutoSize
Write-Host "installer: $setup"
