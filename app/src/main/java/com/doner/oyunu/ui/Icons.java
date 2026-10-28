package com.doner.oyunu.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.doner.oyunu.engine.Station;

/** Canvas ile çizilen şık ikonlar. Hepsi s x s kare içinde, (x,y) sol üst. */
public final class Icons {
    private Icons() {}

    public enum Kind { DONER, BREAD, SAUCE, GRILL, WRAP, DRINK, DESSERT, COIN, STAR, CLOCK, BAG }

    public static Kind forStation(Station st) {
        switch (st) {
            case DONER: return Kind.DONER;
            case EKMEK: return Kind.BREAD;
            case SOS: return Kind.SAUCE;
            case IZGARA: return Kind.GRILL;
            case PAKET: return Kind.WRAP;
            case ICECEK: return Kind.DRINK;
            default: return Kind.DESSERT;
        }
    }

    private static final int MEAT_TOP = 0xFFD9743A, MEAT_BOT = 0xFF7A2E14, MEAT_HI = 0xFFF2A766;
    private static final int BREAD = 0xFFE3A254, BREAD_DARK = 0xFFC27B33, LETTUCE = 0xFF7BC96F;

    public static void draw(Canvas c, Kind k, float x, float y, float s, Paint p, float phase) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        c.save();
        c.translate(x, y);
        switch (k) {
            case DONER: doner(c, s, p, phase); break;
            case BREAD: bread(c, s, p); break;
            case SAUCE: sauce(c, s, p); break;
            case GRILL: grill(c, s, p, phase); break;
            case WRAP: wrap(c, s, p); break;
            case DRINK: drink(c, s, p); break;
            case DESSERT: dessert(c, s, p); break;
            case COIN: coin(c, s, p); break;
            case STAR: star(c, s / 2f, s / 2f, s * 0.48f, p); break;
            case CLOCK: clock(c, s, p); break;
            default: bag(c, s, p);
        }
        c.restore();
        p.setShader(null);
    }

    // ------------------------------------------------------------------ döner şişi
    private static void doner(Canvas c, float s, Paint p, float phase) {
        // şiş
        p.setColor(Theme.CREAM);
        c.drawRoundRect(new RectF(s * 0.48f, s * 0.02f, s * 0.52f, s * 0.98f), s * 0.02f, s * 0.02f, p);
        // et konisi
        Path cone = new Path();
        cone.moveTo(s * 0.20f, s * 0.14f);
        cone.quadTo(s * 0.5f, s * 0.06f, s * 0.80f, s * 0.14f);
        cone.lineTo(s * 0.66f, s * 0.82f);
        cone.quadTo(s * 0.5f, s * 0.88f, s * 0.34f, s * 0.82f);
        cone.close();
        p.setShader(new LinearGradient(0, s * 0.1f, 0, s * 0.85f, MEAT_TOP, MEAT_BOT, Shader.TileMode.CLAMP));
        c.drawPath(cone, p);
        p.setShader(null);
        // dönüş hissi: kayan parlak şerit
        c.save();
        c.clipPath(cone);
        float bx = s * (0.5f + 0.22f * (float) Math.sin(phase));
        p.setColor(0x55FFFFFF);
        c.drawRect(bx - s * 0.05f, s * 0.05f, bx + s * 0.05f, s * 0.9f, p);
        p.setColor(MEAT_HI);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.025f);
        for (int i = 1; i <= 4; i++) {
            float yy = s * (0.14f + i * 0.14f);
            Path w = new Path();
            w.moveTo(0, yy);
            w.quadTo(s * 0.5f, yy + s * 0.05f, s, yy);
            c.drawPath(w, p);
        }
        c.restore();
        p.setStyle(Paint.Style.FILL);
        // kapak ve taban
        p.setColor(Theme.GOLD);
        c.drawCircle(s * 0.5f, s * 0.07f, s * 0.05f, p);
        c.drawRoundRect(new RectF(s * 0.3f, s * 0.86f, s * 0.7f, s * 0.94f), s * 0.03f, s * 0.03f, p);
    }

    // ------------------------------------------------------------------ ekmek
    private static void bread(Canvas c, float s, Paint p) {
        p.setColor(BREAD);
        c.drawRoundRect(new RectF(s * 0.08f, s * 0.20f, s * 0.92f, s * 0.54f), s * 0.24f, s * 0.24f, p);
        p.setColor(0xFF8B3A1E);
        c.drawRoundRect(new RectF(s * 0.06f, s * 0.52f, s * 0.94f, s * 0.64f), s * 0.05f, s * 0.05f, p);
        p.setColor(LETTUCE);
        for (int i = 0; i < 4; i++) c.drawCircle(s * (0.2f + i * 0.2f), s * 0.52f, s * 0.05f, p);
        p.setColor(BREAD_DARK);
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.64f, s * 0.90f, s * 0.82f), s * 0.10f, s * 0.10f, p);
        p.setColor(Theme.CREAM);
        for (int i = 0; i < 5; i++) c.drawCircle(s * (0.28f + (i % 3) * 0.2f), s * (0.30f + (i / 3) * 0.1f), s * 0.018f, p);
        // sıcak buhar
        p.setColor(Theme.ORANGE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.03f);
        for (int i = 0; i < 3; i++) {
            float xx = s * (0.3f + i * 0.2f);
            Path w = new Path();
            w.moveTo(xx, s * 0.16f);
            w.quadTo(xx - s * 0.05f, s * 0.10f, xx, s * 0.05f);
            w.quadTo(xx + s * 0.05f, s * 0.0f, xx, s * -0.03f);
            c.drawPath(w, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ sos / salata
    private static void sauce(Canvas c, float s, Paint p) {
        p.setColor(Theme.CREAM);
        Path nozzle = new Path();
        nozzle.moveTo(s * 0.20f, s * 0.04f);
        nozzle.lineTo(s * 0.30f, s * 0.04f);
        nozzle.lineTo(s * 0.34f, s * 0.20f);
        nozzle.lineTo(s * 0.16f, s * 0.20f);
        nozzle.close();
        c.drawPath(nozzle, p);
        p.setColor(0xFFE0402A);
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.20f, s * 0.40f, s * 0.90f), s * 0.08f, s * 0.08f, p);
        p.setColor(Theme.CREAM);
        c.drawRoundRect(new RectF(s * 0.14f, s * 0.44f, s * 0.36f, s * 0.68f), s * 0.04f, s * 0.04f, p);
        p.setColor(0xFFE0402A);
        c.drawCircle(s * 0.25f, s * 0.56f, s * 0.05f, p);
        // domates
        p.setColor(0xFFE8452D);
        c.drawCircle(s * 0.68f, s * 0.68f, s * 0.22f, p);
        p.setColor(LETTUCE);
        Path leaf = new Path();
        leaf.moveTo(s * 0.68f, s * 0.46f);
        leaf.lineTo(s * 0.58f, s * 0.42f);
        leaf.lineTo(s * 0.66f, s * 0.50f);
        leaf.lineTo(s * 0.68f, s * 0.38f);
        leaf.lineTo(s * 0.72f, s * 0.50f);
        leaf.lineTo(s * 0.80f, s * 0.42f);
        leaf.close();
        c.drawPath(leaf, p);
        // marul yaprağı
        p.setColor(0xFF5FB85A);
        c.drawOval(new RectF(s * 0.48f, s * 0.14f, s * 0.92f, s * 0.38f), p);
        p.setColor(LETTUCE);
        c.drawOval(new RectF(s * 0.52f, s * 0.12f, s * 0.88f, s * 0.32f), p);
    }

    // ------------------------------------------------------------------ ızgara / fırın
    private static void grill(Canvas c, float s, Paint p, float phase) {
        // alevler
        float[] fx = {0.28f, 0.5f, 0.72f};
        for (int i = 0; i < 3; i++) {
            float h = s * (0.30f + 0.05f * (float) Math.sin(phase * 2 + i));
            flame(c, s * fx[i], s * 0.48f, s * 0.11f, h, p);
        }
        p.setColor(0xFF3C3C40);
        c.drawRoundRect(new RectF(s * 0.06f, s * 0.50f, s * 0.94f, s * 0.84f), s * 0.07f, s * 0.07f, p);
        p.setColor(Theme.CREAM);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.03f);
        for (int i = 0; i < 4; i++) {
            float yy = s * (0.58f + i * 0.07f);
            c.drawLine(s * 0.12f, yy, s * 0.88f, yy, p);
        }
        for (int i = 0; i < 5; i++) {
            float xx = s * (0.2f + i * 0.15f);
            c.drawLine(xx, s * 0.54f, xx, s * 0.80f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.GOLD);
        c.drawRoundRect(new RectF(s * 0.06f, s * 0.84f, s * 0.94f, s * 0.90f), s * 0.03f, s * 0.03f, p);
    }

    private static void flame(Canvas c, float cx, float base, float w, float h, Paint p) {
        Path f = new Path();
        f.moveTo(cx - w, base);
        f.quadTo(cx - w * 1.1f, base - h * 0.5f, cx, base - h);
        f.quadTo(cx + w * 1.1f, base - h * 0.5f, cx + w, base);
        f.close();
        p.setColor(Theme.ORANGE_DEEP);
        c.drawPath(f, p);
        Path g = new Path();
        g.moveTo(cx - w * 0.5f, base);
        g.quadTo(cx - w * 0.5f, base - h * 0.4f, cx, base - h * 0.6f);
        g.quadTo(cx + w * 0.5f, base - h * 0.4f, cx + w * 0.5f, base);
        g.close();
        p.setColor(Theme.GOLD);
        c.drawPath(g, p);
    }

    // ------------------------------------------------------------------ paket (dürüm)
    private static void wrap(Canvas c, float s, Paint p) {
        c.save();
        c.rotate(-18, s / 2, s / 2);
        p.setColor(0xFFEBCB92);
        c.drawRoundRect(new RectF(s * 0.12f, s * 0.30f, s * 0.88f, s * 0.70f), s * 0.18f, s * 0.18f, p);
        p.setColor(Theme.CREAM);
        Path paper = new Path();
        paper.moveTo(s * 0.12f, s * 0.46f);
        paper.lineTo(s * 0.88f, s * 0.46f);
        paper.lineTo(s * 0.88f, s * 0.70f);
        paper.lineTo(s * 0.12f, s * 0.70f);
        paper.close();
        c.drawPath(paper, p);
        p.setColor(Theme.RED);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.025f);
        for (int i = 0; i < 5; i++) {
            float xx = s * (0.22f + i * 0.14f);
            c.drawLine(xx, s * 0.46f, xx - s * 0.05f, s * 0.70f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(LETTUCE);
        c.drawCircle(s * 0.30f, s * 0.34f, s * 0.05f, p);
        c.drawCircle(s * 0.46f, s * 0.31f, s * 0.05f, p);
        p.setColor(0xFFE8452D);
        c.drawCircle(s * 0.62f, s * 0.33f, s * 0.05f, p);
        // sıkma uçları
        p.setColor(Theme.CREAM);
        Path l = new Path();
        l.moveTo(s * 0.14f, s * 0.50f);
        l.lineTo(s * 0.02f, s * 0.42f);
        l.lineTo(s * 0.04f, s * 0.58f);
        l.close();
        c.drawPath(l, p);
        Path r = new Path();
        r.moveTo(s * 0.86f, s * 0.50f);
        r.lineTo(s * 0.98f, s * 0.42f);
        r.lineTo(s * 0.96f, s * 0.58f);
        r.close();
        c.drawPath(r, p);
        c.restore();
    }

    // ------------------------------------------------------------------ içecek
    private static void drink(Canvas c, float s, Paint p) {
        p.setColor(Theme.RED);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.05f);
        c.drawLine(s * 0.56f, s * 0.30f, s * 0.66f, s * 0.06f, p);
        p.setStyle(Paint.Style.FILL);
        Path cup = new Path();
        cup.moveTo(s * 0.24f, s * 0.28f);
        cup.lineTo(s * 0.76f, s * 0.28f);
        cup.lineTo(s * 0.68f, s * 0.90f);
        cup.lineTo(s * 0.32f, s * 0.90f);
        cup.close();
        p.setColor(0xFFF6EFDD);
        c.drawPath(cup, p);
        c.save();
        c.clipPath(cup);
        p.setColor(Theme.ORANGE);
        c.drawRect(0, s * 0.50f, s, s * 0.68f, p);
        p.setColor(Theme.GOLD);
        c.drawCircle(s * 0.5f, s * 0.59f, s * 0.05f, p);
        c.restore();
        p.setColor(0xFFDDD2B8);
        c.drawRoundRect(new RectF(s * 0.20f, s * 0.22f, s * 0.80f, s * 0.31f), s * 0.04f, s * 0.04f, p);
    }

    // ------------------------------------------------------------------ tatlı (künefe)
    private static void dessert(Canvas c, float s, Paint p) {
        p.setColor(Theme.CREAM);
        c.drawOval(new RectF(s * 0.04f, s * 0.52f, s * 0.96f, s * 0.90f), p);
        p.setColor(0xFFD9CDB2);
        c.drawOval(new RectF(s * 0.14f, s * 0.58f, s * 0.86f, s * 0.84f), p);
        p.setColor(0xFFE39A2D);
        c.drawOval(new RectF(s * 0.16f, s * 0.34f, s * 0.84f, s * 0.74f), p);
        p.setColor(0xFFF2BA52);
        c.drawOval(new RectF(s * 0.22f, s * 0.30f, s * 0.78f, s * 0.60f), p);
        p.setColor(0xFFB4701A);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.02f);
        for (int i = 0; i < 5; i++) {
            float xx = s * (0.32f + i * 0.09f);
            c.drawLine(xx, s * 0.38f, xx - s * 0.04f, s * 0.54f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF7FB069);
        c.drawCircle(s * 0.40f, s * 0.42f, s * 0.025f, p);
        c.drawCircle(s * 0.56f, s * 0.46f, s * 0.025f, p);
        c.drawCircle(s * 0.62f, s * 0.38f, s * 0.025f, p);
        p.setColor(0x88FFFFFF);
        c.drawOval(new RectF(s * 0.28f, s * 0.33f, s * 0.46f, s * 0.38f), p);
    }

    // ------------------------------------------------------------------ yardımcı ikonlar
    private static void coin(Canvas c, float s, Paint p) {
        p.setColor(Theme.GOLD);
        c.drawCircle(s / 2, s / 2, s * 0.46f, p);
        p.setColor(0xFFC8921B);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.06f);
        c.drawCircle(s / 2, s / 2, s * 0.36f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF8A5A0E);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(s * 0.5f);
        c.drawText("₺", s / 2, s * 0.67f, p);
        p.setFakeBoldText(false);
    }

    public static void star(Canvas c, float cx, float cy, float r, Paint p) {
        Path st = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float px = cx + (float) Math.cos(a) * rr;
            float py = cy + (float) Math.sin(a) * rr;
            if (i == 0) st.moveTo(px, py); else st.lineTo(px, py);
        }
        st.close();
        c.drawPath(st, p);
    }

    private static void clock(Canvas c, float s, Paint p) {
        p.setColor(Theme.CREAM);
        c.drawCircle(s / 2, s / 2, s * 0.46f, p);
        p.setColor(Theme.DARK);
        c.drawCircle(s / 2, s / 2, s * 0.38f, p);
        p.setColor(Theme.CREAM);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.07f);
        c.drawLine(s / 2, s / 2, s / 2, s * 0.22f, p);
        c.drawLine(s / 2, s / 2, s * 0.68f, s * 0.60f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.GOLD);
        c.drawCircle(s / 2, s / 2, s * 0.05f, p);
    }

    private static void bag(Canvas c, float s, Paint p) {
        Path r = new Path();
        r.moveTo(s * 0.18f, s * 0.06f);
        r.lineTo(s * 0.82f, s * 0.06f);
        r.lineTo(s * 0.82f, s * 0.94f);
        r.lineTo(s * 0.70f, s * 0.86f);
        r.lineTo(s * 0.58f, s * 0.94f);
        r.lineTo(s * 0.46f, s * 0.86f);
        r.lineTo(s * 0.34f, s * 0.94f);
        r.lineTo(s * 0.18f, s * 0.86f);
        r.close();
        p.setColor(Theme.CREAM);
        c.drawPath(r, p);
        p.setColor(Theme.ORANGE_DEEP);
        for (int i = 0; i < 4; i++) c.drawRoundRect(new RectF(s * 0.28f, s * (0.20f + i * 0.16f), s * (i == 3 ? 0.50f : 0.72f), s * (0.26f + i * 0.16f)), s * 0.02f, s * 0.02f, p);
    }
}
