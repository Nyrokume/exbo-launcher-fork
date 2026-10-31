#!/usr/bin/env bash
# Builds all deliverables into dist/ from Linux:
#   LauncherFork_Setup_<ver>.exe      NSIS per-user installer (Windows x64, bundled jlink runtime)
#   LauncherFork_portable_win64.zip   the same Windows image, unpack-and-run
#   LauncherFork_linux_x64.tar.gz     jlink image for Linux x64
#   exbo-launcher-fork-src.zip        git archive of HEAD
#   SHA256SUMS.txt
#
# Linux compiles the setup (makensis). It does not run that window: payload SHA-256 is checked on
# Windows by PowerShell Get-FileHash when the setup actually installs. There is no Authenticode certificate
# in this repo. On Windows use packaging/build-installer.ps1 (it downloads NSIS and the Windows jmods).
#
# Needs: JDK 21 (host jlink/jdeps + host jmods), makensis, zip, and these downloads (any 21.0.x, same feature
# release as the host jlink):
#   WIN_JDK   = Temurin JDK 21 windows-x64 unpacked (contains jmods/)
#   WIN_JFX   = openjfx-21.x_windows-x64_bin-jmods unpacked dir with *.jmod
#   LINUX_JFX = openjfx-21.x_linux-x64_bin-jmods unpacked dir with *.jmod
#   HOST_JMODS (optional) = host JDK jmods dir (default: $JAVA_HOME/jmods of `java`)
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
: "${WIN_JDK:?set WIN_JDK}" "${WIN_JFX:?set WIN_JFX}" "${LINUX_JFX:=}"
HOST_JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
HOST_JMODS=${HOST_JMODS:-$HOST_JAVA_HOME/jmods}

VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' build.gradle.kts)
case "$VERSION" in *-SNAPSHOT) echo "release version must not be a SNAPSHOT: $VERSION" >&2; exit 1;; esac
VERSION_QUAD="$VERSION$(printf '.0%.0s' $(seq $((4 - $(tr -cd . <<<"$VERSION" | wc -c) - 1))))"
APP=LauncherFork
MAIN=dev.launcherfork.app.MainKt
WORK=$ROOT/build/dist-work
DIST=$ROOT/dist
rm -rf "$WORK" && mkdir -p "$WORK" "$DIST"

echo "== app jars ($VERSION)"
./gradlew -q stageAppJars
LIB=$ROOT/build/stage/lib
APP_JAR=$LIB/exbo-launcher-fork-$VERSION.jar
test -f "$APP_JAR" || { echo "missing $APP_JAR" >&2; exit 1; }
JDK_MODULES=$(jdeps --ignore-missing-deps --print-module-deps --multi-release 21 --class-path "$LIB/*" "$APP_JAR")
MODULES="$JDK_MODULES,javafx.controls,javafx.fxml,java.management,jdk.management,jdk.crypto.ec"
echo "   modules: $MODULES"
JLINK_OPTS=(--add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages --compress zip-6)

# Prebuilt multi-size icon (16..256, pixel-snapped renders from docs/design/icon/gen.py), not an upscale of the
# 54 px icon.png.
ICO_SRC=$ROOT/docs/design/icon/icon.ico
test -f "$ICO_SRC" || { echo "missing $ICO_SRC" >&2; exit 1; }
cp "$ICO_SRC" "$WORK/$APP.ico"

readme() { # $1 = platform notes
    cat <<TXT
$APP $VERSION

EXBO Launcher for STALZONE. Separate from ExboLauncher.exe: it signs in, updates and starts the game client already stored under %APPDATA%\EXBO (RU) or %APPDATA%\Exbo_East (EU). Removing this program does not delete that client or HKCU session values (EXBO_SESSION).
The Windows setup is per-user (%LOCALAPPDATA%\LauncherFork) and does not ask for administrator rights.

$1
JVM/system options (e.g. -Dlauncher.lang=en, -Dlauncher.theme=exbo, -Dlauncher.dev=true) can be passed as
command-line arguments of the launcher; they are given to the JVM.
TXT
}

