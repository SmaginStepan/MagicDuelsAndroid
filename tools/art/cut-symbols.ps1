# Cuts the element symbols (glowing emblems on black, see art/symbols/PROMPTS.md) into transparent
# square sprites for the spell grid, where the new spells grow during a cast.
#
#   cut-symbols.ps1                  art/symbols/originals/*.png -> res/drawable-nodpi/sym_*.webp
#
# Alpha comes from brightness (ramp Lo..Hi) and the colour is un-multiplied, so drawing a sprite over
# black gives back the original. The crop is a square around the visible part, centred on it, and
# its outer ring fades out so faint background haze never shows as a box.
param([int]$Size = 192, [int]$Lo = 24, [int]$Hi = 90)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class SymbolCutter {
    public static string Cut(string src, string dst, int size, int lo, int hi) {
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
            // The visible box's diagonal fits inside the faded circle below.
            int side = (int)(Math.Max(x1 - x0, y1 - y0) * 1.25);
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
$art = Join-Path $root 'art\symbols'
$res = Join-Path $root 'app\src\main\res\drawable-nodpi'
New-Item -ItemType Directory -Force (Join-Path $art 'sprites') | Out-Null
$pngs = @()
foreach ($n in @('fire', 'water', 'earth', 'air', 'lightning', 'shadow')) {
    $sprite = (Resolve-Path $art).Path + "\sprites\$n.png"
    "{0,-10} {1}" -f $n, [SymbolCutter]::Cut((Resolve-Path "$art\originals\$n.png").Path, $sprite, $Size, $Lo, $Hi)
    $out = (Resolve-Path $res).Path + "\sym_$n.png"
    Copy-Item $sprite $out -Force
    $pngs += $out
}
& (Join-Path $PSScriptRoot 'to-webp.ps1') -Files $pngs
