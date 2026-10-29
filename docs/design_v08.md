# v0.8 – Süre baskısı ayarı ve sadeleşen gün ekranı

Kaynak: görme engelli oyuncuların geri bildirimi: (1) "Zamanlayıcı bazılarını zorluyor; isteyen kapatsın, tüm siparişler tamamlanınca gün bitsin." (2) "Gün başlayınca hazırlık butonları kaybolsun; istasyonlarda yapabiliyoruz, buton karmaşasından kurtulalım." v0.7 ve öncesi belgeler geçerlidir; çakışmada bu belge kazanır.

## 1. Süre baskısı (`SaveData.timePressure`, varsayılan açık)
Ayarlar > "Süre baskısı: Açık/Kapalı" (tek büyük aç/kapa düğmesi; durum etikette ve açıklamada: "Süre baskısı: kapalı. Açmak için dokun. …"), oyun içinde Duraklat menüsünde aynı düğme (`engine.setTimePressure`). Kayıt: `timer=1|0` (`v=4` kaldı; eski kayıtlarda anahtar yok = açık = süreli; bozuk değer süreli). Yeni oyun ayarı korur.

| | Süre baskısı AÇIK (eski davranış) | KAPALI (süresiz gün) |
|---|---|---|
| Sayaç | gün süresi + 30 sn mesai | **yok**; durum çubuğu "Kalan müşteri: N", "Durumu oku" "kalan müşteri N" der |
| Sabır | azalır, uyarılar/sesler | **sabit** (azalmaz), uyarı/ses yok, kart ve açıklamada sabır yok |
| Müşteri gitmesi | sabır bitince gider (-0,30 yıldız) | **gitmez** |
| Müşteri gelişi | gün süresi boyunca, yıldız/yükseltme çarpanlı | kota dolana dek; sıra boşaldıkça (boş sırada en çok 2,5 sn sonra) ya da normal aralığın ~0,8'iyle; bekleyen sipariş üst sınırı (`baseQueue + queueBonus`) **korunur** |
| Gün bitişi | süre dolup sıra boşalınca (ya da mesai bitince) | `kota kadar müşteri geldi` **ve** tüm siparişler teslim edildi |
| Teslim memnuniyeti | hızlı +0,12 / memnun +0,08 / yavaş +0,02 | her teslim **+0,08** (hız bonusu yok; kusursuz bonusu aynen) |
| Bahşiş | %20 hızlı / %8 memnun, x tip x yükseltme x akış | sabit %10 **x 0,8** (= %8) x tip x yükseltme x akış |
| Satış geliri, kira | normal | normal (satış fiyatı ve kira aynı) |
| Hata cezaları | yanlış istasyon -0,02; hatalı sipariş -0,05/hata (tavan -0,15), bahşiş yarıya | **aynen** |
| Gün başarısızlığı | yıldız < 1,0 | yıldız < 1,0 (süresizde yalnızca hatalardan olur: kaybolan müşteri yok) |

