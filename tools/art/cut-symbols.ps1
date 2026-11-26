# Cuts glowing Midjourney art on black into transparent square sprites:
#
#   cut-symbols.ps1                  element symbols: art/symbols/originals -> res/drawable-nodpi/sym_*.webp
#                                    (the new spells growing in the spell grid, see art/symbols/PROMPTS.md)
#   cut-symbols.ps1 -Kind rings      cast rings: art/rings/originals -> res/drawable-nodpi/ring_*.webp
#                                    (glyph rings around a spell being cast, see art/rings/PROMPTS.md)
#   cut-symbols.ps1 -Kind walls      wall button and wall block: art/walls/originals -> wall_*.webp
#                                    (square tiles with rounded corners, see art/walls/PROMPTS.md)
#
# Alpha comes from brightness (ramp Lo..Hi) and the colour is un-multiplied, so drawing a sprite over
# black gives back the original. The crop is a square around the visible part, centred on it. Symbols
# get some margin and their outer ring fades out, so faint background haze never shows as a box;
# rings are cropped tight, their outer edge on the sprite's edge.
param([ValidateSet('symbols', 'rings', 'walls')][string]$Kind = 'symbols', [int]$Lo = 24, [int]$Hi = 90)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class SymbolCutter {
    // A square tile on black (a button, a wall block): crop a square around it (shrink < 1 trims a
    // glowing rim's halo) and round the corners (cornerFraction of the size).
    public static string Tile(string src, string dst, int size, int threshold, double shrink, double cornerFraction, bool squareFromTop) {
        using (Bitmap b = new Bitmap(src)) {
            int W = b.Width, H = b.Height;
            BitmapData d = b.LockBits(new Rectangle(0, 0, W, H), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            int s = d.Stride;
            byte[] px = new byte[s * H];
            Marshal.Copy(d.Scan0, px, 0, px.Length);
            b.UnlockBits(d);
            int[] cols = new int[W], rows = new int[H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = y * s + x * 4;
                    if (Math.Max(px[i], Math.Max(px[i + 1], px[i + 2])) > threshold) { cols[x]++; rows[y]++; }
                }
            int x0 = 0; while (x0 < W - 1 && cols[x0] < H / 4) x0++;
            int x1 = W - 1; while (x1 > 0 && cols[x1] < H / 4) x1--;
            int y0 = 0; while (y0 < H - 1 && rows[y0] < W / 4) y0++;
            int y1 = H - 1; while (y1 > 0 && rows[y1] < W / 4) y1--;
            // squareFromTop: the tile is square, so a glow or reflection below it is left out.
            if (squareFromTop) y1 = Math.Min(y1, y0 + (x1 - x0));
            int side = (int)(Math.Max(x1 - x0, y1 - y0) * shrink);
            int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
            using (Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb)) {
                using (Graphics g = Graphics.FromImage(o)) {
                    g.CompositingMode = CompositingMode.SourceCopy;
                    g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                    g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                    using (ImageAttributes a = new ImageAttributes()) {
                        a.SetWrapMode(WrapMode.TileFlipXY);
                        g.DrawImage(b, new Rectangle(0, 0, size, size), cx - side / 2, cy - side / 2, side, side, GraphicsUnit.Pixel, a);
                    }
                }
                BitmapData od = o.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
                byte[] op = new byte[od.Stride * size];
                Marshal.Copy(od.Scan0, op, 0, op.Length);
                double r = size * cornerFraction, f = Math.Max(1.0, size * 0.01);
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++) {
                        double qx = Math.Max(Math.Max(r - (x + 0.5), (x + 0.5) - (size - r)), 0);
                        double qy = Math.Max(Math.Max(r - (y + 0.5), (y + 0.5) - (size - r)), 0);
                        double edge = Math.Min(Math.Min(x + 0.5, size - x - 0.5), Math.Min(y + 0.5, size - y - 0.5));
                        double a = (qx > 0 && qy > 0) ? (r - Math.Sqrt(qx * qx + qy * qy)) / f : edge / f;
                        op[y * od.Stride + x * 4 + 3] = (byte)(Math.Min(1.0, Math.Max(0.0, a)) * 255);
                    }
                Marshal.Copy(op, 0, od.Scan0, op.Length);
                o.UnlockBits(od);
                o.Save(dst, ImageFormat.Png);
            }
            return String.Format("tile=({0},{1})-({2},{3}) side={4}", x0, y0, x1, y1, side);
        }
    }

    public static string Cut(string src, string dst, int size, int lo, int hi, double pad, bool fade) {
        using (Bitmap b = new Bitmap(src)) {
            int W = b.Width, H = b.Height;
            BitmapData d = b.LockBits(new Rectangle(0, 0, W, H), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            int s = d.Stride;
            byte[] px = new byte[s * H];
            Marshal.Copy(d.Scan0, px, 0, px.Length);
            b.UnlockBits(d);
            int[] cols = new int[W], rows = new int[H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = y * s + x * 4;
                    int m = Math.Max(px[i], Math.Max(px[i + 1], px[i + 2]));
                    double a = Math.Min(1.0, Math.Max(0.0, (m - lo) / (double)(hi - lo)));
                    if (a > 0) {
                        px[i] = (byte)Math.Min(255, px[i] / a);
                        px[i + 1] = (byte)Math.Min(255, px[i + 1] / a);
                        px[i + 2] = (byte)Math.Min(255, px[i + 2] / a);
                    }
                    px[i + 3] = (byte)(a * 255);
                    if (a > 0.5) { cols[x]++; rows[y]++; }
                }
            int need = Math.Max(3, H / 120);
            int x0 = 0; while (x0 < W - 1 && cols[x0] < need) x0++;
            int x1 = W - 1; while (x1 > 0 && cols[x1] < need) x1--;
            int y0 = 0; while (y0 < H - 1 && rows[y0] < need) y0++;
            int y1 = H - 1; while (y1 > 0 && rows[y1] < need) y1--;
            // With fade, pad 1.25 makes the visible box's diagonal fit inside the faded circle below.
            int side = (int)(Math.Max(x1 - x0, y1 - y0) * pad);
            int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
            Rectangle crop = new Rectangle(cx - side / 2, cy - side / 2, side, side);
            using (Bitmap keyed = new Bitmap(W, H, PixelFormat.Format32bppArgb)) {
                BitmapData k = keyed.LockBits(new Rectangle(0, 0, W, H), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
                Marshal.Copy(px, 0, k.Scan0, px.Length);
                keyed.UnlockBits(k);
                using (Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb))
                using (Graphics g = Graphics.FromImage(o)) {
                    g.CompositingMode = CompositingMode.SourceCopy;
                    g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                    g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                    g.Clear(Color.Transparent);
                    // Outside the image counts as transparent black.
                    g.DrawImage(keyed, new Rectangle(0, 0, size, size), crop.X, crop.Y, crop.Width, crop.Height, GraphicsUnit.Pixel);
                    if (!fade) { o.Save(dst, ImageFormat.Png); return String.Format("visible=({0},{1})-({2},{3}) side={4}", x0, y0, x1, y1, side); }
                    // Fade the outer ring to nothing, so faint background haze can never show as a box.
                    BitmapData od = o.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
                    byte[] op = new byte[od.Stride * size];
                    Marshal.Copy(od.Scan0, op, 0, op.Length);
                    double half = size / 2.0;
                    for (int y = 0; y < size; y++)
                        for (int x = 0; x < size; x++) {
                            double r = Math.Sqrt((x + 0.5 - half) * (x + 0.5 - half) + (y + 0.5 - half) * (y + 0.5 - half)) / half;
                            double f = Math.Min(1.0, Math.Max(0.0, (1.0 - r) / 0.22));
                            int i = y * od.Stride + x * 4 + 3;
                            op[i] = (byte)(op[i] * f * f * (3 - 2 * f));
                        }
                    Marshal.Copy(op, 0, od.Scan0, op.Length);
                    o.UnlockBits(od);
                    o.Save(dst, ImageFormat.Png);
                }
            }
            return String.Format("visible=({0},{1})-({2},{3}) side={4}", x0, y0, x1, y1, side);
        }
    }
}
"@

