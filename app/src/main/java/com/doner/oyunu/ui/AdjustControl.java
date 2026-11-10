package com.doner.oyunu.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Arrays;

/**
 * v2.7 yeniden kullanılabilir sayısal ayar denetimi: üstte değer etiketi, altında <b>[−] [kaydırma çubuğu] [+]</b>.
 * <ul>
 * <li><b>Düğmeler</b> ({@code buttonStep}): bir sonraki/önceki basamağa gider (112 yüzde +5 → 115); uçta hata sesi ve
 * "En yüksek/En düşük" duyurusu.</li>
 * <li><b>Kaydırma çubuğu</b> ({@code step} granülü): sürüklerken değer canlı uygulanır ama OKUNMAZ; bırakınca tek duyuru.
 * TalkBack'te yukarı/aşağı kaydırma (SCROLL_FORWARD/BACKWARD) düğme basamağı kadar, SET_PROGRESS doğrudan ayarlar;
 * düğüm {@code rangeInfo} taşır, gerçek değer {@code stateDescription}'dadır (API 30+; altında contentDescription).
 * TalkBack seekbar değerini kendisi okuduğundan o yolda ayrıca duyuru yapılmaz.</li>
 * <li><b>Değer etiketi</b>: dokununca sayısal klavye (EditText, inputType number) açılır; "Tamam" (ya da klavye Bitti)
 * onaylar. Aralık dışı değer SINIRLANIR ve uyarılır ("Değer 50 ile 150 arasında olmalı. yüzde 150 yapıldı."); geçersiz/boş
 * giriş pencereyi açık tutar. Seçenekli (ayrık) denetimde ({@link #choices}) değer en yakın seçeneğe yuvarlanır.</li>
 * <li><b>Duyuru</b>: değer değişince TEK kısa cümle, geri sayımlı ({@link #DEBOUNCE_MS}): art arda dokunuşlarda yalnızca son
 * değer söylenir. Denetimin kendi geri bildirimi ekran okuyucu düzeyinden ({@code Anlatım düzeyi}) bağımsızdır.</li>
 * <li><b>Odak kaybı yok</b>: görünümler bir kez kurulur ve yerinde güncellenir; klavye kapanınca odak değer etiketine döner.</li>
 * <li>Tüm parçalar ≥ {@link #MIN_TARGET_DP} dp; contentDescription kısa Türkçe.</li>
 * </ul>
 */
public final class AdjustControl extends LinearLayout {
    /** Değerin gösterimi: {@code shown} etikette ve erişilebilirlik durumunda, {@code spoken} duyuruda. */
    public interface Format {
        /** Kısa gösterim ("yüzde 105, 26 lira", "2000 lira", "10 gün"). */
        String shown(int value);

        /** Duyuru cümlesi (varsayılan: gösterim). */
        default String spoken(int value) { return shown(value); }

        /** Klavye ipucundaki birim ("yüzde", "lira", "gün"). */
        String unit();
    }

    /** {@code settled=false}: sürükleme sürüyor (canlı uygula, kaydetme); true: değer kesinleşti (kaydet). */
    public interface Listener { void onChanged(int value, boolean settled); }

    /** Ekran/etkinlik bağları (ses, geri bildirim, ekran okuyucu durumu); testlerde taklit edilir. */
    public interface Host {
        /** Kısa duyuru (açık okuma yolu: konuşanı keser, yinelenen metin yutulmaz). */
        void speak(String text);

        boolean screenReaderOn();

        void tapFeedback();

        void errorFeedback();
    }

    public static final int MIN_TARGET_DP = 56;
    /** Düğme/klavye değişiminden sonra duyuru gecikmesi: art arda dokunuşlar tek duyuruya iner. */
    public static final long DEBOUNCE_MS = 450;
    /** Sürükleme bırakılınca duyuru gecikmesi. */
    public static final long RELEASE_MS = 150;
    /** Kaydırma çubuğunun etiketi (denetim ve test için). */
    public static final String SEEK_TAG = "adjust_seek";

    /** Hazır gösterimler. */
    public static final class Formats {
        private Formats() {}

