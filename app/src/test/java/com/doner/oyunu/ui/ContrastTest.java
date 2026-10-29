package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Theme renkleri için WCAG 2.1 kontrast oranı (metin AA >= 4.5:1, büyük metin/bileşen >= 3:1). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ContrastTest {
    static double lum(int c) {
        double[] v = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    static double ratio(int a, int b) {
        double la = lum(a), lb = lum(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** fg'yi alpha ile bg üstüne karıştırır (View.setAlpha benzetimi). */
    static int blend(int fg, int bg, float a) {
        return Color.rgb(Math.round(Color.red(fg) * a + Color.red(bg) * (1 - a)),
                Math.round(Color.green(fg) * a + Color.green(bg) * (1 - a)),
                Math.round(Color.blue(fg) * a + Color.blue(bg) * (1 - a)));
    }

    private void aa(String name, int fg, int bg) {
        double r = ratio(fg, bg);
        System.out.println("CONTRAST " + name + " = " + String.format("%.2f", r));
        assertTrue(name + " kontrast " + r + " < 4.5", r >= 4.5);
    }

    @Test public void normalTextPairsMeetAA() {
        int[] surfaces = {Theme.BG_TOP, Theme.BG_MID, Theme.BG_BOTTOM, Theme.CARD, Theme.CARD_HI};
        for (int s : surfaces) {
            aa("CREAM/" + Integer.toHexString(s), Theme.CREAM, s);
            aa("GOLD/" + Integer.toHexString(s), Theme.GOLD, s);
            aa("MUTED/" + Integer.toHexString(s), Theme.MUTED, s);
        }
        aa("GREEN/CARD", Theme.GREEN, Theme.CARD);
        // düğme gradyanları (PRIMARY: ORANGE->ORANGE_DEEP, GOLD: GOLD->ORANGE), koyu metin
        aa("DARK/ORANGE", Theme.DARK, Theme.ORANGE);
        aa("DARK/ORANGE_DEEP", Theme.DARK, Theme.ORANGE_DEEP);
        aa("DARK/GOLD", Theme.DARK, Theme.GOLD);
        // basılı hâl (darken 0.8): geçici durum, en az 3:1
        assertTrue("basılı PRIMARY", ratio(Theme.DARK, Theme.darken(Theme.ORANGE_DEEP, 0.8f)) >= 3.0);
        // başarısız gün başlığı
        aa("RED/BG_TOP", Theme.RED, Theme.BG_TOP);
        aa("RED/BG_BOTTOM", Theme.RED, Theme.BG_BOTTOM);
    }

    @Test public void nonTextBoundariesMeet3to1() {
        double r = ratio(Theme.STROKE, Theme.CARD);
        System.out.println("CONTRAST STROKE/CARD = " + r);
        assertTrue("STROKE/CARD " + r, r >= 3.0);
        assertTrue(ratio(Theme.GOLD, Theme.CARD) >= 3.0);
        assertTrue(ratio(Theme.RED, Theme.CARD) >= 3.0);
    }

    /** Ui.dim(): soluk (alpha 0.6) gösterilen metinler. Bilgi taşıyan metin AA'yı korumalı. */
    @Test public void dimmedStatesStillReadable() {
        float a = 0.7f; // Ui.dim()
        int bgMid = Theme.BG_MID;
        // kullanılmayan istasyon: kart üstünde MUTED ve CREAM metin
        double muted = ratio(blend(Theme.MUTED, bgMid, a), blend(Theme.CARD, bgMid, a));
        double cream = ratio(blend(Theme.CREAM, bgMid, a), blend(Theme.CARD, bgMid, a));
        // pasif "Teslim et" (GOLD düğme) ve pasif "Önerilen malzemeleri al" (PRIMARY düğme)
        double deliver = ratio(blend(Theme.DARK, bgMid, a), blend(Theme.GOLD, bgMid, a));
        double primaryDeep = ratio(blend(Theme.DARK, bgMid, a), blend(Theme.ORANGE_DEEP, bgMid, a));
        System.out.println("CONTRAST dim muted=" + muted + " cream=" + cream + " deliver=" + deliver + " primary=" + primaryDeep);
        assertTrue("soluk CREAM", cream >= 4.5);
        assertTrue("soluk Teslim et", deliver >= 4.5);
        assertTrue("soluk MUTED/CARD (istasyon 'Boş' yazısı) " + muted, muted >= 4.5);
        // Pasif PRIMARY düğme (stok yeterliyken "Önerilen malzemeleri al"): WCAG'de devre dışı bileşenler muaf; yine de >= 3:1 beklenir.
        assertTrue("soluk PRIMARY düğme " + primaryDeep, primaryDeep >= 3.0);
    }
}
