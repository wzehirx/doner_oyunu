package com.doner.oyunu.ui;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.PrepKind;
import com.doner.oyunu.engine.ToppingOption;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Malzeme seçimi paneli (SOS istasyonu, NEEDS_TOPPINGS) ve salata/sos tepsisi hazırlama paneli.
 * Her malzeme bir aç/kapa düğmesidir; durumu sesli söylenir ("Soğan, eklenmedi"). Tam ekran perde olarak
 * GameScreen'in üstüne konur.
 */
final class SelectionPanel {
    enum Mode { TOPPING, TRAY }

    interface Host {
        /** "Tamam" / "Hazırla": seçilen malzemelerle onay. */
        void onConfirm(SelectionPanel p, Set<String> ids);
        /** "Vazgeç". */
        void onCancel(SelectionPanel p);
    }

    /** Panel satırı: bir malzeme. */
    private final class Row extends LinearLayout {
        final Ingredient ing;
        final TextView name, note;
        boolean available;
        boolean selected;
        String noteText = "";

        Row(Ingredient ing) {
            super(a);
            this.ing = ing;
            setOrientation(VERTICAL);
            setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
            setMinimumHeight(Ui.dp(a, 64));
            setGravity(Gravity.CENTER_VERTICAL);
            name = Ui.label(a, "", 19, Theme.CREAM, true);
            note = Ui.label(a, "", 14, Theme.MUTED, false);
            addView(name);
            addView(note);
            Ui.groupAsButton(this);
            setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.sound.play("sfx_tap");
                    a.haptics.tap();
                    toggle(Row.this);
                }
            });
        }

        void update() {
            name.setText((selected ? "☑ " : "☐ ") + ing.name + (available ? "" : "  (stokta yok)"));
            note.setText(noteText);
            name.setTextColor(available ? (selected ? Theme.GOLD : Theme.CREAM) : Theme.MUTED);
            setBackground(Ui.round(a, selected ? Theme.CARD_HI : Theme.CARD,
                    selected ? Theme.GOLD : (available ? Theme.STROKE : Theme.RED), selected ? 3.5f : 2f, 16));
            String state = narrator.toppingState(ing, selected, available);
            String d = state + ". " + noteText + (noteText.isEmpty() ? "" : ". ")
                    + (available ? "Değiştirmek için çift dokun." : "Dokununca acil alma seçeneği çıkar.");
            Ui.setDesc(this, d);
        }
    }

    private final MainActivity a;
    private final GameEngine engine;
    private final Narrator narrator;
    private final Mode mode;
    private final int orderId, itemIndex;
    private final Host host;
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Set<String> wantEmergency = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();

    final FrameLayout overlay;
    final TextView titleView;
    private TextView infoTv, targetTv, summaryTv, warnTv;
    private Button emergencyBtn, okBtn;

    SelectionPanel(MainActivity a, GameEngine engine, Narrator narrator, Mode mode, int orderId, int itemIndex, Host host) {
        this.a = a;
        this.engine = engine;
        this.narrator = narrator;
        this.mode = mode;
        this.orderId = orderId;
        this.itemIndex = itemIndex;
        this.host = host;

        overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0xF01A0E08);
        overlay.setClickable(true);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(a, Theme.CARD, Theme.GOLD, 3, 20));
        panel.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16));

        titleView = Ui.label(a, title(), 24, Theme.GOLD, true);
        Ui.heading(titleView);
        panel.addView(titleView);

        infoTv = Ui.label(a, infoText(), 17, Theme.CREAM, false);
        infoTv.setFocusable(true);
        infoTv.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        panel.addView(infoTv);

        warnTv = Ui.label(a, "", 16, Theme.RED, true);
        warnTv.setFocusable(true);
        warnTv.setVisibility(View.GONE);
        warnTv.setPadding(0, 0, 0, Ui.dp(a, 6));
        panel.addView(warnTv);

        if (mode == Mode.TOPPING) {
            targetTv = Ui.label(a, "", 16, Theme.GOLD, false);
            targetTv.setFocusable(true);
            targetTv.setVisibility(View.GONE);
            targetTv.setPadding(0, 0, 0, Ui.dp(a, 6));
            panel.addView(targetTv);
            panel.addView(button("Hedefi oku", Ui.GOLD, new Runnable() {
                @Override public void run() { readTarget(); }
            }), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));
        }

        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        buildRows(list);
        panel.addView(list);

        summaryTv = Ui.label(a, "", 16, Theme.CREAM, false);
        summaryTv.setFocusable(true);
        summaryTv.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 6));
        panel.addView(summaryTv);

        emergencyBtn = Ui.button(a, "", Ui.DANGER);
        emergencyBtn.setVisibility(View.GONE);
        emergencyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.sound.play("sfx_tap"); a.haptics.tap(); buyWanted(); }
        });
        panel.addView(emergencyBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));

        if (mode == Mode.TOPPING) {
            panel.addView(button("Seçimi oku", Ui.SECONDARY, new Runnable() {
                @Override public void run() { a.speaker.force(narrator.toppingSelectionSummary(selectedIds)); }
            }), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));
        }
        okBtn = button(mode == Mode.TOPPING ? "Tamam" : "Hazırla", Ui.PRIMARY, new Runnable() {
            @Override public void run() { host.onConfirm(SelectionPanel.this, new LinkedHashSet<>(selectedIds)); }
        });
        okBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        panel.addView(okBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));
        panel.addView(button("Vazgeç", Ui.SECONDARY, new Runnable() {
            @Override public void run() { host.onCancel(SelectionPanel.this); }
        }), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 0));

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        FrameLayout wrap = new FrameLayout(a);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
        plp.setMargins(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        wrap.addView(panel, plp);
        sv.addView(wrap, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
        overlay.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        refresh();
        updateSummary();
    }

    // ------------------------------------------------------------------ içerik
    private Order order() { return engine.getOrder(orderId); }

    private String title() {
        if (mode == Mode.TRAY) return "Salata ve sos tepsisi hazırla";
        Order o = order();
        OrderLine l = o == null ? null : o.getLine(itemIndex);
        return "Malzeme seçimi" + (l == null ? "" : ": " + l.product.name);
    }

    private String infoText() {
        if (mode == Mode.TRAY) {
            return "Seçtiğin her malzemeden " + PrepKind.TRAY.batch + " kullanım hazırlanır, malzeme stoktan düşer. "
                    + "Hazır tepsi, malzeme seçimini hızlandırır. Gün sonunda artanlar israf olur.";
        }
        Order o = order();
        if (o == null) return "";
        String t = "Sipariş " + o.getPosition() + ", " + o.customer.name + ". ";
        t += o.hasRequests() ? "Müşteri isteği: " + o.requestsText() + "." : "Müşteri isteği yok, standart malzemeler.";
        t += " Panel açıkken süre durur.";
        return t;
    }

    private void buildRows(LinearLayout list) {
        rows.clear();
        List<Ingredient> ings = new ArrayList<>();
        if (mode == Mode.TOPPING) {
            for (ToppingOption op : engine.getToppingOptions(orderId, itemIndex)) {
                ings.add(op.ingredient);
            }
        } else {
            ings.addAll(engine.getPrepTrayChoices());
        }
        for (Ingredient i : ings) {
            Row r = new Row(i);
            rows.add(r);
            list.addView(r, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
        if (mode == Mode.TOPPING) {
            for (ToppingOption op : engine.getToppingOptions(orderId, itemIndex)) {
                if (op.selected && op.available) selectedIds.add(op.ingredient.id);
            }
        }
    }

    /** Stok/hazır tepsi durumunu motordan yeniden okur. */
    void refresh() {
        List<String> missing = new ArrayList<>();
        if (mode == Mode.TOPPING) {
            List<ToppingOption> ops = engine.getToppingOptions(orderId, itemIndex);
            for (Row r : rows) {
                for (ToppingOption op : ops) {
                    if (!op.ingredient.id.equals(r.ing.id)) continue;
                    r.available = op.available;
                    r.noteText = "Stok " + op.stock + (op.trayUses > 0 ? ", hazır tepside " + op.trayUses : "");
                    if (!op.available) { selectedIds.remove(r.ing.id); missing.add(r.ing.name); }
                }
            }
        } else {
            for (Row r : rows) {
                int stock = engine.getSave().inventory.get(r.ing.id);
                int tray = engine.getTrayUses(r.ing.id);
                r.available = stock >= PrepKind.TRAY.batch;
                r.noteText = "Stok " + stock + ", tepside " + tray + ", " + PrepKind.TRAY.batch + " gerekli";
                if (!r.available) { selectedIds.remove(r.ing.id); missing.add(r.ing.name); }
            }
        }
        for (Row r : rows) {
            r.selected = selectedIds.contains(r.ing.id);
            r.update();
        }
        if (missing.isEmpty()) {
            warnTv.setVisibility(View.GONE);
        } else {
            warnTv.setVisibility(View.VISIBLE);
            String w = "Stokta yok: " + Texts.join(missing) + ".";
            warnTv.setText(w);
            Ui.setDesc(warnTv, w);
        }
        updateEmergency();
        updateSummary();
    }

    private void updateSummary() {
        String t = narrator.toppingSelectionSummary(selectedIds);
        summaryTv.setText(t);
        Ui.setDesc(summaryTv, t);
    }

    private void updateEmergency() {
        // yalnızca gerçekten stokta olmayanlar
        wantEmergency.retainAll(unavailableIds());
        if (wantEmergency.isEmpty()) { emergencyBtn.setVisibility(View.GONE); return; }
        int cost = 0;
        List<String> names = new ArrayList<>();
        for (String id : wantEmergency) {
            Ingredient i = com.doner.oyunu.engine.Catalog.ingredient(id);
            if (i == null) continue;
            cost += i.emergencyPrice() * Ingredient.EMERGENCY_BATCH;
            names.add(i.name);
        }
        String n = Texts.join(names);
        emergencyBtn.setVisibility(View.VISIBLE);
        emergencyBtn.setText("Acil al: " + n + " (" + cost + " TL)");
        Ui.setDesc(emergencyBtn, "Acil al: " + n + ", toplam " + cost + " lira. Normalden yüzde elli pahalı, her malzemeden "
                + Ingredient.EMERGENCY_BATCH + " birim alınır.");
    }

    private Set<String> unavailableIds() {
        Set<String> s = new LinkedHashSet<>();
        for (Row r : rows) if (!r.available) s.add(r.ing.id);
        return s;
    }

    // ------------------------------------------------------------------ etkileşim
    private void toggle(Row r) {
        if (!r.available) {
            wantEmergency.add(r.ing.id);
            a.sound.play("sfx_error");
            a.haptics.error();
            updateEmergency();
            Ingredient ing = r.ing;
            a.speaker.announce(ing.name + " stokta yok, seçilemez. Acil al düğmesiyle "
                    + Narrator.fmtMoney(ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH) + " karşılığında alabilirsin.", true);
            return;
        }
        r.selected = !r.selected;
        if (r.selected) selectedIds.add(r.ing.id); else selectedIds.remove(r.ing.id);
        r.update();
        updateSummary();
        a.speaker.announce(narrator.toppingState(r.ing, r.selected, r.available), true);
    }

    /** Hedefi oku: müşteri istekleri + hedef malzemeler (hem sesli hem metin). */
    void readTarget() {
        if (mode != Mode.TOPPING) return;
        Order o = order();
        if (o == null) return;
        String t = narrator.toppingTarget(o, itemIndex);
        targetTv.setText(t);
        Ui.setDesc(targetTv, t);
        targetTv.setVisibility(View.VISIBLE);
        a.speaker.force(t);
    }

    private void buyWanted() {
        List<String> ids = new ArrayList<>(wantEmergency);
        boolean any = false;
        for (String id : ids) {
            if (engine.emergencyBuy(id)) { wantEmergency.remove(id); any = true; }
            else break; // para yetmedi: motor onNotEnoughMoney bildirdi
        }
        refresh();
        if (any) a.speaker.announce("Malzeme alındı. Artık seçilebilir.", false);
    }

    /** Motor reddettiyse (ör. stok) ilgili malzemeyi acil alım listesine ekler ve durumu yeniler. */
    void onRejected(Ingredient ing) {
        if (ing != null) wantEmergency.add(ing.id);
        refresh();
    }

    Set<String> selected() { return new LinkedHashSet<>(selectedIds); }

    int itemIndex() { return itemIndex; }

    int orderId() { return orderId; }

    Mode mode() { return mode; }

    private Button button(String text, int style, final Runnable action) {
        Button b = Ui.button(a, text, style);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.sound.play("sfx_tap");
                a.haptics.tap();
                action.run();
            }
        });
        return b;
    }
}
