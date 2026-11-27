# Cuts a character out of its Midjourney videos (see art/avatars/PROMPTS.md, art/enemies/PLAN.md):
#
#   cut-avatar.ps1 -Avatar mage -DefeatSheet                 player avatar: art/avatars/anim/mage/
#       cast1..3/, defeat/ -> res/drawable-nodpi/avatar_mage.webp (idle), avatar_mage_cast1..3.webp,
#       avatar_mage_defeat.webp (sheets) and avatar_mage_defeated.webp (the defeat's last frame)
#   cut-avatar.ps1 -Art art\enemies -Prefix '' -Avatar bot_01  enemy: art/enemies/anim/bot_01/cast1/,
#       defeat/ -> bot_01.webp, bot_01_cast1.webp, bot_01_defeated.webp
#   cut-avatar.ps1 -Art art\enemies -Prefix '' -Avatar dummy -NoIdle   dummy: defeat/ -> dummy_defeated.webp
#   -PocketMin 4 also removes smaller enclosed bits of background (the sorceress: see PROMPTS.md)
#   -Shadow removes a pale ground shadow under the feet (bot_02)
#   -FigureMargin 0.1 crops to the figure (first frames and the defeated pose) plus 10% of the height,
#       letting magic that fills the frame run past the edges (Google Flow clips: the sorceress)
#
# Each clip folder holds frame_00..NN.png. The white background is removed by flood-filling from the
# image border through pixels close to the background colour, with a soft edge and the colour
# un-blended. Unlike tools/art/cut-sprite.ps1 (the straw dummy's hit), pale pixels don't let the fill
# through, so a pale glowing crystal stays. Every frame of every clip shares one crop, so the idle pose,
# the animations and the defeated image line up exactly.
param([string]$Avatar, [string]$Art = 'art\avatars', [string]$Prefix = 'avatar_', [int]$Height = 300, [int]$Cols = 8,
      [switch]$DefeatSheet, [switch]$NoIdle, [double]$PocketSeed = 18, [int]$PocketMin = 12, [switch]$Shadow,
      [double]$FigureMargin = 0)
$ErrorActionPreference = 'Stop'
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

public static class AvatarCut {
    const double Hard = 32, Soft = 84;   // colour distance from the background: <= Hard gone, >= Soft solid
    // Enclosed background pockets: patches seeded by pixels closer than PocketSeed to the background
    // colour, removed when at least PocketMin pixels (see Cut).
    static double PocketSeed = 18; static int PocketMin = 12;
    static bool Shadow = false;   // remove a pale ground shadow under the feet (see Cut)
    const byte GlowAlpha = 110;   // opacity of the tint in white pockets inside magic (see Cut)

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

