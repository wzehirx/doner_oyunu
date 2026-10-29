package com.doner.oyunu.engine;

/**
 * Oyun akışı (v0.7): malzeme ve döner boyu için ne kadarının otomatik yapılacağı. Ayarlardan, brifingten ve
 * duraklatma menüsünden değiştirilir; oyun ortasında değişebilir; {@link SaveData#flowMode} ile kaydedilir.
 * <ul>
 * <li>{@link #RAHAT}: malzemeler (müşteri istekleri dahil) ve boy otomatik. Her istasyon tek dokunuş (v0.4 hissi).</li>
 * <li>{@link #DENGELI} (varsayılan): standart olan otomatik, farklı olan elle. Varsayılan malzemeler tek dokunuşla
 *     eklenir; müşteri isteği varsa farkı (soğan çıkar, acı sos ekle) oyuncu yapar. Tam ekmek otomatik; yarım
 *     ekmek / duble istenince boyu oyuncu seçer.</li>
 * <li>{@link #GERCEKCI}: v0.6 akışı: her malzeme tek tek, boy her dilimlemede seçilir. Hafif ödül: bahşiş +%15.</li>
 * </ul>
 */
public enum FlowMode {
    RAHAT("rahat", "Rahat",
            "Malzemeler ve döner boyu otomatik. Müşteri isteklerini de sistem uygular; her istasyona bir kez dokunman yeter.",
            1.0, 1.0, 1.0),
    DENGELI("dengeli", "Dengeli",
            "Varsayılan malzemeler ve tam ekmek otomatik. Müşteri isteği varsa farkı sen yaparsın, örneğin soğan çıkar ya da acı sos ekle; yarım ekmek ya da duble istenince boyu sen seçersin.",
            1.0, 1.0, 1.0),
    GERCEKCI("gercekci", "Gerçekçi",
            "Her malzemeyi tek tek eklersin ve her dilimlemede boyu sen seçersin. En çok emek isteyen akış; müşteriler el emeğine saygıyla biraz daha sabırlı ve seyrek gelir, bahşişler yüzde on beş fazla.",
            1.15, 1.15, 0.9); // sabır ve geliş çarpanları: v0.7 yumuşatma, denge testleriyle ayarlandı

    public static final FlowMode DEFAULT = DENGELI;

    public final String id;
    /** "Rahat" / "Dengeli" / "Gerçekçi". */
    public final String displayName;
    /** Sesli/yazılı kısa açıklama (ayarlar, brifing, mod değişimi duyurusu). */
    public final String description;
    /** Bahşiş çarpanı (emeğe küçük ödül). */
    public final double tipMult;
    /** Müşteri sabrı çarpanı (Gerçekçi: daha fazla elle iş). */
    public final double patienceMult;
    /** Müşteri geliş sıklığı çarpanı (Gerçekçi: daha az; mutfak temposu el emeğine göre). */
    public final double arrivalMult;

    FlowMode(String id, String displayName, String description, double tipMult, double patienceMult, double arrivalMult) {
        this.patienceMult = patienceMult;
        this.arrivalMult = arrivalMult;
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.tipMult = tipMult;
    }

    /** Malzeme adımında tek dokunuşluk "auto" seçeneği sunulur mu (Rahat: hedef küme, Dengeli: varsayılan küme). */
    public boolean autoToppings() { return this != GERCEKCI; }

    /** Otomatik ekleme müşteri isteklerini de uygular mı (yalnızca Rahat). */
    public boolean autoRequests() { return this == RAHAT; }

    /** Bu boyda istenen kalemde dilimleme boyu otomatik mi (Rahat: hep; Dengeli: yalnızca tam ekmek). */
    public boolean autoSize(PortionSize ordered) {
        if (this == RAHAT) return true;
        return this == DENGELI && ordered == PortionSize.FULL;
    }

    /** Sıradaki mod (Rahat → Dengeli → Gerçekçi → Rahat); tek düğmeyle değiştirmek için. */
    public FlowMode next() { return values()[(ordinal() + 1) % values().length]; }

    /** "Oyun akışı: Dengeli. <açıklama>" */
    public String spoken() { return "Oyun akışı: " + displayName + ". " + description; }

    public static FlowMode byId(String id) {
        if (id == null) return null;
        for (FlowMode m : values()) if (m.id.equals(id.trim())) return m;
        return null;
    }
}
