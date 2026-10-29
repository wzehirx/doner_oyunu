package com.doner.oyunu.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.StationOption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * İstasyon seçenek paneli: TalkBack Eylemleriyle AYNI seçenekleri büyük (>=56dp) düğmeler olarak gösterir.
 * Gruplar başlıklıdır (Boy seç / Malzeme ekle / Kontrol / Oku / Hazırlık). Tek dokunma bir seçeneği uygular;
 * panel, host kapatana ya da "Kapat" denene kadar açık kalır ve her yenilemede listeyi motordan tazeler
 * (art arda malzeme eklemek için). Tam ekran perde olarak GameScreen'in üstüne konur.
 */
final class MenuPanel {

    interface Source {
        /** Güncel seçenekler (panel açıkken sürekli tazelenir). */
        List<StationOption> options();
        /** Başlığın altındaki sipariş/istek bilgisi (boş olabilir). */
        String info();
        /** Canlı durum satırı (ör. "Eklenenler: domates bol"); boş olabilir. */
        String status();
    }

    interface Host {
        /** Bir seçenek seçildi: GameScreen motoru çağırır ve paneli kapatıp kapatmayacağına karar verir. */
        void onPick(MenuPanel p, StationOption op);
        /** "Kapat". */
        void onClose(MenuPanel p);
        /** Panelin acil al düğmesi. */
        void onEmergency(MenuPanel p);
    }

    final FrameLayout overlay;
    final TextView titleView;
    final Button closeBtn;
    private final MainActivity a;
    private final Source src;
    private final Host host;
    private final LinearLayout list;
    private final TextView infoTv, statusTv, msgTv;
    private final Button emergencyBtn;
    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<String, StationOption> current = new HashMap<>();
    private String signature = "";
    /** Panelin açıldığı istasyon / sipariş (GameScreen kullanır). */
    final int stationId;
    final int orderId;

