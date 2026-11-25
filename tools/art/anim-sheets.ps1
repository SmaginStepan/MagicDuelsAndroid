param([string]$Art, [string]$Res, [string]$Element = "fire")
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.IO;
using System.Linq;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class AnimSheet {
    public const int Cols = 6, Rows = 4;

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

    static void DrawInto(Graphics g, Bitmap src, Rectangle crop, Rectangle dst) {
        using (ImageAttributes a = new ImageAttributes()) {
            a.SetWrapMode(WrapMode.TileFlipXY);
            g.DrawImage(src, dst, crop.X, crop.Y, crop.Width, crop.Height, GraphicsUnit.Pixel, a);
        }
    }

    static Graphics Hq(Bitmap b) {
        Graphics g = Graphics.FromImage(b);
        g.CompositingMode = CompositingMode.SourceCopy;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        return g;
    }

    static string[] Frames(string dir) { return Directory.GetFiles(dir, "frame_*.png").OrderBy(f => f).ToArray(); }

    // Globe: find the orb on the first frame, then cut the same circle from every frame.
    public static string Globe(string dir, string dst, int size, int threshold) {
        string[] files = Frames(dir);
        double cx, cy, r;
        using (Bitmap f0 = new Bitmap(files[0])) {
            int W = f0.Width, H = f0.Height, s;
            byte[] px = Read(f0, out s);
            int[] cols = new int[W], rows = new int[H];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * s + x * 4;
                int l = (px[i + 2] * 299 + px[i + 1] * 587 + px[i] * 114) / 1000;
                if (l > threshold) { cols[x]++; rows[y]++; }
            }
            int need = Math.Max(3, H / 100);
            int x0 = 0; while (x0 < W - 1 && cols[x0] < need) x0++;
            int x1 = W - 1; while (x1 > 0 && cols[x1] < need) x1--;
            int y0 = 0; while (y0 < H - 1 && rows[y0] < need) y0++;
            int y1 = H - 1; while (y1 > 0 && rows[y1] < need) y1--;
            cx = (x0 + x1) / 2.0; cy = (y0 + y1) / 2.0; r = ((x1 - x0) + (y1 - y0)) / 4.0 * 1.01;
        }
        Rectangle crop = new Rectangle((int)(cx - r), (int)(cy - r), (int)(2 * r), (int)(2 * r));
        using (Bitmap sheet = new Bitmap(Cols * size, Rows * size, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < files.Length && k < Cols * Rows; k++)
                    using (Bitmap f = new Bitmap(files[k]))
                        DrawInto(g, f, crop, new Rectangle((k % Cols) * size, (k / Cols) * size, size, size));
            int st;
            byte[] op = Read(sheet, out st);
            double c = size / 2.0, R = size / 2.0 - 0.5, feather = Math.Max(1.2, size * 0.012);
            for (int y = 0; y < sheet.Height; y++) for (int x = 0; x < sheet.Width; x++) {
                double dx = (x % size) + 0.5 - c, dy = (y % size) + 0.5 - c;
                double a = Math.Min(1.0, Math.Max(0.0, (R - Math.Sqrt(dx * dx + dy * dy)) / feather));
                op[y * st + x * 4 + 3] = (byte)(a * 255);
            }
            using (Bitmap o = FromBytes(op, sheet.Width, sheet.Height)) o.Save(dst, ImageFormat.Png);
        }
        return String.Format("{0} frames, circle c=({1:F0},{2:F0}) r={3:F0}, sheet {4}x{5}", files.Length, cx, cy, r, Cols * size, Rows * size);
    }

    // Projectile: brightness key on every frame, one shared crop (union of all frames) so it doesn't jitter.
    public static string Projectile(string dir, string dst, int frameHeight, int lo, int hi) {
        string[] files = Frames(dir);
        Bitmap[] keyed = new Bitmap[files.Length];
        int x0 = int.MaxValue, y0 = int.MaxValue, x1 = -1, y1 = -1;
        for (int k = 0; k < files.Length; k++)
            using (Bitmap b = new Bitmap(files[k])) {
                int W = b.Width, H = b.Height, s;
                byte[] px = Read(b, out s);
                int[] cols = new int[W], rows = new int[H];
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
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
                int need = Math.Max(2, H / 150);
                for (int x = 0; x < W; x++) if (cols[x] >= need) { x0 = Math.Min(x0, x); x1 = Math.Max(x1, x); }
                for (int y = 0; y < H; y++) if (rows[y] >= need) { y0 = Math.Min(y0, y); y1 = Math.Max(y1, y); }
                keyed[k] = FromBytes(px, W, H);
            }
        int W0 = keyed[0].Width, H0 = keyed[0].Height;
        int mx = (int)((x1 - x0) * 0.03), my = (int)((y1 - y0) * 0.06);
        Rectangle crop = Rectangle.FromLTRB(Math.Max(0, x0 - mx), Math.Max(0, y0 - my), Math.Min(W0, x1 + mx), Math.Min(H0, y1 + my));
        int fw = (int)Math.Round(frameHeight * crop.Width / (double)crop.Height);
        using (Bitmap sheet = new Bitmap(Cols * fw, Rows * frameHeight, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < keyed.Length && k < Cols * Rows; k++)
                    DrawInto(g, keyed[k], crop, new Rectangle((k % Cols) * fw, (k / Cols) * frameHeight, fw, frameHeight));
            sheet.Save(dst, ImageFormat.Png);
        }
        foreach (Bitmap b in keyed) b.Dispose();
        return String.Format("{0} frames, crop {1}, frame {2}x{3}, sheet {4}x{5}", files.Length, crop, fw, frameHeight, Cols * fw, Rows * frameHeight);
    }
}
"@

$e = $Element
"globe:      " + [AnimSheet]::Globe((Join-Path $Art "elements\anim\$e"), (Join-Path $Res "orb_${e}_anim.png"), 128, 48)
"projectile: " + [AnimSheet]::Projectile((Join-Path $Art "projectiles\anim\$e"), (Join-Path $Res "proj_${e}_anim.png"), 128, 8, 60)
Get-Item (Join-Path $Res "orb_${e}_anim.png"), (Join-Path $Res "proj_${e}_anim.png") | ForEach-Object { "{0} {1:N0} bytes" -f $_.Name, $_.Length }
