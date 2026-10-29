package com.doner.oyunu.ui;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;

/** Ana menü: Devam et / Yeni oyun / Nasıl oynanır / Ayarlar. */
public final class MenuScreen extends Screen {
    private ValueAnimator spin;
    private IconView skewer;
    private Button newGameBtn;
    private boolean confirmNew;

    public MenuScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Döner Dükkanı, ana menü"; }

    @Override public View create() {
        LinearLayout root = rootColumn();
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        skewer = new IconView(ctx, Icons.Kind.DONER, 120);
        root.addView(skewer, Ui.lp(ctx, Ui.dp(ctx, 120), Ui.dp(ctx, 120), 0, 12, 0, 0));

        TextView t = makeTitle("Döner Dükkanı");
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 38);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription("Döner Dükkanı, ana menü");
        root.addView(t, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        TextView sub = Ui.label(ctx, "Sıcak, hızlı, erişilebilir", 18, Theme.CREAM, false);
        sub.setGravity(Gravity.CENTER);
        sub.setFocusable(true);
        root.addView(sub, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));

        LinearLayout body = scrollBody(root);

        if (a.hasGame()) {
            Button cont = button("Devam et", Ui.PRIMARY, new Runnable() {
                @Override public void run() { a.startBriefing(); }
            });
            cont.setContentDescription("Devam et, gün " + a.save.day);
            body.addView(cont, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 8));
        }

        newGameBtn = button("Yeni oyun", a.hasGame() ? Ui.SECONDARY : Ui.PRIMARY, new Runnable() {
            @Override public void run() { onNewGame(); }
        });
        newGameBtn.setContentDescription("Yeni oyun");
        body.addView(newGameBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 8));

        body.addView(button("Nasıl oynanır", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new HelpScreen(a)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 8));

        body.addView(button("Ayarlar", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new SettingsScreen(a)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 8));

        TextView hint = Ui.label(ctx, "TalkBack açıkken sağa sola kaydırarak gez, çift dokunarak seç.", 15, Theme.MUTED, false);
        hint.setFocusable(true);
        hint.setGravity(Gravity.CENTER);
        body.addView(hint, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 12, 0, 4));
        return root;
    }

    private void onNewGame() {
        if (a.hasGame() && !confirmNew) {
            confirmNew = true;
            newGameBtn.setText("Emin misin? Tekrar dokun");
            newGameBtn.setContentDescription("Emin misin? Kayıt silinecek, tekrar dokun");
            a.speaker.announce("Kayıtlı oyun silinecek. Onaylamak için tekrar dokun.", true);
            return;
        }
        a.newGame();
        a.startBriefing();
    }

    @Override public void onEnter() {
        if (Ui.animationsOn(ctx) && skewer != null) {
            spin = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            spin.setDuration(3600);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) { skewer.setPhase((Float) va.getAnimatedValue()); }
            });
            spin.start();
        }
    }

    @Override public void onExit() { if (spin != null) { spin.cancel(); spin = null; } }

    @Override public void onPause() { if (spin != null) spin.pause(); }

    @Override public void onResume() { if (spin != null) spin.resume(); }

    @Override public boolean onBack() {
        if (confirmNew) {
            confirmNew = false;
            newGameBtn.setText("Yeni oyun");
            newGameBtn.setContentDescription("Yeni oyun");
            return true;
        }
        return false;
    }
}
