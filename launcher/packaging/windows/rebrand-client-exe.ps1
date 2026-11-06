# Copies the app-image's runtime\bin\javaw.exe to QueryExe.exe and rebrands its
# version resource with rcedit, so the client process shows as "QueryExe" in Task
# Manager instead of javaw.exe's own "Java Platform SE binary". UpdateManager
# prefers this renamed binary over javaw.exe when launching the client.
#
# Run by the installer Maven profile after jpackage-app-image, before Inno Setup.

param(
    [Parameter(Mandatory = $true)][string]$AppImageDir,
    [Parameter(Mandatory = $true)][string]$RceditPath,
    [Parameter(Mandatory = $true)][string]$IconPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $RceditPath)) {
    "rcedit not found at $RceditPath, downloading it..."
    New-Item -ItemType Directory -Force -Path (Split-Path $RceditPath) | Out-Null
    Invoke-WebRequest -Uri 'https://github.com/electron/rcedit/releases/latest/download/rcedit-x64.exe' -OutFile $RceditPath
}

$javaw = Join-Path $AppImageDir 'runtime\bin\javaw.exe'
$target = Join-Path $AppImageDir 'runtime\bin\QueryExe.exe'
Copy-Item -Path $javaw -Destination $target -Force

& $RceditPath $target `
    --set-icon $IconPath `
    --set-version-string 'FileDescription' 'QueryExe' `
    --set-version-string 'ProductName' 'QueryExe' `
    --set-version-string 'InternalName' 'QueryExe' `
    --set-version-string 'OriginalFilename' 'QueryExe.exe'
if ($LASTEXITCODE -ne 0) {
    throw "rcedit failed with exit code $LASTEXITCODE"
}

"rebranded $target"
