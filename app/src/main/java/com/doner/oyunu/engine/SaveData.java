package com.doner.oyunu.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Kalıcı oyun durumu + ayarlar. "anahtar=değer" düz metin olarak serileştirilir. */
public final class SaveData {
    public static final int START_MONEY = 5000;
    public static final double START_STARS = 3.0;

    public int day = 1;
    public int bestDay = 1;
    public int money = START_MONEY;
    public double stars = START_STARS;
    public Inventory inventory = new Inventory();
    public final Map<String, Integer> upgradeLevels = new LinkedHashMap<>();

    // Ayarlar
    public double tempo = 1.0;          // 0.6 - 1.6: süre ve sabır ölçeği
    public boolean speechOn = true;
    public double speechRate = 1.0;
    public boolean vibration = true;
    public double musicVolume = 0.7;
    public double sfxVolume = 1.0;
    public boolean tutorialDone = false;
    /** Çırak duyuruları (ayar). */
    public boolean apprenticeAnnounce = true;

    /** Oyun akışı (v0.7): Rahat / Dengeli / Gerçekçi. Eski kayıtlarda yoksa varsayılan (Dengeli). */
    public FlowMode flowMode = FlowMode.DEFAULT;
    /** Oyuncu oyun akışını bilerek seçti/onayladı mı (brifing ilk seferde sorar). */
    public boolean flowChosen = false;

    /**
     * Süre baskısı (v0.8): true (varsayılan; eski kayıtlar) = süreli gün, müşteri sabrı azalır. false = süresiz gün:
     * sayaç yok, sabır sabit, müşteri gitmez; kota kadar müşteri gelip tüm siparişler teslim edilince gün biter.
     */
    public boolean timePressure = true;

    /** Hazır (doldurulmuş) içecek stoğu; gün sonunda atılmaz, ertesi güne kalır. */
    public int readyDrinks = 0;

    /** Yeni oyun: 1. gün için önerilen stok hazır gelir. */
    public static SaveData newGame() {
        SaveData s = new SaveData();
        Shop.grantRecommended(s);
        return s;
    }

    public int level(String upgradeId) {
        Integer v = upgradeLevels.get(upgradeId);
        return v == null ? 0 : v;
    }

    public void setTempo(double t) { tempo = Math.max(0.6, Math.min(1.6, t)); }

    public SaveData copy() {
        SaveData c = parse(serialize());
        return c;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("v=4\n"); // v=1 (v0.4), v=2 (v0.5) ve v=3 (v0.6) kayıtları da yüklenir
        sb.append("day=").append(day).append('\n');
        sb.append("bestDay=").append(bestDay).append('\n');
        sb.append("money=").append(money).append('\n');
        sb.append("stars=").append(stars).append('\n');
        for (Map.Entry<String, Integer> e : inventory.asMap().entrySet()) {
            sb.append("stock.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, Integer> e : upgradeLevels.entrySet()) {
            if (e.getValue() > 0) sb.append("up.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        sb.append("tempo=").append(tempo).append('\n');
        sb.append("speech=").append(speechOn ? 1 : 0).append('\n');
        sb.append("speechRate=").append(speechRate).append('\n');
        sb.append("vibration=").append(vibration ? 1 : 0).append('\n');
        sb.append("music=").append(musicVolume).append('\n');
        sb.append("sfx=").append(sfxVolume).append('\n');
        sb.append("tutorial=").append(tutorialDone ? 1 : 0).append('\n');
        sb.append("apprenticeAnnounce=").append(apprenticeAnnounce ? 1 : 0).append('\n');
        sb.append("readyDrinks=").append(readyDrinks).append('\n');
        sb.append("flow=").append(flowMode.id).append('\n');
        sb.append("flowChosen=").append(flowChosen ? 1 : 0).append('\n');
        sb.append("timer=").append(timePressure ? 1 : 0).append('\n');
        return sb.toString();
    }

    /** NaN/Infinity kabul etmez (bozuk kayıt oyunu kilitlemesin); NumberFormatException fırlatır, çağıran satırı atlar. */
    private static double num(String v) {
        double d = Double.parseDouble(v);
        if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException(v);
        return d;
    }

    /** Hataya toleranslı yükleme: bozuk/eksik satırlar atlanır, null/boş = yeni oyun. */
    public static SaveData parse(String text) {
        if (text == null || text.trim().isEmpty()) return newGame();
        SaveData s = new SaveData();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            try {
                if (k.equals("day")) s.day = Math.max(1, Integer.parseInt(v));
                else if (k.equals("bestDay")) s.bestDay = Math.max(1, Integer.parseInt(v));
                else if (k.equals("money")) s.money = Math.max(0, Integer.parseInt(v));
                else if (k.equals("stars")) s.stars = Math.max(1.0, Math.min(5.0, num(v)));
                else if (k.startsWith("stock.")) {
                    String id = k.substring(6);
                    if (Catalog.ingredient(id) != null) s.inventory.set(id, Integer.parseInt(v));
                } else if (k.startsWith("up.")) {
                    String id = k.substring(3);
                    Upgrade u = Catalog.upgrade(id);
                    if (u != null) s.upgradeLevels.put(id, Math.max(0, Math.min(u.maxLevel(), Integer.parseInt(v))));
                } else if (k.equals("tempo")) s.setTempo(num(v));
                else if (k.equals("speech")) s.speechOn = v.equals("1");
                else if (k.equals("speechRate")) s.speechRate = Math.max(0.5, Math.min(2.0, num(v)));
                else if (k.equals("vibration")) s.vibration = v.equals("1");
                else if (k.equals("music")) s.musicVolume = Math.max(0, Math.min(1, num(v)));
                else if (k.equals("sfx")) s.sfxVolume = Math.max(0, Math.min(1, num(v)));
                else if (k.equals("tutorial")) s.tutorialDone = v.equals("1");
                else if (k.equals("apprenticeAnnounce")) s.apprenticeAnnounce = v.equals("1");
                else if (k.equals("flow")) { FlowMode m = FlowMode.byId(v); if (m != null) s.flowMode = m; }
                else if (k.equals("flowChosen")) s.flowChosen = v.equals("1");
                else if (k.equals("timer")) s.timePressure = !v.equals("0");
                else if (k.equals("readyDrinks")) s.readyDrinks = Math.max(0, Math.min(40, Integer.parseInt(v)));
            } catch (NumberFormatException ignored) {
                // bozuk satır atlanır
            }
        }
        if (s.bestDay < s.day) s.bestDay = s.day;
        return s;
    }
}
