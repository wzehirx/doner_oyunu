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
import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.DayConfig;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.GameListenerAdapter;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.StartResult;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Oyun ekranı: canlı durum çubuğu, sipariş listesi, istasyon ızgarası, eylem düğmeleri, duraklatma paneli. */
public final class GameScreen extends Screen implements Choreographer.FrameCallback {

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
    private float startDelay = 1.6f;
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

    // ------------------------------------------------------------------ görünümler
    private FrameLayout host;
    private LinearLayout content;
    private TextView moneyTv, starsTv, timeTv, pendingTv;
    private StarsView starsView;
    private LinearLayout statusBar;
    private String statusKey = "";
    private int lastAnnounceBucket = Integer.MAX_VALUE;
    private LinearLayout cardsBox;
    private TextView emptyTv;
    private Button emergencyBtn;
    private final Map<Integer, OrderCard> cards = new LinkedHashMap<>();
    private final Map<Station, StationTile> tiles = new HashMap<>();
    private TextView slotsTv;
    private LinearLayout slotsCell;
    private Button deliverBtn, nextBtn, pauseBtn, readBtn;
    private FrameLayout overlay;
    private TextView overlayTitle;
    private Button quitBtn;

    public GameScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Gün " + a.save.day + ", oyun ekranı"; }

    @Override public String music() { return "music_day_calm"; }

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

        // Acil al: kaydırılan listenin DIŞINDA, durum çubuğunun hemen altında sabit; kartların altına düşmez.
        emergencyBtn = Ui.button(ctx, "", Ui.DANGER);
        emergencyBtn.setVisibility(View.GONE);
        tap(emergencyBtn, new Runnable() { @Override public void run() { onEmergency(); } });
        content.addView(emergencyBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = column();
        sv.addView(body, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));
        content.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        body.addView(sectionHeading("Siparişler"));
        emptyTv = Ui.label(ctx, "Henüz sipariş yok. Müşteri bekleniyor.", 17, Theme.MUTED, false);
        emptyTv.setFocusable(true);
        emptyTv.setPadding(dp(4), dp(6), dp(4), dp(6));
        body.addView(emptyTv);
        cardsBox = column();
        body.addView(cardsBox);

        body.addView(sectionHeading("İstasyonlar"));
        buildGrid(body);

