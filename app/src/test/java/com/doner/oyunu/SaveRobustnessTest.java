package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.SaveData;

import org.junit.Test;

/** Bozuk/kötü niyetli kayıt metinleri oyunu kilitlememeli (NaN/Infinity/aşırı değerler). */
public class SaveRobustnessTest {

    @Test public void nanAndInfinityAreRejected() {
        SaveData s = SaveData.parse("stars=NaN\ntempo=NaN\nspeechRate=NaN\nmusic=NaN\nsfx=Infinity\nmoney=5\nday=2\n");
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
        SaveData s = SaveData.parse("day=99999999999\nmoney=99999999999\nbestDay=-4\nstock.et_doner=-5\nup.cirak=99\n");
        assertTrue(s.day >= 1);
        assertTrue(s.money >= 0);
        assertTrue(s.bestDay >= s.day || s.bestDay >= 1);
        assertTrue(s.inventory.get("et_doner") >= 0);
        assertTrue(s.level("cirak") <= 3);
    }

    @Test public void serializeParseRoundTripKeepsSettings() {
        SaveData s = SaveData.newGame();
        s.setTempo(0.7); s.speechRate = 1.3; s.vibration = false; s.musicVolume = 0.2; s.sfxVolume = 0.0; s.tutorialDone = true;
        SaveData r = SaveData.parse(s.serialize());
        assertEquals(s.serialize(), r.serialize());
    }
}
