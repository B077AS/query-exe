#!/usr/bin/env bash
# Wraps a jpackage app-image into a QueryExe AppImage.
#
# Usage: make-appimage.sh <app-image-dir> <output-dir> <version> [appimagetool]
#
# Takes the jpackage output (launcher binary + private jlink runtime + staged
# app files), adds the AppImage metadata (AppRun, .desktop, icon), and runs
# appimagetool over the resulting AppDir. Called by the `appimage` Maven
# profile; the Linux counterpart of packaging/windows/query-exe.iss.
set -euo pipefail

APP_IMAGE_DIR="$1"
OUTPUT_DIR="$2"
VERSION="$3"
APPIMAGETOOL="${4:-appimagetool}"

HERE="$(cd "$(dirname "$0")" && pwd)"

if ! command -v "$APPIMAGETOOL" >/dev/null 2>&1; then
    echo "appimagetool not found: $APPIMAGETOOL" >&2
    echo "Install it (or pass -Dappimagetool.path=...):" >&2
    echo "  curl -Lo ~/.local/bin/appimagetool https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" >&2
    echo "  chmod +x ~/.local/bin/appimagetool" >&2
    exit 1
fi

APPDIR="$OUTPUT_DIR/QueryExe.AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR"
cp -a "$APP_IMAGE_DIR/." "$APPDIR/"

install -m 755 "$HERE/AppRun" "$APPDIR/AppRun"
cp "$HERE/query-exe.desktop" "$APPDIR/query-exe.desktop"
# Same icon the client ships; kept in sync via src/main/resources/icon.png.
cp "$HERE/../../src/main/resources/icon.png" "$APPDIR/query-exe.png"
ln -sf query-exe.png "$APPDIR/.DirIcon"

export ARCH="${ARCH:-$(uname -m)}"
# Lets the appimagetool AppImage run on hosts without FUSE; harmless otherwise.
export APPIMAGE_EXTRACT_AND_RUN=1

OUT="$OUTPUT_DIR/QueryExe-$VERSION-$ARCH.AppImage"
"$APPIMAGETOOL" "$APPDIR" "$OUT"
echo "AppImage written to $OUT"
