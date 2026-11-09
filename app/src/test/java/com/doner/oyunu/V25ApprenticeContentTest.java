package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** v2.5: çırak teslimi, çırak seviye 4–5, 5–6 seviyeli yükseltmeler, 60 günlük içerik, kayıt dönemi (epoch). */
public class V25ApprenticeContentTest {
    static final List<String> NONE = Collections.emptyList();

    static GameEngine engine(int day, int cirak) {
        SaveData s = ResourceModelTest.day(day, FlowMode.DENGELI);
        if (cirak > 0) s.upgradeLevels.put("cirak", cirak);
        return ResourceModelTest.engine(s);
    }

    static final class Rec extends GameListenerAdapter {
        final List<ApprenticeAction> acts = new ArrayList<>();
        final List<Order> delivered = new ArrayList<>();
        final List<ActiveStep> already = new ArrayList<>();
        @Override public void onApprenticeAction(ApprenticeAction a) { acts.add(a); }
        @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { delivered.add(o); }
        @Override public void onAlreadyRunning(Order o, ActiveStep j) { already.add(j); }
    }

    // ------------------------------------------------------------ çırak teslimi
    @Test public void apprenticeDeliversAPackedOrderThatIsNotSelected() {
        GameEngine e = engine(3, 1);
        Rec r = new Rec();
        e.addListener(r);
        Order sel = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.selectOrder(sel.id);
        Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        // oyuncu seçili siparişle meşgul: çırak A'yı doldurur, paketler, teslim eder
        for (int i = 0; i < 120 && !r.delivered.contains(a); i++) { e.tick(0.1f); if (i % 10 == 0) e.selectOrder(sel.id); }
        assertTrue("çırak teslim etti", r.delivered.contains(a));
        assertEquals(1, a.getDeliveredBy());
        assertNull(e.getOrder(a.id));
        assertEquals(1, e.getSummary().apprenticeDeliveries);
        assertEquals("ödeme oyuncununkiyle aynı kural", 45, e.getSummary().revenue);
        boolean start = false, finish = false;
        for (ApprenticeAction x : r.acts) if (x.deliver && x.order == a) { if (x.started) start = true; else finish = true; }
        assertTrue(start && finish);
    }

    @Test public void selectedReadyOrderIsDeliveredOnlyAfterTheGraceOrWhenHandsAreBusy() {
        GameEngine e = engine(3, 1);
        Rec r = new Rec();
        e.addListener(r);
        Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        e.selectOrder(a.id);
        Flow.doItemStation(e, a, 0, Station.ICECEK);
        assertEquals(StartResult.STARTED, e.startStep(a.id, Station.PAKET.id));
        for (int i = 0; i < 40 && !a.isReady(); i++) e.tick(0.1f);
        assertTrue(a.isReady());
        e.selectOrder(a.id); // oyuncu az önce eylem yaptı
        ResourceModelTest.run(e, 1.0f);
        assertFalse("1,5 sn dolmadan seçili siparişe dokunmaz", a.isDelivering() || r.delivered.contains(a));
        assertTrue("oyuncu teslim edebilir", e.deliver(a.id));
        assertEquals(0, a.getDeliveredBy());
        // ikinci senaryo: oyuncu boşta kalırsa çırak teslim eder; o sırada oyuncunun teslimi reddedilir
        Order b = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        e.selectOrder(b.id);
        Flow.doItemStation(e, b, 0, Station.ICECEK);
        e.startStep(b.id, Station.PAKET.id);
        for (int i = 0; i < 60 && !b.isDelivering(); i++) e.tick(0.1f);
        assertTrue(b.isDelivering());
        List<ReadyAction> ra = e.getReadyActions(b.id);
        assertEquals(ReadyAction.Status.RUNNING, ra.get(0).status);
        assertTrue(ra.get(0).byApprentice);
        assertEquals("Teslim ediliyor, çırak", ra.get(0).text);
        assertFalse(e.deliver(b.id));
        assertEquals(1, r.already.size());
        assertTrue(r.already.get(0).isDeliver());
        ResourceModelTest.run(e, 2f);
        assertTrue(r.delivered.contains(b));
    }

