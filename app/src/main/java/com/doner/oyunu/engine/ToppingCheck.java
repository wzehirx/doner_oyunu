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

    /** Boy hatası: istenenden farklı boyda dilimlenmiş kalem. */
    public static final class SizeMistake {
        public final String productName;
        public final PortionSize ordered;
        public final PortionSize served;

        public SizeMistake(String productName, PortionSize ordered, PortionSize served) {
            this.productName = productName;
            this.ordered = ordered;
            this.served = served;
        }

        /** true: istenenden büyük (çok fazla), false: küçük (çok az). */
        public boolean tooMuch() { return served.ordinal() > ordered.ordinal(); }
    }

    private final List<Mistake> mistakes = new ArrayList<>();
    private final List<SizeMistake> sizeMistakes = new ArrayList<>();
    /** Döner boyu seçilmesi gereken kalem sayısı. */
    int sizedLines;
    /** Malzeme seçimi gereken kalem sayısı. */
    int toppingLines;

    public void add(Mistake m) { mistakes.add(m); }

    public void addSize(SizeMistake m) { sizeMistakes.add(m); }

    /** Malzeme (eksik/fazla) hataları. */
    public List<Mistake> getMistakes() { return Collections.unmodifiableList(mistakes); }
    /** Boy hataları. */
    public List<SizeMistake> getSizeMistakes() { return Collections.unmodifiableList(sizeMistakes); }
    /** Toplam hata sayısı: malzeme hataları + boy hataları (ceza ve bahşiş bu sayıya göre). */
    public int mistakes() { return mistakes.size() + sizeMistakes.size(); }
    public int ingredientMistakes() { return mistakes.size(); }
    public int sizeMistakeCount() { return sizeMistakes.size(); }
    public int getSizedLines() { return sizedLines; }
    public int getToppingLines() { return toppingLines; }
    /** Malzeme seçimi vardı ve hepsi doğru. */
    public boolean perfect() { return (toppingLines > 0 || sizedLines > 0) && mistakes() == 0; }
}
