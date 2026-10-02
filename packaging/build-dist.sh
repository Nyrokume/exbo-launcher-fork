#!/usr/bin/env bash
# Builds all deliverables into dist/ from Linux:
#   LauncherFork_Setup_<ver>.exe      NSIS per-user installer (Windows x64, bundled jlink runtime)
#   LauncherFork_portable_win64.zip   the same Windows image, unpack-and-run
#   LauncherFork_linux_x64.tar.gz     jlink image for Linux x64
#   exbo-launcher-fork-src.zip        git archive of HEAD
#   SHA256SUMS.txt
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
MODULES="$JDK_MODULES,javafx.controls,javafx.fxml"
echo "   modules: $MODULES"
JLINK_OPTS=(--add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages --compress zip-6)

java "$ROOT/packaging/MakeIco.java" src/main/resources/dev/launcherfork/ui/style/image/icon.png "$WORK/$APP.ico"

readme() { # $1 = platform notes
    cat <<TXT
$APP $VERSION - visual-only fork of the EXBO launcher UI (mock data, no network).
INTERNAL USE ONLY: contains EXBO-owned images and the commercial "Europe" font. Do not redistribute.

$1
JVM/system options (e.g. -Dlauncher.lang=en, -Dlauncher.theme=shadcn, -Dlauncher.dev=true) can be passed as
command-line arguments of the launcher; they are given to the JVM.
TXT
}

echo "== Windows x64 image"
WIN=$WORK/win/$APP
jlink --module-path "$WIN_JDK/jmods:$WIN_JFX" "${JLINK_OPTS[@]}" --output "$WIN/runtime"
test -f "$WIN/runtime/bin/javaw.exe"
mkdir -p "$WIN/app" && cp "$LIB"/*.jar "$WIN/app/"
cp "$WORK/$APP.ico" "$WIN/"
makensis -V2 -DOUTFILE="$WIN/$APP.exe" -DICON="$WORK/$APP.ico" -DVERSION="$VERSION" -DVERSION_QUAD="$VERSION_QUAD" packaging/launcher.nsi
printf '@echo off\r\nrem Fallback launcher (same as LauncherFork.exe), no console window stays open.\r\nstart "" "%%~dp0runtime\\bin\\javaw.exe" -Dprism.lcdtext=false %%* -cp "%%~dp0app\\*" %s\r\n' "$MAIN" > "$WIN/$APP.cmd"
readme "Run LauncherFork.exe (or LauncherFork.cmd). The bundled Java runtime is in runtime\\." | sed 's/$/\r/' > "$WIN/README.txt"

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
makensis -V2 -DOUTFILE="$DIST/${APP}_Setup_${VERSION}.exe" -DSRCDIR="$WIN" -DUNINSTALL_LIST="$UNLIST" -DICON="$WORK/$APP.ico" \
    -DVERSION="$VERSION" -DVERSION_QUAD="$VERSION_QUAD" packaging/installer.nsi

if [[ -n "$LINUX_JFX" ]]; then
    echo "== Linux x64 image"
    LNX=$WORK/linux/$APP
    jlink --module-path "$HOST_JMODS:$LINUX_JFX" "${JLINK_OPTS[@]}" --output "$LNX/runtime"
    mkdir -p "$LNX/app" "$LNX/bin" && cp "$LIB"/*.jar "$LNX/app/"
    cat > "$LNX/bin/launcherfork" <<SH
#!/bin/sh
# Arguments are JVM options, e.g.: bin/launcherfork -Dlauncher.lang=en
DIR=\$(cd "\$(dirname "\$0")/.." && pwd)
exec "\$DIR/runtime/bin/java" -Dprism.lcdtext=false "\$@" -cp "\$DIR/app/*" $MAIN
SH
    chmod +x "$LNX/bin/launcherfork"
    readme "Run bin/launcherfork." > "$LNX/README.txt"
    tar -C "$WORK/linux" -czf "$DIST/${APP}_linux_x64.tar.gz" "$APP"
fi

echo "== source archive"
git archive --format=zip --prefix=exbo-launcher-fork/ -o "$DIST/exbo-launcher-fork-src.zip" HEAD

(cd "$DIST" && rm -f SHA256SUMS.txt && sha256sum -- *.exe *.zip *.tar.gz 2>/dev/null > SHA256SUMS.txt)
ls -l "$DIST"
