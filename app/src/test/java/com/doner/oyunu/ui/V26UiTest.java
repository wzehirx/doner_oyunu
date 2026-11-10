package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CompoundButton;

import com.doner.oyunu.audio.Speaker;
import com.doner.oyunu.engine.Bill;
import com.doner.oyunu.engine.BillType;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.Finance;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.SpeechQueue;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.robolectric.shadows.ShadowTextToSpeech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v2.6 arayüz/erişilebilirlik (denetim raporu §2, §3.2): konuşma süresi sınırı ve uyarının açık okumayı kesmesi, konuşan
 * ekran okuyucu tanımı, fatura/banka satırlarının yerinde güncellenmesi (odak kaybı yok, tek duyuru), brifing uyarılarının
 * kısa ve tek sefer söylenmesi, ekran geçişinde tek başlık okuması, Acil al odağı, ilk fatura öğreticisi, otomatik ödeme
 * ayarı, iflas ekranı.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class V26UiTest {
    private int evBase;

    @After public void reset() { GameScreen.testSeed = null; }

    private static ShadowAccessibilityManager a11y() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return shadowOf(am);
    }

    private static ShadowAccessibilityManager talkBack(boolean on) {
        ShadowAccessibilityManager sam = a11y();
        sam.setEnabled(on);
        sam.setTouchExplorationEnabled(on);
        return sam;
    }

    private List<AccessibilityEvent> events(ShadowAccessibilityManager sam) {
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        return all.subList(Math.min(evBase, all.size()), all.size());
    }

    private List<String> announcements(ShadowAccessibilityManager sam) {
        List<String> l = new ArrayList<>();
        for (AccessibilityEvent e : events(sam))
            if (e.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !e.getText().isEmpty()) l.add(Speaker.stripMark(e.getText().get(0).toString()));
        return l;
    }

    /** Erişilebilirlik odağı alan görünümlerin metinleri (sırayla). */
    private List<String> focusTexts(ShadowAccessibilityManager sam) {
        List<String> l = new ArrayList<>();
        for (AccessibilityEvent e : events(sam))
            if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) l.add(String.valueOf(e.getContentDescription() != null ? e.getContentDescription() : e.getText()));
        return l;
    }

    private static UiHarness newGame(FlowMode m) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        return h;
    }

    /** TTS motorunu hazır duruma getirir (gerçek cihazda asenkron). */
    private static ShadowTextToSpeech readyTts(UiHarness h) {
        h.act.speaker.force("Deneme.");
        h.idle(20);
        TextToSpeech t = ShadowTextToSpeech.getLastTextToSpeechInstance();
        assertNotNull(t);
        ShadowTextToSpeech st = shadowOf(t);
        st.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        h.idle(20);
        return st;
    }

    private static String longText() {
        StringBuilder sb = new StringBuilder("Para 5000 lira.");
        while (sb.length() < 300) sb.append(" Sipariş hazırlanıyor, sabır iyi.");
        return sb.toString();
    }

    // ================================================================== 1. konuşma süresi ve kesme
    /** TalkBack: 300 karakterlik açık okuma sürerken gelen olağan duyuru ATILMAZ (eskiden ~19 sn meşgul, 3,5 sn'de bayat). */
    @Test public void longExplicitReadDoesNotDropNormalAnnouncementsWithTalkBack() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.force(longText());
        h.idle(300);
        assertEquals(1, announcements(sam).size());
        h.act.speaker.say("Müşteri geldi: Ali.", false, Narrator.CAT_CUSTOMER, null);
        h.act.speaker.say("Ekmek ısındı.", false, Narrator.CAT_STEP, null);
        h.idle(SpeechQueue.STALE_MS - 400);
        assertTrue("bayatlamadan söylenmeli: " + announcements(sam), announcements(sam).contains("Müşteri geldi: Ali."));
        h.idle(4000);
        assertTrue(announcements(sam).toString(), announcements(sam).contains("Ekmek ısındı."));
        assertTrue("sıranın meşgul süresi bayatlık süresinden kısa olmalı", Speaker.MAX_BUSY_MS < SpeechQueue.STALE_MS);
    }

    /** TalkBack: sabır/uyarı duyurusu süren açık okumayı keser (hemen söylenir); olağan duyuru kesmez. */
    @Test public void alertCutsExplicitReadButNormalDoesNotWithTalkBack() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.force(longText());
        h.idle(300);
        assertTrue(h.act.speaker.isExplicitSpeaking());
        h.act.speaker.say("Müşteri geldi: Ali.", false, Narrator.CAT_CUSTOMER, null);
        h.idle(20);
        assertEquals("olağan duyuru okumayı kesmez", 1, announcements(sam).size());
        assertTrue(h.act.speaker.isExplicitSpeaking());
        h.act.speaker.say("Ayşe'nin sabrı kritik.", true, Narrator.CAT_PATIENCE, null);
        h.idle(20);
        List<String> got = announcements(sam);
        assertEquals("uyarı beklemeden söylenmeli: " + got, "Ayşe'nin sabrı kritik.", got.get(got.size() - 1));
        assertFalse("açık okuma kesildi", h.act.speaker.isExplicitSpeaking());
    }

    /** TTS (TalkBack kapalı): uyarı açık okumayı QUEUE_FLUSH ile keser; olağan duyuru okumanın arkasına eklenir (atılmaz). */
    @Test public void alertCutsExplicitReadWithTts() {
        talkBack(false);
        UiHarness h = new UiHarness(false);
        h.idle(1000);
        ShadowTextToSpeech tts = readyTts(h);
        h.act.speaker.force(longText());
        h.idle(50);
        assertEquals(longText(), tts.getLastSpokenText());
        assertTrue(h.act.speaker.isExplicitSpeaking());
        h.act.speaker.say("Müşteri geldi: Ali.", false, Narrator.CAT_CUSTOMER, null);
        h.idle(Speaker.MAX_BUSY_MS + 200);
        assertEquals("olağan duyuru atılmadı, okumanın arkasına eklendi", "Müşteri geldi: Ali.", tts.getLastSpokenText());
        assertEquals(TextToSpeech.QUEUE_ADD, tts.getQueueMode());
        h.act.speaker.force(longText());
        h.idle(50);
        h.act.speaker.say("Elin dolu.", true, Narrator.CAT_ALERT, null);
        h.idle(20);
        assertEquals("Elin dolu.", tts.getLastSpokenText());
        assertEquals("uyarı okumayı keser", TextToSpeech.QUEUE_FLUSH, tts.getQueueMode());
    }

    // ================================================================== 2. konuşan ekran okuyucu tanımı
    /** Dokunarak keşif KAPALI ama konuşan servis açık: TalkBack sayılır; kendi TTS'imiz konuşmaz (çift konuşma olmaz). */
    @Test public void spokenFeedbackServiceWithoutTouchExplorationCountsAsScreenReader() {
        ShadowAccessibilityManager sam = a11y();
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(false);
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        sam.setEnabledAccessibilityServiceList(Collections.singletonList(info));
        UiHarness h = new UiHarness(false);
        assertTrue(h.act.speaker.isTalkBackOn());
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.force("Hazır stok yok.");
        h.idle(300);
        assertEquals(Collections.singletonList("Hazır stok yok."), announcements(sam));
        TextToSpeech t = ShadowTextToSpeech.getLastTextToSpeechInstance();
        assertTrue("kendi TTS'i konuşmamalı", t == null || shadowOf(t).getLastSpokenText() == null);
        // erişilebilirlik açık ama konuşan servis yok (ör. yalnızca anahtar erişimi): TTS yolu
        sam.setEnabledAccessibilityServiceList(new ArrayList<AccessibilityServiceInfo>());
        assertFalse(h.act.speaker.isTalkBackOn());
    }

    // ================================================================== 3. faturalar: yerinde güncelleme, tek odak + tek duyuru
    private static void bills(SaveData s) {
        Finance.issue(s, BillType.ELEKTRIK, 300, s.day - 3);   // 1 gün gecikti
        Finance.issue(s, BillType.KIRA, 1500, s.day - 2);      // son ödeme bugün
        Finance.issue(s, BillType.SU, 120, s.day - 1);         // 1 gün kaldı
    }

    private static UiHarness billsScreen(int money) {
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.autoPayBills = false;
        s.day = 9;
        s.money = money;
        bills(s);
        h.act.show(new BillsScreen(h.act, Nav.BRIEFING)); h.settle();
        return h;
    }

    private static Bill bill(SaveData s, BillType t) {
        for (Bill b : s.bills) if (b.type == t) return b;
        throw new AssertionError("fatura yok: " + t);
    }

    @Test public void deferAndLoanConfirmUpdateRowsInPlaceAndKeepFocus() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = billsScreen(1000);
        SaveData s = h.act.save;
        BillsBox box = (BillsBox) h.field(h.screen(), "box");
        Bill kira = bill(s, BillType.KIRA);
        Button defer = box.deferButton(kira.id), loan = box.loanButton(kira.id);
        View info = box.infoView(kira.id);
        List<View> before = UiHarness.all(box);

        // Ertele: hiçbir görünüm yeniden yaratılmaz, odak düğmede kalır, tek duyuru
        assertTrue(defer.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        evBase = sam.getSentAccessibilityEvents().size();
        defer.performClick(); h.idle(300);
        assertEquals("görünümler yerinde", before, UiHarness.all(box));
        assertSame(defer, box.deferButton(kira.id));
        assertTrue("odak Ertele düğmesinde kalmalı", defer.isAccessibilityFocused());
        assertTrue(UiHarness.label(info), UiHarness.label(info).endsWith("Ertelendi."));
        assertTrue("odak taşınmadı: " + focusTexts(sam), focusTexts(sam).isEmpty());
        assertEquals(1, announcements(sam).size());
        assertTrue(announcements(sam).get(0), announcements(sam).get(0).startsWith("Ertelendi: kira."));

        // Krediyle öde, ilk dokunuş: AYNI düğme "Onayla"ya döner; odak üzerinde, yeniden aramak gerekmez
        assertTrue(loan.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        evBase = sam.getSentAccessibilityEvents().size();
        loan.performClick(); h.idle(300);
        assertEquals(before, UiHarness.all(box));
        assertSame(loan, box.loanButton(kira.id));
        assertTrue("odak onay düğmesinde", loan.isAccessibilityFocused());
        assertEquals("Emin misin? Krediyle öde: Kira", UiHarness.label(loan));
        assertEquals("Onayla: krediyle öde", loan.getText().toString());
        assertTrue(focusTexts(sam).toString(), focusTexts(sam).isEmpty());
        List<String> said = announcements(sam);
        assertEquals(1, said.size());
        assertTrue(said.get(0), said.get(0).startsWith("Kredi: 1500 lira, 10 gün, günlük taksit "));
        assertTrue("teklif kısa olmalı: " + said.get(0), said.get(0).length() <= 90);
        assertEquals(3, s.bills.size());

        // ikinci dokunuş (odak yerinde): ödenir; kart kalkar, odak TEK kez özet satırına, TEK duyuru
        evBase = sam.getSentAccessibilityEvents().size();
        loan.performClick(); h.idle(400);
        assertEquals(2, s.bills.size());
        assertNull(box.loanButton(kira.id));
        assertEquals("tek odak taşıması: " + focusTexts(sam), 1, focusTexts(sam).size());
        assertTrue(box.header.isAccessibilityFocused());
        assertEquals(1, announcements(sam).size());
        assertTrue(announcements(sam).get(0), announcements(sam).get(0).startsWith("Krediyle ödendi: kira, 1500 lira."));
        // kalan satırlar yeniden yaratılmadı
        assertSame(box.payButton(bill(s, BillType.SU).id), box.payButton(bill(s, BillType.SU).id));
        assertTrue(UiHarness.all(box).contains(box.infoView(bill(s, BillType.ELEKTRIK).id)));
    }

    /** Öde: önce odak (özet satırı), SONRA duyuru; odak olayı duyurudan önce gönderilir (yarış yok). */
    @Test public void payMovesFocusOnceThenAnnouncesOnce() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = billsScreen(5000);
        SaveData s = h.act.save;
        BillsBox box = (BillsBox) h.field(h.screen(), "box");
        Bill el = bill(s, BillType.ELEKTRIK);
        Button pay = box.payButton(el.id);
        View suInfo = box.infoView(bill(s, BillType.SU).id);
        assertTrue(pay.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        evBase = sam.getSentAccessibilityEvents().size();
        pay.performClick(); h.idle(400);
        assertNull(Finance.bill(s, el.id));
        int focusAt = -1, sayAt = -1, i = 0;
        for (AccessibilityEvent e : events(sam)) {
            if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED && focusAt < 0) focusAt = i;
            if (e.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && sayAt < 0) sayAt = i;
            i++;
        }
        assertTrue("odak sonra duyuru: odak=" + focusAt + " duyuru=" + sayAt, focusAt >= 0 && sayAt > focusAt);
        assertEquals(1, focusTexts(sam).size());
        assertEquals(1, announcements(sam).size());
        assertTrue(announcements(sam).get(0).startsWith("Elektrik faturası ödendi"));
        assertTrue(box.header.isAccessibilityFocused());
        assertTrue(UiHarness.label(box.header), UiHarness.label(box.header).startsWith("Açık fatura 2, borç "));
        assertSame("diğer satırlar yerinde", suInfo, box.infoView(bill(s, BillType.SU).id));
    }

    /** Fatura satırı kısa (≤60) + tek cümle etki; uzun açıklama satıra dokununca (ayrıntı); "Faturaları oku" özet. */
    @Test public void billRowsAreShortAndDetailIsOnDemand() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = billsScreen(1000);
        SaveData s = h.act.save;
        BillsBox box = (BillsBox) h.field(h.screen(), "box");
        Bill el = bill(s, BillType.ELEKTRIK);
        View info = box.infoView(el.id);
        assertEquals("Elektrik faturası " + el.amountDue(s.day) + " lira, 1 gün gecikti", UiHarness.label(info));
        assertNotNull(h.findText("Elektrik kısmen kesik. Ödersen hemen düzelir."));
        assertNotNull(h.findText("Ödemezsen yarın ev sahibi ihtar çeker."));
        for (View v : UiHarness.visibleViews(box)) assertTrue(UiHarness.label(v), UiHarness.label(v).length() <= 80);
        evBase = sam.getSentAccessibilityEvents().size();
        info.performClick(); h.idle(200);
        assertTrue(announcements(sam).toString(), announcements(sam).get(0).startsWith("Elektrik faturası 1 gündür ödenmedi"));
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Faturaları oku"); h.idle(200);
        String read = announcements(sam).get(0);
        assertTrue(read, read.startsWith("3 açık fatura, toplam "));
        assertTrue(read, read.contains("En acili: Elektrik faturası"));
        assertFalse("özet tek tek faturaları saymaz: " + read, read.contains("Su faturası"));
        assertTrue("özet kısa: " + read.length(), read.length() <= 160);
    }

    // ================================================================== 4. banka: odak yerinde
    @Test public void bankTakeLoanKeepsFocusAndRepayMovesFocusToLoanList() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 8;
        s.money = 20_000;
        h.act.show(new BankScreen(h.act, Nav.BRIEFING)); h.settle();
        assertNotNull(h.findText("Kredi notu 60. Alınabilir "));
        View take = h.findLabel("Kredi al: ");
        assertTrue(take.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        evBase = sam.getSentAccessibilityEvents().size();
        take.performClick(); h.idle(300);
        assertEquals(1, s.loans.size());
        assertTrue("Kredi al düğmesi yerinde, odak üzerinde", take.isAccessibilityFocused());
        assertTrue(focusTexts(sam).toString(), focusTexts(sam).isEmpty());
        assertEquals(1, announcements(sam).size());
        assertTrue(announcements(sam).get(0).startsWith("Kredi alındı: "));
        // erken kapat: kart kalkar; odak listenin ilk satırına ("Kredi borcun yok."), tek duyuru
        View close = h.findLabel("Erken kapat: ");
        assertTrue(close.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        evBase = sam.getSentAccessibilityEvents().size();
        close.performClick(); h.idle(400);
        assertTrue(s.loans.isEmpty());
        assertEquals(focusTexts(sam).toString(), 1, focusTexts(sam).size());
        assertTrue(focusTexts(sam).get(0), focusTexts(sam).get(0).contains("Kredi borcun yok."));
        assertEquals(1, announcements(sam).size());
        assertTrue(announcements(sam).get(0).startsWith("Kredi kapatıldı: "));
    }

    // ================================================================== 5. brifing: kısa ve tek sefer
    @Test public void briefingSpeaksShortSummaryOnceAndGameDoesNotRepeatIt() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.autoPayBills = false;
        s.day = 9;
        s.money = 30_000;
        Shop.buyRecommended(s);
        Finance.issue(s, BillType.ELEKTRIK, 300, 5);
        Finance.issue(s, BillType.DOGALGAZ, 250, 5);
        Finance.issue(s, BillType.SU, 120, 5);
        Finance.issue(s, BillType.KIRA, 1500, 6);
        String full = GameScreen.joinNotices(Finance.notices(s, s.day));
        assertTrue("uzun metin gerçekten uzun: " + full.length(), full.length() > 350);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.startBriefing(); h.settle();
        h.idle(1500);
        List<String> said = announcements(sam);
        assertEquals("brifing uyarıları tek duyuru: " + said, 1, said.size());
        assertTrue(said.get(0), said.get(0).startsWith("4 fatura uyarısı. En acili: "));
        assertTrue("kısa olmalı: " + said.get(0).length(), said.get(0).length() <= 200);
        // uzun metinler ekranda (kartlarda) durur
        assertNotNull(h.findText("Elektrik faturası 2 gündür ödenmedi"));
        // gün açılışı: motor aynı uyarıları bildirir; oyun ekranı yeniden SÖYLEMEZ (görsel not kalır)
        evBase = sam.getSentAccessibilityEvents().size();
        h.click("Güne başla"); h.settle();
        h.run(3000);
        for (String a : announcements(sam)) {
            assertFalse("uzun uyarı yinelenmemeli: " + a, a.contains("gündür ödenmedi"));
            assertFalse("kısa özet de yinelenmemeli: " + a, a.contains("fatura uyarısı"));
        }
        assertNotNull("ekranda not", h.findText("gündür ödenmedi"));
    }

    /** Brifing atlandıysa (özet > market > Güne başla) oyun ekranı uyarıyı KISA söyler (uzun birleşik metni değil). */
    @Test public void gameSpeaksShortNoticeWhenBriefingWasSkipped() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.autoPayBills = false;
        s.day = 9;
        s.money = 30_000;
        Shop.buyRecommended(s);
        Finance.issue(s, BillType.ELEKTRIK, 300, 5);
        Finance.issue(s, BillType.SU, 120, 5);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.show(new GameScreen(h.act)); h.settle();
        h.run(4000);
        int brief = 0;
        for (String a : announcements(sam)) {
            assertFalse(a, a.contains("gündür ödenmedi"));
            if (a.startsWith("2 fatura uyarısı.")) brief++;
        }
        assertEquals(announcements(sam).toString(), 1, brief);
    }

    /** 2. günden itibaren her gün aynı olan satırlar "Ayrıntıları göster" altında; dokununca açılır, odak ilk satıra. */
    @Test public void briefingCollapsesBoilerplateBehindDetailsButton() {
        talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        h.act.save.day = 5;
        h.act.save.flowChosen = true;
        h.act.startBriefing(); h.settle();
        assertNotNull(h.findText("Kasada "));
        assertNull("açıklama satırı katlı", h.findText("Önce hazırlık aşaması gelir"));
        assertNull(h.findText("Hedef: yıldızın"));
        View more = h.findLabel("Ayrıntıları göster");
        assertNotNull(more);
        more.performClick(); h.idle(300);
        assertNotNull(h.findText("Önce hazırlık aşaması gelir"));
        assertNotNull(h.findText("Hedef: yıldızın"));
        assertNull(h.findLabel("Ayrıntıları göster"));
        A11yAudit a = A11yAudit.run(h, "Brifing(ayrıntılar)");
        assertTrue(a.report(), a.errors.isEmpty());
        // 1. gün: yeni oyuncu her şeyi görür
        h.act.save.day = 1;
        h.act.startBriefing(); h.settle();
        assertNotNull(h.findText("Önce hazırlık aşaması gelir"));
        assertNull(h.findLabel("Ayrıntıları göster"));
    }

    // ================================================================== 6. ekran geçişi: başlık bir kez
    @Test public void screenChangeWithTalkBackFocusesFirstContentNotTheTitle() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.show(new BillsScreen(h.act, Nav.BRIEFING)); h.settle();
        View title = h.screen().titleTarget();
        assertNotNull(title);
        assertFalse("başlık odakta olursa pencere başlığıyla iki kez okunur", title.isAccessibilityFocused());
        List<String> f = focusTexts(sam);
        assertEquals(f.toString(), 1, f.size());
        assertTrue(f.get(0), f.get(0).startsWith("Açık fatura yok."));
        // pencere başlığı yerinde (TalkBack bunu okur)
        assertEquals("Faturalar", String.valueOf(((View) h.field(h.act, "currentView")).getAccessibilityPaneTitle()));
        for (String a : announcements(sam)) assertNotEquals("başlık ayrıca duyurulmaz", "Faturalar", a);
    }

    /** TalkBack kapalı: başlık odakta ve TTS ile bir kez söylenir (eski davranış). */
    @Test public void screenChangeWithoutTalkBackStillFocusesAndSpeaksTitle() {
        talkBack(false);
        UiHarness h = newGame(FlowMode.DENGELI);
        ShadowTextToSpeech tts = readyTts(h);
        h.act.show(new BillsScreen(h.act, Nav.BRIEFING)); h.settle();
        assertTrue(tts.getSpokenTextList().toString(), tts.getSpokenTextList().contains("Faturalar"));
        assertEquals("başlık bir kez", 1, Collections.frequency(tts.getSpokenTextList(), "Faturalar"));
    }

    // ================================================================== 7. Acil al odağı
    @Test public void emergencyDoesNotStealFocusFromActionRowImmediatelyAndReturnsItAfterBuying() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.money = 20_000;
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(300);
        h.selectOrder(o); h.run(300);
        Button btn = (Button) h.field(h.screen(), "emergencyBtn");
        View row = h.access().actionRow(0);
        assertEquals(View.VISIBLE, row.getVisibility());
        for (String id : new ArrayList<>(o.nextStep().needs.keySet())) e.getSave().inventory.set(id, 0);
        assertTrue(row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        row.performClick(); h.run(300);
        assertEquals("Acil al belirdi", View.VISIBLE, btn.getVisibility());
        assertTrue("odak hemen çalınmaz", row.isAccessibilityFocused());
        assertFalse(btn.isAccessibilityFocused());
        // oyuncu aynı satırda bekliyor: gecikmeden sonra odak Acil al'a gelir
        h.run(GameScreen.EMERGENCY_FOCUS_DELAY_MS + 300);
        assertTrue("bekleyince odak Acil al düğmesinde", btn.isAccessibilityFocused());
        // satın alınca düğme kalkar: odak geldiği satıra döner (kaybolmaz)
        btn.performClick(); h.run(400);
        assertEquals(View.GONE, btn.getVisibility());
        assertTrue("odak eylem satırına döndü", row.isAccessibilityFocused());
    }

    @Test public void emergencyFocusIsNotTakenIfPlayerMovedOn() {
        talkBack(true);
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(300);
        h.selectOrder(o); h.run(300);
        Button btn = (Button) h.field(h.screen(), "emergencyBtn");
        View row = h.access().actionRow(0), other = h.access().hands();
        for (String id : new ArrayList<>(o.nextStep().needs.keySet())) e.getSave().inventory.set(id, 0);
        assertTrue(row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        row.performClick(); h.run(300);
        assertEquals(View.VISIBLE, btn.getVisibility());
        // oyuncu başka yere gezindi
        assertTrue(other.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        h.run(GameScreen.EMERGENCY_FOCUS_DELAY_MS + 500);
        assertTrue("gezinen oyuncunun odağı çalınmaz", other.isAccessibilityFocused());
        assertFalse(btn.isAccessibilityFocused());
    }

    // ================================================================== 8. ilk fatura öğreticisi, otomatik ödeme, iflas
    private static DaySummary summaryWithBill(SaveData s) {
        DaySummary d = new DaySummary();
        d.day = s.day - 1;
        d.endStars = d.startStars = 3;
        d.moneyAfter = s.money;
        Finance.issue(s, BillType.ELEKTRIK, 231, d.day);
        d.newBills.add(s.bills.get(s.bills.size() - 1));
        return d;
    }

    @Test public void firstBillTipIsShownAndSpokenOnce() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 4;
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.show(new SummaryScreen(h.act, summaryWithBill(s))); h.settle();
        h.idle(1500);
        String tip = "İlk faturan geldi: elektrik faturası 231 lira. Son ödeme gününde kasadan otomatik ödenir; paran yetmezse Faturalar düğmesinden ödersin.";
        assertNotNull("görsel kart", h.findText(tip));
        assertTrue(announcements(sam).toString(), announcements(sam).contains(tip));
        A11yAudit a = A11yAudit.run(h, "Özet(ilk fatura)");
        assertTrue(a.report(), a.errors.isEmpty());
        // ikinci kez gösterilmez
        h.act.show(new SummaryScreen(h.act, summaryWithBill(s))); h.settle();
        assertNull(h.findText("İlk faturan geldi"));
        // otomatik ödeme kapalıyken metin el ile ödemeyi anlatır; yeni oyunda öğretici yeniden açılır
        h.act.newGame();
        h.act.save.autoPayBills = false;
        h.act.save.day = 4;
        h.act.show(new SummaryScreen(h.act, summaryWithBill(h.act.save))); h.settle();
        assertNotNull(h.findText("Faturalar düğmesinden Öde; iki gün içinde ödersen sorun yok."));
    }

    @Test public void settingsHasAutoPaySwitchThatWritesTheSave() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        assertTrue("varsayılan açık", h.act.save.autoPayBills);
        h.click("Ayarlar"); h.settle();
        CompoundButton sw = null;
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v instanceof CompoundButton && "Faturaları otomatik öde".contentEquals(((CompoundButton) v).getText())) sw = (CompoundButton) v;
        }
        assertNotNull(sw);
        assertTrue(sw.isChecked());
        assertTrue("≥56 dp", sw.getHeight() >= Ui.dp(h.act, 56));
        assertNotNull(h.findText(Texts.AUTO_PAY_NOTE));
        evBase = sam.getSentAccessibilityEvents().size();
        sw.performClick(); h.idle(200);
        assertFalse(h.act.save.autoPayBills);
        assertTrue(announcements(sam).toString(), announcements(sam).get(0).startsWith("Otomatik ödeme kapalı"));
        // kayda yazıldı
        h.act.reloadSave();
        assertFalse(h.act.save.autoPayBills);
        sw.performClick(); h.idle(200);
        assertTrue(h.act.save.autoPayBills);
        A11yAudit a = A11yAudit.run(h, "Ayarlar(otomatik ödeme)");
        assertTrue(a.report(), a.errors.isEmpty());
    }

    /** Otomatik ödeme açık: brifing geciken faturayı öder, kısa söyler; kesinti uyarısı kalmaz. */
    @Test public void briefingAutoPaysLateBillsAndSaysSo() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 9;
        s.money = 30_000;
        Shop.buyRecommended(s);
        Finance.issue(s, BillType.ELEKTRIK, 300, 5);
        int due = s.bills.get(0).amountDue(s.day), money = s.money;
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.startBriefing(); h.settle();
        h.idle(1000);
        assertTrue(s.bills.isEmpty());
        assertEquals(money - due, s.money);
        assertNotNull(h.findText("Otomatik ödendi: elektrik faturası " + due + " lira."));
        assertNull(h.findText("gündür ödenmedi"));
        assertTrue(announcements(sam).toString(), announcements(sam).contains("Otomatik ödendi: elektrik faturası " + due + " lira."));
    }

    @Test public void bankruptSummaryShowsShopClosedAndOnlyOffersNewGame() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 30;
        s.bestDay = 30;
        s.money = 0;
        s.speechRate = 1.5;
        DaySummary d = new DaySummary();
        d.day = 29;
        d.failed = true;
        d.bankruptcy = Finance.Bankruptcy.NO_MEANS;
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.show(new SummaryScreen(h.act, d)); h.settle();
        h.idle(500);
        String text = Texts.bankruptcyText(Finance.Bankruptcy.NO_MEANS, 29);
        assertTrue(text, text.startsWith("Dükkân kapandı. Stok ve para bitti"));
        assertNotNull(h.findText(text));
        assertTrue(announcements(sam).toString(), announcements(sam).contains(text));
        assertNull("iflasta aynı günü deneme yok", h.findLabel("Aynı günü tekrar dene"));
        assertNull(h.findLabel("Markete git"));
        assertNotNull(h.findLabel("Ana menü"));
        A11yAudit a = A11yAudit.run(h, "Özet(iflas)");
        assertTrue(a.report(), a.errors.isEmpty());
        h.click("Yeni oyun"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        assertEquals(1, h.act.save.day);
        assertEquals(SaveData.START_MONEY, h.act.save.money);
        assertEquals("en iyi gün korunur", 30, h.act.save.bestDay);
        assertEquals("ayarlar korunur", 1.5, h.act.save.speechRate, 1e-9);
    }

    /** Brifing: motorun iflas denetimi iflas diyorsa "Güne başla" yok, "Yeni oyun" var. */
    @Test public void bankruptBriefingReplacesStartWithNewGame() {
        talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 20;
        s.money = 0;
        for (com.doner.oyunu.engine.Ingredient i : com.doner.oyunu.engine.Catalog.ingredients()) s.inventory.set(i.id, 0);
        s.readyDrinks = 0;
        // kredi ve acil destek yolu kapalı: 3 kredi + açık acil destek
        s.money = 0;
        while (Finance.canRescue(s)) {
            if (com.doner.oyunu.engine.Bank.emergencyAidAvailable(s)) com.doner.oyunu.engine.Bank.takeEmergencyAid(s);
            else if (com.doner.oyunu.engine.Bank.takeLoan(s, com.doner.oyunu.engine.Bank.MIN_LOAN, 5) != com.doner.oyunu.engine.Bank.Result.OK) break;
            s.money = 0;
        }
        org.junit.Assume.assumeTrue("motor bu durumu iflas saymalı", Finance.bankruptcy(s) != Finance.Bankruptcy.NONE);
        h.act.startBriefing(); h.settle();
        assertNotNull(h.findText("Dükkân kapandı."));
        assertNull(h.findLabel("Güne başla"));
        assertNotNull(h.findLabel("Yeni oyun"));
        A11yAudit a = A11yAudit.run(h, "Brifing(iflas)");
        assertTrue(a.report(), a.errors.isEmpty());
    }

    /** Kasa stoğa yetmiyor, banka limiti de yok: brifing "Acil destek kredisi al" sunar; alınca para kasaya girer. */
    @Test public void briefingOffersEmergencyAidWhenCashCannotCoverStock() {
        talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 12;
        s.money = 0;
        for (com.doner.oyunu.engine.Ingredient i : com.doner.oyunu.engine.Catalog.ingredients()) s.inventory.set(i.id, 0);
        // normal limit kalmasın
        while (com.doner.oyunu.engine.Bank.available(s) >= com.doner.oyunu.engine.Bank.MIN_LOAN
                && com.doner.oyunu.engine.Bank.takeLoan(s, com.doner.oyunu.engine.Bank.MIN_LOAN, 5) == com.doner.oyunu.engine.Bank.Result.OK) s.money = 0;
        org.junit.Assume.assumeTrue(com.doner.oyunu.engine.Bank.emergencyAidAvailable(s));
        int amt = com.doner.oyunu.engine.Bank.emergencyAidAmount(s);
        h.act.startBriefing(); h.settle();
        View b = h.findLabel("Acil destek kredisi al: " + amt + " lira");
        assertNotNull(h.clickables().toString(), b);
        A11yAudit a = A11yAudit.run(h, "Brifing(acil destek)");
        assertTrue(a.report(), a.errors.isEmpty());
        b.performClick(); h.settle();
        assertEquals(amt, s.money);
        assertTrue(h.screen() instanceof BriefingScreen);
        assertNull("destek alındı: düğme kalkar", h.findLabel("Acil destek kredisi al"));
    }
}
