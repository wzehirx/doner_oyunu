package com.doner.oyunu.audio;

import java.io.IOException;
import java.io.InputStream;

/**
 * Ogg Vorbis dosyasının süresini (ms) saf Java ile okur: kimlik başlığındaki örnekleme hızı ve son sayfanın granül konumu.
 * SoundPool süre bildirmediği için ses havuzu (varyant zinciri, üst üste binme denetimi) bunu kullanır. Okunamazsa 0.
 */
final class OggInfo {
    private OggInfo() {}

    static long durationMs(InputStream in) {
        if (in == null) return 0L;
        try {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return durationMs(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            return 0L;
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    static long durationMs(byte[] d) {
        if (d == null || d.length < 64) return 0L;
        long rate = 0;
        // kimlik başlığı: 0x01 "vorbis" sürüm(4) kanal(1) örnekleme hızı(4, LE)
        for (int i = 0; i + 16 < d.length && i < 4096; i++) {
            if (d[i] == 1 && d[i + 1] == 'v' && d[i + 2] == 'o' && d[i + 3] == 'r' && d[i + 4] == 'b' && d[i + 5] == 'i' && d[i + 6] == 's') {
                rate = le(d, i + 12, 4);
                break;
            }
        }
        if (rate <= 0) return 0L;
        for (int i = d.length - 14; i >= 0; i--) {
            if (d[i] == 'O' && d[i + 1] == 'g' && d[i + 2] == 'g' && d[i + 3] == 'S') {
                long granule = le(d, i + 6, 8);
                if (granule <= 0) return 0L;
                return granule * 1000L / rate;
            }
        }
        return 0L;
    }

    private static long le(byte[] d, int off, int len) {
        long v = 0;
        for (int k = len - 1; k >= 0; k--) v = (v << 8) | (d[off + k] & 0xFFL);
        return v;
    }
}
