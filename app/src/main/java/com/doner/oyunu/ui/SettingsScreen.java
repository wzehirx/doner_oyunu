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
import com.doner.oyunu.engine.NarrationLevel;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SaveData;

/** Ayarlar: v2.7 AdjustControl (−/+ düğmeleri, kaydırma çubuğu, sayısal klavye), seçim düğmeleri ve anahtarlarla. */
public final class SettingsScreen extends Screen {
    public SettingsScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Ayarlar"; }

    @Override public View create() {
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Ayarlar"));
        LinearLayout body = scrollBody(root);
        final SaveData s = a.save;

        addFlowChooser(body, s);
        addTimePressure(body, s);

        addVolume(body, "Müzik sesi", 10, new IntSetting() {
            @Override public int get() { return pct(s.musicVolume); }
            @Override public void set(int v) { s.musicVolume = v / 100.0; }
        });
        addVolume(body, "Efekt sesi", 10, new IntSetting() {
            @Override public int get() { return pct(s.sfxVolume); }
            @Override public void set(int v) { s.sfxVolume = v / 100.0; }
        });
        // v0.9: dükkân ambiyansı (müzikten bağımsız; 0 = kapalı)
        addVolume(body, "Ambiyans sesi", 5, new IntSetting() {
            @Override public int get() { return pct(s.ambienceVolume); }
            @Override public void set(int v) { s.ambienceVolume = v / 100.0; }
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

        // v2.7: anlatım hızı yüzde olarak (50–200; eski "1,5 kat" = yüzde 150): düğme, çubuk ve klavye aynı sayıyı kullanır
        addAdjust(body, "Anlatım hızı", 50, 200, 1, 10, new IntSetting() {
            @Override public int get() { return pct(s.speechRate); }
            @Override public void set(int v) { s.speechRate = v / 100.0; }
        }, new AdjustControl.Format() {
            @Override public String shown(int v) { return "yüzde " + v; }
            @Override public String spoken(int v) { return "Anlatım hızı yüzde " + v; }
            @Override public String unit() { return "yüzde"; }
        });

        addNarrationLevel(body, s);

        body.addView(button("Sesli anlatımı dene", Ui.SECONDARY, new Runnable() {
            @Override public void run() {
                a.speaker.force("Merhaba! Ben Döner Dükkânı anlatıcısıyım. Hızı ve sesi buradan ayarlayabilirsin.");
            }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 6));

        addAdjust(body, "Oyun temposu", 60, 160, 1, 10, new IntSetting() {
            @Override public int get() { return pct(s.tempo); }
            @Override public void set(int v) { s.setTempo(v / 100.0); }
        }, new AdjustControl.Format() {
            @Override public String shown(int v) { return "yüzde " + v; }
            @Override public String spoken(int v) {
                return "Oyun temposu yüzde " + v + (v > 105 ? ", daha rahat" : (v < 95 ? ", daha hızlı" : ", normal"));
            }
            @Override public String unit() { return "yüzde"; }
        });
        TextView note = Ui.label(ctx, "Tempo yükselince işlemler yavaşlar, müşteriler sabırlı olur.", 15, Theme.MUTED, false);
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
                a.speaker.announce(on ? "Çırak duyuruları açık" : "Çırak duyuruları kapalı", true, com.doner.oyunu.engine.Importance.VERBOSE);
            }
        });
        TextView apprNote = Ui.label(ctx, "Açıkken çırağın işleri sesli duyurulur.", 15, Theme.MUTED, false);
        apprNote.setFocusable(true);
        body.addView(apprNote, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 8));