        public static final Format PERCENT = new Format() {
            @Override public String shown(int v) { return "yüzde " + v; }
            @Override public String unit() { return "yüzde"; }
        };
        public static final Format LIRA = new Format() {
            @Override public String shown(int v) { return v + " lira"; }
            @Override public String unit() { return "lira"; }
        };
        public static final Format DAYS = new Format() {
            @Override public String shown(int v) { return v + " gün"; }
            @Override public String unit() { return "gün"; }
        };
    }

    private final Host host;
    private final String name;
    private final Format fmt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Button label, minus, plus;
    private final SeekBar seek;
    private int min, max, step, buttonStep;
    /** Ayrık seçenekler (null: aralık modu). */
    private final int[] choices;
    private int value;
    private Listener listener;
    private boolean tracking, updating;
    private String pendingText;
    private boolean showName;
    private Dialog dialog;
    private EditText dialogEdit;
    private TextView dialogError;

    /** Aralık denetimi: {@code min}..{@code max}, {@code step} granül (çubuk ve klavye), {@code buttonStep} düğme basamağı. */
    public AdjustControl(Context ctx, Host host, String name, int min, int max, int step, int buttonStep, Format fmt) {
        this(ctx, host, name, min, max, step, buttonStep, fmt, null);
    }

    /** Ayrık seçenekli denetim (ör. vade 5/10/20 gün): düğme ve çubuk seçenekler arasında gezer, klavye en yakına yuvarlar. */
    public static AdjustControl choices(Context ctx, Host host, String name, int[] values, Format fmt) {
        int[] v = values.clone();
        Arrays.sort(v);
        return new AdjustControl(ctx, host, name, v[0], v[v.length - 1], 1, 1, fmt, v);
    }

