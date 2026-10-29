package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

public class SimulationTest {

    @Test public void botWithUpgradesPlaysDays1To40() {
        // hazırlık yapan, malzeme seçimine dikkat eden bot
        Campaign c = new Campaign();
        int failedDay = c.run(40, 1, 0.8f, true, 3, true, true);
        if (failedDay != -1) fail("Yükseltmeli bot gün " + failedDay + " geçemedi: " + c.results.get(c.results.size() - 1));
        assertEquals(40, c.attemptsPerDay.size());
        assertEquals(41, c.save.day);
        // ekonomi sağlıklı: son günlerde de para pozitif ve çoğu gün ilk denemede geçilir
        int firstTry = 0;
        for (int a : c.attemptsPerDay) if (a == 1) firstTry++;
        assertTrue("çoğu gün ilk denemede geçilmeli: " + firstTry, firstTry >= 32);
        assertTrue(c.save.money > 0);
    }

    @Test public void noPrepBotWithUpgradesAlsoPlaysDays1To40() {
        Campaign c = new Campaign();
        int failedDay = c.run(40, 2, 0.8f, true, 3, false, true);
        if (failedDay != -1) fail("Hazırlıksız yükseltmeli bot gün " + failedDay + " geçemedi: " + c.results.get(c.results.size() - 1));
        assertEquals(41, c.save.day);
    }

    @Test public void slowHumanPaceWithUpgradesStillPlaysDays1To40() {
        Campaign c = new Campaign();
        int failedDay = c.run(40, 3, 1.5f, true, 3, true, true);
        if (failedDay != -1) fail("Yavaş (1,5 sn) bot gün " + failedDay + " geçemedi: " + c.results.get(c.results.size() - 1));
    }

    @Test public void botWithoutUpgradesEventuallyFails() {
        for (boolean prep : new boolean[]{false, true}) {
            Campaign c = new Campaign();
            int failedDay = c.run(40, 1, 0.8f, false, 3, prep, true);
            assertTrue("yükseltmesiz bot 40. güne kadar takılmalı (prep=" + prep + ")", failedDay > 0);
            assertTrue("ilk günler rahat olmalı (prep=" + prep + ", gün " + failedDay + ")", failedDay > 10);
        }
    }

    @Test public void botWithoutPrepOrUpgradesPassesFirstFiveDays() {
        for (long seed = 1; seed <= 4; seed++) {
            Campaign c = new Campaign();
            int failedDay = c.run(5, seed, 0.8f, false, 3, false, true);
            assertEquals("seed " + seed, -1, failedDay);
        }
    }

    private static long profit(Campaign c) {
        long p = 0;
        for (DaySummary d : c.results) if (!d.failed) p += d.profit();
        return p;
    }

    @Test public void prepBotEarnsMoreThanNonPrepBot() {
        long prep = 0, noprep = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Campaign a = new Campaign(), b = new Campaign();
            assertEquals(-1, a.run(15, seed, 0.8f, false, 3, true, true));
            assertEquals(-1, b.run(15, seed, 0.8f, false, 3, false, true));
            prep += profit(a);
            noprep += profit(b);
            System.out.println("SIM seed " + seed + " 15 gün (yükseltmesiz): hazırlıklı kazanç=" + profit(a) + " hazırlıksız=" + profit(b));
        }
        assertTrue("hazırlık yapan bot daha kazançlı olmalı: " + prep + " vs " + noprep, prep > noprep * 1.03);
    }

    @Test public void carelessToppingBotMakesMistakesAndCareful_BotDoesNot() {
        int carelessMistakes = 0, carefulMistakes = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Campaign a = new Campaign(), b = new Campaign();
            a.run(10, seed, 0.8f, false, 3, true, false);
            b.run(10, seed, 0.8f, false, 3, true, true);
            for (DaySummary d : a.results) carelessMistakes += d.mistakes;
            for (DaySummary d : b.results) carefulMistakes += d.mistakes;
        }
        assertTrue("istekleri yok sayan bot hata yapar: " + carelessMistakes, carelessMistakes > 0);
        assertEquals("istekleri uygulayan bot hata yapmaz", 0, carefulMistakes);
    }

    @Test public void carelessSizeBotMakesSizeMistakesAndCarefulBotDoesNot() {
        int careless = 0, careful = 0, carefulMistakes = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Campaign a = new Campaign(), b = new Campaign();
            a.run(10, seed, 0.8f, false, 3, true, false); // her zaman tam ekmek dilimler
            b.run(10, seed, 0.8f, false, 3, true, true);
            for (DaySummary d : a.results) careless += d.sizeMistakes;
            for (DaySummary d : b.results) { careful += d.sizeMistakes; carefulMistakes += d.mistakes; }
        }
        assertTrue("yarım/duble istenince her zaman tam dilimleyen bot boy hatası yapar: " + careless, careless > 0);
        assertEquals("istenen boyu seçen bot boy hatası yapmaz", 0, careful);
        assertEquals(0, carefulMistakes);
    }

    @Test public void botsPlayDays1To40OnSeveralSeedsAtHumanPace() {
        for (long seed = 11; seed <= 14; seed++) {
            Campaign c = new Campaign();
            int failedDay = c.run(40, seed, 1.0f, true, 3, true, true);
            if (failedDay != -1) fail("1,0 sn bot seed " + seed + " gün " + failedDay + " geçemedi: " + c.results.get(c.results.size() - 1));
        }
    }

    @Test public void idleBotFailsWithinFiveDaysOnEverySeed() {
        for (long seed = 1; seed <= 6; seed++) {
            SaveData s = SaveData.newGame();
            int failedAt = -1;
            for (int d = 1; d <= 40 && failedAt < 0; d++) {
                GameEngine e = new GameEngine(seed * 100 + d, s);
                e.tick(2000f);
                if (e.getSummary().failed) failedAt = d;
            }
            assertTrue("seed " + seed + " başarısız gün: " + failedAt, failedAt > 0 && failedAt <= 5);
        }
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
        a.run(15, 77, 0.9f, true, 3, true, true);
        b.run(15, 77, 0.9f, true, 3, true, true);
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
        new TestBot(1.0f, true, true).playDay(e);
        return sb.toString();
    }

    @Test public void everyDay1To60StartsAndFinishesCleanly() {
        for (int d = 1; d <= 60; d += 1) {
            SaveData s = SaveData.newGame();
            s.day = d;
            s.stars = 4.0;
            Shop.buyRecommended(s);
            GameEngine e = new GameEngine(d, s);
            new TestBot(0.8f, d % 2 == 0, true).playDay(e);
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
