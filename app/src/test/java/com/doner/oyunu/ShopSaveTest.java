package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.Map;

public class ShopSaveTest {
    @Test public void buyIngredient() {
        SaveData s = SaveData.newGame();
        s.inventory.set("ekmek", 0);
        int m = s.money;
        assertEquals(Shop.BuyResult.OK, Shop.buyIngredient(s, "ekmek", 5));
        assertEquals(5, s.inventory.get("ekmek"));
        assertEquals(m - 30, s.money);
        assertEquals(Shop.BuyResult.UNKNOWN, Shop.buyIngredient(s, "yok", 1));
        s.money = 3;
        assertEquals(Shop.BuyResult.NOT_ENOUGH_MONEY, Shop.buyIngredient(s, "et_doner", 1));
        assertEquals(3, s.money);
    }

    @Test public void stockCapEnforcedAndFridgeRaisesIt() {
        SaveData s = new SaveData();
        s.money = 100000;
        assertEquals(Shop.BuyResult.OK, Shop.buyIngredient(s, "ekmek", 1000));
        assertEquals(30, s.inventory.get("ekmek"));
        assertEquals(Shop.BuyResult.STOCK_FULL, Shop.buyIngredient(s, "ekmek", 1));
        s.day = 2;
        assertEquals(Shop.BuyResult.OK, Shop.buyUpgrade(s, "buzdolabi"));
        assertEquals(50, new Upgrades(s).stockCap());
        assertEquals(Shop.BuyResult.OK, Shop.buyIngredient(s, "ekmek", 1000));
        assertEquals(50, s.inventory.get("ekmek"));
    }

    @Test public void buyUpgradeRules() {
        SaveData s = new SaveData();
        s.day = 1;
        s.money = 100000;
        assertEquals(Shop.BuyResult.LOCKED, Shop.buyUpgrade(s, "cirak"));
        assertEquals(0, s.level("cirak"));
        s.day = 3;
        int m = s.money;
        assertEquals(Shop.BuyResult.OK, Shop.buyUpgrade(s, "cirak"));
        assertEquals(m - 3000, s.money);
        assertEquals(1, s.level("cirak"));
        assertEquals(2, new Upgrades(s).slots());
        assertEquals(Shop.BuyResult.LOCKED, Shop.buyUpgrade(s, "cirak")); // 2. seviye gün 8
        s.day = 8;
        assertEquals(Shop.BuyResult.OK, Shop.buyUpgrade(s, "cirak"));
        assertEquals(3, new Upgrades(s).slots());
        assertEquals(Shop.BuyResult.MAX_LEVEL, Shop.buyUpgrade(s, "cirak"));
        assertEquals(Shop.BuyResult.UNKNOWN, Shop.buyUpgrade(s, "yok"));
        s.money = 10;
        s.day = 10;
        assertEquals(Shop.BuyResult.NOT_ENOUGH_MONEY, Shop.buyUpgrade(s, "doner_makinesi"));
        assertEquals(0, s.level("doner_makinesi"));
    }

    @Test public void upgradeEffectsAccumulate() {
        SaveData s = new SaveData();
        s.upgradeLevels.put("doner_makinesi", 2);
        s.upgradeLevels.put("oto_dilimleyici", 1);
        s.upgradeLevels.put("klima", 3);
        s.upgradeLevels.put("tabela", 1);
        s.upgradeLevels.put("masa", 2);
        s.upgradeLevels.put("dekor", 1);
        s.upgradeLevels.put("muzik", 1);
        Upgrades u = new Upgrades(s);
        assertEquals(0.70 * 0.75, u.speedMult(Station.DONER), 1e-9);
        assertEquals(1.0, u.speedMult(Station.PAKET), 1e-9);
        assertEquals(1.30, u.patienceMult(), 1e-9);
        assertEquals(1.0 + 0.10 + 0.05 + 0.05, u.arrivalMult(), 1e-9);
        assertEquals(2, u.queueBonus());
        assertEquals(1.05, u.tipMult(), 1e-9);
        assertEquals(1.10, u.touristMult(), 1e-9);
        assertEquals(1.05, u.starGainMult(), 1e-9);
    }

