param([string]$Art, [string]$Res, [string]$Element = "fire", [switch]$ProjectilesOnly)
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

    // Brightness key: black becomes transparent, colours are un-multiplied so that drawing the result
    // over black gives back the original.
    static void Key(byte[] px, int W, int H, int s, int lo, int hi) {
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
        }
    }

    // The projectile's head: its front edge (the right-most column that is at least 30% as dense as
    // the densest column) and the vertical centre of the last headW columns behind that edge.
    static void Head(byte[] px, int W, int H, int s, int headW, out double front, out double centreY) {
        double[] col = new double[W];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) col[x] += px[y * s + x * 4 + 3];
        double max = col.Max(), t = 0.3 * max;
        int f = W - 1;
        while (f > 0 && col[f] < t) f--;
        double sw = 0, sy = 0;
        for (int y = 0; y < H; y++) for (int x = Math.Max(0, f - headW); x <= f; x++) {
            double a = px[y * s + x * 4 + 3];
            sw += a; sy += a * y;
        }
        front = f;
        centreY = sw > 0 ? sy / sw : H / 2.0;
    }

    static double Median(double[] v) { double[] c = (double[])v.Clone(); Array.Sort(c); return c[c.Length / 2]; }

    // Projectile: key every frame, then lock the head in place — each frame is shifted so its head sits
    // where the median frame has it. In the game the head then moves at exactly the flight speed, in a
    // straight line, while the tail keeps its own animation. One shared crop for all frames.
    public static string Projectile(string dir, string dst, int frameHeight, int lo, int hi) {
        string[] files = Frames(dir);
        int n = files.Length, W0 = 0, H0 = 0;
        byte[][] keyed = new byte[n][];
        double[] fx = new double[n], fy = new double[n];
        for (int k = 0; k < n; k++)
            using (Bitmap b = new Bitmap(files[k])) {
                int s;
                W0 = b.Width; H0 = b.Height;
                byte[] px = Read(b, out s);
                Key(px, W0, H0, s, lo, hi);
                Head(px, W0, H0, s, (int)(H0 * 0.35), out fx[k], out fy[k]);
                keyed[k] = px;
            }
        int headW = (int)(H0 * 0.35);
        double rx = Median(fx), ry = Median(fy);
        double before = 0;
        for (int k = 0; k < n; k++) before = Math.Max(before, Math.Max(Math.Abs(fx[k] - rx), Math.Abs(fy[k] - ry)));

        // Shift each frame on a transparent canvas with room around it.
        int P = H0 / 2, PW = W0 + 2 * P, PH = H0 + 2 * P, ps = PW * 4;
        byte[][] shifted = new byte[n][];
        int ux0 = int.MaxValue, uy0 = int.MaxValue, ux1 = -1, uy1 = -1;
        double after = 0;
        for (int k = 0; k < n; k++) {
            int dx = (int)Math.Round(rx - fx[k]), dy = (int)Math.Round(ry - fy[k]);
            byte[] pp = new byte[ps * PH];
            for (int y = 0; y < H0; y++)
                Buffer.BlockCopy(keyed[k], y * W0 * 4, pp, ((y + P + dy) * PW + (P + dx)) * 4, W0 * 4);
            keyed[k] = null;
            int[] cols = new int[PW], rows = new int[PH];
            for (int y = 0; y < PH; y++) for (int x = 0; x < PW; x++)
                if (pp[y * ps + x * 4 + 3] > 64) { cols[x]++; rows[y]++; }
            int need = Math.Max(2, H0 / 150);
            for (int x = 0; x < PW; x++) if (cols[x] >= need) { ux0 = Math.Min(ux0, x); ux1 = Math.Max(ux1, x); }
            for (int y = 0; y < PH; y++) if (rows[y] >= need) { uy0 = Math.Min(uy0, y); uy1 = Math.Max(uy1, y); }
            double f2, c2;
            Head(pp, PW, PH, ps, headW, out f2, out c2);
            after = Math.Max(after, Math.Max(Math.Abs(f2 - (rx + P)), Math.Abs(c2 - (ry + P))));
            shifted[k] = pp;
        }
        int mx = (int)((ux1 - ux0) * 0.03), my = (int)((uy1 - uy0) * 0.06);
        Rectangle crop = Rectangle.FromLTRB(Math.Max(0, ux0 - mx), Math.Max(0, uy0 - my), Math.Min(PW, ux1 + mx), Math.Min(PH, uy1 + my));
        int fw = (int)Math.Round(frameHeight * crop.Width / (double)crop.Height);
        using (Bitmap sheet = new Bitmap(Cols * fw, Rows * frameHeight, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < n && k < Cols * Rows; k++)
                    using (Bitmap f = FromBytes(shifted[k], PW, PH))
                        DrawInto(g, f, crop, new Rectangle((k % Cols) * fw, (k / Cols) * frameHeight, fw, frameHeight));
            sheet.Save(dst, ImageFormat.Png);
        }
        double toGame = frameHeight / (double)crop.Height;
        return String.Format("{0} frames, head wandered up to {1:F0} px ({2:F1} px in game), after locking {3:F0} px ({4:F1} px in game); frame {5}x{6}",
            n, before, before * toGame, after, after * toGame, fw, frameHeight);
    }
}
"@

$e = $Element
if (-not $ProjectilesOnly) {
    "globe:      " + [AnimSheet]::Globe((Join-Path $Art "elements\anim\$e"), (Join-Path $Res "orb_${e}_anim.png"), 128, 48)
}
"projectile: " + [AnimSheet]::Projectile((Join-Path $Art "projectiles\anim\$e"), (Join-Path $Res "proj_${e}_anim.png"), 128, 8, 60)
