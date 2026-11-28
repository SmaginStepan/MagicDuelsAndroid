# Builds the game's animation sheets from Midjourney video frames (frame_00.png, frame_01.png …).
#   Old use, one element:  anim-sheets.ps1 -Art <art dir> -Res <drawable dir> -Element water [-ProjectilesOnly]
#   Any frames → any sheet: anim-sheets.ps1 -Kind globe|projectile|hit|wall|spikyglobe|spikyshot|glassglobe|plushglobe -Frames <dir> -Out <png or webp>
#   Glass orb in roots:     anim-sheets.ps1 -Kind glassglobe -Frames <dir> -Out <png> -Cx <x> -Cy <y> -D <d>
#   Freeze (shot → globe):  anim-sheets.ps1 -Kind freeze -Frames <dir> -Out <png> -Cx <x> -Cy <y> -D <d>
#     where (Cx, Cy) and D are the globe's centre and diameter in the video's end frame (video pixels).
param([string]$Art, [string]$Res, [string]$Element = "fire", [switch]$ProjectilesOnly,
      [string]$Kind, [string]$Frames, [string]$Out, [double]$Cx, [double]$Cy, [double]$D, [string]$Track, [switch]$NoLock, [double]$HeadFrac = 0.45,
      [double]$BallBack = 0.2, [double]$BallR = 0.2, [double]$HeadDensity = 0.3, [int]$KeyLo = 8, [double]$Protect = 0)
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

    // Background key for dark subjects (element packs: a black obsidian orb, a black iron ball) on a dark
    // but tinted background, where a brightness key would wipe out the subject too. The background's
    // colour is the average of the frame's corners; it is flood-filled from the frame's edges through
    // pixels that differ from it by less than soft (fully clear under hard, a soft edge in between, the
    // colour un-blended from the background). Pixels within protR of (protCx, protCy) are never filled:
    // the orb's dark body, which can be as dark as the background in places.
    static void FloodKey(byte[] px, int W, int H, int s, double hard, double soft, double protCx, double protCy, double protR, double protRy = -1) {
        double br = 0, bgc = 0, bb = 0; int nb = 0;
        int patch = Math.Max(4, Math.Min(W, H) / 40);
        foreach (int[] c in new[] { new[] { 0, 0 }, new[] { W - patch, 0 }, new[] { 0, H - patch }, new[] { W - patch, H - patch } })
            for (int y = c[1]; y < c[1] + patch; y++) for (int x = c[0]; x < c[0] + patch; x++) {
                int i = y * s + x * 4; bb += px[i]; bgc += px[i + 1]; br += px[i + 2]; nb++;
            }
        bb /= nb; bgc /= nb; br /= nb;
        Func<int, double> dist = i => Math.Max(Math.Abs(px[i] - bb), Math.Max(Math.Abs(px[i + 1] - bgc), Math.Abs(px[i + 2] - br)));
        // The protected area is a circle, or an ellipse with radii protR (across) and protRy (up and down).
        double ry = protRy > 0 ? protRy : protR;
        Func<int, int, bool> prot = (x, y) => protR > 0 && (x - protCx) * (x - protCx) / (protR * protR) + (y - protCy) * (y - protCy) / (ry * ry) < 1;
        bool[] seen = new bool[W * H];
        var q = new System.Collections.Generic.Queue<int>();
        Action<int, int> push = (x, y) => {
            int k = y * W + x;
            if (seen[k] || prot(x, y) || dist(y * s + x * 4) >= soft) return;
            seen[k] = true; q.Enqueue(k);
        };
        for (int x = 0; x < W; x++) { push(x, 0); push(x, H - 1); }
        for (int y = 0; y < H; y++) { push(0, y); push(W - 1, y); }
        while (q.Count > 0) {
            int k = q.Dequeue(), x = k % W, y = k / W;
            if (x > 0) push(x - 1, y);
            if (x < W - 1) push(x + 1, y);
            if (y > 0) push(x, y - 1);
            if (y < H - 1) push(x, y + 1);
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * s + x * 4;
            if (!seen[y * W + x]) { px[i + 3] = 255; continue; }
            double a = Math.Min(1.0, Math.Max(0.0, (dist(i) - hard) / (soft - hard)));
            a = a * a * (3 - 2 * a);
            if (a > 0) {
                px[i] = (byte)Math.Max(0, Math.Min(255, bb + (px[i] - bb) / a));
                px[i + 1] = (byte)Math.Max(0, Math.Min(255, bgc + (px[i + 1] - bgc) / a));
                px[i + 2] = (byte)Math.Max(0, Math.Min(255, br + (px[i + 2] - br) / a));
            }
            px[i + 3] = (byte)(a * 255);
        }
    }

    // A spiked globe (element packs): like Globe, but the orb is dark and spikes stick out past its body,
    // so instead of a hard circle the background is flood-keyed away (FloodKey) with the body protected,
    // and the window reaches the spike tips (1.16 × the bright radius, which takes in their lit tips; the
    // body is protected within 0.8 × it), fading out at its
    // round edge.
    public static string SpikyGlobe(string dir, string dst, int size, int threshold) {
        string[] files = Frames(dir);
        double cx, cy, r;
        int W, H;
        using (Bitmap f0 = new Bitmap(files[0])) {
            W = f0.Width; H = f0.Height;
            int s;
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
            cx = (x0 + x1) / 2.0; cy = (y0 + y1) / 2.0; r = ((x1 - x0) + (y1 - y0)) / 4.0;
        }
        double R = Math.Min(r * 1.16, Math.Min(Math.Min(cx, W - cx), Math.Min(cy, H - cy)));
        Rectangle crop = new Rectangle((int)(cx - R), (int)(cy - R), (int)(2 * R), (int)(2 * R));
        using (Bitmap sheet = new Bitmap(Cols * size, Rows * size, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < files.Length && k < Cols * Rows; k++)
                    using (Bitmap f = new Bitmap(files[k])) {
                        int s;
                        byte[] px = Read(f, out s);
                        FloodKey(px, f.Width, f.Height, s, 8, 36, cx, cy, r * 0.8);
                        using (Bitmap keyed = FromBytes(px, f.Width, f.Height))
                            DrawInto(g, keyed, crop, new Rectangle((k % Cols) * size, (k / Cols) * size, size, size));
                    }
            int st;
            byte[] op = Read(sheet, out st);
            double c = size / 2.0, edge = size * 0.06;
            for (int y = 0; y < sheet.Height; y++) for (int x = 0; x < sheet.Width; x++) {
                double dx = (x % size) + 0.5 - c, dy = (y % size) + 0.5 - c;
                double a = Math.Min(1.0, Math.Max(0.0, (c - Math.Sqrt(dx * dx + dy * dy)) / edge));
                a = a * a * (3 - 2 * a);
                op[y * st + x * 4 + 3] = (byte)(op[y * st + x * 4 + 3] * a);
            }
            using (Bitmap o = FromBytes(op, sheet.Width, sheet.Height)) o.Save(dst, ImageFormat.Png);
        }
        return String.Format("{0} frames, body c=({1:F0},{2:F0}) r={3:F0}, window r={4:F0}", files.Length, cx, cy, r, R);
    }

    // Clears the loose bits a key leaves (specks of glow under a creature): every piece of visible pixels
    // (alpha over 40, joined side to side) smaller than minFrac of the biggest piece.
    static void DropSpecks(byte[] px, int W, int H, int s, double minFrac) {
        int[] label = new int[W * H];
        var sizes = new System.Collections.Generic.List<int> { 0 };
        var q = new System.Collections.Generic.Queue<int>();
        for (int start = 0; start < W * H; start++) {
            if (label[start] != 0 || px[(start / W) * s + (start % W) * 4 + 3] <= 40) continue;
            int id = sizes.Count, n = 0;
            label[start] = id; q.Enqueue(start);
            while (q.Count > 0) {
                int k = q.Dequeue(), x = k % W, y = k / W;
                n++;
                foreach (int nk in new[] { x > 0 ? k - 1 : -1, x < W - 1 ? k + 1 : -1, y > 0 ? k - W : -1, y < H - 1 ? k + W : -1 }) {
                    if (nk < 0 || label[nk] != 0 || px[(nk / W) * s + (nk % W) * 4 + 3] <= 40) continue;
                    label[nk] = id; q.Enqueue(nk);
                }
            }
            sizes.Add(n);
        }
        int biggest = sizes.Max();
        for (int k = 0; k < W * H; k++)
            if (label[k] != 0 && sizes[label[k]] < minFrac * biggest) px[(k / W) * s + (k % W) * 4 + 3] = 0;
    }

    // A bouncing plush creature (element packs: Plush Toys): bright fur on a dark background that glows a
    // little around it. Every frame is flood-keyed from its edges (FloodKey, nothing protected: the
    // creature squishes and moves, and its fur is far from the background's colour), then all frames are
    // cut with one square window around everything they show, so the bounce stays in the frame.
    // protect > 0: a creature with patches as dark as the background (earth's moss) gets its body
    // protected in every frame. A first key finds it: its width from the columns at least a quarter as
    // full as the fullest, and its bottom from the rows (its top is no use: leaves on its head are as wide
    // as a quarter of it). The body is taken as an ellipse 0.9 times as tall as it is wide, resting on that
    // bottom, and the real key stays out of protect × that ellipse.
    public static string PlushGlobe(string dir, string dst, int size, double hard, double soft, double margin, double protect) {
        string[] files = Frames(dir);
        int n = Math.Min(files.Length, Cols * Rows), W = 0, H = 0;
        byte[][] keyed = new byte[n][];
        int ux0 = int.MaxValue, uy0 = int.MaxValue, ux1 = -1, uy1 = -1;
        string firstProt = "";
        for (int k = 0; k < n; k++)
            using (Bitmap f = new Bitmap(files[k])) {
                int s;
                W = f.Width; H = f.Height;
                byte[] px = Read(f, out s);
                double pcx = 0, pcy = 0, prx = 0, pry = 0;
                if (protect > 0) {
                    byte[] probe = (byte[])px.Clone();
                    FloodKey(probe, W, H, s, hard, soft, 0, 0, 0);
                    int[] pc = new int[W], prw = new int[H];
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                        if (probe[y * s + x * 4 + 3] > 128) { pc[x]++; prw[y]++; }
                    int mc = pc.Max(), mr = prw.Max();
                    int bx0 = 0; while (bx0 < W - 1 && pc[bx0] < mc / 4) bx0++;
                    int bx1 = W - 1; while (bx1 > 0 && pc[bx1] < mc / 4) bx1--;
                    int by1 = H - 1; while (by1 > 0 && prw[by1] < mr / 4) by1--;
                    // A round shape's chord is a quarter as long as its widest at 0.968 of its radius out.
                    double rx = (bx1 - bx0) / 2.0 / 0.968, ry = 0.9 * rx;
                    pcx = (bx0 + bx1) / 2.0; pcy = by1 + 0.03 * ry - ry;
                    prx = protect * rx; pry = protect * ry;
                }
                FloodKey(px, W, H, s, hard, soft, pcx, pcy, prx, pry);
                DropSpecks(px, W, H, s, 0.01);
                if (k == 0) firstProt = String.Format(" protected c=({0:F0},{1:F0}) r=({2:F0},{3:F0})", pcx, pcy, prx, pry);
                // What the frame shows: the rows and columns with more than a few solid pixels (specks at
                // the video's edge don't count).
                int[] cols = new int[W], rows = new int[H];
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                    if (px[y * s + x * 4 + 3] > 128) { cols[x]++; rows[y]++; }
                int need = Math.Max(3, H / 100);
                for (int x = 0; x < W; x++) if (cols[x] >= need) { ux0 = Math.Min(ux0, x); ux1 = Math.Max(ux1, x); }
                for (int y = 0; y < H; y++) if (rows[y] >= need) { uy0 = Math.Min(uy0, y); uy1 = Math.Max(uy1, y); }
                keyed[k] = px;
            }
        // Clear the specks outside that, so they don't show at the window's edge.
        int pad = 2;
        for (int k = 0; k < n; k++)
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                if (x < ux0 - pad || x > ux1 + pad || y < uy0 - pad || y > uy1 + pad) keyed[k][(y * W + x) * 4 + 3] = 0;
        double cx = (ux0 + ux1) / 2.0, cy = (uy0 + uy1) / 2.0;
        double half = Math.Max(ux1 - ux0, uy1 - uy0) / 2.0 * margin;
        Rectangle crop = new Rectangle((int)Math.Round(cx - half), (int)Math.Round(cy - half), (int)Math.Round(2 * half), (int)Math.Round(2 * half));
        using (Bitmap sheet = new Bitmap(Cols * size, Rows * size, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < n; k++)
                    using (Bitmap kb = FromBytes(keyed[k], W, H))
                        DrawInto(g, kb, crop, new Rectangle((k % Cols) * size, (k / Cols) * size, size, size));
            sheet.Save(dst, ImageFormat.Png);
        }
        return String.Format("{0} frames, everything shown in ({1},{2})-({3},{4}), window {5}", n, ux0, uy0, ux1, uy1, crop) + firstProt;
    }

    // A glass orb wrapped in roots and leaves (element packs: Living Grove). The orb, a circle measured by
    // eye on the video (centre cx, cy and radius r, a little inside the glass so no background is kept),
    // stays whole. Outside it the background is keyed away (RingKey), so the roots and leaves reaching
    // out of the orb stay. The window is win × r, fading out at its round edge.
    public static string GlassGlobe(string dir, string dst, int size, double cx, double cy, double r, double win, double hard, double soft) {
        string[] files = Frames(dir);
        int W, H;
        using (Bitmap f0 = new Bitmap(files[0])) { W = f0.Width; H = f0.Height; }
        double R = Math.Min(r * win, Math.Min(Math.Min(cx, W - cx), Math.Min(cy, H - cy)));
        Rectangle crop = new Rectangle((int)(cx - R), (int)(cy - R), (int)(2 * R), (int)(2 * R));
        using (Bitmap sheet = new Bitmap(Cols * size, Rows * size, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < files.Length && k < Cols * Rows; k++)
                    using (Bitmap f = new Bitmap(files[k])) {
                        int s;
                        byte[] px = Read(f, out s);
                        RingKey(px, W, H, s, cx, cy, r, hard, soft);
                        using (Bitmap keyed = FromBytes(px, W, H))
                            DrawInto(g, keyed, crop, new Rectangle((k % Cols) * size, (k / Cols) * size, size, size));
                    }
            int st;
            byte[] op = Read(sheet, out st);
            double c = size / 2.0, edge = size * 0.06;
            for (int y = 0; y < sheet.Height; y++) for (int x = 0; x < sheet.Width; x++) {
                double dx = (x % size) + 0.5 - c, dy = (y % size) + 0.5 - c;
                double a = Math.Min(1.0, Math.Max(0.0, (c - Math.Sqrt(dx * dx + dy * dy)) / edge));
                a = a * a * (3 - 2 * a);
                op[y * st + x * 4 + 3] = (byte)(op[y * st + x * 4 + 3] * a);
            }
            using (Bitmap o = FromBytes(op, sheet.Width, sheet.Height)) o.Save(dst, ImageFormat.Png);
        }
        return String.Format("{0} frames, orb c=({1:F0},{2:F0}) r={3:F0}, window r={4:F0}", files.Length, cx, cy, r, R);
    }

    // Background key around a round orb (centre cx, cy, radius r) on a background that glows softly around
    // it, which one colour can't match: the background's colour at each distance from the centre is the
    // median of that ring (over 7 neighbouring rings, outside the orb; roots and leaves cover less than
    // half of a ring). From the frame's edges the background is flood-filled through pixels that differ
    // from their ring's colour by less than soft (clear under hard, a soft edge in between, the colour
    // un-blended); holes enclosed by roots are cleared where they match it within hard. The orb is kept.
    static void RingKey(byte[] px, int W, int H, int s, double cx, double cy, double r, double hard, double soft) {
        int n = (int)Math.Ceiling(Math.Sqrt((double)W * W + (double)H * H)) + 2;
        int[,] hist = new int[n, 768];
        int[] cnt = new int[n];
        int[] ringOf = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double rho = Math.Sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
            int b = rho < r ? -1 : (int)rho;
            ringOf[y * W + x] = b;
            if (b < 0) continue;
            int i = y * s + x * 4;
            hist[b, px[i]]++; hist[b, 256 + px[i + 1]]++; hist[b, 512 + px[i + 2]]++; cnt[b]++;
        }
        double[,] bg = new double[n, 3];
        for (int b = 0; b < n; b++) {
            int lo = Math.Max(0, b - 3), hi = Math.Min(n - 1, b + 3), tot = 0;
            for (int k = lo; k <= hi; k++) tot += cnt[k];
            if (tot == 0) continue;
            for (int c = 0; c < 3; c++) {
                int acc = 0, v = 0;
                for (; v < 256; v++) {
                    for (int k = lo; k <= hi; k++) acc += hist[k, c * 256 + v];
                    if (acc * 2 >= tot) break;
                }
                bg[b, c] = v;
            }
        }
        Func<int, int, double> dist = (i, b) => Math.Max(Math.Abs(px[i] - bg[b, 0]), Math.Max(Math.Abs(px[i + 1] - bg[b, 1]), Math.Abs(px[i + 2] - bg[b, 2])));
        bool[] seen = new bool[W * H];
        var q = new System.Collections.Generic.Queue<int>();
        Action<int, int> push = (x, y) => {
            int k = y * W + x, b = ringOf[k];
            if (seen[k] || b < 0 || dist(y * s + x * 4, b) >= soft) return;
            seen[k] = true; q.Enqueue(k);
        };
        for (int x = 0; x < W; x++) { push(x, 0); push(x, H - 1); }
        for (int y = 0; y < H; y++) { push(0, y); push(W - 1, y); }
        while (q.Count > 0) {
            int k = q.Dequeue(), x = k % W, y = k / W;
            if (x > 0) push(x - 1, y);
            if (x < W - 1) push(x + 1, y);
            if (y > 0) push(x, y - 1);
            if (y < H - 1) push(x, y + 1);
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * s + x * 4, k = y * W + x, b = ringOf[k];
            if (b < 0) { px[i + 3] = 255; continue; }
            double d = dist(i, b);
            if (!seen[k] && d >= hard) { px[i + 3] = 255; continue; }
            double a = Math.Min(1.0, Math.Max(0.0, (d - hard) / (soft - hard)));
            a = a * a * (3 - 2 * a);
            if (a > 0) {
                px[i] = (byte)Math.Max(0, Math.Min(255, bg[b, 0] + (px[i] - bg[b, 0]) / a));
                px[i + 1] = (byte)Math.Max(0, Math.Min(255, bg[b, 1] + (px[i + 1] - bg[b, 1]) / a));
                px[i + 2] = (byte)Math.Max(0, Math.Min(255, bg[b, 2] + (px[i + 2] - bg[b, 2]) / a));
            }
            px[i + 3] = (byte)(a * 255);
        }
    }

    // The projectile's head: its front edge (the right-most column that is at least HeadDensity as dense
    // as the densest column) and the vertical centre of the last headW columns behind that edge. A thin
    // head behind a big trail (a spear in a wave) needs a low HeadDensity (-HeadDensity 0.06).
    public static double HeadDensity = 0.3;
    static void Head(byte[] px, int W, int H, int s, int headW, out double front, out double centreY) {
        double[] col = new double[W];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) col[x] += px[y * s + x * 4 + 3];
        double max = col.Max(), t = HeadDensity * max;
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
    public static string Projectile(string dir, string dst, int frameHeight, int lo, int hi, bool lockHead = true, double headFrac = HeadFrac, bool flood = false, double ballBack = 0.2, double ballR = 0.2) {
        string[] files = Frames(dir);
        int n = files.Length, W0 = 0, H0 = 0;
        byte[][] keyed = new byte[n][];
        double[] fx = new double[n], fy = new double[n];
        for (int k = 0; k < n; k++)
            using (Bitmap b = new Bitmap(files[k])) {
                int s;
                W0 = b.Width; H0 = b.Height;
                byte[] px = Read(b, out s);
                if (flood) {
                    // A dark spiked ball: find the head first (from what differs clearly from the
                    // background) and protect a ball-sized circle there, so the fill can't leak in
                    // through its dark spikes.
                    double hx, hy;
                    byte[] probe = (byte[])px.Clone();
                    FloodKey(probe, W0, H0, s, 16, 34, 0, 0, 0);
                    Head(probe, W0, H0, s, (int)(H0 * 0.35), out hx, out hy);
                    FloodKey(px, W0, H0, s, 16, 34, hx - H0 * ballBack, hy, H0 * ballR);
                } else Key(px, W0, H0, s, lo, hi);
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
            // Without head locking (for comets whose "front" is spray that comes and goes) the frames stay as filmed.
            int dx = lockHead ? (int)Math.Round(rx - fx[k]) : 0, dy = lockHead ? (int)Math.Round(ry - fy[k]) : 0;
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
        // Every comet gets the same head size in the game: measure the head (in the columns just
        // behind the locked front edge, the most rows that are solidly covered, median over frames)
        // and cut a window HeadFrac times that tall, with the head's centre half a window height in
        // from the right edge (where the game puts the shot's position) and the tail to the left.
        int hf = (int)Math.Round(rx + P), band = (int)(H0 * 0.25);
        double[] heads = new double[n];
        for (int k = 0; k < n; k++) {
            int best = 0;
            for (int x = Math.Max(0, hf - band); x <= Math.Min(PW - 1, hf); x++) {
                int c = 0;
                for (int y = 0; y < PH; y++) if (shifted[k][y * ps + x * 4 + 3] > 128) c++;
                best = Math.Max(best, c);
            }
            heads[k] = best;
        }
        double headH = Median(heads), cropH = headH / headFrac;
        double hcx = hf - headH / 2, hcy = ry + P;
        // Room ahead of the head: its centre sits HeadInset window heights in from the right edge
        // (GameView.SHOT_HEAD_INSET), so a head that bulges forward is not cut off.
        double right = hcx + HeadInset * cropH, left = Math.Max(ux0, right - MaxAspect * cropH);
        Rectangle crop = new Rectangle((int)Math.Round(left), (int)Math.Round(hcy - cropH / 2), (int)Math.Round(right - left), (int)Math.Round(cropH));
        int fw = (int)Math.Round(frameHeight * crop.Width / (double)crop.Height);
        using (Bitmap sheet = new Bitmap(Cols * fw, Rows * frameHeight, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < n && k < Cols * Rows; k++)
                    using (Bitmap f = FromBytes(shifted[k], PW, PH))
                        DrawInto(g, f, crop, new Rectangle((k % Cols) * fw, (k / Cols) * frameHeight, fw, frameHeight));
            Feather(sheet, fw, frameHeight, 0.3, 0.08, 0.1, 0.1, dst);
        }
        double toGame = frameHeight / (double)crop.Height;
        return String.Format("{0} frames, head {1:F0} px, head wandered up to {2:F0} px ({3:F1} px in game), after locking {4:F0} px ({5:F1} px in game); frame {6}x{7}",
            n, headH, before, before * toGame, after, after * toGame, fw, frameHeight);
    }

    /** Comet head height as a share of the comet frame height (the game draws frames GameView.SHOT_HEIGHT cells tall). */
    // Default head share; a smaller one (-HeadFrac) draws the comet smaller (earth: its dust cloud is much bigger than the boulder).
    public const double HeadFrac = 0.45;
    /** Where the head's centre sits, in window heights in from the right edge (GameView.SHOT_HEAD_INSET). */
    public const double HeadInset = 0.75;
    /** Longest comet tail kept, in frame heights. */
    public const double MaxAspect = 3.0;

    // Fades every cell of the sheet out towards its edges (share of the cell width/height per edge) so
    // whatever the window cuts off ends softly, then saves the sheet.
    static void Feather(Bitmap sheet, int cw, int ch, double l, double r, double t, double b, string dst) {
        int st;
        byte[] op = Read(sheet, out st);
        for (int y = 0; y < sheet.Height; y++) for (int x = 0; x < sheet.Width; x++) {
            double fx = (x % cw + 0.5) / cw, fy = (y % ch + 0.5) / ch;
            double a = Math.Min(Math.Min(fx / l, (1 - fx) / r), Math.Min(fy / t, (1 - fy) / b));
            if (a >= 1) continue;
            a = Math.Max(0, a); a = a * a * (3 - 2 * a);
            op[y * st + x * 4 + 3] = (byte)(op[y * st + x * 4 + 3] * a);
        }
        using (Bitmap o = FromBytes(op, sheet.Width, sheet.Height)) o.Save(dst, ImageFormat.Png);
    }

    // Diameter of the solid blob around (cx, cy) — the comet's head or the forming orb: along rays
    // from the centre (skipping the tail side on the left), where each ray last meets a solidly
    // covered pixel; the median over the rays, so stray frost puffs don't count.
    static double OrbSize(byte[] px, int W, int H, int s, double cx, double cy, double maxR) {
        var rs = new System.Collections.Generic.List<double>();
        for (int a = -135; a <= 135; a += 15) {
            double ang = a * Math.PI / 180, dx = Math.Cos(ang), dy = Math.Sin(ang), last = 0;
            for (double r = 0; r < maxR; r += 1) {
                int x = (int)(cx + dx * r), y = (int)(cy + dy * r);
                if (x < 0 || y < 0 || x >= W || y >= H) break;
                if (px[y * s + x * 4 + 3] > 128) last = r;
            }
            rs.Add(last);
        }
        rs.Sort();
        return 2 * rs[rs.Count / 2];
    }

    // Freeze: the landed shot turning into its globe (a Midjourney video from the comet image to an
    // end frame with the globe where the comet's head was). All frames are brightness-keyed and cut
    // with one window around the globe: the window is d / globeFrac tall and aspect times as wide,
    // with the globe centred half a window height in from its right edge (see Art.freezeFrames).
    // The globe's centre and size are refined from the last frame, which is the end frame.
    public static string Freeze(string dir, string dst, int frameHeight, double cx, double cy, double d,
                                double globeFrac, double aspect, int cols, int rows, int lo, int hi, double headStart, double[] trackDx, double[] trackSize) {
        string[] files = Frames(dir);
        string refined = "as given";
        using (Bitmap last = new Bitmap(files[files.Length - 1])) {
            int s, W = last.Width, H = last.Height;
            byte[] px = Read(last, out s);
            int x0 = int.MaxValue, y0 = int.MaxValue, x1 = -1, y1 = -1;
            for (int y = Math.Max(0, (int)(cy - d)); y < Math.Min(H, (int)(cy + d)); y++)
                for (int x = Math.Max(0, (int)(cx - d)); x < Math.Min(W, (int)(cx + d)); x++) {
                    int i = y * s + x * 4;
                    if (Math.Max(px[i], Math.Max(px[i + 1], px[i + 2])) > 40) { x0 = Math.Min(x0, x); x1 = Math.Max(x1, x); y0 = Math.Min(y0, y); y1 = Math.Max(y1, y); }
                }
            double nd = Math.Max(x1 - x0, y1 - y0);
            if (x1 > x0 && nd > d * 0.8 && nd < d * 1.25) {
                cx = (x0 + x1) / 2.0; cy = (y0 + y1) / 2.0; d = nd;
                refined = String.Format("refined from the last frame");
            }
        }
        double wh = d / globeFrac, ww = wh * aspect;
        int n = Math.Min(files.Length, cols * rows);
        // Size of the head (or the forming orb) in each frame: in a band of columns around the
        // globe's centre, the most rows that are solidly covered — the same measure as the comet
        // heads. Relative to the last frame, which is the globe.
        Bitmap[] keyed = new Bitmap[n];
        double[] size = new double[n];
        for (int k = 0; k < n; k++)
            using (Bitmap b = new Bitmap(files[k])) {
                int s;
                byte[] px = Read(b, out s);
                Key(px, b.Width, b.Height, s, lo, hi);
                // Where the orb is and how big, in globes: hand-measured per frame (trackDx: left of the
                // final globe, trackSize: diameter), or else measured.
                size[k] = trackSize != null && trackSize.Length >= n ? trackSize[k] * d : Math.Max(1, OrbSize(px, b.Width, b.Height, s, cx, cy, 1.3 * d));
                keyed[k] = FromBytes(px, b.Width, b.Height);
            }
        // The clip should start at the flying comet's head size (headStart globes) and grow smoothly
        // into the globe, without Midjourney's swell in between: scale each frame about the globe's
        // centre to the size wanted at that moment (limited and smoothed over neighbouring frames).
        double[] sc = new double[n];
        for (int k = 0; k < n; k++) {
            double t = k / (double)(n - 1), e = t * t * (3 - 2 * t);
            double want = headStart + (1 - headStart) * e, have = size[k] / size[n - 1];
            sc[k] = Math.Min(1.4, Math.Max(0.3, want / have));
        }
        double[] sm = new double[n];
        for (int k = 0; k < n; k++) sm[k] = 0.25 * sc[Math.Max(0, k - 1)] + 0.5 * sc[k] + 0.25 * sc[Math.Min(n - 1, k + 1)];
        sm[n - 1] = 1;
        int fw = (int)Math.Round(frameHeight * aspect);
        using (Bitmap sheet = new Bitmap(cols * fw, rows * frameHeight, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
            using (ImageAttributes clamp = new ImageAttributes()) {
                clamp.SetWrapMode(WrapMode.Clamp); // outside the video is transparent
                for (int k = 0; k < n; k++) {
                    double sw = ww / sm[k], sh = wh / sm[k];
                    // Window scaled about the orb's centre, which is put where the globe will be: half a
                    // window height in from the right edge.
                    double ox = cx - (trackDx != null && trackDx.Length >= n ? trackDx[k] * d : 0);
                    double sx = ox - (ww - wh / 2) / sm[k];
                    double sy = cy - (wh / 2) / sm[k];
                    g.DrawImage(keyed[k], new Rectangle((k % cols) * fw, (k / cols) * frameHeight, fw, frameHeight),
                        (float)sx, (float)sy, (float)sw, (float)sh, GraphicsUnit.Pixel, clamp);
                    keyed[k].Dispose();
                }
            }
            // Feather the window's edges so the cut-off tail fades out instead of ending in a straight line.
            Feather(sheet, fw, frameHeight, 0.35, 0.09, 0.06, 0.06, dst);
        }
        return String.Format("{0} frames, globe ({1:F0},{2:F0}) d={3:F0} {4}; frame {5}x{6}; head/orb sizes {7}; scales {8}",
            n, cx, cy, d, refined, fw, frameHeight,
            String.Join(" ", size.Select(v => (v / size[n - 1]).ToString("F2"))), String.Join(" ", sm.Select(v => v.ToString("F2"))));
    }

    // Hit burst: a one-shot square video (the blast at its peak, flying apart to black). Every frame is
    // brightness-keyed, scaled whole into a size×size cell, and faded out towards a circle so the
    // square never shows; 16 frames in a 4×4 grid (Art.hit).
    public static string Hit(string dir, string dst, int size, int lo, int hi) {
        string[] files = Frames(dir);
        int n = Math.Min(files.Length, 16);
        using (Bitmap sheet = new Bitmap(4 * size, 4 * size, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < n; k++)
                    using (Bitmap b = new Bitmap(files[k])) {
                        int s;
                        byte[] px = Read(b, out s);
                        Key(px, b.Width, b.Height, s, lo, hi);
                        int side = Math.Min(b.Width, b.Height);
                        using (Bitmap keyed = FromBytes(px, b.Width, b.Height))
                            g.DrawImage(keyed, new Rectangle((k % 4) * size, (k / 4) * size, size, size),
                                (b.Width - side) / 2, (b.Height - side) / 2, side, side, GraphicsUnit.Pixel);
                    }
            int st;
            byte[] op = Read(sheet, out st);
            double c = size / 2.0;
            for (int y = 0; y < sheet.Height; y++) for (int x = 0; x < sheet.Width; x++) {
                double dx = (x % size) + 0.5 - c, dy = (y % size) + 0.5 - c;
                double a = Math.Min(1.0, Math.Max(0.0, (c - Math.Sqrt(dx * dx + dy * dy)) / (size * 0.14)));
                a = a * a * (3 - 2 * a);
                op[y * st + x * 4 + 3] = (byte)(op[y * st + x * 4 + 3] * a);
            }
            using (Bitmap o = FromBytes(op, sheet.Width, sheet.Height)) o.Save(dst, ImageFormat.Png);
        }
        return String.Format("{0} frames, cell {1}x{1}", n, size);
    }

    // Wall break: a one-shot square video of a crystal wall bursting into shards (art/walls/PROMPTS.md).
    // Like a hit burst (brightness key, 16 frames in a 4×4 grid), but the first frames are the square wall
    // itself, so the edges are only feathered thinly (6% of the cell) instead of fading to a circle.
    public static string Wall(string dir, string dst, int size, int lo, int hi) {
        string[] files = Frames(dir);
        int n = Math.Min(files.Length, 16);
        using (Bitmap sheet = new Bitmap(4 * size, 4 * size, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Hq(sheet))
                for (int k = 0; k < n; k++)
                    using (Bitmap b = new Bitmap(files[k])) {
                        int s;
                        byte[] px = Read(b, out s);
                        Key(px, b.Width, b.Height, s, lo, hi);
                        int side = Math.Min(b.Width, b.Height);
                        using (Bitmap keyed = FromBytes(px, b.Width, b.Height))
                            g.DrawImage(keyed, new Rectangle((k % 4) * size, (k / 4) * size, size, size),
                                (b.Width - side) / 2, (b.Height - side) / 2, side, side, GraphicsUnit.Pixel);
                    }
            int st;
            byte[] op = Read(sheet, out st);
            double edge = size * 0.06;
            for (int y = 0; y < sheet.Height; y++) for (int x = 0; x < sheet.Width; x++) {
                int fx = x % size, fy = y % size;
                double d = Math.Min(Math.Min(fx, size - 1 - fx), Math.Min(fy, size - 1 - fy)) + 0.5;
                double a = Math.Min(1.0, d / edge);
                a = a * a * (3 - 2 * a);
                op[y * st + x * 4 + 3] = (byte)(op[y * st + x * 4 + 3] * a);
            }
            using (Bitmap o = FromBytes(op, sheet.Width, sheet.Height)) o.Save(dst, ImageFormat.Png);
        }
        return String.Format("{0} frames, cell {1}x{1}", n, size);
    }
}
"@

if ($Kind) {
    [AnimSheet]::HeadDensity = $HeadDensity
    # -Out *.webp: the sheet is built as PNG, then converted with tools/art/to-webp.ps1 (lossy, alpha kept).
    $webp = $Out -like "*.webp"
    if ($webp) { $Out = [IO.Path]::ChangeExtension($Out, ".png") }
    switch ($Kind) {
        "hit"        { "hit:        " + [AnimSheet]::Hit($Frames, $Out, 256, 28, 110) }
        "wall"       { "wall:       " + [AnimSheet]::Wall($Frames, $Out, 256, 28, 110) }
        # Element packs (art/packs/PROMPTS.md): dark spiked subjects on a tinted dark background.
        "spikyglobe" { "spikyglobe: " + [AnimSheet]::SpikyGlobe($Frames, $Out, 128, 48) }
        "spikyshot"  { "spikyshot:  " + [AnimSheet]::Projectile($Frames, $Out, 128, 8, 60, -not $NoLock, $HeadFrac, $true, $BallBack, $BallR) }
        # A glass orb wrapped in roots: -Cx -Cy -D are the orb's centre and diameter (video pixels, by eye).
        "glassglobe" { "glassglobe: " + [AnimSheet]::GlassGlobe($Frames, $Out, 128, $Cx, $Cy, $D / 2, 1.22, 10, 40) }
        # A bouncing plush creature on a dark background (Plush Toys); -Protect 0.92 keeps a key out of
        # its body where it is as dark as the background.
        "plushglobe" { "plushglobe: " + [AnimSheet]::PlushGlobe($Frames, $Out, 128, 14, 48, 1.04, $Protect) }
        "globe"      { "globe:      " + [AnimSheet]::Globe($Frames, $Out, 128, 48) }
        # -KeyLo: the brightness below which the background is clear (raise it for a glowing background).
        "projectile" { "projectile: " + [AnimSheet]::Projectile($Frames, $Out, 128, $KeyLo, 60, -not $NoLock, $HeadFrac) }
        # Matches Art.FREEZE_GLOBE, FREEZE_ASPECT, FREEZE_COLS and FREEZE_ROWS in the game; the clip starts
        # at the flying comet's head size: GameView.SHOT_HEIGHT (1.35 cells) × HeadFrac over a 0.9-cell globe.
        # -Track "dx:size,dx:size,…" (16 frames, in globes): where the orb is, left of the final globe, and
        # how big it is, measured by eye from the frames (art/ANIMATIONS.md); without it the size is measured.
        "freeze"     {
            $dx = $null; $sz = $null
            if ($Track) {
                $pairs = $Track.Split(',') | ForEach-Object { , ($_.Split(':') | ForEach-Object { [double]::Parse($_, [Globalization.CultureInfo]::InvariantCulture) }) }
                $dx = [double[]]($pairs | ForEach-Object { $_[0] }); $sz = [double[]]($pairs | ForEach-Object { $_[1] })
            }
            "freeze:     " + [AnimSheet]::Freeze($Frames, $Out, 200, $Cx, $Cy, $D, 0.42, 1.8, 4, 4, 8, 60, 1.35 * $HeadFrac / 0.9, $dx, $sz)
        }
    }
    if ($webp) { & (Join-Path $PSScriptRoot 'to-webp.ps1') -Files $Out }
    return
}

$e = $Element
if (-not $ProjectilesOnly) {
    "globe:      " + [AnimSheet]::Globe((Join-Path $Art "elements\anim\$e"), (Join-Path $Res "orb_${e}_anim.png"), 128, 48)
}
"projectile: " + [AnimSheet]::Projectile((Join-Path $Art "projectiles\anim\$e"), (Join-Path $Res "proj_${e}_anim.png"), 128, 8, 60)
