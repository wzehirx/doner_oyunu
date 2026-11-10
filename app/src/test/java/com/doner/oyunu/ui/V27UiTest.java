package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

import com.doner.oyunu.audio.Speaker;
import com.doner.oyunu.engine.Bank;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.Importance;
import com.doner.oyunu.engine.NarrationLevel;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.Pricing;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.SaveData;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v2.7 arayüz: (1) anlatım düzeyi filtreleri (Az / Orta / Çok) ve TalkBack açık/kapalı farkı, açık okumalar her zaman;
 * (2) Ayarlar'da Anlatım düzeyi seçimi; (3) AdjustControl'ün Fiyatlar, Banka ve Ayarlar ekranlarında klavye, çubuk ve
 * TalkBack eylemleriyle kullanımı.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class V27UiTest {
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
            if (e.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !e.getText().isEmpty())
                l.add(Speaker.stripMark(e.getText().get(0).toString()));
        return l;
    }

    private static UiHarness newGame(FlowMode m) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        return h;
    }

    private static void level(UiHarness h, NarrationLevel l) {
        h.act.save.narrationLevel = l;
        h.act.applySettings();
    }

    private static SaveData stored() {
        String s = RuntimeEnvironment.getApplication().getSharedPreferences("doner_oyunu", Context.MODE_PRIVATE).getString("save", null);
        return SaveData.parse(s);
    }

    private static A11yAudit audit(UiHarness h, String where) {
        h.idle(100);
        A11yAudit a = A11yAudit.run(h, where);
        assertTrue(where + ": denetlenen etkileşimli görünüm yok", a.checkedInteractive > 0);
        assertTrue("Erişilebilirlik hataları:\n" + a.report(), a.errors.isEmpty());
        return a;
    }

    private static boolean anyStartsWith(List<String> l, String prefix) {
        for (String s : l) if (s.startsWith(prefix)) return true;
        return false;
    }

    // ================================================================== 1. Speaker süzgeci
    /** TalkBack AÇIK + Az: yalnızca kritik; açık okuma istekleri her zaman. */
    @Test public void lowLevelWithTalkBackSpeaksOnlyCriticalAndExplicitReads() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        level(h, NarrationLevel.LOW);
        Speaker sp = h.act.speaker;
        assertTrue(sp.isTalkBackOn());
        assertEquals(NarrationLevel.LOW, sp.effectiveLevel());
        evBase = sam.getSentAccessibilityEvents().size();
        sp.say("Yeni müşteri: Ali", false, Narrator.CAT_CUSTOMER, null, Importance.CRITICAL); h.idle(4000);
        sp.say("Teslim edildi", false, Narrator.CAT_ORDER, null, Importance.NORMAL); h.idle(4000);
        sp.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null, Importance.VERBOSE); h.idle(4000);
        sp.say("Öğretici. Dokun", false, Narrator.CAT_TUTORIAL, null); h.idle(4000);   // öğretici: varsayılan ayrıntı
        sp.announce("Sekme açıldı", true, Importance.VERBOSE); h.idle(4000);
        sp.announce("Stok yok", true, Importance.CRITICAL); h.idle(4000);
        sp.result("Kredi alındı", Importance.NORMAL); h.idle(4000);
        sp.result("Kredi alınamadı", Importance.CRITICAL); h.idle(4000);
        sp.force("Durumu oku metni"); h.idle(4000);                                  // açık okuma isteği
        sp.say("Hedef metni", true, Narrator.CAT_INFO, null, Importance.VERBOSE); h.idle(4000); // "Hedefi oku": açık
        sp.speakLong("Yardım metni", true); h.idle(4000);
        assertEquals(Arrays.asList("Yeni müşteri: Ali", "Stok yok", "Kredi alınamadı", "Durumu oku metni", "Hedef metni", "Yardım metni"),
                announcements(sam));
    }

    @Test public void mediumLevelAddsResultsButNotChatter() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        level(h, NarrationLevel.MEDIUM);
        Speaker sp = h.act.speaker;
        evBase = sam.getSentAccessibilityEvents().size();
        sp.say("Yeni müşteri", false, Narrator.CAT_CUSTOMER, null, Importance.CRITICAL); h.idle(4000);
        sp.say("Teslim edildi", false, Narrator.CAT_ORDER, null, Importance.NORMAL); h.idle(4000);
        sp.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null, Importance.VERBOSE); h.idle(4000);
        sp.result("Kredi alındı", Importance.NORMAL); h.idle(4000);
        sp.result("Sekme", Importance.VERBOSE); h.idle(4000);
        assertEquals(Arrays.asList("Yeni müşteri", "Teslim edildi", "Kredi alındı"), announcements(sam));
    }

    @Test public void highLevelSpeaksEverythingLikeBefore() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        level(h, NarrationLevel.HIGH);
        Speaker sp = h.act.speaker;
        evBase = sam.getSentAccessibilityEvents().size();
        sp.say("Yeni müşteri", false, Narrator.CAT_CUSTOMER, null, Importance.CRITICAL); h.idle(4000);
        sp.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null, Importance.VERBOSE); h.idle(4000);
        sp.say("Öğretici. Şunu yap", false, Narrator.CAT_TUTORIAL, null); h.idle(4000);
        sp.announce("Sekme açıldı", true, Importance.VERBOSE); h.idle(4000);
        assertEquals(Arrays.asList("Yeni müşteri", "Seçilen sipariş 1", "Öğretici. Şunu yap", "Sekme açıldı"), announcements(sam));
    }

    /** TalkBack KAPALI: ayar ne olursa olsun mevcut davranış (her şey kendi TTS'inle söylenir; announceForAccessibility yok). */
    @Test public void talkBackOffKeepsTheOldBehaviourWhateverTheSetting() {
        ShadowAccessibilityManager sam = talkBack(false);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        Speaker sp = h.act.speaker;
        assertFalse(sp.isTalkBackOn());
        for (NarrationLevel l : NarrationLevel.values()) {
            level(h, l);
            assertEquals("TalkBack kapalı: etkin düzey her zaman Çok", NarrationLevel.HIGH, sp.effectiveLevel());
            assertTrue(sp.allows(Importance.VERBOSE));
            sp.stop();
            assertFalse(sp.isBusy());
            sp.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null, Importance.VERBOSE);
            assertTrue("ayrıntı duyuru TTS ile söylenir (" + l + ")", sp.isBusy());
            sp.stop();
            sp.say("Öğretici. Şunu yap", false, Narrator.CAT_TUTORIAL, null);
            assertTrue("öğretici söylenir (" + l + ")", sp.isBusy());
            sp.stop();
            sp.announce("Sekme açıldı", true, Importance.VERBOSE);
            assertTrue(sp.isBusy());
            sp.stop();
        }
        evBase = 0;
        assertTrue(announcements(sam).isEmpty());
    }

    /** Aynı ayrıntı duyuru: TalkBack açık+Az sessiz, TalkBack kapalı+Az konuşur (fark). */
    @Test public void sameSettingDiffersBetweenTalkBackOnAndOff() {
        talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        level(h, NarrationLevel.LOW);
        Speaker sp = h.act.speaker;
        sp.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null, Importance.VERBOSE);
        assertFalse("TalkBack açık + Az: sessiz", sp.isBusy());
        talkBack(false);
        assertFalse(sp.isTalkBackOn());
        sp.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null, Importance.VERBOSE);
        assertTrue("TalkBack kapalı + Az: yine söylenir", sp.isBusy());
    }

    @Test public void speechSwitchOffStillSilencesGameNarrationButNotExplicitReads() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = new UiHarness(false);
        h.idle(3000);
        level(h, NarrationLevel.HIGH);
        h.act.speaker.setEnabled(false);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.say("Yeni müşteri", false, Narrator.CAT_CUSTOMER, null, Importance.CRITICAL); h.idle(3000);
        assertTrue(announcements(sam).isEmpty());
        h.act.speaker.force("Durumu oku metni"); h.idle(1000);
        assertEquals(Collections.singletonList("Durumu oku metni"), announcements(sam));
    }

    // ================================================================== 2. gerçek oyun: Az ile rutin sessiz
    private List<String> playScenario(NarrationLevel lvl) {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        level(h, lvl);
        h.startDay();
        assertTrue(h.screen() instanceof GameScreen);
        h.run(9000); // gün açılış duyuruları bitsin
        evBase = sam.getSentAccessibilityEvents().size();
        Order o = h.engine().spawnOrder(Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(1500);
        h.selectOrder(o);
        h.run(4000);
        return announcements(sam);
    }

    @Test public void realGameLowSaysNewCustomerButNotSelectionEchoOrHints() {
        List<String> low = playScenario(NarrationLevel.LOW);
        assertTrue("yeni müşteri söylenir: " + low, anyStartsWith(low, "Yeni müşteri"));
        assertFalse("seçim yankısı susar: " + low, anyStartsWith(low, "Seçilen sipariş"));
        assertFalse("önerilen adım ipucu susar: " + low, anyStartsWith(low, "Önerilen"));
        assertTrue("Az: yalnızca kritik (en çok yeni müşteri + adım bitti gibi): " + low, low.size() <= 3);
    }

    @Test public void realGameHighStillNarratesSelectionAndHints() {
        List<String> high = playScenario(NarrationLevel.HIGH);
        assertTrue("yeni müşteri: " + high, anyStartsWith(high, "Yeni müşteri"));
        assertTrue("seçim yankısı (eski davranış): " + high, anyStartsWith(high, "Seçilen sipariş"));
        assertTrue("Çok: yeni müşteri + iki seçim yankısı + ipucu: " + high, high.size() >= 3);
    }

    // ================================================================== 3. Ayarlar: Anlatım düzeyi
    @Test public void settingsOffersNarrationLevelAndPersistsIt() {
        talkBack(true);
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        assertNotNull("başlık", h.findText("Anlatım düzeyi"));
        assertNotNull("açıklama", h.findText("TalkBack açıkken oyun yalnızca seçtiğin düzeyde konuşur"));
        assertNotNull(h.findLabel("Az: yalnızca önemli olaylar"));
        assertNotNull(h.findLabel("Orta: önemli olaylar ve sonuçlar"));
        assertNotNull("harness Çok kurar", h.findLabel("Çok: her şey, ipuçları dahil, seçili"));
        assertTrue(A11yAudit.run(h, "Ayarlar (düzey)").checkedSelected >= 2);
        audit(h, "Ayarlar (düzey)");
        h.click("Orta: "); h.idle(200);
        assertEquals(NarrationLevel.MEDIUM, h.act.save.narrationLevel);
        assertEquals("kayda yazılır", NarrationLevel.MEDIUM, stored().narrationLevel);
        assertEquals("hemen uygulanır", NarrationLevel.MEDIUM, h.act.speaker.getNarrationLevel());
        assertNotNull(h.findLabel("Orta: önemli olaylar ve sonuçlar, seçili"));
        h.click("Az: "); h.idle(200);
        assertEquals(NarrationLevel.LOW, h.act.save.narrationLevel);
        assertNotNull(h.findLabel("Az: yalnızca önemli olaylar, seçili"));
        assertNull("yalnızca biri seçili", h.findLabel("Orta: önemli olaylar ve sonuçlar, seçili"));
        h.act.reloadSave();
        assertEquals("yeniden yüklemede korunur", NarrationLevel.LOW, h.act.save.narrationLevel);
        assertEquals(NarrationLevel.LOW, h.act.speaker.getNarrationLevel());
    }

    @Test public void freshInstallDefaultsToLowWhichOnlyMattersWithTalkBack() {
        talkBack(true);
        com.doner.oyunu.MainActivity act = Robolectric.buildActivity(com.doner.oyunu.MainActivity.class).setup().get();
        assertEquals("varsayılan Az", NarrationLevel.LOW, act.save.narrationLevel);
        assertEquals(NarrationLevel.LOW, act.speaker.getNarrationLevel());
        assertEquals("TalkBack açık: yalnızca kritik", NarrationLevel.LOW, act.speaker.effectiveLevel());
        talkBack(false);
        assertEquals("TalkBack kapalı: eski davranış", NarrationLevel.HIGH, act.speaker.effectiveLevel());
    }

    // ================================================================== 4. AdjustControl: Fiyatlar
    private static Dialog dialog() { return ShadowDialog.getLatestDialog(); }

    private static EditText edit(Dialog d) {
        for (View v : UiHarness.all(d.getWindow().getDecorView())) if (v instanceof EditText) return (EditText) v;
        throw new AssertionError("EditText yok");
    }

    private static void type(String text) {
        Dialog d = dialog();
        edit(d).setText(text);
        for (View v : UiHarness.all(d.getWindow().getDecorView()))
            if (v instanceof Button && ((Button) v).getText().toString().equals("Tamam")) { v.performClick(); return; }
        throw new AssertionError("Tamam yok");
    }

    private static SeekBar seek(UiHarness h, String name) {
        for (View v : UiHarness.all(h.root()))
            if (v instanceof SeekBar && name.equals(String.valueOf(v.getContentDescription()))) return (SeekBar) v;
        return null;
    }

    private UiHarness priceScreen() {
        UiHarness h = newGame(FlowMode.DENGELI);
        h.act.save.day = 6;
        h.act.startBriefing(); h.settle();
        h.click("Fiyatlar"); h.settle();
        assertTrue(h.screen() instanceof PriceScreen);
        return h;
    }

    @Test public void priceTypedPercentAnyWholeNumberClampedAndSaved() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = priceScreen();
        level(h, NarrationLevel.LOW); // denetimin kendi duyurusu düzeyden bağımsızdır
        SaveData s = h.act.save;
        Product ayran = Catalog.product("ayran");
        View info = h.findLabel("Ayran, ");
        assertNotNull(info);
        View label = h.findLabel("Ayran fiyatı: yüzde 100");
        assertNotNull("değer etiketi (dokununca klavye): " + h.clickables(), label);
        evBase = sam.getSentAccessibilityEvents().size();
        label.performClick(); h.idle(100);
        type("112"); h.run(1200); // duyuru: geri sayım + TalkBack gecikmesi zincirli
        assertEquals("her tam yüzde kabul (5'in katı değil)", 112, Pricing.percent(s, "ayran"));
        assertTrue("kayda yazıldı", s.serialize().contains("price.ayran=112"));
        assertEquals(112, stored().prices.get("ayran").intValue());
        assertNotNull("satır güncellendi", h.findLabel("Ayran, " + Pricing.priceOf(s, ayran) + " lira, yüzde 112"));
        assertSame("satır görünümü yeniden kurulmadı (odak kaybı yok)", info, h.findLabel("Ayran, "));
        assertTrue("yeni fiyat söylendi: " + announcements(sam), anyStartsWith(announcements(sam), "Ayran: "));
        // aralık dışı: kırp + uyar
        evBase = sam.getSentAccessibilityEvents().size();
        h.findLabel("Ayran fiyatı: yüzde 112").performClick(); h.idle(100);
        type("300"); h.run(1200);
        assertEquals(150, Pricing.percent(s, "ayran"));
        assertTrue(announcements(sam).toString(), announcements(sam).contains("Değer 50 ile 150 arasında olmalı. yüzde 150, " + Math.round(ayran.price * 1.5) + " lira yapıldı."));
        h.findLabel("Ayran fiyatı: yüzde 150").performClick(); h.idle(100);
        type("0"); h.idle(900);
        assertEquals(50, Pricing.percent(s, "ayran"));
        // referans fiyata dön çubukları ve etiketleri de günceller
        h.click("Referans fiyata dön"); h.idle(200);
        assertNotNull(h.findLabel("Ayran fiyatı: yüzde 100"));
        assertEquals(100, seek(h, "Ayran fiyatı").getProgress() + Pricing.MIN_PERCENT);
        assertTrue(Pricing.allDefault(s));
        assertTrue("fiyat satırında kaydırma çubuğu denetlendi", audit(h, "Fiyatlar").checkedSeekBars >= Catalog.productsForDay(6).size());
    }

    @Test public void priceSeekBarTalkBackActionsAndRowActionsStayInSync() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = priceScreen();
        SaveData s = h.act.save;
        SeekBar sb = seek(h, "Ayran fiyatı");
        assertNotNull(sb);
        AccessibilityNodeInfo info = sb.createAccessibilityNodeInfo();
        assertNotNull(info.getRangeInfo());
        assertEquals("yüzde 100, 25 lira", String.valueOf(info.getStateDescription()).replaceAll("^yüzde 100, \\d+ lira$", "yüzde 100, 25 lira"));
        evBase = sam.getSentAccessibilityEvents().size();
        assertTrue(sb.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertEquals(105, Pricing.percent(s, "ayran"));
        assertTrue(sb.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertEquals(110, Pricing.percent(s, "ayran"));
        assertTrue(sb.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
        assertEquals(105, Pricing.percent(s, "ayran"));
        h.idle(1500);
        assertTrue("TalkBack değeri kendisi okur, oyun ikinci duyuru yapmaz: " + announcements(sam), announcements(sam).isEmpty());
        assertEquals("kayda yazıldı", 105, stored().prices.get("ayran").intValue());
        // satırın TalkBack Eylemleri de aynı basamakla çalışır
        View row = h.findLabel("Ayran, ");
        List<String> acts = A11yAudit.customActionLabels(row);
        row.performAccessibilityAction(ActionMenu.ACTION_BASE + acts.indexOf("Fiyatı artır"), null); h.idle(100);
        assertEquals(110, Pricing.percent(s, "ayran"));
        assertEquals("çubuk da taşındı", 60, sb.getProgress());
        // sürükleme: bırakınca kesinleşir ve kaydedilir
        SeekBar.OnSeekBarChangeListener l = shadowOf(sb).getOnSeekBarChangeListener();
        l.onStartTrackingTouch(sb);
        sb.setProgress(70);
        l.onProgressChanged(sb, 70, true);
        assertEquals("sürüklerken canlı", 120, Pricing.percent(s, "ayran"));
        l.onStopTrackingTouch(sb);
        assertEquals(120, stored().prices.get("ayran").intValue());
    }

    // ================================================================== 5. AdjustControl: Banka
    @Test public void bankAmountAndTermAreAdjustControlsWithKeyboard() {
        talkBack(true);
        UiHarness h = newGame(FlowMode.DENGELI);
        SaveData s = h.act.save;
        s.day = 8;
        s.money = 20_000;
        h.act.startBriefing(); h.settle();
        h.click("Banka"); h.settle();
        assertTrue(h.screen() instanceof BankScreen);
        int max = Math.max(Bank.MIN_LOAN, (Bank.available(s) / Bank.STEP) * Bank.STEP);
        assertNotNull(seek(h, "Tutar"));
        assertNotNull(seek(h, "Vade"));
        // tutar: yazarak (100'lük)
        h.findLabel("Tutar: ").performClick(); h.idle(100);
        type("2700"); h.idle(100);
        assertNotNull(h.findText("Tutar: 2700 lira"));
        assertNotNull("kredi al düğmesi yeni tutarı taşır", h.findLabel("Kredi al: 2700 lira, 10 gün"));
        assertNotNull(h.findText("Teklif: 2700 lira, 10 gün"));
        // aralık dışı: limite kırpılır
        h.findLabel("Tutar: ").performClick(); h.idle(100);
        type("99999999"); h.idle(100);
        assertNotNull("limite kırpıldı", h.findText("Tutar: " + max + " lira"));
        h.findLabel("Tutar: ").performClick(); h.idle(100);
        type("100"); h.idle(100);
        assertNotNull("en az 500", h.findText("Tutar: 500 lira"));
        // 100'lüğe yuvarlama
        h.findLabel("Tutar: ").performClick(); h.idle(100);
        type("1234"); h.idle(100);
        assertNotNull(h.findText("Tutar: 1200 lira"));
        // kaydırma çubuğu: TalkBack eylemi 500'lük basamak
        SeekBar sb = seek(h, "Tutar");
        assertTrue(sb.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertNotNull("1200 -> 1500 (500'lük basamak)", h.findText("Tutar: 1500 lira"));
        // vade: seçenekli denetim 5/10/20
        assertNotNull(h.findLabel("Vade: 10 gün"));
        h.click("Vadeyi azalt");
        assertNotNull(h.findLabel("Vade: 5 gün"));
        h.findLabel("Vade: ").performClick(); h.idle(100);
        type("7"); h.idle(100);
        assertNotNull("7 -> en yakın seçenek 5", h.findLabel("Vade: 5 gün"));
        h.findLabel("Vade: ").performClick(); h.idle(100);
        type("18"); h.idle(100);
        assertNotNull("18 -> 20", h.findLabel("Vade: 20 gün"));
        assertNotNull(h.findLabel("Kredi al: 1500 lira, 20 gün"));
        int money = s.money;
        h.click("Kredi al: 1500 lira, 20 gün"); h.idle(150);
        assertEquals(1, s.loans.size());
        assertEquals(money + 1500, s.money);
        assertTrue("banka ekranında kaydırma çubukları denetlendi", audit(h, "Banka").checkedSeekBars >= 2);
    }

    // ================================================================== 6. AdjustControl: Ayarlar
    @Test public void settingsNumericSettingsAreAdjustControlsWithKeyboardAndSeekBars() {
        talkBack(true);
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        for (String n : new String[]{"Müzik sesi", "Efekt sesi", "Ambiyans sesi", "Anlatım hızı", "Oyun temposu"})
            assertNotNull("kaydırma çubuğu yok: " + n, seek(h, n));
        assertTrue("beş ayar çubuğu denetlendi", audit(h, "Ayarlar").checkedSeekBars >= 5);
        SaveData s = h.act.save;
        h.findLabel("Müzik sesi: ").performClick(); h.idle(100);
        type("35"); h.idle(100);
        assertEquals(0.35, s.musicVolume, 1e-9);
        assertEquals(0.35, stored().musicVolume, 1e-9);
        assertNotNull(h.findLabel("Müzik sesi: yüzde 35"));
        h.findLabel("Efekt sesi: ").performClick(); h.idle(100);
        type("0"); h.idle(100);
        assertEquals(0.0, s.sfxVolume, 1e-9);
        assertNotNull("0 = kapalı", h.findLabel("Efekt sesi: kapalı"));
        h.findLabel("Ambiyans sesi: ").performClick(); h.idle(100);
        type("250"); h.idle(100);
        assertEquals("aralık dışı: 100'e kırpılır", 1.0, s.ambienceVolume, 1e-9);
        h.findLabel("Anlatım hızı: ").performClick(); h.idle(100);
        type("150"); h.idle(100);
        assertEquals(1.5, s.speechRate, 1e-9);
        assertEquals(1.5f, 1.5f * 1f, 0f);
        h.findLabel("Anlatım hızı: ").performClick(); h.idle(100);
        type("500"); h.idle(100);
        assertEquals("üst sınır 2,0", 2.0, s.speechRate, 1e-9);
        h.findLabel("Oyun temposu: ").performClick(); h.idle(100);
        type("40"); h.idle(100);
        assertEquals("alt sınır 0,6", 0.6, s.tempo, 1e-9);
        assertEquals(0.6, stored().tempo, 1e-9);
        // çubuk TalkBack eylemi: 10'luk basamak
        SeekBar sb = seek(h, "Müzik sesi");
        assertTrue(sb.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertEquals("35 -> 40", 0.40, s.musicVolume, 1e-9);
        assertEquals(0.40, stored().musicVolume, 1e-9);
        // ayarlar yeni oyunda korunur
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        assertEquals(0.6, h.act.save.tempo, 1e-9);
        assertEquals(0.40, h.act.save.musicVolume, 1e-9);
    }

    @Test public void settingsButtonsStillStepAndAnnounceOnceWithoutTalkBack() {
        talkBack(false);
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        double m0 = h.act.save.musicVolume;
        h.click("Müzik sesi azalt");
        assertEquals(m0 - 0.1, h.act.save.musicVolume, 1e-6);
        assertEquals(m0 - 0.1, stored().musicVolume, 1e-6);
        for (int i = 0; i < 12; i++) h.click("Müzik sesi azalt");
        assertEquals(0.0, h.act.save.musicVolume, 1e-9);
        // sesli anlatım kapalı + TalkBack kapalı: denetim susar
        h.act.save.speechOn = false;
        h.act.applySettings();
        h.click("Müzik sesi artır"); h.idle(1000);
        assertFalse("anlatım kapalı: konuşmaz", h.act.speaker.isBusy());
    }
}
