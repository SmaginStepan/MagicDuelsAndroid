# Cuts a character sprite and its animation frames out of a light, plain background
# (e.g. Midjourney images/videos "on a plain pure white background").
#
#   cut-sprite.ps1 -Dir art\dummy\anim -OutIdle ...\dummy_idle.png -OutSheet ...\dummy_hit.png
#
# Dir holds idle.png and frame_00..NN.png. The background is removed by flood-filling from the
# image border through pixels close to the background colour (soft edge, colour un-blended), so
# light areas enclosed by the character (e.g. the white rings of a target) stay. All frames share
# one crop; the last FadeFrames frames fade back into the idle pose. Frames go in a Cols-wide grid.
param([string]$Dir, [string]$OutIdle, [string]$OutSheet, [int]$Height = 300, [int]$Cols = 4, [int]$FadeFrames = 4)
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class SpriteCut {
    const double Hard = 26, Soft = 70;   // colour distance: <= Hard is background, >= Soft is solid

    static byte[] Read(Bitmap b) {
        BitmapData d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        byte[] px = new byte[b.Width * 4 * b.Height];
        for (int y = 0; y < b.Height; y++) Marshal.Copy(d.Scan0 + y * d.Stride, px, y * b.Width * 4, b.Width * 4);
        b.UnlockBits(d);
        return px;
    }

    static Bitmap FromBytes(byte[] px, int w, int h) {
        Bitmap o = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        BitmapData d = o.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        for (int y = 0; y < h; y++) Marshal.Copy(px, y * w * 4, d.Scan0 + y * d.Stride, w * 4);
        o.UnlockBits(d);
        return o;
    }

    // Background colour: per-channel median of a 4-pixel border ring.
    static int[] Background(byte[] px, int W, int H) {
        var r = new List<int>(); var g = new List<int>(); var b = new List<int>();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (x >= 4 && x < W - 4 && y >= 4 && y < H - 4) continue;
            int i = (y * W + x) * 4; b.Add(px[i]); g.Add(px[i + 1]); r.Add(px[i + 2]);
        }
        r.Sort(); g.Sort(); b.Sort();
        return new[] { b[b.Count / 2], g[g.Count / 2], r[r.Count / 2] };
    }

    // Returns premultiplied-free BGRA with the background removed.
    public static byte[] Cut(byte[] px, int W, int H) {
        int[] bg = Background(px, W, H);
        double[] dist = new double[W * H];
        for (int k = 0; k < W * H; k++) {
            int i = k * 4;
            double db = px[i] - bg[0], dg = px[i + 1] - bg[1], dr = px[i + 2] - bg[2];
            dist[k] = Math.Sqrt(db * db + dg * dg + dr * dr);
        }
        // Light pixels (darkest channel >= 115: pale haze and light straw) also let the fill through:
        // pale haze is often walled in by straw strands and would otherwise be cut off from the
        // border. Saturated colours (orange body, purple hat, red target rings) stop the fill.
        bool[] pale = new bool[W * H];
        for (int k = 0; k < W * H; k++) { int i = k * 4; pale[k] = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2])) >= 115; }
        bool[] reached = new bool[W * H];
        var q = new Queue<int>();
        for (int x = 0; x < W; x++) { q.Enqueue(x); q.Enqueue((H - 1) * W + x); }
        for (int y = 0; y < H; y++) { q.Enqueue(y * W); q.Enqueue(y * W + W - 1); }
        while (q.Count > 0) {
            int k = q.Dequeue();
            if (reached[k] || (dist[k] >= Soft && !pale[k])) continue;
            reached[k] = true;
            int x = k % W, y = k / W;
            if (x > 0) q.Enqueue(k - 1); if (x < W - 1) q.Enqueue(k + 1);
            if (y > 0) q.Enqueue(k - W); if (y < H - 1) q.Enqueue(k + W);
        }
        byte[] o = new byte[W * H * 4];
        for (int k = 0; k < W * H; k++) {
            int i = k * 4;
            double a = reached[k] ? Math.Min(1.0, Math.Max(0.0, (dist[k] - Hard) / (Soft - Hard))) : 1.0;
            // Very pale haze connected to the background (pale straw streaks, cream dust puffs) fades out
            // by how pale it is: its darkest channel at 135 or below stays, at 165 or above it's gone.
            // Strong colours (orange body, yellow straw, golden glow) are untouched.
            if (reached[k]) {
                int mn = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2]));
                a *= Math.Min(1.0, Math.Max(0.0, (165 - mn) / 30.0));
            }
            if (a <= 0) continue;
            for (int c = 0; c < 3; c++) o[i + c] = (byte)Math.Min(255, Math.Max(0, (px[i + c] - (1 - a) * bg[c]) / a));
            o[i + 3] = (byte)(a * 255);
        }
        return o;
    }

    static void Bounds(byte[] a, int W, int H, ref int x0, ref int y0, ref int x1, ref int y1) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            if (a[(y * W + x) * 4 + 3] > 128) { x0 = Math.Min(x0, x); x1 = Math.Max(x1, x); y0 = Math.Min(y0, y); y1 = Math.Max(y1, y); }
    }

    // Straight-alpha blend of two cut frames, done premultiplied so edges don't get dark fringes.
    static byte[] Blend(byte[] a, byte[] b, double w) {
        byte[] o = new byte[a.Length];
        for (int i = 0; i < a.Length; i += 4) {
            double aa = a[i + 3] / 255.0, ba = b[i + 3] / 255.0;
            double oa = aa * (1 - w) + ba * w;
            if (oa <= 0) continue;
            for (int c = 0; c < 3; c++) o[i + c] = (byte)Math.Min(255, (a[i + c] * aa * (1 - w) + b[i + c] * ba * w) / oa);
            o[i + 3] = (byte)(oa * 255);
        }
        return o;
    }

    static Bitmap Scale(byte[] px, int W, int H, Rectangle crop, int w, int h) {
        using (Bitmap src = FromBytes(px, W, H)) {
            Bitmap o = new Bitmap(w, h, PixelFormat.Format32bppArgb);
            using (Graphics g = Graphics.FromImage(o)) {
                g.CompositingMode = CompositingMode.SourceCopy;
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                using (ImageAttributes ia = new ImageAttributes()) {
                    ia.SetWrapMode(WrapMode.TileFlipXY);
                    g.DrawImage(src, new Rectangle(0, 0, w, h), crop.X, crop.Y, crop.Width, crop.Height, GraphicsUnit.Pixel, ia);
                }
            }
            return o;
        }
    }

    public static string Run(string dir, string outIdle, string outSheet, int height, int cols, int fade) {
        string[] frames = Directory.GetFiles(dir, "frame_*.png").OrderBy(f => f).ToArray();
        int W, H;
        byte[] idle;
        using (Bitmap b = new Bitmap(Path.Combine(dir, "idle.png"))) { W = b.Width; H = b.Height; idle = Cut(Read(b), W, H); }
        var cut = new List<byte[]>();
        foreach (string f in frames) using (Bitmap b = new Bitmap(f)) cut.Add(Cut(Read(b), W, H));
        int n = cut.Count;
        for (int k = 0; k < fade && k < n; k++) {
            int i = n - fade + k;
            cut[i] = Blend(cut[i], idle, (k + 1.0) / (fade + 1.0));
        }
        int x0 = int.MaxValue, y0 = int.MaxValue, x1 = -1, y1 = -1;
        Bounds(idle, W, H, ref x0, ref y0, ref x1, ref y1);
        foreach (byte[] c in cut) Bounds(c, W, H, ref x0, ref y0, ref x1, ref y1);
        int m = (int)(H * 0.02);
        Rectangle crop = Rectangle.FromLTRB(Math.Max(0, x0 - m), Math.Max(0, y0 - m), Math.Min(W, x1 + m + 1), Math.Min(H, y1 + 2));
        int fw = (int)Math.Round(height * crop.Width / (double)crop.Height);
        using (Bitmap o = Scale(idle, W, H, crop, fw, height)) o.Save(outIdle, ImageFormat.Png);
        int rows = (n + cols - 1) / cols;
        using (Bitmap sheet = new Bitmap(cols * fw, rows * height, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Graphics.FromImage(sheet)) {
                g.CompositingMode = CompositingMode.SourceCopy;
                for (int k = 0; k < n; k++)
                    using (Bitmap f = Scale(cut[k], W, H, crop, fw, height)) g.DrawImage(f, (k % cols) * fw, (k / cols) * height, fw, height);
            }
            sheet.Save(outSheet, ImageFormat.Png);
        }
        return String.Format("{0} hit frames (+idle), crop {1}, frame {2}x{3}, sheet {4}x{5}", n, crop, fw, height, cols * fw, rows * height);
    }
}
"@
[SpriteCut]::Run((Resolve-Path $Dir).Path, $OutIdle, $OutSheet, $Height, $Cols, $FadeFrames)
