package com.doner.oyunu.ui;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.audio.SoundManager;
import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.ApprenticeAction;
import com.doner.oyunu.engine.DayConfig;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.GameListenerAdapter;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.PortionSize;
import com.doner.oyunu.engine.StationOption;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.PrepKind;
import com.doner.oyunu.engine.PrepResult;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.StartResult;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.Step;
import com.doner.oyunu.engine.ToppingCheck;
import com.doner.oyunu.engine.ToppingSfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Oyun ekranı: önce HAZIRLIK aşaması (müşteri yok, süre akmaz), sonra gün. Canlı durum çubuğu, çalışanlar şeridi,
 * kalem bazlı sipariş kartları, istasyon ızgarası, hazırlık bölümü, istasyon eylem menüleri (TalkBack Eylemleri + seçenek paneli),
 * eylem düğmeleri, duraklatma paneli.
 */
public final class GameScreen extends Screen implements Choreographer.FrameCallback {

    /** Öğretici son aşama numarası (ilk teslimden sonra biter). */
    static final int TUTORIAL_END = 10;
    /** Ekran açıldıktan sonra hazırlık aşamasının başlama gecikmesi (ekran başlığı önce okunsun). */
    private static final long PREP_BEGIN_DELAY_MS = 600;

    // ------------------------------------------------------------------ durum
    private SaveData gameSave;
    private GameEngine engine;
    private Narrator narrator;
    private Set<Station> inUse;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean paused;
    private boolean finishing;
    private boolean exited;
    private boolean frameQueued;
    private boolean dirty = true;
    private boolean confirmQuit;
    private float uiAccum;
    private long lastNanos;
    private float noSelTimer;
    private float busyHold;
    private int lastTickSecond = -1;
    /** Eksik malzeme uyarısı verilen sipariş (acil al düğmesi onun için); yoksa -1. */
    private int missingOrderId = -1;
    /** Acil al düğmesinin en son gösterdiği eksik malzeme kümesi (odak/duyuru yalnızca değişince). */
    private String emergencyKey = "";
    private boolean tutorialActive;
    private int tutorialStage;
    /** Hazırlık düzeni (gün başlamadı) mı gösteriliyor. */
    private boolean prepUi = true;
    private boolean prepBegun;
    /** Açık istasyon seçenek paneli (yoksa null). */
    private MenuPanel panel;
    /** Panel kapanınca odağın döneceği düğme. */
    private View panelOrigin;
    /** Stokta olmadığı için eklenemeyen malzemeler (acil al düğmesi bunları da sunar). */
    private final Set<String> wantEmergency = new LinkedHashSet<>();
    private boolean tutSizeShown;
    private final Random fx = new Random();

    // ------------------------------------------------------------------ görünümler
    private FrameLayout host;
    private LinearLayout content;
    private LinearLayout body;
    private TextView moneyTv, starsTv, timeTv, pendingTv, timeCaption;
    private StarsView starsView;
    private LinearLayout statusBar;
    private String statusKey = "";
    private int lastAnnounceBucket = Integer.MAX_VALUE;
    private TextView noticeTv;
    private WorkersBox workersBox;
    private PrepBox prepBox;
    private LinearLayout ordersGroup, stationsGroup;
    private LinearLayout cardsBox;
    private TextView emptyTv;
    private Button emergencyBtn;
    private final Map<Integer, OrderCard> cards = new LinkedHashMap<>();
    private final Map<Station, StationTile> tiles = new HashMap<>();
    private TextView slotsTv;
    private LinearLayout slotsCell;
    private Button deliverBtn, nextBtn, pauseBtn, readBtn, openBtn, skipBtn;
    private LinearLayout openBar;
    private FrameLayout overlay;
    private TextView overlayTitle;
    private Button quitBtn;

    public GameScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Gün " + a.save.day + ", oyun ekranı"; }

    @Override public String music() { return "music_day_calm"; }

    /** Dükkân ambiyansı: gün ekranında (hazırlık ve gün) düşük sesle çalar; dosya yoksa sessiz. */
    @Override public String ambience() { return SoundManager.AMBIENCE_SHOP; }

    // ================================================================== oluşturma
    @Override public View create() {
        gameSave = a.save.copy();
        engine = new GameEngine(System.nanoTime(), gameSave);
        narrator = new Narrator(engine, a.speaker);
        engine.addListener(narrator);
        engine.addListener(new Feedback());
        inUse = Texts.stationsInUse(gameSave.day);
        tutorialActive = gameSave.day == 1 && !gameSave.tutorialDone;

        host = new FrameLayout(ctx);
        host.setKeepScreenOn(true);
        content = rootColumn();
        host.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        TextView head = makeTitle("Gün " + gameSave.day);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        head.setPadding(0, 0, 0, dp(6));
        head.setContentDescription("Gün " + gameSave.day + ", oyun ekranı");
        content.addView(head);

        buildStatusBar();

        // Son önemli olay (hatalı sipariş, kilitli adım, reddedilen seçim...): görsel + okunabilir.
        noticeTv = Ui.label(ctx, "", 16, Theme.GOLD, true);
        noticeTv.setFocusable(true);
        noticeTv.setVisibility(View.GONE);
        noticeTv.setPadding(dp(6), dp(2), dp(6), dp(4));
        content.addView(noticeTv);

        // Acil al: kaydırılan listenin DIŞINDA, durum çubuğunun hemen altında sabit; kartların altına düşmez.
        emergencyBtn = Ui.button(ctx, "", Ui.DANGER);
        emergencyBtn.setVisibility(View.GONE);
        tap(emergencyBtn, new Runnable() { @Override public void run() { doEmergency(); } });
        content.addView(emergencyBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body = column();
        sv.addView(body, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));
        content.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        workersBox = new WorkersBox();
        body.addView(workersBox, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 2));
        prepBox = new PrepBox();

        ordersGroup = column();
        ordersGroup.addView(sectionHeading("Siparişler"));
        emptyTv = Ui.label(ctx, "Henüz sipariş yok. Müşteri bekleniyor.", 17, Theme.MUTED, false);
        emptyTv.setFocusable(true);
        emptyTv.setPadding(dp(4), dp(6), dp(4), dp(6));
        ordersGroup.addView(emptyTv);
        cardsBox = column();
        ordersGroup.addView(cardsBox);
        body.addView(ordersGroup);

        stationsGroup = column();
        stationsGroup.addView(sectionHeading("İstasyonlar"));
        buildGrid(stationsGroup);
        body.addView(stationsGroup);

