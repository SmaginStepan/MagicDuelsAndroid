param([string]$From, [string]$To, [int]$Size = 256)
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $To | Out-Null
foreach ($n in @("fire", "water", "earth", "air", "lightning", "shadow")) {
    $src = New-Object System.Drawing.Bitmap (Join-Path $From "$n.png")
    $dst = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $attrs = New-Object System.Drawing.Imaging.ImageAttributes
    $attrs.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $Size, $Size), 0, 0, $src.Width, $src.Height, [System.Drawing.GraphicsUnit]::Pixel, $attrs)
    $g.Dispose()
    $out = Join-Path $To "orb_$n.png"
    $dst.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $dst.Dispose(); $src.Dispose()
    "{0}  {1:N0} bytes" -f $out, (Get-Item $out).Length
}