        // v2.5: çırak teslimi (seviye 1+; kapalıyken teslimi her zaman sen yaparsın)
        addSwitch(body, "Çırak teslimi", s.apprenticeDelivery, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                s.apprenticeDelivery = on;
                a.saveNow();
                a.sound.play("sfx_tap");
                a.speaker.announce(on ? "Çırak teslimi açık: çırak teslime hazır siparişi teslim eder." : "Çırak teslimi kapalı: teslimi sen yaparsın.", true, com.doner.oyunu.engine.Importance.VERBOSE);
            }
        });
        TextView delivNote = Ui.label(ctx, "Açıkken çırak teslime hazır siparişi müşteriye teslim eder.", 15, Theme.MUTED, false);
        delivNote.setFocusable(true);
        body.addView(delivNote, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 8));

        // v2.6: faturaları otomatik öde (varsayılan açık; motor: GameEngine.isAutoPayBills / SaveData.autoPayBills)
        addSwitch(body, "Faturaları otomatik öde", s.autoPayBills, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                s.autoPayBills = on;
                a.saveNow();
                a.sound.play("sfx_tap");
                a.speaker.announce(on ? "Otomatik ödeme açık: faturalar son ödeme gününde kasadan ödenir." : "Otomatik ödeme kapalı: faturaları sen ödersin.", true, com.doner.oyunu.engine.Importance.VERBOSE);
            }
        });
        TextView payNote = Ui.label(ctx, Texts.AUTO_PAY_NOTE, 15, Theme.MUTED, false);
        payNote.setFocusable(true);
        body.addView(payNote, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 8));

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
        TextView note = Ui.label(ctx, Texts.FLOW_NOTE, 15, Theme.MUTED, false);
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
        a.speaker.announce(s.timePressure ? "Süre baskısı açık." : "Süre baskısı kapalı, kimse gitmez.", true, com.doner.oyunu.engine.Importance.VERBOSE);
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
        a.speaker.announce(m.spoken(), true, com.doner.oyunu.engine.Importance.VERBOSE);
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

    /** Tam sayı ayarı (yüzde): okuma ve yazma. */
    private interface IntSetting {
        int get();
        void set(int v);
    }

    private static int pct(double v) { return (int) Math.round(v * 100); }

    private void addVolume(LinearLayout body, final String name, int buttonStep, IntSetting st) {
        addAdjust(body, name, 0, 100, 1, buttonStep, st, new AdjustControl.Format() {
            @Override public String shown(int v) { return v <= 0 ? "kapalı" : "yüzde " + v; }
            @Override public String spoken(int v) { return name + (v <= 0 ? " kapalı" : " yüzde " + v); }
            @Override public String unit() { return "yüzde"; }
        });
    }

    /**
     * v2.7: her sayısal ayar AdjustControl ({@code −  [kaydırma çubuğu]  +}, değere dokununca sayısal klavye). Değer
     * sürüklerken canlı uygulanır (ses düzeyi hemen duyulur), bırakınca/düğmeyle kesinleşince kaydedilir.
     */
    private AdjustControl addAdjust(LinearLayout body, String name, int min, int max, int step, int buttonStep,
                                    final IntSetting st, AdjustControl.Format fmt) {
        LinearLayout card = card();
        final AdjustControl ctl = new AdjustControl(ctx, adjustHost(), name, min, max, step, buttonStep, fmt);
        ctl.setShowName(true);
        ctl.setValue(st.get());
        ctl.setListener(new AdjustControl.Listener() {
            @Override public void onChanged(int value, boolean settled) {
                st.set(value);
                a.applySettings();
                if (settled) a.saveNow();
            }
        });
        card.addView(ctl);
        body.addView(card, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 6));
        return ctl;
    }

    /** v2.7 Anlatım düzeyi: Az / Orta / Çok (yalnızca TalkBack açıkken geçerli). Oyun akışı gibi üç seçimli düğme. */
    private final java.util.Map<NarrationLevel, Button> levelButtons = new java.util.EnumMap<>(NarrationLevel.class);

    private void addNarrationLevel(LinearLayout body, final SaveData s) {
        TextView h = Ui.label(ctx, "Anlatım düzeyi", 21, Theme.GOLD, true);
        Ui.heading(h);
        h.setPadding(dp(4), dp(8), dp(4), dp(2));
        body.addView(h);
        TextView note = Ui.label(ctx, Texts.NARRATION_NOTE, 15, Theme.MUTED, false);
        note.setFocusable(true);
        body.addView(note, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 0, 4, 4));
        for (final NarrationLevel l : NarrationLevel.values()) {
            Button b = button(l.displayName, Ui.SECONDARY, new Runnable() {
                @Override public void run() { chooseLevel(s, l); }
            });
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            levelButtons.put(l, b);
            body.addView(b, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
        refreshLevels(s);
    }

    private void chooseLevel(SaveData s, NarrationLevel l) {
        s.narrationLevel = l;
        a.applySettings();
        a.saveNow();
        refreshLevels(s);
        a.sound.play("sfx_tap");
        if (a.speaker.isTalkBackOn() || s.speechOn) a.speaker.force(Texts.narrationChosen(l));
    }

    private void refreshLevels(SaveData s) {
        for (java.util.Map.Entry<NarrationLevel, Button> e : levelButtons.entrySet()) {
            boolean sel = e.getKey() == s.narrationLevel;
            Button b = e.getValue();
            b.setText(e.getKey().displayName + (sel ? " (seçili)" : ""));
            b.setContentDescription(Texts.narrationOptionDescription(e.getKey(), sel));
            b.setBackground(Ui.buttonBg(ctx, sel ? Ui.GOLD : Ui.SECONDARY));
            b.setTextColor(sel ? Theme.DARK : Theme.CREAM);
            b.setSelected(sel);
        }
    }

    @Override public boolean onBack() { a.saveNow(); a.show(new MenuScreen(a)); return true; }
}
