# Turns a sound-pack WAV (any PCM 16/24/32-bit or float, any rate, mono or stereo) into a small game
# sound: mono, 22050 Hz, 16-bit, leading silence trimmed, at most -MaxSeconds long with a short fade-out,
# peak-normalised to -Gain (0..1 of full scale). Android's SoundPool plays these WAVs directly.
#
#   wav-sfx.ps1 -In "Fire Spell Impact 2.wav" -Out app/src/main/res/raw/sfx_fire.wav [-MaxSeconds 1.2] [-Gain 0.9]
#   wav-sfx.ps1 -In x.wav -Info      prints format, length and peak only
param([string]$In, [string]$Out, [double]$MaxSeconds = 1.2, [double]$Gain = 0.9, [switch]$Info)
Add-Type -TypeDefinition @"
using System;
using System.IO;
public static class Sfx {
    public static float[] Read(string path, out int rate, out string fmt) {
        byte[] b = File.ReadAllBytes(path);
        int pos = 12, ch = 0, bits = 0, tag = 0; rate = 0;
        int dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.Length) {
            string id = System.Text.Encoding.ASCII.GetString(b, pos, 4);
            int sz = BitConverter.ToInt32(b, pos + 4);
            if (id == "fmt ") {
                tag = BitConverter.ToInt16(b, pos + 8); ch = BitConverter.ToInt16(b, pos + 10);
                rate = BitConverter.ToInt32(b, pos + 12); bits = BitConverter.ToInt16(b, pos + 22);
                if (tag == -2 && sz >= 40) tag = BitConverter.ToInt16(b, pos + 32); // WAVE_FORMAT_EXTENSIBLE
            } else if (id == "data") { dataOff = pos + 8; dataLen = Math.Min(sz, b.Length - dataOff); break; }
            pos += 8 + sz + (sz & 1);
        }
        if (dataOff < 0 || ch == 0) throw new Exception("no fmt/data in " + path);
        int bps = bits / 8, frames = dataLen / (bps * ch);
        float[] mono = new float[frames];
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            for (int c = 0; c < ch; c++) {
                int o = dataOff + (f * ch + c) * bps; double v;
                if (tag == 3 && bits == 32) v = BitConverter.ToSingle(b, o);
                else if (bits == 16) v = BitConverter.ToInt16(b, o) / 32768.0;
                else if (bits == 24) v = ((b[o] | (b[o + 1] << 8) | (b[o + 2] << 16)) << 8 >> 8) / 8388608.0;
                else if (bits == 32) v = BitConverter.ToInt32(b, o) / 2147483648.0;
                else v = (b[o] - 128) / 128.0;
                sum += v;
            }
            mono[f] = (float)(sum / ch);
        }
        fmt = String.Format("{0}ch {1}Hz {2}bit tag{3}", ch, rate, bits, tag);
        return mono;
    }

    public static double Peak(float[] s) { double p = 0; foreach (float v in s) p = Math.Max(p, Math.Abs(v)); return p; }

    public static string Convert(string inPath, string outPath, double maxSec, double gain) {
        int rate; string fmt;
        float[] s = Read(inPath, out rate, out fmt);
        const int R = 22050;
        // Low-pass by averaging over the step, then sample every step (simple and clean enough for SFX).
        double step = rate / (double)R;
        int n = (int)(s.Length / step);
        float[] r = new float[n];
        int win = Math.Max(1, (int)Math.Round(step));
        for (int i = 0; i < n; i++) {
            int c = (int)(i * step); double sum = 0; int k = 0;
            for (int j = c; j < Math.Min(s.Length, c + win); j++) { sum += s[j]; k++; }
            r[i] = (float)(sum / Math.Max(1, k));
        }
        double peak = Peak(r);
        // Trim leading silence (below 2% of the peak).
        int start = 0; while (start < n && Math.Abs(r[start]) < 0.02 * peak) start++;
        start = Math.Max(0, start - (int)(0.005 * R));
        int len = Math.Min(n - start, (int)(maxSec * R));
        // Trim trailing silence too.
        while (len > 1 && Math.Abs(r[start + len - 1]) < 0.01 * peak) len--;
        int fade = Math.Min(len, (int)(0.12 * R));
        double scale = peak > 0 ? gain / peak : 1;
        short[] o = new short[len];
        for (int i = 0; i < len; i++) {
            double v = r[start + i] * scale;
            if (i >= len - fade) v *= (len - i) / (double)fade;
            o[i] = (short)Math.Max(-32767, Math.Min(32767, v * 32767));
        }
        using (BinaryWriter w = new BinaryWriter(File.Create(outPath))) {
            w.Write(System.Text.Encoding.ASCII.GetBytes("RIFF")); w.Write(36 + len * 2);
            w.Write(System.Text.Encoding.ASCII.GetBytes("WAVEfmt ")); w.Write(16); w.Write((short)1); w.Write((short)1);
            w.Write(R); w.Write(R * 2); w.Write((short)2); w.Write((short)16);
            w.Write(System.Text.Encoding.ASCII.GetBytes("data")); w.Write(len * 2);
            foreach (short v in o) w.Write(v);
        }
        return String.Format("{0} -> {1:N2}s mono 22050 Hz ({2:N0} KB); source {3}, {4:N2}s", Path.GetFileName(inPath), len / (double)R, (44 + len * 2) / 1024.0, fmt, s.Length / (double)rate);
    }

    public static string Describe(string inPath) {
        int rate; string fmt; float[] s = Read(inPath, out rate, out fmt);
        return String.Format("{0}: {1}, {2:N2}s, peak {3:N2}", Path.GetFileName(inPath), fmt, s.Length / (double)rate, Peak(s));
    }
}
"@
if ($Info) { [Sfx]::Describe((Resolve-Path $In).Path) } else { [Sfx]::Convert((Resolve-Path $In).Path, [IO.Path]::GetFullPath($Out), $MaxSeconds, $Gain) }
