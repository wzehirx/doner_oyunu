package com.doner.oyunu.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Bir ekranın View ağacını erişilebilirlik kurallarına göre denetler. */
final class A11yAudit {
    static final float MIN_DP = 48f;
    static final float PREF_DP = 56f;
    /** Yaygın İngilizce yer tutucu/varsayılan etiketler (Türkçe oyunda bulunmamalı). */
    private static final Pattern ENGLISH = Pattern.compile("(?i)\\b(button|back|start|settings|cancel|close|play|pause|resume|image|icon|unlabeled|switch)\\b");

    final List<String> errors = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    int checkedInteractive;
    /** Denetlenen TalkBack özel eylemi sayısı (istasyon / hazırlık düğmeleri). */
    int checkedActions;

    static A11yAudit run(UiHarness h, String where) {
        A11yAudit a = new A11yAudit();
        float density = h.act.getResources().getDisplayMetrics().density;
        View root = h.root();
        // ekran kökü: container'ın son çocuğu
        ViewGroup content = (ViewGroup) root;
        View screenRoot = null;
        if (content.getChildCount() > 0) {
            ViewGroup container = (ViewGroup) content.getChildAt(0);
            for (int i = container.getChildCount() - 1; i >= 0; i--) {
                View c = container.getChildAt(i);
                if (c.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) { screenRoot = c; break; }
            }
        }
        if (screenRoot == null) { a.errors.add(where + ": ekran kökü yok"); return a; }
        CharSequence pane = screenRoot.getAccessibilityPaneTitle();
        if (pane == null || pane.length() == 0) a.errors.add(where + ": paneTitle yok");
        else if (!pane.toString().equals(h.screen().title())) a.errors.add(where + ": paneTitle != title(): " + pane);
        if (h.screen().focusTarget() == null) a.errors.add(where + ": focusTarget yok");

        List<View> shown = UiHarness.visibleViews(screenRoot);
        boolean anyHeading = false;
        List<String> labels = new ArrayList<>();
        for (View v : shown) {
            boolean interactive = (v.isClickable() || v.isFocusable() || v instanceof CompoundButton) && !(v instanceof android.widget.ScrollView);
            boolean important = v.isImportantForAccessibility();
            String lab = UiHarness.label(v).trim();
            String id = where + ": " + v.getClass().getSimpleName() + " '" + shorten(lab) + "'";
            if (v.isAccessibilityHeading()) anyHeading = true;
            if (important && v.isClickable()) checkActions(v, id, a);

            // dekoratif View'lar
            if (isDecorative(v)) {
                if (important) a.errors.add(id + " dekoratif ama erişilebilirlik ağacında");
                continue;
            }
            if (v.isLongClickable() || v.hasOnLongClickListeners()) a.errors.add(id + " uzun basma gerektiriyor");

            if (interactive && important) {
                a.checkedInteractive++;
                if (lab.isEmpty()) a.errors.add(id + " etiketsiz");
                if (ENGLISH.matcher(lab).find()) a.errors.add(id + " İngilizce etiket olabilir");
                if (v.isClickable()) {
                    float w = v.getWidth() / density, hh = v.getHeight() / density;
                    if (w <= 0 || hh <= 0) a.errors.add(id + " boyutsuz (yerleşmemiş)");
                    else if (w < MIN_DP || hh < MIN_DP) a.errors.add(id + " dokunma hedefi küçük: " + Math.round(w) + "x" + Math.round(hh) + "dp");
                    else if (w < PREF_DP || hh < PREF_DP) a.warnings.add(id + " dokunma hedefi <56dp: " + Math.round(w) + "x" + Math.round(hh) + "dp");
                    labels.add(lab);
                }
            }
            boolean scrim = v.getClass() == android.widget.FrameLayout.class && lab.isEmpty() && v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0;
            if (v.isClickable() && !important && !scrim) a.errors.add(id + " tıklanabilir ama importantForAccessibility=no");
            if (v instanceof TextView && !(v instanceof android.widget.Button) && ((TextView) v).getText().length() > 0
                    && !v.isFocusable() && !v.isClickable() && important && v.getContentDescription() == null) {
                // odaklanamayan düz metin: TalkBack yine de okur (grup içi değilse). Bilgi amaçlı.
            }
            // grup çocukları
            if (v instanceof ViewGroup && (v.isFocusable() || v.isClickable()) && !(v instanceof android.widget.ScrollView) && v.getClass() != android.widget.Switch.class) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View ch = g.getChildAt(i);
                    if (ch.getVisibility() == View.VISIBLE && ch.isImportantForAccessibility() && !(ch instanceof ViewGroup))
                        a.errors.add(id + " grup çocuğu gizlenmemiş: " + ch.getClass().getSimpleName() + " '" + shorten(UiHarness.label(ch)) + "'");
                }
            }
            // kayan değer / sürükleme denetimleri
            String cn = v.getClass().getName();
            if (cn.contains("SeekBar") || cn.contains("RatingBar") || cn.contains("NumberPicker") || cn.contains("ViewPager") || cn.contains("Spinner"))
                a.errors.add(id + " sürükleme/karmaşık denetim: " + cn);
        }
        if (!anyHeading) a.errors.add(where + ": başlık (heading) yok");
        // aynı etikete sahip tıklanabilirler
        for (int i = 0; i < labels.size(); i++)
            for (int j = i + 1; j < labels.size(); j++)
                if (labels.get(i).equals(labels.get(j))) a.warnings.add(where + ": aynı etiket iki kez: '" + shorten(labels.get(i)) + "'");
        return a;
    }

    /** Düğmenin TalkBack özel eylemleri (ActionMenu kimlik aralığı): etiketler boş değil, Türkçe, tekrarsız. */
    static List<String> customActionLabels(View v) {
        List<String> out = new ArrayList<>();
        for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction act : v.createAccessibilityNodeInfo().getActionList()) {
            int id = act.getId();
            if (id >= ActionMenu.ACTION_BASE && id < ActionMenu.ACTION_BASE + 4096) {
                CharSequence l = act.getLabel();
                out.add(l == null ? "" : l.toString());
            }
        }
        return out;
    }

    private static void checkActions(View v, String id, A11yAudit a) {
        List<String> labels = customActionLabels(v);
        for (int i = 0; i < labels.size(); i++) {
            String l = labels.get(i).trim();
            a.checkedActions++;
            if (l.isEmpty()) a.errors.add(id + " özel eylem " + i + " etiketsiz");
            else if (ENGLISH.matcher(l).find()) a.errors.add(id + " özel eylem İngilizce olabilir: '" + shorten(l) + "'");
            for (int j = i + 1; j < labels.size(); j++) {
                if (labels.get(j).trim().equals(l)) a.errors.add(id + " aynı etiketli iki özel eylem: '" + shorten(l) + "'");
            }
        }
    }

    private static boolean isDecorative(View v) {
        String n = v.getClass().getSimpleName();
        return n.equals("IconView") || n.equals("BarView") || n.equals("StarsView");
    }

    static String shorten(String s) { return s.length() > 50 ? s.substring(0, 50) + "…" : s; }

    String report() {
        StringBuilder sb = new StringBuilder();
        for (String e : errors) sb.append("HATA  ").append(e).append('\n');
        for (String e : warnings) sb.append("UYARI ").append(e).append('\n');
        return sb.toString();
    }
}
