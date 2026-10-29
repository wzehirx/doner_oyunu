package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

/** v0.5 (v=2) kayıtları v0.6'da yüklenmeli; v0.6 kaydı v=3 yazar ve geri okunur. */
public class SaveV05CompatTest {
    private static final String V05 = "v=2\nday=6\nbestDay=9\nmoney=7421\nstars=3.7\nstock.et_doner=14\nstock.ekmek=9\nstock.domates=20\n"
            + "stock.sogan=11\nstock.marul=8\nstock.ketcap=15\nstock.mayonez=6\nstock.aci_sos=4\nstock.ayran=10\nstock.lavas=9\nstock.tavuk_doner=7\n"
            + "stock.pirinc=6\nstock.tereyagi=9\nup.cirak=1\nup.buzdolabi=1\ntempo=1.1\nspeech=1\nspeechRate=1.2\nvibration=1\nmusic=0.5\nsfx=0.8\n"
            + "tutorial=1\napprenticeAnnounce=0\nreadyDrinks=5\n";

    @Test public void v05SaveLoadsEveryField() {
        SaveData s = SaveData.parse(V05);
        assertEquals(6, s.day);
        assertEquals(9, s.bestDay);
        assertEquals(7421, s.money);
        assertEquals(3.7, s.stars, 1e-9);
        assertEquals(14, s.inventory.get("et_doner"));
        assertEquals(15, s.inventory.get("ketcap"));
        assertEquals(1, s.level("cirak"));
        assertEquals(1, s.level("buzdolabi"));
        assertEquals(1.1, s.tempo, 1e-9);
        assertTrue(s.tutorialDone);
        assertFalse(s.apprenticeAnnounce);
        assertEquals(5, s.readyDrinks);
        // v0.6'da eklenen malzemeler eski kayıtta 0
        assertEquals(0, s.inventory.get("sumak"));
        assertEquals(0, s.inventory.get("kozlenmis"));
        assertEquals(0, s.inventory.get("sarimsakli_yogurt"));
    }

    @Test public void v05SavePlaysADayWithSizesAndPerItemToppings() {
        SaveData s = SaveData.parse(V05);
        Shop.buyRecommended(s);
        GameEngine e = new GameEngine(5, s);
        new TestBot(1.0f, true, true).playDay(e);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertEquals(7, s.day);
        assertTrue(e.getSummary().delivered > 0);
        assertEquals("kusursuz bot hata yapmaz", 0, e.getSummary().mistakes);
    }

    @Test public void v05DaySixOrdersUseOnlyOpenIngredientsEvenForOldStock() {
        SaveData s = SaveData.parse(V05);
        s.inventory.set("lahmacun_harci", 5);
        s.inventory.set("tursu", 10); // 7. gün açılır; eski kayıtta stok olsa da gün 6'da kullanılmaz
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Order o = e.spawnOrder(java.util.Collections.singletonList("hatay_durum"), java.util.Collections.<String>emptyList());
        assertFalse(o.getLine(0).getPool().contains("tursu"));
        assertFalse(o.getLine(0).getPool().contains("nar_eksisi"));
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        Flow.doItemStation(e, o, 0, Station.IZGARA);
        assertEquals(StartResult.INVALID, e.addTopping(o.id, 0, "tursu"));
    }

    @Test public void v06SaveWritesV3AndRoundTrips() {
        SaveData s = SaveData.parse(V05);
        String text = s.serialize();
        assertTrue(text.startsWith("v=3\n"));
        SaveData t = SaveData.parse(text);
        assertEquals(text, t.serialize());
        assertEquals(s.copy().serialize(), text);
    }

    @Test public void futureOrUnknownVersionAndKeysAreTolerated() {
        SaveData s = SaveData.parse("v=9\nday=4\nmoney=100\nbilinmeyen=1\nstock.yok_malzeme=5\nup.yok=2\n");
        assertEquals(4, s.day);
        assertEquals(100, s.money);
        assertEquals(0, s.inventory.get("yok_malzeme"));
        assertEquals(0, s.level("yok"));
    }
}
