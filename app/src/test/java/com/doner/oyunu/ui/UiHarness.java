package com.doner.oyunu.ui;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.Station;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Robolectric testleri için ortak yardımcılar: etkinlik başlatma, zaman ilerletme, görünüm arama. */
final class UiHarness {
    final ActivityController<MainActivity> controller;
    final MainActivity act;

    // Robolectric PAUSED modunda Choreographer her idle() çağrısında tam BİR kare çalıştırır (kare zamanı = sanal uptime).
    // Duraksız modda ise 1 ms aralıklı kareler sonsuz döngüye girer; bu yüzden her zaman paused kullanılır.

    UiHarness() { this(false); }

    /** Belirli oyun akışıyla (v0.7): ayar yeni oyunda korunur; v0.6 akış testleri GERCEKCI kullanır. */
    UiHarness(boolean animations, com.doner.oyunu.engine.FlowMode flow) {
        this(animations);
        act.save.flowMode = flow;
        act.save.flowChosen = true;
    }

    /** animations=false: geçiş animasyonları kapalı (animasyonlu mod: kare başına bir ilerleme). */
    UiHarness(boolean animations) {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        if (!animations) {
            android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        }
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        act = controller.get();
        idle(600);
    }

    void idle(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }

    /** Gerçek zamana yakın akış: 16 ms karelerle ilerletir (Choreographer her karede çalışsın). */
    void run(long ms) {
        long left = ms;
        while (left > 0) {
            long d = Math.min(16, left);
            ShadowLooper.idleMainLooper(d, TimeUnit.MILLISECONDS);
            left -= d;
        }
    }

    View root() { return act.findViewById(android.R.id.content); }

    Screen screen() { return act.currentScreen(); }

    static List<View> all(View v) {
        List<View> out = new ArrayList<>();
        collect(v, out);
        return out;
    }

    private static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    /** Ekrandaki (gizli olmayan) görünümler: GONE ve NO_HIDE_DESCENDANTS altındakiler hariç. */
    static List<View> visibleViews(View v) {
        List<View> out = new ArrayList<>();
        collectVisible(v, out, false);
        return out;
    }

    private static void collectVisible(View v, List<View> out, boolean hidden) {
        if (v.getVisibility() != View.VISIBLE) return;
        boolean h = hidden || v.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
        if (!h) out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectVisible(g.getChildAt(i), out, h);
        }
    }

    static String label(View v) {
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) return d.toString();
        if (v instanceof TextView && ((TextView) v).getText() != null) return ((TextView) v).getText().toString();
        return "";
    }

    View findLabel(String contains) {
        for (View v : visibleViews(root())) {
            if (v.isClickable() && label(v).contains(contains)) return v;
        }
        return null;
    }

    View findText(String contains) {
        for (View v : visibleViews(root())) {
            if (label(v).contains(contains)) return v;
        }
        return null;
    }

    void click(String contains) {
        View v = findLabel(contains);
        if (v == null) throw new AssertionError("Tıklanabilir görünüm yok: '" + contains + "' ekran=" + screen().title() + " mevcut=" + clickables());
        if (!v.performClick()) throw new AssertionError("performClick false: " + contains);
        idle(50);
    }

    List<String> clickables() {
        List<String> l = new ArrayList<>();
        for (View v : visibleViews(root())) if (v.isClickable()) l.add(label(v));
        return l;
    }

    /** Brifing/Market'ten "Güne başla" der, hazırlık aşamasını atlar ("Hazırlık yapmadan aç"): gün çalışmaya başlar. */
    void startDay() {
        click("Güne başla");
        settle();
        skipPrep();
    }

    /** Hazırlık aşamasındaki ekranda "Hazırlık yapmadan aç" düğmesine dokunur. */
    void skipPrep() {
        click("Hazırlık yapmadan aç");
        idle(100);
    }

    /** Sahne geçişini bitir (animasyon + onEnter gecikmesi). */
    void settle() { idle(800); }

    // ---------------------------------------------------------------- GameScreen içi (yansıma)
    GameEngine engine() {
        Object g = screen();
        try {
            Field f = g.getClass().getDeclaredField("engine");
            f.setAccessible(true);
            return (GameEngine) f.get(g);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    /** İstasyon düğmesini bulur (contentDescription "Ad, durum"). */
    View stationTile(Station st) {
        for (View v : visibleViews(root())) {
            if (v.isClickable() && label(v).startsWith(st.displayName)) return v;
        }
        return null;
    }

    View orderCard(Order o) {
        for (View v : visibleViews(root())) {
            if (v.isClickable() && label(v).startsWith("Sipariş " + o.getPosition() + ", " + o.customer.name)) return v;
        }
        return null;
    }

    Object field(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    Activity activity() { return act; }

    static Looper main() { return Looper.getMainLooper(); }
}