$root = Join-Path $PSScriptRoot '..\..'
$res = Join-Path $root 'app\src\main\res\drawable-nodpi'
if ($Kind -eq 'walls') {
    # The wall button (a square tile like the spell buttons), and the walls on the field: every
    # originals/crystal_*.png (variants of one crystal brick wall, picked at random per wall) as a
    # square tile with slightly rounded corners -> wall_block_1, wall_block_2, ...
    $art = Join-Path $root 'art\walls'
    $dir = (Resolve-Path $art).Path
    $resDir = (Resolve-Path $res).Path
    New-Item -ItemType Directory -Force (Join-Path $art 'sprites') | Out-Null
    Get-ChildItem $resDir -Filter 'wall_block*.webp' | Remove-Item
    $pngs = @()
    "{0,-12} {1}" -f 'button', [SymbolCutter]::Tile("$dir\originals\button.png", "$dir\sprites\button.png", 256, 60, 0.95, 0.17, $false)
    Copy-Item "$dir\sprites\button.png" "$resDir\wall_button.png" -Force
    $pngs += "$resDir\wall_button.png"
    $i = 1
    foreach ($f in Get-ChildItem "$dir\originals" -Filter 'crystal_*.png' | Sort-Object Name) {
        $sprite = "$dir\sprites\$($f.Name)"
        "{0,-12} {1}" -f $f.BaseName, [SymbolCutter]::Tile($f.FullName, $sprite, 192, 30, 1.0, 0.08, $true)
        Copy-Item $sprite "$resDir\wall_block_$i.png" -Force
        $pngs += "$resDir\wall_block_$i.png"
        $i++
    }
    & (Join-Path $PSScriptRoot 'to-webp.ps1') -Files $pngs
    return
}
if ($Kind -eq 'rings') {
    $art = Join-Path $root 'art\rings'; $names = @('glyphs', 'chain', 'seal'); $prefix = 'ring_'
    $size = 256; $pad = 1.02; $fade = $false
} else {
    $art = Join-Path $root 'art\symbols'; $names = @('fire', 'water', 'earth', 'air', 'lightning', 'shadow'); $prefix = 'sym_'
    $size = 192; $pad = 1.25; $fade = $true
}
New-Item -ItemType Directory -Force (Join-Path $art 'sprites') | Out-Null
$pngs = @()
foreach ($n in $names) {
    $sprite = (Resolve-Path $art).Path + "\sprites\$n.png"
    "{0,-10} {1}" -f $n, [SymbolCutter]::Cut((Resolve-Path "$art\originals\$n.png").Path, $sprite, $size, $Lo, $Hi, $pad, $fade)
    $out = (Resolve-Path $res).Path + "\$prefix$n.png"
    Copy-Item $sprite $out -Force
    $pngs += $out
}
& (Join-Path $PSScriptRoot 'to-webp.ps1') -Files $pngs
