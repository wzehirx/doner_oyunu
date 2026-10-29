package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Teslimde yapılan malzeme doğrulaması sonucu (kalem kalem). */
public final class ToppingCheck {
    /** Tek hatalı malzeme. */
    public static final class Mistake {
        public final String ingredientId;
        /** true: fazladan konmuş; false: eksik. */
        public final boolean extra;
        /** Müşterinin açık isteğiyle ilgili mi (istek: soğansız -> soğan konursa requested=true). */
        public final boolean requested;
        public final String productName;

        public Mistake(String ingredientId, boolean extra, boolean requested, String productName) {
            this.ingredientId = ingredientId;
            this.extra = extra;
            this.requested = requested;
            this.productName = productName;
        }
    }

    private final List<Mistake> mistakes = new ArrayList<>();
    /** Malzeme seçimi gereken kalem sayısı. */
    int toppingLines;

    public void add(Mistake m) { mistakes.add(m); }

    public List<Mistake> getMistakes() { return Collections.unmodifiableList(mistakes); }
    public int mistakes() { return mistakes.size(); }
    public int getToppingLines() { return toppingLines; }
    /** Malzeme seçimi vardı ve hepsi doğru. */
    public boolean perfect() { return toppingLines > 0 && mistakes.isEmpty(); }
}
