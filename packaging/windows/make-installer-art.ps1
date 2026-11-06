# Regenerates the Inno Setup wizard artwork from packaging\linux\icons\256.png (the app icon). Each picture is made at three sizes,
# which Inno Setup picks from by the screen's scaling, so it stays sharp on high-DPI screens:
#   wizard-large*.bmp   the banner on the Welcome and Finish pages (164x314 at 100%)
#   wizard-small*.bmp   the small logo in the header of the inner pages (55x55 at 100%)
# Run from the repo root:  powershell -File packaging\windows\make-installer-art.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Resolve-Path "$PSScriptRoot\..\.."
$logo = [System.Drawing.Image]::FromFile((Join-Path $root 'packaging\linux\icons\256.png'))

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    return $bmp, $g
}

function New-Font([double]$px, [System.Drawing.FontStyle]$style) {
    return New-Object System.Drawing.Font('Segoe UI', [single]$px, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

# ---- the banner: dark slate, a rose glow, the icon, the name ----------------------------------------------
foreach ($size in @(@(164, 314, ''), @(246, 459, '-150'), @(328, 604, '-200'))) {
    $w = $size[0]; $h = $size[1]; $s = $w / 164.0
    $bmp, $g = New-Canvas $w $h
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.Color]::FromArgb(43, 45, 48),   # slate at the top
        [System.Drawing.Color]::FromArgb(24, 25, 28),   # nearly black at the bottom
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
    $g.FillRectangle($brush, $rect)
    $brush.Dispose()

    # a soft glow behind the icon, so it sits in the picture rather than on it
    $glowSize = 190 * $s
    $glowRect = New-Object System.Drawing.RectangleF ((($w - $glowSize) / 2), (58 * $s), $glowSize, $glowSize)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($glowRect)
    $glow = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
    $glow.CenterColor = [System.Drawing.Color]::FromArgb(70, 255, 27, 107)
    $glow.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 255, 27, 107))
    $g.FillEllipse($glow, $glowRect)
    $glow.Dispose(); $path.Dispose()

    $icon = 92 * $s
    $g.DrawImage($logo, [single](($w - $icon) / 2), [single](84 * $s), [single]$icon, [single]$icon)

    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $name = New-Font (28 * $s) ([System.Drawing.FontStyle]::Bold)
    $g.DrawString('QueryExe', $name, [System.Drawing.Brushes]::White, (New-Object System.Drawing.RectangleF 0, (192 * $s), $w, (40 * $s)), $fmt)
    $tag = New-Font (12 * $s) ([System.Drawing.FontStyle]::Regular)
    $muted = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(160, 164, 172))
    $g.DrawString("A desktop`r`ndatabase client", $tag, $muted, (New-Object System.Drawing.RectangleF 0, (232 * $s), $w, (40 * $s)), $fmt)

    $g.Dispose()
    $bmp.Save((Join-Path $PSScriptRoot "wizard-large$($size[2]).bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bmp.Dispose()
}

# ---- the small logo: the inner pages have a white header ---------------------------------------------------
foreach ($size in @(@(55, 55, ''), @(83, 80, '-150'), @(110, 106, '-200'))) {
    $w = $size[0]; $h = $size[1]
    $bmp, $g = New-Canvas $w $h
    $g.Clear([System.Drawing.Color]::White)
    $edge = [Math]::Min($w, $h) - 6 * ($w / 55.0)
    $g.DrawImage($logo, [single](($w - $edge) / 2), [single](($h - $edge) / 2), [single]$edge, [single]$edge)
    $g.Dispose()
    $bmp.Save((Join-Path $PSScriptRoot "wizard-small$($size[2]).bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bmp.Dispose()
}

$logo.Dispose()
"wrote the wizard pictures in $PSScriptRoot"