    static int[] Background(byte[] px, int W, int H) {
        var r = new List<int>(); var g = new List<int>(); var b = new List<int>();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (x >= 4 && x < W - 4 && y >= 4 && y < H - 4) continue;
            int i = (y * W + x) * 4; b.Add(px[i]); g.Add(px[i + 1]); r.Add(px[i + 2]);
        }
        r.Sort(); g.Sort(); b.Sort();
        return new[] { b[b.Count / 2], g[g.Count / 2], r[r.Count / 2] };
    }

    static byte[] Cut(byte[] px, int W, int H) {
        int[] bg = Background(px, W, H);
        double[] dist = new double[W * H];
        for (int k = 0; k < W * H; k++) {
            int i = k * 4;
            double db = px[i] - bg[0], dg = px[i + 1] - bg[1], dr = px[i + 2] - bg[2];
            dist[k] = Math.Sqrt(db * db + dg * dg + dr * dr);
        }
        bool[] reached = new bool[W * H];
        var q = new Queue<int>();
        for (int x = 0; x < W; x++) { q.Enqueue(x); q.Enqueue((H - 1) * W + x); }
        for (int y = 0; y < H; y++) { q.Enqueue(y * W); q.Enqueue(y * W + W - 1); }
        while (q.Count > 0) {
            int k = q.Dequeue();
            if (reached[k] || dist[k] >= Soft) continue;
            reached[k] = true;
            int x = k % W, y = k / W;
            if (x > 0) q.Enqueue(k - 1); if (x < W - 1) q.Enqueue(k + 1);
            if (y > 0) q.Enqueue(k - W); if (y < H - 1) q.Enqueue(k + W);
        }
        // Background enclosed by the figure or its magic (a swirl closing around white, a gap between
        // hair and collar): any patch of near-white the border fill didn't reach is background too,
        // down to a dozen pixels. Crystal highlights are coloured, not white, so they stay. Pockets also
        // join through corners: a crack of background between two folds can be one pixel wide and
        // run diagonally (the sorceress's robe), which side-by-side links would split into specks.
        bool[] seen = new bool[W * H];
        var comp = new List<int>();
        int[] glow = new int[W * H];   // 0, or 1 + the 0xRRGGBB tint of a pocket inside magic
        for (int s = 0; s < W * H; s++) {
            if (reached[s] || seen[s] || dist[s] >= PocketSeed) continue;
            comp.Clear();
            q.Enqueue(s); seen[s] = true;
            while (q.Count > 0) {
                int k = q.Dequeue(); comp.Add(k);
                int x = k % W, y = k / W;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if ((dx == 0 && dy == 0) || xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                    int n = yy * W + xx;
                    if (!seen[n] && !reached[n] && dist[n] < Soft) { seen[n] = true; q.Enqueue(n); }
                }
            }
            if (comp.Count < PocketMin) continue;
            // White ringed by magic can be a spell's hot core or the background seen through a rune
            // ring, and their sizes overlap: both become a soft see-through tint of the magic around
            // them (its own colour), so a core glows and a ring gets a faint glowing fill.
            int tint = MagicTint(px, comp, seen, W, H);
            foreach (int k in comp) { reached[k] = true; if (tint >= 0) glow[k] = tint + 1; }
        }
        // A ground shadow Midjourney drew anyway (pale warm grey, a band under the feet): with -Shadow,
        // grow the background into pale, nearly colourless pixels in the bottom fifth of the frame,
        // and drop them completely. Robes, brass and boots are either coloured or dark, so they stay.
        bool[] shadow = new bool[W * H];
        if (Shadow) {
            for (int k = 0; k < W * H; k++) if (reached[k] && k / W >= H * 4 / 5) q.Enqueue(k);
            while (q.Count > 0) {
                int k = q.Dequeue();
                int x = k % W, y = k / W;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= W || yy >= H || yy < H * 4 / 5) continue;
                    int n = yy * W + xx;
                    if (reached[n] || shadow[n]) continue;
                    int i = n * 4, mx = Math.Max(px[i], Math.Max(px[i + 1], px[i + 2])), mn = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2]));
                    // Greyer when darker; the pale part right under the figure picks up a warm tint from it.
                    if ((mx >= 110 && mx - mn <= 40) || (mx >= 120 && mx - mn <= 50) || (mx >= 145 && mx - mn <= 52)) { shadow[n] = true; q.Enqueue(n); }
                }
            }
        }
        byte[] o = new byte[W * H * 4];
        for (int k = 0; k < W * H; k++) {
            int i = k * 4;
            if (shadow[k]) continue;
            if (glow[k] != 0) {
                int t = glow[k] - 1;
                o[i] = (byte)(t & 255); o[i + 1] = (byte)((t >> 8) & 255); o[i + 2] = (byte)((t >> 16) & 255);
                o[i + 3] = GlowAlpha;
                continue;
            }
            double a = reached[k] ? Math.Min(1.0, Math.Max(0.0, (dist[k] - Hard) / (Soft - Hard))) : 1.0;
            if (a <= 0) continue;
            for (int c = 0; c < 3; c++) o[i + c] = (byte)Math.Min(255, Math.Max(0, (px[i + c] - (1 - a) * bg[c]) / a));
            o[i + 3] = (byte)(a * 255);
        }
        FillMagicCores(o, px, W, H);
        // Video compression blurs the white background a pixel or two into the figure's outline, which
        // shows as a light rim on the dark game background: trim two pixels off the outline (twice,
        // each pixel's alpha becomes the smallest in its 3x3 neighbourhood), then darken the pixels
        // right at the new outline, so any light left there turns into a dark edge.
        byte[] e = Erode(Erode(o, W, H), W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int k = y * W + x;
            if (e[k * 4 + 3] == 0) continue;
            bool edge = false;
            for (int dy = -1; dy <= 1 && !edge; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= W || yy >= H || e[(yy * W + xx) * 4 + 3] < 128) { edge = true; break; }
            }
            if (edge) for (int c = 0; c < 3; c++) e[k * 4 + c] = (byte)(e[k * 4 + c] * 0.55);
        }
        // The tinted pockets keep their light colour: trimming and darkening are for the outline only.
        for (int k = 0; k < W * H; k++) {
            if (glow[k] == 0) continue;
            int t = glow[k] - 1, i = k * 4;
            e[i] = (byte)(t & 255); e[i + 1] = (byte)((t >> 8) & 255); e[i + 2] = (byte)((t >> 16) & 255);
            e[i + 3] = GlowAlpha;
        }
        return e;
    }

    // True when a near-white pocket is the hot core of a spell, not background: most pixels around it
    // are bright and strongly coloured (violet glow, flames), while background trapped in the figure
    // is ringed by robe, hair or skin. Such a pocket stays (it shows as the spell's white core).
    // The tint for a pocket inside magic (see the pocket loop in Cut), as 0xRRGGBB in BGR byte order
    // (b | g << 8 | r << 16), or -1 when the pocket isn't inside magic.
    static int MagicTint(byte[] px, List<int> comp, bool[] inComp, int W, int H) {
        int ring = 0, magic = 0; long sb = 0, sg = 0, sr = 0;
        foreach (int k in comp) {
            int x = k % W, y = k / W;
            for (int dy = -2; dy <= 2; dy += 2) for (int dx = -2; dx <= 2; dx += 2) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                int n = yy * W + xx;
                if (inComp[n]) continue;
                int i = n * 4;
                int mx = Math.Max(px[i], Math.Max(px[i + 1], px[i + 2])), mn = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2]));
                ring++;
                if (mx >= 180 && mx - mn >= 35) { magic++; sb += px[i]; sg += px[i + 1]; sr += px[i + 2]; }
            }
        }
        if (ring == 0 || magic * 2 <= ring) return -1;
        int b = (int)(sb / magic), g = (int)(sg / magic), r = (int)(sr / magic);   // the magic's own colour
        return b | g << 8 | r << 16;
    }

    static bool InsideMagic(byte[] px, List<int> comp, bool[] inComp, int W, int H) {
        int ring = 0, magic = 0;
        foreach (int k in comp) {
            int x = k % W, y = k / W;
            for (int dy = -2; dy <= 2; dy += 2) for (int dx = -2; dx <= 2; dx += 2) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                int n = yy * W + xx;
                if (inComp[n]) continue;
                int i = n * 4;
                int mx = Math.Max(px[i], Math.Max(px[i + 1], px[i + 2])), mn = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2]));
                ring++;
                if (mx >= 180 && mx - mn >= 35) magic++;   // also pale lavender glow around a white core
            }
        }
        return ring > 0 && magic * 2 > ring;
    }

    // A spell's white-hot core is as white as the background, so when the magic touches the frame's
    // edge the background fill flows into it and leaves dark holes in the glow. Fill a see-through
    // pixel back in (as the video's own colour, opaque) when bright coloured magic surrounds it: in at
    // least 7 of 8 directions there is such a pixel within 16 px (a small hole; the inside of a rune ring
    // is wider and stays see-through). Background beside the magic has it
    // on one side only, so it stays clear.
    static void FillMagicCores(byte[] o, byte[] px, int W, int H) {
        int[] ddx = { 1, 1, 0, -1, -1, -1, 0, 1 }, ddy = { 0, 1, 1, 1, 0, -1, -1, -1 };
        Func<int, bool> magic = n => {
            int i = n * 4;
            // judged on the video's colour, not the cut's alpha: pale glow around a core is partly see-through too
            int mx = Math.Max(px[i], Math.Max(px[i + 1], px[i + 2])), mn = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2]));
            return mx >= 180 && mx - mn >= 35;
        };
        // The fill is a light tint of the surrounding magic (half its colour, half white), a little
        // see-through: small cores read as hot centres, large white areas as glow rather than paper.
        var fill = new List<int[]>();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int k = y * W + x;
            if (o[k * 4 + 3] >= 128) continue;
            int hits = 0, sb = 0, sg = 0, sr = 0;
            for (int d = 0; d < 8 && hits + (8 - d) >= 7; d++) {
                for (int r = 1; r <= 16; r++) {
                    int xx = x + ddx[d] * r, yy = y + ddy[d] * r;
                    if (xx < 0 || yy < 0 || xx >= W || yy >= H) break;
                    int n = yy * W + xx;
                    if (magic(n)) { hits++; sb += px[n * 4]; sg += px[n * 4 + 1]; sr += px[n * 4 + 2]; break; }
                }
            }
            if (hits >= 7) fill.Add(new[] { k, (sb / hits + 255) / 2, (sg / hits + 255) / 2, (sr / hits + 255) / 2 });
        }
        foreach (int[] f in fill) { int i = f[0] * 4; o[i] = (byte)f[1]; o[i + 1] = (byte)f[2]; o[i + 2] = (byte)f[3]; o[i + 3] = 215; }
    }

    static byte[] Erode(byte[] o, int W, int H) {
        byte[] e = (byte[])o.Clone();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int k = y * W + x;
            if (o[k * 4 + 3] == 0) continue;
            int m = 255;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                int na = (xx < 0 || yy < 0 || xx >= W || yy >= H) ? 0 : o[(yy * W + xx) * 4 + 3];
                if (na < m) m = na;
            }
            e[k * 4 + 3] = (byte)m;
        }
        return e;
    }

    static void Bounds(byte[] a, int W, int H, ref int x0, ref int y0, ref int x1, ref int y1) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            if (a[(y * W + x) * 4 + 3] > 128) { x0 = Math.Min(x0, x); x1 = Math.Max(x1, x); y0 = Math.Min(y0, y); y1 = Math.Max(y1, y); }
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

    // dirs: one folder of frame_*.png per cast. Writes outIdle and outSheets[i] (one per dir).
    // dirs: one folder of frame_*.png per clip. Writes outIdle (the first clip's first frame) unless
    // null, outSheets[i] for each clip unless null, and outDefeated (the last frame of clip
    // defeatIndex) unless null. All share one crop.
    public static string Run(string[] dirs, string outIdle, string[] outSheets, string outDefeated, int defeatIndex, int height, int cols, double pocketSeed, int pocketMin, bool shadow, double figureMargin) {
        PocketSeed = pocketSeed; PocketMin = pocketMin; Shadow = shadow;
        var casts = new List<List<byte[]>>();
        int W = 0, H = 0;
        foreach (string dir in dirs) {
            var frames = new List<byte[]>();
            foreach (string f in Directory.GetFiles(dir, "frame_*.png").OrderBy(f => f))
                using (Bitmap b = new Bitmap(f)) { W = b.Width; H = b.Height; frames.Add(Cut(Read(b), W, H)); }
            casts.Add(frames);
        }
        int x0 = int.MaxValue, y0 = int.MaxValue, x1 = -1, y1 = -1;
        Rectangle crop;
        if (figureMargin > 0) {
            // Frame the figure, not its magic: bounds of each clip's first frame (the pose) and the
            // defeat's last frame, widened by figureMargin of the frame height; effects that reach
            // further (a bolt flying out, a blast filling the frame) are cut at the edges.
            foreach (var frames in casts) Bounds(frames[0], W, H, ref x0, ref y0, ref x1, ref y1);
            if (defeatIndex >= 0) { var d = casts[defeatIndex]; Bounds(d[d.Count - 1], W, H, ref x0, ref y0, ref x1, ref y1); }
            int fm = (int)(H * figureMargin);
            crop = Rectangle.FromLTRB(Math.Max(0, x0 - fm), Math.Max(0, y0 - fm), Math.Min(W, x1 + fm + 1), Math.Min(H, y1 + 2));
        } else {
            foreach (var frames in casts) foreach (byte[] f in frames) Bounds(f, W, H, ref x0, ref y0, ref x1, ref y1);
            int m = (int)(H * 0.02);
            crop = Rectangle.FromLTRB(Math.Max(0, x0 - m), Math.Max(0, y0 - m), Math.Min(W, x1 + m + 1), Math.Min(H, y1 + 2));
        }
        int fw = (int)Math.Round(height * crop.Width / (double)crop.Height);
        if (!string.IsNullOrEmpty(outIdle))
            using (Bitmap o = Scale(casts[0][0], W, H, crop, fw, height)) o.Save(outIdle, ImageFormat.Png);
        if (!string.IsNullOrEmpty(outDefeated) && defeatIndex >= 0) {
            var d = casts[defeatIndex];
            using (Bitmap o = Scale(d[d.Count - 1], W, H, crop, fw, height)) o.Save(outDefeated, ImageFormat.Png);
        }
        for (int s = 0; s < casts.Count; s++) {
            if (string.IsNullOrEmpty(outSheets[s])) continue;
            var frames = casts[s];
            int n = frames.Count, rows = (n + cols - 1) / cols;
            using (Bitmap sheet = new Bitmap(cols * fw, rows * height, PixelFormat.Format32bppArgb)) {
                using (Graphics g = Graphics.FromImage(sheet)) {
                    g.CompositingMode = CompositingMode.SourceCopy;
                    for (int k = 0; k < n; k++)
                        using (Bitmap f = Scale(frames[k], W, H, crop, fw, height)) g.DrawImage(f, (k % cols) * fw, (k / cols) * height, fw, height);
                }
                sheet.Save(outSheets[s], ImageFormat.Png);
            }
        }
        return String.Format("{0} casts of {1} frames, video {2}x{3}, crop {4}, frame {5}x{6}", casts.Count, casts[0].Count, W, H, crop, fw, height);
    }
}
"@

