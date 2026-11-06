#!/usr/bin/env bash
# Regenerates src/main/packaging/macos/QueryExe.icns from
# src/main/packaging/macos/icon-macos.svg (Apple icon grid, transparent margin).
# Run from anywhere on macOS:  scripts/make-icns.sh
# Uses only built-in macOS tools: AppKit (via `swift`) rasterizes the SVG with
# a transparent background (qlmanage would paint it on opaque white, which shows
# as a white square in the Dock), sips scales, iconutil assembles the .icns.
# The .icns is committed and used by the `macos` Maven profile (jpackage --icon).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
src_svg="$root/src/main/packaging/macos/icon-macos.svg"
out_icns="$root/src/main/packaging/macos/QueryExe.icns"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
master="$work/master.png"

swift - "$src_svg" "$master" <<'SWIFT'
import AppKit
let args = CommandLine.arguments
guard let image = NSImage(contentsOf: URL(fileURLWithPath: args[1])) else {
    FileHandle.standardError.write("cannot load \(args[1])\n".data(using: .utf8)!); exit(1)
}
let size = 1024
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
                           bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSColor.clear.set()
NSRect(x: 0, y: 0, width: size, height: size).fill()
image.draw(in: NSRect(x: 0, y: 0, width: size, height: size))
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[2]))
SWIFT

iconset="$work/QueryExe.iconset"
mkdir -p "$iconset"
for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$master" --out "$iconset/icon_${size}x${size}.png" >/dev/null
    double=$((size * 2))
    sips -z "$double" "$double" "$master" --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done

mkdir -p "$(dirname "$out_icns")"
iconutil -c icns "$iconset" -o "$out_icns"
echo "Wrote $out_icns"
