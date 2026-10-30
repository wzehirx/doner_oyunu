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
import com.doner.oyunu.engine.ReadyAction;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Oyun ekranı (v2.0 sade düzen). Önce HAZIRLIK aşaması (müşteri yok, süre akmaz), sonra gün.
 * <p>Gün düzeni (yukarıdan aşağı, TalkBack sırası): durum çubuğu (para, yıldız, süre / kalan müşteri, bekleyen),
 * eller satırı ("Elin boş" / "Elin dolu: …", pişen makineler), son olay, acil al; kaydırılan gövdede çırak satırı,
 * <b>Sıradaki sipariş</b> (tek sipariş: müşteri, kalemlerin tek satırlık özeti, sabır; altında yalnızca O siparişin
 * "şimdi yapılabilecekler" satırları, motorun {@code getReadyActions}), diğer siparişlerin TEK satırlık özeti (dokununca
 * sipariş listesi), bugünün istasyonları (kısa doluluk etiketi, TalkBack Eylemleri + seçenek paneli); en altta Teslim et,
 * Sonraki sipariş, Duraklat, Durumu oku.
 * <p>Kararlılık: görünümler bir kez kurulur; her yenilemede yalnızca metin/etiket yerinde güncellenir (değişince). Eylem
 * satırları sabit bir havuzdur ({@link #MAX_ACTIONS}); odaktaki satır gizlenecekse odak ilk satıra/başlığa taşınır.
 */
public final class GameScreen extends Screen implements Choreographer.FrameCallback {

    /** Öğretici son aşama numarası (ilk teslimden sonra biter). */
    static final int TUTORIAL_END = 10;
    /** "Şimdi yapılabilecekler" satır havuzu (sabit; satırlar yeniden kullanılır). */
    static final int MAX_ACTIONS = 6;
    /** Çalışanlar kutusunun satır havuzu. */
    private static final int MAX_WORKER_ROWS = 6;
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
    private boolean tutPassiveShown;
    /** Hazırlık düzeni (gün başlamadı) mı gösteriliyor. */
    private boolean prepUi = true;
    private boolean prepBegun;
    /** Açık istasyon seçenek paneli (yoksa null). */
    private MenuPanel panel;
    /** Panel kapanınca odağın döneceği düğme. */
    private View panelOrigin;
    /** Açık sipariş listesi (yoksa null). */
    private OrdersPanel ordersPanel;
    /** Stokta olmadığı için eklenemeyen malzemeler (acil al düğmesi bunları da sunar). */
    private final Set<String> wantEmergency = new LinkedHashSet<>();
    private boolean tutSizeShown;
    private final Random fx = new Random();
    /** Bir eylem satırından iş yapıldı: sonraki yenilemede odak ilk satıra (yoksa sipariş başlığına) döner. */
    private int focusRequest;
    private static final int FOCUS_NONE = 0, FOCUS_FIRST_ACTION = 1, FOCUS_HEADER = 2;
    /**
     * TalkBack erişilebilirlik odağının en son bulunduğu "Sıradaki sipariş" bölümü görünümü (başlık, eylem satırı, sıra
     * özeti); odak bölüm dışına geçtiyse null. Erişilebilirlik olaylarından izlenir (odak yerleşim geçişlerinde de bilinir).
     */
    private View lastA11yFocus;
    private boolean focusSeenInGroup;
    /** Pasif istasyon döngüleri (istasyon + ses başına tek): şu an çalan anahtarlar. */
    private final Set<String> stationLoops = new HashSet<>();

    // ------------------------------------------------------------------ görünümler
    private FrameLayout host;
    private LinearLayout content;
    private LinearLayout body;
    private TextView moneyTv, starsTv, timeTv, pendingTv, timeCaption;
    private StarsView starsView;
    private LinearLayout statusBar;
    private String statusKey = "";
    private int lastAnnounceBucket = Integer.MAX_VALUE;
    private TextView handsTv;
    private TextView noticeTv;
    private WorkersBox workersBox;
    private PrepBox prepBox;
    private LinearLayout focusGroup, stationsGroup, grid;
    private TextView focusHeading;
    private FocusHeader focusHeader;
    private final ActionRow[] actionRows = new ActionRow[MAX_ACTIONS];
    private QueueRow queueRow;
    private Button emergencyBtn;
    private final Map<Station, StationTile> tiles = new HashMap<>();
    private String gridKey = "";
    private Button deliverBtn, nextBtn, pauseBtn, readBtn, openBtn, skipBtn;
    private LinearLayout openBar;
    private FrameLayout overlay;
    private TextView overlayTitle;
    private Button quitBtn;

    public GameScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Gün " + a.save.day + ", oyun ekranı"; }

    /** Yalnızca testler: sabit tohum (null = her oyunda rastgele). Testlerin deterministik olması için. */
    static Long testSeed;

    @Override public String music() { return "music_day_calm"; }

    /** Dükkân ambiyansı: gün ekranında (hazırlık ve gün) düşük sesle çalar; dosya yoksa sessiz. */
    @Override public String ambience() { return SoundManager.AMBIENCE_SHOP; }

    // ================================================================== oluşturma
    @Override public View create() {
        gameSave = a.save.copy();
        engine = new GameEngine(testSeed != null ? testSeed : System.nanoTime(), gameSave);
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

        // v2.0: eller ve pişen makineler TEK yerde (eski ortak iş göstergesinin yerine)
        handsTv = Ui.label(ctx, "", 16, Theme.CREAM, true);
        handsTv.setFocusable(true);
        handsTv.setPadding(dp(10), dp(6), dp(10), dp(6));
        handsTv.setMinHeight(dp(48));
        handsTv.setGravity(Gravity.CENTER_VERTICAL);
        handsTv.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 14));
        content.addView(handsTv, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));

        // Son önemli olay (hatalı sipariş, kilitli adım, reddedilen seçim...): görsel + okunabilir.
        noticeTv = Ui.label(ctx, "", 16, Theme.GOLD, true);
        noticeTv.setFocusable(true);
        noticeTv.setVisibility(View.GONE);
        noticeTv.setPadding(dp(6), dp(2), dp(6), dp(4));
        content.addView(noticeTv);

        // Acil al: kaydırılan listenin DIŞINDA, durum çubuğunun hemen altında sabit.
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

        focusGroup = column();
        focusHeading = sectionHeading("Sıradaki sipariş");
        focusGroup.addView(focusHeading);
        focusHeader = new FocusHeader();
        tap(focusHeader, new Runnable() { @Override public void run() { onFocusHeader(); } });
        focusGroup.addView(focusHeader, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 4));
        for (int i = 0; i < MAX_ACTIONS; i++) {
            final int idx = i;
            ActionRow r = new ActionRow();
            tap(r, new Runnable() { @Override public void run() { onActionRow(idx); } });
            actionRows[i] = r;
            focusGroup.addView(r, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 8, 3, 0, 3));
        }
        queueRow = new QueueRow();
        tap(queueRow, new Runnable() { @Override public void run() { openOrdersPanel(); } });
        focusGroup.addView(queueRow, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 2));
        body.addView(focusGroup);
        trackAccessibilityFocus();

        stationsGroup = column();
        stationsGroup.addView(sectionHeading("İstasyonlar"));
        grid = column();
        for (Station st : Station.values()) {
            StationTile t = new StationTile(st);
            tiles.put(st, t);
        }
        stationsGroup.addView(grid);
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

    /** Görünür istasyonları ikişerli satırlara dizer (yalnızca görünür küme değişince; düğmeler yeniden kullanılır). */
    private void layoutGrid(List<Station> visible) {
        StringBuilder k = new StringBuilder();
        for (Station st : visible) k.append(st.name()).append('|');
        if (k.toString().equals(gridKey)) return;
        gridKey = k.toString();
        for (StationTile t : tiles.values()) {
            if (t.getParent() instanceof ViewGroup) ((ViewGroup) t.getParent()).removeView(t);
        }
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < visible.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
            }
            boolean last = i == visible.size() - 1 && i % 2 == 0;
            row.addView(tiles.get(visible.get(i)), Ui.lp(ctx, 0, Ui.WRAP, 1f, i % 2 == 0 ? 0 : 5, 0, i % 2 == 0 && !last ? 5 : 0, 0));
            if (last) { // tek kalan düğme yarım genişlikte kalsın
                View spacer = new View(ctx);
                Ui.decorative(spacer);
                row.addView(spacer, Ui.lp(ctx, 0, 1, 1f, 5, 0, 0, 0));
            }
        }
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
        focusGroup.setVisibility(prep ? View.GONE : View.VISIBLE);
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
        stationLoops.clear();
    }

    @Override public void onPause() {
        if (!finishing && !exited) pauseGame(true);
    }

    @Override public void onResume() {
        if (paused && overlay != null && overlayTitle != null) a.focusOn(overlayTitle);
    }

    @Override public boolean onBack() {
        if (finishing) { showSummary(); return true; } // gün bitti: beklemeden özete geç
        if (ordersPanel != null && !paused) { closeOrdersPanel(true); a.speaker.announce("Sipariş listesi kapatıldı.", true); return true; }
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
        if (ordersPanel != null) closeOrdersPanel(false);
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
        boolean hide = paused || panel != null || ordersPanel != null;
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
        if (panel != null || ordersPanel != null) return;
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
        if (focusBack && !paused && back != null) {
            // panel bir eylem satırından açıldıysa odak, satırın yeni içeriğine göre ilk satıra/başlığa döner
            if (back instanceof ActionRow) focusRequest = FOCUS_FIRST_ACTION;
            else a.focusOn(back);
        }
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
            StationSource src = p.source instanceof StationSource ? (StationSource) p.source : null;
            StartResult r = applyOption(o, src == null ? -1 : src.item, st, op.id);
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

    /** Bir istasyonun (ve isteğe bağlı sipariş / kalem / et filtresinin) seçenek kaynağı; panel ve TalkBack eylemleri kullanır. */
    private final class StationSource implements MenuPanel.Source, ActionMenu.Source {
        final Station st;
        /** -2: her seferinde en uygun siparişi seç (düğme eylemleri); -1: yalnızca hazırlık; >=0: sabit sipariş. */
        final int fixedOrder;
        /** Sabit kalem (eylem satırından açıldı); -1: otomatik (seçili kalem, sonra ilk uygun). */
        final int item;
        final String meatFilter;

        StationSource(Station st, int fixedOrder, String meatFilter) { this(st, fixedOrder, -1, meatFilter); }

        StationSource(Station st, int fixedOrder, int item, String meatFilter) {
            this.st = st;
            this.fixedOrder = fixedOrder;
            this.item = item;
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
            List<StationOption> all = engine.getStationOptions(o == null ? -1 : o.id, o == null ? -1 : item, st.id);
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
            int li = engine.resolveStationItem(o.id, item, st.id);
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
        if (blocked()) return;
        if (engine.getState() != GameEngine.State.RUNNING) { a.speaker.say("Önce dükkânı aç.", true); return; }
        a.sound.play("sfx_tap");
        applyOption(pickOrderFor(st), -1, st, optionId);
    }

    private void onPrepAction(Station st, String optionId) {
        if (blocked()) return;
        if (!prepBegun) beginPrepNow();
        a.sound.play("sfx_tap");
        applyOption(null, -1, st, optionId);
    }

    /** Duraklatma, gün sonu ya da açık bir panel eylemleri engeller. */
    private boolean blocked() { return paused || finishing || panel != null || ordersPanel != null; }

    private void ensureSelected(Order t) {
        Order sel = engine.getSelectedOrder();
        if (sel == null || sel.id != t.id) engine.selectOrder(t.id);
    }

    /**
     * Seçeneği motora uygular (TEK yol: düğme dokunuşu, eylem satırı, TalkBack eylemi ve panel hep buradan geçer). Sonucun
     * duyurusunu motor olaylarıyla Narrator yapar; olay vermeyen sonuçlar burada sesle bildirilir.
     */
    private StartResult applyOption(Order t, int item, Station st, String optionId) {
        if (t != null) ensureSelected(t);
        StartResult r = engine.applyStationOption(t == null ? -1 : t.id, t == null ? -1 : item, st.id, optionId);
        switch (r) {
            case OUT_OF_STOCK:
                if (optionId.startsWith("add:")) wantEmergency.add(optionId.substring(4));
                break;
            case STEP_LOCKED:
                a.sound.play("sfx_error", 0f, 0.7f);
                a.haptics.warning();
                break;
            case NO_ORDER:
            case ORDER_READY:
            case NOT_RUNNING:
            case INVALID:
                if (optionId.equals("step") || optionId.startsWith("size:")) error(narrator.startResultText(r));
                break;
            default:
                break; // HANDS_BUSY / STATION_FULL / ORDER_BUSY: motor olayı (onHandsBusy…) ses + duyuru verir
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

    private void openMenu(String title, Station st, Order target, int item, View origin, String meatFilter) {
        int oid = target == null ? -1 : target.id;
        final StationSource src = new StationSource(st, oid, item, meatFilter);
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

    /** İstasyona dokunulunca hangi sipariş: önce sıradaki (odak) sipariş, sonra uygun adımı olan diğerleri; yoksa kilitli adımı olan. */
    private Order pickOrderFor(Station st) {
        Order sel = engine.getFocusOrder();
        List<Order> cand = new ArrayList<>();
        if (sel != null) cand.add(sel);
        for (Order o : engine.getOrders()) if (sel == null || o.id != sel.id) cand.add(o);
        for (Order o : cand) if (!o.isReady() && hasOpenStepAt(o, st)) return o;
        for (Order o : cand) if (hasLockedStepAt(o, st)) return o;
        return sel;
    }

    /** İstasyon düğmesine dokunuldu (TalkBack kapalıyken tek, açıkken varsayılan çift dokunuş). */
    private void onStation(Station st) {
        if (blocked()) return;
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
            applyOption(target, -1, st, "step"); // istasyonda iş yok: motor yanlış istasyonu bildirir
            return;
        }
        StationOption direct = directOption(ops);
        if (direct != null) { applyOption(target, -1, st, direct.id); return; }
        if (target != null) ensureSelected(target);
        openMenu(st.displayName + ": seçenekler", st, target, -1, tiles.get(st), null);
    }

    /** Sıradaki sipariş başlığına dokunuldu: seçili değilse seçer (motor duyurur), seçiliyse ayrıntıyı okur. */
    private void onFocusHeader() {
        if (blocked()) return;
        Order o = engine.getFocusOrder();
        if (engine.getState() != GameEngine.State.RUNNING || o == null) { a.speaker.force("Bekleyen sipariş yok."); return; }
        Order sel = engine.getSelectedOrder();
        if (sel == null || sel.id != o.id) engine.selectOrder(o.id);
        else a.speaker.force(narrator.focusText());
        dirty = true;
    }

    /** "Şimdi yapılabilecekler" satırına dokunuldu: tek dokunuşla uygula, menü gerekiyorsa istasyon panelini aç. */
    private void onActionRow(int idx) {
        if (blocked()) return;
        ActionRow row = actionRows[idx];
        ReadyAction act = row.action;
        Order o = act == null ? null : engine.getOrder(act.orderId);
        if (act == null || o == null || engine.getState() != GameEngine.State.RUNNING) { dirty = true; return; }
        switch (act.kind) {
            case DELIVER:
                ensureSelected(o);
                engine.deliver(o.id);
                wantEmergency.clear();
                focusRequest = FOCUS_HEADER;
                dirty = true;
                return;
            default:
                break;
        }
        if (act.status == ReadyAction.Status.RUNNING) {
            a.speaker.force(act.text + ".");
            return;
        }
        if (act.optionId != null) {
            applyOption(o, act.itemIndex, act.station, act.optionId);
            focusRequest = FOCUS_FIRST_ACTION;
            return;
        }
        // menü gerekli (boy seçimi, tek tek malzeme) ya da kaynak bekleniyor: istasyon menüsü o kalem için açılır
        ensureSelected(o);
        List<StationOption> ops = engine.getStationOptions(o.id, act.itemIndex, act.station.id);
        if (ops.isEmpty()) { applyOption(o, act.itemIndex, act.station, "step"); return; }
        if (!act.needsMenu) {
            // tek adımlı iş ama şu an kaynak yok (elin dolu / istasyon dolu): uygula ki motor nedenini duyursun (ses + titreşim)
            StationOption single = null;
            int steps = 0;
            for (StationOption op : ops) if (op.group != StationOption.Group.PREP) { steps++; if (op.group == StationOption.Group.STEP) single = op; }
            if (steps == 1 && single != null) { applyOption(o, act.itemIndex, act.station, single.id); return; }
        }
        openMenu(act.station.displayName + ": seçenekler", act.station, o, act.itemIndex, row, null);
    }

    private void onDeliver() {
        if (blocked()) return;
        Order target = engine.getFocusOrder();
        if (target == null || !target.isReady()) {
            target = null;
            for (Order o : engine.getOrders()) if (o.isReady()) { target = o; break; }
        }
        if (target == null) { error("Teslime hazır sipariş yok."); return; }
        engine.deliver(target.id);
        wantEmergency.clear();
        dirty = true;
    }

    /** "Sonraki sipariş": önerilen siparişe geçer (teslime hazır > yapılabilir işi olan en acil); zaten oysa sıradakine. */
    private void onNext() {
        if (blocked()) return;
        Order sug = engine.getSuggestedOrder();
        Order sel = engine.getSelectedOrder();
        boolean ok;
        if (sug != null && (sel == null || sel.id != sug.id)) ok = engine.selectSuggestedOrder();
        else ok = engine.selectNextOrder();
        if (!ok) error("Henüz sipariş yok.");
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
        if (blocked()) return;
        if (!prepUi) return;
        if (!engine.startDay()) return;
        prepBegun = true;
        applyPhase(false);
        refresh(true);
        a.focusOn(focusHeading);
    }

    /** Hazırlık düğmesine dokunuldu: tek seçenek doğrudan uygulanır, birden çoksa panel açılır. */
    private void onPrep(Station st, String meatFilter, View origin, String title) {
        if (blocked()) return;
        if (!prepBegun) beginPrepNow();
        List<StationOption> ops = new StationSource(st, -1, meatFilter).options();
        if (ops.isEmpty()) { error("Bu hazırlık şu an yapılamaz."); return; }
        if (ops.size() == 1) { applyOption(null, -1, st, ops.get(0).id); return; }
        openMenu(title, st, null, -1, origin, meatFilter);
    }

    /** Seçim yoksa kısa süre sonra önerilen siparişi ({@code getSuggestedOrder}) kendiliğinden seçer; son 5 sn geri sayım tıkı. */
    private void updateAuto(float dt) {
        if (engine.getState() != GameEngine.State.RUNNING) return;
        boolean allow = !tutorialActive || tutorialStage >= 5;
        if (engine.getSelectedOrderId() < 0 && !engine.getOrders().isEmpty() && allow && panel == null && ordersPanel == null) {
            noSelTimer += dt;
            if (noSelTimer >= 1.5f) {
                noSelTimer = 0f;
                Order pick = engine.getSuggestedOrder();
                if (pick != null) engine.selectOrder(pick.id);
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

    // ================================================================== sipariş listesi
    private void openOrdersPanel() {
        if (blocked()) return;
        Order focus = engine.getFocusOrder();
        int others = 0;
        for (Order o : engine.getOrders()) if (focus == null || o.id != focus.id) others++;
        if (engine.getState() != GameEngine.State.RUNNING || others == 0) {
            a.speaker.force(engine.getOrders().isEmpty() ? "Bekleyen sipariş yok." : "Sırada başka sipariş yok.");
            return;
        }
        ordersPanel = new OrdersPanel();
        host.addView(ordersPanel.overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        updateContentHidden();
        a.focusOn(ordersPanel.titleView);
    }

    private void closeOrdersPanel(boolean focusBack) {
        OrdersPanel p = ordersPanel;
        if (p == null) return;
        host.removeView(p.overlay);
        ordersPanel = null;
        updateContentHidden();
        dirty = true;
        if (focusBack && !paused) focusRequest = FOCUS_HEADER;
    }

    /**
     * Tüm siparişler (en acil önce), her biri TEK düğme: dokununca seçilir ve liste kapanır, odak "Sıradaki sipariş"
     * başlığına döner. Ana ekranı kalabalıklaştırmadan istenen siparişe geçmenin yolu.
     */
    private final class OrdersPanel {
        final FrameLayout overlay;
        final TextView titleView;
        private final LinearLayout list;
        private final Map<Integer, Button> buttons = new HashMap<>();
        private String key = "";

        OrdersPanel() {
            overlay = new FrameLayout(ctx);
            overlay.setBackgroundColor(0xF01A0E08);
            overlay.setClickable(true);
            overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout pnl = column();
            pnl.setBackground(Ui.round(ctx, Theme.CARD, Theme.GOLD, 3, 20));
            pnl.setPadding(dp(16), dp(16), dp(16), dp(16));
            titleView = Ui.label(ctx, "Siparişler", 24, Theme.GOLD, true);
            Ui.heading(titleView);
            pnl.addView(titleView);
            TextView info = Ui.label(ctx, engine.isTimed() ? "En acil önce. Dokunduğun sipariş sıradaki olur." : "Dokunduğun sipariş sıradaki olur.", 17, Theme.CREAM, false);
            info.setFocusable(true);
            info.setPadding(0, dp(6), 0, dp(6));
            pnl.addView(info);
            list = column();
            pnl.addView(list);
            Button close = Ui.button(ctx, "Kapat", Ui.SECONDARY);
            close.setContentDescription("Kapat");
            tap(close, new Runnable() {
                @Override public void run() {
                    if (ordersPanel == OrdersPanel.this && !paused) {
                        closeOrdersPanel(true);
                        a.speaker.announce("Sipariş listesi kapatıldı.", true);
                    }
                }
            });
            pnl.addView(close, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            ScrollView sv = new ScrollView(ctx);
            sv.setFillViewport(true);
            FrameLayout wrap = new FrameLayout(ctx);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
            plp.setMargins(dp(14), dp(14), dp(14), dp(14));
            wrap.addView(pnl, plp);
            sv.addView(wrap, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
            overlay.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            refresh();
        }

        private List<Order> sorted() {
            List<Order> l = new ArrayList<>(engine.getOrders());
            final boolean timed = engine.isTimed();
            java.util.Collections.sort(l, new java.util.Comparator<Order>() {
                @Override public int compare(Order x, Order y) {
                    if (x.isReady() != y.isReady()) return x.isReady() ? -1 : 1;
                    if (timed) {
                        int c = Float.compare(x.customer.patienceFraction(), y.customer.patienceFraction());
                        if (c != 0) return c;
                    }
                    return Integer.compare(x.getPosition(), y.getPosition());
                }
            });
            return l;
        }

        /** Sipariş kümesi değişince listeyi kurar; değilse etiketleri yerinde günceller. */
        void refresh() {
            List<Order> l = sorted();
            StringBuilder k = new StringBuilder();
            for (Order o : l) k.append(o.id).append('|');
            if (!k.toString().equals(key)) {
                key = k.toString();
                list.removeAllViews();
                buttons.clear();
                for (final Order o : l) {
                    Button b = Ui.button(ctx, "", Ui.SECONDARY);
                    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    b.setMinHeight(dp(64));
                    b.setMinimumHeight(dp(64));
                    final int id = o.id;
                    tap(b, new Runnable() { @Override public void run() { pick(id); } });
                    buttons.put(o.id, b);
                    list.addView(b, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
                }
            }
            Order sel = engine.getSelectedOrder();
            for (Order o : l) {
                Button b = buttons.get(o.id);
                if (b == null) continue;
                boolean isSel = sel != null && sel.id == o.id;
                String label = Texts.orderDescription(o, isSel, engine.getActiveFor(o.id), engine.isTimed());
                String text = Texts.focusTitle(o) + "\n" + o.itemsText() + (engine.isTimed() && !o.isReady() ? "\n" + Texts.patienceVisual(o) : (o.isReady() ? "\nTeslime hazır" : ""));
                if (!text.contentEquals(b.getText())) b.setText(text);
                Ui.setDesc(b, label);
            }
        }

        private void pick(int id) {
            if (ordersPanel != this || paused || finishing) return;
            boolean ok = engine.selectOrder(id); // motor duyurur: seçildi + önerilen
            closeOrdersPanel(true);
            if (!ok) a.speaker.announce("Bu sipariş artık yok.", true);
        }

        /** Testler: sipariş düğmesi. */
        Button button(int orderId) { return buttons.get(orderId); }
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

        // eller (tek yer)
        String hv = Texts.handsVisual(engine);
        if (!hv.contentEquals(handsTv.getText())) handsTv.setText(hv);
        Ui.setDesc(handsTv, Texts.handsLabel(engine));

        // sıradaki sipariş + şimdi yapılabilecekler + diğerlerinin özeti
        if (!prepUi) updateFocus(orders);

        // acil alım
        updateEmergency(selected);

        // açık seçenek paneli: listeyi tazele (malzeme eklerken / eller boşalınca durum değişir); sipariş gittiyse kapat
        if (panel != null) {
            if (panel.orderId >= 0 && engine.getOrder(panel.orderId) == null) {
                closePanel(true);
                a.speaker.announce("Sipariş artık yok, seçenekler kapandı.", true);
            } else {
                panel.refresh();
                if (panel.optionCount() == 0) closePanel(true);
            }
        }
        if (ordersPanel != null) ordersPanel.refresh();

        // çalışanlar + hazırlık bölümü
        workersBox.update();
        prepBox.update();

        // istasyonlar
        if (!prepUi) updateStations(orders);
        syncStationLoops();

        // teslim
        boolean anyReady = false;
        for (Order o : orders) if (o.isReady()) anyReady = true;
        Ui.setDesc(deliverBtn, Texts.deliverDescription(engine));
        String dt = anyReady ? "Teslim et  (hazır)" : "Teslim et";
        if (!dt.contentEquals(deliverBtn.getText())) deliverBtn.setText(dt);
        Ui.dim(deliverBtn, !anyReady);

        updateMusic(orders.size());
        applyFocusRequest();
    }

    /** Sıradaki sipariş bölümü: başlık, eylem satırları (havuz), sıra özeti. Görünümler yeniden yaratılmaz. */
    private void updateFocus(List<Order> orders) {
        Order focus = engine.getFocusOrder();
        boolean timed = engine.isTimed();
        focusHeader.update(focus, timed);
        List<ReadyAction> acts = focus == null ? new ArrayList<ReadyAction>() : engine.getReadyActions(focus.id);
        int shown = Math.min(acts.size(), MAX_ACTIONS);
        boolean rescue = false;
        for (int i = 0; i < MAX_ACTIONS; i++) {
            ActionRow r = actionRows[i];
            if (i < shown) {
                r.update(acts.get(i), i == MAX_ACTIONS - 1 && acts.size() > MAX_ACTIONS ? acts.size() - MAX_ACTIONS : 0);
                if (r.getVisibility() != View.VISIBLE) r.setVisibility(View.VISIBLE);
            } else if (r.getVisibility() != View.GONE) {
                if (r.isAccessibilityFocused() || lastA11yFocus == r) rescue = true;
                r.action = null;
                r.setVisibility(View.GONE);
            }
        }
        // diğer siparişler: tek satır
        int others = 0, readyOthers = 0;
        Order urgent = null;
        for (Order o : orders) {
            if (focus != null && o.id == focus.id) continue;
            others++;
            if (o.isReady()) readyOthers++;
            if (urgent == null || (timed ? o.customer.patienceFraction() < urgent.customer.patienceFraction() : o.getPosition() < urgent.getPosition())) urgent = o;
        }
        queueRow.update(others, readyOthers, urgent, timed);
        if (rescue && focusRequest == FOCUS_NONE) focusRequest = FOCUS_FIRST_ACTION;
    }

    /** Bir eylemden sonra odağı mantıklı ve kalıcı yere taşır (zaten oradaysa dokunmaz: yeniden okutmaz). */
    private void applyFocusRequest() {
        int req = focusRequest;
        if (req == FOCUS_NONE || paused || panel != null || ordersPanel != null || finishing) return;
        focusRequest = FOCUS_NONE;
        if (prepUi) return;
        View target = focusHeader;
        if (req == FOCUS_FIRST_ACTION && actionRows[0].getVisibility() == View.VISIBLE) target = actionRows[0];
        if (!target.isAccessibilityFocused() && lastA11yFocus != target) a.focusOn(target);
    }

    /**
     * Erişilebilirlik odağını izler: bölüm içindeki görünüm odak alınca kaydedilir, ekranın başka bir yeri odak alınca
     * silinir. Gizlenecek satır odaktaysa odak kurtarılır; işlem sonrası odak zaten hedefteyse yeniden gönderilmez.
     */
    private void trackAccessibilityFocus() {
        focusGroup.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public boolean onRequestSendAccessibilityEvent(ViewGroup g, View child, android.view.accessibility.AccessibilityEvent ev) {
                if (ev.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    lastA11yFocus = child;
                    focusSeenInGroup = true;
                }
                return super.onRequestSendAccessibilityEvent(g, child, ev);
            }
        });
        host.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public boolean onRequestSendAccessibilityEvent(ViewGroup g, View child, android.view.accessibility.AccessibilityEvent ev) {
                if (ev.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    if (!focusSeenInGroup) lastA11yFocus = null;
                    focusSeenInGroup = false;
                }
                return super.onRequestSendAccessibilityEvent(g, child, ev);
            }
        });
    }

    /** İstasyon düğmeleri: bugünün menüsündekiler + bir siparişin ya da işin kullandığı (senaryo) istasyonlar. */
    private void updateStations(List<Order> orders) {
        Order focus = engine.getFocusOrder();
        List<Station> visible = new ArrayList<>();
        for (Station st : Station.values()) {
            boolean show = inUse.contains(st) || !engine.getStationJobs(st).isEmpty();
            if (!show) for (Order o : orders) if (usesStation(o, st)) { show = true; break; }
            if (show) visible.add(st);
        }
        layoutGrid(visible);
        for (Station st : visible) tiles.get(st).update(Texts.needsStation(focus, st));
    }

    private static boolean usesStation(Order o, Station st) {
        for (OrderLine l : o.getLines()) for (int i = 0; i < l.stepCount(); i++) if (l.step(i).station == st) return true;
        return false;
    }

    /**
     * Pasif istasyon sesleri (ekmek ısıtıcı, ocak): iş başına değil İSTASYON + ses başına tek döngü; iş sürdükçe uzar, istasyon
     * boşalınca durur. Aynı ısıtıcıda iki ekmek tek döngü; ocakta patates ve mühürleme ayrı; farklı istasyonlar ayrı döngü.
     * Oyuncu başka işe geçse de (eller boş) döngü sürer.
     */
    private void syncStationLoops() {
        Set<String> want = new HashSet<>();
        for (Station st : Station.values()) {
            if (!engine.isPassiveStation(st)) continue;
            Map<String, Float> bySound = new HashMap<>();
            for (ActiveStep job : engine.getStationJobs(st)) {
                if (!job.isPassive()) continue;
                String sfx = job.isPrep() ? sfxForPrep(job.prep) : sfxForStep(job.step);
                Float cur = bySound.get(sfx);
                float rem = Math.max(0.05f, job.getRemaining());
                if (cur == null || rem > cur) bySound.put(sfx, rem);
            }
            for (Map.Entry<String, Float> e : bySound.entrySet()) {
                String key = stationLoopKey(st, e.getKey());
                want.add(key);
                a.sound.holdLoop(key, e.getKey(), panForStation(st), 0.9f, e.getValue() + 0.1f);
            }
        }
        for (Iterator<String> it = stationLoops.iterator(); it.hasNext(); ) {
            String k = it.next();
            if (!want.contains(k)) { a.sound.stopLoop(k); it.remove(); }
        }
        stationLoops.addAll(want);
    }

    static String stationLoopKey(Station st, String sfx) { return "st:" + st.name() + ":" + sfx; }

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
        if (!label.contentEquals(emergencyBtn.getText())) emergencyBtn.setText(label);
        Ui.setDesc(emergencyBtn, desc);
        if (panel != null) panel.setEmergency(label, desc);
        StringBuilder key = new StringBuilder().append(missingOrderId);
        for (Ingredient ing : missing) key.append('|').append(ing.id);
        if (!key.toString().equals(emergencyKey)) {
            emergencyKey = key.toString();
            // Belirdi (ya da eksik küme değişti): duyuru Narrator'da yapıldı (acil, kesici);
            // TalkBack erişilebilirlik odağı da doğrudan düğmeye taşınır (panel açıksa panelde kalır).
            if (panel == null && ordersPanel == null && !paused) { focusRequest = FOCUS_NONE; a.focusOn(emergencyBtn); }
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

    // ================================================================== testler için (paket içi)
    FocusHeaderAccess focusAccess() { return new FocusHeaderAccess(); }

    /** Testlerin ekran yapısına erişimi (yansımasız). */
    final class FocusHeaderAccess {
        View header() { return focusHeader; }
        View heading() { return focusHeading; }
        View queue() { return queueRow; }
        View hands() { return handsTv; }
        View actionRow(int i) { return actionRows[i]; }
        ReadyAction action(int i) { return actionRows[i].getVisibility() == View.VISIBLE ? actionRows[i].action : null; }
        int visibleActions() {
            int n = 0;
            for (ActionRow r : actionRows) if (r.getVisibility() == View.VISIBLE) n++;
            return n;
        }
        View tile(Station st) { return tiles.get(st); }
        boolean ordersPanelOpen() { return ordersPanel != null; }
        Button orderButton(int orderId) { return ordersPanel == null ? null : ordersPanel.button(orderId); }
        Set<String> stationLoopKeys() { return new HashSet<>(stationLoops); }
        /** Erişilebilirlik odağının izlenen yeri (bölüm dışında ise null). */
        View a11yFocus() { return lastA11yFocus; }
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
        stationLoops.clear();
        if (panel != null) closePanel(false);
        if (ordersPanel != null) closeOrdersPanel(false);
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

    /** Kutu içecek adımı ("Dolaptan al"): dolap sesiyle başlar, kutu açma sesiyle biter. */
    static boolean isCanStep(Step s) { return s != null && s.station == Station.ICECEK && s.needs.containsKey("kutu_icecek"); }

    /**
     * Adımın istasyon sesi: kızartma adımı sfx_fry (döngü), içecekler v2.0 varyant havuzu (ayran doldurma: ayran dökme
     * varyantları art arda; dolaptan al: dolap varyantları), diğerleri istasyonun sesi.
     */
    static String sfxForStep(Step s) {
        if (s != null && s.isFry()) return "sfx_fry";
        if (isCanStep(s)) return "sfx_drink_fridge";
        return sfxForStation(s == null ? Station.IZGARA : s.station);
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
            case ICECEK: return "sfx_drink_ayran";
            default: return "sfx_grill_sizzle"; // ocak ve tatlı
        }
    }

    /** İçecek işinin bitiş sesi: kutu açma (kutu içecek) ya da bardak (ayran); içecek değilse null. */
    static String drinkFinishSfx(Step s) {
        if (s == null || s.station != Station.ICECEK) return null;
        return isCanStep(s) ? "sfx_drink_can" : "sfx_drink_glass";
    }

    /** Siparişte içecek var mı (teslimde bardak sesi). */
    static boolean hasDrink(Order o) {
        for (OrderLine l : o.getLines()) for (int i = 0; i < l.stepCount(); i++) if (l.step(i).station == Station.ICECEK) return true;
        return false;
    }

    /** Çırak sesi: kısık (%60) ve pan merkez. */
    private static final float APPRENTICE_VOLUME = 0.6f;

    private static String playerKey(Order o, Step s) { return "p:" + o.id + ":" + s.name; }

    private static String prepKey(PrepKind k) { return "prep:" + k.name(); }

    private static String apprenticeKey(int worker) { return "a:" + worker; }

    private static String autoKey(Order o, int itemIndex) { return "auto:" + o.id + ":" + itemIndex; }

    /** Pasif iş mi (ses döngüsünü istasyon eşitlemesi kurar). */
    private boolean passiveStep(Step s) {
        return s != null && s.isPassive() && engine.isPassiveStation(s.station);
    }

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
            // pasif iş (ısıtıcı, ocak): ses istasyon döngüsünde (syncStationLoops); aktif iş: iş başına döngü
            if (!passiveStep(step)) a.sound.startLoop(playerKey(o, step), sfxForStep(step), panForStation(step.station), 1f, seconds);
            a.haptics.stepStart();
            tutorial(6, Texts.TUT_STEP);
            dirty = true;
        }

        @Override public void onStepFinished(Order o, Step step, boolean complete) {
            a.sound.stopLoop(playerKey(o, step));
            String drink = drinkFinishSfx(step);
            if (drink != null) a.sound.play(drink, panForStation(Station.ICECEK), 0.9f);
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

        // ---- v2.0 kaynak modeli: eller ve makineler (eski onNoFreeSlot artık gelmez)
        @Override public void onHandsBusy(Order o, ActiveStep current) {
            a.sound.play("sfx_error");
            a.haptics.error();
            notice(narrator.handsBusy(o, current));
            dirty = true;
        }

        @Override public void onStationFull(Order o, Station station, int used, int capacity) {
            a.sound.play("sfx_error", panForStation(station));
            a.haptics.error();
            notice(narrator.stationFull(o, station, used, capacity));
            dirty = true;
        }

        @Override public void onAlreadyRunning(Order o, ActiveStep job) {
            a.sound.play("sfx_warning", 0f, 0.6f);
            a.haptics.warning();
            notice(narrator.alreadyRunning(o, job));
            dirty = true;
        }

        /** Pasif işi koyma bitti: eller serbest. Hafif tık + titreşim; menüler/eylem satırları yenilenir. */
        @Override public void onHandsFree(ActiveStep job) {
            a.sound.play("sfx_tick", panForStation(job.getStation()), 0.5f);
            a.haptics.tap();
            actionSig.clear(); // TalkBack eylem listeleri ("Elin dolu" kilitleri kalktı) yeniden kurulsun
            if (tutorialActive && !tutPassiveShown) {
                tutPassiveShown = true;
                a.speaker.say("Öğretici. " + Texts.TUT_PASSIVE, false, Narrator.CAT_TUTORIAL, narrator.freshSinceNow());
            }
            dirty = true;
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
            if (!byApprentice && kind != PrepKind.FRIES) { // patates hazırlığı pasiftir: ocak döngüsü
                a.sound.startLoop(prepKey(kind), sfxForPrep(kind), panForStation(kind.station), 1f, seconds);
            }
            if (!byApprentice) a.haptics.stepStart();
            dirty = true;
        }

        @Override public void onPrepFinished(PrepKind kind, boolean byApprentice, int amount) {
            if (!byApprentice) {
                a.sound.stopLoop(prepKey(kind));
                if (kind == PrepKind.DRINK) a.sound.play("sfx_drink_glass", panForStation(Station.ICECEK), 0.8f);
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
            boolean passive = act.prep == PrepKind.FRIES || (act.prep == null && passiveStep(act.step));
            if (act.started) {
                // pasif işi koyup geçer: sesi istasyon döngüsü verir; çırağın eli için ses kurulmaz
                if (!passive) {
                    String sfx = act.prep != null ? sfxForPrep(act.prep) : sfxForStep(act.step);
                    a.sound.startLoop(key, sfx, 0f, APPRENTICE_VOLUME, act.seconds);
                }
            } else {
                // pasif iş bitişi çırağın ŞU ANKİ işinin sesini kesmesin
                if (!passive) a.sound.stopLoop(key);
                String drink = act.prep == PrepKind.DRINK ? "sfx_drink_glass" : (act.prep == null ? drinkFinishSfx(act.step) : null);
                if (drink != null) a.sound.play(drink, 0f, APPRENTICE_VOLUME);
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
            if (hasDrink(o)) a.sound.playLater("sfx_drink_glass", 0.3f, 90); // bardak/kutu tezgâha konur
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
            stationLoops.clear();
            a.sound.play("sfx_day_end");
            a.haptics.dayEnd();
            beginFinish(s);
        }

        @Override public void onDayFailed(DaySummary s) {
            a.sound.stopAllLoops();
            stationLoops.clear();
            a.sound.play("sfx_day_end");
            a.haptics.dayEnd();
            beginFinish(s);
        }
    }

    // ================================================================== çalışanlar
    /**
     * Çalışanlar: TEK odak noktası (satırlar ayrı odak değil). Hazırlıkta oyuncunun ve çırağın hazırlık işleri ("Sen: döner
     * dilimleme", "Çırak: ayran dolduruyor"); gün içinde yalnızca çırak(lar)ın elindeki iş ya da "boşta" (oyuncunun işi eller
     * satırında, pişen işler istasyon düğmelerinde). Satır görünümleri sabit havuzdur.
     */
    private final class WorkersBox extends LinearLayout {
        private final TextView[] labels = new TextView[MAX_WORKER_ROWS];
        private final BarView[] bars = new BarView[MAX_WORKER_ROWS];

        WorkersBox() {
            super(ctx);
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(6), dp(12), dp(6));
            setMinimumHeight(dp(48));
            setBackground(Ui.round(ctx, Theme.CARD, Theme.GREEN, 1.5f, 14));
            for (int i = 0; i < MAX_WORKER_ROWS; i++) {
                labels[i] = Ui.label(ctx, "", 16, Theme.CREAM, true);
                bars[i] = new BarView(ctx, 5);
                addView(labels[i], Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, i == 0 ? 0 : 4, 0, 0));
                addView(bars[i], Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 5), 0, 2, 0, 0));
            }
            Ui.groupAsText(this);
        }

        void update() {
            List<String> rows = new ArrayList<>();
            List<ActiveStep> jobs = new ArrayList<>();
            int level = engine.getApprenticeLevel();
            if (prepUi) {
                for (ActiveStep s : engine.getActiveSteps()) if (s.isPrep() || s.isApprentice()) { rows.add(Texts.jobLabel(engine, s)); jobs.add(s); }
                if (rows.isEmpty() && level > 0) { rows.add("Çırak: boşta"); jobs.add(null); }
            } else {
                for (int w = 1; w <= level; w++) {
                    ActiveStep hands = null;
                    for (ActiveStep s : engine.getActiveSteps()) if (s.worker == w && s.holdsHands()) hands = s;
                    String who = level > 1 ? "Çırak " + w : "Çırak";
                    rows.add(hands == null ? who + ": boşta" : Texts.jobLabel(engine, hands));
                    jobs.add(hands);
                }
            }
            int n = Math.min(rows.size(), MAX_WORKER_ROWS);
            StringBuilder desc = new StringBuilder();
            for (int i = 0; i < MAX_WORKER_ROWS; i++) {
                boolean on = i < n;
                labels[i].setVisibility(on ? View.VISIBLE : View.GONE);
                ActiveStep j = on ? jobs.get(i) : null;
                bars[i].setVisibility(j != null ? View.VISIBLE : View.GONE);
                if (!on) continue;
                if (!rows.get(i).contentEquals(labels[i].getText())) labels[i].setText(rows.get(i));
                if (j != null) bars[i].set(j.progress(), j.isApprentice() ? Theme.GREEN : Theme.ORANGE);
                if (desc.length() > 0) desc.append(". ");
                desc.append(rows.get(i));
            }
            setVisibility(n == 0 ? View.GONE : View.VISIBLE);
            if (n > 0) Ui.setDesc(this, desc.append('.').toString());
        }
    }

    // ================================================================== hazırlık bölümü
    /** Hazır stok göstergeleri + hazırlık düğmeleri (yalnızca hazırlık aşamasında). */
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
            if (!prepUi) return; // gün içinde ekranda değil
            setText(cells[0], "Hazır döner\n" + PortionSize.halvesText(engine.getReadyDonerHalves()));
            Ui.setDesc(cells[0], Texts.stockCellDescription(engine, PrepKind.DONER));
            if (engine.menuHasDrinkPrep()) {
                cells[1].setVisibility(View.VISIBLE);
                setText(cells[1], "Hazır ayran\n" + engine.getReadyDrinks() + " / " + engine.getReadyCapacity(PrepKind.DRINK));
                Ui.setDesc(cells[1], Texts.stockCellDescription(engine, PrepKind.DRINK));
            } else {
                cells[1].setVisibility(View.GONE);
            }
            setText(cells[2], "Hazır tepsi\n" + engine.getTrayTotal());
            Ui.setDesc(cells[2], Texts.stockCellDescription(engine, PrepKind.TRAY));
            if (friesBtn != null) {
                cells[3].setVisibility(View.VISIBLE);
                setText(cells[3], "Hazır patates\n" + engine.getReadyFries() + " / " + engine.getReadyCapacity(PrepKind.FRIES));
                Ui.setDesc(cells[3], Texts.stockCellDescription(engine, PrepKind.FRIES));
                setText(friesBtn, Texts.prepButtonLabel(engine, PrepKind.FRIES, null));
                Ui.setDesc(friesBtn, Texts.prepButtonDescription(engine, PrepKind.FRIES, null));
                refreshActions(friesBtn);
            } else {
                cells[3].setVisibility(View.GONE);
            }
            for (int i = 0; i < doners.size(); i++) {
                Ingredient m = meats.get(i);
                Button b = doners.get(i);
                setText(b, Texts.prepButtonLabel(engine, PrepKind.DONER, m));
                Ui.setDesc(b, Texts.prepButtonDescription(engine, PrepKind.DONER, m));
                refreshActions(b);
            }
            if (drinkBtn != null) {
                setText(drinkBtn, Texts.prepButtonLabel(engine, PrepKind.DRINK, null));
                Ui.setDesc(drinkBtn, Texts.prepButtonDescription(engine, PrepKind.DRINK, null));
                refreshActions(drinkBtn);
            }
            setText(trayBtn, Texts.prepButtonLabel(engine, PrepKind.TRAY, null));
            Ui.setDesc(trayBtn, Texts.prepButtonDescription(engine, PrepKind.TRAY, null));
            refreshActions(trayBtn);
        }
    }

    private static void setText(TextView t, String s) {
        if (!s.contentEquals(t.getText())) t.setText(s);
    }

    // ================================================================== sıradaki sipariş
    /**
     * Sıradaki siparişin başlığı (tek düğme): "Sipariş 2 · Ali", kalemlerin tek satırlık özeti, istek, eklenenler, sabır
     * göstergesi. Etiket kısa ve saniyesizdir (sabır kelimeyle); dokununca seçer ya da ayrıntıyı okur.
     */
    private final class FocusHeader extends LinearLayout {
        private final TextView head, items, requests, added, patLabel;
        private final BarView patBar;
        private int bgKey = -1;

        FocusHeader() {
            super(ctx);
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(10), dp(12), dp(10));
            setMinimumHeight(dp(64));
            head = Ui.label(ctx, "", 20, Theme.GOLD, true);
            items = Ui.label(ctx, "", 17, Theme.CREAM, false);
            requests = Ui.label(ctx, "", 16, Theme.GOLD, true);
            added = Ui.label(ctx, "", 15, Theme.CREAM, false);
            patLabel = Ui.label(ctx, "", 15, Theme.MUTED, false);
            patBar = new BarView(ctx, 8);
            addView(head);
            addView(items);
            addView(requests);
            addView(added);
            addView(patLabel, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            addView(patBar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 8), 0, 3, 0, 0));
            Ui.groupAsButton(this);
        }

        void update(Order o, boolean timed) {
            if (o == null) {
                setText(head, "Bekleyen sipariş yok");
                setText(items, "Müşteri bekleniyor.");
                requests.setVisibility(GONE);
                added.setVisibility(GONE);
                patLabel.setVisibility(GONE);
                patBar.setVisibility(GONE);
                Ui.setDesc(this, "Bekleyen sipariş yok, müşteri bekleniyor");
                background(0);
                return;
            }
            setText(head, Texts.focusTitle(o));
            setText(items, Texts.focusItems(o));
            if (o.hasRequests()) { requests.setVisibility(VISIBLE); setText(requests, "İstek: " + o.requestsText()); }
            else requests.setVisibility(GONE);
            String ad = Texts.focusAdded(o);
            if (ad.isEmpty()) added.setVisibility(GONE); else { added.setVisibility(VISIBLE); setText(added, ad); }
            int level = 0;
            boolean showPat = timed && !o.isReady();
            patLabel.setVisibility(showPat ? VISIBLE : GONE);
            patBar.setVisibility(showPat ? VISIBLE : GONE);
            if (showPat) {
                float f = o.customer.patienceFraction();
                level = Texts.patienceLevel(f);
                setText(patLabel, Texts.patienceVisual(o));
                patBar.set(f, level == 2 ? Theme.RED : (level == 1 ? Theme.ORANGE : Theme.GOLD));
            }
            Ui.setDesc(this, Texts.orderDescription(o, false, engine.getActiveFor(o.id), timed));
            background(1 | (level << 1) | (o.isReady() ? 8 : 0));
        }

        private void background(int key) {
            if (key == bgKey) return;
            bgKey = key;
            int level = (key >> 1) & 3;
            boolean ready = (key & 8) != 0;
            int stroke = ready ? Theme.GREEN : (level == 2 ? Theme.RED : (key == 0 ? Theme.STROKE : Theme.GOLD));
            setBackground(Ui.round(ctx, key == 0 ? Theme.CARD : Theme.CARD_HI, stroke, level == 2 || ready ? 3.5f : 2.5f, 16));
        }
    }

    /** "Şimdi yapılabilecekler" satırı (havuzdan; ReadyAction değişince yerinde güncellenir). */
    final class ActionRow extends LinearLayout {
        ReadyAction action;
        private final TextView text;
        private final BarView bar;
        private int bgKey = -1;

        ActionRow() {
            super(ctx);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(10), dp(12), dp(10));
            setMinimumHeight(dp(60));
            text = Ui.label(ctx, "", 17, Theme.CREAM, true);
            bar = new BarView(ctx, 5);
            addView(text);
            addView(bar, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 5), 0, 4, 0, 0));
            setVisibility(GONE);
            Ui.groupAsButton(this);
        }

        void update(ReadyAction act, int more) {
            action = act;
            String label = Texts.actionLabel(engine, act);
            String vis = act.kind == ReadyAction.Kind.DELIVER ? "✓ Teslim et"
                    : (act.isDoable() ? "▶ " : "") + act.text + (more > 0 ? "  (+" + more + " iş daha)" : "");
            setText(text, vis);
            Ui.setDesc(this, label);
            boolean running = act.status == ReadyAction.Status.RUNNING;
            bar.setVisibility(running ? VISIBLE : GONE);
            if (running) {
                ActiveStep job = null;
                for (ActiveStep s : engine.getActiveSteps()) {
                    if (s.order != null && s.order.id == act.orderId && s.itemIndex == act.itemIndex && s.step.name.equals(act.stepName)) job = s;
                }
                bar.set(job == null ? 0f : job.progress(), act.byApprentice ? Theme.GREEN : Theme.ORANGE);
            }
            int key = act.kind == ReadyAction.Kind.DELIVER ? 3 : act.isDoable() ? 1 : running ? 2 : 0;
            text.setTextColor(key == 0 || key == 2 ? Theme.MUTED : Theme.CREAM);
            if (key != bgKey) {
                bgKey = key;
                int stroke = key == 3 ? Theme.GREEN : key == 1 ? Theme.GOLD : key == 2 ? Theme.ORANGE : Theme.STROKE;
                setBackground(Ui.round(ctx, key == 1 || key == 3 ? Theme.CARD_HI : Theme.BG_MID, stroke, key == 1 || key == 3 ? 3 : 1.5f, 14));
            }
        }
    }

    /** Diğer siparişlerin TEK satırlık özeti; dokununca sipariş listesi açılır. En acil kritikse kırmızı çerçeve + metin. */
    private final class QueueRow extends LinearLayout {
        private final TextView text;
        private int bgKey = -1;

        QueueRow() {
            super(ctx);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(10), dp(12), dp(10));
            setMinimumHeight(dp(56));
            text = Ui.label(ctx, "", 16, Theme.CREAM, true);
            addView(text);
            Ui.groupAsButton(this);
        }

        void update(int others, int readyOthers, Order urgent, boolean timed) {
            setText(text, Texts.queueVisual(others, readyOthers, urgent, timed));
            Ui.setDesc(this, Texts.queueLabel(others, readyOthers, urgent, timed));
            int level = timed && urgent != null && !urgent.isReady() ? Texts.patienceLevel(urgent.customer.patienceFraction()) : 0;
            int key = others == 0 ? 0 : (readyOthers > 0 ? 4 : 1 + level);
            if (key != bgKey) {
                bgKey = key;
                int stroke = key == 3 ? Theme.RED : key == 4 ? Theme.GREEN : key == 2 ? Theme.ORANGE : Theme.STROKE;
                setBackground(Ui.round(ctx, Theme.CARD, stroke, key >= 2 ? 3f : 1.5f, 14));
            }
            Ui.dim(this, others == 0);
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

        void update(boolean needed) {
            int cap = engine.getStationCapacity(st);
            List<ActiveStep> jobs = engine.getStationJobs(st);
            boolean busy = !jobs.isEmpty();
            boolean full = cap > 0 && engine.getStationUsed(st) >= cap;
            ActiveStep act = null;
            for (ActiveStep s : jobs) if (act == null || s.getRemaining() < act.getRemaining()) act = s;
            setText(stat, Texts.stationVisual(engine, st, needed));
            stat.setTextColor(needed ? Theme.GOLD : Theme.MUTED);
            stat.setTypeface(needed || busy ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            bar.set(act == null ? 0f : act.progress(), act != null && act.isApprentice() ? Theme.GREEN : Theme.ORANGE);
            icon.setPhase(engine.getElapsed() * 3f);
            int key = (needed ? 1 : 0) | (busy ? 2 : 0) | (full ? 4 : 0);
            if (key != bgKey) {
                bgKey = key;
                setBackground(Ui.round(ctx, busy ? Theme.CARD_HI : Theme.CARD,
                        needed ? Theme.GOLD : (full ? Theme.RED : (busy ? Theme.ORANGE : Theme.STROKE)), needed || full ? 4 : 2, 16));
            }
            Ui.setDesc(this, Texts.stationShort(engine, st, needed));
            refreshActions(this);
        }
    }
}
