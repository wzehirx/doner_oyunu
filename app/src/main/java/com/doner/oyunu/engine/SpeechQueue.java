package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Konuşma sırası (v0.9). Saf Java, saat ve oynatıcı dışarıdan verilir, bu yüzden JVM'de ölçülebilir.
 * Kurallar:
 * <ul>
 * <li><b>Bayat duyuru okunmaz:</b> sırada bekleyen acil olmayan duyuru {@link #STALE_MS} (kategorisizde {@link #GENERIC_STALE_MS})
 * içinde söylenemezse atılır; yeni bir olay geldiğinde de eskiler elenir. Duyuru ayrıca söylenmeden hemen önce
 * {@link Narrator.Fresh} ile yeniden denetlenir (oyuncu o adımı çoktan başlattıysa söylenmez).</li>
 * <li><b>Aynı kategori birleşir:</b> aynı kategoriden bekleyen eski duyuru yenisi gelince atılır (yalnızca sonuncusu okunur).
 * Aynı kategoriden iki duyuru en az {@link #MIN_GAP_MS} arayla başlar.</li>
 * <li><b>Acil olanlar öne geçer:</b> acil duyuru bekleyen acil olmayanları siler ve konuşan acil olmayan duyuruyu keser;
 * konuşan duyuru da gerçek acilse (hata/uyarı, sabır kritik) sırada ilk bekler (en çok {@link #MAX_URGENT_PENDING});
 * seçim, bilgi okuma, ayar değişimi gibi oyuncunun kendi eylemine yanıtlar ve bilgi duyuruları kesilebilir.</li>
 * <li>Uzun metin ({@link #sayLong}, yardım okuma) hiçbir zaman bayatlamaz ve oyun anlatımıyla atılmaz.</li>
 * </ul>
 */
public final class SpeechQueue implements Narrator.Output {
    /** Sistem saati (ms). */
    public interface Clock { long now(); }

    /** Konuşmayı gerçekten yapan taraf (TalkBack duyurusu ya da TTS). Dönüş: beklenen süre (ms). */
    public interface Player { long play(String text, boolean interrupt); }

    public static final long STALE_MS = 3500;
    public static final long GENERIC_STALE_MS = 8000;
    public static final long URGENT_STALE_MS = 6000;
    /** Öğretici ipucu zamana göre değil, oyuncu eylemine göre bayatlar ({@link Narrator.Fresh}); zaman sınırı geniştir. */
    public static final long TUTORIAL_STALE_MS = 20000;
    public static final long MIN_GAP_MS = 1200;
    public static final int MAX_PENDING = 4;
    public static final int MAX_URGENT_PENDING = 2;

    private static final class Item {
        final String text;
        final boolean urgent;
        final String category;
        final Narrator.Fresh fresh;
        final long created;
        final boolean keep;

        Item(String text, boolean urgent, String category, Narrator.Fresh fresh, long created, boolean keep) {
            this.text = text;
            this.urgent = urgent;
            this.category = category;
            this.fresh = fresh;
            this.created = created;
            this.keep = keep;
        }

        long staleMs() {
            if (keep) return Long.MAX_VALUE;
            if (urgent) return URGENT_STALE_MS;
            if (Narrator.CAT_TUTORIAL.equals(category)) return TUTORIAL_STALE_MS;
            return category == null ? GENERIC_STALE_MS : STALE_MS;
        }
    }

    private final Clock clock;
    private final Player player;
    private final List<Item> pending = new ArrayList<>();
    private final Map<String, Long> lastStart = new HashMap<>();
    private long busyUntil;
    /** Konuşan duyuru yenisi tarafından kesilebilir mi (acil olmayan ya da oyuncunun kendi eyleminin yanıtı: seçim, bilgi okuma). */
    private boolean currentInterruptible = true;
    private int dropped;
    private int spoken;

    public SpeechQueue(Clock clock, Player player) {
        this.clock = clock;
        this.player = player;
    }

    // ------------------------------------------------------------------ Narrator.Output
    @Override public void say(String text, boolean urgent) { say(text, urgent, null, null); }

    @Override public void say(String text, boolean urgent, String category, Narrator.Fresh fresh) {
        if (text == null || text.isEmpty()) return;
        long now = clock.now();
        purge(now);
        Item it = new Item(text, urgent, category, fresh, now, false);
        if (category != null) {
            for (Iterator<Item> i = pending.iterator(); i.hasNext(); ) {
                Item p = i.next();
                if (!p.keep && category.equals(p.category) && p.urgent == urgent) { i.remove(); dropped++; }
            }
        }
        if (urgent) {
            for (Iterator<Item> i = pending.iterator(); i.hasNext(); ) {
                Item p = i.next();
                if (!p.keep && !p.urgent) { i.remove(); dropped++; }
            }
            if (now >= busyUntil || currentInterruptible) { // boşta ya da kesilebilir bir duyuru konuşuyor: hemen (kes)
                start(it, now, now < busyUntil);
                return;
            }
            addPending(it);
            capUrgent();
        } else {
            addPending(it);
            capNormal();
        }
        pump(now);
    }

    /** Uzun metin (yardım): bayatlamaz, oyun anlatımıyla atılmaz. flush: bekleyen her şeyi siler ve hemen söyler. */
    public void sayLong(String text, boolean flush) {
        if (text == null || text.isEmpty()) return;
        long now = clock.now();
        Item it = new Item(text, false, null, null, now, true);
        if (flush) {
            dropped += pending.size();
            pending.clear();
            start(it, now, now < busyUntil);
            return;
        }
        pending.add(it);
        pump(now);
    }

    // ------------------------------------------------------------------ sıra
    private void addPending(Item it) { pending.add(it); }

    private void capNormal() {
        int n = 0;
        for (Item p : pending) if (!p.keep && !p.urgent) n++;
        while (n > MAX_PENDING) {
            for (Iterator<Item> i = pending.iterator(); i.hasNext(); ) {
                Item p = i.next();
                if (!p.keep && !p.urgent) { i.remove(); dropped++; n--; break; }
            }
        }
    }

    private void capUrgent() {
        int n = 0;
        for (Item p : pending) if (p.urgent) n++;
        while (n > MAX_URGENT_PENDING) {
            for (Iterator<Item> i = pending.iterator(); i.hasNext(); ) {
                Item p = i.next();
                if (p.urgent) { i.remove(); dropped++; n--; break; }
            }
        }
    }

    /** Bayatlamış duyuruları siler. */
    private void purge(long now) {
        for (Iterator<Item> i = pending.iterator(); i.hasNext(); ) {
            Item p = i.next();
            if (now - p.created > p.staleMs()) { i.remove(); dropped++; }
        }
    }

    private boolean gapBlocked(Item p, long now) {
        if (p.urgent || p.category == null) return false;
        Long last = lastStart.get(p.category);
        return last != null && now - last < MIN_GAP_MS;
    }

    /** Konuşma boşsa sıradaki uygun duyuruyu söyler. */
    public void pump(long now) {
        if (now < busyUntil) return;
        purge(now);
        Item pick = null;
        for (int pass = 0; pass < 2 && pick == null; pass++) {
            boolean wantUrgent = pass == 0;
            for (Iterator<Item> i = pending.iterator(); i.hasNext(); ) {
                Item p = i.next();
                if (p.urgent != wantUrgent) continue;
                if (p.fresh != null && !p.fresh.ok()) { i.remove(); dropped++; continue; }
                if (gapBlocked(p, now)) continue;
                i.remove();
                pick = p;
                break;
            }
        }
        if (pick != null) start(pick, now, false);
    }

    private void start(Item it, long now, boolean interrupt) {
        if (it.fresh != null && !it.fresh.ok()) { dropped++; return; }
        if (it.category != null) lastStart.put(it.category, now);
        // Yalnızca gerçek acil duyurular (hata/uyarı, sabır kritik) başka acil duyuruyla kesilmez; oyuncunun kendi eylemine
        // yanıt olan duyurular (seçim, bilgi okuma, ayar değişimi, arayüz geri bildirimi) ve bilgi niteliğindeki acil
        // duyurular (hazırlık aşaması, gün sonu özeti) yenisiyle kesilir.
        currentInterruptible = !it.urgent || !(Narrator.CAT_ALERT.equals(it.category) || Narrator.CAT_PATIENCE.equals(it.category));
        spoken++;
        long ms = player.play(it.text, interrupt);
        busyUntil = now + Math.max(0L, ms);
    }

    /** Oynatıcı konuşmanın bittiğini bildirdi (TTS): sıradaki hemen söylenebilir. */
    public void finished(long now) {
        if (busyUntil > now) busyUntil = now;
        pump(now);
    }

    /** Her şeyi susturur (arka plana gitme, Duraklat). */
    public void clear() {
        dropped += pending.size();
        pending.clear();
        busyUntil = 0;
        currentInterruptible = true;
    }

    /** Şu an konuşuyor ya da sırada duyuru var mı. */
    public boolean isBusy(long now) { return now < busyUntil || !pending.isEmpty(); }

    public boolean isSpeaking(long now) { return now < busyUntil; }

    public int pendingCount() { return pending.size(); }

    /** Bir sonraki {@link #pump} için en erken beklenecek süre (ms); sırada bir şey yoksa ve konuşma bittiyse -1. */
    public long nextWakeup(long now) {
        long best = -1;
        if (now < busyUntil) best = busyUntil - now;
        if (pending.isEmpty()) return best;
        for (Item p : pending) {
            long d = 0;
            if (!p.urgent && p.category != null) {
                Long last = lastStart.get(p.category);
                if (last != null) d = Math.max(0L, last + MIN_GAP_MS - now);
            }
            if (now < busyUntil) d = Math.max(d, busyUntil - now);
            if (best < 0 || d < best) best = d;
        }
        return Math.max(20L, best);
    }

    /** Atılan duyuru sayısı (bayat, birleşen, kesilen); ölçüm içindir. */
    public int droppedCount() { return dropped; }

    /** Söylenen duyuru sayısı. */
    public int spokenCount() { return spoken; }
}