    @Test public void recommendedPurchaseCoversDemandAndIsAffordable() {
        SaveData s = new SaveData();
        s.day = 5;
        Map<String, Integer> rec = Shop.recommendedPurchase(s);
        assertFalse(rec.isEmpty());
        for (String id : rec.keySet()) assertNotNull(Catalog.ingredient(id));
        int cost = Shop.recommendedCost(s);
        assertTrue(cost > 0 && cost < s.money);
        int spent = Shop.buyRecommended(s);
        assertEquals(cost, spent);
        assertTrue(Shop.recommendedPurchase(s).isEmpty());
        // parası azken sınırı aşmaz
        SaveData poor = new SaveData();
        poor.day = 5;
        poor.money = 50;
        Shop.buyRecommended(poor);
        assertTrue(poor.money >= 0);
    }

    @Test public void saveRoundTrip() {
        SaveData s = SaveData.newGame();
        s.day = 17;
        s.bestDay = 21;
        s.money = 12345;
        s.stars = 4.35;
        s.inventory.set("et_doner", 11);
        s.inventory.set("kadayif", 4);
        s.upgradeLevels.put("cirak", 2);
        s.upgradeLevels.put("tabela", 1);
        s.setTempo(1.25);
        s.speechOn = false;
        s.speechRate = 1.5;
        s.vibration = false;
        s.musicVolume = 0.25;
        s.sfxVolume = 0.5;
        s.tutorialDone = true;
        String text = s.serialize();
        assertTrue(text.contains("day=17"));
        SaveData t = SaveData.parse(text);
        assertEquals(17, t.day);
        assertEquals(21, t.bestDay);
        assertEquals(12345, t.money);
        assertEquals(4.35, t.stars, 1e-9);
        assertEquals(11, t.inventory.get("et_doner"));
        assertEquals(4, t.inventory.get("kadayif"));
        assertEquals(s.inventory.asMap(), t.inventory.asMap());
        assertEquals(2, t.level("cirak"));
        assertEquals(1, t.level("tabela"));
        assertEquals(0, t.level("klima"));
        assertEquals(1.25, t.tempo, 1e-9);
        assertFalse(t.speechOn);
        assertEquals(1.5, t.speechRate, 1e-9);
        assertFalse(t.vibration);
        assertEquals(0.25, t.musicVolume, 1e-9);
        assertEquals(0.5, t.sfxVolume, 1e-9);
        assertTrue(t.tutorialDone);
        assertEquals(text, t.serialize());
    }

    @Test public void saveParseIsTolerant() {
        SaveData n = SaveData.parse(null);
        assertEquals(1, n.day);
        assertTrue(n.inventory.total() > 0);
        assertEquals(1, SaveData.parse("").day);
        SaveData t = SaveData.parse("day=abc\nmoney=77\ngarbage\nstock.yok=5\nup.cirak=99\ntempo=9\nstars=0.2\n=x\n");
        assertEquals(1, t.day);
        assertEquals(77, t.money);
        assertEquals(2, t.level("cirak"));
        assertEquals(1.6, t.tempo, 1e-9);
        assertEquals(1.0, t.stars, 1e-9);
        assertEquals(0, t.inventory.total());
    }

    @Test public void saveCopyIsIndependent() {
        SaveData a = SaveData.newGame();
        SaveData b = a.copy();
        b.money = 1;
        b.inventory.set("ekmek", 99);
        assertEquals(SaveData.START_MONEY, a.money);
        assertNotEquals(99, a.inventory.get("ekmek"));
    }

    @Test public void saveSurvivesAcrossAGameDay() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(5, s);
        new TestBot(1.0f).playDay(e);
        SaveData loaded = SaveData.parse(s.serialize());
        assertEquals(s.day, loaded.day);
        assertEquals(s.money, loaded.money);
        assertEquals(s.stars, loaded.stars, 1e-9);
        GameEngine e2 = new GameEngine(6, loaded);
        assertEquals(2, e2.getDay());
    }
}