    private AdjustControl(Context ctx, Host host, String name, int min, int max, int step, int buttonStep, Format fmt, int[] choices) {
        super(ctx);
        this.host = host;
        this.name = name == null ? "" : name;
        this.fmt = fmt;
        this.min = min;
        this.max = Math.max(min, max);
        this.step = Math.max(1, step);
        this.buttonStep = Math.max(this.step, buttonStep);
        this.choices = choices;
        this.value = min;
        setOrientation(VERTICAL);

        int target = Ui.dp(ctx, MIN_TARGET_DP + 4);
        label = Ui.button(ctx, "", Ui.SECONDARY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        label.setMinHeight(target);
        label.setMinimumHeight(target);
        label.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { AdjustControl.this.host.tapFeedback(); openKeyboard(); }
        });
        label.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View v, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(v, info);
                info.setClassName(Button.class.getName());
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Değeri yazarak değiştir"));
            }
        });
        addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        minus = sign(ctx, "−");
        plus = sign(ctx, "+");
        minus.setContentDescription(this.name + " azalt");
        plus.setContentDescription(this.name + " artır");
        minus.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { AdjustControl.this.host.tapFeedback(); byButton(-1); }
        });
        plus.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { AdjustControl.this.host.tapFeedback(); byButton(+1); }
        });
        seek = new SeekBar(ctx);
        seek.setTag(SEEK_TAG);
        seek.setMinimumHeight(target);
        seek.setPadding(Ui.dp(ctx, 18), 0, Ui.dp(ctx, 18), 0);
        seek.setThumbTintList(ColorStateList.valueOf(Theme.GOLD));
        seek.setProgressTintList(ColorStateList.valueOf(Theme.ORANGE));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.STROKE));
        seek.setSplitTrack(false);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || updating) return;
                int v = fromProgress(progress);
                if (tracking) {
                    if (setInternal(v)) notifyListener(false); // sürükleme: canlı uygula, OKUMA
                } else if (setInternal(v)) {
                    // TalkBack yukarı/aşağı kaydırma, SET_PROGRESS ya da yön tuşu
                    notifyListener(true);
                    if (!AdjustControl.this.host.screenReaderOn()) say(null, DEBOUNCE_MS); // TalkBack kendisi okur
                }
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {
                tracking = true;
                handler.removeCallbacks(sayRunner);
            }

            @Override public void onStopTrackingTouch(SeekBar sb) {
                tracking = false;
                notifyListener(true);
                say(null, RELEASE_MS); // bırakınca tek duyuru
            }
        });
        seek.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View v, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(v, info);
                info.setClassName(SeekBar.class.getName());
                info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                        0, progressMax(), toProgress(value)));
                if (v.isEnabled()) {
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
                    if (value < maxValue()) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                    if (value > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
                }
            }

            @Override public boolean performAccessibilityAction(View v, int action, Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    if (!v.isEnabled()) return false;
                    int dir = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? +1 : -1;
                    if (!setInternal(buttonTarget(dir))) return false;
                    notifyListener(true);
                    if (!AdjustControl.this.host.screenReaderOn()) say(null, DEBOUNCE_MS);
                    return true;
                }
                return super.performAccessibilityAction(v, action, args);
            }
        });
        seek.setKeyProgressIncrement(Math.max(1, choices != null ? 1 : this.buttonStep / this.step));

        int sz = Ui.dp(ctx, MIN_TARGET_DP + 4);
        row.addView(minus, new LayoutParams(sz, sz));
        row.addView(seek, new LayoutParams(0, sz, 1f));
        row.addView(plus, new LayoutParams(sz, sz));
        LayoutParams rlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(ctx, 4);
        addView(row, rlp);
        refreshSeekRange();
        syncViews();
    }

    private Button sign(Context ctx, String glyph) {
        Button b = Ui.button(ctx, glyph, Ui.SECONDARY);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        b.setPadding(0, 0, 0, 0);
        int s = Ui.dp(ctx, MIN_TARGET_DP + 4);
        b.setMinHeight(s);
        b.setMinimumHeight(s);
        b.setMinWidth(s);
        b.setMinimumWidth(s);
        return b;
    }

    // ------------------------------------------------------------------ genel API
    public void setListener(Listener l) { listener = l; }

    /** Değer etiketinde adı da göster ("Müzik sesi: yüzde 70"); varsayılan: yalnızca değer (ad üstteki başlıktadır). */
    public void setShowName(boolean on) {
        showName = on;
        syncViews();
    }

    /** Düğme açıklamaları (varsayılan: "{ad} azalt" / "{ad} artır"). */
    public void setButtonDescriptions(String decrease, String increase) {
        minus.setContentDescription(decrease);
        plus.setContentDescription(increase);
    }

    /** Değeri sessizce ayarlar (dinleyici ve duyuru yok); aralığa/granüle uydurur. */
    public void setValue(int v) {
        setInternal(v);
        syncViews();
    }

    public int getValue() { return value; }

    public int getMin() { return min; }

    /** Granüle hizalı en yüksek değer. */
    public int getMax() { return maxValue(); }

    /** Aralığı değiştirir (ör. banka limiti değişti); değer sığdırılır, dinleyici çağrılmaz. */
    public void setRange(int newMin, int newMax) {
        if (choices != null) return;
        min = newMin;
        max = Math.max(newMin, newMax);
        refreshSeekRange();
        value = snap(value);
        syncViews();
    }

    /** Değer etiketi (düğme): dokununca klavye. */
    public Button valueLabel() { return label; }

    public Button minusButton() { return minus; }

    public Button plusButton() { return plus; }

    public SeekBar seekBar() { return seek; }

    /** Açık klavye penceresi (yoksa null; test). */
    public Dialog keyboardDialog() { return dialog; }

    // ------------------------------------------------------------------ model
    /** Granüle hizalı en yüksek değer. */
    private int maxValue() {
        if (choices != null) return choices[choices.length - 1];
        return min + ((max - min) / step) * step;
    }

    private int progressMax() {
        if (choices != null) return choices.length - 1;
        return (maxValue() - min) / step;
    }

    private int toProgress(int v) {
        if (choices != null) return indexOfNearest(v);
        return (snap(v) - min) / step;
    }

    private int fromProgress(int p) {
        if (choices != null) return choices[Math.max(0, Math.min(choices.length - 1, p))];
        return snap(min + p * step);
    }

    private int indexOfNearest(int v) {
        int best = 0;
        for (int i = 1; i < choices.length; i++) if (Math.abs(choices[i] - v) < Math.abs(choices[best] - v)) best = i;
        return best;
    }

    /** Aralığa sınırlar ve granüle (ya da en yakın seçeneğe) hizalar. */
    private int snap(int v) {
        if (choices != null) return choices[indexOfNearest(v)];
        int hi = maxValue();
        if (v <= min) return min;
        if (v >= hi) return hi;
        int off = v - min;
        int r = Math.round(off / (float) step) * step;
        return Math.min(hi, min + r);
    }

    /** Düğme/kaydırma hedefi: yönde bir sonraki basamak (basamağın katına hizalı); uçta aynı değer. */
    private int buttonTarget(int dir) {
        if (choices != null) {
            int i = indexOfNearest(value) + dir;
            return choices[Math.max(0, Math.min(choices.length - 1, i))];
        }
        int off = value - min;
        int next;
        if (dir > 0) next = (off / buttonStep + 1) * buttonStep;
        else next = (off % buttonStep == 0) ? off - buttonStep : (off / buttonStep) * buttonStep;
        return Math.max(min, Math.min(maxValue(), min + next));
    }

    /** Değeri değiştirir (hizalı); değiştiyse true. Dinleyici/duyuru çağıranın işidir. */
    private boolean setInternal(int v) {
        int s = snap(v);
        if (s == value) { syncViews(); return false; }
        value = s;
        syncViews();
        return true;
    }

    private void notifyListener(boolean settled) {
        if (listener != null) listener.onChanged(value, settled);
    }

    private void refreshSeekRange() {
        updating = true;
        seek.setMax(progressMax());
        updating = false;
    }

    /** Görünümleri yerinde günceller (odak korunur: yeni görünüm kurulmaz). */
    private void syncViews() {
        String shown = fmt.shown(value);
        String lab = name.isEmpty() ? shown : name + ": " + shown;
        CharSequence text = showName ? lab : shown;
        if (!text.toString().contentEquals(label.getText())) label.setText(text);
        Ui.setDesc(label, lab);
        updating = true;
        seek.setEnabled(progressMax() > 0);
        int p = toProgress(value);
        if (seek.getProgress() != p) seek.setProgress(p);
        updating = false;
        // ekran okuyucuya gerçek değer: API 30+ durum açıklaması; altında kaydırma çubuğunun açıklamasına eklenir
        if (Build.VERSION.SDK_INT >= 30) {
            seek.setStateDescription(shown);
            Ui.setDesc(seek, name.isEmpty() ? "Kaydırma çubuğu" : name);
        } else {
            Ui.setDesc(seek, name.isEmpty() ? shown : name + ": " + shown);
        }
        Ui.dim(minus, value <= min);
        Ui.dim(plus, value >= maxValue());
    }

    // ------------------------------------------------------------------ eylemler
    /** Bir basamak artır (+1) / azalt (−1): düğmelerle aynı iş (TalkBack Eylemleri menüsü de bunu kullanır). */
    public void stepBy(int dir) { byButton(dir < 0 ? -1 : +1); }

    private void byButton(int dir) {
        int t = buttonTarget(dir);
        if (t == value) {
            host.errorFeedback();
            say((dir < 0 ? "En düşük: " : "En yüksek: ") + fmt.spoken(value), DEBOUNCE_MS);
            return;
        }
        setInternal(t);
        notifyListener(true);
        say(null, DEBOUNCE_MS);
    }

    private final Runnable sayRunner = new Runnable() {
        @Override public void run() {
            String t = pendingText != null ? pendingText : fmt.spoken(value);
            pendingText = null;
            host.speak(t);
        }
    };

    /** Geri sayımlı tek duyuru: yeni değişiklik öncekini iptal eder (yalnızca son değer söylenir). */
    private void say(String text, long delayMs) {
        pendingText = text;
        handler.removeCallbacks(sayRunner);
        handler.postDelayed(sayRunner, delayMs);
    }

    // ------------------------------------------------------------------ sayısal klavye
    private String rangeText() {
        if (choices != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < choices.length; i++) {
                if (i > 0) sb.append(i == choices.length - 1 ? " ya da " : ", ");
                sb.append(choices[i]);
            }
            return sb.toString() + " olabilir";
        }
        return min + " ile " + maxValue() + " arasında olmalı";
    }

    /** Sayısal klavye penceresini açar (değer etiketine dokunuşla). */
    public void openKeyboard() {
        dismissDialog();
        final Context c = getContext();
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        int pad = Ui.dp(c, 18);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(Ui.round(c, Theme.CARD, Theme.GOLD, 3, 20));
        if (Build.VERSION.SDK_INT >= 28) box.setAccessibilityPaneTitle((name.isEmpty() ? "Değer" : name) + " değerini yaz");

        TextView title = Ui.label(c, (name.isEmpty() ? "Değer" : name) + " değerini yaz", 22, Theme.GOLD, true);
        Ui.heading(title);
        box.addView(title);
        TextView hint = Ui.label(c, fmt.unit() + " olarak, " + rangeText() + ".", 17, Theme.CREAM, false);
        hint.setFocusable(true);
        hint.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        box.addView(hint);

        final EditText et = new EditText(c);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setSingleLine(true);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        et.setText(String.valueOf(value));
        et.setSelectAllOnFocus(true);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        et.setTextColor(Theme.CREAM);
        et.setHintTextColor(Theme.MUTED);
        et.setGravity(Gravity.CENTER);
        et.setMinHeight(Ui.dp(c, MIN_TARGET_DP + 4));
        et.setBackground(Ui.round(c, Theme.CARD_HI, Theme.STROKE, 2, 14));
        box.addView(et, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialogEdit = et;

        final TextView err = Ui.label(c, "", 17, Theme.RED, true);
        err.setVisibility(GONE);
        err.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        err.setPadding(0, Ui.dp(c, 6), 0, 0);
        box.addView(err);
        dialogError = err;

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        Button cancel = Ui.button(c, "Vazgeç", Ui.SECONDARY);
        Button ok = Ui.button(c, "Tamam", Ui.PRIMARY);
        cancel.setContentDescription("Vazgeç");
        ok.setContentDescription("Tamam");
        cancel.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { host.tapFeedback(); d.dismiss(); }
        });
        ok.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { host.tapFeedback(); commit(et.getText().toString()); }
        });
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) { commit(v.getText().toString()); return true; }
                return false;
            }
        });
        LayoutParams l1 = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dp(c, 6);
        LayoutParams l2 = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = Ui.dp(c, 6);
        bar.addView(cancel, l1);
        bar.addView(ok, l2);
        LayoutParams blp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(c, 12);
        box.addView(bar, blp);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) {
                if (dialog == d) { dialog = null; dialogEdit = null; dialogError = null; }
                restoreFocus();
            }
        });
        dialog = d;
        d.show();
        et.requestFocus();
    }

    /** Klavye girişini uygular (Tamam / klavye Bitti). Test için de açıktır. */
    public void commit(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || !t.matches("\\d+")) {
            host.errorFeedback();
            if (dialogError != null) {
                dialogError.setText("Bir sayı yaz.");
                dialogError.setVisibility(VISIBLE);
            }
            return; // pencere açık kalır
        }
        int typed = t.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(t); // çok uzun sayı: üst sınıra sınırlanır
        int v = snap(typed);
        boolean outside = choices != null ? !isChoice(typed) : (typed < min || typed > maxValue());
        dismissDialog();
        setInternal(v);
        notifyListener(true);
        if (outside) {
            host.errorFeedback();
            say("Değer " + rangeText() + ". " + fmt.shown(value) + " yapıldı.", RELEASE_MS);
        } else {
            say(null, RELEASE_MS);
        }
    }

    private boolean isChoice(int v) {
        for (int c : choices) if (c == v) return true;
        return false;
    }

    private void dismissDialog() {
        Dialog d = dialog;
        dialog = null;
        dialogEdit = null;
        dialogError = null;
        if (d != null) { try { d.dismiss(); } catch (Exception ignored) { } }
    }

    private void restoreFocus() {
        label.requestFocus();
        label.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
        if (host.screenReaderOn()) label.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(sayRunner);
        dismissDialog();
    }
}
