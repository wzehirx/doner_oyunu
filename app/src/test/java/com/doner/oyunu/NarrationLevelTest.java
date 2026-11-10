package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v2.7 anlatım düzeyi (JVM): önem sınıfı süzgeci (Az / Orta / Çok), TalkBack açık/kapalı etkin düzey, kayıt, Narrator
 * olaylarının önem sınıfları ve kategori varsayılanları.
 */
public class NarrationLevelTest {
    // ------------------------------------------------------------------ süzgeç
    @Test public void levelsFilterByImportance() {
        assertTrue(NarrationLevel.LOW.allows(Importance.CRITICAL));
        assertFalse(NarrationLevel.LOW.allows(Importance.NORMAL));
        assertFalse(NarrationLevel.LOW.allows(Importance.VERBOSE));
        assertTrue(NarrationLevel.MEDIUM.allows(Importance.CRITICAL));
        assertTrue(NarrationLevel.MEDIUM.allows(Importance.NORMAL));
        assertFalse(NarrationLevel.MEDIUM.allows(Importance.VERBOSE));
        for (Importance i : Importance.values()) assertTrue("Çok her şeyi söyler: " + i, NarrationLevel.HIGH.allows(i));
        for (NarrationLevel l : NarrationLevel.values()) assertTrue("önemsiz (null) duyuru elenmez", l.allows(null));
    }

    @Test public void talkBackOffAlwaysBehavesLikeHigh() {
        for (NarrationLevel l : NarrationLevel.values())
            assertEquals("TalkBack kapalı: eski davranış (her şey)", NarrationLevel.HIGH, NarrationLevel.effective(l, false));
        assertEquals(NarrationLevel.LOW, NarrationLevel.effective(NarrationLevel.LOW, true));
        assertEquals(NarrationLevel.MEDIUM, NarrationLevel.effective(NarrationLevel.MEDIUM, true));
        assertEquals(NarrationLevel.HIGH, NarrationLevel.effective(NarrationLevel.HIGH, true));
        assertEquals("ayar yoksa varsayılan Az", NarrationLevel.LOW, NarrationLevel.effective(null, true));
    }

    @Test public void idsNamesAndDefault() {
        assertEquals(NarrationLevel.LOW, NarrationLevel.DEFAULT);
        assertEquals("Az", NarrationLevel.LOW.displayName);
        assertEquals("Orta", NarrationLevel.MEDIUM.displayName);
        assertEquals("Çok", NarrationLevel.HIGH.displayName);
        for (NarrationLevel l : NarrationLevel.values()) assertEquals(l, NarrationLevel.fromId(l.id));
        assertEquals("geçersiz kimlik: varsayılan", NarrationLevel.DEFAULT, NarrationLevel.fromId(99));
        assertEquals(NarrationLevel.DEFAULT, NarrationLevel.fromId(-1));
    }

    // ------------------------------------------------------------------ kayıt
    @Test public void settingPersistsAndOldSavesGetDefault() {
        SaveData s = SaveData.newGame();
        assertEquals("varsayılan Az", NarrationLevel.LOW, s.narrationLevel);
        s.narrationLevel = NarrationLevel.MEDIUM;
        assertEquals(NarrationLevel.MEDIUM, SaveData.parse(s.serialize()).narrationLevel);
        s.narrationLevel = NarrationLevel.HIGH;
        assertEquals(NarrationLevel.HIGH, SaveData.parse(s.serialize()).narrationLevel);
        assertEquals("kopya", NarrationLevel.HIGH, s.copy().narrationLevel);
        // eski kayıt: anahtar yok -> varsayılan
        String old = s.serialize().replaceAll("(?m)^narration=.*\n", "");
        assertFalse(old.contains("narration="));
        assertEquals(NarrationLevel.DEFAULT, SaveData.parse(old).narrationLevel);
        // bozuk değer: çökmez, varsayılan
        assertEquals(NarrationLevel.DEFAULT, SaveData.parse(s.serialize().replace("narration=2", "narration=zzz")).narrationLevel);
        assertEquals(NarrationLevel.DEFAULT, SaveData.parse(s.serialize().replace("narration=2", "narration=77")).narrationLevel);
    }

    @Test public void settingSurvivesNewGameAndReset() {
        SaveData s = SaveData.newGame();
        s.narrationLevel = NarrationLevel.HIGH;
        s.day = 5;
        assertEquals(NarrationLevel.HIGH, SaveData.newGameKeepingSettings(s).narrationLevel);
        assertEquals(NarrationLevel.HIGH, SaveData.restartAfterBankruptcy(s).narrationLevel);
    }

    // ------------------------------------------------------------------ varsayılan önem
    @Test public void defaultImportanceByCategory() {
        for (String c : new String[]{Narrator.CAT_ALERT, Narrator.CAT_PATIENCE, Narrator.CAT_BILLS, Narrator.CAT_CUSTOMER, Narrator.CAT_DAY})
            assertEquals(c, Importance.CRITICAL, Narrator.defaultImportance(c, false));
        for (String c : new String[]{Narrator.CAT_ORDER, Narrator.CAT_MONEY, Narrator.CAT_STARS, Narrator.CAT_APPRENTICE, Narrator.CAT_TOPPING})
            assertEquals(c, Importance.NORMAL, Narrator.defaultImportance(c, false));
        for (String c : new String[]{Narrator.CAT_SELECT, Narrator.CAT_HINT, Narrator.CAT_TUTORIAL, Narrator.CAT_SYSTEM})
            assertEquals(c, Importance.VERBOSE, Narrator.defaultImportance(c, false));
        assertEquals(Importance.NORMAL, Narrator.defaultImportance(null, true));
        assertEquals(Importance.VERBOSE, Narrator.defaultImportance(null, false));
    }