    @Test public void deliverySettingOffKeepsV20Behaviour() {
        SaveData s = ResourceModelTest.day(3, FlowMode.DENGELI);
        s.upgradeLevels.put("cirak", 3);
        s.apprenticeDelivery = false;
        GameEngine e = ResourceModelTest.engine(s);
        Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        ResourceModelTest.run(e, 10f);
        assertTrue(a.isReady());
        assertNotNull("teslim oyuncuda", e.getOrder(a.id));
        assertFalse(SaveData.parse(s.serialize()).apprenticeDelivery);
    }

    @Test public void absentApprenticeDoesNotDeliver() {
        SaveData s = ResourceModelTest.day(4, FlowMode.DENGELI);
        s.upgradeLevels.put("cirak", 1);
        s.wageDebt = 100;
        GameEngine e = ResourceModelTest.engine(s);
        Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        e.selectOrder(a.id);
        Flow.makeReady(e, a);
        ResourceModelTest.run(e, 5f);
        assertNotNull(e.getOrder(a.id));
    }

    @Test public void narratorSaysWhoDeliveredAndThatTheApprenticeWillDeliver() {
        GameEngine e = engine(3, 1);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() { @Override public void say(String t, boolean u) { said.add(t); } }));
        Order sel = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.selectOrder(sel.id);
        Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        for (int i = 0; i < 120 && e.getOrder(a.id) != null; i++) { e.tick(0.1f); if (i % 10 == 0) e.selectOrder(sel.id); }
        String all = said.toString();
        assertTrue(all, all.contains("Sipariş " + a.getPosition() + " teslime hazır: " + a.customer.name + ", çırak teslim edecek."));
        assertTrue(all, all.contains("Çırak teslim etti: sipariş " + a.getPosition() + ", " + a.customer.name + ". 45 lira kazandın"));
        for (String t : said) assertFalse("teslim başlangıcı sessiz", t.contains("teslim ediyor"));
    }

    // ------------------------------------------------------------ çırak seviye 4–5
    @Test public void level4IsAFasterJourneymanAndLevel5AddsAFourthApprentice() {
        assertEquals(3, Upgrades.apprenticeWorkers(3));
        assertEquals(3, Upgrades.apprenticeWorkers(4));
        assertEquals(4, Upgrades.apprenticeWorkers(5));
        assertEquals(3, Upgrades.apprenticeSkill(5));
        assertEquals(1.2f, Upgrades.apprenticeSlowdown(3), 0);
        assertEquals(1.05f, Upgrades.apprenticeSlowdown(4), 0);
        float[] ayran = new float[2];
        int[] lv = {3, 4};
        for (int i = 0; i < 2; i++) {
            GameEngine e = engine(3, lv[i]);
            Order sel = e.spawnOrder(Collections.singletonList("kutu_icecek"), NONE);
            e.selectOrder(sel.id);
            e.spawnOrder(Collections.singletonList("ayran"), NONE);
            e.tick(0.1f);
            for (ActiveStep a : e.getActiveSteps()) if (a.isApprentice() && a.step.name.equals("Ayranı doldur")) ayran[i] = a.total;
        }
        assertEquals(1.05 / 1.2, ayran[1] / ayran[0], 0.01);
        GameEngine five = engine(3, 5);
        Order sel = five.spawnOrder(Collections.singletonList("kutu_icecek"), NONE);
        five.selectOrder(sel.id);
        for (int k = 0; k < 4; k++) five.spawnOrder(Collections.singletonList("ayran"), NONE);
        five.tick(0.1f);
        Set<Integer> workers = new HashSet<>();
        for (ActiveStep a : five.getActiveSteps()) if (a.isApprentice()) workers.add(a.worker);
        assertEquals(4, workers.size());
        assertEquals(4, five.getApprenticeWorkers());
    }

    // ------------------------------------------------------------ yükseltmeler
    @Test public void upgradesHaveFiveOrSixLevelsWithRisingPricesAndDays() {
        Set<String> ids = new HashSet<>();
        for (Upgrade u : Catalog.upgrades()) ids.add(u.id);
        assertTrue(ids.containsAll(Arrays.asList("cirak", "doner_makinesi", "ekmek_isitici", "ocak", "oto_dilimleyici", "buzdolabi",
                "masa", "tabela", "klima", "muzik", "dekor", "havalandirma", "sogutma", "ikinci_kasa", "jenerator", "su_deposu")));
        assertEquals(16, ids.size());
        for (Upgrade u : Catalog.upgrades()) {
            if (u.id.equals("jenerator") || u.id.equals("su_deposu")) assertEquals(2, u.maxLevel());
            else assertTrue(u.id, u.maxLevel() == 5 || u.maxLevel() == 6);
            for (int k = 1; k < u.maxLevel(); k++) {
                assertTrue(u.id + " fiyat katlanır", u.prices[k] > u.prices[k - 1] * 1.3);
                assertTrue(u.id + " açılış uzar", u.unlockDays[k] > u.unlockDays[k - 1]);
            }
            assertTrue(u.id, u.unlockDays[u.maxLevel() - 1] <= DayConfig.CAMPAIGN_DAYS);
        }
        assertEquals(6, Catalog.upgrade("tabela").maxLevel());
        // v2.0 seviyeleri aynen
        assertEquals(24000, Catalog.upgrade("cirak").priceForNext(2));
        assertEquals(16, Catalog.upgrade("cirak").unlockDayForNext(2));
        SaveData s = new SaveData();
        s.upgradeLevels.put("ekmek_isitici", 5);
        s.upgradeLevels.put("ocak", 4);
        s.upgradeLevels.put("buzdolabi", 5);
        s.upgradeLevels.put("sogutma", 3);
        s.upgradeLevels.put("ikinci_kasa", 2);
        s.upgradeLevels.put("masa", 5);
        s.upgradeLevels.put("havalandirma", 1);
        Upgrades u = new Upgrades(s);
        assertEquals(6, u.stationCapacity(Station.EKMEK));
        assertEquals(5, u.stationCapacity(Station.IZGARA));
        assertEquals(400, u.stockCap());
        assertEquals(8 + 4 * 5 + 6, u.readyCapacity(PrepKind.DONER));
        assertEquals(12 + 6 * 5 + 9, u.readyCapacity(PrepKind.TRAY));
        assertEquals(0.70, u.speedMult(Station.ICECEK), 1e-9);
        assertEquals(0.65, u.speedMult(Station.PAKET), 1e-9);
        assertEquals(4 + 1, u.queueBonus());
        assertEquals(0.60 * 0.95, u.speedMult(Station.IZGARA), 1e-9);
        assertEquals(1.04, u.patienceMult(), 1e-9);
        assertTrue(Catalog.upgrade("doner_makinesi").raises(BillType.ELEKTRIK) && Catalog.upgrade("doner_makinesi").raises(BillType.DOGALGAZ));
        assertTrue(Catalog.upgrade("cirak").raises(BillType.SU));
        assertFalse(Catalog.upgrade("jenerator").raises(BillType.ELEKTRIK));
        assertFalse(Catalog.newUpgradeLevelsOnDay(30).isEmpty());
    }

    // ------------------------------------------------------------ içerik
    @Test public void sixtyDayContentOpensDayByDay() {
        assertEquals("v2.0'ın 12 ürünü + 12 yeni", 24, Catalog.products().size());
        assertEquals(15, Catalog.customerTypes().size());
        String[][] opens = {{"tavuk_pilav", "17"}, {"su", "19"}, {"corba", "21"}, {"kasarli_durum", "23"}, {"salgam", "25"},
                {"sutlac", "28"}, {"cay", "31"}, {"kofte_ekmek", "34"}, {"tavuk_iskender", "38"}, {"baklava", "42"},
                {"tantuni", "46"}, {"karisik_tabak", "50"}};
        for (String[] o : opens) {
            Product p = Catalog.product(o[0]);
            assertNotNull(o[0], p);
            assertEquals(o[0], Integer.parseInt(o[1]), p.openDay);
            assertTrue(Catalog.newProductsOnDay(p.openDay).contains(p));
            for (String ing : p.totalNeeds().keySet()) assertTrue(o[0] + " " + ing, Catalog.ingredient(ing).isOpen(p.openDay));
            assertTrue("kâr var", p.ingredientCost() < p.price);
        }
        assertTrue(Catalog.product("cay").needsWater() && Catalog.product("corba").needsWater());
        assertFalse(Catalog.product("ayran").needsWater());
        assertTrue(Catalog.product("kasarli_durum").friesOk() && Catalog.product("kofte_ekmek").friesOk());
        assertEquals(Collections.singletonList("kurye"), ids(Catalog.newTypesOnDay(15)));
        assertEquals(Collections.singletonList("gurme"), ids(Catalog.newTypesOnDay(33)));
        assertTrue(Catalog.newTypesOnDay(1).isEmpty());
        assertEquals("Yeni müşteri tipi: Yemek kuryesi.", Narrator.newTypesText(15));
        String t = new Narrator(ResourceModelTest.engine(V25FinanceTest.day(15))).dayStarted(15, DayConfig.forDay(15));
        assertTrue(t, t.contains("Yeni müşteri tipi: Yemek kuryesi."));
        // menü her gün bir öncekini içerir, 60. günde hepsi açık
        for (int d = 2; d <= 60; d++) assertTrue(Catalog.productsForDay(d).containsAll(Catalog.productsForDay(d - 1)));
        assertEquals(24, Catalog.productsForDay(60).size());
        // yeni ürünler oyunda yapılabilir (bot bitirir)
        for (String[] o : opens) {
            GameEngine e = ResourceModelTest.engine(V25FinanceTest.day(Integer.parseInt(o[1])));
            Order ord = e.spawnOrder(Collections.singletonList(o[0]), NONE);
            Flow.makeReady(e, ord);
            assertTrue(e.deliver(ord.id));
        }
    }

    private static List<String> ids(List<CustomerType> l) { List<String> o = new ArrayList<>(); for (CustomerType t : l) o.add(t.id); return o; }

    @Test public void difficultyCurveRunsSixtyDaysThenEndlessCycles() {
        assertEquals(40.0, DayConfig.forDay(40).intensity, 1e-9);
        assertEquals(45.0, DayConfig.forDay(50).intensity, 1e-9);
        assertEquals(50.0, DayConfig.forDay(60).intensity, 1e-9);
        double prev = 0;
        for (int d = 1; d <= 60; d++) { assertTrue(DayConfig.forDay(d).intensity >= prev); prev = DayConfig.forDay(d).intensity; }
        assertTrue("sonsuz: dalgalı ama kalıcı artan", DayConfig.forDay(90).intensity > DayConfig.forDay(70).intensity);
        assertTrue(DayConfig.forDay(69).intensity > DayConfig.forDay(71).intensity);
        assertEquals(60, DayConfig.CAMPAIGN_DAYS);
    }

    // ------------------------------------------------------------ kayıt dönemi
    @Test public void epochResetKeepsSettingsResetsAllProgressOnce() {
        SaveData old = SaveData.newGame();
        old.day = 37; old.bestDay = 40; old.money = 99999; old.stars = 4.7;
        old.upgradeLevels.put("cirak", 3); old.inventory.set("et_doner", 77); old.readyDrinks = 6;
        Pricing.setPercent(old, "ekmek_arasi", 130);
        Finance.issue(old, BillType.KIRA, 5000, 35);
        Bank.takeLoan(old, 1000, 10);
        old.creditScore = 80; old.wageDebt = 50; old.salesSinceTax = 1234;
        old.setTempo(1.3); old.speechOn = false; old.speechRate = 1.6; old.vibration = false; old.musicVolume = 0.1;
        old.sfxVolume = 0.4; old.ambienceVolume = 0.0; old.tutorialDone = true; old.apprenticeAnnounce = false;
        old.apprenticeDelivery = false; old.flowMode = FlowMode.RAHAT; old.flowChosen = true; old.timePressure = false;
        String text = old.serialize();
        String[] olderEpochs = {text.replace("epoch=" + SaveData.EPOCH, "epoch=20"), text.replace("epoch=" + SaveData.EPOCH + "\n", "")};
        for (String t : olderEpochs) {
            SaveData s = SaveData.parse(t);
            assertEquals(SaveData.EPOCH, s.epoch);
            assertTrue(s.progressResetNotice);
            // ilerleme sıfır
            assertEquals(1, s.day); assertEquals(1, s.bestDay); assertEquals(SaveData.START_MONEY, s.money);
            assertEquals(SaveData.START_STARS, s.stars, 1e-9); assertTrue(s.upgradeLevels.isEmpty() || s.level("cirak") == 0);
            assertEquals(SaveData.newGame().inventory.asMap(), s.inventory.asMap()); assertEquals(0, s.readyDrinks);
            assertTrue(s.prices.isEmpty()); assertTrue(s.bills.isEmpty()); assertTrue(s.loans.isEmpty());
            assertEquals(Finance.CREDIT_START, s.creditScore); assertEquals(0, s.wageDebt); assertEquals(0, s.salesSinceTax);
            // ayarlar korunur
            assertEquals(1.3, s.tempo, 1e-9); assertFalse(s.speechOn); assertEquals(1.6, s.speechRate, 1e-9); assertFalse(s.vibration);
            assertEquals(0.1, s.musicVolume, 1e-9); assertEquals(0.4, s.sfxVolume, 1e-9); assertEquals(0.0, s.ambienceVolume, 1e-9);
            assertTrue(s.tutorialDone); assertFalse(s.apprenticeAnnounce); assertFalse(s.apprenticeDelivery);
            assertEquals(FlowMode.RAHAT, s.flowMode); assertTrue(s.flowChosen); assertFalse(s.timePressure);
            // bildirim kayıtta kalır (uygulama kapanırsa yine gösterilir), gösterilince temizlenir
            SaveData again = SaveData.parse(s.serialize());
            assertTrue(again.progressResetNotice);
            assertEquals(1, again.day);
            again.clearProgressResetNotice();
            assertFalse(SaveData.parse(again.serialize()).progressResetNotice);
        }
        // bu dönemin kaydı olduğu gibi yüklenir (finans dahil)
        SaveData cur = SaveData.parse(text);
        assertFalse(cur.progressResetNotice);
        assertEquals(37, cur.day); assertEquals(130, Pricing.percent(cur, "ekmek_arasi"));
        assertEquals(1, cur.bills.size()); assertEquals(1, cur.loans.size()); assertEquals(80, cur.creditScore);
        assertEquals(text, cur.serialize());
        assertEquals(text, old.copy().serialize());
        // gelecek dönem (geri sürüm) sıfırlanmaz
        assertEquals(37, SaveData.parse(text.replace("epoch=" + SaveData.EPOCH, "epoch=99")).day);
        // ilerlemesi olmayan eski ayar dosyası bildirim üretmez
        SaveData settingsOnly = SaveData.parse("v=4\ntempo=1.2\nspeech=0\n");
        assertFalse(settingsOnly.progressResetNotice);
        assertEquals(1.2, settingsOnly.tempo, 1e-9);
        assertFalse(SaveData.parse(null).progressResetNotice);
        assertFalse(new SaveData().progressResetNotice);
        assertEquals(SaveData.EPOCH, new SaveData().epoch);
        assertTrue(Narrator.progressResetText().contains("İlerleme sıfırlandı") || Narrator.progressResetText().contains("ilerleme sıfırlandı"));
    }

    @Test public void newGameKeepingSettingsIsTheSameResetAsTheEpochOne() {
        SaveData old = SaveData.newGame();
        old.day = 9; old.setTempo(0.8); old.flowMode = FlowMode.GERCEKCI; old.apprenticeDelivery = false;
        SaveData n = SaveData.newGameKeepingSettings(old);
        assertEquals(1, n.day);
        assertEquals(0.8, n.tempo, 1e-9);
        assertEquals(FlowMode.GERCEKCI, n.flowMode);
        assertFalse(n.apprenticeDelivery);
        assertFalse(n.progressResetNotice);
    }
}