**Kota** (`DayConfig.quota`): `normal gün süresi / geliş aralığı x yükseltme çarpanı` (restoran yükseltmelerinin `arrivalMult`'u, en çok x1,25; tabela/müzik/masa süresizde de anlamlı kalsın), en az 5, en çok 60. 1. gün 7, 10. gün ~12, 20. gün ~23, 40. gün 54 (yükseltmeyle 60). Brifing ve motor aynı değeri kullanır (`DayConfig.quotaFor(save)`). Senaryo/öğretici siparişleri (`spawnOrder`) kotaya sayılmaz.

**Ekonomi kararı.** Süresiz oynayan, süreli oynayana göre biraz az kazanmalı ki süreli oynayan haksız duruma düşmesin; ama ceza gibi hissetmemeli. Ölçüm (`UntimedSimulationTest`, hazırlık+yükseltmeli bot, Dengeli, 15 gün): süreli 249 teslim / bahşiş-satış %12,4 / kasa 9 210; süresiz 180 teslim / %5,4 / kasa 7 549 (süreli kazancın ~%82'si). Farkın kaynakları: hız bonusu ve %20'lik hızlı bahşişin gitmesi (bahşiş çarpanı 0,8 ve sabit %10 taban), yıldız çarpanıyla artan müşteri sayısının kotada olmaması. Testler süresiz kazancın süreliden düşük ama en az %70 olduğunu doğrular.

**Gün uzunluğu.** Kota gün süresine bağlı olduğundan 40. günde 54 müşteri demektir (bot ~24 dk sanal, insanda daha uzun). Bu bilinçli: tek kural "normal sürenin getireceği müşteri kadar". Uzun gün sorun olursa tek sabit `DayConfig.QUOTA_FACTOR` (şimdi 1,0) ile ayarlanır.

**Gün ortasında değiştirme.** Kapatınca sayaç durur, bekleyenlerin sabrı donar, kalan kota kadar müşteri gelir; kota zaten dolmuş ve sıra boşsa gün biter. Açınca gün saati, o ana dek gelen müşteri oranından (en çok %90) geri kalmaz; sabır kaldığı yerden azalmaya başlar. `onTimePressureChanged(timed)` olayı Narrator'da duyurulur: "Süre baskısı: kapalı. … Kalan müşteri: N." / "açık … Kalan süre X saniye."

**Metinler.** Brifing: "Bu gün süre yok, N müşteri. …" + "Süre baskısı: kapalı/açık. … Ayarlardan, oyun sırasında Duraklat menüsünden değiştirebilirsin." Gün başı (Narrator): "Gün 3 başladı. Bu gün süre yok, 12 müşteri. Müşteriler beklemekten gitmez; hepsi teslim edilince gün biter." Gün sonu: "… sipariş teslim, süre baskısı kapalıydı." (kayıp müşteri cümlesi yok); özet satırı "Süre baskısı: kapalıydı; müşteri gitmedi". Yardım: yeni "Süre baskısı" bölümü, `quickHelp(mode, timed)`, `tutEnd(timed)`.

**Çırak, hazırlık, akış modları** süresizde aynı çalışır (çırak boşta hazırlık israfını önlemek için "kalan süre" yerine kalan kota x geliş aralığını kullanır). Botlar Rahat/Dengeli/Gerçekçi x çırak x hazırlıkla süresiz 1–40'ı geçer.

**Bilinen sınır.** Süresiz günde stok ve para bittiyse (ve acil alım karşılanamıyorsa) gün kendiliğinden başarısız olmaz; oyuncu Duraklat > çıkıştan ayrılabilir (gün kaydı yazılmaz). Süreli modda da aynı para/stok kilitlenmesi vardı (başarısız gün aynı duruma döner). Bu sürümde değiştirilmedi.

## 2. Gün içinde hazırlık düğmeleri kalktı
- `GameScreen`: hazırlık bölümü (Döner dilimle / Ayran doldur / Salata ve sos tepsisi hazırla düğmeleri, "Hazır döner/ayran/tepsi" göstergeleri, "Hazır stoğu oku") **yalnızca PREP/READY aşamasında** görünür; gün (RUNNING) başlayınca `GONE`.
- Gün içi hazırlık **yalnızca istasyon düğmesinin Eylemler menüsünden / seçenekler panelinden** (`prep:*`, v0.6): DONER `prep:doner:<et>:4|2`, ICECEK `prep:drink`, SOS `prep:tray`. Sipariş yokken tek hazırlık seçeneği olan istasyon (İçecek) doğrudan uygular, birden çok seçenek (Döner) panel açar; sipariş bekleyen istasyonda TalkBack Eylemleri menüsünde bulunur (dokunuş o siparişin adımını yapar).
- Hazır stok bilgisi kalır: "Durumu oku" ("Hazır: döner 3,5 porsiyon, …") ve istasyon düğmesi etiketinde ("Döner makinesi istasyonu, …, hazır 3,5 porsiyon", "…, hazır 3 bardak ayran", "…, hazır tepsi 6 kullanım").
- Öğretici (`TUT_OPEN`), brifing, `quickHelp`, yardım "Gün öncesi hazırlık" bölümü bu davranışa göre güncellendi.

## 3. Testler
`TimePressureTest` (motor: kayıt uyumu, kota, sabır sabit/gitme yok, hiçbir şey yapmayan bot 10 dk sanal sürede günü bitiremez ve sıra üst sınırda, kota+teslim ile bitiş, ekonomi, ceza/başarısızlık, gün ortası geçiş, Narrator, çırak+hazırlık+üç akış, gün içi `prep:*`), `UntimedSimulationTest` (üç akışta gün 1–40, insan benzeri bot, yükseltmesiz bot, ekonomi, 40 günün hepsinde "hiçbir şey yapma" botu), `TimePressureUiTest` (Ayarlar düğmesi, Duraklat, brifing, kalan müşteri görünümü, hazırlık bölümünün gün içinde yokluğu, süresiz günün baştan sona oynanması), `PrepToppingUiTest`/`A11yTest` (istasyondan hazırlık), `A11yAudit` yeni kurallar (aç/kapa düğmesi durumu etikette; gün içinde hazırlık bölümü yok; süresizde sabır/süre metni yok). Bot güvenliği: `TestBot` süreli günde 1000, süresiz günde 6000 sanal saniye ile sınırlıdır; `Campaign` günü bitirememeyi "geçti" saymaz.
