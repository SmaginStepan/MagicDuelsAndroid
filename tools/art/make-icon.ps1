# Makes the adaptive launcher icon from a square Midjourney picture (see art/icon/PROMPTS.md).
#
#   make-icon.ps1                    art/icon/originals/sigil.png -> res/drawable-nodpi/ic_launcher_foreground.webp
#                                    and the background colour in res/values/ic_launcher_colors.xml
#
# Adaptive icons are 108 dp layers of which launchers show roughly the middle 72 dp (cropped to their
# shape). The picture goes in at $ArtDp (84 dp) in the middle of a 432 px (xxxhdpi) foreground, its
# edges fading out over $Feather px into the background colour: the average colour of the picture's
# border, so the seam doesn't show.
param([string]$Picture = 'art\icon\originals\sigil.png', [int]$ArtDp = 84, [int]$Feather = 28)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '..\..'
$res = (Resolve-Path (Join-Path $root 'app\src\main\res')).Path
$srcPath = (Resolve-Path (Join-Path $root $Picture)).Path
$src = New-Object System.Drawing.Bitmap $srcPath

# Background colour: the average of the picture's outer 3% border.
$band = [int]($src.Width * 0.03)
[double]$r = 0; [double]$g = 0; [double]$b = 0; $n = 0
for ($y = 0; $y -lt $src.Height; $y += 4) {
    for ($x = 0; $x -lt $src.Width; $x += 4) {
        if ($x -ge $band -and $x -lt $src.Width - $band -and $y -ge $band -and $y -lt $src.Height - $band) { continue }
        $c = $src.GetPixel($x, $y); $r += $c.R; $g += $c.G; $b += $c.B; $n++
    }
}
$back = '#{0:X2}{1:X2}{2:X2}' -f [int]($r / $n), [int]($g / $n), [int]($b / $n)

# Foreground: the picture at $ArtDp of 108 dp, edges feathered to transparent.
$size = 432
$art = [int]($size * $ArtDp / 108)
$off = [int](($size - $art) / 2)
$fg = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gr = [System.Drawing.Graphics]::FromImage($fg)
$gr.InterpolationMode = 'HighQualityBicubic'; $gr.PixelOffsetMode = 'HighQuality'
$gr.Clear([System.Drawing.Color]::Transparent)
$gr.DrawImage($src, $off, $off, $art, $art)
$gr.Dispose(); $src.Dispose()
for ($y = $off; $y -lt $off + $art; $y++) {
    for ($x = $off; $x -lt $off + $art; $x++) {
        $d = [Math]::Min([Math]::Min($x - $off, $off + $art - 1 - $x), [Math]::Min($y - $off, $off + $art - 1 - $y))
        if ($d -ge $Feather) { continue }
        $k = ($d + 0.5) / $Feather; $k = $k * $k * (3 - 2 * $k)
        $c = $fg.GetPixel($x, $y)
        $fg.SetPixel($x, $y, [System.Drawing.Color]::FromArgb([int]($c.A * $k), $c.R, $c.G, $c.B))
    }
}
$out = Join-Path $res 'drawable-nodpi\ic_launcher_foreground.png'
$fg.Save($out, [System.Drawing.Imaging.ImageFormat]::Png); $fg.Dispose()
& (Join-Path $PSScriptRoot 'to-webp.ps1') -Files $out

@"
<?xml version="1.0" encoding="utf-8"?>
<!-- Made by tools/art/make-icon.ps1: the average colour of the icon picture's border. -->
<resources>
    <color name="ic_launcher_background">$back</color>
</resources>
"@ | ForEach-Object { [IO.File]::WriteAllText((Join-Path $res 'values\ic_launcher_colors.xml'), $_) }
"background $back, art ${art}px at $off"
