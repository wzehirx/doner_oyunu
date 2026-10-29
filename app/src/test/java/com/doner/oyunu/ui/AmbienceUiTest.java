package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.content.Context;

import com.doner.oyunu.audio.SoundManager;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.SaveData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** v0.9 madde 3: dükkân ambiyansı yalnızca gün ekranında, ayrı ses ayarı, duraklat/arka plan/ducking kuralları. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class AmbienceUiTest {
    @Test public void ambienceFileExistsInRaw() {
        Context c = RuntimeEnvironment.getApplication();
        assertTrue(c.getResources().getIdentifier("amb_shop", "raw", c.getPackageName()) != 0);
        assertFalse("ambiyans SoundPool'a yüklenmez (büyük dosya)", java.util.Arrays.asList(SoundManager.SFX).contains("amb_shop"));
    }

    @Test public void playsOnlyDuringTheDayScreenAndFollowsPauseAndLifecycle() {
        UiHarness h = new UiHarness(false, FlowMode.DENGELI);
        SoundManager s = h.act.sound;
        assertNull("ana menüde yok", s.currentAmbience());
        h.click("Yeni oyun"); h.settle();
        assertNull("brifingde yok", s.currentAmbience());
        h.click("Markete git"); h.settle();
        assertNull("markette yok", s.currentAmbience());
        h.click("Güne başla"); h.settle();
        assertEquals("hazırlık aşamasında çalar", "amb_shop", s.currentAmbience());
        assertTrue(s.ambienceShouldPlay());
        h.skipPrep();
        assertEquals("gün içinde çalar", "amb_shop", s.currentAmbience());
        assertTrue(s.ambienceShouldPlay());
        h.click("Duraklat"); h.run(100);
        assertFalse("duraklatınca susar", s.ambienceShouldPlay());
        h.click("Devam et"); h.run(100);
        assertTrue("devam edince sürer", s.ambienceShouldPlay());
        h.controller.pause();
        assertFalse("arka planda susar", s.ambienceShouldPlay());
        h.controller.resume();
        assertTrue(s.ambienceShouldPlay() || s.currentAmbience() != null);
        h.act.show(new MenuScreen(h.act)); h.settle();
        assertNull("gün dışı ekranda durur", s.currentAmbience());
        assertFalse(s.ambienceShouldPlay());
    }

    @Test public void volumeIsIndependentOfMusicAndDucksWhileSpeaking() {
        Context c = RuntimeEnvironment.getApplication();
        SoundManager s = new SoundManager(c);
        s.setMusicVolume(0.9f);
        s.setAmbienceVolume(0.35f);
        assertEquals(0.35f, s.ambienceOutputVolume(), 1e-5f);
        s.setMusicVolume(0.1f);
        assertEquals("müzik ayarından bağımsız", 0.35f, s.ambienceOutputVolume(), 1e-5f);
        s.setSpeechDucking(true);
        assertEquals("konuşurken kısılır", 0.35f * SoundManager.DUCK_FACTOR, s.ambienceOutputVolume(), 1e-5f);
        s.setSpeechDucking(false);
        s.setAmbienceVolume(7f);
        assertEquals(1f, s.ambienceOutputVolume(), 1e-5f);
        s.playAmbience("amb_shop");
        s.playAmbience("yok_boyle_bir_ses");   // dosya yok: sessizce atlanır
        s.setAmbienceHold(true); s.setAmbienceHold(false);
        s.pause(); s.resume();
        s.stopAmbience();
        assertNull(s.currentAmbience());
        s.release();
        s.playAmbience("amb_shop");            // release sonrası çökmez
    }

    @Test public void settingsHasAmbienceStepperThatPersistsAndSaveIsBackwardCompatible() {
        assertEquals(0.35, SaveData.parse("v=4\nday=3\n").ambienceVolume, 1e-9);
        assertEquals("yeni oyun", 0.35, SaveData.newGame().ambienceVolume, 1e-9);
        SaveData s = SaveData.newGame();
        s.ambienceVolume = 0.2;
        assertEquals(0.2, SaveData.parse(s.serialize()).ambienceVolume, 1e-9);
        assertEquals("bozuk değer aralığa çekilir", 1.0, SaveData.parse("ambience=5\n").ambienceVolume, 0);
        assertEquals(0.35, SaveData.parse("ambience=abc\n").ambienceVolume, 1e-9);
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        assertNotNull(h.findText("Ambiyans sesi"));
        h.click("Ambiyans sesi azalt");
        assertEquals(0.30, h.act.save.ambienceVolume, 1e-9);
        for (int i = 0; i < 10; i++) h.click("Ambiyans sesi azalt");
        assertEquals("sıfır = kapalı", 0.0, h.act.save.ambienceVolume, 1e-9);
        assertNotNull(h.findText("Ambiyans sesi: kapalı"));
        h.click("Ambiyans sesi artır");
        assertEquals(0.05, h.act.save.ambienceVolume, 1e-9);
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        assertEquals("yeni oyun ayarı korur", 0.05, h.act.save.ambienceVolume, 1e-9);
        assertEquals(0.05f, h.act.sound.ambienceOutputVolume(), 1e-5f);
        A11yAudit.run(h, "x");
    }
}
