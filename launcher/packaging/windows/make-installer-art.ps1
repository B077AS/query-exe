# Regenerates the Inno Setup wizard artwork from src/main/resources/icon.png:
#   wizard-large.bmp  164x314  left banner on the Welcome/Finish pages
#   wizard-small.bmp   55x55   small logo in the header of the inner pages
# Run from the repo root:  powershell -File packaging\windows\make-installer-art.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root   = Resolve-Path "$PSScriptRoot\..\.."
$srcPng = Join-Path $root 'src\main\resources\icon.png'
$logo   = [System.Drawing.Image]::FromFile($srcPng)

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    return $bmp, $g
}

# ── Large banner: dark gradient (query-exe's own bg tokens), logo, wordmark ────
$bmp, $g = New-Canvas 164 314
$rect = New-Object System.Drawing.Rectangle 0, 0, 164, 314
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect,
    [System.Drawing.Color]::FromArgb(34, 36, 47),   # -color-bg-overlay #22242f
    [System.Drawing.Color]::FromArgb(24, 25, 32),   # -color-bg-inset   #181920
    [System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
$g.FillRectangle($brush, $rect)
$brush.Dispose()

$logoSize = 96
$g.DrawImage($logo, [int]((164 - $logoSize) / 2), 72, $logoSize, $logoSize)

$font = New-Object System.Drawing.Font('Segoe UI', 20, [System.Drawing.FontStyle]::Bold)
$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = [System.Drawing.StringAlignment]::Center
$white = [System.Drawing.Brushes]::White
$g.DrawString('QueryExe', $font, $white, (New-Object System.Drawing.RectangleF 0, 186, 164, 40), $fmt)

$font2 = New-Object System.Drawing.Font('Segoe UI', 9)
$muted = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(191, 191, 195)) # -color-fg-muted #bfbfc3
$g.DrawString('Database client', $font2, $muted, (New-Object System.Drawing.RectangleF 0, 228, 164, 24), $fmt)

$g.Dispose()
$bmp.Save((Join-Path $PSScriptRoot 'wizard-large.bmp'), [System.Drawing.Imaging.ImageFormat]::Bmp)
$bmp.Dispose()

# ── Small header logo on white (inner wizard pages have a white header) ────────
$bmp, $g = New-Canvas 55 55
$g.Clear([System.Drawing.Color]::White)
$g.DrawImage($logo, 3, 3, 49, 49)
$g.Dispose()
$bmp.Save((Join-Path $PSScriptRoot 'wizard-small.bmp'), [System.Drawing.Imaging.ImageFormat]::Bmp)
$bmp.Dispose()

$logo.Dispose()
"wrote wizard-large.bmp and wizard-small.bmp in $PSScriptRoot"