        buildBottomBar();
        applyPhase(true);
        return host;
    }

    private LinearLayout statCell(Icons.Kind kind, TextView valueOut, String caption, View below) {
        return statCell(kind, valueOut, caption, below, null);
    }

    private LinearLayout statCell(Icons.Kind kind, TextView valueOut, String caption, View below, TextView[] captionOut) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new IconView(ctx, kind, 24), Ui.lp(ctx, Ui.dp(ctx, 24), Ui.dp(ctx, 24), 0, 0, 4, 0));
        row.addView(valueOut);
        cell.addView(row);
        if (below != null) cell.addView(below);
        else {
            TextView cap = Ui.label(ctx, caption, 12, Theme.MUTED, false);
            cap.setGravity(Gravity.CENTER);
            cell.addView(cap);
            if (captionOut != null) captionOut[0] = cap;
        }
        return cell;
    }

    private void buildStatusBar() {
        statusBar = new LinearLayout(ctx);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setBackground(Ui.round(ctx, Theme.CARD, Theme.GOLD, 2, 16));
        statusBar.setPadding(dp(6), dp(8), dp(6), dp(8));

        moneyTv = Ui.label(ctx, "", 18, Theme.GOLD, true);
        starsTv = Ui.label(ctx, "", 18, Theme.GOLD, true);
        timeTv = Ui.label(ctx, "", 18, Theme.CREAM, true);
        pendingTv = Ui.label(ctx, "", 18, Theme.CREAM, true);
        starsView = new StarsView(ctx, 12);

        statusBar.addView(statCell(Icons.Kind.COIN, moneyTv, "Para", null), new LinearLayout.LayoutParams(0, Ui.WRAP, 1.15f));
        LinearLayout starsCell = statCell(Icons.Kind.STAR, starsTv, "", starsView);
        statusBar.addView(starsCell, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView[] capOut = new TextView[1];
        statusBar.addView(statCell(Icons.Kind.CLOCK, timeTv, "Süre", null, capOut), new LinearLayout.LayoutParams(0, Ui.WRAP, 0.9f));
        timeCaption = capOut[0];
        statusBar.addView(statCell(Icons.Kind.BAG, pendingTv, "Bekleyen", null), new LinearLayout.LayoutParams(0, Ui.WRAP, 0.9f));
        Ui.groupAsText(statusBar);
        content.addView(statusBar, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));
    }

    private void buildGrid(LinearLayout parent) {
        Station[] all = Station.values();
        LinearLayout row = null;
        for (int i = 0; i < all.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                parent.addView(row, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
            }
            StationTile t = new StationTile(all[i]);
            tiles.put(all[i], t);
            row.addView(t, Ui.lp(ctx, 0, Ui.WRAP, 1f, i % 2 == 0 ? 0 : 5, 0, i % 2 == 0 ? 5 : 0, 0));
        }
        slotsCell = column();
        slotsCell.setGravity(Gravity.CENTER);
        slotsCell.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 16));
        slotsCell.setMinimumHeight(dp(104));
        slotsTv = Ui.label(ctx, "", 16, Theme.CREAM, false);
        slotsTv.setGravity(Gravity.CENTER);
        slotsCell.addView(slotsTv);
        Ui.groupAsText(slotsCell);
        row.addView(slotsCell, Ui.lp(ctx, 0, Ui.WRAP, 1f, 5, 0, 0, 0));
    }

    private void buildBottomBar() {
        LinearLayout bar = column();
        bar.setPadding(0, dp(6), 0, 0);

        // hazırlık aşamasında: dükkânı aç / hazırlık yapmadan aç
        openBar = column();
        openBtn = Ui.button(ctx, "Dükkânı aç", Ui.GOLD);
        openBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        openBtn.setMinHeight(dp(68));
        openBtn.setMinimumHeight(dp(68));
        openBtn.setContentDescription("Dükkânı aç");
        tap(openBtn, new Runnable() { @Override public void run() { onOpenShop(); } });
        openBar.addView(openBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
        skipBtn = Ui.button(ctx, "Hazırlık yapmadan aç", Ui.SECONDARY);
        skipBtn.setContentDescription("Hazırlık yapmadan aç");
        tap(skipBtn, new Runnable() { @Override public void run() { onOpenShop(); } });
        openBar.addView(skipBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
        bar.addView(openBar);

        deliverBtn = Ui.button(ctx, "Teslim et", Ui.GOLD);
        deliverBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        deliverBtn.setMinHeight(dp(68));
        deliverBtn.setMinimumHeight(dp(68));
        tap(deliverBtn, new Runnable() { @Override public void run() { onDeliver(); } });
        bar.addView(deliverBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        nextBtn = Ui.button(ctx, "Sonraki sipariş", Ui.SECONDARY);
        pauseBtn = Ui.button(ctx, "Duraklat", Ui.SECONDARY);
        readBtn = Ui.button(ctx, "Durumu oku", Ui.SECONDARY);
        for (Button b : new Button[]{nextBtn, pauseBtn, readBtn}) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            b.setPadding(dp(4), dp(8), dp(4), dp(8));
        }
        tap(nextBtn, new Runnable() { @Override public void run() { onNext(); } });
        tap(pauseBtn, new Runnable() { @Override public void run() { pauseGame(); } });
        tap(readBtn, new Runnable() { @Override public void run() { a.speaker.force(narrator.statusText()); } });
        nextBtn.setContentDescription("Sonraki sipariş");
        pauseBtn.setContentDescription("Duraklat");
        readBtn.setContentDescription("Durumu oku");
        row.addView(nextBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 3, 0));
        row.addView(pauseBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 3, 0, 3, 0));
        row.addView(readBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 3, 0, 0, 0));
        bar.addView(row);
        content.addView(bar);
    }

    /** Hazırlık düzeni ile gün düzeni arasında geçiş (yalnızca değişince iş yapar). */
    private void applyPhase(boolean force) {
        GameEngine.State st = engine.getState();
        boolean prep = st == GameEngine.State.READY || st == GameEngine.State.PREP;
        if (!force && prep == prepUi) return;
        prepUi = prep;
        ordersGroup.setVisibility(prep ? View.GONE : View.VISIBLE);
        stationsGroup.setVisibility(prep ? View.GONE : View.VISIBLE);
        openBar.setVisibility(prep ? View.VISIBLE : View.GONE);
        deliverBtn.setVisibility(prep ? View.GONE : View.VISIBLE);
        nextBtn.setVisibility(prep ? View.GONE : View.VISIBLE);
        // v0.8: hazırlık bölümü (döner dilimle, ayran doldur, tepsi hazırla, hazır stok göstergeleri, stok okuma düğmesi)
        // YALNIZCA hazırlık aşamasında vardır; gün içinde ekrandan kalkar. Gün içi hazırlık istasyon düğmelerinin
        // eylem menüsünden/panelinden (prep:* seçenekleri) yapılır; hazır stok "Durumu oku"da ve istasyon etiketindedir.
        body.removeView(prepBox);
        if (prep) body.addView(prepBox, 1, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 2));
        prepBox.setVisibility(prep ? View.VISIBLE : View.GONE);
        dirty = true;
    }

    // ================================================================== yaşam döngüsü
    @Override public void onEnter() {
        queueFrame();
        refresh(true);
        handler.postDelayed(new Runnable() {
            @Override public void run() { beginPrepNow(); }
        }, PREP_BEGIN_DELAY_MS);
    }

    private void beginPrepNow() {
        if (prepBegun || exited) return;
        prepBegun = true;
        engine.beginPrep();
        dirty = true;
    }

    @Override public void onExit() {
        exited = true;
        handler.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(this);
        frameQueued = false;
        a.sound.stopAllLoops();
    }

    @Override public void onPause() {
        if (!finishing && !exited) pauseGame(true);
    }

    @Override public void onResume() {
        if (paused && overlay != null && overlayTitle != null) a.focusOn(overlayTitle);
    }

    @Override public boolean onBack() {
        if (finishing) { showSummary(); return true; } // gün bitti: beklemeden özete geç
        if (panel != null && !paused) { cancelPanel(panel); return true; }
        if (paused) resumeGame(); else pauseGame();
        return true;
    }

    private void queueFrame() {
        if (frameQueued || paused || exited) return;
        frameQueued = true;
        lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long nanos) {
        frameQueued = false;
        if (exited || paused) return;
        float dt = lastNanos == 0 ? 0f : (nanos - lastNanos) / 1_000_000_000f;
        lastNanos = nanos;
        dt = Math.max(0f, Math.min(dt, 0.25f));

        if (!finishing) {
            GameEngine.State st = engine.getState();
            if (st == GameEngine.State.PREP || st == GameEngine.State.RUNNING) engine.tick(dt);
            uiAccum += dt;
            if (dirty || uiAccum >= 0.1f) {
                refresh(false);
                uiAccum = 0f;
            }
            updateAuto(dt);
        }
        if (!exited && !paused && !finishing) {
            frameQueued = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    // ================================================================== duraklatma
    private void pauseGame() { pauseGame(false); }

    /** quiet: uygulama arka plana giderken ses/odak üretmeden duraklat. */
    private void pauseGame(boolean quiet) {
        if (paused || finishing || exited) return;
        paused = true;
        confirmQuit = false;
        Choreographer.getInstance().removeFrameCallback(this);
        frameQueued = false;
        a.sound.pauseLoops();
        a.sound.setAmbienceHold(true); // Duraklat: ambiyans da susar
        if (!quiet) a.haptics.tap();
        showOverlay(quiet);
    }

    private void resumeGame() {
        if (!paused) return;
        paused = false;
        if (overlay != null) { host.removeView(overlay); overlay = null; }
        if (panel != null) panel.overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        updateContentHidden();
        dirty = true;
        queueFrame();
        a.sound.resumeLoops();
        a.sound.setAmbienceHold(false);
        a.sound.play("sfx_tap");
        a.speaker.announce("Oyun devam ediyor.", true);
        a.focusOn(panel != null ? panel.titleView : pauseBtn);
    }

    private void updateContentHidden() {
        boolean hide = paused || panel != null;
        content.setImportantForAccessibility(hide ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private void showOverlay(boolean quiet) {
        overlay = new FrameLayout(ctx);
        overlay.setBackgroundColor(0xE61A0E08);
        overlay.setClickable(true); // arkadaki oyuna dokunmayı engeller
        // Etiketsiz tıklanabilir perde TalkBack'te "etiketsiz" odak noktası olmasın: kendisi gizli, çocukları açık.
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout pnl = column();
        pnl.setBackground(Ui.round(ctx, Theme.CARD, Theme.GOLD, 3, 20));
        pnl.setPadding(dp(18), dp(18), dp(18), dp(18));
        overlayTitle = Ui.label(ctx, "Duraklatıldı", 30, Theme.GOLD, true);
        Ui.heading(overlayTitle);
        pnl.addView(overlayTitle);
        TextView info = Ui.label(ctx, "Oyun durdu.", 18, Theme.CREAM, false);
        info.setFocusable(true);
        info.setPadding(0, dp(6), 0, dp(10));
        pnl.addView(info);

        pnl.addView(button("Devam et", Ui.PRIMARY, new Runnable() {
            @Override public void run() { resumeGame(); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        pnl.addView(button("Durumu oku", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(narrator.statusText()); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));

        // v0.7: oyun akışı oyun ortasında da değişir (tek düğme, sıradakine geçer)
        final Button flowBtn = Ui.button(ctx, Texts.flowButtonLabel(engine.getFlowMode()), Ui.SECONDARY);
        flowBtn.setContentDescription(Texts.flowButtonDescription(engine.getFlowMode()));
        tap(flowBtn, new Runnable() {
            @Override public void run() {
                engine.setFlowMode(engine.getFlowMode().next()); // Narrator duyurur, Feedback kaydı günceller
                a.saveNow();
                flowBtn.setText(Texts.flowButtonLabel(engine.getFlowMode()));
                flowBtn.setContentDescription(Texts.flowButtonDescription(engine.getFlowMode()));
                if (!a.save.speechOn) a.speaker.announce(engine.getFlowMode().spoken(), true);
            }
        });
        pnl.addView(flowBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));

        // v0.8: süre baskısı oyun ortasında da değişir (aç/kapa)
        final Button timeBtn = Ui.button(ctx, Texts.timePressureButtonLabel(engine.isTimed()), Ui.SECONDARY);
        timeBtn.setContentDescription(Texts.timePressureButtonDescription(engine.isTimed()));
        tap(timeBtn, new Runnable() {
            @Override public void run() {
                engine.setTimePressure(!engine.isTimed()); // Narrator duyurur, Feedback kaydı günceller
                a.saveNow();
                timeBtn.setText(Texts.timePressureButtonLabel(engine.isTimed()));
                timeBtn.setContentDescription(Texts.timePressureButtonDescription(engine.isTimed()));
                if (!a.save.speechOn) a.speaker.announce(narrator.timePressureChanged(engine.isTimed()), true);
            }
        });
        pnl.addView(timeBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));

        final TextView help = Ui.label(ctx, Texts.quickHelp(engine.getFlowMode(), engine.isTimed()), 17, Theme.CREAM, false);
        help.setFocusable(true);
        help.setVisibility(View.GONE);
        help.setPadding(dp(4), dp(6), dp(4), dp(6));
        pnl.addView(button("Nasıl oynanır", Ui.SECONDARY, new Runnable() {
            @Override public void run() {
                boolean show = help.getVisibility() != View.VISIBLE;
                help.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) {
                    help.setText(Texts.quickHelp(engine.getFlowMode(), engine.isTimed()));
                    a.speaker.force(Texts.quickHelp(engine.getFlowMode(), engine.isTimed()));
                }
            }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        pnl.addView(help);

        quitBtn = button("Günü bırak, ana menü", Ui.DANGER, new Runnable() {
            @Override public void run() { onQuit(); }
        });
        pnl.addView(quitBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
        plp.setMargins(dp(20), dp(20), dp(20), dp(20));
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(pnl, plp);
        sv.addView(wrap, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
        overlay.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        host.addView(overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (panel != null) panel.overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        updateContentHidden();
        if (!quiet) {
            a.sound.play("sfx_tap");
            a.focusOn(overlayTitle);
            a.speaker.screenChanged("Duraklatıldı");
        }
    }

    private void onQuit() {
        if (!confirmQuit) {
            confirmQuit = true;
            quitBtn.setText("Emin misin? Tekrar dokun");
            quitBtn.setContentDescription("Emin misin? Günün ilerlemesi kaybolur, tekrar dokun");
            a.speaker.announce("Günün ilerlemesi kaybolur. Çıkmak için tekrar dokun.", true);
            return;
        }
        a.show(new MenuScreen(a));
    }

    // ================================================================== istasyon eylem menüsü
    // İki erişim yolu AYNI motor çağrısını yapar (engine.applyStationOption):
    //  (1) TalkBack Eylemleri: düğmenin AccessibilityDelegate'i her seçenek için özel eylem sunar (ActionMenu);
    //  (2) seçenek paneli: düğmeye normal dokunuş (TalkBack açıkken çift dokunuş) büyük düğmeli panel açar (MenuPanel).

    private void openPanel(MenuPanel p, View origin) {
        if (panel != null) return;
        panel = p;
        panelOrigin = origin;
        host.addView(p.overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        updateContentHidden();
        a.focusOn(p.titleView);
        dirty = true;
        updateEmergency(engine.getSelectedOrder());
    }

    private void closePanel(boolean focusBack) {
        MenuPanel p = panel;
        if (p == null) return;
        host.removeView(p.overlay);
        panel = null;
        updateContentHidden();
        dirty = true;
        View back = panelOrigin;
        panelOrigin = null;
        if (focusBack && !paused && back != null) a.focusOn(back);
    }

    private void cancelPanel(MenuPanel p) {
        closePanel(true);
        a.speaker.announce("Seçenekler kapatıldı.", true);
    }

    /** Bir seçenek seçilince panel kapansın mı? (Malzeme eklerken ve okurken açık kalır: art arda kullanım.) */
    private static boolean shouldClose(StationOption op, StartResult r) {
        if (r == StartResult.NO_ORDER || r == StartResult.ORDER_READY || r == StartResult.NOT_RUNNING) return true;
        switch (op.group) {
            case READ:
            case ADD:
            case REMOVE:
                return false;
            case CONTROL:
                return op.id.equals("finish") && r == StartResult.STARTED;
            default:
                return r == StartResult.STARTED || r == StartResult.OUT_OF_STOCK;
        }
    }

    private final MenuPanel.Host panelHost = new MenuPanel.Host() {
        @Override public void onPick(MenuPanel p, StationOption op) {
            if (p != panel || paused || finishing) return;
            Station st = Station.byId(p.stationId);
            Order o = p.orderId >= 0 ? engine.getOrder(p.orderId) : null;
            StartResult r = applyOption(o, st, op.id);
            if (p != panel) return; // eylem günü bitirmiş olabilir
            if (shouldClose(op, r)) closePanel(true);
            else { p.refresh(); updateEmergency(engine.getSelectedOrder()); }
        }

        @Override public void onClose(MenuPanel p) {
            if (p == panel && !paused) cancelPanel(p);
        }

        @Override public void onEmergency(MenuPanel p) {
            if (p == panel && !paused) doEmergency();
        }
    };

    /** Bir istasyonun (ve isteğe bağlı sipariş / et filtresinin) seçenek kaynağı; panel ve TalkBack eylemleri kullanır. */
    private final class StationSource implements MenuPanel.Source, ActionMenu.Source {
        final Station st;
        /** -2: her seferinde en uygun siparişi seç (düğme eylemleri); -1: yalnızca hazırlık; >=0: sabit sipariş. */
        final int fixedOrder;
        final String meatFilter;

        StationSource(Station st, int fixedOrder, String meatFilter) {
            this.st = st;
            this.fixedOrder = fixedOrder;
            this.meatFilter = meatFilter;
        }

        Order order() {
            if (fixedOrder == -1) return null;
            if (fixedOrder >= 0) return engine.getOrder(fixedOrder);
            return pickOrderFor(st);
        }

        @Override public List<StationOption> options() {
            if (finishing || exited) return new ArrayList<>();
            Order o = engine.getState() == GameEngine.State.RUNNING ? order() : null;
            List<StationOption> all = engine.getStationOptions(o == null ? -1 : o.id, -1, st.id);
            if (meatFilter == null) return all;
            List<StationOption> out = new ArrayList<>();
            for (StationOption op : all) {
                if (op.id.startsWith("prep:doner:") && meatFilter.equals(op.ingredientId)) out.add(op);
            }
            return out;
        }

        @Override public String info() { return Texts.menuInfo(order()); }

        @Override public String status() {
            Order o = order();
            if (o == null || st != Station.SOS) return "";
            int li = engine.resolveStationItem(o.id, -1, st.id);
            return li < 0 ? "" : Texts.addedStatus(o.getLine(li));
        }
    }

    /** Eylem listesi izlenen düğmeler ve son eylem parmak izleri. */
    private final Map<View, StationSource> actionSrc = new HashMap<>();
    private final Map<View, String> actionSig = new HashMap<>();

    /** Düğmeye TalkBack özel eylemlerini bağlar (tile: istasyon düğmesi, değilse hazırlık düğmesi). */
    private void attachMenu(View v, StationSource src, final Station st, final boolean tile) {
        actionSrc.put(v, src);
        ActionMenu.attach(v, src, new ActionMenu.Handler() {
            @Override public void onAction(String optionId) {
                if (tile) onStationAction(st, optionId); else onPrepAction(st, optionId);
            }
        });
    }

    private boolean a11yOn() {
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
            return am != null && am.isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Durum değişip eylem listesi (etiketler dahil) farklılaştıysa düğmenin erişilebilirlik düğümünü tazeletir. */
    private void refreshActions(View v) {
        StationSource src = actionSrc.get(v);
        if (src == null || !a11yOn()) return;
        String sig = ActionMenu.signature(src.options());
        if (!sig.equals(actionSig.get(v))) {
            actionSig.put(v, sig);
            ActionMenu.invalidate(v);
        }
    }

    /** TalkBack özel eylemi seçildi (panel açmadan uygular). */
    private void onStationAction(Station st, String optionId) {
        if (paused || finishing || panel != null) return;
        if (engine.getState() != GameEngine.State.RUNNING) { a.speaker.say("Önce dükkânı aç.", true); return; }
        a.sound.play("sfx_tap");
        applyOption(pickOrderFor(st), st, optionId);
    }

    private void onPrepAction(Station st, String optionId) {
        if (paused || finishing || panel != null) return;
        if (!prepBegun) beginPrepNow();
        a.sound.play("sfx_tap");
        applyOption(null, st, optionId);
    }

    private void ensureSelected(Order t) {
        Order sel = engine.getSelectedOrder();
        if (sel == null || sel.id != t.id) engine.selectOrder(t.id);
    }

    /**
     * Seçeneği motora uygular (TEK yol: düğme dokunuşu, TalkBack eylemi ve panel hep buradan geçer). Sonucun
     * duyurusunu motor olaylarıyla Narrator yapar; olay vermeyen sonuçlar burada sesle bildirilir.
     */
    private StartResult applyOption(Order t, Station st, String optionId) {
        if (t != null) ensureSelected(t);
        StartResult r = engine.applyStationOption(t == null ? -1 : t.id, -1, st.id, optionId);
        switch (r) {
            case OUT_OF_STOCK:
                if (optionId.startsWith("add:")) wantEmergency.add(optionId.substring(4));
                break;
            case STEP_LOCKED:
                a.sound.play("sfx_error", 0f, 0.7f);
                a.haptics.warning();
                break;
            case NO_ORDER:
            case ORDER_BUSY:
            case ORDER_READY:
            case NOT_RUNNING:
            case INVALID:
                if (optionId.equals("step") || optionId.startsWith("size:")) error(narrator.startResultText(r));
                break;
            default:
                break;
        }
        dirty = true;
        return r;
    }

    /**
     * Tek dokunuşla uygulanacak seçenek (panel açmadan), yoksa null. Kural: işlem seçenekleri (hazırlık hariç)
     * içinde etkin bir işlem yoksa kilit nedenini duyurmak için ilki uygulanır; tek işlem varsa o; yalnızca
     * hazırlık varsa ve tek ise o.
     */
    static StationOption directOption(List<StationOption> ops) {
        // v0.7: kural motorda (bot ölçütleri de aynı kuralı kullanır); okuma seçenekleri ("Hedefi oku") tek işlemi
        // panele zorlamaz: Rahat/Dengeli'de "Malzemeleri ekle" ve otomatik boylu dilimleme tek dokunuştur.
        return StationOption.direct(ops);
    }

    private void openMenu(String title, Station st, Order target, View origin, String meatFilter) {
        int oid = target == null ? -1 : target.id;
        final StationSource src = new StationSource(st, oid, meatFilter);
        MenuPanel p = new MenuPanel(a, title, st.id, oid, src, panelHost);
        boolean hasSize = false;
        for (StationOption op : src.options()) if (op.group == StationOption.Group.SIZE) hasSize = true;
        openPanel(p, origin);
        if (tutorialActive && hasSize && !tutSizeShown) {
            tutSizeShown = true;
            a.speaker.say("Öğretici. " + Texts.tutSize(engine.getFlowMode()), false, Narrator.CAT_TUTORIAL, narrator.freshSinceNow());
        }
    }

    // ================================================================== eylemler
    private void error(String msg) {
        a.sound.play("sfx_error");
        a.haptics.error();
        if (msg != null && !msg.isEmpty()) a.speaker.say(msg, true);
    }

    /** Siparişte bu istasyonda başlatılabilir (açık) adım var mı? */
    private static boolean hasOpenStepAt(Order o, Station st) {
        return Texts.needsStation(o, st);
    }

    /** Siparişte bu istasyonda henüz açılmamış (kilitli) adım var mı? */
    private static boolean hasLockedStepAt(Order o, Station st) {
        if (o.isReady()) return false;
        for (OrderLine l : o.getLines()) {
            for (int i = 0; i < l.stepCount(); i++) {
                if (l.step(i).station == st && !l.isStepDone(i) && !l.isStepRunning(i) && !l.isStepUnlocked(i)) return true;
            }
        }
        return st == Station.PAKET && !o.isPacking() && !o.allLinesDone();
    }

    /** İstasyona dokunulunca hangi sipariş: önce seçili, sonra uygun adımı olan diğerleri; yoksa kilitli adımı olan. */
    private Order pickOrderFor(Station st) {
        Order sel = engine.getSelectedOrder();
        List<Order> cand = new ArrayList<>();
        if (sel != null) cand.add(sel);
        for (Order o : engine.getOrders()) if (sel == null || o.id != sel.id) cand.add(o);
        for (Order o : cand) if (!o.isReady() && hasOpenStepAt(o, st)) return o;
        for (Order o : cand) if (hasLockedStepAt(o, st)) return o;
        return sel;
    }

    /** İstasyon düğmesine dokunuldu (TalkBack kapalıyken tek, açıkken varsayılan çift dokunuş). */
    private void onStation(Station st) {
        if (paused || finishing || panel != null) return;
        if (engine.getState() != GameEngine.State.RUNNING) {
            a.speaker.say("Önce dükkânı aç.", true);
            return;
        }
        Order target = pickOrderFor(st);
        List<StationOption> ops = engine.getStationOptions(target == null ? -1 : target.id, -1, st.id);
        if (ops.isEmpty()) {
            if (target == null) {
                error(engine.getOrders().isEmpty() ? "Henüz sipariş yok." : "Önce bir sipariş seç.");
                return;
            }
            applyOption(target, st, "step"); // istasyonda iş yok: motor yanlış istasyonu bildirir
            return;
        }
        StationOption direct = directOption(ops);
        if (direct != null) { applyOption(target, st, direct.id); return; }
        if (target != null) ensureSelected(target);
        openMenu(st.displayName + ": seçenekler", st, target, tiles.get(st), null);
    }

    private void onSelectItem(int orderId, int itemIndex) {
        if (paused || finishing || panel != null) return;
        Order sel = engine.getSelectedOrder();
        if (sel == null || sel.id != orderId) engine.selectOrder(orderId);
        engine.selectItem(itemIndex);
        dirty = true;
    }

    private void onPackRow(int orderId) {
        if (paused || finishing || panel != null) return;
        Order o = engine.getOrder(orderId);
        if (o == null) return;
        if (o.isReady()) {
            Order sel = engine.getSelectedOrder();
            if (sel == null || sel.id != orderId) engine.selectOrder(orderId);
            a.speaker.say("Sipariş paketlendi. Teslim et düğmesine dokun.", true);
            return;
        }
        applyOption(o, Station.PAKET, "step");
    }

    private void onDeliver() {
        if (paused || finishing || panel != null) return;
        Order target = engine.getSelectedOrder();
        if (target == null || !target.isReady()) {
            target = null;
            for (Order o : engine.getOrders()) if (o.isReady()) { target = o; break; }
        }
        if (target == null) { error("Teslime hazır sipariş yok."); return; }
        engine.deliver(target.id);
        wantEmergency.clear();
        dirty = true;
    }

    private void onNext() {
        if (paused || finishing || panel != null) return;
        if (!engine.selectNextOrder()) error("Henüz sipariş yok.");
        dirty = true;
    }

    /** Acil al: eksik adım malzemelerinin hepsi + eklenemeyen malzemeler (panelin ve ana ekranın düğmesi). */
    private void doEmergency() {
        if (paused || finishing) return;
        if (missingOrderId >= 0 && engine.emergencyBuyMissing(missingOrderId)) missingOrderId = -1;
        for (String id : new ArrayList<>(wantEmergency)) {
            if (engine.emergencyBuy(id)) wantEmergency.remove(id);
            else break; // para yetmedi: motor onNotEnoughMoney bildirdi
        }
        dirty = true;
        if (panel != null) panel.refresh();
    }

    private void onOpenShop() {
        if (paused || finishing || panel != null) return;
        if (!prepUi) return;
        if (!engine.startDay()) return;
        prepBegun = true;
        applyPhase(false);
        refresh(true);
        a.focusOn(emptyTv);
    }

    /** Hazırlık düğmesine dokunuldu: tek seçenek doğrudan uygulanır, birden çoksa panel açılır. */
    private void onPrep(Station st, String meatFilter, View origin, String title) {
        if (paused || finishing || panel != null) return;
        if (!prepBegun) beginPrepNow();
        List<StationOption> ops = new StationSource(st, -1, meatFilter).options();
        if (ops.isEmpty()) { error("Bu hazırlık şu an yapılamaz."); return; }
        if (ops.size() == 1) { applyOption(null, st, ops.get(0).id); return; }
        openMenu(title, st, null, origin, meatFilter);
    }

    /** Seçim yoksa kısa süre sonra kendiliğinden bir sipariş seçer; son 5 sn geri sayım tıkı. */
    private void updateAuto(float dt) {
        if (engine.getState() != GameEngine.State.RUNNING) return;
        boolean allow = !tutorialActive || tutorialStage >= 5;
        if (engine.getSelectedOrderId() < 0 && !engine.getOrders().isEmpty() && allow) {
            noSelTimer += dt;
            if (noSelTimer >= 1.5f) {
                noSelTimer = 0f;
                Order pick = null;
                for (Order o : engine.getOrders()) if (!o.isReady() && !o.isRunning()) { pick = o; break; }
                if (pick == null) pick = engine.getOrders().get(0);
                engine.selectOrder(pick.id);
            }
        } else {
            noSelTimer = 0f;
        }
        float left = engine.getTimeLeft();
        if (engine.isTimed() && !engine.isOvertime() && left <= 5f) {
            int sec = (int) Math.ceil(left);
            if (sec != lastTickSecond) { lastTickSecond = sec; a.sound.play("sfx_tick"); }
        }
    }

    // ================================================================== yenileme
    private void refresh(boolean force) {
        dirty = false;
        applyPhase(false);
        List<Order> orders = engine.getOrders();
        Order selected = engine.getSelectedOrder();

        // durum çubuğu
        moneyTv.setText(Texts.money(engine.getMoney()));
        starsTv.setText(Narrator.fmtStars(engine.getStars()));
        starsView.setValue(engine.getStars());
        if (prepUi) { timeTv.setText("Hazırlık"); setTimeCaption("Süre"); }
        else if (!engine.isTimed()) { timeTv.setText(String.valueOf(engine.getRemainingCustomers())); setTimeCaption("Kalan müşteri"); }
        else { timeTv.setText(engine.isOvertime() ? "Mesai" : Texts.clock(engine.getTimeLeft())); setTimeCaption("Süre"); }
        pendingTv.setText(String.valueOf(orders.size()));
        updateStatusDescription(orders.size(), selected);

        // sipariş kartları
        for (Iterator<Map.Entry<Integer, OrderCard>> it = cards.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, OrderCard> e = it.next();
            if (engine.getOrder(e.getKey()) == null) { cardsBox.removeView(e.getValue()); it.remove(); }
        }
        int idx = 0;
        for (Order o : orders) {
            OrderCard c = cards.get(o.id);
            if (c == null) {
                c = new OrderCard(o);
                cards.put(o.id, c);
                cardsBox.addView(c, Math.min(idx, cardsBox.getChildCount()), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
            }
            c.update(o, selected != null && selected.id == o.id);
            idx++;
        }
        emptyTv.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);

        // acil alım
        updateEmergency(selected);

        // açık seçenek paneli: listeyi tazele (malzeme eklerken durum değişir); sipariş gittiyse kapat
        if (panel != null) {
            if (panel.orderId >= 0 && engine.getOrder(panel.orderId) == null) {
                closePanel(true);
                a.speaker.announce("Sipariş artık yok, seçenekler kapandı.", true);
            } else {
                panel.refresh();
                if (panel.optionCount() == 0) closePanel(true);
            }
        }

        // çalışanlar şeridi + hazırlık bölümü
        workersBox.update();
        prepBox.update();

        // istasyonlar
        if (!prepUi) {
            for (Map.Entry<Station, StationTile> e : tiles.entrySet()) e.getValue().update();
            slotsTv.setText("Boş yuva\n" + engine.getFreeSlots() + " / " + engine.getSlotCount());
            Ui.setDesc(slotsCell, Texts.slotsText(engine));
        }

        // teslim
        boolean anyReady = false;
        for (Order o : orders) if (o.isReady()) anyReady = true;
        Ui.setDesc(deliverBtn, Texts.deliverDescription(engine));
        deliverBtn.setText(anyReady ? "Teslim et  (hazır)" : "Teslim et");
        Ui.dim(deliverBtn, !anyReady);

        updateMusic(orders.size());
    }

    /** Acil al düğmesini eksik TÜM malzemeler için günceller; yeni belirince duyurur ve odağı ona taşır. */
    private void updateEmergency(Order selected) {
        List<Ingredient> missing = new ArrayList<>();
        int cost = 0;
        if (missingOrderId >= 0) {
            boolean still = selected != null && selected.id == missingOrderId && !selected.isReady();
            if (still) missing = engine.missingIngredients(missingOrderId);
            if (!still || missing.isEmpty()) { missingOrderId = -1; missing = new ArrayList<>(); }
            else cost = engine.emergencyCostForMissing(missingOrderId);
        }
        // tek tek eklemede stokta olmayan malzemeler
        for (Iterator<String> it = wantEmergency.iterator(); it.hasNext(); ) {
            String id = it.next();
            Ingredient ing = Catalog.ingredient(id);
            if (ing == null || engine.getSave().inventory.get(id) > 0 || engine.getTrayUses(id) > 0) { it.remove(); continue; }
            boolean dup = false;
            for (Ingredient m : missing) if (m.id.equals(id)) dup = true;
            if (!dup) { missing.add(ing); cost += ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH; }
        }
        if (missing.isEmpty()) {
            emergencyBtn.setVisibility(View.GONE);
            emergencyKey = "";
            if (panel != null) panel.setEmergency(null, null);
            return;
        }
        String label = Texts.emergencyLabel(missing, cost);
        String desc = Texts.emergencyDescription(missing, cost);
        emergencyBtn.setVisibility(View.VISIBLE);
        emergencyBtn.setText(label);
        Ui.setDesc(emergencyBtn, desc);
        if (panel != null) panel.setEmergency(label, desc);
        StringBuilder key = new StringBuilder().append(missingOrderId);
        for (Ingredient ing : missing) key.append('|').append(ing.id);
        if (!key.toString().equals(emergencyKey)) {
            emergencyKey = key.toString();
            // Belirdi (ya da eksik küme değişti): duyuru Narrator'da yapıldı (acil, kesici);
            // TalkBack erişilebilirlik odağı da doğrudan düğmeye taşınır (panel açıksa panelde kalır).
            if (panel == null && !paused) a.focusOn(emergencyBtn);
        }
    }

    private void setTimeCaption(String c) {
        if (timeCaption != null && !c.contentEquals(timeCaption.getText())) timeCaption.setText(c);
    }

    private void updateStatusDescription(int pending, Order selected) {
        // Düşük sıklıkta güncelle: yalnızca anlamlı değişimde (para/yıldız/bekleyen/seçili/10 sn dilimi/hazır stok).
        float left = engine.getTimeLeft();
        boolean timedDay = engine.isTimed();
        int bucket = !timedDay ? 1000 + engine.getRemainingCustomers() : (engine.isOvertime() ? -1 : (int) (left / 10f));
        String key = engine.getMoney() + "|" + Narrator.fmtStars(engine.getStars()) + "|" + pending + "|"
                + (selected == null ? -1 : selected.id) + "|" + (selected == null ? 0 : selected.getNextStepIndex()) + "|" + bucket
                + "|" + prepUi + "|" + engine.getReadyDonerHalves() + "|" + engine.getReadyDrinks() + "|" + engine.getTrayTotal() + "|" + engine.getReadyFries();
        if (key.equals(statusKey)) return;
        statusKey = key;
        // Canlı duyuru yalnızca süre eşiklerinde: 60, 30, 10 sn kala ve mesai başında.
        // (süresiz günde süre eşiği yoktur: müşteri gelişlerini Narrator zaten duyurur)
        int threshold = !timedDay ? Integer.MAX_VALUE : (engine.isOvertime() ? -1 : (left <= 10f ? 10 : left <= 30f ? 30 : left <= 60f ? 60 : Integer.MAX_VALUE));
        boolean announce = threshold != lastAnnounceBucket;
        lastAnnounceBucket = threshold;
        if (threshold == Integer.MAX_VALUE || prepUi) announce = false;
        Ui.liveUpdate(statusBar, narrator.statusShort(), announce);
    }

    private void updateMusic(int pending) {
        float elapsed = engine.getElapsed();
        boolean busy = pending >= 5 || (engine.isTimed() && engine.getTimeLeft() <= engine.getConfig().duration * 0.25f);
        if (busy) busyHold = elapsed + 12f;
        String want = (busy || elapsed < busyHold) ? "music_day_busy" : "music_day_calm";
        if (!want.equals(a.sound.currentMusic())) a.sound.playMusic(want);
    }

    private final Runnable clearNotice = new Runnable() {
        @Override public void run() { noticeTv.setVisibility(View.GONE); }
    };

    /** Son önemli olayı ekranda gösterir (okunabilir, canlı bölge değil: konuşmayı Narrator yapar). */
    private void notice(String text) {
        if (text == null || text.isEmpty() || noticeTv == null) return;
        if (panel != null) panel.setMessage(text);
        noticeTv.setText(text);
        noticeTv.setContentDescription(text);
        noticeTv.setVisibility(View.VISIBLE);
        handler.removeCallbacks(clearNotice);
        handler.postDelayed(clearNotice, 9000);
    }

    // ================================================================== bitiş
    /** Duyuru bitince ya da en geç bu kadar ms sonra özet ekranına geçilir. */
    static final long FINISH_MAX_MS = 4000;
    private static final long FINISH_MIN_MS = 1200;
    private DaySummary finishSummary;
    private boolean summaryShown;

    private void beginFinish(final DaySummary s) {
        if (finishing) return;
        finishing = true;
        finishSummary = s;
        a.save = gameSave; // motor başarısızlıkta durumu zaten geri aldı
        a.markStarted();
        a.saveNow();
        a.sound.stopAllLoops();
        if (panel != null) closePanel(false);
        final long t0 = SystemClock.uptimeMillis();
        Runnable r = new Runnable() {
            @Override public void run() {
                if (exited) return;
                long el = SystemClock.uptimeMillis() - t0;
                if ((el >= FINISH_MIN_MS && !a.speaker.isBusy()) || el >= FINISH_MAX_MS) showSummary();
                else handler.postDelayed(this, 150);
            }
        };
        handler.postDelayed(r, FINISH_MIN_MS);
    }

    private void showSummary() {
        if (summaryShown || exited || finishSummary == null) return;
        summaryShown = true;
        a.show(new SummaryScreen(a, finishSummary));
    }

    // ================================================================== öğretici
    /**
     * Öğretici aşaması. Metin boşsa aşama yalnızca ilerler (sessiz aşama). Konuşan ipucu "şunu yap" der; söylenmeden önce
     * oyuncunun o sırada başka eylem yapıp yapmadığı denetlenir (yaptıysa susar), bayat ipucu okunmaz.
     */
    private void tutorial(int stage, String text) {
        if (!tutorialActive || stage <= tutorialStage) return;
        tutorialStage = stage;
        if (text == null || text.isEmpty()) return;
        a.speaker.say("Öğretici. " + text, false, Narrator.CAT_TUTORIAL, narrator.freshSinceNow());
    }

    // ================================================================== geri bildirim
    private static float panForPosition(Order o) {
        int p = o.getPosition();
        return p <= 1 ? -0.8f : (p == 2 ? 0f : 0.8f);
    }

    private static float panForStation(Station s) {
        switch (s) {
            case DONER: return -0.8f;
            case EKMEK: return -0.4f;
            case SOS: return 0f;
            case PAKET: return 0.25f;
            case ICECEK: return 0.45f;
            case IZGARA: return 0.7f;
            default: return 0.8f;
        }
    }

    /** Adımın istasyon sesi: kızartma adımı sfx_fry (döngü), diğerleri istasyonun sesi. */
    static String sfxForStep(Step s) {
        return s != null && s.isFry() ? "sfx_fry" : sfxForStation(s == null ? Station.IZGARA : s.station);
    }

    static String sfxForPrep(PrepKind k) {
        return k == PrepKind.FRIES ? "sfx_fry" : sfxForStation(k.station);
    }

    static String sfxForStation(Station s) {
        switch (s) {
            case DONER: return "sfx_slice";
            case EKMEK: return "sfx_bread_warm";
            case SOS: return "sfx_sauce";
            case PAKET: return "sfx_wrap";
            case ICECEK: return "sfx_pour";
            default: return "sfx_grill_sizzle"; // ızgara ve tatlı/fırın
        }
    }

    /** Çırak sesi: kısık (%60) ve pan merkez. */
    private static final float APPRENTICE_VOLUME = 0.6f;

    private static String playerKey(Order o, Step s) { return "p:" + o.id + ":" + s.name; }

    private static String prepKey(PrepKind k) { return "prep:" + k.name(); }

    private static String apprenticeKey(int worker) { return "a:" + worker; }

    private static String autoKey(Order o, int itemIndex) { return "auto:" + o.id + ":" + itemIndex; }

    /** Motor olaylarını ses + titreşim + arayüz yenilemeye çevirir (anlatım Narrator'dadır). */
    private final class Feedback extends GameListenerAdapter {
        private final Narrator.StarsTracker starsFx = new Narrator.StarsTracker();

        @Override public void onPrepPhaseStarted(int day) {
            a.sound.play("sfx_tap", 0f, 0.8f);
            tutorial(1, Texts.TUT_PREP);
            dirty = true;
        }

        @Override public void onPrepPhaseEnded(int day) { dirty = true; }

        @Override public void onDayStarted(int day, DayConfig config) {
            starsFx.reset(engine.getStars());
            a.sound.play("sfx_day_start");
            a.haptics.dayStart();
            tutorial(3, Texts.TUT_OPEN);
            dirty = true;
        }

        @Override public void onCustomerArrived(Order o) {
            a.sound.play("sfx_customer_arrive", 0.7f);
            a.sound.playLater("sfx_order_new", panForPosition(o), 280);
            a.haptics.customer();
            tutorial(4, Texts.TUT_CUSTOMER);
            dirty = true;
        }

        @Override public void onOrderSelected(Order o) {
            a.sound.play("sfx_tap", panForPosition(o));
            tutorial(5, Texts.TUT_SELECTED);
            dirty = true;
        }

        @Override public void onItemSelected(Order o, int itemIndex) {
            a.sound.play("sfx_tap", panForPosition(o));
            dirty = true;
        }

        @Override public void onNextStepHint(Order o, Step s) {
            if (s != null && s.isTopping()) tutorial(7, Texts.tutTopping(engine.getFlowMode()));
            dirty = true;
        }

        @Override public void onStepStarted(Order o, Step step, int slot, float seconds) {
            if (step.isSized() && tutorialActive && !tutSizeShown) { // otomatik boyda panel açılmaz: öğretici burada
                tutSizeShown = true;
                a.speaker.say("Öğretici. " + Texts.tutSize(engine.getFlowMode()), false, Narrator.CAT_TUTORIAL, narrator.freshSinceNow());
            }
            a.sound.startLoop(playerKey(o, step), sfxForStep(step), panForStation(step.station), 1f, seconds);
            a.haptics.stepStart();
            tutorial(6, Texts.TUT_STEP);
            dirty = true;
        }

        @Override public void onStepFinished(Order o, Step step, boolean complete) {
            a.sound.stopLoop(playerKey(o, step));
            a.sound.play("sfx_step_done", panForPosition(o));
            a.haptics.stepDone();
            dirty = true;
        }

        @Override public void onItemFinished(Order o, int itemIndex) { dirty = true; }

        @Override public void onOrderReadyToPack(Order o) {
            a.sound.playLater("sfx_step_done", panForPosition(o), 150);
            tutorial(8, Texts.TUT_PACK);
            dirty = true;
        }

        @Override public void onOrderReady(Order o) {
            a.sound.playLater("sfx_step_done", panForPosition(o), 200);
            a.haptics.orderReady();
            tutorial(9, Texts.TUT_READY);
            dirty = true;
        }

        @Override public void onWrongStation(Order o, Station tapped, Station expected) {
            a.sound.play("sfx_error");
            a.haptics.error();
            notice(narrator.wrongStation(o, tapped, expected));
            dirty = true;
        }

        @Override public void onStepLocked(Order o, Step step) {
            notice(narrator.stepLocked(o, step));
            dirty = true;
        }

        @Override public void onNoFreeSlot(Order o) {
            a.sound.play("sfx_error");
            a.haptics.error();
        }

        @Override public void onToppingsRejected(Order o, int itemIndex, StartResult reason, Ingredient ingredient) {
            a.sound.play("sfx_error");
            a.haptics.error();
            notice(narrator.toppingsRejected(o, itemIndex, reason, ingredient));
            dirty = true;
        }

        // ---- tek tek malzeme ekleme (kısa işler: onStepStarted/ses döngüsü YOK, tek atış ses)
        @Override public void onToppingAddStarted(Order o, int itemIndex, Ingredient ing, int count, float seconds) {
            boolean salad = ing.category == Ingredient.Category.SALATA;
            String sfx = ToppingSfx.sfxFor(ing);
            // domates: ıslak dilim; marul/soğan: çıtır yaprak; sos: alçak, yumuşak; hafif rastgele perde
            float rate = (salad ? 1.0f : 0.9f) + (fx.nextFloat() - 0.5f) * 0.1f;
            float pan = panForStation(Station.SOS);
            a.sound.play(sfx, pan, 0.9f, rate);
            if (count >= 2) a.sound.playLater(sfx, pan, 150); // bol: çift vuruş
            a.haptics.tap();
            dirty = true;
        }

        @Override public void onToppingAdded(Order o, int itemIndex, Ingredient ing, int count) {
            a.sound.play("sfx_tap", panForStation(Station.SOS), 0.5f, 1.4f);
            notice(narrator.toppingAdded(ing, count));
            dirty = true;
        }

        @Override public void onToppingRemoved(Order o, int itemIndex, Ingredient ing, int remaining) {
            a.sound.play("sfx_tap", panForStation(Station.SOS), 0.8f, 0.75f);
            a.haptics.tap();
            notice(narrator.toppingRemoved(ing, remaining));
            dirty = true;
        }

        /** Otomatik iş adımı bitirdiyse onun (daha bilgili) duyurusu ekranda kalır. */
        private boolean autoJustFinished;

        @Override public void onToppingsFinished(Order o, int itemIndex, Set<String> chosen) {
            wantEmergency.clear();
            if (!autoJustFinished) notice(narrator.toppingsFinished(o, itemIndex, chosen));
            autoJustFinished = false;
            dirty = true;
        }

        // ---- v0.7 otomatik malzeme ve oyun akışı
        /**
         * Otomatik malzeme: HER malzeme kendi sesini sırayla (zamanlı) çalar; tek bir sos döngüsü değil. Domates = ıslak dilim,
         * marul/soğan = çıtır yaprak, sos = yumuşak; ekleme süresine yayılır, duraklatmada durur.
         */
        @Override public void onAutoToppingsStarted(Order o, int itemIndex, List<Ingredient> adding, float seconds) {
            List<SoundManager.Cue> cues = new ArrayList<>();
            for (ToppingSfx.Cue c : ToppingSfx.plan(adding, seconds)) {
                boolean sauce = ToppingSfx.SAUCE.equals(c.sfx);
                cues.add(new SoundManager.Cue(c.offsetMs, c.sfx, (sauce ? 0.9f : 1.0f) + (fx.nextFloat() - 0.5f) * 0.1f));
            }
            if (!cues.isEmpty()) a.sound.startSequence(autoKey(o, itemIndex), cues, panForStation(Station.SOS), 0.9f, seconds);
            a.haptics.stepStart();
            dirty = true;
        }

        @Override public void onToppingsAutoAdded(Order o, int itemIndex, List<Ingredient> added, List<Ingredient> removed, boolean finished) {
            a.sound.stopLoop(autoKey(o, itemIndex));
            a.sound.play("sfx_tap", panForStation(Station.SOS), 0.6f, 1.3f);
            if (!finished) a.haptics.warning();
            notice(narrator.toppingsAutoAdded(o, itemIndex, added, removed, finished));
            autoJustFinished = finished;
            dirty = true;
        }

        @Override public void onTimePressureChanged(boolean timed) {
            a.save.timePressure = timed;
            notice(narrator.timePressureChanged(timed));
            lastAnnounceBucket = Integer.MAX_VALUE;
            statusKey = "";
            dirty = true;
        }

        @Override public void onFlowModeChanged(FlowMode mode) {
            a.save.flowMode = mode;
            a.save.flowChosen = true;
            actionSig.clear(); // TalkBack eylem listeleri yeni akışa göre yeniden kurulsun
            notice(narrator.flowModeChanged(mode));
            dirty = true;
        }

        @Override public void onSizeChosen(Order o, int itemIndex, PortionSize ordered, PortionSize chosen) {
            a.haptics.tap();
            notice(narrator.sizeChosen(chosen));
            dirty = true;
        }

        @Override public void onStationInfo(Order o, int itemIndex, String optionId) {
            String t = narrator.stationInfo(o, itemIndex, optionId);
            a.sound.play("sfx_tap", 0f, 0.6f);
            notice(t);
            // Narrator "Sesli anlatım" kapalıyken susar; okuma istendiği için TalkBack/TTS yine de duyurulur.
            if (!a.save.speechOn) a.speaker.announce(t, true);
            dirty = true;
        }

        @Override public void onOptionRejected(Order o, String optionId, StartResult reason) {
            a.sound.play("sfx_error", 0f, 0.8f);
            if (reason == StartResult.LOCKED || reason == StartResult.NOTHING_TO_UNDO) a.haptics.warning(); else a.haptics.error();
            notice(narrator.optionRejected(o, optionId, reason));
            dirty = true;
        }

        @Override public void onOrderMistake(Order o, ToppingCheck check, double penalty) {
            a.sound.play("sfx_error");
            a.haptics.error();
            notice(narrator.orderMistake(o, check, penalty));
            dirty = true;
        }

        @Override public void onOrderPerfect(Order o) {
            a.sound.playLater("sfx_star_up", 0.3f, 350);
            a.haptics.starUp();
            notice(narrator.orderPerfect(o));
            dirty = true;
        }

        @Override public void onPrepStarted(PrepKind kind, boolean byApprentice, float seconds) {
            if (!byApprentice) {
                a.sound.startLoop(prepKey(kind), sfxForPrep(kind), panForStation(kind.station), 1f, seconds);
                a.haptics.stepStart();
            }
            dirty = true;
        }

        @Override public void onPrepFinished(PrepKind kind, boolean byApprentice, int amount) {
            if (!byApprentice) {
                a.sound.stopLoop(prepKey(kind));
                a.sound.play("sfx_step_done", panForStation(kind.station));
                a.haptics.stepDone();
                tutorial(2, Texts.TUT_PREP_DONE);
            }
            dirty = true;
        }

        @Override public void onPrepRejected(PrepKind kind, PrepResult reason, Ingredient ingredient) {
            a.sound.play("sfx_error");
            a.haptics.error();
            notice(narrator.prepRejected(kind, reason, ingredient));
            dirty = true;
        }

        @Override public void onApprenticeAction(ApprenticeAction act) {
            String key = apprenticeKey(act.worker);
            if (act.started) {
                a.sound.startLoop(key, act.prep == PrepKind.FRIES ? "sfx_fry" : sfxForStep(act.step), 0f, APPRENTICE_VOLUME, act.seconds);
            } else {
                a.sound.stopLoop(key);
                a.sound.play("sfx_step_done", 0f, APPRENTICE_VOLUME);
            }
            dirty = true;
        }

        @Override public void onOutOfStock(Order o, Ingredient ing) {
            a.sound.play("sfx_error");
            a.haptics.error();
            missingOrderId = o.id;
            dirty = true;
        }

        @Override public void onEmergencyPurchase(Ingredient ing, int qty, int cost) {
            a.sound.play("sfx_buy");
            a.haptics.buy();
            dirty = true; // düğme, eksik kalmayınca refresh'te kendiliğinden kapanır
        }

        @Override public void onNotEnoughMoney(int needed) {
            a.sound.play("sfx_error");
            a.haptics.error();
        }

        @Override public void onPatienceWarning(Order o, int level) {
            a.sound.play("sfx_warning", panForPosition(o));
            if (level >= 2) {
                a.sound.playLater("sfx_warning", panForPosition(o), 260);
                a.haptics.critical();
            } else {
                a.haptics.warning();
            }
            dirty = true;
        }

        @Override public void onCustomerLeft(Order o) {
            wantEmergency.clear();
            a.sound.play("sfx_customer_angry", 0.7f);
            a.haptics.warning();
            dirty = true;
        }

        @Override public void onOrderDelivered(Order o, int money, int tip, boolean happy) {
            a.sound.play("sfx_deliver", 0.3f);
            a.sound.playLater("sfx_coin", 0.3f, 160);
            a.sound.playLater(happy ? "sfx_customer_happy" : "sfx_customer_angry", 0.6f, 480);
            a.haptics.deliver();
            tutorial(TUTORIAL_END, Texts.tutEnd(engine.isTimed()));
            if (tutorialActive && tutorialStage >= TUTORIAL_END) {
                tutorialActive = false;
                gameSave.tutorialDone = true;
            }
            dirty = true;
        }

        @Override public void onStarsChanged(double oldS, double newS) {
            if (!starsFx.changed(oldS, newS)) return;
            if (newS > oldS) { a.sound.playLater("sfx_star_up", 0f, 650); a.haptics.starUp(); }
            else { a.sound.playLater("sfx_star_down", 0f, 650); a.haptics.starDown(); }
            dirty = true;
        }

        @Override public void onDayFinished(DaySummary s) {
            a.sound.stopAllLoops();
            a.sound.play("sfx_day_end");
            a.haptics.dayEnd();
            beginFinish(s);
        }

        @Override public void onDayFailed(DaySummary s) {
            a.sound.stopAllLoops();
            a.sound.play("sfx_day_end");
            a.haptics.dayEnd();
            beginFinish(s);
        }
    }

    // ================================================================== çalışanlar şeridi
    /** Çalışan işleri (oyuncunun hazırlığı, çırak işleri) gösterir: "Çırak: ayran dolduruyor". */
    private final class WorkersBox extends LinearLayout {
        private final List<TextView> labels = new ArrayList<>();
        private final List<BarView> bars = new ArrayList<>();
        private final List<ActiveStep> jobs = new ArrayList<>();
        private String key = "";

        WorkersBox() {
            super(ctx);
            setOrientation(VERTICAL);
        }

        void update() {
            List<ActiveStep> now = new ArrayList<>();
            for (ActiveStep s : engine.getActiveSteps()) if (s.isApprentice() || s.isPrep()) now.add(s);
            StringBuilder k = new StringBuilder();
            for (ActiveStep s : now) k.append(Texts.jobLabel(engine, s)).append('|');
            if (now.isEmpty() && engine.getApprenticeLevel() > 0) k.append("boşta");
            if (!k.toString().equals(key)) {
                key = k.toString();
                removeAllViews();
                labels.clear();
                bars.clear();
                if (now.isEmpty() && engine.getApprenticeLevel() > 0) {
                    addRow("Çırak: boşta", null);
                } else {
                    for (ActiveStep s : now) addRow(Texts.jobLabel(engine, s), s);
                }
            }
            jobs.clear();
            jobs.addAll(now);
            for (int i = 0; i < jobs.size() && i < bars.size(); i++) {
                BarView b = bars.get(i);
                if (b != null) b.set(jobs.get(i).progress(), jobs.get(i).isApprentice() ? Theme.GREEN : Theme.ORANGE);
            }
            setVisibility(getChildCount() == 0 ? View.GONE : View.VISIBLE);
        }

        private void addRow(String label, ActiveStep s) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setMinimumHeight(dp(48));
            row.setBackground(Ui.round(ctx, Theme.CARD, s != null && s.isApprentice() ? Theme.GREEN : Theme.STROKE, 1.5f, 14));
            TextView t = Ui.label(ctx, label, 16, Theme.CREAM, true);
            row.addView(t);
            BarView b = null;
            if (s != null) {
                b = new BarView(ctx, 6);
                row.addView(b, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 6), 0, 4, 0, 0));
            }
            Ui.groupAsText(row);
            row.setContentDescription(label + ".");
            addView(row, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 3, 0, 3));
            labels.add(t);
            bars.add(b);
        }
    }

    // ================================================================== hazırlık bölümü
    /** Hazır stok göstergeleri + hazırlık düğmeleri (hazırlık aşamasında ve gün içinde). */
    private final class PrepBox extends LinearLayout {
        private final TextView[] cells = new TextView[4];
        private final List<Button> doners = new ArrayList<>();
        private final List<Ingredient> meats = new ArrayList<>();
        private Button drinkBtn, friesBtn;
        Button trayBtn;
        private final Button readStockBtn;

        PrepBox() {
            super(ctx);
            setOrientation(VERTICAL);
            addView(sectionHeading("Hazırlık"));

            LinearLayout stockRow = new LinearLayout(ctx);
            stockRow.setOrientation(HORIZONTAL);
            for (int i = 0; i < 4; i++) {
                TextView c = Ui.label(ctx, "", 15, Theme.CREAM, true);
                c.setGravity(Gravity.CENTER);
                c.setFocusable(true);
                c.setMinHeight(dp(64));
                c.setPadding(dp(4), dp(6), dp(4), dp(6));
                c.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 14));
                cells[i] = c;
                stockRow.addView(c, Ui.lp(ctx, 0, Ui.WRAP, 1f, i == 0 ? 0 : 3, 0, i == 3 ? 0 : 3, 0));
            }
            addView(stockRow, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 4));

            if (!engine.getPrepMeats().isEmpty()) {
                for (final Ingredient m : engine.getPrepMeats()) {
                    Button b = Ui.button(ctx, "", Ui.SECONDARY);
                    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    final Ingredient meat = m;
                    final Button btn = b;
                    tap(b, new Runnable() { @Override public void run() { onPrep(Station.DONER, meat.id, btn, "Döner dilimle: " + meat.name); } });
                    attachMenu(b, new StationSource(Station.DONER, -1, meat.id), Station.DONER, false);
                    doners.add(b);
                    meats.add(m);
                    addView(b, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 3, 0, 3));
                }
            }
            if (engine.menuHasDrinkPrep()) {
                drinkBtn = Ui.button(ctx, "", Ui.SECONDARY);
                drinkBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tap(drinkBtn, new Runnable() { @Override public void run() { onPrep(Station.ICECEK, null, drinkBtn, "Ayran doldur"); } });
                attachMenu(drinkBtn, new StationSource(Station.ICECEK, -1, null), Station.ICECEK, false);
                addView(drinkBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 3, 0, 3));
            }
            if (engine.menuHasFries()) {
                friesBtn = Ui.button(ctx, "", Ui.SECONDARY);
                friesBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tap(friesBtn, new Runnable() { @Override public void run() { onPrep(Station.IZGARA, null, friesBtn, "Patates kızart"); } });
                attachMenu(friesBtn, new StationSource(Station.IZGARA, -1, null), Station.IZGARA, false);
                addView(friesBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 3, 0, 3));
            }
            trayBtn = Ui.button(ctx, "", Ui.SECONDARY);
            trayBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tap(trayBtn, new Runnable() { @Override public void run() { onPrep(Station.SOS, null, trayBtn, "Salata ve sos tepsisi"); } });
            attachMenu(trayBtn, new StationSource(Station.SOS, -1, null), Station.SOS, false);
            addView(trayBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 3, 0, 3));

            readStockBtn = Ui.button(ctx, "Hazır stoğu oku", Ui.SECONDARY);
            readStockBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            readStockBtn.setContentDescription("Hazır stoğu oku");
            tap(readStockBtn, new Runnable() {
                @Override public void run() {
                    String t = narrator.readyStockText();
                    a.speaker.force(t.isEmpty() ? "Hazır stok yok." : t);
                }
            });
            addView(readStockBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 3, 0, 3));
        }

        void update() {
            cells[0].setText("Hazır döner\n" + PortionSize.halvesText(engine.getReadyDonerHalves()));
            Ui.setDesc(cells[0], Texts.stockCellDescription(engine, PrepKind.DONER));
            if (engine.menuHasDrinkPrep()) {
                cells[1].setVisibility(View.VISIBLE);
                cells[1].setText("Hazır ayran\n" + engine.getReadyDrinks() + " / " + engine.getReadyCapacity(PrepKind.DRINK));
                Ui.setDesc(cells[1], Texts.stockCellDescription(engine, PrepKind.DRINK));
            } else {
                cells[1].setVisibility(View.GONE);
            }
            cells[2].setText("Hazır tepsi\n" + engine.getTrayTotal());
            Ui.setDesc(cells[2], Texts.stockCellDescription(engine, PrepKind.TRAY));
            if (friesBtn != null) {
                cells[3].setVisibility(View.VISIBLE);
                cells[3].setText("Hazır patates\n" + engine.getReadyFries() + " / " + engine.getReadyCapacity(PrepKind.FRIES));
                Ui.setDesc(cells[3], Texts.stockCellDescription(engine, PrepKind.FRIES));
                friesBtn.setText(Texts.prepButtonLabel(engine, PrepKind.FRIES, null));
                Ui.setDesc(friesBtn, Texts.prepButtonDescription(engine, PrepKind.FRIES, null));
                refreshActions(friesBtn);
            } else {
                cells[3].setVisibility(View.GONE);
            }
            for (int i = 0; i < doners.size(); i++) {
                Ingredient m = meats.get(i);
                Button b = doners.get(i);
                b.setText(Texts.prepButtonLabel(engine, PrepKind.DONER, m));
                Ui.setDesc(b, Texts.prepButtonDescription(engine, PrepKind.DONER, m));
                refreshActions(b);
            }
            if (drinkBtn != null) {
                drinkBtn.setText(Texts.prepButtonLabel(engine, PrepKind.DRINK, null));
                Ui.setDesc(drinkBtn, Texts.prepButtonDescription(engine, PrepKind.DRINK, null));
                refreshActions(drinkBtn);
            }
            trayBtn.setText(Texts.prepButtonLabel(engine, PrepKind.TRAY, null));
            Ui.setDesc(trayBtn, Texts.prepButtonDescription(engine, PrepKind.TRAY, null));
            refreshActions(trayBtn);
        }
    }

    // ================================================================== sipariş kartı
    /** Kalem satırı: kalem adı + adımları; dokununca kalem seçilir. */
    private final class ItemRow extends LinearLayout {
        final int index;
        private final TextView title, steps;
        private final BarView bar;
        private int bgKey = -1;

        ItemRow(int index) {
            super(ctx);
            this.index = index;
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(8), dp(12), dp(8));
            setMinimumHeight(dp(60));
            title = Ui.label(ctx, "", 17, Theme.CREAM, true);
            steps = Ui.label(ctx, "", 14, Theme.MUTED, false);
            bar = new BarView(ctx, 5);
            addView(title);
            addView(steps);
            addView(bar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 5), 0, 4, 0, 0));
            Ui.groupAsButton(this);
        }

        void update(final Order o, OrderLine l, boolean selItem) {
            title.setText((selItem ? "▶ " : "") + (index + 1) + ". " + l.displayName() + "  ·  " + l.price() + " TL" + (l.isDone() ? "  ✓ hazır" : ""));
            steps.setText(Texts.lineSteps(engine, o, l));
            float prog = l.isDone() ? 1f : 0f;
            if (!l.isDone()) {
                int total = l.stepCount();
                float sum = l.doneCount();
                for (ActiveStep s : engine.getActiveSteps()) if (s.order == o && s.itemIndex == l.index) sum += s.progress();
                prog = sum / Math.max(1, total);
            }
            bar.set(prog, l.isDone() ? Theme.GREEN : Theme.CREAM);
            int key = (selItem ? 1 : 0) | (l.isDone() ? 2 : 0) | (l.isRunning() ? 4 : 0);
            if (key != bgKey) {
                bgKey = key;
                setBackground(Ui.round(ctx, selItem ? Theme.CARD_HI : Theme.BG_MID,
                        l.isDone() ? Theme.GREEN : (selItem ? Theme.GOLD : (l.isRunning() ? Theme.ORANGE : Theme.STROKE)), selItem ? 3 : 1.5f, 12));
            }
            Ui.setDesc(this, Texts.lineDescription(engine, o, l, selItem));
        }
    }

    /** Ortak "Siparişi paketle" satırı. */
    private final class PackRow extends LinearLayout {
        private final TextView text;
        private int bgKey = -1;

        PackRow() {
            super(ctx);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(8), dp(12), dp(8));
            setMinimumHeight(dp(56));
            text = Ui.label(ctx, "", 16, Theme.CREAM, true);
            addView(text);
            Ui.groupAsButton(this);
        }

        void update(Order o) {
            text.setText(Texts.packText(o));
            int key = o.isReady() ? 3 : (o.isPacking() ? 2 : (o.canPack() ? 1 : 0));
            if (key != bgKey) {
                bgKey = key;
                setBackground(Ui.round(ctx, o.canPack() ? Theme.CARD_HI : Theme.BG_MID,
                        key >= 2 ? Theme.GREEN : (key == 1 ? Theme.GOLD : Theme.STROKE), key == 1 ? 3 : 1.5f, 12));
            }
            Ui.setDesc(this, Texts.packDescription(o));
        }
    }

    private final class OrderCard extends LinearLayout {
        final int orderId;
        private final LinearLayout header;
        private final TextView head, items, requests, status, patLabel;
        private final BarView patBar, stepBar;
        private final List<ItemRow> rows = new ArrayList<>();
        private final PackRow packRow;
        private int bgKey = -1;

        OrderCard(Order o) {
            super(ctx);
            orderId = o.id;
            setOrientation(VERTICAL);
            setPadding(dp(8), dp(8), dp(8), dp(8));

            header = new LinearLayout(ctx);
            header.setOrientation(VERTICAL);
            header.setPadding(dp(6), dp(4), dp(6), dp(6));
            header.setMinimumHeight(dp(64));
            head = Ui.label(ctx, "", 19, Theme.GOLD, true);
            items = Ui.label(ctx, "", 17, Theme.CREAM, false);
            requests = Ui.label(ctx, "", 16, Theme.GOLD, true);
            status = Ui.label(ctx, "", 16, Theme.CREAM, true);
            patLabel = Ui.label(ctx, "", 15, Theme.MUTED, false);
            patBar = new BarView(ctx, 8);
            stepBar = new BarView(ctx, 6);
            header.addView(head);
            header.addView(items);
            header.addView(requests);
            header.addView(status, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 4));
            header.addView(patLabel);
            header.addView(patBar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 8), 0, 3, 0, 5));
            header.addView(stepBar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 6), 0, 0, 0, 0));
            Ui.groupAsButton(header);
            tap(header, new Runnable() {
                @Override public void run() {
                    if (paused || finishing || panel != null) return;
                    engine.selectOrder(orderId);
                    dirty = true;
                }
            });
            addView(header);

            for (int i = 0; i < o.lineCount(); i++) {
                final int idx = i;
                ItemRow r = new ItemRow(i);
                tap(r, new Runnable() { @Override public void run() { onSelectItem(orderId, idx); } });
                rows.add(r);
                addView(r, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 4, 4, 0));
            }
            packRow = new PackRow();
            tap(packRow, new Runnable() { @Override public void run() { onPackRow(orderId); } });
            addView(packRow, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 4, 4, 4, 0));
        }

        void update(Order o, boolean sel) {
            ActiveStep act = engine.getActiveFor(o.id);
            float f = o.customer.patienceFraction();
            int level = Texts.patienceLevel(f);
            head.setText((sel ? "▶ " : "") + Texts.orderTitle(o));
            items.setText(o.itemsText() + "  ·  " + o.price() + " TL");
            if (o.hasRequests()) {
                requests.setVisibility(View.VISIBLE);
                requests.setText("İstek: " + o.requestsText());
            } else {
                requests.setVisibility(View.GONE);
            }
            status.setText(Texts.orderStatus(o, act, sel));
            boolean timedDay = engine.isTimed();
            patLabel.setVisibility(timedDay ? View.VISIBLE : View.GONE); // süresiz günde sabır göstergesi yok
            patBar.setVisibility(timedDay ? View.VISIBLE : View.GONE);
            if (timedDay) {
                String w = Texts.patienceWord(f);
                patLabel.setText(Character.toUpperCase(w.charAt(0)) + w.substring(1) + " · " + Math.max(0, Math.round(o.customer.getPatienceLeft())) + " sn");
                int col = level == 2 ? Theme.RED : (level == 1 ? Theme.ORANGE : Theme.GOLD);
                patBar.set(f, col);
            }
            float stepsDone = o.getNextStepIndex();
            for (ActiveStep s : engine.getActiveSteps()) if (s.order == o) stepsDone += s.progress();
            stepBar.set(stepsDone / Math.max(1, o.steps.size()), Theme.CREAM);
            setSelected(sel);
            int selItem = sel ? engine.getSelectedItem() : -1;
            for (int i = 0; i < rows.size() && i < o.lineCount(); i++) rows.get(i).update(o, o.getLine(i), i == selItem);
            packRow.update(o);
            if (!timedDay) level = 0;
            int key = (sel ? 1 : 0) | (level << 1) | (o.isReady() ? 8 : 0);
            if (key != bgKey) {
                bgKey = key;
                int stroke = sel ? Theme.GOLD : (level == 2 ? Theme.RED : Theme.STROKE);
                float sw = sel ? 3.5f : (level == 2 || o.isReady() ? 2.5f : 1.5f);
                setBackground(Ui.round(ctx, sel ? Theme.CARD_HI : Theme.CARD, o.isReady() ? Theme.GREEN : stroke, sw, 16));
            }
            Ui.setDesc(header, Texts.orderDescription(o, sel, act, timedDay));
        }
    }

    // ================================================================== istasyon düğmesi
    private final class StationTile extends LinearLayout {
        final Station st;
        private final IconView icon;
        private final TextView name, stat;
        private final BarView bar;
        private int bgKey = -1;

        StationTile(Station st) {
            super(ctx);
            this.st = st;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(8), dp(10), dp(8), dp(10));
            setMinimumHeight(dp(108));
            icon = new IconView(ctx, Icons.forStation(st), 48);
            name = Ui.label(ctx, st.displayName, 16, Theme.CREAM, true);
            name.setGravity(Gravity.CENTER);
            stat = Ui.label(ctx, "Boş", 14, Theme.MUTED, false);
            stat.setGravity(Gravity.CENTER);
            bar = new BarView(ctx, 6);
            addView(icon, new LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)));
            addView(name, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            addView(stat, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));
            addView(bar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 6), 4, 0, 4, 0));
            Ui.groupAsButton(this);
            final Station s = st;
            tap(this, new Runnable() { @Override public void run() { onStation(s); } });
            attachMenu(this, new StationSource(st, -2, null), st, true);
        }

        void update() {
            String vs = Texts.stationVisualStatus(engine, st);
            boolean busy = vs.equals("Çalışıyor");
            boolean needed = vs.equals("Şimdi bu");
            ActiveStep act = null;
            for (ActiveStep s : engine.getActiveSteps()) {
                if (s.step.station != st) continue;
                if (act == null || (act.isApprentice() && !s.isApprentice())) act = s;
            }
            stat.setText(needed ? "▶ Şimdi bu" : (busy ? (act != null && act.isApprentice() ? "Çırak çalışıyor" : "Çalışıyor") : "Boş"));
            stat.setTextColor(needed ? Theme.GOLD : Theme.MUTED);
            stat.setTypeface(needed || busy ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            bar.set(act == null ? 0f : act.progress(), act != null && act.isApprentice() ? Theme.GREEN : Theme.ORANGE);
            icon.setPhase(engine.getElapsed() * 3f);
            int key = (needed ? 1 : 0) | (busy ? 2 : 0);
            if (key != bgKey) {
                bgKey = key;
                setBackground(Ui.round(ctx, busy ? Theme.CARD_HI : Theme.CARD,
                        needed ? Theme.GOLD : (busy ? Theme.ORANGE : Theme.STROKE), needed ? 4 : 2, 16));
            }
            boolean used = inUse.contains(st);
            Ui.dim(this, !used);
            Ui.setDesc(this, Texts.stationDescription(narrator, engine, st, used));
            refreshActions(this);
        }
    }
}