echo "== Windows x64 image"
WIN=$WORK/win/$APP
jlink --module-path "$WIN_JDK/jmods:$WIN_JFX" "${JLINK_OPTS[@]}" --output "$WIN/runtime"
test -f "$WIN/runtime/bin/javaw.exe"
mkdir -p "$WIN/app" && cp "$LIB"/*.jar "$WIN/app/"
cp "$WORK/$APP.ico" "$WIN/"
makensis -V2 -DOUTFILE="$WIN/$APP.exe" -DICON="$WORK/$APP.ico" -DVERSION="$VERSION" -DVERSION_QUAD="$VERSION_QUAD" -DADD_MODULES="$MODULES" packaging/launcher.nsi
printf '@echo off\r\nrem Fallback launcher (same as LauncherFork.exe), no console window stays open.\r\nstart "" "%%~dp0runtime\\bin\\javaw.exe" -Dprism.text=native -Dprism.lcdtext=true --enable-native-access=ALL-UNNAMED --add-modules %s %%* -cp "%%~dp0app\\*" %s\r\n' "$MODULES" "$MAIN" > "$WIN/$APP.cmd"
readme "Run LauncherFork.exe (or LauncherFork.cmd). The bundled Java runtime is in runtime\\." | sed 's/$/\r/' > "$WIN/README.txt"

echo "== payload SHA-256 (checked again by the installer; size is not enough)"
MANIFEST=$WORK/payload.sha256
bytes=0
count=0
: > "$MANIFEST"
while IFS= read -r f; do
    hash=$(sha256sum -- "$WIN/$f" | awk '{print $1}')
    if [[ ! "$hash" =~ ^[0-9a-f]{64}$ ]]; then
        echo "sha256 failed for $f" >&2
        exit 1
    fi
    win=$(printf '%s' "$f" | sed 's|/|\\|g')
    printf '%s *%s\n' "$hash" "$win" >> "$MANIFEST"
    bytes=$((bytes + $(wc -c < "$WIN/$f")))
    count=$((count + 1))
done < <(cd "$WIN" && find . -type f -print | sed 's|^\./||' | sort)
test "$count" -gt 0 || { echo "payload is empty" >&2; exit 1; }
test "$(grep -c '^[0-9a-f]\{64\} \*' "$MANIFEST")" = "$count"
ESTIMATED_KB=$(( (bytes + 1023) / 1024 ))
echo "   $count files, ${ESTIMATED_KB} KB"

echo "== per-file copy list (no wildcard copy)"
COPY_LIST=$WORK/copy_payload.nsh
(
    cd "$WIN"
    index=0
    find . -type f -print | sed 's|^\./||' | sort | while IFS= read -r f; do
        index=$((index + 1))
        win=$(printf '%s' "$f" | sed 's|/|\\|g; s|\$|$$|g')
        dir=$(dirname "$f")
        if [[ "$dir" != "." ]]; then
            windir=$(printf '%s' "$dir" | sed 's|/|\\|g; s|\$|$$|g')
            printf '  CreateDirectory "$INSTDIR\\%s"\n' "$windir"
        fi
        printf '  Push "c %s %s %s"\n' "$index" "$count" "$win"
        printf '  Call ProgressLine\n'
        printf '  CopyFiles /SILENT "$Payload\\%s" "$INSTDIR\\%s"\n' "$win" "$win"
    done
) > "$COPY_LIST"
test "$(grep -c CopyFiles "$COPY_LIST")" = "$count"

echo "== uninstall list (exactly the files/dirs the installer ships)"
# Paths relative to the image, Windows separators, '$' escaped for NSIS. Dirs deepest first; RMDir (non-recursive)
# only removes a dir that is empty, so anything the user added there survives.
UNLIST=$WORK/uninstall_files.nsh
(
    cd "$WIN"
    find . -type f | sed 's|^\./||' | sort | while IFS= read -r f; do
        printf '  Delete "$INSTDIR\\%s"\n' "$(sed 's|/|\\|g; s|\$|$$|g' <<<"$f")"
    done
    find . -mindepth 1 -type d | sed 's|^\./||' | awk -F/ '{print NF "\t" $0}' | sort -k1,1nr -k2 | cut -f2- | while IFS= read -r d; do
        printf '  RMDir "$INSTDIR\\%s"\n' "$(sed 's|/|\\|g; s|\$|$$|g' <<<"$d")"
    done
) > "$UNLIST"
echo "   $(grep -c Delete "$UNLIST") files, $(grep -c RMDir "$UNLIST") dirs"

echo "== portable zip"
rm -f "$DIST/${APP}_portable_win64.zip"
(cd "$WORK/win" && zip -qr -9 "$DIST/${APP}_portable_win64.zip" "$APP")

echo "== NSIS installer"
makensis -V2 -DOUTFILE="$DIST/${APP}_Setup_${VERSION}.exe" -DSRCDIR="$WIN" -DUNINSTALL_LIST="$UNLIST" -DCOPY_LIST="$COPY_LIST" -DICON="$WORK/$APP.ico" \
    -DMANIFEST="$MANIFEST" -DESTIMATED_KB="$ESTIMATED_KB" \
    -DVERSION="$VERSION" -DVERSION_QUAD="$VERSION_QUAD" -DADD_MODULES="$MODULES" packaging/installer.nsi

if [[ -n "$LINUX_JFX" ]]; then
    echo "== Linux x64 image"
    LNX=$WORK/linux/$APP
    jlink --module-path "$HOST_JMODS:$LINUX_JFX" "${JLINK_OPTS[@]}" --output "$LNX/runtime"
    mkdir -p "$LNX/app" "$LNX/bin" && cp "$LIB"/*.jar "$LNX/app/"
    cat > "$LNX/bin/launcherfork" <<SH
#!/bin/sh
# Arguments are JVM options, e.g.: bin/launcherfork -Dlauncher.lang=en
DIR=\$(cd "\$(dirname "\$0")/.." && pwd)
exec "\$DIR/runtime/bin/java" -Dprism.lcdtext=false --enable-native-access=ALL-UNNAMED "\$@" -cp "\$DIR/app/*" $MAIN
SH
    chmod +x "$LNX/bin/launcherfork"
    readme "Run bin/launcherfork." > "$LNX/README.txt"
    tar -C "$WORK/linux" -czf "$DIST/${APP}_linux_x64.tar.gz" "$APP"
fi

echo "== source archive"
git archive --format=zip --prefix=exbo-launcher-fork/ -o "$DIST/exbo-launcher-fork-src.zip" HEAD

(cd "$DIST" && rm -f SHA256SUMS.txt && sha256sum -- *.exe *.zip *.tar.gz 2>/dev/null > SHA256SUMS.txt)
ls -l "$DIST"
