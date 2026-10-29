package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.widget.Switch;

import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.SaveData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class SettingsShopTest {

    private SaveData stored() {
        String s = RuntimeEnvironment.getApplication().getSharedPreferences("doner_oyunu", Context.MODE_PRIVATE).getString("save", null);
        return SaveData.parse(s);
    }

    @Test public void settingsSteppersSwitchesPersistAndClamp() {
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        assertTrue(h.screen() instanceof SettingsScreen);
        double m0 = h.act.save.musicVolume;
        h.click("Müzik sesi azalt");
        assertEquals(m0 - 0.1, h.act.save.musicVolume, 1e-6);
        assertEquals(m0 - 0.1, stored().musicVolume, 1e-6);
        for (int i = 0; i < 12; i++) h.click("Müzik sesi azalt");
        assertEquals(0.0, h.act.save.musicVolume, 1e-9);
        for (int i = 0; i < 15; i++) h.click("Müzik sesi artır");
        assertEquals(1.0, h.act.save.musicVolume, 1e-9);
        for (int i = 0; i < 15; i++) h.click("Efekt sesi azalt");
        assertEquals(0.0, h.act.save.sfxVolume, 1e-9);
        h.click("Sesli anlatımı dene"); // sfx 0 iken de çökmemeli
        for (int i = 0; i < 20; i++) h.click("Anlatım hızı artır");
        assertEquals(2.0, h.act.save.speechRate, 1e-9);
        for (int i = 0; i < 20; i++) h.click("Oyun temposu azalt");
        assertEquals(0.6, h.act.save.tempo, 1e-9);
        for (int i = 0; i < 20; i++) h.click("Oyun temposu artır");
        assertEquals(1.6, h.act.save.tempo, 1e-9);
        assertEquals(1.6, stored().tempo, 1e-9);
        // anahtarlar
        Switch speech = null, vib = null;
        for (View v : UiHarness.visibleViews(h.root())) if (v instanceof Switch) {
            String t = ((Switch) v).getText().toString();
            if (t.equals("Sesli anlatım")) speech = (Switch) v;
            if (t.equals("Titreşim")) vib = (Switch) v;
        }
        assertNotNull(speech); assertNotNull(vib);
        assertTrue(speech.isChecked());
        speech.performClick();
        assertFalse(h.act.save.speechOn);
        assertFalse(stored().speechOn);
        assertFalse(h.act.speaker.isEnabled());
        vib.performClick();
        assertFalse(h.act.save.vibration);
        speech.performClick();
        assertTrue(h.act.speaker.isEnabled());
        // ayarlar yeni oyunda korunur
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        assertEquals(1.6, h.act.save.tempo, 1e-9);
        assertFalse(h.act.save.vibration);
    }

    @Test public void shopBuyIngredientsUpgradesAndPersist() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Markete git"); h.settle();
        int money0 = h.act.save.money;
        Ingredient ing = Catalog.ingredients().get(0);
        int stock0 = h.act.save.inventory.get(ing.id);
        h.click(ing.name + " al");   // "+1" düğmesi: "1 <ad> al, N lira"
        assertEquals(Math.min(stock0 + 1, 30), h.act.save.inventory.get(ing.id));
        // stok kapasitesine kadar al; sonra dolu uyarısı, para düşmez
        for (int i = 0; i < 6; i++) h.click("10 " + ing.name + " al");
        int full = h.act.save.inventory.get(ing.id);
        assertTrue(full <= 30);
        int moneyBefore = h.act.save.money;
        h.click("10 " + ing.name + " al");
        assertEquals("Dolu stokta para harcanmamalı", moneyBefore, h.act.save.money);
        assertEquals(full, h.act.save.inventory.get(ing.id));
        // önerilen
        h.click("Önerilen malzemeleri al");
        assertTrue(h.act.save.money <= moneyBefore);
        assertEquals("Kayıt market işleminden sonra diske yazılmalı", h.act.save.money, stored().money);

        // yükseltmeler: gün 1'de kilitli
        h.click("Mutfak aletleri"); h.settle();
        int m = h.act.save.money;
        h.click("Satın al: Çırak");
        assertEquals("Kilitli yükseltme satın alınamamalı", m, h.act.save.money);
        assertEquals(0, h.act.save.level("cirak"));
        // gün 3'te açılır, para ver
        h.act.save.day = 3; h.act.save.money = 50_000;
        h.click("Restoran"); h.settle(); h.click("Mutfak aletleri"); h.settle();
        h.click("Satın al: Çırak");
        assertEquals(1, h.act.save.level("cirak"));
        assertEquals(50_000 - 3000, h.act.save.money);
        assertEquals(1, stored().level("cirak"));
        // yetersiz para
        h.act.save.money = 10;
        h.click("Restoran"); h.settle(); h.click("Mutfak aletleri"); h.settle();
        h.click("Satın al: Ekmek ısıtıcı");
        assertEquals(10, h.act.save.money);
        assertEquals(0, h.act.save.level("ekmek_isitici"));
        // Geri -> brifing, Güne başla
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
    }

    @Test public void helpReadAllWithNoTtsDoesNotCrash() {
        UiHarness h = new UiHarness(false);
        h.click("Nasıl oynanır"); h.settle();
        h.click("Hepsini sesli oku");
        h.idle(5000);
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof MenuScreen);
    }
}
