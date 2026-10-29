package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Malzeme ekleme sesleri (saf Java). Tek tek eklemede ve otomatik eklemede (Rahat/Dengeli "Malzemeleri ekle") her
 * malzeme kendi sesini çalar: domates = sfx_veg_tomato, marul/soğan ve diğer salata = sfx_veg_lettuce, sos = sfx_sauce.
 * Otomatik işte malzemeler sırayla, ekleme süresine yayılarak (salata 0,6 sn, sos 0,5 sn oranında) çalınır.
 */
public final class ToppingSfx {
    private ToppingSfx() {}

    public static final String TOMATO = "sfx_veg_tomato";
    public static final String LETTUCE = "sfx_veg_lettuce";
    public static final String SAUCE = "sfx_sauce";

    /** Bir malzemenin ekleme sesi. */
    public static String sfxFor(Ingredient ing) {
        if (ing == null || ing.category != Ingredient.Category.SALATA) return SAUCE;
        return "domates".equals(ing.id) ? TOMATO : LETTUCE;
    }

    /** Plandaki bir vuruş: işin başından offsetMs sonra sfx çalınır. */
    public static final class Cue {
        public final long offsetMs;
        public final String sfx;
        public final String ingredientId;

        Cue(long offsetMs, String sfx, String ingredientId) {
            this.offsetMs = offsetMs;
            this.sfx = sfx;
            this.ingredientId = ingredientId;
        }
    }

    private static double weight(Ingredient ing) { return ing != null && ing.category == Ingredient.Category.SALATA ? 0.6 : 0.5; }

    /**
     * Otomatik işin ses planı: her malzeme için bir vuruş, malzemenin ekleme payına göre toplam süreye yayılmış (ilk vuruş
     * işin başında). Süre boş/negatifse ya da malzeme yoksa boş liste.
     */
    public static List<Cue> plan(List<Ingredient> adding, float seconds) {
        List<Cue> out = new ArrayList<>();
        if (adding == null || adding.isEmpty() || seconds <= 0f) return out;
        double sum = 0;
        for (Ingredient i : adding) sum += weight(i);
        double acc = 0;
        for (Ingredient i : adding) {
            out.add(new Cue(Math.round(seconds * 1000.0 * acc / sum), sfxFor(i), i == null ? null : i.id));
            acc += weight(i);
        }
        return out;
    }
}
