package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

public class SimulationTest {

    @Test public void botWithUpgradesPlaysDays1To40() {
        Campaign c = new Campaign();
        int failedDay = c.run(40, 1, 0.8f, true, 3);
        if (failedDay != -1) fail("Yükseltmeli bot gün " + failedDay + " geçemedi: " + c.results.get(c.results.size() - 1));
        assertEquals(40, c.attemptsPerDay.size());
        assertEquals(41, c.save.day);
        // ekonomi sağlıklı: son günlerde de para pozitif ve çoğu gün ilk denemede geçilir
        int firstTry = 0;
        for (int a : c.attemptsPerDay) if (a == 1) firstTry++;
        assertTrue("çoğu gün ilk denemede geçilmeli: " + firstTry, firstTry >= 32);
        assertTrue(c.save.money > 0);
    }

    @Test public void botWithoutUpgradesEventuallyFails() {
        Campaign c = new Campaign();
        int failedDay = c.run(40, 1, 0.8f, false, 3);
        assertTrue("yükseltmesiz bot 40. güne kadar takılmalı", failedDay > 0);
        assertTrue("ilk günler rahat olmalı", failedDay > 10);
    }

    @Test public void dayOneIsComfortableForSlowPlayer() {
        for (long seed = 1; seed <= 10; seed++) {
            SaveData s = SaveData.newGame();
            GameEngine e = new GameEngine(seed, s);
            new TestBot(2.5f).playDay(e); // çok yavaş oyuncu
            assertFalse("seed " + seed, e.getSummary().failed);
            assertTrue(s.stars >= 3.0 - 0.31);
        }
    }

    @Test public void idleBotLosesCampaign() {
        SaveData s = SaveData.newGame();
        int failedAt = -1;
        for (int d = 1; d <= 40 && failedAt < 0; d++) {
            GameEngine e = new GameEngine(d, s);
            e.tick(2000f);
            if (e.getSummary().failed) failedAt = d;
        }
        assertTrue(failedAt > 0 && failedAt <= 5);
    }

    @Test public void determinismSameSeedSameResult() {
        Campaign a = new Campaign();
        Campaign b = new Campaign();
        a.run(15, 77, 0.9f, true, 3);
        b.run(15, 77, 0.9f, true, 3);
        assertEquals(a.results.size(), b.results.size());
        for (int i = 0; i < a.results.size(); i++) assertEquals(a.results.get(i).toString(), b.results.get(i).toString());
        assertEquals(a.save.serialize(), b.save.serialize());
    }

    @Test public void differentSeedsDiffer() {
        String x = playOne(1), y = playOne(2);
        assertNotEquals(x, y);
    }

    private String playOne(long seed) {
        SaveData s = SaveData.newGame();
        s.day = 8;
        GameEngine e = new GameEngine(seed, s);
        new TestBot(1.0f).playDay(e);
        return e.getSummary().toString();
    }

    @Test public void eventSequenceIsDeterministic() {
        assertEquals(trace(5), trace(5));
    }

    private String trace(long seed) {
        SaveData s = SaveData.newGame();
        s.day = 6;
        GameEngine e = new GameEngine(seed, s);
        final StringBuilder sb = new StringBuilder();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { sb.append(text).append('|'); }
        }));
        new TestBot(1.0f).playDay(e);
        return sb.toString();
    }

    @Test public void everyDay1To60StartsAndFinishesCleanly() {
        for (int d = 1; d <= 60; d += 1) {
            SaveData s = SaveData.newGame();
            s.day = d;
            s.stars = 4.0;
            Shop.buyRecommended(s);
            GameEngine e = new GameEngine(d, s);
            new TestBot(0.8f).playDay(e);
            assertFalse("gün " + d + " bitmeli", e.isRunning());
        }
    }

    @Test public void overtimeDismissesRemainingOrders() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(3, s);
        e.tick(2000f);
        assertFalse(e.isRunning());
        assertTrue(e.getOrders().isEmpty());
    }
}
