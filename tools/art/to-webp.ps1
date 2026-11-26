# Converts PNG images to lossy WebP with lossless alpha (quality 92 by default) and deletes the PNGs.
# Android loads WebP resources exactly like PNG (same resource names), at a fraction of the size.
#
#   to-webp.ps1                      all PNGs in app/src/main/res/drawable-nodpi
#   to-webp.ps1 -Files a.png,b.png   just these
#
# Needs Google's cwebp in tools/art/bin (not in git): download libwebp-<version>-windows-x64.zip from
# https://storage.googleapis.com/downloads.webmproject.org/releases/webp/ and copy bin\cwebp.exe there.
param([string[]]$Files, [int]$Quality = 92)
$ErrorActionPreference = 'Stop'
$cwebp = Join-Path $PSScriptRoot 'bin\cwebp.exe'
if (-not (Test-Path $cwebp)) { throw "cwebp not found at $cwebp (see the comment at the top of this script)" }
if (-not $Files) {
    $res = Join-Path $PSScriptRoot '..\..\app\src\main\res\drawable-nodpi'
    $Files = Get-ChildItem $res -Filter *.png | ForEach-Object { $_.FullName }
}
$before = 0; $after = 0
foreach ($f in $Files) {
    $out = [IO.Path]::ChangeExtension($f, '.webp')
    & $cwebp -quiet -q $Quality -alpha_q 100 -m 6 -sharp_yuv $f -o $out
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $out)) { throw "cwebp failed on $f" }
    $before += (Get-Item $f).Length; $after += (Get-Item $out).Length
    Remove-Item $f
}
"{0} files: {1:N1} MB PNG -> {2:N1} MB WebP" -f $Files.Count, ($before / 1MB), ($after / 1MB)
