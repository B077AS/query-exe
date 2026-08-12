# Regenerates src/main/resources/icon.ico from src/main/resources/icon.png.
# Run from the repo root:  powershell -File scripts\make-ico.ps1
# The ICO embeds PNG-compressed images (supported since Vista) at the standard
# shell sizes, so the icon stays crisp at every DPI. Mirrors
# query-exe-launcher/packaging/windows/make-ico.ps1 (the .ico actually used for
# the shipped exe/installer icon — this repo's copy isn't wired into the build,
# kept only for parity/future use).

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root    = Resolve-Path "$PSScriptRoot\.."
$srcPng  = Join-Path $root 'src\main\resources\icon.png'
$outIco  = Join-Path $root 'src\main\resources\icon.ico'
$sizes   = 16, 24, 32, 48, 64, 128

$source = [System.Drawing.Image]::FromFile($srcPng)
try {
    $pngBlobs = foreach ($size in $sizes) {
        $bmp = New-Object System.Drawing.Bitmap $size, $size
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.DrawImage($source, 0, 0, $size, $size)
        $g.Dispose()
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        , $ms.ToArray()
    }
} finally {
    $source.Dispose()
}

# ICO layout: ICONDIR (6 bytes) + N * ICONDIRENTRY (16 bytes) + image data.
$stream = [System.IO.File]::Create($outIco)
$writer = New-Object System.IO.BinaryWriter $stream
try {
    $writer.Write([uint16]0)              # reserved
    $writer.Write([uint16]1)              # type: icon
    $writer.Write([uint16]$sizes.Count)   # image count

    $offset = 6 + 16 * $sizes.Count
    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $writer.Write([byte]($sizes[$i] -band 0xFF))  # width (0 = 256)
        $writer.Write([byte]($sizes[$i] -band 0xFF))  # height
        $writer.Write([byte]0)                        # palette colors
        $writer.Write([byte]0)                        # reserved
        $writer.Write([uint16]1)                      # color planes
        $writer.Write([uint16]32)                     # bits per pixel
        $writer.Write([uint32]$pngBlobs[$i].Length)   # data size
        $writer.Write([uint32]$offset)                # data offset
        $offset += $pngBlobs[$i].Length
    }
    foreach ($blob in $pngBlobs) { $writer.Write($blob) }
} finally {
    $writer.Dispose()
}

"wrote $outIco ($((Get-Item $outIco).Length) bytes, sizes: $($sizes -join ', '))"