    @Test public void oldOutputsWithoutImportanceStillWork() {
        // eski çıkış yalnızca iki bağımsız değişkenli say'ı uygular: dört ve beş bağımsız değişkenli biçimler ona düşer
        final List<String> said = new ArrayList<>();
        Narrator.Output o = new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        };
        o.say("a", false);
        o.say("b", true, Narrator.CAT_ALERT, null);
        o.say("c", true, Narrator.CAT_ALERT, null, Importance.CRITICAL);
        assertEquals(Arrays.asList("a", "b", "c"), said);
    }

    // ------------------------------------------------------------------ Narrator olayları
    private final List<String> texts = new ArrayList<>();
    private final List<Importance> imps = new ArrayList<>();

    private GameEngine engine() {
        SaveData s = SaveData.newGame();
        s.flowMode = FlowMode.GERCEKCI;
        s.day = 5;
        s.stars = 5.0;
        s.money = 50000;
        Shop.buyRecommended(s);
        GameEngine e = new GameEngine(3, s);
        e.setSpawning(false);
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { texts.add(text); imps.add(null); }
            @Override public void say(String text, boolean urgent, String category, Narrator.Fresh fresh, Importance importance) {
                texts.add(text);
                imps.add(importance);
            }
        }));
        return e;
    }

    private Importance last() { return imps.get(imps.size() - 1); }

    @Test public void narratorEventsCarryImportance() {
        GameEngine e = engine();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { texts.add(text); imps.add(null); }
            @Override public void say(String text, boolean urgent, String category, Narrator.Fresh fresh, Importance importance) {
                texts.add(text);
                imps.add(importance);
            }
        });
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), new ArrayList<String>());
        // yeni müşteri: kritik
        texts.clear(); imps.clear();
        n.onCustomerArrived(o);
        assertEquals(Importance.CRITICAL, last());
        // seçim yankısı, ipucu, adım başladı, hazırlık aşaması bitti, ayar onayları: ayrıntı
        Step st = o.getLine(0).step(0);
        n.onOrderSelected(o); assertEquals(Importance.VERBOSE, last());
        n.onItemSelected(o, 0); assertEquals(Importance.VERBOSE, last());
        n.onNextStepHint(o, st); assertEquals(Importance.VERBOSE, last());
        n.onStepStarted(o, st, 0, 3f); assertEquals(Importance.VERBOSE, last());
        n.onPrepPhaseEnded(5); assertEquals(Importance.VERBOSE, last());
        n.onFlowModeChanged(FlowMode.RAHAT); assertEquals(Importance.VERBOSE, last());
        n.onTimePressureChanged(false); assertEquals(Importance.VERBOSE, last());
        // adım bitti, sipariş hazır, uyarılar, para/fatura: kritik
        n.onStepFinished(o, st, false); assertEquals(Importance.CRITICAL, last());
        n.onOrderReady(o); assertEquals(Importance.CRITICAL, last());
        n.onCustomerLeft(o); assertEquals(Importance.CRITICAL, last());
        n.onNotEnoughMoney(500); assertEquals(Importance.CRITICAL, last());
        n.onPatienceWarning(o, 2); assertEquals("sabır kritik", Importance.CRITICAL, last());
        n.onFinanceNotices(Collections.singletonList("Kira 1500 lira, son ödeme bugün.")); assertEquals(Importance.CRITICAL, last());
        n.onWrongStation(o, Station.values()[0], Station.values()[1]); assertEquals(Importance.CRITICAL, last());
        n.onPrepPhaseStarted(5); assertEquals(Importance.NORMAL, last());
        // olay sonuçları: normal
        n.onOrderDelivered(o, 240, 20, false); assertEquals(Importance.NORMAL, last());
        n.onOrderPerfect(o); assertEquals(Importance.NORMAL, last());
        n.onPatienceWarning(o, 1); assertEquals("sabır uyarısı ilk kademe", Importance.NORMAL, last());
        n.onEmergencyPurchase(Catalog.ingredient("domates"), 2, 30); assertEquals(Importance.NORMAL, last());
        for (Importance i : imps) assertNotNull("hiçbir Narrator duyurusu önemsiz kalmamalı", i);
    }

    @Test public void everyNarratedTextOfARealDayHasAnImportance() {
        // gerçek akış: spawn + seçim + başlat + ilerlet; tüm duyurular önem sınıfıyla gelir (null yok)
        GameEngine e = engine();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), new ArrayList<String>());
        e.selectOrder(o.id);
        for (int i = 0; i < 300; i++) e.tick(0.1f);
        assertFalse(texts.isEmpty());
        for (int i = 0; i < imps.size(); i++) assertNotNull("önemsiz duyuru: " + texts.get(i), imps.get(i));
    }
}
