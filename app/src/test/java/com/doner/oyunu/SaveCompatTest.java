package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

/** v0.4 kayıtları v0.5'te yüklenmeli; yeni alanlar varsayılan. */
public class SaveCompatTest {
    private static final String V04 = "v=1\nday=9\nbestDay=11\nmoney=8123\nstars=4.2\nstock.et_doner=12\nstock.ekmek=7\n"
            + "up.cirak=2\nup.tabela=1\ntempo=1.2\nspeech=1\nspeechRate=1.4\nvibration=0\nmusic=0.3\nsfx=0.9\ntutorial=1\n";

    @Test public void v04SaveLoadsWithDefaultsForNewFields() {
        SaveData s = SaveData.parse(V04);
        assertEquals(9, s.day);
        assertEquals(11, s.bestDay);
        assertEquals(8123, s.money);
        assertEquals(4.2, s.stars, 1e-9);
        assertEquals(12, s.inventory.get("et_doner"));
        assertEquals(2, s.level("cirak"));
        assertEquals(1.2, s.tempo, 1e-9);
        assertTrue(s.tutorialDone);
        assertFalse(s.vibration);
        // yeni alanlar
        assertTrue("çırak duyuruları varsayılan açık", s.apprenticeAnnounce);
        assertEquals(0, s.readyDrinks);
        // yeni malzemelerin stoğu 0 (market alır)
        assertEquals(0, s.inventory.get("ketcap"));
    }

    @Test public void v04SaveStartsAGameDayAndPlaysWithoutCrash() {
        SaveData s = SaveData.parse(V04);
        Shop.buyRecommended(s);
        GameEngine e = new GameEngine(3, s);
        new TestBot(1.0f, true, true).playDay(e);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertEquals(10, s.day);
    }

    @Test public void newFieldsRoundTrip() {
        SaveData s = SaveData.newGame();
        s.apprenticeAnnounce = false;
        s.readyDrinks = 6;
        String text = s.serialize();
        assertTrue(text.startsWith("v=2"));
        SaveData t = SaveData.parse(text);
        assertFalse(t.apprenticeAnnounce);
        assertEquals(6, t.readyDrinks);
        assertEquals(text, t.serialize());
        // copy() de yeni alanları taşır
        SaveData c = s.copy();
        assertFalse(c.apprenticeAnnounce);
        assertEquals(6, c.readyDrinks);
    }

    @Test public void badNewFieldsAreTolerated() {
        SaveData s = SaveData.parse("readyDrinks=abc\napprenticeAnnounce=x\nday=3\n");
        assertEquals(0, s.readyDrinks);
        assertFalse(s.apprenticeAnnounce); // "1" değil -> kapalı
        assertEquals(3, s.day);
        assertEquals(40, SaveData.parse("readyDrinks=99999\n").readyDrinks);
        assertEquals(0, SaveData.parse("readyDrinks=-5\n").readyDrinks);
    }

    @Test public void readyDrinksCarryOverToNextDay() {
        SaveData s = SaveData.newGame();
        s.day = 3;
        s.stars = 5.0;
        Shop.buyRecommended(s);
        GameEngine e = new GameEngine(5, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepDrink());
        for (int i = 0; i < 100; i++) e.tick(0.1f);
        assertEquals(4, s.readyDrinks);
        e.startDay();
        e.tick(2000f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertEquals("içecek gün sonunda atılmaz", 4, s.readyDrinks);
        SaveData next = SaveData.parse(s.serialize());
        assertEquals(4, next.readyDrinks);
    }
}
