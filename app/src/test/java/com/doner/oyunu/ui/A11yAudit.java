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
    /**
     * v0.9 kısa etiket kuralı: düğme etiketi yalnızca ad + gerekli durumdur ("Domates ekle", "Döner makinesi, boş",
     * "Sipariş 2, Ali, ayran ve döner"). Uzun açıklama cümlesi ve "…için dokun" ipuçları TalkBack'i yavaşlatır.
     */
    static final int MAX_LABEL = 60;
    /** Sipariş kartı/kalemi etiketleri müşteri içeriğini (ad, ürünler, istekler) taşır; biraz daha uzun olabilir. */
    static final int MAX_ORDER_LABEL = 110;
    static final float MIN_DP = 48f;
    static final float PREF_DP = 56f;
    /** Yaygın İngilizce yer tutucu/varsayılan etiketler (Türkçe oyunda bulunmamalı). */
    private static final Pattern ENGLISH = Pattern.compile("(?i)\\b(button|back|start|settings|cancel|close|play|pause|resume|image|icon|unlabeled|switch)\\b");

    final List<String> errors = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    int checkedInteractive;
    /** Kısa etiket kuralı denetlenen düğme ve özel eylem sayısı (v0.9). */
    int checkedShortLabels;
    /** Denetlenen TalkBack özel eylemi sayısı (istasyon / hazırlık düğmeleri). */
    int checkedActions;
    /** Denetlenen seçili-durumlu görünüm sayısı (v0.7 oyun akışı seçenekleri). */
    int checkedSelected;
    /** Denetlenen aç/kapa durumlu düğme sayısı (v0.8 Süre baskısı). */
    int checkedToggles;
    /** v0.8 oyun ekranı denetimi yapıldı mı (gün içinde hazırlık bölümü yok; süresiz günde sabır/süre metni yok). */
    int checkedGameRules;
    /** v2.0 sade oyun ekranı denetimi yapıldı mı (tek sıradaki sipariş, kalem listesi yok, eller satırı, sınırlı odak). */
    int checkedV20;
    /**
     * v2.0: gün içinde (panel kapalı) ekranda en çok bu kadar tıklanabilir öğe olur, sipariş sayısından BAĞIMSIZ: sıradaki
     * sipariş başlığı 1 + eylem satırları ≤6 + sıra özeti 1 + istasyonlar ≤7 + acil al 1 + alt çubuk 4.
     */
    static final int MAX_GAME_CLICKABLES = 20;

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
        checkGameScreenRules(h, shown, where, a);
        checkV20Rules(h, shown, where, a);
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
                // v0.7: seçim durumu (ör. Ayarlar > Oyun akışı) yalnızca renkle değil, etikette de söylenmeli
                boolean parentSelected = v.getParent() instanceof View && ((View) v.getParent()).isSelected();
                if (v.isSelected() && !parentSelected && !(v instanceof CompoundButton)) {
                    a.checkedSelected++;
                    if (!lab.toLowerCase(new java.util.Locale("tr")).contains("seçili")) a.errors.add(id + " seçili ama etiketi 'seçili' demiyor (renge bağımlı durum)");
                }
                if (ENGLISH.matcher(lab).find()) a.errors.add(id + " İngilizce etiket olabilir");
                if (v.isClickable()) checkShort(lab, id, a);
                // v0.8: aç/kapa düğmesi (Süre baskısı) durumunu etikette söylemeli: "açık" ya da "kapalı"
                if (v.isClickable() && lab.startsWith("Süre baskısı")) {
                    a.checkedToggles++;
                    String low = lab.toLowerCase(new java.util.Locale("tr"));
                    if (!low.contains("açık") && !low.contains("kapalı")) a.errors.add(id + " aç/kapa düğmesi ama etiketi durumunu (açık/kapalı) söylemiyor");
                }
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

    /**
     * v0.8 oyun ekranı kuralları: (1) gün içinde (RUNNING) hazırlık bölümü (hazırlık düğmeleri, hazır stok göstergeleri,
     * stok okuma düğmesi) ekranda olmamalı; (2) süre baskısı kapalıyken sabır / kalan süre metni ve göstergesi olmamalı.
     */
    private static void checkGameScreenRules(UiHarness h, List<View> shown, String where, A11yAudit a) {
        if (!(h.screen() instanceof GameScreen)) return;
        com.doner.oyunu.engine.GameEngine e = h.engine();
        a.checkedGameRules++;
        boolean running = e.getState() == com.doner.oyunu.engine.GameEngine.State.RUNNING;
        // açık seçenek panelinde hazırlık SEÇENEKLERİ ("Salata ve sos tepsisi hazırla, hazırlık, …") olabilir: bunlar bölüm değil
        boolean panel = h.field(h.screen(), "panel") != null;
        for (View v : shown) {
            String lab = UiHarness.label(v);
            String low = lab.toLowerCase(new java.util.Locale("tr"));
            if (running && ((!panel && (lab.startsWith("Döner dilimle: ") || lab.startsWith("Ayran doldur") || lab.startsWith("Salata ve sos tepsisi hazırla")))
                    || lab.startsWith("Hazır stoğu oku") || lab.startsWith("Hazır döner:") || lab.startsWith("Hazır ayran:") || lab.startsWith("Hazır salata ve sos tepsisi")))
                a.errors.add(where + ": gün içinde hazırlık bölümü görünmemeli: '" + shorten(lab) + "'");
            if (running && !e.isTimed() && (low.contains("sabır") || low.contains("saniye kaldı") || low.contains("kalan süre") || low.contains("mesai")))
                a.errors.add(where + ": süre baskısı kapalıyken sabır/süre metni olmamalı: '" + shorten(lab) + "'");
        }
    }

    /**
     * v2.0 kuralları: (1) hiçbir etikette eski "yuva" kelimesi yok; (2) gün içinde (panel kapalıyken) TEK "Sıradaki sipariş"
     * başlığı, "Sipariş …" etiketli en çok BİR tıklanabilir (sıradaki sipariş), kalem satırı ("Sipariş N, kalem") yok;
     * (3) eller satırı ("Elin boş" / "Elin dolu…") görünür; (4) pasif istasyon düğmeleri doluluğu metinle söyler ("1/2");
     * (5) tıklanabilir öğe sayısı sipariş sayısından bağımsız, ≤ {@link #MAX_GAME_CLICKABLES}.
     */
    private static void checkV20Rules(UiHarness h, List<View> shown, String where, A11yAudit a) {
        for (View v : shown) {
            String low = UiHarness.label(v).toLowerCase(new java.util.Locale("tr"));
            if (low.contains("yuva")) a.errors.add(where + ": eski 'yuva' kelimesi: '" + shorten(UiHarness.label(v)) + "'");
        }
        if (!(h.screen() instanceof GameScreen)) return;
        com.doner.oyunu.engine.GameEngine e = h.engine();
        if (e.getState() != com.doner.oyunu.engine.GameEngine.State.RUNNING) return;
        if (h.field(h.screen(), "panel") != null || h.access().ordersPanelOpen() || h.field(h.screen(), "overlay") != null) return;
        a.checkedV20++;
        int headings = 0, orderButtons = 0, clickables = 0;
        boolean hands = false;
        for (View v : shown) {
            String lab = UiHarness.label(v);
            if (v.isAccessibilityHeading() && lab.equals("Sıradaki sipariş")) headings++;
            if (!v.isImportantForAccessibility()) continue;
            if (v.isClickable()) {
                clickables++;
                if (lab.startsWith("Sipariş ")) orderButtons++;
                if (lab.matches("^Sipariş \\d+, kalem.*")) a.errors.add(where + ": eski kalem satırı: '" + shorten(lab) + "'");
            }
            if ((v.isFocusable() || v.isClickable()) && lab.startsWith("Elin ")) hands = true;
        }
        if (headings != 1) a.errors.add(where + ": tek 'Sıradaki sipariş' başlığı olmalı, bulunan " + headings);
        if (orderButtons > 1) a.errors.add(where + ": ekranda birden çok sipariş düğmesi: " + orderButtons);
        if (!hands) a.errors.add(where + ": eller satırı ('Elin boş' / 'Elin dolu') yok");
        if (clickables > MAX_GAME_CLICKABLES) a.errors.add(where + ": çok fazla tıklanabilir öğe: " + clickables + " > " + MAX_GAME_CLICKABLES);
        for (com.doner.oyunu.engine.Station st : com.doner.oyunu.engine.Station.values()) {
            View t = h.stationTile(st);
            if (t == null || e.getStationCapacity(st) <= 0) continue;
            String lab = UiHarness.label(t);
            String want = st.displayName + " " + e.getStationUsed(st) + "/" + e.getStationCapacity(st);
            if (!lab.startsWith(want)) a.errors.add(where + ": istasyon doluluğu etikette yok: '" + lab + "' (beklenen '" + want + "')");
        }
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
            checkShort(l, id + " özel eylem", a);
            for (int j = i + 1; j < labels.size(); j++) {
                if (labels.get(j).trim().equals(l)) a.errors.add(id + " aynı etiketli iki özel eylem: '" + shorten(l) + "'");
            }
        }
    }

    /** v0.9: etiket ≤ 60 karakter (sipariş etiketleri ≤ 110) ve "dokun" ipucu yok ("Emin misin" onayı hariç). */
    static void checkShort(String lab, String id, A11yAudit a) {
        a.checkedShortLabels++;
        int max = lab.startsWith("Sipariş ") ? MAX_ORDER_LABEL : MAX_LABEL;
        if (lab.length() > max) a.errors.add(id + " etiket uzun (" + lab.length() + " > " + max + "): '" + shorten(lab) + "'");
        if (lab.toLowerCase(new java.util.Locale("tr")).contains("dokun") && !lab.startsWith("Emin misin"))
            a.errors.add(id + " etiket 'dokun' ipucu içeriyor: '" + shorten(lab) + "'");
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
