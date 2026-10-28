package com.doner.oyunu.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;

import java.util.List;

/** Nasıl oynanır: başlıklı bölümler + hepsini sesli oku düğmesi. */
public final class HelpScreen extends Screen {
    public HelpScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Nasıl oynanır"; }

    @Override public View create() {
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Nasıl oynanır"));
        LinearLayout body = scrollBody(root);

        final List<String[]> sections = Texts.helpSections();
        body.addView(button("Hepsini sesli oku", Ui.GOLD, new Runnable() {
            @Override public void run() {
                boolean first = true;
                for (String[] s : sections) {
                    a.speaker.speakLong(s[0] + ". " + s[1], first);
                    first = false;
                }
            }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));

        for (String[] s : sections) {
            body.addView(sectionHeading(s[0]));
            TextView p = paragraph(s[1]);
            body.addView(p);
        }

        root.addView(button("Geri", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new MenuScreen(a)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        return root;
    }

    @Override public boolean onBack() { a.show(new MenuScreen(a)); return true; }
}