    MenuPanel(MainActivity a, String title, int stationId, int orderId, Source src, Host host) {
        this.a = a;
        this.src = src;
        this.host = host;
        this.stationId = stationId;
        this.orderId = orderId;

        overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0xF01A0E08);
        overlay.setClickable(true);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(a, Theme.CARD, Theme.GOLD, 3, 20));
        panel.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16));

        titleView = Ui.label(a, title, 24, Theme.GOLD, true);
        Ui.heading(titleView);
        panel.addView(titleView);

        infoTv = Ui.label(a, "", 17, Theme.CREAM, false);
        infoTv.setFocusable(true);
        infoTv.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 4));
        panel.addView(infoTv);

        statusTv = Ui.label(a, "", 17, Theme.GOLD, true);
        statusTv.setFocusable(true);
        statusTv.setPadding(0, 0, 0, Ui.dp(a, 6));
        statusTv.setVisibility(View.GONE);
        panel.addView(statusTv);

        msgTv = Ui.label(a, "", 17, Theme.CREAM, true);
        msgTv.setFocusable(true);
        msgTv.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 6));
        msgTv.setBackground(Ui.round(a, Theme.CARD_HI, Theme.STROKE, 1.5f, 12));
        msgTv.setVisibility(View.GONE);
        panel.addView(msgTv, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));

        emergencyBtn = Ui.button(a, "", Ui.DANGER);
        emergencyBtn.setVisibility(View.GONE);
        emergencyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MenuPanel.this.a.sound.play("sfx_tap");
                MenuPanel.this.a.haptics.tap();
                MenuPanel.this.host.onEmergency(MenuPanel.this);
            }
        });
        panel.addView(emergencyBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));

        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        panel.addView(list);

        closeBtn = Ui.button(a, "Kapat", Ui.SECONDARY);
        closeBtn.setContentDescription("Kapat. Seçenekleri kapatır, hiçbir şey uygulanmaz.");
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MenuPanel.this.a.sound.play("sfx_tap");
                MenuPanel.this.a.haptics.tap();
                MenuPanel.this.host.onClose(MenuPanel.this);
            }
        });
        panel.addView(closeBtn, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        FrameLayout wrap = new FrameLayout(a);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER);
        plp.setMargins(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        wrap.addView(panel, plp);
        sv.addView(wrap, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
        overlay.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        refresh();
    }

    // ------------------------------------------------------------------ içerik
    static String groupTitle(StationOption.Group g) {
        switch (g) {
            case SIZE: return "Boy seç";
            case ADD: return "Malzeme ekle";
            case CONTROL: return "Kontrol";
            case READ: return "Oku";
            case PREP: return "Hazırlık";
            default: return "İşlem";
        }
    }

    /** Seçenek listesini ve durum satırlarını motordan tazeler (düğme kimlikleri değişmediyse yerinde günceller). */
    void refresh() {
        List<StationOption> ops = src.options();
        StringBuilder sig = new StringBuilder();
        for (StationOption op : ops) sig.append(op.id).append(':').append(op.group).append('|');
        if (!sig.toString().equals(signature)) {
            signature = sig.toString();
            rebuild(ops);
        }
        current.clear();
        for (StationOption op : ops) {
            current.put(op.id, op);
            Button b = buttons.get(op.id);
            if (b == null) continue;
            String label = op.label();
            if (!label.contentEquals(b.getText())) b.setText(label);
            Ui.setDesc(b, label);
            b.setAlpha(op.enabled ? 1f : 0.65f);
        }
        String info = src.info();
        infoTv.setVisibility(info == null || info.isEmpty() ? View.GONE : View.VISIBLE);
        if (info != null && !info.contentEquals(infoTv.getText())) { infoTv.setText(info); Ui.setDesc(infoTv, info); }
        String st = src.status();
        statusTv.setVisibility(st == null || st.isEmpty() ? View.GONE : View.VISIBLE);
        if (st != null && !st.contentEquals(statusTv.getText())) { statusTv.setText(st); Ui.setDesc(statusTv, st); }
    }

    private void rebuild(List<StationOption> ops) {
        list.removeAllViews();
        buttons.clear();
        StationOption.Group last = null;
        for (final StationOption op : ops) {
            if (op.group != last) {
                last = op.group;
                TextView h = Ui.label(a, groupTitle(op.group), 19, Theme.GOLD, true);
                Ui.heading(h);
                h.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
                list.addView(h);
            }
            int style = op.group == StationOption.Group.SIZE || op.group == StationOption.Group.STEP ? Ui.PRIMARY
                    : op.id.equals("finish") ? Ui.GOLD : Ui.SECONDARY;
            Button b = Ui.button(a, op.label(), style);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
            b.setMinHeight(Ui.dp(a, 64));
            b.setMinimumHeight(Ui.dp(a, 64));
            final String id = op.id;
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.sound.play("sfx_tap");
                    a.haptics.tap();
                    StationOption cur = current.get(id);
                    host.onPick(MenuPanel.this, cur != null ? cur : op);
                }
            });
            buttons.put(id, b);
            list.addView(b, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
    }

    /** Son sonucu (okunan hedef, eklendi, reddedildi...) panelde görünür ve okunur satır olarak gösterir. */
    void setMessage(String text) {
        if (text == null || text.isEmpty()) { msgTv.setVisibility(View.GONE); return; }
        msgTv.setVisibility(View.VISIBLE);
        msgTv.setText(text);
        Ui.setDesc(msgTv, text);
    }

    /** Acil al düğmesini gösterir (label null ise gizler). */
    void setEmergency(String label, String desc) {
        if (label == null) { emergencyBtn.setVisibility(View.GONE); return; }
        emergencyBtn.setVisibility(View.VISIBLE);
        emergencyBtn.setText(label);
        Ui.setDesc(emergencyBtn, desc);
    }

    boolean emergencyVisible() { return emergencyBtn.getVisibility() == View.VISIBLE; }

    int optionCount() { return buttons.size(); }

    /** Testler / host için: seçenek düğmesi. */
    Button button(String optionId) { return buttons.get(optionId); }
}
