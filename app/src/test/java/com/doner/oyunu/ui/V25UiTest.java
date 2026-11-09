package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.doner.oyunu.audio.Speaker;
import com.doner.oyunu.engine.Bank;
import com.doner.oyunu.engine.Bill;
import com.doner.oyunu.engine.BillType;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.Finance;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Pricing;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.ReadyAction;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.Upgrade;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.robolectric.shadows.ShadowTextToSpeech;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.5 arayüz: "Stoğu oku" ve diğer açık okumalar her basışta konuşur; Fiyatlar, Banka, Faturalar ekranları; gün içi
 * faturalar ve kesinti etiketleri; çırak teslimi ayarı; gün sonu özet satırları; tahliye yönlendirmesi; sıfırlama bildirimi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class V25UiTest {
    private int evBase;

    @After public void reset() { GameScreen.testSeed = null; }

    private static ShadowAccessibilityManager talkBack(boolean on) {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = shadowOf(am);
        sam.setEnabled(on);
        sam.setTouchExplorationEnabled(on);
        return sam;
    }

    private List<String> announcements(ShadowAccessibilityManager sam) {
        List<String> l = new ArrayList<>();
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent e : all.subList(Math.min(evBase, all.size()), all.size()))
            if (e.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !e.getText().isEmpty()) l.add(e.getText().get(0).toString());
        return l;
    }

    private static int countStripped(List<String> l, String text) {
        int n = 0;
        for (String s : l) if (Speaker.stripMark(s).equals(text)) n++;
        return n;
    }

    private static void audit(UiHarness h, String where) {
        h.idle(100);
        A11yAudit a = A11yAudit.run(h, where);
        assertTrue(where + ": denetlenen etkileşimli görünüm yok", a.checkedInteractive > 0);
        assertTrue("Erişilebilirlik hataları:\n" + a.report(), a.errors.isEmpty());
    }

    private static UiHarness newGame(FlowMode m) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        return h;
    }

    // ================================================================== 1. "Stoğu oku" art arda
    /** TalkBack açık: "Hazır stoğu oku" art arda üç kez: her basış bir duyuru, art arda aynı metin değil (TalkBack yutmasın). */
    @Test public void readStockSpeaksOnEveryPressWithTalkBack() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        h.click("Güne başla"); h.settle();
        assertTrue(h.screen() instanceof GameScreen);
        h.run(1500); // hazırlık aşaması başladı, açılış duyuruları geçti
        String want = "Hazır stok yok.";
        evBase = sam.getSentAccessibilityEvents().size();
        for (int i = 0; i < 3; i++) { h.click("Hazır stoğu oku"); h.idle(200); }
        List<String> got = announcements(sam);
        assertEquals("her basış konuşmalı: " + got, 3, countStripped(got, want));
        List<String> reads = new ArrayList<>();
        for (String s : got) if (Speaker.stripMark(s).equals(want)) reads.add(s);
        for (int i = 1; i < reads.size(); i++) assertNotEquals("art arda aynı ham metin TalkBack'te yutulur", reads.get(i - 1), reads.get(i));
        // hemen art arda (gecikme bitmeden) iki basış da en az bir okuma ve sonuncusu söylenir
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Hazır stoğu oku"); h.click("Hazır stoğu oku"); h.idle(300);
        assertTrue(countStripped(announcements(sam), want) >= 1);
    }

    /** Konuşan, kesilmeyen bir uyarı varken bile açık okuma hemen söylenir (eskiden sıraya girip bayatlıyordu). */
    @Test public void explicitReadInterruptsNonInterruptibleAlert() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        h.click("Güne başla"); h.settle();
        h.run(1500);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.say("Elin dolu: Döneri dilimle sürüyor, 4 saniye.", true, com.doner.oyunu.engine.Narrator.CAT_ALERT, null);
        h.act.speaker.say("İkinci uyarı.", true, com.doner.oyunu.engine.Narrator.CAT_ALERT, null);
        h.act.speaker.say("Üçüncü uyarı.", true, com.doner.oyunu.engine.Narrator.CAT_ALERT, null);
        h.click("Hazır stoğu oku"); h.idle(150);
        List<String> got = announcements(sam);
        assertEquals("açık okuma uyarının arkasında beklememeli: " + got, 1, countStripped(got, "Hazır stok yok."));
        // uyarılar okuma bittikten sonra söylenir (atılmadı)
        h.idle(9000);
        assertTrue(announcements(sam).toString(), announcements(sam).contains("Üçüncü uyarı."));
    }

    /** "Durumu oku" (oyun ekranı) art arda iki basış: iki okuma. */
    @Test public void statusReadSpeaksTwiceWithTalkBack() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        h.startDay();
        h.run(2000);
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Durumu oku"); h.idle(200);
        h.click("Durumu oku"); h.idle(200);
        int n = 0;
        for (String s : announcements(sam)) if (Speaker.stripMark(s).startsWith("Para ")) n++;
        assertEquals(2, n);
    }

    /** TalkBack kapalı (TTS): her basış QUEUE_FLUSH ile önceki konuşmayı keserek okunur; aynı metin üç kez de söylenir. */
    @Test public void readStockSpeaksOnEveryPressWithTts() {
        talkBack(false);
        UiHarness h = newGame(FlowMode.DENGELI);
        h.click("Güne başla"); h.settle();
        h.run(1500);
        h.click("Hazır stoğu oku"); h.idle(50); // TTS motoru burada başlatılır
        TextToSpeech tts = ShadowTextToSpeech.getLastTextToSpeechInstance();
        assertNotNull(tts);
        ShadowTextToSpeech st = shadowOf(tts);
        st.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        h.idle(50);
        int before = st.getSpokenTextList().size();
        for (int i = 0; i < 3; i++) { h.click("Hazır stoğu oku"); h.idle(30); }
        List<String> spoken = st.getSpokenTextList();
        int n = 0;
        for (String s : spoken.subList(before, spoken.size())) if (s.equals("Hazır stok yok.")) n++;
        assertEquals("her basış konuşmalı: " + spoken, 3, n);
        assertEquals(TextToSpeech.QUEUE_FLUSH, st.getQueueMode());
    }

    // ================================================================== 3. Fiyatlar
    @Test public void priceScreenRowsButtonsActionsAndReset() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        h.act.save.day = 6;
        h.act.startBriefing(); h.settle();
        h.click("Fiyatlar"); h.settle();
        assertTrue(h.screen() instanceof PriceScreen);
        audit(h, "Fiyatlar");
        SaveData s = h.act.save;
        for (Product p : Catalog.productsForDay(6)) {
            assertNotNull("satır yok: " + p.name, h.findLabel(p.name + ", " + p.price + " lira, yüzde 100"));
            assertNotNull(h.findLabel(p.name + " fiyatını artır"));
            assertNotNull(h.findLabel(p.name + " fiyatını azalt"));
        }
        assertNotNull(h.findText("Tahmini müşteri: değişmez."));
        // +%5 düğmesi
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Ayran fiyatını artır"); h.idle(200);
        assertEquals(105, Pricing.percent(s, "ayran"));
        Product ayran = Catalog.product("ayran");
        assertNotNull(h.findLabel("Ayran, " + Pricing.priceOf(s, ayran) + " lira, yüzde 105"));
        boolean spoke = false;
        for (String a : announcements(sam)) if (Speaker.stripMark(a).startsWith("Ayran: ")) spoke = true;
        assertTrue("fiyat değişimi okunur", spoke);
        assertTrue("kayda yazılır", s.serialize().contains("price.ayran=105"));
        // TalkBack Eylemleri: satırda "Fiyatı azalt" iki kez
        View row = h.findLabel("Ayran, ");
        List<String> acts = A11yAudit.customActionLabels(row);
        assertTrue(acts.toString(), acts.contains("Fiyatı artır") && acts.contains("Fiyatı azalt"));
        row.performAccessibilityAction(ActionMenu.ACTION_BASE + acts.indexOf("Fiyatı azalt"), null); h.idle(150);
        row.performAccessibilityAction(ActionMenu.ACTION_BASE + acts.indexOf("Fiyatı azalt"), null); h.idle(150);
        assertEquals(95, Pricing.percent(s, "ayran"));
        assertTrue(h.findText("Tahmini müşteri: yüzde") != null);
        // tahmini müşteri etkisi okuma
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Tahmini müşteri etkisi"); h.idle(200);
        assertTrue(announcements(sam).toString(), Speaker.stripMark(announcements(sam).get(0)).startsWith("Tahmini müşteri"));
        // alt sınır
        for (int i = 0; i < 15; i++) { h.click("Ekmek arası döner fiyatını azalt"); h.idle(20); }
        assertEquals(Pricing.MIN_PERCENT, Pricing.percent(s, "ekmek_arasi"));
        // referans fiyata dön
        h.click("Referans fiyata dön"); h.idle(150);
        assertTrue(Pricing.allDefault(s));
        assertNotNull(h.findLabel("Ayran, " + ayran.price + " lira, yüzde 100"));
        // Geri: brifinge döner
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        // marketten de açılır
        h.click("Markete git"); h.settle();
        h.click("Fiyatlar"); h.settle();
        assertTrue(h.screen() instanceof PriceScreen);
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof ShopScreen);
    }

    // ================================================================== 4. Banka
    @Test public void bankScreenTakeLoanTermAmountAndRepay() {
        talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 8;
        s.money = 20_000;
        h.act.startBriefing(); h.settle();
        h.click("Banka"); h.settle();
        assertTrue(h.screen() instanceof BankScreen);
        audit(h, "Banka");
        assertNotNull(h.findText("Kredi notu 60"));
        assertNotNull(h.findText("Kredi borcun yok."));
        assertNotNull(h.findLabel("10 gün vade, seçili"));
        h.click("Tutarı azalt"); h.idle(50);
        h.click("Tutarı azalt"); h.idle(50);
        assertNotNull(h.findText("Tutar: 2000 lira"));
        h.click("20 gün vade"); h.idle(50);
        assertNotNull(h.findLabel("20 gün vade, seçili"));
        assertNotNull(h.findText("Teklif: 2000 lira, 20 gün"));
        int money = s.money;
        h.click("Kredi al: 2000 lira, 20 gün"); h.idle(150);
        assertEquals(1, s.loans.size());
        assertEquals(money + 2000, s.money);
        audit(h, "Banka(kredi var)");
        View close = h.findLabel("Erken kapat: 2000 liralık kredi");
        assertNotNull(h.clickables().toString(), close);
        close.performClick(); h.idle(150);
        assertTrue(s.loans.isEmpty());
        assertEquals(63, s.creditScore);
        // en çok 3 kredi
        for (int i = 0; i < 4; i++) { h.click("Tutarı azalt"); h.idle(20); }
        for (int i = 0; i < 3; i++) { h.click("Kredi al: 500 lira"); h.idle(50); }
        assertEquals(3, s.loans.size());
        int m = s.money;
        h.click("Kredi al: 500 lira"); h.idle(50);
        assertEquals("dördüncü kredi verilmez", 3, s.loans.size());
        assertEquals(m, s.money);
        assertNotNull(h.findText("En çok 3 kredi"));
        // Bankayı oku
        h.click("Bankayı oku"); h.idle(150);
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
    }

    // ================================================================== 5. Faturalar
    private static void bills(SaveData s) {
        Finance.issue(s, BillType.ELEKTRIK, 300, s.day - 3);   // 1 gün gecikti
        Finance.issue(s, BillType.KIRA, 1500, s.day - 2);      // son ödeme bugün
        Finance.issue(s, BillType.SU, 120, s.day - 1);         // 1 gün kaldı
    }

    @Test public void billsScreenPayDeferLoanAndWages() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 9;
        s.money = 1000;
        s.upgradeLevels.put("cirak", 1);
        s.wageDebt = 260;
        bills(s);
        h.act.startBriefing(); h.settle();
        assertNotNull("brifingte fatura uyarısı", h.findText("Elektrik faturası 1 gündür ödenmedi"));
        assertNotNull(h.findText("Kira 1500 lira, son ödeme günü bugün"));
        h.click("Faturalar, 3 açık fatura"); h.settle();
        assertTrue(h.screen() instanceof BillsScreen);
        audit(h, "Faturalar");
        assertNotNull(h.findText("Açık fatura 3"));
        assertNotNull(h.findText("Maaş borcu 260 lira"));
        // gecikmiş olan önce
        List<String> cl = h.clickables();
        int iE = -1, iK = -1;
        for (int i = 0; i < cl.size(); i++) { if (cl.get(i).startsWith("Öde: Elektrik")) iE = i; if (cl.get(i).startsWith("Öde: Kira")) iK = i; }
        assertTrue(cl.toString(), iE >= 0 && iK > iE);
        // Öde (elektrik): para düşer, fatura kalkar
        Bill el = Finance.openBills(s).get(0);
        int due = el.amountDue(s.day);
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Öde: Elektrik"); h.idle(200);
        assertNull(Finance.bill(s, el.id));
        assertEquals(1000 - due, s.money);
        assertTrue(announcements(sam).toString(), Speaker.stripMark(announcements(sam).get(announcements(sam).size() - 1)).startsWith("Elektrik faturası ödendi"));
        // Öde (kira): para yetmez
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Öde: Kira"); h.idle(200);
        assertEquals(2, s.bills.size());
        assertTrue(announcements(sam).toString(), Speaker.stripMark(announcements(sam).get(0)).startsWith("Kasada yeterli para yok"));
        // Ertele
        h.click("Ertele: Kira"); h.idle(150);
        assertNotNull(h.findText("Ertelendi."));
        // Krediyle öde: önce teklif, sonra onay
        h.click("Krediyle öde: Kira"); h.idle(150);
        assertEquals(2, s.bills.size());
        assertNotNull(h.findLabel("Emin misin? Krediyle öde: Kira"));
        h.click("Emin misin? Krediyle öde: Kira"); h.idle(150);
        assertEquals(1, s.bills.size());
        assertEquals(1, s.loans.size());
        // maaş
        h.click("Maaşı öde: 260 lira"); h.idle(150);
        assertEquals(0, s.wageDebt);
        audit(h, "Faturalar(ödendi)");
        // Banka ve Markete git, Güne başla
        assertNotNull(h.findLabel("Banka"));
        assertNotNull(h.findLabel("Güne başla"));
        h.click("Banka"); h.settle();
        assertTrue(h.screen() instanceof BankScreen);
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof BillsScreen);
    }

    @Test public void evictionRoutesToBillsAndPayingRentReopens() {
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 16;
        s.money = 300;
        Finance.issue(s, BillType.KIRA, 1400, 7);      // son ödeme 9: 7 gün gecikti -> tahliye
        h.act.startBriefing(); h.settle();
        assertNull("tahliyede Güne başla yok", h.findLabel("Güne başla"));
        assertNotNull(h.findText("Tahliye kararı"));
        h.click("Markete git"); h.settle();
        h.click("Güne başla"); h.settle();
        assertTrue("market Güne başla tahliyede faturalara gider", h.screen() instanceof BillsScreen);
        assertNull(h.findLabel("Güne başla"));
        h.click("Krediyle öde: Kira"); h.idle(100);
        h.click("Emin misin? Krediyle öde: Kira"); h.idle(150);
        assertTrue("kira kredisi limitten bağımsız", s.bills.isEmpty());
        assertNotNull("kira ödenince Güne başla", h.findLabel("Güne başla"));
        h.click("Güne başla"); h.settle();
        assertTrue(h.screen() instanceof GameScreen);
        assertNotEquals(GameEngine.State.FAILED, h.engine().getState());
    }

    @Test public void evictionInGameFailsDayAndSummaryPointsToBills() {
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 16;
        Finance.issue(s, BillType.KIRA, 1400, 7);
        h.act.show(new GameScreen(h.act)); h.settle();
        h.run(6000);
        assertTrue(h.screen().title(), h.screen() instanceof SummaryScreen);
        assertNotNull(h.findText("tahliye kararı var"));
        assertNotNull(h.findLabel("Faturalar: kirayı öde"));
        h.click("Faturalar: kirayı öde"); h.settle();
        assertTrue(h.screen() instanceof BillsScreen);
    }

    // ================================================================== 5b. gün içi faturalar, kesinti etiketleri
    @Test public void gasCutClosesOcakTileAndPayingInPauseMenuReopens() {
        talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 6;
        s.money = 30_000;
        Shop.buyRecommended(s);
        Finance.issue(s, BillType.DOGALGAZ, 200, 1);  // son ödeme 3: 3 gün gecikti -> Ocak kapalı
        h.startDay();
        GameEngine e = h.engine();
        assertTrue(e.isStationClosed(Station.IZGARA));
        View tile = h.stationTile(Station.IZGARA);
        assertNotNull(h.clickables().toString(), tile);
        assertEquals(GameEngine.LOCK_CLOSED, UiHarness.label(tile));
        audit(h, "Oyun(Ocak kapalı)");
        // kapalı istasyona dokunma: hata, çökme yok
        tile.performClick(); h.run(200);
        // Duraklat > Faturalar > Öde
        h.click("Duraklat"); h.run(100);
        h.click("Faturalar, 1 açık fatura"); h.run(100);
        audit(h, "Oyun(duraklat+faturalar)");
        assertNotNull(h.findText("Doğalgaz faturası"));
        assertNull("gün içinde ertele yok", h.findLabel("Ertele"));
        int money = e.getMoney();
        h.click("Öde: Doğalgaz"); h.run(100);
        assertTrue(e.getSave().bills.isEmpty());
        assertTrue(e.getMoney() < money);
        assertFalse("ödeyince Ocak hemen açılır", e.isStationClosed(Station.IZGARA));
        h.click("Kapat"); h.run(100);
        assertNotNull(h.findLabel("Devam et"));
        h.click("Devam et"); h.run(200);
        assertTrue(UiHarness.label(h.stationTile(Station.IZGARA)).startsWith("Ocak"));
        assertNotEquals(GameEngine.LOCK_CLOSED, UiHarness.label(h.stationTile(Station.IZGARA)));
    }

    @Test public void electricCutShowsOnStationLabels() {
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 6;
        s.money = 30_000;
        Shop.buyRecommended(s);
        Finance.issue(s, BillType.ELEKTRIK, 200, 3); // son ödeme 5: 1 gün gecikti -> kısmi
        h.startDay();
        assertTrue(UiHarness.label(h.stationTile(Station.EKMEK)).contains("elektrik kesik"));
        assertTrue(UiHarness.label(h.stationTile(Station.ICECEK)).contains("elektrik kesik"));
        assertFalse(UiHarness.label(h.stationTile(Station.DONER)).contains("elektrik"));
        String st = new com.doner.oyunu.engine.Narrator(h.engine()).statusText();
        assertTrue(st, st.contains("elektrik kısmen kesik"));
    }

    @Test public void closedActionRowSaysOcakClosed() {
        GameEngine e = new GameEngine(3, SaveData.newGame());
        ReadyAction a = null;
        // CLOSED satırı motorda Ocak kapalıyken üretilir; etiket "…, Ocak kapalı, doğalgaz kesik"
        SaveData s = SaveData.newGame();
        s.day = 6;
        s.money = 20_000;
        Shop.buyRecommended(s);
        Finance.issue(s, BillType.DOGALGAZ, 200, 1);
        e = new GameEngine(3, s);
        e.setSpawning(false);
        assertTrue(e.startDay());
        com.doner.oyunu.engine.Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi+patates"), new ArrayList<String>());
        for (ReadyAction r : e.getReadyActions(o.id)) if (r.status == ReadyAction.Status.CLOSED) a = r;
        assertNotNull(e.getReadyActions(o.id).toString(), a);
        String l = Texts.actionLabel(e, a);
        assertTrue(l, l.contains(GameEngine.LOCK_CLOSED) && l.length() <= 60 && !l.contains("sürüyor"));
    }

    // ================================================================== 5c. gün sonu özet satırları
    @Test public void summaryRowsShowWagesInstallmentsSeizureBillsAndNoZeroRent() {
        DaySummary d = new DaySummary();
        d.day = 10; d.delivered = 12; d.revenue = 3000; d.tips = 200; d.moneyAfter = 4000; d.startStars = 3; d.endStars = 3.2;
        d.wagesDue = 340; d.wagesPaid = 300; d.wageDebt = 40;
        d.installmentsDue = 324; d.installmentsPaid = 324;
        d.seized = 120;
        d.newBills.add(Finance.issue(SaveData.newGame(), BillType.ELEKTRIK, 315, 10));
        d.billsDue = 900; d.billsLate = 1; d.loanDebt = 2600; d.creditScore = 58; d.priceComplaints = 2;
        StringBuilder sb = new StringBuilder();
        for (String[] r : Texts.summaryRows(d)) sb.append(r[0]).append('=').append(r[1]).append(';');
        String t = sb.toString();
        assertFalse("günlük kira satırı yok", t.contains("Kira="));
        assertTrue(t, t.contains("Çırak maaşı=300 lira"));
        assertTrue(t, t.contains("Maaş borcu=40 lira"));
        assertTrue(t, t.contains("Kredi taksiti=324 lira"));
        assertTrue(t, t.contains("Haciz=120 lira"));
        assertTrue(t, t.contains("Yeni faturalar=elektrik faturası 315 lira, son ödeme 12. gün"));
        assertTrue(t, t.contains("Açık fatura borcu=900 lira, 1 fatura gecikmiş"));
        assertTrue(t, t.contains("Kredi notu=58"));
        assertTrue(t, t.contains("Fiyatı pahalı bulan=2"));
        assertTrue(t, t.contains("Net kazanç=" + d.profit() + " lira"));
        // ekranda
        UiHarness h = newGame(FlowMode.DENGELI);
        Finance.issue(h.act.save, BillType.ELEKTRIK, 315, 1);
        h.act.show(new SummaryScreen(h.act, d)); h.settle();
        assertNotNull(h.findText("Çırak maaşı"));
        assertNotNull(h.findLabel("Faturalar, 1 açık fatura"));
        audit(h, "Özet(v2.5)");
        h.click("Faturalar, 1 açık fatura"); h.settle();
        assertTrue(h.screen() instanceof BillsScreen);
        h.click("Markete git"); h.settle();
        assertTrue(h.screen() instanceof ShopScreen);
    }

    // ================================================================== 6. çırak
    @Test public void apprenticeDeliverySettingTogglesAndPersists() {
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        View sw = h.findLabel("Çırak teslimi");
        assertNotNull(h.clickables().toString(), sw);
        assertTrue(((android.widget.Switch) sw).isChecked());
        sw.performClick(); h.idle(100);
        assertFalse(h.act.save.apprenticeDelivery);
        assertTrue(h.act.save.serialize().contains("apprenticeDelivery=0"));
        audit(h, "Ayarlar(çırak teslimi)");
        // ayar oyunda motora geçer
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        assertFalse("yeni oyunda ayar korunur", h.act.save.apprenticeDelivery);
        sw = null;
        h.act.show(new SettingsScreen(h.act)); h.settle();
        h.findLabel("Çırak teslimi").performClick(); h.idle(50);
        assertTrue(h.act.save.apprenticeDelivery);
    }

    @Test public void apprenticeWageInMarketBriefingAndAbsentWarning() {
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 10;
        s.upgradeLevels.put("cirak", 1);
        h.act.startBriefing(); h.settle();
        String wage = "Çırak maaşı günlük " + Finance.dailyWage(1, 10) + " lira";
        assertNotNull(h.findText(wage));
        h.click("Markete git"); h.settle();
        h.click("Mutfak aletleri"); h.settle();
        assertNotNull(h.findText("Günlük maaş " + Finance.dailyWage(1, 10) + " lira"));
        // maaş borcu: çırak gelmez
        s.wageDebt = 100;
        s.money = 20_000;
        Shop.buyRecommended(s);
        h.act.startBriefing(); h.settle();
        assertNotNull(h.findText("Çırak maaşını alamadı, bugün gelmedi"));
        h.startDay();
        assertTrue(h.engine().isApprenticeAbsent());
        assertNotNull(h.findText("Çırak bugün yok"));
    }

    // ================================================================== 7. yükseltmeler
    @Test public void marketListsAllUpgradesWithLevelsAndBillNotes() {
        UiHarness h = newGame(FlowMode.DENGELI);
        h.act.save.day = 60;
        h.act.save.money = 1_000_000;
        h.click("Markete git"); h.settle();
        int found = 0;
        for (String tab : new String[]{"Mutfak aletleri", "Restoran"}) {
            h.click(tab); h.settle();
            for (Upgrade u : Catalog.upgrades()) {
                if (h.findText(u.name + ", seviye 0 / " + u.maxLevel()) != null) found++;
            }
            audit(h, "Market " + tab);
        }
        assertEquals(16, Catalog.upgrades().size());
        assertEquals("16 yükseltme seviyeleriyle listelenir", 16, found);
        h.click("Mutfak aletleri"); h.settle();
        assertNotNull(h.findText("Elektrik ve doğalgaz faturasını artırır"));
        assertNotNull(h.findText("Tabela, seviye 0 / 6") == null ? h.findText("Tabela") : h.findText("Tabela, seviye 0 / 6"));
    }

    @Test public void newProductSoundsMapToExistingSounds() {
        assertEquals("sfx_pour", GameScreen.sfxForStep(Catalog.product("cay").steps.get(0)));
        assertEquals("sfx_pour", GameScreen.sfxForStep(Catalog.product("salgam").steps.get(0)));
        assertEquals("sfx_pour", GameScreen.sfxForStep(Catalog.product("corba").steps.get(0)));
        assertEquals("sfx_drink_fridge", GameScreen.sfxForStep(Catalog.product("su").steps.get(0)));
        assertEquals("sfx_drink_can", GameScreen.drinkFinishSfx(Catalog.product("su").steps.get(0)));
        assertEquals("sfx_drink_glass", GameScreen.drinkFinishSfx(Catalog.product("cay").steps.get(0)));
        assertEquals("sfx_drink_fridge", GameScreen.sfxForStep(Catalog.product("sutlac").steps.get(0)));
        assertEquals("sfx_wrap", GameScreen.sfxForStep(Catalog.product("baklava").steps.get(0)));
        assertEquals("sfx_grill_sizzle", GameScreen.sfxForStep(Catalog.product("tantuni").steps.get(1)));
        // eski eşleşmeler değişmedi
        assertEquals("sfx_drink_ayran", GameScreen.sfxForStep(Catalog.product("ayran").steps.get(0)));
        assertEquals("sfx_drink_fridge", GameScreen.sfxForStep(Catalog.product("kutu_icecek").steps.get(0)));
        assertEquals("sfx_grill_sizzle", GameScreen.sfxForStep(Catalog.product("kunefe").steps.get(0)));
        // her ürünün her adımı var olan bir sese eşlenir
        for (Product p : Catalog.products()) for (com.doner.oyunu.engine.Step st : p.steps) {
            String sfx = GameScreen.sfxForStep(st);
            assertTrue(p.id + " " + st.name + " -> " + sfx, java.util.Arrays.asList(com.doner.oyunu.audio.SoundManager.SFX).contains(sfx));
        }
    }

    // ================================================================== 8. sıfırlama bildirimi
    @Test public void progressResetNoticeShownOnceAndSettingsKept() {
        ShadowAccessibilityManager sam = talkBack(true);
        SaveData old = SaveData.newGame();
        old.day = 14;
        old.money = 22_222;
        old.speechRate = 1.4;
        old.apprenticeDelivery = false;
        String text = old.serialize().replace("epoch=25\n", "").replace("v=5", "v=4");
        assertFalse(text.contains("epoch"));
        RuntimeEnvironment.getApplication().getSharedPreferences("doner_oyunu", 0).edit()
                .putString("save", text).putBoolean("game_started", true).commit();
        evBase = sam.getSentAccessibilityEvents().size();
        UiHarness h = new UiHarness(false);
        h.idle(500);
        assertTrue(h.screen() instanceof MenuScreen);
        assertNotNull("görsel bildirim", h.findText(com.doner.oyunu.MainActivity.RESET_NOTICE));
        assertEquals(1, countStripped(announcements(sam), com.doner.oyunu.MainActivity.RESET_NOTICE));
        assertEquals(1, h.act.save.day);
        assertEquals(SaveData.START_MONEY, h.act.save.money);
        assertEquals(1.4, h.act.save.speechRate, 1e-9);
        assertFalse("ayar korunur", h.act.save.apprenticeDelivery);
        assertFalse(h.act.save.progressResetNotice);
        String saved = RuntimeEnvironment.getApplication().getSharedPreferences("doner_oyunu", 0).getString("save", "");
        assertFalse("bildirim kayıtta kapandı", saved.contains("resetNotice=1"));
        assertNotNull("Devam et 1. günden", h.findLabel("Devam et, gün 1"));
        audit(h, "Menü(sıfırlama bildirimi)");
        // menüye dönünce bir daha yok
        h.click("Ayarlar"); h.settle();
        h.click("Geri"); h.settle();
        assertNull(h.findText(com.doner.oyunu.MainActivity.RESET_NOTICE));
        h.controller.pause().stop().destroy();
        // uygulama yeniden açılınca da yok
        evBase = sam.getSentAccessibilityEvents().size();
        UiHarness h2 = new UiHarness(false);
        h2.idle(500);
        assertNull(h2.findText(com.doner.oyunu.MainActivity.RESET_NOTICE));
        assertEquals(0, countStripped(announcements(sam), com.doner.oyunu.MainActivity.RESET_NOTICE));
    }

    // ================================================================== 9. tam gün (v2.5 içerik, faturalı)
    private static void fullDayLate(FlowMode m, boolean timed) {
        GameScreen.testSeed = 25L;
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.act.save.timePressure = timed;
        h.click("Yeni oyun"); h.settle();
        SaveData s = h.act.save;
        s.day = 32;
        s.money = 400_000;
        s.stars = 4.0;
        for (String id : new String[]{"buzdolabi", "ekmek_isitici", "ocak", "doner_makinesi", "cirak"}) s.upgradeLevels.put(id, 3);
        s.prices.put("ayran", 110);
        Finance.issue(s, BillType.ELEKTRIK, 600, 31);
        Shop.buyRecommended(s);
        h.act.startBriefing(); h.settle();
        assertNotNull(h.findText("Yeni malzeme"));
        h.startDay();
        GameEngine e = h.engine();
        assertTrue(e.getMenu().size() >= 18);
        UiBot.playDayViaRows(h, 700_000);
        h.run(20_000);
        assertTrue(m + "/" + timed + ": gün bitmeli, ekran " + h.screen().title(), h.screen() instanceof SummaryScreen);
        assertFalse(h.act.save.day == 32);
        assertNotNull(h.findText("Net kazanç"));
        assertNotNull("faturalar düğmesi (açık fatura)", h.findLabel("Faturalar"));
    }

    @Test public void fullLateDayViaRowsDengeliTimed() { fullDayLate(FlowMode.DENGELI, true); }
    @Test public void fullLateDayViaRowsRahatUntimed() { fullDayLate(FlowMode.RAHAT, false); }
    @Test public void fullLateDayViaRowsGercekciTimed() { fullDayLate(FlowMode.GERCEKCI, true); }
}
