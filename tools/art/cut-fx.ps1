param([string]$Art, [string]$Res)
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class FxCutter {
    static byte[] Read(Bitmap b, out int stride) {
        BitmapData d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        byte[] px = new byte[d.Stride * b.Height];
        Marshal.Copy(d.Scan0, px, 0, px.Length);
        stride = d.Stride;
        b.UnlockBits(d);
        return px;
    }

    static Bitmap FromBytes(byte[] px, int w, int h) {
        Bitmap o = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        BitmapData d = o.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(px, 0, d.Scan0, px.Length);
        o.UnlockBits(d);
        return o;
    }

    static Bitmap Resize(Bitmap src, Rectangle crop, int w, int h) {
        Bitmap o = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(o)) {
            g.CompositingMode = CompositingMode.SourceCopy;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            using (ImageAttributes a = new ImageAttributes()) {
                a.SetWrapMode(WrapMode.TileFlipXY);
                g.DrawImage(src, new Rectangle(0, 0, w, h), crop.X, crop.Y, crop.Width, crop.Height, GraphicsUnit.Pixel, a);
            }
        }
        return o;
    }

    // Glowing effect on black: alpha from brightness (ramp lo..hi), colour un-multiplied so that
    // drawing it over black gives back the original exactly. Cropped to the visible part,
    // so the head of the projectile sits at the right edge.
    public static string Projectile(string src, string dst, int outHeight, int lo, int hi) {
        using (Bitmap b = new Bitmap(src)) {
            int W = b.Width, H = b.Height, s;
            byte[] px = Read(b, out s);
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
                    if (a > 0.25) { cols[x]++; rows[y]++; }
                }
            int need = Math.Max(3, H / 150);
            int x0 = 0; while (x0 < W - 1 && cols[x0] < need) x0++;
            int x1 = W - 1; while (x1 > 0 && cols[x1] < need) x1--;
            int y0 = 0; while (y0 < H - 1 && rows[y0] < need) y0++;
            int y1 = H - 1; while (y1 > 0 && rows[y1] < need) y1--;
            int mx = (int)((x1 - x0) * 0.03), my = (int)((y1 - y0) * 0.06);
            Rectangle crop = Rectangle.FromLTRB(Math.Max(0, x0 - mx), Math.Max(0, y0 - my), Math.Min(W, x1 + mx), Math.Min(H, y1 + my));
            using (Bitmap keyed = FromBytes(px, W, H)) {
                int outW = (int)Math.Round(outHeight * crop.Width / (double)crop.Height);
                using (Bitmap o = Resize(keyed, crop, outW, outHeight)) o.Save(dst, ImageFormat.Png);
                return String.Format("crop={0} -> {1}x{2}", crop, outW, outHeight);
            }
        }
    }

    // Square stone tile on black: crop a square around the tile and round the corners.
    public static string Button(string src, string dst, int size, int threshold, double cornerFraction) {
        using (Bitmap b = new Bitmap(src)) {
            int W = b.Width, H = b.Height, s;
            byte[] px = Read(b, out s);
            int[] cols = new int[W], rows = new int[H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = y * s + x * 4;
                    int m = Math.Max(px[i], Math.Max(px[i + 1], px[i + 2]));
                    if (m > threshold) { cols[x]++; rows[y]++; }
                }
            int needC = H / 4, needR = W / 4;
            int x0 = 0; while (x0 < W - 1 && cols[x0] < needC) x0++;
            int x1 = W - 1; while (x1 > 0 && cols[x1] < needC) x1--;
            int y0 = 0; while (y0 < H - 1 && rows[y0] < needR) y0++;
            int y1 = H - 1; while (y1 > 0 && rows[y1] < needR) y1--;
            // The rim glow makes the detected box a little larger than the stone itself.
            int side = (int)(Math.Max(x1 - x0, y1 - y0) * 0.95);
            int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
            Rectangle crop = new Rectangle(cx - side / 2, cy - side / 2, side, side);
            using (Bitmap o = Resize(b, crop, size, size)) {
                int os;
                byte[] op = Read(o, out os);
                double r = size * cornerFraction, f = Math.Max(1.0, size * 0.01);
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++) {
                        double px0 = x + 0.5, py0 = y + 0.5;
                        double qx = Math.Max(Math.Max(r - px0, px0 - (size - r)), 0);
                        double qy = Math.Max(Math.Max(r - py0, py0 - (size - r)), 0);
                        double dist = Math.Sqrt(qx * qx + qy * qy);
                        double edge = Math.Min(Math.Min(px0, size - px0), Math.Min(py0, size - py0));
                        double a = (qx > 0 && qy > 0) ? (r - dist) / f : edge / f;
                        a = Math.Min(1.0, Math.Max(0.0, a));
                        op[y * os + x * 4 + 3] = (byte)(a * 255);
                    }
                using (Bitmap m = FromBytes(op, size, size)) m.Save(dst, ImageFormat.Png);
            }
            return String.Format("tile=({0},{1})-({2},{3}) side={4}", x0, y0, x1, y1, side);
        }
    }

    public static void Preview(string[] projectiles, string[] buttons, string dst) {
        int ph = 110, bs = 130, gap = 18;
        int w = 900, h = gap + projectiles.Length * (ph + gap) + bs + gap * 2;
        using (Bitmap p = new Bitmap(w, h, PixelFormat.Format32bppArgb))
        using (Graphics g = Graphics.FromImage(p)) {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.Clear(Color.FromArgb(255, 0x13, 0x0E, 0x2A));
            int y = gap;
            foreach (string f in projectiles)
                using (Bitmap s = new Bitmap(f)) {
                    int pw = ph * s.Width / s.Height;
                    g.DrawImage(s, new Rectangle(gap, y, pw, ph));
                    // mirrored, as the enemy fires it
                    g.DrawImage(s, new Rectangle(w - gap, y, -pw, ph));
                    y += ph + gap;
                }
            int x = gap;
            foreach (string f in buttons)
                using (Bitmap s = new Bitmap(f)) {
                    g.DrawImage(s, new Rectangle(x, y + gap, bs, bs));
                    x += bs + gap;
                }
            p.Save(dst, ImageFormat.Png);
        }
    }
}
"@

