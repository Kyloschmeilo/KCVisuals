<#
.SYNOPSIS
  Copies the collected PvP packs into <Target>\kcvisuals\packs\ for the KC Visuals mod.
.PARAMETER Target
  The .minecraft folder (default: %APPDATA%\.minecraft).
#>
param(
    [string]$Target = (Join-Path $env:APPDATA '.minecraft')
)

$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
$dest = Join-Path $Target 'kcvisuals\packs'
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$copied = 0; $skipped = 0; $bytes = 0L
foreach ($sub in 'local', 'modrinth') {
    $dir = Join-Path $repo "packs\$sub"
    if (-not (Test-Path -LiteralPath $dir)) { Write-Warning "Missing folder: $dir"; continue }
    foreach ($f in Get-ChildItem -LiteralPath $dir -Filter '*.zip' -File) {
        $out = Join-Path $dest $f.Name
        if ((Test-Path -LiteralPath $out) -and ((Get-Item -LiteralPath $out).Length -eq $f.Length)) {
            $skipped++
        } else {
            Copy-Item -LiteralPath $f.FullName -Destination $out -Force
            $copied++
            $bytes += $f.Length
        }
    }
}

$total = (Get-ChildItem -LiteralPath $dest -Filter '*.zip' -File | Measure-Object Length -Sum).Sum
Write-Host ("Target: {0}" -f $dest)
Write-Host ("Copied: {0}  Skipped: {1}  Copied size: {2:N1} MB  Total in target: {3:N1} MB" -f $copied, $skipped, ($bytes / 1MB), ($total / 1MB))
