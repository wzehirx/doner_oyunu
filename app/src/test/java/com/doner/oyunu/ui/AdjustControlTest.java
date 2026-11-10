package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * v2.7 AdjustControl: −/+ düğmeleri, kaydırma çubuğu (sürüklerken sessiz, bırakınca tek duyuru), TalkBack eylemleri
 * (rangeInfo, SET_PROGRESS, yukarı/aşağı kaydırma), sayısal klavye (kırpma/uyarı/geçersiz giriş), ayrık seçenekler,
 * dokunma hedefleri ≥56 dp, odak kaybı yok (görünümler yerinde güncellenir).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class AdjustControlTest {
    private static final class FakeHost implements AdjustControl.Host {
        final List<String> spoken = new ArrayList<>();
        boolean screenReader;
        int taps, errors;
        @Override public void speak(String text) { spoken.add(text); }
        @Override public boolean screenReaderOn() { return screenReader; }
        @Override public void tapFeedback() { taps++; }
        @Override public void errorFeedback() { errors++; }
    }

    private Activity act;
    private FakeHost host;
    private FrameLayout frame;

    @Before public void setUp() {
        act = Robolectric.buildActivity(Activity.class).setup().get();
        host = new FakeHost();
        frame = new FrameLayout(act);
        act.setContentView(frame);
    }

    private static void idle(long ms) { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS); }

    private final List<int[]> changes = new ArrayList<>();

    /** Fiyat gibi: yüzde 50–150, tam yüzde, düğme +5. */
    private AdjustControl price() {
        AdjustControl c = new AdjustControl(act, host, "Ayran fiyatı", 50, 150, 1, 5, AdjustControl.Formats.PERCENT);
        c.setValue(100);
        c.setListener(new AdjustControl.Listener() {
            @Override public void onChanged(int v, boolean settled) { changes.add(new int[]{v, settled ? 1 : 0}); }
        });
        frame.addView(c, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    /** Kredi tutarı gibi: 500–3000, granül 100, düğme 500. */
    private AdjustControl amount() {
        AdjustControl c = new AdjustControl(act, host, "Tutar", 500, 3000, 100, 500, AdjustControl.Formats.LIRA);
        c.setValue(1500);
        c.setListener(new AdjustControl.Listener() {
            @Override public void onChanged(int v, boolean settled) { changes.add(new int[]{v, settled ? 1 : 0}); }
        });
        frame.addView(c, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private static SeekBar.OnSeekBarChangeListener seekListener(AdjustControl c) {
        return shadowOf(c.seekBar()).getOnSeekBarChangeListener();
    }

    private static void drag(AdjustControl c, int... progresses) {
        SeekBar.OnSeekBarChangeListener l = seekListener(c);
        l.onStartTrackingTouch(c.seekBar());
        for (int p : progresses) {
            c.seekBar().setProgress(p);
            l.onProgressChanged(c.seekBar(), p, true);
        }
    }

    private static void release(AdjustControl c) { seekListener(c).onStopTrackingTouch(c.seekBar()); }

    private Dialog dialog() { return ShadowDialog.getLatestDialog(); }

    private static <T extends View> T find(View root, Class<T> cls) {
        for (View v : UiHarness.all(root)) if (cls.isInstance(v)) return cls.cast(v);
        return null;
    }

    private static Button findButton(View root, String text) {
        for (View v : UiHarness.all(root)) if (v instanceof Button && ((Button) v).getText().toString().equals(text)) return (Button) v;
        return null;
    }

    // ------------------------------------------------------------------ yerleşim ve erişilebilirlik
    @Test public void everyPartIsAtLeast56dpAndLabelledShort() {
        AdjustControl c = price();
        float d = act.getResources().getDisplayMetrics().density;
        c.measure(View.MeasureSpec.makeMeasureSpec(Math.round(328 * d), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        c.layout(0, 0, c.getMeasuredWidth(), c.getMeasuredHeight());
        for (View v : new View[]{c.valueLabel(), c.minusButton(), c.plusButton(), c.seekBar()}) {
            assertTrue(v.getClass().getSimpleName() + " genişlik " + v.getWidth() / d, v.getWidth() / d >= 56f - 0.01f);
            assertTrue(v.getClass().getSimpleName() + " yükseklik " + v.getHeight() / d, v.getHeight() / d >= 56f - 0.01f);
        }
        c.setShowName(true);
        assertEquals("Ayran fiyatı: yüzde 100", c.valueLabel().getContentDescription().toString());
        assertEquals("Ayran fiyatı azalt", c.minusButton().getContentDescription().toString());
        assertEquals("Ayran fiyatı artır", c.plusButton().getContentDescription().toString());
        for (View v : new View[]{c.valueLabel(), c.minusButton(), c.plusButton(), c.seekBar()}) {
            String l = UiHarness.label(v);
            assertFalse("etiket boş", l.isEmpty());
            assertTrue("kısa etiket: " + l, l.length() <= 60);
            assertFalse("'dokun' ipucu yok: " + l, l.toLowerCase().contains("dokun"));
        }
    }

    @Test public void seekBarExposesRangeInfoProgressActionAndScrollActions() {
        AdjustControl c = price();
        c.setValue(112);
        AccessibilityNodeInfo info = c.seekBar().createAccessibilityNodeInfo();
        assertNotNull("rangeInfo", info.getRangeInfo());
        assertEquals(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, info.getRangeInfo().getType());
        assertEquals(0f, info.getRangeInfo().getMin(), 0f);
        assertEquals(100f, info.getRangeInfo().getMax(), 0f); // 50..150, granül 1
        assertEquals(62f, info.getRangeInfo().getCurrent(), 0f);
        boolean setProgress = false, fwd = false, back = false;
        for (AccessibilityNodeInfo.AccessibilityAction a : info.getActionList()) {
            if (a.getId() == android.R.id.accessibilityActionSetProgress) setProgress = true;
            if (a.getId() == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) fwd = true;
            if (a.getId() == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) back = true;
        }
        assertTrue("SET_PROGRESS", setProgress);
        assertTrue("yukarı kaydırma", fwd);
        assertTrue("aşağı kaydırma", back);
        assertEquals("gerçek değer durum açıklamasında", "yüzde 112", String.valueOf(info.getStateDescription()));
        assertEquals("Ayran fiyatı", c.seekBar().getContentDescription().toString());
        assertTrue(c.seekBar().isFocusable());
    }

    @Test public void talkBackScrollActionsMoveByTheButtonStepAndStopAtTheEnds() {
        AdjustControl c = price();
        host.screenReader = true;
        assertTrue(c.seekBar().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertEquals(105, c.getValue());
        assertTrue(c.seekBar().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertEquals(110, c.getValue());
        assertTrue(c.seekBar().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
        assertEquals(105, c.getValue());
        assertEquals("dinleyici kesinleşmiş değerlerle çağrıldı", 3, changes.size());
        assertTrue(changes.get(0)[1] == 1);
        c.setValue(150);
        assertFalse("uçta ilerlenmez", c.seekBar().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        AccessibilityNodeInfo info = c.seekBar().createAccessibilityNodeInfo();
        for (AccessibilityNodeInfo.AccessibilityAction a : info.getActionList())
            assertNotEquals("uçta yukarı kaydırma eylemi sunulmaz", AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, a.getId());
        idle(2000);
        assertTrue("TalkBack değeri kendisi okur: ikinci duyuru yok", host.spoken.isEmpty());
    }

    @Test public void setProgressActionSetsTheValueDirectly() {
        AdjustControl c = price();
        host.screenReader = true;
        Bundle b = new Bundle();
        b.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 62f);
        assertTrue(c.seekBar().performAccessibilityAction(android.R.id.accessibilityActionSetProgress, b));
        assertEquals("ilerleme 62 = 50 + 62", 112, c.getValue());
        assertEquals("yüzde 112", String.valueOf(c.seekBar().createAccessibilityNodeInfo().getStateDescription()));
        assertEquals(112, changes.get(changes.size() - 1)[0]);
        idle(2000);
        assertTrue("TalkBack açıkken ayrıca duyuru yok", host.spoken.isEmpty());
    }

    @Test public void withoutScreenReaderSeekActionsAreAnnouncedOnceDebounced() {
        AdjustControl c = price();
        host.screenReader = false; // ör. yön tuşu ya da anahtar erişimi
        for (int i = 0; i < 3; i++) c.seekBar().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null);
        idle(AdjustControl.DEBOUNCE_MS - 50);
        assertTrue(host.spoken.isEmpty());
        idle(200);
        assertEquals(java.util.Collections.singletonList("yüzde 115"), host.spoken);
    }

    // ------------------------------------------------------------------ düğmeler
    @Test public void buttonsStepToTheNextBoundaryAndSnapOffMultiples() {
        AdjustControl c = price();
        c.plusButton().performClick();
        assertEquals(105, c.getValue());
        c.setValue(112);
        c.plusButton().performClick();
        assertEquals("112 + düğme = 115", 115, c.getValue());
        c.setValue(112);
        c.minusButton().performClick();
        assertEquals("112 - düğme = 110", 110, c.getValue());
        c.minusButton().performClick();
        assertEquals(105, c.getValue());
        assertEquals("her tıkta dokunma geri bildirimi", 4, host.taps);
        // etiket yerinde güncellenir
        assertEquals("yüzde 105", c.valueLabel().getText().toString());
        assertEquals("Ayran fiyatı: yüzde 105", c.valueLabel().getContentDescription().toString());
    }

    @Test public void amountStepsBy500WithinRangeAndSnapsTyped100s() {
        AdjustControl c = amount();
        c.plusButton().performClick();
        assertEquals(2000, c.getValue());
        c.setValue(1700); // 100'lük granül
        c.plusButton().performClick();
        assertEquals("1700 -> bir sonraki 500'lük basamak (min 500'den)", 2000, c.getValue());
        c.setValue(1700);
        c.minusButton().performClick();
        assertEquals(1500, c.getValue());
        c.setValue(2900);
        c.plusButton().performClick();
        assertEquals("üst sınıra", 3000, c.getValue());
    }

    @Test public void buttonsAtTheLimitsGiveErrorFeedbackAndOneAnnouncement() {
        AdjustControl c = price();
        c.setValue(150);
        c.plusButton().performClick();
        c.plusButton().performClick();
        assertEquals(150, c.getValue());
        assertEquals("sınırda hata geri bildirimi", 2, host.errors);
        assertTrue("sınırda dinleyici çağrılmaz", changes.isEmpty());
        idle(1000);
        assertEquals("art arda iki basış tek duyuru", java.util.Collections.singletonList("En yüksek: yüzde 150"), host.spoken);
        host.spoken.clear();
        c.setValue(50);
        c.minusButton().performClick();
        idle(1000);
        assertEquals(java.util.Collections.singletonList("En düşük: yüzde 50"), host.spoken);
    }

    @Test public void announcementIsDebouncedToTheLastValue() {
        AdjustControl c = price();
        for (int i = 0; i < 4; i++) { c.plusButton().performClick(); idle(100); }
        assertTrue("geri sayım bitmeden söylenmez", host.spoken.isEmpty());
        idle(AdjustControl.DEBOUNCE_MS);
        assertEquals("yalnızca son değer", java.util.Collections.singletonList("yüzde 120"), host.spoken);
        idle(3000);
        assertEquals("tek duyuru", 1, host.spoken.size());
    }

    // ------------------------------------------------------------------ kaydırma çubuğu
    @Test public void dragAppliesLiveButSpeaksOnlyAfterRelease() {
        AdjustControl c = price();
        drag(c, 55, 60, 70);
        assertEquals("sürüklerken değer canlı: 50 + 70", 120, c.getValue());
        assertEquals("yüzde 120", c.valueLabel().getText().toString());
        assertEquals(3, changes.size());
        for (int[] ch : changes) assertEquals("sürükleme sürerken kesinleşmedi", 0, ch[1]);
        idle(3000);
        assertTrue("sürüklerken hiçbir adım okunmaz", host.spoken.isEmpty());
        release(c);
        assertEquals("bırakınca kesinleşir", 1, changes.get(changes.size() - 1)[1]);
        assertEquals(120, changes.get(changes.size() - 1)[0]);
        idle(AdjustControl.RELEASE_MS + 50);
        assertEquals("bırakınca tek duyuru", java.util.Collections.singletonList("yüzde 120"), host.spoken);
    }

    @Test public void seekBarMirrorsValueAndRange() {
        AdjustControl c = price();
        assertEquals(100, c.seekBar().getMax());
        assertEquals(50, c.seekBar().getProgress());
        c.plusButton().performClick();
        assertEquals("düğme çubuğu da taşır", 55, c.seekBar().getProgress());
        AdjustControl a = amount();
        assertEquals("granül 100: (3000-500)/100", 25, a.seekBar().getMax());
        assertEquals(10, a.seekBar().getProgress());
        a.setRange(500, 1700); // banka limiti düştü
        assertEquals(12, a.seekBar().getMax());
        assertEquals("değer sığdırılır", 1500, a.getValue());
        a.setRange(500, 1200);
        assertEquals(1200, a.getValue());
        a.setRange(500, 500); // limit yok
        assertEquals(500, a.getValue());
        assertFalse("tek seçenek: çubuk kapalı", a.seekBar().isEnabled());
        a.setRange(500, 2000);
        assertTrue(a.seekBar().isEnabled());
    }

    // ------------------------------------------------------------------ sayısal klavye
    @Test public void tappingTheValueOpensANumericKeyboardAndOkAppliesIt() {
        AdjustControl c = price();
        c.valueLabel().performClick();
        Dialog d = dialog();
        assertNotNull(d);
        assertTrue(d.isShowing());
        assertSame(d, c.keyboardDialog());
        EditText et = find(d.getWindow().getDecorView(), EditText.class);
        assertNotNull("EditText var", et);
        assertEquals("sayısal klavye", InputType.TYPE_CLASS_NUMBER, et.getInputType() & InputType.TYPE_MASK_CLASS);
        assertEquals("mevcut değer", "100", et.getText().toString());
        assertNotNull("Tamam", findButton(d.getWindow().getDecorView(), "Tamam"));
        assertNotNull("Vazgeç", findButton(d.getWindow().getDecorView(), "Vazgeç"));
        for (String t : new String[]{"Tamam", "Vazgeç"}) {
            Button b = findButton(d.getWindow().getDecorView(), t);
            assertTrue(t + " ≥56dp", b.getMinimumHeight() >= Math.round(56 * act.getResources().getDisplayMetrics().density));
        }
        et.setText("112");
        findButton(d.getWindow().getDecorView(), "Tamam").performClick();
        assertEquals(112, c.getValue());
        assertFalse("pencere kapandı", d.isShowing());
        assertNull(c.keyboardDialog());
        assertEquals(112, changes.get(changes.size() - 1)[0]);
        assertEquals("kesinleşmiş", 1, changes.get(changes.size() - 1)[1]);
        assertEquals("yüzde 112", c.valueLabel().getText().toString());
        assertEquals(50 + 62, 50 + c.seekBar().getProgress());
        idle(1000);
        assertEquals(java.util.Collections.singletonList("yüzde 112"), host.spoken);
    }

    @Test public void keyboardDoneActionAlsoApplies() {
        AdjustControl c = price();
        c.valueLabel().performClick();
        EditText et = find(dialog().getWindow().getDecorView(), EditText.class);
        assertEquals(EditorInfo.IME_ACTION_DONE, et.getImeOptions() & EditorInfo.IME_MASK_ACTION);
        et.setText("77");
        et.onEditorAction(EditorInfo.IME_ACTION_DONE);
        assertEquals(77, c.getValue());
    }

    @Test public void outOfRangeIsClampedAndWarned() {
        AdjustControl c = price();
        c.valueLabel().performClick();
        EditText et = find(dialog().getWindow().getDecorView(), EditText.class);
        et.setText("999");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals("üst sınıra kırpılır", 150, c.getValue());
        assertEquals("hata geri bildirimi", 1, host.errors);
        idle(1000);
        assertEquals(java.util.Collections.singletonList("Değer 50 ile 150 arasında olmalı. yüzde 150 yapıldı."), host.spoken);
        host.spoken.clear();
        c.valueLabel().performClick();
        et = find(dialog().getWindow().getDecorView(), EditText.class);
        et.setText("3");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals("alt sınıra kırpılır", 50, c.getValue());
        idle(1000);
        assertEquals(1, host.spoken.size());
        assertTrue(host.spoken.get(0), host.spoken.get(0).startsWith("Değer 50 ile 150 arasında olmalı."));
        // çok uzun sayı da çökmez, üst sınıra kırpılır
        c.valueLabel().performClick();
        et = find(dialog().getWindow().getDecorView(), EditText.class);
        et.setText("123456789");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals(150, c.getValue());
    }

    @Test public void invalidOrEmptyInputKeepsTheDialogOpenWithAnError() {
        AdjustControl c = price();
        c.valueLabel().performClick();
        Dialog d = dialog();
        EditText et = find(d.getWindow().getDecorView(), EditText.class);
        et.setText("");
        findButton(d.getWindow().getDecorView(), "Tamam").performClick();
        assertTrue("boş giriş: pencere açık kalır", d.isShowing());
        assertEquals(100, c.getValue());
        assertEquals(1, host.errors);
        boolean shown = false;
        for (View v : UiHarness.all(d.getWindow().getDecorView()))
            if (v instanceof TextView && !(v instanceof Button) && ((TextView) v).getText().toString().equals("Bir sayı yaz.") && v.getVisibility() == View.VISIBLE) shown = true;
        assertTrue("hata metni görünür", shown);
        et.setText("abc");
        findButton(d.getWindow().getDecorView(), "Tamam").performClick();
        assertTrue(d.isShowing());
        assertEquals(100, c.getValue());
        et.setText("120");
        findButton(d.getWindow().getDecorView(), "Tamam").performClick();
        assertFalse(d.isShowing());
        assertEquals(120, c.getValue());
    }

    @Test public void cancelKeepsTheValue() {
        AdjustControl c = price();
        c.valueLabel().performClick();
        Dialog d = dialog();
        find(d.getWindow().getDecorView(), EditText.class).setText("140");
        findButton(d.getWindow().getDecorView(), "Vazgeç").performClick();
        assertFalse(d.isShowing());
        assertEquals(100, c.getValue());
        assertTrue(changes.isEmpty());
        idle(1000);
        assertTrue(host.spoken.isEmpty());
    }

    @Test public void typedValuesSnapToTheGranule() {
        AdjustControl c = amount();
        c.valueLabel().performClick();
        find(dialog().getWindow().getDecorView(), EditText.class).setText("1234");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals("100'lüğe yuvarlanır", 1200, c.getValue());
        assertEquals(0, host.errors);
        idle(1000);
        assertEquals("aralık içinde: uyarı yok, değer söylenir", java.util.Collections.singletonList("1200 lira"), host.spoken);
        c.valueLabel().performClick();
        find(dialog().getWindow().getDecorView(), EditText.class).setText("100");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals("en az 500", 500, c.getValue());
    }

    @Test public void focusAndViewsSurviveChangesAndKeyboard() {
        AdjustControl c = price();
        int children = c.getChildCount();
        Button label = c.valueLabel();
        SeekBar sb = c.seekBar();
        c.plusButton().performClick();
        c.valueLabel().performClick();
        find(dialog().getWindow().getDecorView(), EditText.class).setText("120");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        drag(c, 10, 20);
        release(c);
        c.setRange(50, 150);
        assertEquals("görünüm yeniden kurulmaz (odak kaybı yok)", children, c.getChildCount());
        assertSame(label, c.valueLabel());
        assertSame(sb, c.seekBar());
        assertSame(label, c.getChildAt(0));
    }

    @Test public void valueLabelOffersATypeAction() {
        AdjustControl c = price();
        boolean found = false;
        for (AccessibilityNodeInfo.AccessibilityAction a : c.valueLabel().createAccessibilityNodeInfo().getActionList())
            if (a.getId() == AccessibilityNodeInfo.ACTION_CLICK && "Değeri yazarak değiştir".contentEquals(a.getLabel())) found = true;
        assertTrue("tıklama eylemi 'Değeri yazarak değiştir' diye etiketli", found);
    }

    // ------------------------------------------------------------------ ayrık seçenekler (vade)
    @Test public void choiceModeWalksTheOptionsAndRoundsTypedValues() {
        AdjustControl c = AdjustControl.choices(act, host, "Vade", new int[]{20, 5, 10}, AdjustControl.Formats.DAYS);
        c.setListener(new AdjustControl.Listener() {
            @Override public void onChanged(int v, boolean settled) { changes.add(new int[]{v, settled ? 1 : 0}); }
        });
        frame.addView(c);
        c.setValue(10);
        assertEquals("seçenek sayısı - 1", 2, c.seekBar().getMax());
        assertEquals(1, c.seekBar().getProgress());
        c.plusButton().performClick();
        assertEquals(20, c.getValue());
        c.plusButton().performClick();
        assertEquals("uçta kalır", 20, c.getValue());
        assertEquals(1, host.errors);
        c.minusButton().performClick(); c.minusButton().performClick();
        assertEquals(5, c.getValue());
        // klavye: 12 -> en yakın seçenek (10), uyarı
        host.spoken.clear();
        c.valueLabel().performClick();
        find(dialog().getWindow().getDecorView(), EditText.class).setText("12");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals(10, c.getValue());
        idle(1000);
        assertEquals(java.util.Collections.singletonList("Değer 5, 10 ya da 20 olabilir. 10 gün yapıldı."), host.spoken);
        // doğru seçenek yazılırsa uyarı yok
        host.spoken.clear();
        c.valueLabel().performClick();
        find(dialog().getWindow().getDecorView(), EditText.class).setText("20");
        findButton(dialog().getWindow().getDecorView(), "Tamam").performClick();
        assertEquals(20, c.getValue());
        idle(1000);
        assertEquals(java.util.Collections.singletonList("20 gün"), host.spoken);
        // çubuk seçenek dizini gezer
        drag(c, 0);
        release(c);
        assertEquals(5, c.getValue());
        // kaydırma eylemi bir seçenek ilerler
        assertTrue(c.seekBar().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        assertEquals(10, c.getValue());
    }

    @Test public void silentSetValueDoesNotNotifyOrSpeak() {
        AdjustControl c = price();
        c.setValue(130);
        c.setValue(130);
        idle(2000);
        assertTrue(changes.isEmpty());
        assertTrue(host.spoken.isEmpty());
        assertEquals("yüzde 130", c.valueLabel().getText().toString());
        c.setValue(5); // aralığa sığdırılır
        assertEquals(50, c.getValue());
        c.setValue(500);
        assertEquals(150, c.getValue());
    }

    @Test public void showNameAddsTheNameToTheVisibleLabel() {
        AdjustControl c = amount();
        assertEquals("1500 lira", c.valueLabel().getText().toString());
        c.setShowName(true);
        assertEquals("Tutar: 1500 lira", c.valueLabel().getText().toString());
        assertEquals("Tutar: 1500 lira", c.valueLabel().getContentDescription().toString());
        c.setButtonDescriptions("Tutarı azalt", "Tutarı artır");
        assertEquals("Tutarı azalt", c.minusButton().getContentDescription().toString());
    }

    @Test public void detachCancelsPendingAnnouncementAndClosesTheDialog() {
        AdjustControl c = price();
        c.plusButton().performClick();
        c.valueLabel().performClick();
        Dialog d = dialog();
        frame.removeView(c);
        idle(2000);
        assertTrue("ayrılınca bekleyen duyuru iptal", host.spoken.isEmpty());
        assertFalse("ayrılınca klavye penceresi kapanır", d.isShowing());
    }
}
