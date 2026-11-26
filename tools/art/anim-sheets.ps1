# Builds the game's animation sheets from Midjourney video frames (frame_00.png, frame_01.png …).
#   Old use, one element:  anim-sheets.ps1 -Art <art dir> -Res <drawable dir> -Element water [-ProjectilesOnly]
#   Any frames → any sheet: anim-sheets.ps1 -Kind globe|projectile|hit -Frames <dir> -Out <png or webp>
#   Freeze (shot → globe):  anim-sheets.ps1 -Kind freeze -Frames <dir> -Out <png> -Cx <x> -Cy <y> -D <d>
#     where (Cx, Cy) and D are the globe's centre and diameter in the video's end frame (video pixels).
param([string]$Art, [string]$Res, [string]$Element = "fire", [switch]$ProjectilesOnly,
      [string]$Kind, [string]$Frames, [string]$Out, [double]$Cx, [double]$Cy, [double]$D, [string]$Track, [switch]$NoLock, [double]$HeadFrac = 0.45)
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
    public static string Projectile(string dir, string dst, int frameHeight, int lo, int hi, bool lockHead = true, double headFrac = HeadFrac) {
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
}
"@

if ($Kind) {
    # -Out *.webp: the sheet is built as PNG, then converted with tools/art/to-webp.ps1 (lossy, alpha kept).
    $webp = $Out -like "*.webp"
    if ($webp) { $Out = [IO.Path]::ChangeExtension($Out, ".png") }
    switch ($Kind) {
        "hit"        { "hit:        " + [AnimSheet]::Hit($Frames, $Out, 256, 28, 110) }
        "globe"      { "globe:      " + [AnimSheet]::Globe($Frames, $Out, 128, 48) }
        "projectile" { "projectile: " + [AnimSheet]::Projectile($Frames, $Out, 128, 8, 60, -not $NoLock, $HeadFrac) }
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