$root = Join-Path $PSScriptRoot '..\..'
$anim = (Resolve-Path (Join-Path $root "$Art\anim\$Avatar")).Path
$res = (Resolve-Path (Join-Path $root 'app\src\main\res\drawable-nodpi')).Path
$base = "$Prefix$Avatar"
$dirs = @(Get-ChildItem $anim -Directory | Where-Object { $_.Name -like 'cast*' -or $_.Name -eq 'defeat' } | Sort-Object Name | ForEach-Object { $_.FullName })
$defeatIndex = [array]::IndexOf(@($dirs | ForEach-Object { Split-Path $_ -Leaf }), 'defeat')
$sheets = @($dirs | ForEach-Object {
    $leaf = Split-Path $_ -Leaf
    if ($leaf -eq 'defeat' -and -not $DefeatSheet) { $null } else { Join-Path $res ("{0}_{1}.png" -f $base, $leaf) }
})
$idle = if ($NoIdle) { $null } else { Join-Path $res "$base.png" }
$defeated = if ($defeatIndex -ge 0) { Join-Path $res "${base}_defeated.png" } else { $null }
[AvatarCut]::Run($dirs, $idle, $sheets, $defeated, $defeatIndex, $Height, $Cols, $PocketSeed, $PocketMin, $Shadow.IsPresent, $FigureMargin)
& (Join-Path $PSScriptRoot 'to-webp.ps1') -Files (@($idle, $defeated) + $sheets | Where-Object { $_ })
