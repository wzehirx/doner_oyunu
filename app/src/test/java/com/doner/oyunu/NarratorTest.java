package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class NarratorTest {

    private static boolean anySaid(List<String> said, String part) {
        for (String t : said) if (t.contains(part)) return true;
        return false;
    }

    @Test public void formatting() {
        assertEquals("3,0", Narrator.fmtStars(3.0));
        assertEquals("4,3", Narrator.fmtStars(4.25));
        assertEquals("1,0", Narrator.fmtStars(0.96));
        assertEquals("5 saniye", Narrator.fmtSeconds(4.6f));
        assertEquals("1 saniye", Narrator.fmtSeconds(0.2f));
    }

    @Test public void announcesEveryEventInTurkish() {
        SaveData s = SaveData.newGame();
        s.inventory.set("ekmek", 0);
        GameEngine e = new GameEngine(21, s);
        final List<String> said = new ArrayList<>();
        final List<Boolean> urgent = new ArrayList<>();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String t, boolean u) { said.add(t); urgent.add(u); }
        });
        e.addListener(n);
        for (int i = 0; i < 300 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        Order o = e.getOrders().get(0);
        assertTrue(said.get(0).startsWith("Gün 1 başladı"));
        assertTrue(said.get(0).contains("Yeni ürün: Ekmek arası döner"));
        String arrive = said.get(1);
        assertTrue(arrive, arrive.startsWith("Yeni müşteri: " + o.customer.name + ", Ekmek arası döner istiyor."));
        e.selectOrder(o.id);
        assertTrue(said.toString(), anySaid(said, "Önerilen: Ekmeği ısıt. Ekmek ısıtıcı istasyonuna dokun."));
        e.startStep(o.id, Station.ICECEK.id);
        assertTrue(said.toString(), anySaid(said, "Yanlış istasyon. Bu sipariş için şimdi Ekmek ısıtıcı gerekli."));
        e.startStep(o.id, Station.EKMEK.id);
        assertTrue(said.toString(), anySaid(said, "Malzeme bitti: Döner ekmeği."));
        assertTrue(said.toString(), anySaid(said, "27 lira"));
        e.emergencyBuy("ekmek");
        e.startStep(o.id, Station.EKMEK.id);
        assertTrue(said.toString(), anySaid(said, "Ekmeği ısıt başladı"));
        for (int i = 0; i < 2000 && e.getOrders().size() > 0; i++) e.tick(0.1f);
        StringBuilder all = new StringBuilder();
        for (String t : said) all.append(t).append('\n');
        String text = all.toString();
        assertTrue(text, text.contains("Ekmeği ısıt bitti. Önerilen: Döneri dilimle"));
        assertTrue(text, text.contains("sabırsızlanıyor"));
        assertTrue(text, text.contains("gitmek üzere"));
        assertTrue(text, text.contains("sabrı tükendi ve gitti"));
        assertTrue(text, text.contains("Yıldız"));
        assertTrue(text, text.contains("düştü"));
    }

    @Test public void deliveryAndDayTextsAndStatus() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(21, s);
        final List<String> said = new ArrayList<>();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String t, boolean u) { said.add(t); }
        });
        e.addListener(n);
        assertTrue(n.statusText().contains("Para 5000 lira, yıldız 3,0, kalan süre 126 saniye, bekleyen sipariş yok."));
        new TestBot(0.8f).playDay(e);
        StringBuilder all = new StringBuilder();
        for (String t : said) all.append(t).append('\n');
        String text = all.toString();
        assertTrue(text, text.contains("Sipariş hazır:"));
        assertTrue(text, text.contains("Teslim edildi. 240 lira kazandın"));
        assertTrue(text, text.contains("Gün 1 bitti."));
        assertTrue(text, text.contains("Yarın yeni ürün: Ayran."));
        assertTrue(n.statusText().contains("bekleyen sipariş yok"));
    }

    @Test public void stationLabelDescribesStateAndNeed() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(21, s);
        Narrator n = new Narrator(e);
        for (int i = 0; i < 300 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        Order o = e.getOrders().get(0);
        assertEquals("Ekmek ısıtıcı istasyonu, boş", n.stationLabel(Station.EKMEK));
        e.selectOrder(o.id);
        assertEquals("Ekmek ısıtıcı istasyonu, boş, Ekmeği ısıt için gerekli", n.stationLabel(Station.EKMEK));
        e.startStep(o.id, Station.EKMEK.id);
        assertTrue(n.stationLabel(Station.EKMEK).contains("çalışıyor"));
        assertEquals("", n.startResultText(StartResult.STARTED));
        assertFalse(n.startResultText(StartResult.NO_SLOT).isEmpty());
    }

    @Test public void failedDayText() {
        DaySummary d = new DaySummary();
        d.day = 4;
        d.failed = true;
        Narrator n = new Narrator(null);
        assertTrue(n.dayFailed(d).contains("Gün 4 başarısız"));
        assertEquals("", n.statusText());
    }
}
