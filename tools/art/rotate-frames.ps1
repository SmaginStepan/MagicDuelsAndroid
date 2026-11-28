# Turns every frame_*.png of a clip by the same angle about the frame's centre (clockwise for a positive
# angle, on black), e.g. to make a shot that Midjourney drew rising to the right fly level:
#   rotate-frames.ps1 -Frames <dir> -Out <dir> -Angle 13 [-Dy <px>]
# -Dy moves the picture down by that many pixels after turning it (to keep it centred in the frame).
param([string]$Frames, [string]$Out, [double]$Angle, [double]$Dy = 0)
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $Out | Out-Null
$n = 0
foreach ($f in Get-ChildItem $Frames -Filter 'frame_*.png' | Sort-Object Name) {
    $src = New-Object System.Drawing.Bitmap $f.FullName
    $dst = New-Object System.Drawing.Bitmap $src.Width, $src.Height
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.Clear([System.Drawing.Color]::Black)
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.TranslateTransform([float]($src.Width / 2), [float]($src.Height / 2 + $Dy))
    $g.RotateTransform([float]$Angle)
    $g.DrawImage($src, [float](-$src.Width / 2), [float](-$src.Height / 2), [float]$src.Width, [float]$src.Height)
    $g.Dispose()
    $src.Dispose()
    $dst.Save((Join-Path $Out $f.Name), [System.Drawing.Imaging.ImageFormat]::Png)
    $dst.Dispose()
    $n++
}
"$n frames turned by $Angle degrees"
