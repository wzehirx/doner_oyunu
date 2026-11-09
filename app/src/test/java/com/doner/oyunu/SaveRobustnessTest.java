package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.SaveData;

import org.junit.Test;

/** Bozuk/kötü niyetli kayıt metinleri oyunu kilitlememeli (NaN/Infinity/aşırı değerler). */
public class SaveRobustnessTest {

    @Test public void nanAndInfinityAreRejected() {
        // v2.5: ilerleme alanları yalnızca bu dönemin kaydında okunur (dönemsiz kayıt sıfırlanır)
        SaveData s = SaveData.parse(SaveCompatTest.current("stars=NaN\ntempo=NaN\nspeechRate=NaN\nmusic=NaN\nsfx=Infinity\nmoney=5\nday=2\n"));
        assertFalse("stars NaN kalmamalı", Double.isNaN(s.stars));
        assertFalse("tempo NaN kalmamalı", Double.isNaN(s.tempo));
        assertFalse("speechRate NaN kalmamalı", Double.isNaN(s.speechRate));
        assertFalse("music NaN kalmamalı", Double.isNaN(s.musicVolume));
        assertTrue(s.sfxVolume >= 0 && s.sfxVolume <= 1);
        assertTrue(s.stars >= 1.0 && s.stars <= 5.0);
        assertTrue(s.tempo >= 0.6 && s.tempo <= 1.6);
        assertEquals(2, s.day);
    }

    @Test public void nanSaveStillProducesCustomers() {
        SaveData s = SaveData.parse("stars=NaN\ntempo=NaN\n");
        GameEngine e = new GameEngine(1, s);
        e.tick(60f);
        assertTrue("NaN kayıtla bile müşteri gelmeli", e.getOrders().size() + e.getSummary().delivered + e.getSummary().lost > 0);
    }

    @Test public void extremeNumbersAreClamped() {
        SaveData s = SaveData.parse(SaveCompatTest.current("day=99999999999\nmoney=99999999999\nbestDay=-4\nstock.et_doner=-5\nup.cirak=99\n"));
        assertTrue(s.day >= 1);
        assertTrue(s.money >= 0);
        assertTrue(s.bestDay >= s.day || s.bestDay >= 1);
        assertTrue(s.inventory.get("et_doner") >= 0);
        assertTrue(s.level("cirak") <= 5); // v2.5: çırak 5 seviye
    }

    /** v2.5 kayıt bölümleri (fiyat, fatura, kredi, kredi notu) bozuk değerleri tolere eder. */
    @Test public void brokenFinanceLinesAreSkipped() {
        SaveData s = SaveData.parse(SaveCompatTest.current("day=5\nprice.ekmek_arasi=abc\nprice.yok=120\nprice.ayran=999\n"
                + "bill.3=elektrik,x,1,2,0\nbill.4=uzay,100,1,3,0\nbill.5=kira,700,2,4,0\nloan.9=1,2\nloan.10=1000,80,10,3,1080,0,0,10,0,0\n"
                + "credit=500\nwageDebt=-3\nsalesTax=nan\nfinId=2\n"));
        assertEquals(5, s.day);
        assertEquals(100, com.doner.oyunu.engine.Pricing.percent(s, "ekmek_arasi"));
        assertEquals("sınırlanır", 150, com.doner.oyunu.engine.Pricing.percent(s, "ayran"));
        assertFalse(s.prices.containsKey("yok"));
        assertEquals(1, s.bills.size());
        assertEquals(1, s.loans.size());
        assertEquals(100, s.creditScore);
        assertEquals(0, s.wageDebt);
        assertTrue("kimlik sayacı mevcut kayıtların üstünde", s.nextFinanceId > 10);
    }

    @Test public void serializeParseRoundTripKeepsSettings() {
        SaveData s = SaveData.newGame();
        s.setTempo(0.7); s.speechRate = 1.3; s.vibration = false; s.musicVolume = 0.2; s.sfxVolume = 0.0; s.tutorialDone = true;
        SaveData r = SaveData.parse(s.serialize());
        assertEquals(s.serialize(), r.serialize());
    }
}
