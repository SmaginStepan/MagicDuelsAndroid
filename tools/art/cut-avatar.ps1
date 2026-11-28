# Cuts a character out of its Midjourney videos (see art/avatars/PROMPTS.md, art/enemies/PLAN.md):
#
#   cut-avatar.ps1 -Avatar mage -DefeatSheet                 player avatar: art/avatars/anim/mage/
#       cast1..3/, defeat/ -> res/drawable-nodpi/avatar_mage.webp (idle), avatar_mage_cast1..3.webp,
#       avatar_mage_defeat.webp (sheets) and avatar_mage_defeated.webp (the defeat's last frame);
#       revive/ (rising from the defeated pose to the idle one) -> avatar_mage_revive.webp
#   cut-avatar.ps1 -Art art\enemies -Prefix '' -Avatar bot_01  enemy: art/enemies/anim/bot_01/cast1/,
#       defeat/ -> bot_01.webp, bot_01_cast1.webp, bot_01_defeated.webp
#   cut-avatar.ps1 -Art art\enemies -Prefix '' -Avatar dummy -NoIdle   dummy: defeat/ -> dummy_defeated.webp
#   -PocketMin 4 also removes smaller enclosed bits of background (the sorceress: see PROMPTS.md)
#   -Shadow removes a pale ground shadow under the feet (bot_02)
#   -FigureMargin 0.1 crops to the figure (first frames and the defeated pose) plus 10% of the height,
#       letting magic that fills the frame run past the edges (Google Flow clips: the sorceress)
#   -NoWhite erases pale whitish parts of the magic, which read as flat white patches in the game
#   -FillCores (with -NoWhite) fills a white core enclosed by the spell with its colour instead of a hole (the mage)
#   -ColorToAlpha un-blends pale magic outside the figure from white (white clear, pale blue a faint glow): the mage
#   -HotCores (with -ColorToAlpha) also un-blends warm glow and fills white-hot cores of fire: the conjurer
#   -BlackBg: the figure is on a plain black background (enemies from bot_03, art/enemies/PLAN.md)
#   -ReviveGlow un-blends the revive clip's aura of any colour from white (colour-to-alpha for that clip
#       only, the others are cut as before): the sorceress's violet aura
#
# Each clip folder holds frame_00..NN.png. The white background is removed by flood-filling from the
# image border through pixels close to the background colour, with a soft edge and the colour
# un-blended. Unlike tools/art/cut-sprite.ps1 (the straw dummy's hit), pale pixels don't let the fill
# through, so a pale glowing crystal stays. Every frame of every clip shares one crop, so the idle pose,
# the animations and the defeated image line up exactly.
param([string]$Avatar, [string]$Art = 'art\avatars', [string]$Prefix = 'avatar_', [int]$Height = 300, [int]$Cols = 8,
      [switch]$DefeatSheet, [switch]$NoIdle, [double]$PocketSeed = 18, [int]$PocketMin = 12, [switch]$Shadow,
      [double]$FigureMargin = 0, [switch]$NoWhite, [switch]$FillCores, [switch]$ColorToAlpha, [switch]$HotCores, [switch]$ReviveGlow, [switch]$BlackBg)
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
    // Colour distance from the background: <= Hard gone, >= Soft solid. On a black background (-BlackBg)
    // the dark robes lie close to it, so the edges are much tighter.
    static double Hard = 32, Soft = 84;
    static bool BlackBg = false;   // the figure is on black: protect its dark inside (see FigureInside)
    // Enclosed background pockets: patches seeded by pixels closer than PocketSeed to the background
    // colour, removed when at least PocketMin pixels (see Cut).
    static double PocketSeed = 18; static int PocketMin = 12;
    static bool Shadow = false;   // remove a pale ground shadow under the feet (see Cut)
    static bool NoWhite = false;   // erase pale whitish areas of magic (see Cut)
    static bool FillCores = false;   // with NoWhite, fill enclosed spell cores with the spell's colour
    static bool ColorToAlpha = false;   // un-blend pale magic from white instead of erasing it (see ToAlpha)
    static bool HotCores = false;   // with ColorToAlpha: warm glow too, and fill white-hot cores (fire)
    static bool[] Mask;   // where the figure stands in the current clip's first frame (ColorToAlpha)
    static bool GlowClip; // -ReviveGlow, while cutting the revive clip: any bright colour outside the figure is glow
    static bool ReviveClip; // while cutting the revive clip
    const int EdgeFade = 24;   // the revive's glow fades out over this many pixels at the clip's sides and top
    const double CoreRatio = 0.75;   // share of a pocket's surroundings that must be magic to count as a core
    const byte GlowAlpha = 110;   // opacity of the tint in white pockets inside magic (see Cut)
    const byte CoreAlpha = 210;   // opacity of a filled core (-FillCores)

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

    // -BlackBg: where the figure is, so the background fill stays out of its dark robe and shadows
    // (as dark as the black around it). Its clearly visible parts (rim light, skin, brass, the crystal:
    // further than SeenDist from the background) are joined over small gaps (closed by CloseR pixels),
    // and everything they enclose is figure too, unless an enclosed patch is large and almost exactly
    // the background colour (a real gap, between the staff and the robe, say).
    const double SeenDist = 19, BareDist = 12;
    const int CloseR = 10, BareMin = 400;

    static bool[] Grow(bool[] m, int W, int H, int r, bool grow) {
        // Square dilation (grow) or erosion of mask m by r pixels, as two 1-D passes.
        bool[] t = new bool[W * H], o = new bool[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            bool v = !grow;
            for (int d = -r; d <= r; d++) {
                int xx = x + d; bool s = xx >= 0 && xx < W ? m[y * W + xx] : !grow;
                if (grow ? s : !s) { v = grow; break; }
            }
            t[y * W + x] = v;
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            bool v = !grow;
            for (int d = -r; d <= r; d++) {
                int yy = y + d; bool s = yy >= 0 && yy < H ? t[yy * W + x] : !grow;
                if (grow ? s : !s) { v = grow; break; }
            }
            o[y * W + x] = v;
        }
        return o;
    }

    static bool[] FigureInside(double[] dist, int W, int H) {
        bool[] seen = new bool[W * H];
        for (int k = 0; k < W * H; k++) seen[k] = dist[k] >= SeenDist;
        bool[] closed = Grow(Grow(seen, W, H, CloseR, true), W, H, CloseR, false);
        // Outside: reached from the border without crossing the closed figure.
        bool[] outside = new bool[W * H];
        var q = new Queue<int>();
        for (int x = 0; x < W; x++) { q.Enqueue(x); q.Enqueue((H - 1) * W + x); }
        for (int y = 0; y < H; y++) { q.Enqueue(y * W); q.Enqueue(y * W + W - 1); }
        while (q.Count > 0) {
            int k = q.Dequeue();
            if (outside[k] || closed[k]) continue;
            outside[k] = true;
            int x = k % W, y = k / W;
            if (x > 0) q.Enqueue(k - 1); if (x < W - 1) q.Enqueue(k + 1);
            if (y > 0) q.Enqueue(k - W); if (y < H - 1) q.Enqueue(k + W);
        }
        bool[] inside = new bool[W * H];
        for (int k = 0; k < W * H; k++) inside[k] = !outside[k];
        // Large enclosed patches of bare background (not the closed figure itself) open up again.
        bool[] done = new bool[W * H];
        var comp = new List<int>();
        for (int s = 0; s < W * H; s++) {
            if (done[s] || !inside[s] || closed[s] || dist[s] >= BareDist) continue;
            comp.Clear(); q.Enqueue(s); done[s] = true;
            while (q.Count > 0) {
                int k = q.Dequeue(); comp.Add(k);
                int x = k % W, y = k / W;
                foreach (int n in new[] { x > 0 ? k - 1 : -1, x < W - 1 ? k + 1 : -1, y > 0 ? k - W : -1, y < H - 1 ? k + W : -1 })
                    if (n >= 0 && !done[n] && inside[n] && !closed[n] && dist[n] < BareDist) { done[n] = true; q.Enqueue(n); }
            }
            if (comp.Count >= BareMin) foreach (int k in comp) inside[k] = false;
        }
        return inside;
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
        // On black the figure's own shadows are as dark as the background: the fill must not get into it.
        bool[] inside = BlackBg ? FigureInside(dist, W, H) : null;
        var q = new Queue<int>();
        for (int x = 0; x < W; x++) { q.Enqueue(x); q.Enqueue((H - 1) * W + x); }
        for (int y = 0; y < H; y++) { q.Enqueue(y * W); q.Enqueue(y * W + W - 1); }
        while (q.Count > 0) {
            int k = q.Dequeue();
            if (reached[k] || dist[k] >= Soft || (inside != null && inside[k])) continue;
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
            if (reached[s] || seen[s] || dist[s] >= PocketSeed || PocketSeed <= 0) continue;
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
            double ratio;
            int tint = MagicTint(px, comp, seen, W, H, out ratio);
            // -NoWhite: see-through, except (with -FillCores) a hot core all but enclosed by the spell,
            // which is filled with the spell's own colour rather than left as a hole.
            bool fill = tint >= 0 && (!NoWhite || (FillCores && ratio >= CoreRatio));
            foreach (int k in comp) { reached[k] = true; if (fill) glow[k] = tint + 1; }
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
                o[i + 3] = FillCores ? CoreAlpha : GlowAlpha;
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
            e[i + 3] = FillCores ? CoreAlpha : GlowAlpha;
        }
        // With -NoWhite, pale whitish areas go: the white or lavender-white parts of magic (a swirl's
        // middle, an orb's rim, the tint in white pockets) that read as flat white patches in the game.
        // Saturated magic stays, and so does the figure (skin and robes are darker or more coloured).
        if (NoWhite) for (int k = 0; k < W * H; k++) {
            int i = k * 4;
            if (e[i + 3] == 0 || glow[k] != 0) continue;
            int mx = Math.Max(e[i], Math.Max(e[i + 1], e[i + 2])), mn = Math.Min(e[i], Math.Min(e[i + 1], e[i + 2]));
            if ((mn >= 185 && mx - mn <= 70) || (mn >= 150 && mx - mn <= 45)) e[i + 3] = 0;   // whitish, or light grey
            // The soft edge of the magic: half see-through and pale once un-blended from the white
            // background, it reads as a white rim; keep only edge pixels with real colour.
            else if (e[i + 3] < 250 && mx >= 120 && (mx - mn) * 100 < 40 * mx) e[i + 3] = 0;
            // A very light, faint-coloured glow (pale sky blue round a frost sphere) reads as a white halo
            // too; the figure's light parts (a grey beard) are darker than this.
            else if (mx >= 215 && (mx - mn) * 100 < 36 * mx) e[i + 3] = 0;
        }
        if (ColorToAlpha && Mask != null) {
            ToAlpha(e, px, W, H);
            if (HotCores) Dewhiten(e, W, H);
        }
        return e;
    }

    // True when a near-white pocket is the hot core of a spell, not background: most pixels around it
    // are bright and strongly coloured (violet glow, flames), while background trapped in the figure
    // is ringed by robe, hair or skin. Such a pocket stays (it shows as the spell's white core).
    // The tint for a pocket inside magic (see the pocket loop in Cut), as 0xRRGGBB in BGR byte order
    // (b | g << 8 | r << 16), or -1 when the pocket isn't inside magic.
    static int MagicTint(byte[] px, List<int> comp, bool[] inComp, int W, int H, out double ratio) {
        int ring = 0, magic = 0, thick = 0; long sb = 0, sg = 0, sr = 0;
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
                if (mx >= 180 && mx - mn >= 35) {
                    magic++; sb += px[i]; sg += px[i + 1]; sr += px[i + 2];
                    // Thick magic around it (a sphere's core), not a thin ring of runes: still magic 24 px further out.
                    int fx = x + dx * 12, fy = y + dy * 12;
                    if (fx >= 0 && fy >= 0 && fx < W && fy < H) {
                        int j = (fy * W + fx) * 4;
                        int fmx = Math.Max(px[j], Math.Max(px[j + 1], px[j + 2])), fmn = Math.Min(px[j], Math.Min(px[j + 1], px[j + 2]));
                        if (fmx >= 140 && fmx - fmn >= 35) thick++;
                    }
                }
            }
        }
        ratio = ring == 0 ? 0 : thick / (double)ring;   // the share of its surroundings that is thick magic
        if (ring == 0 || magic * 2 <= ring) return -1;
        int b = (int)(sb / magic), g = (int)(sg / magic), r = (int)(sr / magic);   // the magic's own colour
        if (FillCores) Saturate(ref b, ref g, ref r);
        return b | g << 8 | r << 16;
    }

    // The figure in a cut frame (its opaque pixels), grown by 4 px: kept as it is by ToAlpha.
    static bool[] FigureMask(byte[] e, int W, int H) {
        var m = new bool[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (e[(y * W + x) * 4 + 3] < 128) continue;
            for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < W && yy < H) m[yy * W + xx] = true;
            }
        }
        return m;
    }

    // -ColorToAlpha: outside the figure, every pale or bluish-bright pixel of the video is un-blended
    // from the white background with the least opacity that explains it (GIMP's colour-to-alpha):
    // white goes clear, pale blue becomes a faint pure-blue glow, strong blue stays strong. So frost
    // magic loses its white halo and patches without holes.
    static void ToAlpha(byte[] e, byte[] px, int W, int H) {
        for (int k = 0; k < W * H; k++) {
            int i = k * 4, b = px[i], g = px[i + 1], r = px[i + 2];
            int mx = Math.Max(b, Math.Max(g, r)), mn = Math.Min(b, Math.Min(g, r));
            // -HotCores (a dark figure): bright pixels are magic even over the body. -ReviveGlow: the
            // aura also covers where the figure will stand; bright, clearly blue or violet light there
            // is aura (skin, a red robe, black hair, a grey-blue beard and a white crystal never are).
            if (Mask[k] && !HotCores && !(GlowClip && mx >= 200 && b >= r && b - mn >= 30)) continue;
            bool warm = HotCores && r - b >= 60 && mx >= 170;   // fire: amber and orange glow too
            bool aura = GlowClip && mx >= 150 && mx - mn >= 40;   // -ReviveGlow: a bright aura of any colour
            if (!(mn >= 185 || (b - r >= 40 && mx >= 170) || warm || aura)) continue;
            // -HotCores: a clearly yellow pale pixel is fire's hot core (half see-through it reads as
            // olive on the dark background): keep it solid. The paler cream halo still fades out.
            // (Not in the revive: its pale gold is the staff appearing.)
            if (HotCores && !ReviveClip && mn >= 110 && r - b >= 95 && r >= 235 && g >= 200) { e[i] = (byte)b; e[i + 1] = (byte)g; e[i + 2] = (byte)r; e[i + 3] = 255; continue; }
            double a = (255 - mn) / 255.0;
            if (a < 0.08) { e[i + 3] = 0; continue; }
            e[i] = (byte)Math.Max(0, Math.Min(255, (b - (1 - a) * 255) / a));
            e[i + 1] = (byte)Math.Max(0, Math.Min(255, (g - (1 - a) * 255) / a));
            e[i + 2] = (byte)Math.Max(0, Math.Min(255, (r - (1 - a) * 255) / a));
            e[i + 3] = (byte)(a * 255);
        }
        // Not in the revive: there the glow hugging the figure and the staff appearing near the edge
        // would be taken for white-hot cores and filled with cream, and its fire has no such cores.
        if (HotCores && !ReviveClip) FillHotCores(e, px, W, H);
    }

    // -HotCores: a fire spell's white-hot centre went clear in ToAlpha and shows the dark game
    // background as a hole. It is filled in the fire's own colours (white or cream would read as a
    // white patch on the dark background):
    // A half see-through warm pixel surrounded by strong magic (in 6 of 8 directions within 48 px; the
    // frame edge counts when fire runs off it) takes that magic's colour, a quarter of the way to white:
    // a glowing core. (The pure white eye of a vortex is FillWhiteEyes'.)
    static void FillHotCores(byte[] e, byte[] px, int W, int H) {
        byte[] s = (byte[])e.Clone();
        int[] ddx = { 1, 1, 0, -1, -1, -1, 0, 1 }, ddy = { 0, 1, 1, 1, 0, -1, -1, -1 };
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int k = y * W + x, i = k * 4;
            if (Mask[k] && !HotCores) continue;   // any alpha: a pale core that ToAlpha made half see-through reads as olive
            if (s[i + 3] >= 200) continue;   // already solid: a half see-through pixel inside fire is its hot core
            if (px[i + 2] - px[i] < 20) continue;   // pure white in the video (background inside a rune ring), not a warm core
            int hits = 0, edge = 0, sb = 0, sg = 0, sr = 0;
            for (int d = 0; d < 8 && hits + edge + (8 - d) >= 6; d++) {
                for (int rr = 1; rr <= 48; rr++) {
                    int xx = x + ddx[d] * rr, yy = y + ddy[d] * rr;
                    if (xx < 0 || yy < 0 || xx >= W || yy >= H) { if (rr > 1) edge++; break; }   // fire running off the frame
                    int j = (yy * W + xx) * 4;
                    int mx = Math.Max(s[j], Math.Max(s[j + 1], s[j + 2])), mn = Math.Min(s[j], Math.Min(s[j + 1], s[j + 2]));
                    if (s[j + 3] >= 150 && mx >= 150 && mx - mn >= 60) { hits++; sb += s[j]; sg += s[j + 1]; sr += s[j + 2]; break; }
                }
            }
            if (hits < 3 || hits + edge < 6) continue;   // 6 of 8 directions: fire, or the frame edge beyond it
            e[i] = (byte)((sb / hits * 3 + 255) / 4); e[i + 1] = (byte)((sg / hits * 3 + 255) / 4); e[i + 2] = (byte)((sr / hits * 3 + 255) / 4);
            e[i + 3] = 235;
        }
        FillWhiteEyes(e, px, W, H);
    }

    // The white eye of a fire vortex (pure white in the video) went clear and shows the dark game
    // background as a hole. A small patch of see-through pixels that are pale in the video, walled in by
    // fire on (nearly) every side, becomes a bright yellow core. A large one (the empty middle of a
    // rune ring), one touching the frame's edge (the background) or one walled in by the figure (the gap
    // between an arm and the staff) stays clear.
    const int MaxEye = 6000;
    static void FillWhiteEyes(byte[] e, byte[] px, int W, int H) {
        var seen = new bool[W * H];
        var stack = new Stack<int>();
        var comp = new List<int>();
        int[] nx = { 1, -1, 0, 0 }, ny = { 0, 0, 1, -1 };
        for (int k0 = 0; k0 < W * H; k0++) {
            if (seen[k0] || !EyePixel(e, px, k0)) continue;
            comp.Clear();
            stack.Push(k0);
            seen[k0] = true;
            bool atEdge = false;
            int border = 0, fire = 0;
            while (stack.Count > 0) {
                int k = stack.Pop();
                comp.Add(k);
                int x = k % W, y = k / W;
                for (int d = 0; d < 4; d++) {
                    int xx = x + nx[d], yy = y + ny[d];
                    if (xx < 0 || yy < 0 || xx >= W || yy >= H) { atEdge = true; continue; }
                    int n = yy * W + xx;
                    if (seen[n]) continue;
                    if (EyePixel(e, px, n)) { seen[n] = true; stack.Push(n); continue; }
                    border++;
                    int j = n * 4;
                    if (e[j + 3] >= 120 && e[j + 2] - e[j] >= 60) fire++;   // warm and fairly solid: fire
                }
            }
            if (atEdge || comp.Count > MaxEye || fire * 10 < border * 8) continue;
            foreach (int k in comp) { int i = k * 4; e[i] = 110; e[i + 1] = 228; e[i + 2] = 255; e[i + 3] = 255; }   // BGR: bright yellow
        }
    }

    static bool EyePixel(byte[] e, byte[] px, int k) {
        int i = k * 4;
        return e[i + 3] < 200 && Math.Min(px[i], Math.Min(px[i + 1], px[i + 2])) >= 140;   // see-through, pale in the video
    }

    // -HotCores, last: on the dark game background any pale pixel left in the fire (a cream core or
    // rim, a beige crescent, a white streak) reads as a white patch. Pale warm pixels get the saturation
    // of fire (70%, same hue and brightness: cream turns yellow, beige orange). Grey (the blur of a
    // swinging staff against the white background, smoke, a glint) is un-blended from white as if over a
    // near-black: see-through dark, gone on the dark background; other pale tints fade out. Over the
    // figure itself (the first frame's pose) only clearly pale pixels change, so its skin, eyes and gold
    // trim stay as they are (the conjurer has no grey parts of his own).
    static void Dewhiten(byte[] e, int W, int H) {
        for (int k = 0; k < W * H; k++) {
            int i = k * 4;
            if (e[i + 3] == 0) continue;
            int b = e[i], g = e[i + 1], r = e[i + 2];
            int mx = Math.Max(b, Math.Max(g, r)), mn = Math.Min(b, Math.Min(g, r));
            bool onFigure = Mask != null && Mask[k];
            if (mx - mn <= 15 && mn >= (onFigure ? 100 : 70)) {
                double a = Math.Min(1.0, (255 - (r + g + b) / 3.0) / 230.0);
                e[i] = 25; e[i + 1] = 25; e[i + 2] = 25;
                e[i + 3] = (byte)(e[i + 3] * a);
                continue;
            }
            int pale = onFigure ? 160 : 100;   // how light a pixel must be to count as pale
            if (mn < pale) continue;
            if (r - b >= 25) {
                if (mx < 140 || (mx - mn) * 10 >= mx * 6) continue;   // already as saturated as fire
                int mn2 = mx * 3 / 10;
                double f = (double)(mx - mn2) / Math.Max(1, mx - mn);
                e[i] = (byte)(mn2 + (b - mn) * f);
                e[i + 1] = (byte)(mn2 + (g - mn) * f);
                e[i + 2] = (byte)(mn2 + (r - mn) * f);
            } else {
                double w = Math.Min(1.0, (mn - pale) / 60.0);   // how white it is
                e[i + 3] = (byte)(e[i + 3] * (1 - w));
            }
        }
    }

    // Pushes a colour away from grey (30% more saturated), so a filled core reads as the spell's
    // colour, not a grey-blue patch.
    static void Saturate(ref int b, ref int g, ref int r) {
        int l = (b + g + r) / 3;
        b = Math.Max(0, Math.Min(255, l + (b - l) * 13 / 10)); g = Math.Max(0, Math.Min(255, l + (g - l) * 13 / 10)); r = Math.Max(0, Math.Min(255, l + (r - l) * 13 / 10));
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
            // -FillCores: the spell's own colour (not lightened), so -NoWhite keeps it.
            if (hits < 7) continue;
            int cb = sb / hits, cg = sg / hits, cr = sr / hits;
            if (FillCores) Saturate(ref cb, ref cg, ref cr);
            fill.Add(FillCores ? new[] { k, cb, cg, cr } : new[] { k, (sb / hits + 255) / 2, (sg / hits + 255) / 2, (sr / hits + 255) / 2 });
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
    // The revive's glow, and the conjurer's fire, can reach the clip's sides and top (a crystal held near
    // the edge, a blast leaving the frame): fade it out there instead of cutting it off in a straight line.
    static void FadeEdges(byte[] e, int W, int H) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int d = Math.Min(y, Math.Min(x, W - 1 - x));
            if (d >= EdgeFade) continue;
            int i = (y * W + x) * 4;
            e[i + 3] = (byte)(e[i + 3] * d / EdgeFade);
        }
    }

    // reviveIndex: the clip that rises from the defeated pose to the idle one (or -1). It takes no part
    // in the crop (its ends are the defeated and idle poses, already framed), and with ColorToAlpha its
    // figure mask covers both ends, the kneeling and the standing figure.
    public static string Run(string[] dirs, string outIdle, string[] outSheets, string outDefeated, int defeatIndex, int reviveIndex, int height, int cols, double pocketSeed, int pocketMin, bool shadow, double figureMargin, bool noWhite, bool fillCores, bool colorToAlpha, bool hotCores, bool reviveGlow, double hard, double soft) {
        Hard = hard; Soft = soft; BlackBg = hard < 32;
        PocketSeed = pocketSeed; PocketMin = pocketMin; Shadow = shadow; NoWhite = noWhite; FillCores = fillCores; ColorToAlpha = colorToAlpha; HotCores = hotCores;
        var casts = new List<List<byte[]>>();
        int W = 0, H = 0;
        for (int di = 0; di < dirs.Length; di++) {
            string dir = dirs[di];
            // -ReviveGlow: the revive clip's aura is un-blended from white (ColorToAlpha), whatever its colour.
            GlowClip = reviveGlow && di == reviveIndex;
            ReviveClip = di == reviveIndex;
            ColorToAlpha = colorToAlpha || GlowClip;
            var frames = new List<byte[]>();
            Mask = null;
            string[] files = Directory.GetFiles(dir, "frame_*.png").OrderBy(f => f).ToArray();
            if (ColorToAlpha && di == reviveIndex) {
                bool[] both = null;
                foreach (string f in new[] { files[0], files[files.Length - 1] })
                    using (Bitmap b = new Bitmap(f)) {
                        W = b.Width; H = b.Height;
                        bool[] m = FigureMask(Cut(Read(b), W, H), W, H);
                        if (both == null) both = m; else for (int k = 0; k < m.Length; k++) both[k] |= m[k];
                    }
                Mask = both;
            }
            foreach (string f in files)
                using (Bitmap b = new Bitmap(f)) {
                    W = b.Width; H = b.Height;
                    byte[] cut = Cut(Read(b), W, H);
                    // -ColorToAlpha: the clip's first frame (the pose) marks where the figure is.
                    if (ColorToAlpha && frames.Count == 0 && Mask == null) { Mask = FigureMask(cut, W, H); cut = Cut(Read(b), W, H); }
                    if (ReviveClip || HotCores) FadeEdges(cut, W, H);   // the conjurer: fire runs off the frame
                    frames.Add(cut);
                }
            casts.Add(frames);
        }
        ReviveClip = false; GlowClip = false;
        int x0 = int.MaxValue, y0 = int.MaxValue, x1 = -1, y1 = -1;
        Rectangle crop;
        if (figureMargin > 0) {
            // Frame the figure, not its magic: bounds of each clip's first frame (the pose) and the
            // defeat's last frame, widened by figureMargin of the frame height; effects that reach
            // further (a bolt flying out, a blast filling the frame) are cut at the edges.
            for (int s = 0; s < casts.Count; s++) if (s != reviveIndex) Bounds(casts[s][0], W, H, ref x0, ref y0, ref x1, ref y1);
            if (defeatIndex >= 0) { var d = casts[defeatIndex]; Bounds(d[d.Count - 1], W, H, ref x0, ref y0, ref x1, ref y1); }
            int fm = (int)(H * figureMargin);
            crop = Rectangle.FromLTRB(Math.Max(0, x0 - fm), Math.Max(0, y0 - fm), Math.Min(W, x1 + fm + 1), Math.Min(H, y1 + 2));
        } else {
            for (int s = 0; s < casts.Count; s++) if (s != reviveIndex) foreach (byte[] f in casts[s]) Bounds(f, W, H, ref x0, ref y0, ref x1, ref y1);
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
$dirs = @(Get-ChildItem $anim -Directory | Where-Object { $_.Name -like 'cast*' -or $_.Name -eq 'defeat' -or $_.Name -eq 'revive' } | Sort-Object Name | ForEach-Object { $_.FullName })
$defeatIndex = [array]::IndexOf(@($dirs | ForEach-Object { Split-Path $_ -Leaf }), 'defeat')
$reviveIndex = [array]::IndexOf(@($dirs | ForEach-Object { Split-Path $_ -Leaf }), 'revive')
$sheets = @($dirs | ForEach-Object {
    $leaf = Split-Path $_ -Leaf
    if ($leaf -eq 'defeat' -and -not $DefeatSheet) { $null } else { Join-Path $res ("{0}_{1}.png" -f $base, $leaf) }
})
$idle = if ($NoIdle) { $null } else { Join-Path $res "$base.png" }
$defeated = if ($defeatIndex -ge 0) { Join-Path $res "${base}_defeated.png" } else { $null }
# -BlackBg: figures on a plain black background (enemies from bot_03): tighter edges.
# Enclosed dark patches are the robe's own deepest folds on black, not background, so no pockets.
if ($BlackBg) { $hardBg = 14; $softBg = 40; if (-not $PSBoundParameters.ContainsKey('PocketSeed')) { $PocketSeed = 0 } } else { $hardBg = 32; $softBg = 84 }
[AvatarCut]::Run($dirs, $idle, $sheets, $defeated, $defeatIndex, $reviveIndex, $Height, $Cols, $PocketSeed, $PocketMin, $Shadow.IsPresent, $FigureMargin, $NoWhite.IsPresent, $FillCores.IsPresent, $ColorToAlpha.IsPresent, $HotCores.IsPresent, $ReviveGlow.IsPresent, $hardBg, $softBg)
& (Join-Path $PSScriptRoot 'to-webp.ps1') -Files (@($idle, $defeated) + $sheets | Where-Object { $_ })