$names = @("fire", "water", "earth", "air", "lightning", "shadow")
foreach ($k in @("projectiles", "buttons")) { New-Item -ItemType Directory -Force (Join-Path $Art "$k\sprites") | Out-Null }
$projs = @(); $btns = @()
foreach ($n in $names) {
    $p = Join-Path $Art "projectiles\sprites\$n.png"
    "proj {0,-10} {1}" -f $n, [FxCutter]::Projectile((Join-Path $Art "projectiles\originals\$n.png"), $p, 384, 8, 60)
    $projs += $p
    $b = Join-Path $Art "buttons\sprites\$n.png"
    "btn  {0,-10} {1}" -f $n, [FxCutter]::Button((Join-Path $Art "buttons\originals\$n.png"), $b, 512, 30, 0.17)
    $btns += $b
}
[FxCutter]::Preview($projs, $btns, (Join-Path $Art "projectiles-and-buttons-preview.png"))

# Game copies: projectiles 160 px tall, buttons 256 px.
foreach ($n in $names) {
    foreach ($pair in @(@("projectiles", "proj", 160), @("buttons", "btn", 256))) {
        $src = New-Object System.Drawing.Bitmap (Join-Path $Art ("{0}\sprites\{1}.png" -f $pair[0], $n))
        $h = [int]$pair[2]; $w = [int][Math]::Round($h * $src.Width / $src.Height)
        $dst = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($dst)
        $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.DrawImage($src, 0, 0, $w, $h)
        $g.Dispose()
        $out = Join-Path $Res ("{0}_{1}.png" -f $pair[1], $n)
        $dst.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
        $dst.Dispose(); $src.Dispose()
        "{0} {1}x{2} {3:N0} bytes" -f (Split-Path $out -Leaf), $w, $h, (Get-Item $out).Length
    }
}
