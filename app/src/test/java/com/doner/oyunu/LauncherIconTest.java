package com.doner.oyunu;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

/** API 24/25 için düz (PNG) launcher ikonları: her yoğunlukta var ve doğru boyutta olmalı. */
public class LauncherIconTest {
    private static final String RES = "src/main/res/";

    private static int[] pngSize(File f) throws Exception {
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            byte[] sig = new byte[8];
            in.readFully(sig);
            assertEquals("PNG imzası", (byte) 0x89, sig[0]);
            assertEquals('P', sig[1]); assertEquals('N', sig[2]); assertEquals('G', sig[3]);
            in.readInt();              // IHDR uzunluğu
            assertEquals(0x49484452, in.readInt()); // "IHDR"
            return new int[]{in.readInt(), in.readInt()};
        }
    }

    @Test public void legacyLauncherIconsExistForEveryDensity() throws Exception {
        String[] dirs = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] px = {48, 72, 96, 144, 192};
        for (int i = 0; i < dirs.length; i++) {
            File f = new File(RES + "mipmap-" + dirs[i] + "/ic_launcher.png");
            assertTrue("eksik ikon: " + f, f.isFile());
            int[] wh = pngSize(f);
            assertEquals(f + " genişlik", px[i], wh[0]);
            assertEquals(f + " yükseklik", px[i], wh[1]);
        }
    }

    @Test public void adaptiveIconStillPresentAndManifestUsesMipmapLauncher() throws Exception {
        assertTrue(new File(RES + "mipmap-anydpi-v26/ic_launcher.xml").isFile());
        String manifest = new String(java.nio.file.Files.readAllBytes(new File("src/main/AndroidManifest.xml").toPath()), "UTF-8");
        assertTrue(manifest.contains("@mipmap/ic_launcher"));
    }

    @Test public void legacyIconIsNotTrivial() {
        // düz renkli boş bir PNG birkaç yüz bayta sıkışır; çizim içeren ikon daha büyüktür
        assertTrue(new File(RES + "mipmap-xxxhdpi/ic_launcher.png").length() > 400);
    }
}