        buildBottomBar();
        return host;
    }

    private LinearLayout statCell(Icons.Kind kind, TextView valueOut, String caption, View below) {
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
        statusBar.addView(statCell(Icons.Kind.CLOCK, timeTv, "Süre", null), new LinearLayout.LayoutParams(0, Ui.WRAP, 0.9f));
        statusBar.addView(statCell(Icons.Kind.BAG, pendingTv, "Bekleyen", null), new LinearLayout.LayoutParams(0, Ui.WRAP, 0.9f));
        Ui.groupAsText(statusBar);
        content.addView(statusBar, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));
    }

    private void buildGrid(LinearLayout body) {
        Station[] all = Station.values();
        LinearLayout row = null;
        for (int i = 0; i < all.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                body.addView(row, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
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
        nextBtn.setContentDescription("Sonraki siparişi seç");
        pauseBtn.setContentDescription("Duraklat. Oyunu durdurur.");
        readBtn.setContentDescription("Durumu oku. Para, yıldız, süre ve seçili sipariş.");
        row.addView(nextBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 3, 0));
        row.addView(pauseBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 3, 0, 3, 0));
        row.addView(readBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 3, 0, 0, 0));
        bar.addView(row);
        content.addView(bar);
    }

    // ================================================================== yaşam döngüsü
    @Override public void onEnter() {
        queueFrame();
        refresh(true);
    }

    @Override public void onExit() {
        exited = true;
        handler.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(this);
        frameQueued = false;
    }

    @Override public void onPause() {
        if (!finishing && !exited) pauseGame(true);
    }

    @Override public void onResume() {
        if (paused && overlay != null && overlayTitle != null) a.focusOn(overlayTitle);
    }

    @Override public boolean onBack() {
        if (finishing) { showSummary(); return true; } // gün bitti: beklemeden özete geç
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
            if (startDelay > 0f) startDelay -= dt;
            else engine.tick(dt);
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
        if (!quiet) a.haptics.tap();
        showOverlay(quiet);
    }

    private void resumeGame() {
        if (!paused) return;
        paused = false;
        if (overlay != null) { host.removeView(overlay); overlay = null; }
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        dirty = true;
        queueFrame();
        a.sound.play("sfx_tap");
        a.speaker.announce("Oyun devam ediyor.", true);
        a.focusOn(pauseBtn);
    }

    private void showOverlay(boolean quiet) {
        overlay = new FrameLayout(ctx);
        overlay.setBackgroundColor(0xE61A0E08);
        overlay.setClickable(true); // arkadaki oyuna dokunmayı engeller
        // Etiketsiz tıklanabilir perde TalkBack'te "etiketsiz" odak noktası olmasın: kendisi gizli, çocukları açık.
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout panel = column();
        panel.setBackground(Ui.round(ctx, Theme.CARD, Theme.GOLD, 3, 20));
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        overlayTitle = Ui.label(ctx, "Duraklatıldı", 30, Theme.GOLD, true);
        Ui.heading(overlayTitle);
        panel.addView(overlayTitle);
        TextView info = Ui.label(ctx, "Oyun durdu. Müşteriler beklemeye devam etmiyor.", 18, Theme.CREAM, false);
        info.setFocusable(true);
        info.setPadding(0, dp(6), 0, dp(10));
        panel.addView(info);

        panel.addView(button("Devam et", Ui.PRIMARY, new Runnable() {
            @Override public void run() { resumeGame(); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        panel.addView(button("Durumu oku", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(narrator.statusText()); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));

        final TextView help = Ui.label(ctx, Texts.quickHelp(), 17, Theme.CREAM, false);
        help.setFocusable(true);
        help.setVisibility(View.GONE);
        help.setPadding(dp(4), dp(6), dp(4), dp(6));
        panel.addView(button("Nasıl oynanır", Ui.SECONDARY, new Runnable() {
            @Override public void run() {
                boolean show = help.getVisibility() != View.VISIBLE;
                help.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) a.speaker.force(Texts.quickHelp());
            }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        panel.addView(help);

        quitBtn = button("Günü bırak, ana menü", Ui.DANGER, new Runnable() {
            @Override public void run() { onQuit(); }
        });
        panel.addView(quitBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
        plp.setMargins(dp(20), dp(20), dp(20), dp(20));
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(panel, plp);
        sv.addView(wrap, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
        overlay.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        host.addView(overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
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
            quitBtn.setContentDescription("Emin misin? Bugünün ilerlemesi kaybolur. Ana menüye dönmek için tekrar dokun.");
            a.speaker.announce("Bugünün ilerlemesi kaybolur. Çıkmak için düğmeye tekrar dokun.", true);
            return;
        }
        a.show(new MenuScreen(a));
    }

    // ================================================================== eylemler
    private void error(String msg) {
        a.sound.play("sfx_error");
        a.haptics.error();
        if (msg != null) a.speaker.say(msg, true);
    }

    private void onStation(Station st) {
        if (paused || finishing) return;
        if (startDelay > 0f) { a.speaker.say("Gün birazdan başlıyor.", true); return; }
        Order sel = engine.getSelectedOrder();
        if (sel == null || sel.isRunning() || sel.isReady()) {
            // seçili sipariş bu istasyonu beklemiyorsa, onu bekleyen başka bir siparişi seç
            Order alt = null;
            for (Order o : engine.getOrders()) {
                if (!o.isReady() && !o.isRunning() && o.nextStep() != null && o.nextStep().station == st) { alt = o; break; }
            }
            if (alt != null) { engine.selectOrder(alt.id); sel = alt; }
        }
        if (sel == null) {
            error(engine.getOrders().isEmpty() ? "Henüz sipariş yok." : "Önce bir sipariş seç.");
            return;
        }
        StartResult r = engine.startStep(sel.id, st.id);
        switch (r) {
            case NO_ORDER:
            case ORDER_BUSY:
            case ORDER_READY:
            case NOT_RUNNING:
                error(narrator.startResultText(r));
                break;
            default:
                break; // diğerlerini motor olay olarak bildirir
        }
        dirty = true;
    }

    private void onDeliver() {
        if (paused || finishing) return;
        Order target = engine.getSelectedOrder();
        if (target == null || !target.isReady()) {
            target = null;
            for (Order o : engine.getOrders()) if (o.isReady()) { target = o; break; }
        }
        if (target == null) { error("Teslime hazır sipariş yok."); return; }
        engine.deliver(target.id);
        dirty = true;
    }

    private void onNext() {
        if (paused || finishing) return;
        if (!engine.selectNextOrder()) error("Henüz sipariş yok.");
        dirty = true;
    }

    private void onEmergency() {
        if (paused || finishing || missingOrderId < 0) return;
        if (engine.emergencyBuyMissing(missingOrderId)) missingOrderId = -1;
        dirty = true;
    }

    /** Seçim yoksa kısa süre sonra kendiliğinden bir sipariş seçer; son 5 sn geri sayım tıkı. */
    private void updateAuto(float dt) {
        if (startDelay > 0f || engine.getState() != GameEngine.State.RUNNING) return;
        boolean allow = !tutorialActive || tutorialStage >= 2;
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
        if (!engine.isOvertime() && left <= 5f) {
            int sec = (int) Math.ceil(left);
            if (sec != lastTickSecond) { lastTickSecond = sec; a.sound.play("sfx_tick"); }
        }
    }

    // ================================================================== yenileme
    private void refresh(boolean force) {
        dirty = false;
        List<Order> orders = engine.getOrders();
        Order selected = engine.getSelectedOrder();

        // durum çubuğu
        moneyTv.setText(Texts.money(engine.getMoney()));
        starsTv.setText(Narrator.fmtStars(engine.getStars()));
        starsView.setValue(engine.getStars());
        timeTv.setText(engine.isOvertime() ? "Mesai" : Texts.clock(engine.getTimeLeft()));
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

        // istasyonlar
        for (Map.Entry<Station, StationTile> e : tiles.entrySet()) e.getValue().update();
        slotsTv.setText("Boş yuva\n" + engine.getFreeSlots() + " / " + engine.getSlotCount());
        Ui.setDesc(slotsCell, Texts.slotsText(engine));

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
        if (missingOrderId >= 0) {
            boolean still = selected != null && selected.id == missingOrderId && !selected.isRunning() && !selected.isReady();
            if (still) missing = engine.missingIngredients(missingOrderId);
            if (!still || missing.isEmpty()) { missingOrderId = -1; missing = new ArrayList<>(); }
        }
        if (missing.isEmpty()) {
            emergencyBtn.setVisibility(View.GONE);
            emergencyKey = "";
            return;
        }
        int cost = engine.emergencyCostForMissing(missingOrderId);
        String label = Texts.emergencyLabel(missing, cost);
        String desc = Texts.emergencyDescription(missing, cost);
        emergencyBtn.setVisibility(View.VISIBLE);
        emergencyBtn.setText(label);
        Ui.setDesc(emergencyBtn, desc);
        StringBuilder key = new StringBuilder().append(missingOrderId);
        for (Ingredient ing : missing) key.append('|').append(ing.id);
        if (!key.toString().equals(emergencyKey)) {
            emergencyKey = key.toString();
            // Belirdi (ya da eksik küme değişti): duyuru Narrator.onOutOfStock'ta yapıldı (acil, kesici);
            // TalkBack erişilebilirlik odağı da doğrudan düğmeye taşınır.
            a.focusOn(emergencyBtn);
        }
    }

    private void updateStatusDescription(int pending, Order selected) {
        // Düşük sıklıkta güncelle: yalnızca anlamlı değişimde (para/yıldız/bekleyen/seçili/10 sn dilimi).
        float left = engine.getTimeLeft();
        int bucket = engine.isOvertime() ? -1 : (int) (left / 10f);
        String key = engine.getMoney() + "|" + Narrator.fmtStars(engine.getStars()) + "|" + pending + "|"
                + (selected == null ? -1 : selected.id) + "|" + (selected == null ? 0 : selected.getNextStepIndex()) + "|" + bucket;
        if (key.equals(statusKey)) return;
        statusKey = key;
        // Canlı duyuru yalnızca süre eşiklerinde: 60, 30, 10 sn kala ve mesai başında.
        int threshold = engine.isOvertime() ? -1 : (left <= 10f ? 10 : left <= 30f ? 30 : left <= 60f ? 60 : Integer.MAX_VALUE);
        boolean announce = threshold != lastAnnounceBucket;
        lastAnnounceBucket = threshold;
        if (threshold == Integer.MAX_VALUE) announce = false;
        Ui.liveUpdate(statusBar, narrator.statusText(), announce);
    }

    private void updateMusic(int pending) {
        float elapsed = engine.getElapsed();
        boolean busy = pending >= 5 || engine.getTimeLeft() <= engine.getConfig().duration * 0.25f;
        if (busy) busyHold = elapsed + 12f;
        String want = (busy || elapsed < busyHold) ? "music_day_busy" : "music_day_calm";
        if (!want.equals(a.sound.currentMusic())) a.sound.playMusic(want);
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
    private void tutorial(int stage, String text) {
        if (!tutorialActive || stage <= tutorialStage) return;
        tutorialStage = stage;
        a.speaker.say("Öğretici. " + text, false);
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

    private static String sfxForStation(Station s) {
        switch (s) {
            case DONER: return "sfx_slice";
            case EKMEK: return "sfx_bread_warm";
            case SOS: return "sfx_sauce";
            case PAKET: return "sfx_wrap";
            case ICECEK: return "sfx_pour";
            default: return "sfx_grill_sizzle"; // ızgara ve tatlı/fırın
        }
    }

    /** Motor olaylarını ses + titreşim + arayüz yenilemeye çevirir (anlatım Narrator'dadır). */
    private final class Feedback extends GameListenerAdapter {
        private final Narrator.StarsTracker starsFx = new Narrator.StarsTracker();

        @Override public void onDayStarted(int day, DayConfig config) {
            starsFx.reset(engine.getStars());
            a.sound.play("sfx_day_start");
            a.haptics.dayStart();
            if (tutorialActive) {
                a.speaker.say("Öğretici açık. Müşteri gelince sipariş listesinde görünür. Sana adım adım yol göstereceğim.", false);
            }
            dirty = true;
        }

        @Override public void onCustomerArrived(Order o) {
            a.sound.play("sfx_customer_arrive", 0.7f);
            a.sound.playLater("sfx_order_new", panForPosition(o), 280);
            a.haptics.customer();
            tutorial(1, "Bir müşteri geldi. Siparişi seçmek için sipariş kartına çift dokun, ya da Sonraki sipariş düğmesini kullan.");
            dirty = true;
        }

        @Override public void onOrderSelected(Order o) {
            a.sound.play("sfx_tap", panForPosition(o));
            tutorial(2, "Şimdi sistemin söylediği istasyonun düğmesine dokun.");
            dirty = true;
        }

        @Override public void onNextStepHint(Order o, Step s) { dirty = true; }

        @Override public void onStepStarted(Order o, Step step, int slot, float seconds) {
            a.sound.play(sfxForStation(step.station), panForStation(step.station));
            a.haptics.stepStart();
            tutorial(3, "İşlem sürerken bekle. Bitince bir ses duyacaksın ve sıradaki adımı söyleyeceğim. Aynı şekilde devam et.");
            dirty = true;
        }

        @Override public void onStepFinished(Order o, Step step, boolean complete) {
            a.sound.play("sfx_step_done", panForPosition(o));
            a.haptics.stepDone();
            dirty = true;
        }

        @Override public void onOrderReady(Order o) {
            a.sound.playLater("sfx_step_done", panForPosition(o), 200);
            a.haptics.orderReady();
            tutorial(4, "Sipariş hazır. Teslim et düğmesine dokun.");
            dirty = true;
        }

        @Override public void onWrongStation(Order o, Station tapped, Station expected) {
            a.sound.play("sfx_error");
            a.haptics.error();
            dirty = true;
        }

        @Override public void onNoFreeSlot(Order o) {
            a.sound.play("sfx_error");
            a.haptics.error();
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
            a.sound.play("sfx_customer_angry", 0.7f);
            a.haptics.warning();
            dirty = true;
        }

        @Override public void onOrderDelivered(Order o, int money, int tip, boolean happy) {
            a.sound.play("sfx_deliver", 0.3f);
            a.sound.playLater("sfx_coin", 0.3f, 160);
            a.sound.playLater(happy ? "sfx_customer_happy" : "sfx_customer_angry", 0.6f, 480);
            a.haptics.deliver();
            tutorial(5, "Harika, ilk siparişini teslim ettin! Sabır azalınca uyarı sesi gelir. Durumu oku düğmesiyle istediğin an bilgi alabilirsin. Öğretici bitti.");
            if (tutorialActive && tutorialStage >= 5) {
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
            a.sound.play("sfx_day_end");
            a.haptics.dayEnd();
            beginFinish(s);
        }

        @Override public void onDayFailed(DaySummary s) {
            a.sound.play("sfx_day_end");
            a.haptics.dayEnd();
            beginFinish(s);
        }
    }

    // ================================================================== sipariş kartı
    private final class OrderCard extends LinearLayout {
        final int orderId;
        private final TextView head, items, status, patLabel;
        private final BarView patBar, stepBar;
        private int bgKey = -1;

        OrderCard(Order o) {
            super(ctx);
            orderId = o.id;
            setOrientation(VERTICAL);
            setPadding(dp(14), dp(10), dp(14), dp(10));
            head = Ui.label(ctx, "", 19, Theme.GOLD, true);
            items = Ui.label(ctx, "", 17, Theme.CREAM, false);
            status = Ui.label(ctx, "", 16, Theme.CREAM, true);
            patLabel = Ui.label(ctx, "", 15, Theme.MUTED, false);
            patBar = new BarView(ctx, 8);
            stepBar = new BarView(ctx, 6);
            addView(head);
            addView(items);
            addView(status, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 4));
            addView(patLabel);
            addView(patBar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 8), 0, 3, 0, 5));
            addView(stepBar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 6), 0, 0, 0, 0));
            Ui.groupAsButton(this);
            setMinimumHeight(dp(64));
            tap(this, new Runnable() {
                @Override public void run() {
                    if (paused || finishing) return;
                    engine.selectOrder(orderId);
                    dirty = true;
                }
            });
        }

        void update(Order o, boolean sel) {
            ActiveStep act = engine.getActiveFor(o.id);
            float f = o.customer.patienceFraction();
            int level = Texts.patienceLevel(f);
            head.setText((sel ? "▶ " : "") + Texts.orderTitle(o));
            items.setText(o.itemsText() + "  ·  " + o.price() + " TL");
            status.setText(Texts.orderStatus(o, act, sel));
            String w = Texts.patienceWord(f);
            patLabel.setText(Character.toUpperCase(w.charAt(0)) + w.substring(1) + " · " + Math.max(0, Math.round(o.customer.getPatienceLeft())) + " sn");
            int col = level == 2 ? Theme.RED : (level == 1 ? Theme.ORANGE : Theme.GOLD);
            patBar.set(f, col);
            float stepsDone = o.getNextStepIndex() + (act != null ? act.progress() : 0f);
            stepBar.set(stepsDone / Math.max(1, o.steps.size()), Theme.CREAM);
            setSelected(sel);
            int key = (sel ? 1 : 0) | (level << 1) | (o.isReady() ? 8 : 0);
            if (key != bgKey) {
                bgKey = key;
                int stroke = sel ? Theme.GOLD : (level == 2 ? Theme.RED : Theme.STROKE);
                float sw = sel ? 3.5f : (level == 2 || o.isReady() ? 2.5f : 1.5f);
                setBackground(Ui.round(ctx, sel ? Theme.CARD_HI : Theme.CARD, o.isReady() ? Theme.GREEN : stroke, sw, 16));
            }
            Ui.setDesc(this, Texts.orderDescription(o, sel, act));
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
        }

        void update() {
            String vs = Texts.stationVisualStatus(engine, st);
            boolean busy = vs.equals("Çalışıyor");
            boolean needed = vs.equals("Şimdi bu");
            ActiveStep act = null;
            for (ActiveStep s : engine.getActiveSteps()) if (s.step.station == st) { act = s; break; }
            stat.setText(needed ? "▶ Şimdi bu" : (busy ? "Çalışıyor" : "Boş"));
            stat.setTextColor(needed ? Theme.GOLD : Theme.MUTED);
            stat.setTypeface(needed || busy ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            bar.set(act == null ? 0f : act.progress(), Theme.ORANGE);
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
        }
    }
}
