param([string]$Root)
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class OrbCutter {
    // Finds the glowing orb on a dark background, crops a square around it and applies a
    // round alpha mask with a soft edge. Returns the detected box for checking.
    public static string Cut(string src, string dst, int size, int threshold) {
        using (Bitmap b = new Bitmap(src)) {
            int W = b.Width, H = b.Height;
            BitmapData d = b.LockBits(new Rectangle(0, 0, W, H), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            byte[] px = new byte[d.Stride * H];
            Marshal.Copy(d.Scan0, px, 0, px.Length);
            int stride = d.Stride;
            b.UnlockBits(d);

            int[] cols = new int[W], rows = new int[H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = y * stride + x * 4;
                    int l = (px[i + 2] * 299 + px[i + 1] * 587 + px[i] * 114) / 1000;
                    if (l > threshold) { cols[x]++; rows[y]++; }
                }
            int need = Math.Max(4, H / 100);
            int x0 = 0; while (x0 < W - 1 && cols[x0] < need) x0++;
            int x1 = W - 1; while (x1 > 0 && cols[x1] < need) x1--;
            int y0 = 0; while (y0 < H - 1 && rows[y0] < need) y0++;
            int y1 = H - 1; while (y1 > 0 && rows[y1] < need) y1--;
            double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0;
            double r = ((x1 - x0) + (y1 - y0)) / 4.0 * 1.01;

            using (Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb)) {
                using (Graphics g = Graphics.FromImage(o)) {
                    g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                    g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                    g.SmoothingMode = SmoothingMode.HighQuality;
                    g.Clear(Color.Black);
                    g.DrawImage(b, new Rectangle(0, 0, size, size),
                        new RectangleF((float)(cx - r), (float)(cy - r), (float)(2 * r), (float)(2 * r)), GraphicsUnit.Pixel);
                }
                BitmapData od = o.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
                byte[] op = new byte[od.Stride * size];
                Marshal.Copy(od.Scan0, op, 0, op.Length);
                double c = size / 2.0, R = size / 2.0 - 1, feather = Math.Max(1.5, size * 0.012);
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++) {
                        double dx = x + 0.5 - c, dy = y + 0.5 - c;
                        double a = Math.Min(1.0, Math.Max(0.0, (R - Math.Sqrt(dx * dx + dy * dy)) / feather));
                        op[y * od.Stride + x * 4 + 3] = (byte)(a * 255);
                    }
                Marshal.Copy(op, 0, od.Scan0, op.Length);
                o.UnlockBits(od);
                o.Save(dst, ImageFormat.Png);
            }
            return String.Format("box=({0},{1})-({2},{3}) center=({4:F0},{5:F0}) radius={6:F0}", x0, y0, x1, y1, cx, cy, r);
        }
    }

    // Lays the sprites out on the game's field colour at two sizes to check edges and readability.
    public static void Preview(string[] sprites, string dst) {
        int big = 200, small = 96, gap = 16;
        int n = sprites.Length;
        int w = gap + n * (big + gap), h = gap + big + gap + small + gap;
        using (Bitmap p = new Bitmap(w, h, PixelFormat.Format32bppArgb))
        using (Graphics g = Graphics.FromImage(p)) {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.Clear(Color.FromArgb(255, 0x13, 0x0E, 0x2A));
            for (int i = 0; i < n; i++)
                using (Bitmap s = new Bitmap(sprites[i])) {
                    int x = gap + i * (big + gap);
                    g.DrawImage(s, new Rectangle(x, gap, big, big));
                    g.DrawImage(s, new Rectangle(x + (big - small) / 2, gap + big + gap, small, small));
                }
            p.Save(dst, ImageFormat.Png);
        }
    }
}
"@

$names = @("fire", "water", "earth", "air", "lightning", "shadow")
$spritesDir = Join-Path $Root "sprites"
New-Item -ItemType Directory -Force $spritesDir | Out-Null
$made = @()
foreach ($n in $names) {
    $src = Join-Path $Root "originals\$n.png"
    $dst = Join-Path $spritesDir "$n.png"
    $info = [OrbCutter]::Cut($src, $dst, 512, 48)
    "{0,-10} {1}" -f $n, $info
    $made += $dst
}
[OrbCutter]::Preview($made, (Join-Path $Root "preview.png"))
"preview written"
