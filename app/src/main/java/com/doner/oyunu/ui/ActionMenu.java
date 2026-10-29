package com.doner.oyunu.ui;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.Button;

import com.doner.oyunu.engine.StationOption;

import java.util.ArrayList;
import java.util.List;

/**
 * İstasyon / hazırlık düğmelerine TalkBack ÖZEL EYLEMLERİ ekler: her {@link StationOption} için bir
 * {@link AccessibilityAction} (Türkçe, durumlu etiket). Eylem seçilince {@link Handler#onAction} aynı
 * {@code engine.applyStationOption} yolunu çalıştırır (seçenek paneliyle ortak).
 * Eylem listesi düğme erişilebilirlik düğümü her oluşturulduğunda motordan yeniden hesaplanır; durum değişince
 * {@link #invalidate} ile düğüm yenilenir.
 */
final class ActionMenu {
    private ActionMenu() {}

    /** Özel eylem kimliklerinin tabanı: üst bayt sıfır olmamalı (platform kuralı), standart kimliklerle çakışmaz. */
    static final int ACTION_BASE = 0x7F520000;

    interface Source {
        /** Düğmenin şu anki seçenek listesi (boş olabilir). */
        List<StationOption> options();
    }

    interface Handler {
        /** Eylem seçildi: seçenek kimliğiyle motoru çağır. */
        void onAction(String optionId);
    }

    /** Düğmeye eylem menüsünü bağlar (düğmenin sınıf adı "Button" olarak kalır). */
    static void attach(View host, Source src, Handler h) {
        host.setAccessibilityDelegate(new Delegate(src, h));
    }

    /** Eylem listesi değişti: düğmenin erişilebilirlik düğümünü tazelemesi için olay gönderir. */
    static void invalidate(View host) {
        host.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    /** Seçenek listesinin parmak izi (etiketler dahil): değişirse eylem menüsü tazelenir. */
    static String signature(List<StationOption> ops) {
        StringBuilder sb = new StringBuilder();
        for (StationOption op : ops) sb.append(op.id).append('=').append(op.label()).append('|');
        return sb.toString();
    }

    private static final class Delegate extends View.AccessibilityDelegate {
        private final Source src;
        private final Handler handler;
        /** Son düğümde sunulan eylemlerin seçenek kimlikleri (eylem kimliği = ACTION_BASE + dizin). */
        private final List<String> ids = new ArrayList<>();

        Delegate(Source src, Handler handler) {
            this.src = src;
            this.handler = handler;
        }

        @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
            List<StationOption> ops = safe();
            ids.clear();
            for (int i = 0; i < ops.size(); i++) {
                StationOption op = ops.get(i);
                info.addAction(new AccessibilityAction(ACTION_BASE + i, op.label()));
                ids.add(op.id);
            }
        }

        @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
            int i = action - ACTION_BASE;
            if (i >= 0 && i < 4096) {
                String id = i < ids.size() ? ids.get(i) : null;
                if (id == null) {
                    List<StationOption> ops = safe();
                    if (i < ops.size()) id = ops.get(i).id;
                }
                if (id == null) return false;
                handler.onAction(id);
                return true;
            }
            return super.performAccessibilityAction(host, action, args);
        }

        private List<StationOption> safe() {
            try {
                List<StationOption> l = src.options();
                return l == null ? new ArrayList<StationOption>() : l;
            } catch (RuntimeException e) {
                return new ArrayList<>();
            }
        }
    }
}
