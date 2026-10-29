package com.doner.oyunu.ui;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.content.res.ColorStateList;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SaveData;

/** Ayarlar: eksi/artı düğmeleriyle (sürükleme gerektirmez) ve anahtarlarla. */
public final class SettingsScreen extends Screen {
    private abstract class Value {
        final String name; final double min, max, step;
        Value(String name, double min, double max, double step) { this.name = name; this.min = min; this.max = max; this.step = step; }
        abstract double get();
        abstract void set(double v);
        abstract String shown(double v);
        abstract String spoken(double v);
    }

    public SettingsScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Ayarlar"; }

    @Override public View create() {
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Ayarlar"));
        LinearLayout body = scrollBody(root);
        final SaveData s = a.save;

        addFlowChooser(body, s);
        addTimePressure(body, s);

        addStepper(body, new Value("Müzik sesi", 0, 1, 0.1) {
            double get() { return s.musicVolume; }
            void set(double v) { s.musicVolume = v; }
            String shown(double v) { return Texts.volumeText(v); }
            String spoken(double v) { return Texts.volumeSpoken(v); }
        });
        addStepper(body, new Value("Efekt sesi", 0, 1, 0.1) {
            double get() { return s.sfxVolume; }
            void set(double v) { s.sfxVolume = v; }
            String shown(double v) { return Texts.volumeText(v); }
            String spoken(double v) { return Texts.volumeSpoken(v); }
        });

        addSwitch(body, "Sesli anlatım", s.speechOn, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                boolean tb = a.speaker.isTalkBackOn();
                s.speechOn = on;
                a.applySettings();
                a.saveNow();
                a.sound.play("sfx_tap");
                if (!tb) {
                    // kapanırken de son bir onay söyle
                    a.speaker.setEnabled(true);
                    a.speaker.force(on ? "Sesli anlatım açık" : "Sesli anlatım kapalı");
                    a.speaker.setEnabled(on);
                }
            }
        });

        addStepper(body, new Value("Anlatım hızı", 0.5, 2.0, 0.1) {
            double get() { return s.speechRate; }
            void set(double v) { s.speechRate = v; }
            String shown(double v) { return Texts.times(v); }
            String spoken(double v) { return Texts.times(v); }
        });

        body.addView(button("Sesli anlatımı dene", Ui.SECONDARY, new Runnable() {
            @Override public void run() {
                a.speaker.force("Merhaba! Ben Döner Dükkanı anlatıcısıyım. Hızı ve sesi buradan ayarlayabilirsin.");
            }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 6));

        addStepper(body, new Value("Oyun temposu", 0.6, 1.6, 0.1) {
            double get() { return s.tempo; }
            void set(double v) { s.setTempo(v); }
            String shown(double v) { return Texts.times(v); }
            String spoken(double v) { return Texts.times(v) + (v > 1.05 ? ", daha rahat" : (v < 0.95 ? ", daha hızlı" : ", normal")); }
        });
        TextView note = Ui.label(ctx, "Tempo yükseldikçe işlemler yavaşlar ve müşteriler daha sabırlı olur. Düşük tempo daha hızlı ve zor bir oyun demektir.", 15, Theme.MUTED, false);
        note.setFocusable(true);
        body.addView(note, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 8));

        addSwitch(body, "Titreşim", s.vibration, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                s.vibration = on;
                a.applySettings();
                a.saveNow();
                a.sound.play("sfx_tap");
                a.haptics.deliver();
            }
        });

        addSwitch(body, "Çırak duyuruları", s.apprenticeAnnounce, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                s.apprenticeAnnounce = on;
                a.saveNow();
                a.sound.play("sfx_tap");
                a.speaker.announce(on ? "Çırak duyuruları açık" : "Çırak duyuruları kapalı", true);
            }
        });
        TextView apprNote = Ui.label(ctx, "Açıkken çırağın yaptığı işler sesli duyurulur, örneğin çırak ayran dolduruyor. Çırak yine de çalışır ve ekranda görünür.", 15, Theme.MUTED, false);
        apprNote.setFocusable(true);
        body.addView(apprNote, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 8));

        root.addView(button("Geri", Ui.PRIMARY, new Runnable() {
            @Override public void run() { a.saveNow(); a.show(new MenuScreen(a)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        return root;
    }

    /** v0.7 oyun akışı seçimi: üç büyük düğme (Rahat / Dengeli / Gerçekçi), seçili olan altın ve "seçili" okunur. */
    private final java.util.Map<FlowMode, Button> flowButtons = new java.util.EnumMap<>(FlowMode.class);

    private void addFlowChooser(LinearLayout body, final SaveData s) {
        TextView h = Ui.label(ctx, "Oyun akışı", 21, Theme.GOLD, true);
        Ui.heading(h);
        h.setPadding(dp(4), dp(4), dp(4), dp(2));
        body.addView(h);
        TextView note = Ui.label(ctx, "Malzemelerin ve döner boyunun ne kadarını senin yapacağını seç. Oyun sırasında Duraklat menüsünden de değiştirebilirsin.", 15, Theme.MUTED, false);
        note.setFocusable(true);
        body.addView(note, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 4));
        for (final FlowMode m : FlowMode.values()) {
            Button b = button(m.displayName, Ui.SECONDARY, new Runnable() {
                @Override public void run() { chooseFlow(s, m); }
            });
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            flowButtons.put(m, b);
            body.addView(b, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
        refreshFlow(s);
    }

    /** v0.8 süre baskısı: tek büyük aç/kapa düğmesi; durum etikette ("Süre baskısı: Açık") ve açıklamada okunur. */
    private Button timeBtn;

    private void addTimePressure(LinearLayout body, final SaveData s) {
        TextView h = Ui.label(ctx, "Süre baskısı", 21, Theme.GOLD, true);
        Ui.heading(h);
        h.setPadding(dp(4), dp(8), dp(4), dp(2));
        body.addView(h);
        TextView note = Ui.label(ctx, Texts.TIME_PRESSURE_NOTE, 15, Theme.MUTED, false);
        note.setFocusable(true);
        body.addView(note, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 4));
        timeBtn = button("", Ui.SECONDARY, new Runnable() {
            @Override public void run() { toggleTimePressure(s); }
        });
        timeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        timeBtn.setMinHeight(dp(68));
        timeBtn.setMinimumHeight(dp(68));
        body.addView(timeBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        refreshTimePressure(s);
    }

    private void toggleTimePressure(SaveData s) {
        s.timePressure = !s.timePressure;
        a.saveNow();
        refreshTimePressure(s);
        a.sound.play("sfx_tap");
        a.speaker.announce(s.timePressure ? "Süre baskısı açık. Gün süreli, müşteriler sabırsızlanır."
                : "Süre baskısı kapalı. Süre sayacı yok, müşteri sabrı azalmaz, kimse gitmez.", true);
    }

    private void refreshTimePressure(SaveData s) {
        timeBtn.setText(Texts.timePressureSettingLabel(s.timePressure));
        timeBtn.setContentDescription(Texts.timePressureSettingDescription(s.timePressure));
        timeBtn.setBackground(Ui.buttonBg(ctx, s.timePressure ? Ui.SECONDARY : Ui.GOLD));
        timeBtn.setTextColor(s.timePressure ? Theme.CREAM : Theme.DARK);
    }

    private void chooseFlow(SaveData s, FlowMode m) {
        s.flowMode = m;
        s.flowChosen = true;
        a.saveNow();
        refreshFlow(s);
        a.sound.play("sfx_tap");
        a.speaker.announce(m.spoken(), true);
    }

    private void refreshFlow(SaveData s) {
        for (java.util.Map.Entry<FlowMode, Button> e : flowButtons.entrySet()) {
            boolean sel = e.getKey() == s.flowMode;
            Button b = e.getValue();
            b.setText(e.getKey().displayName + (sel ? " (seçili)" : ""));
            b.setContentDescription(Texts.flowOptionDescription(e.getKey(), sel));
            b.setBackground(Ui.buttonBg(ctx, sel ? Ui.GOLD : Ui.SECONDARY));
            b.setTextColor(sel ? Theme.DARK : Theme.CREAM);
            b.setSelected(sel);
        }
    }

    private void addSwitch(LinearLayout body, String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(ctx);
        sw.setText(label);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        sw.setTextColor(Theme.CREAM);
        sw.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sw.setChecked(checked);
        sw.setMinHeight(dp(64));
        sw.setPadding(dp(14), dp(10), dp(14), dp(10));
        sw.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 16));
        sw.setShowText(true);
        sw.setTextOn("Açık");
        sw.setTextOff("Kapalı");
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Theme.GOLD, Theme.MUTED}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Theme.ORANGE_DEEP, 0xFF6B4A33}));
        sw.setOnCheckedChangeListener(l);
        body.addView(sw, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 6));
    }

    private void addStepper(LinearLayout body, final Value v) {
        LinearLayout card = card();
        final TextView info = Ui.label(ctx, "", 19, Theme.CREAM, true);
        info.setFocusable(true);
        card.addView(info);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        final Button minus = Ui.button(ctx, "−  Azalt", Ui.SECONDARY);
        final Button plus = Ui.button(ctx, "+  Artır", Ui.SECONDARY);
        minus.setContentDescription(v.name + " azalt");
        plus.setContentDescription(v.name + " artır");
        row.addView(minus, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 8, 6, 0));
        row.addView(plus, Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 8, 0, 0));
        card.addView(row);
        body.addView(card, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 6));

        refresh(info, v);
        tap(minus, new Runnable() { @Override public void run() { change(info, v, -1); } });
        tap(plus, new Runnable() { @Override public void run() { change(info, v, +1); } });
    }

    private void refresh(TextView info, Value v) {
        double cur = v.get();
        info.setText(v.name + ": " + v.shown(cur));
        info.setContentDescription(v.name + ": " + v.spoken(cur));
    }

    private void change(TextView info, Value v, int dir) {
        double cur = v.get();
        double next = Math.round((cur + dir * v.step) * 100) / 100.0;
        next = Math.max(v.min, Math.min(v.max, next));
        if (Math.abs(next - cur) < 1e-9) {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.announce(dir < 0 ? "En düşük değer. " + v.spoken(cur) : "En yüksek değer. " + v.spoken(cur), true);
            return;
        }
        v.set(next);
        a.applySettings();
        a.saveNow();
        refresh(info, v);
        a.sound.play("sfx_tap");
        if (a.speaker.isTalkBackOn() || a.save.speechOn) {
            if (v.name.equals("Anlatım hızı")) a.speaker.force(v.name + " " + v.spoken(next));
            else a.speaker.announce(v.name + " " + v.spoken(next), true);
        }
    }

    @Override public boolean onBack() { a.saveNow(); a.show(new MenuScreen(a)); return true; }
}
