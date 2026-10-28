# Döner Dükkanı: Oyun Tasarım Araştırma Notu

> Çalışma adı: **Döner Dükkanı** (Android, tek oyunculu dükkan simülasyonu)
> Kapsam: ürünler, malzemeler, yükseltmeler, ekonomi eğrisi, müşteriler, yıldız sistemi, erişilebilirlik, ses tasarımı.
> Not: Veriler tasarım tahminidir. Web araştırması yapılmadı. Fiyatlar oyun içi TL'dir, enflasyon ve bölgesel farklar modellenmemiştir.
> Zaman ölçeği: 1 oyun günü = 6 gerçek dakika. Hazırlama süreleri oyun saniyesidir.

---

## 1. Ürünler (12)

### 1.1 Özet tablo

| ID | Ürün | Açılış (gün) | Satış (TL) | Malzeme (TL) | Marj | Hazırlama (sn) |
|---|---|---|---|---|---|---|
| U01 | Ekmek Arası Et Döner | 1 | 240 | 96 | %60 | 12 |
| U02 | Ayran | 1 | 45 | 14 | %69 | 2 |
| U03 | Kutu İçecek | 2 | 55 | 22 | %60 | 1 |
| U04 | Ekmek Arası Tavuk Döner | 3 | 190 | 54 | %72 | 10 |
| U05 | Pilav Üstü Döner | 5 | 320 | 138 | %57 | 14 |
| U06 | Porsiyon Et Döner | 7 | 380 | 161 | %58 | 13 |
| U07 | Lahmacun | 9 | 120 | 34 | %72 | 9 (+20 fırın) |
| U08 | Tavuk Dürüm | 11 | 210 | 61 | %71 | 12 |
| U09 | Hatay Usulü Dürüm | 13 | 260 | 94 | %64 | 16 |
| U10 | Zurna Dürüm | 16 | 340 | 142 | %58 | 18 |
| U11 | İskender | 19 | 420 | 163 | %61 | 22 |
| U12 | Künefe | 23 | 240 | 66 | %73 | 20 |

### 1.2 Malzeme ve hazırlama detayı

| ID | Ürün | Gerekli malzemeler (miktar) | Hazırlama adımları |
|---|---|---|---|
| U01 | Ekmek Arası Et Döner | Döner ekmeği 1 adet, et döner 100 g, domates 30 g, soğan 15 g, süzme yoğurt 30 g | 1) Ekmeği ızgarada 4 sn ısıt. 2) Et dönerden 100 g dilimle. 3) Ekmeğe yoğurt sür, et ve domates-soğanı yerleştir. 4) Ekmeği kapat, kağıda sar. |
| U02 | Ayran | Ayran şişe 1 adet | 1) Buzdolabından şişeyi al. 2) Bardağa koy. 3) Teslim et. |
| U03 | Kutu İçecek | Kutu içecek 1 adet | 1) Buzdolabından al. 2) Teslim et. |
| U04 | Ekmek Arası Tavuk Döner | Döner ekmeği 1 adet, tavuk döner 110 g, marul 1/4 adet, domates 30 g, süzme yoğurt 30 g | 1) Ekmeği ısıt. 2) Tavuk dönerden 110 g dilimle. 3) Yoğurt sür, tavuk, marul ve domatesi ekle. 4) Ekmeği sar. |
| U05 | Pilav Üstü Döner | Pirinç 80 g, tereyağı 10 g, et döner 150 g | 1) Pilavı tabağa koy. 2) Tereyağını üstüne gezdir. 3) Et dönerden 150 g dilimle. 4) Pilavın üstüne yerleştir. |
| U06 | Porsiyon Et Döner | Döner ekmeği 1 adet, et döner 180 g, domates 40 g, soğan 20 g | 1) Ekmeği ısıt. 2) Et dönerden 180 g dilimle. 3) Domates ve soğanı tabağa diz. 4) Tabağı teslim et. |
| U07 | Lahmacun | Dürüm lavaşı 1 adet, lahmacun harcı 90 g, domates 30 g, soğan 15 g | 1) Lavaşa harcı yay. 2) Fırına ver (20 sn). 3) Çıkınca domates ve soğan ekle. 4) Sar veya katla. |
| U08 | Tavuk Dürüm | Dürüm lavaşı 1 adet, tavuk döner 130 g, marul 1/4 adet, domates 30 g, süzme yoğurt 40 g | 1) Lavaşı ısıt. 2) Tavuk dönerden 130 g dilimle. 3) Yoğurt, marul ve domatesi ekle. 4) Lavaşı sar. |
| U09 | Hatay Usulü Dürüm | Dürüm lavaşı 1 adet, et döner 80 g, lahmacun harcı 50 g, domates 40 g, soğan 30 g, marul 1/4 adet | 1) Lavaşı ısıt. 2) Harcı ve et dönerden 80 g ekle. 3) Soğan, domates ve marulu ekle. 4) Sıkı sar, kağıda al. |
| U10 | Zurna Dürüm | Dürüm lavaşı 1 adet, et döner 150 g, tereyağı 10 g, domates 30 g, süzme yoğurt 40 g | 1) Lavaşa tereyağı sür. 2) Et dönerden 150 g dilimle. 3) Yoğurt ve domatesi ekle. 4) Uzun sararak teslim et. |
| U11 | İskender | Döner ekmeği 1 adet, et döner 160 g, tereyağı 25 g, süzme yoğurt 80 g, domates 30 g | 1) Ekmeği küp doğra, tabağa diz. 2) Et dönerden 160 g dilimle, üstüne ser. 3) Sıcak tereyağı ve domates sosunu gezdir. 4) Yoğurdu kenara ekle. |
| U12 | Künefe | Tel kadayıf 120 g, künefe peyniri 80 g, tereyağı 25 g | 1) Kadayıfı tereyağı ile yoğur. 2) Yarısını tavaya yay, peyniri ser. 3) Üstünü kadayıfla kapat, fırında 20 sn pişir. 4) Ters çevirip servis et. |

---

## 2. Malzemeler (15)

| ID | Malzeme | Birim | Birim fiyat (TL) | Kullanıldığı ürünler |
|---|---|---|---|---|
| M01 | Et döner (karışık dana-kuzu) | kg | 850 | U01, U05, U06, U09, U10, U11 |
| M02 | Tavuk döner | kg | 360 | U04, U08 |
| M03 | Döner ekmeği | adet | 6 | U01, U04, U06, U11 |
| M04 | Dürüm lavaşı | adet | 5 | U07, U08, U09, U10 |
| M05 | Pirinç (pilavlık) | kg | 75 | U05 |
| M06 | Tereyağı | kg | 420 | U05, U10, U11, U12 |
| M07 | Domates | kg | 45 | U01, U04, U06, U07, U08, U09, U10, U11 |
| M08 | Soğan | kg | 25 | U01, U06, U07, U09 |
| M09 | Marul | adet | 15 | U04, U08, U09 |
| M10 | Süzme yoğurt | kg | 110 | U01, U04, U08, U10, U11 |
| M11 | Tel kadayıf | kg | 140 | U12 |
| M12 | Künefe peyniri | kg | 480 | U12 |
| M13 | Lahmacun harcı (acılı kıyma) | kg | 300 | U07, U09 |
| M14 | Ayran (şişe) | adet | 14 | U02 |
| M15 | Kutu içecek | adet | 22 | U03 |

---

## 3. Mağaza Yükseltmeleri

Fiyatlar TL, etki yüzdeleri temel değere göre. "Açılış" o kademenin gün 1'den itibaren görünür olduğu gündür.

### 3.1 Mutfak aletleri

| Ekipman | Kademe | Fiyat (TL) | Etki | Açılış (gün) |
|---|---|---|---|---|
| Döner makinesi | Lv1 | 0 (başlangıç) | Temel hız | 1 |
| | Lv2 | 4.000 | Et ürünlerinde hazırlık -%15 | 4 |
| | Lv3 | 15.000 | Hazırlık -%30, aynı anda 2 döner kolu | 10 |
| | Lv4 | 48.000 | Hazırlık -%45, otomatik döndürme | 18 |
| Ekmek ısıtıcı | Lv1 | 0 (başlangıç) | Ekmek ısınma 3 sn | 1 |
| | Lv2 | 2.500 | Ekmek ürünlerinde -2 sn | 3 |
| | Lv3 | 8.000 | -4 sn, ekmek bayatlamaz | 9 |
| | Lv4 | 22.000 | -5 sn, ekmek kalitesi +%5 (bahşiş) | 17 |
| Otomatik dilimleyici | Lv1 | 0 (başlangıç) | Manuel dilimleme | 1 |
| | Lv2 | 3.500 | Döner kesiminde -3 sn | 6 |
| | Lv3 | 12.000 | Kesimde -5 sn, israf -%50 | 12 |
| | Lv4 | 34.000 | Kesimde -6 sn, israf -%80 | 21 |
| Buzdolabı | Lv1 | 0 (başlangıç) | Stok 30 birim | 1 |
| | Lv2 | 2.000 | Stok 50, bozulma normal | 2 |
| | Lv3 | 7.500 | Stok 100, bozulma -%30 | 8 |
| | Lv4 | 24.000 | Stok 220, bozulma -%60 | 16 |

### 3.2 Restoran öğeleri

| Öğe | Kademe | Fiyat (TL) | Etki | Açılış (gün) |
|---|---|---|---|---|
| Masa | Lv1 | 0 (başlangıç) | 2 masa, 4 kişilik kapasite | 1 |
| | Lv2 | 3.000 | Kapasite 8 kişi | 11 |
| | Lv3 | 9.000 | Kapasite 12 kişi | 22 |
| | Lv4 | 24.000 | Kapasite 16 kişi, VIP köşe (bahşiş +%5) | 28 |
| Tabela | Lv1 | 0 (başlangıç) | Yerel akış, T = 0 | 1 |
| | Lv2 | 1.000 | Müşteri çarpanı T = +%10 | 8 |
| | Lv3 | 4.000 | T = +%20 | 15 |
| | Lv4 | 12.000 | T = +%35, gece ışıklı (20:00 sonrası ek +%15) | 24 |
| Klima | Lv1 | 0 (başlangıç) | Sabır süresi +%0 | 1 |
| | Lv2 | 2.500 | Sabır süresi +%10 | 13 |
| | Lv3 | 7.000 | Sabır süresi +%20 | 21 |
| | Lv4 | 18.000 | Sabır süresi +%30, sıcak dalga olayında sabır kaybı yok | 26 |
| Müzik sistemi | Lv1 | 0 (başlangıç) | Ortam müziği, M = 0 | 1 |
| | Lv2 | 1.800 | M = +%5, İP kazanımı +%5 | 4 |
| | Lv3 | 5.500 | M = +%10, İP +%10, müzik tarzı seçimi | 14 |
| | Lv4 | 15.000 | M = +%15, İP +%15, haftalık canlı müzik | 23 |
| Dekor | Lv1 | 0 (başlangıç) | Temel dekor | 1 |
| | Lv2 | 2.200 | Turist çıkma olasılığı +%10 | 7 |
| | Lv3 | 6.500 | Turist +%20, İP +%5 | 16 |
| | Lv4 | 20.000 | Turist +%35, İP +%10 | 25 |

### 3.3 Toplam yükseltme maliyeti

| Grup | Lv2 + Lv3 (TL) | Lv2-Lv4 (TL) |
|---|---|---|
| Mutfak (4 ekipman) | 54.500 | 182.500 |
| Restoran (5 öğe) | 42.500 | 131.500 |
| **Toplam** | **97.000** | **314.000** |

Tasarım notu: 30 günün sonunda Lv2-Lv3 tamamlanabilir (yaklaşık %46 net gelir). Lv4 kademeleri 30. gün sonrası içerik için ayrılabilir.

---

## 4. 30 Günlük Para, Müşteri ve Zorluk Eğrisi

Kurallar:
- Ciro = Müşteri x Ortalama sipariş.
- Malzeme gideri cironun %38'i. Sabit gider = 300 + 30 x (gün - 1) TL.
- Net = Ciro x 0,62 - Sabit gider. Yükseltme harcaması bu tabloda düşülmemiştir.
- Yıldız B değerleri: 1★=10, 2★=20, 3★=28, 4★=36, 5★=44. Müşteri = B x C. Tablodaki C değerleri 1,0-2,2 aralığındadır.
- Başlangıç sermayesi: 5.000 TL.

| Gün | Yıldız | Müşteri | Ort. sipariş (TL) | Ciro (TL) | Tahmini net (TL) | Zorluk (1-10) | Açılanlar |
|---|---|---|---|---|---|---|---|
| 1 | 1★ | 10 | 150 | 1.500 | 630 | 1 | U01, U02 |
| 2 | 1★ | 12 | 150 | 1.800 | 786 | 1 | U03, Buzdolabı Lv2 |
| 3 | 1★ | 15 | 165 | 2.475 | 1.175 | 2 | U04, Ekmek ısıtıcı Lv2 |
| 4 | 1★ | 17 | 165 | 2.805 | 1.349 | 2 | Döner Lv2, Müzik Lv2 |
| 5 | 1★ | 20 | 185 | 3.700 | 1.874 | 2 | U05 |
| 6 | 1★ | 22 | 185 | 4.070 | 2.073 | 3 | Dilimleyici Lv2 |
| 7 | 2★ | 25 | 200 | 5.000 | 2.620 | 3 | U06, Dekor Lv2 |
| 8 | 2★ | 27 | 200 | 5.400 | 2.838 | 3 | Buzdolabı Lv3, Tabela Lv2 |
| 9 | 2★ | 30 | 210 | 6.300 | 3.366 | 4 | U07, Ekmek ısıtıcı Lv3 |
| 10 | 2★ | 32 | 210 | 6.720 | 3.596 | 4 | Döner Lv3 |
| 11 | 2★ | 35 | 220 | 7.700 | 4.174 | 4 | U08, Masa Lv2 |
| 12 | 2★ | 37 | 220 | 8.140 | 4.417 | 4 | Dilimleyici Lv3 |
| 13 | 3★ | 40 | 240 | 9.600 | 5.292 | 5 | U09, Klima Lv2 |
| 14 | 3★ | 42 | 240 | 10.080 | 5.560 | 5 | Müzik Lv3 |
| 15 | 3★ | 45 | 240 | 10.800 | 5.976 | 5 | Tabela Lv3 |
| 16 | 3★ | 47 | 260 | 12.220 | 6.826 | 5 | U10, Dekor Lv3, Buzdolabı Lv4 |
| 17 | 3★ | 50 | 260 | 13.000 | 7.280 | 6 | Ekmek ısıtıcı Lv4 |
| 18 | 3★ | 52 | 260 | 13.520 | 7.572 | 6 | Döner Lv4 |
| 19 | 3★ | 55 | 285 | 15.675 | 8.879 | 6 | U11 |
| 20 | 4★ | 57 | 285 | 16.245 | 9.202 | 6 | Sadık müşteri etkinliği |
| 21 | 4★ | 60 | 285 | 17.100 | 9.702 | 6 | Dilimleyici Lv4, Klima Lv3 |
| 22 | 4★ | 62 | 285 | 17.670 | 10.025 | 7 | Masa Lv3 |
| 23 | 4★ | 65 | 300 | 19.500 | 11.130 | 7 | U12, Müzik Lv4 |
| 24 | 4★ | 67 | 300 | 20.100 | 11.472 | 7 | Tabela Lv4 |
| 25 | 4★ | 70 | 300 | 21.000 | 12.000 | 7 | Dekor Lv4 |
| 26 | 4★ | 72 | 300 | 21.600 | 12.342 | 7 | Klima Lv4 |
| 27 | 5★ | 75 | 320 | 24.000 | 13.800 | 8 | - |
| 28 | 5★ | 77 | 320 | 24.640 | 14.167 | 8 | Masa Lv4 |
| 29 | 5★ | 80 | 320 | 25.600 | 14.732 | 8 | - |
| 30 | 5★ | 82 | 320 | 26.240 | 15.099 | 9 | Dönem sonu raporu |

30 gün toplamı: ciro yaklaşık 374.200 TL, sabit gider 22.050 TL, net yaklaşık 209.950 TL (yükseltme öncesi).

---

## 5. Müşteri Tipleri

Sabır süresi, müşteri masaya oturunca başlar. Süre dolunca müşteri ayrılır ve İP'den düşülür.

| Tip | Oran | Açılış (gün) | Sabır (sn) | Tercih | Özellik |
|---|---|---|---|---|---|
| Acele eden iş insanı | %20 | 1 | 25 | U01, U04 | Hızlı sipariş, bahşiş yok |
| Sabırlı aile | %15 | 1 | 60 | U05, U06, U02 | 2-4 ürünlük sipariş |
| Öğrenci | %15 | 1 | 40 | U04, U03 | Fiyat hassas: fiyat +%10 üstünde ayrılma olasılığı +%20 |
| Taksi şoförü | %7 | 2 | 30 | U08, U03 | Hızlı, 3 ziyaretten sonra VIP (sabır +%10) |
| Yaşlı müşteri | %10 | 3 | 70 | U01, U02 | Yavaş ama sabırlı, tabak hatasına hassas |
| Turist | %10 | 5 | 45 | U07, U11, U12 | Dekor ve tabela etkisi, bahşiş +%15 |
| Komşu esnaf (sadık) | %5 | 7 | 50 | U01, U02 | Her gün gelir, İP birikimine katkı |
| Şikayetçi | %5 | 9 | 20 | rastgele | Hatalı tabakta İP -30 riski |
| Gece vardiyası işçisi | %8 | 13 | 35 | U06, U09 | Yalnızca 20:00 sonrası |
| Influencer | %5 | 19 | 30 | U11, U12 | Fotoğraf bonusu İP +10, servis yavaşlığı |

Toplam oran: %100.

---

## 6. Yıldız Sistemi

### 6.1 Yıldız eşikleri

| Yıldız | İP aralığı | B (taban müşteri) | Tipik gün aralığı |
|---|---|---|---|
| 1★ | 0-199 | 10 | 1-6 |
| 2★ | 200-399 | 20 | 7-12 |
| 3★ | 400-649 | 28 | 13-19 |
| 4★ | 650-849 | 36 | 20-26 |
| 5★ | 850-1000 | 44 | 27-30 |

### 6.2 İtibar puanı (İP) kazanım ve kaybı

| Olay | İP |
|---|---|
| Sipariş zamanında teslim | +5 |
| Sabrın %50'si kalarak teslim | +3 ek |
| Temiz masa görevi | +10 |
| Influencer memnuniyeti | +10 |
| Sadık müşteri (haftalık) | +5 |
| Yanlış ürün teslimi | -15 |
| Müşteri sabır dolup ayrıldı | -20 |
| Şikayetçi müşteri olayı (hatalı tabak) | -30 |

### 6.3 Müşteri akış formülü

```
Günlük müşteri (N) = round( B_yıldız x C )
C = 1 + T + D + M + S
```

| Terim | Anlam | Kaynak |
|---|---|---|
| B | Yıldız taban değeri | Tablo 6.1 |
| T | Tabela bonusu | Tabela Lv1-Lv4: 0 / 0,10 / 0,20 / 0,35 |
| D | Dekor bonusu | Dekor Lv1-Lv4 (genel akış için): 0 / 0,05 / 0,10 / 0,15 |
| M | Müzik bonusu | Müzik Lv1-Lv4: 0 / 0,05 / 0,10 / 0,15 |
| S | Sadık müşteri birikimi | Her ziyaret +0,01, en fazla +0,40 |

Dengeleme notu: Tablo 4'teki müşteri değerleri hedeftir. C değerleri oyuncunun yükseltme durumuna göre 1,0-2,2 aralığında kalmalı, aşırı yükseliş için sınır (cap) uygulanmalıdır.

---

## 7. Görme Engelliler İçin Erişilebilirlik Rehberi

### 7.1 Temel ilkeler

| Konu | Uygulama | Android notu |
|---|---|---|
| TalkBack uyumu | Her etkileşimli öğeye anlamlı ad ve rol ver. Görsel bilgi metinle de sunulsun. | View: `android:contentDescription`. Compose: `Modifier.semantics { contentDescription = "..." }` |
| Odak sırası | Sıra: sipariş alma, hazırlık, teslim, kasa. Görsel düzenle eşleşmeli. | View: `accessibilityTraversalBefore/After`. Compose: `traversalIndex` |
| Sesli duyuru | "Sipariş hazır: İskender, masa 3" gibi kısa cümleler. | `view.announceForAccessibility("...")` |
| Canlı bölge | Sabır uyarısı için polite, sabır bitti ve hata için assertive. | View: `android:accessibilityLiveRegion="polite"`. Compose: `liveRegion = LiveRegionMode.Polite` |
| Ses ipucu | Her kritik olay için ses + metin + titreşim. Kanallar ayrı ses seviyesi. | AudioAttributes ile ayrı kanal |
| Uzamsal ses | Döner solda, fırın sağda, kasa ortada. Müşteri kapısı sağdan gelir. | SoundPool `play(leftVol, rightVol)` ile pan |
| Haptic | Dilim kesimi: kısa çift tık. Hazır: uzun tek titreşim. Hata: üç kısa darbe. | `VibrationEffect.createPredefined` ve `createWaveform` |
| Büyük dokunma hedefi | Minimum 48x48 dp, öğeler arası en az 8 dp. Kritik butonlar 64 dp. | Material ve Android erişilebilirlik önerileri |
| Tempo ayarı | Oyun hızı 0,75x / 1,0x / 1,25x. Sabır çarpanı 1,5x veya 2,0x. Duraklatma açık. | Ayar ekranında kaydırıcı ve hazır ön ayar |
| Metronom ipucu | Hazırlık için düzenli tık sesi. Doğru zamanlamada yükselen ton. | SoundPool ile düşük gecikmeli tık |
| Kontrast | Metin 4,5:1, UI bileşenleri 3:1 (WCAG AA). Renk tek bilgi taşımaz, ikon ve metin eklenir. | Renk tokenlerinde kontrol |
| Yazı ölçeği | Sistem yazı boyutu %200'e kadar bozulmadan çalışmalı. | `sp` birimi, sabit yükseklik kullanma |
| Hareket azaltma | Sistem "animasyonları azalt" ayarına uy. | `Settings.Global.ANIMATOR_DURATION_SCALE` kontrolü |

### 7.2 Erişilebilirlik modu başlangıç akışı

| Adım | İçerik |
|---|---|
| 1 | İlk açılışta "Erişilebilirlik modu" sorusu (TalkBack algılanırsa önerilir) |
| 2 | Tempo, sabır çarpanı, titreşim ve ses kanalı ayarları |
| 3 | Kısa eğitim: sesli rehber ile ilk döner hazırlığı |

---

## 8. Ses Tasarımı

### 8.1 Gerekli ses efektleri

Teknik hedef: format `.ogg` (Vorbis veya Opus), 44,1 kHz. Efektler -16 LUFS, tepe -1 dBTP. Müzik -20 LUFS (duck destekli). Ses tasarım yönü: mekanik değil, ahşap, porselen, ateş ve cam gibi organik kaynaklar.

| # | Ses | Olay | Kayıt / sentez yönü | Süre | Uzamsal konum | Not |
|---|---|---|---|---|---|---|
| 1 | Dilim kesimi (et) | Döner kesildiğinde | Islak bıçak, kalın ahşap blok, düşük geçiş | 0,4 sn | Sol (döner) | Yumuşak, yağlı |
| 2 | Ekmek ısıtma | Ekmek ızgarada | Hafif çıtırtı ve hava üflemesi | 1,2 sn | Sol-orta | Sıcak doku |
| 3 | Tereyağı eriyişi | Tereyağı eklenince | Yumuşak "şıs" | 0,8 sn | Orta | Yağ ve sıcaklık hissi |
| 4 | Yoğurt sosu | Sos sürülünce | Kaşık sürtünmesi | 0,3 sn | Orta | Hafif, kısa |
| 5 | Tabak bırakma | Sipariş teslimi | İnce porselen tıkı | 0,25 sn | Kasa (orta-sağ) | Net ama sert değil |
| 6 | Sipariş alındı | Yeni sipariş | Kalimba, iki nota | 0,6 sn | Merkez | Tanıtım sesi |
| 7 | Sipariş hazır | Hazırlık tamamlanınca | Glockenspiel, yükselen üç nota | 0,9 sn | Tezgah | Ana geri bildirim |
| 8 | Müşteri kapı zili | Müşteri gelince | Pirinç çan, ahşap kapı | 0,8 sn | Sağ (kapı) | Sıcak ve davetkar |
| 9 | Müşteri ayrıldı | Sabır doldu | Düşen ney tonu, nefes | 1,0 sn | Sağ (kapı) | Üzücü ama sert değil |
| 10 | Hata | Yanlış ürün | İki düşük nota, buzzer değil | 0,5 sn | Merkez | Suçlamayan ton |
| 11 | Sabır uyarısı | Sabır azalırken | Yavaş ahşap tık, kalp atışı benzeri | 0,3 sn tekrar | Ekran | Sadece son %30'da |
| 12 | Yıldız kazanımı | Yıldız artınca | Çan ve kısa arpej | 1,5 sn | Merkez | Kutlama |
| 13 | Kasa | Ödeme | Çekmece ve bozuk para, yumuşak | 0,5 sn | Kasa | Doğal kaynak |
| 14 | Ayran açma | Ayran servisi | Cam kapak çevrilmesi | 0,4 sn | Tezgah | Sıcak, kırsal |
| 15 | Kutu içecek açma | Kutu servisi | Alüminyum tık ve köpük | 0,3 sn | Tezgah | Kısa |
| 16 | Künefe fırın | Künefe pişerken | Kadayıf çıtırtısı, düşük ısı | 1,0 sn | Sağ (fırın) | Ambiyans katmanı |
| 17 | Fırın hazır | Lahmacun hazır | Tok tahta ve sıcak hava | 0,6 sn | Sağ (fırın) | Ayırt edici |
| 18 | UI tık | Buton basımı | Yumuşak ahşap tık | 0,1 sn | Merkez | Düşük seviye |
| 19 | Ambiyans sokak | Arka plan | Uzak trafik, simit arabası zili | 30 sn döngü | Stereo geniş | Düşük seviye |
| 20 | Ambiyans mutfak | Arka plan | Ocak uğultusu, düşük frekans | 30 sn döngü | Mutfak alanı | Düşük seviye |

### 8.2 Müzik tarzları

Her tarz üç katmandan oluşur: yatak (pad ve bas), ritim ve melodi. Döngü 32 ölçü, geçiş 2 sn çapraz geçiş ile yapılır.

| Tarz | Nota ve tonalite | Ölçek | Tempo (BPM) | Ölçü | Enstrümanlar | Duygu | Kullanım |
|---|---|---|---|---|---|---|---|
| Sokak Ocağı (Anadolu lo-fi) | Re (D) tonik | Hicaz benzeri: D, Eb, F#, G, A, Bb, C | 92 | 4/4 | Bağlama, ney örneği, darbuka, hafif kontrbas | Sıcak, sokak, samimi | Gündüz, 1★-2★ |
| Akşam Lokantası (Bossa-Jazz) | Fa (F) majör çevresi | Dorian: F, G, Ab, Bb, C, D, Eb | 110 | 4/4, hafif swing | Rhodes, kontrbas, fırça davul, flügel | Sakin, rahat, akşam | 18:00 sonrası, 3★-4★ |
| Gece Ocağı (Downtempo) | La minör (A) | Doğal minör ve pentatonik | 84 | 4/4, yarım tempolu davul | Vinil çıtırtısı, sub bass, sentez pad, ney fill | Odaklı, sakin, gece | Gece modu, 4★-5★ |

---

## 9. Açık Konular

| Konu | Karar gerekli mi | Öneri |
|---|---|---|
| Fiyat ölçeği ve enflasyon | Evet | Oyun içi fiyatları bölgesel değil, sabit tut |
| Gerçek zaman ile oyun günü oranı | Evet | 1 gün = 6 dk ile test et, gerekirse 4-8 dk arası ayarla |
| Sabır çarpanları ve erişilebilirlik modu | Evet | Erişilebilirlik modunda sabır x1,5 varsayılan olsun |
| Lv4 kademeleri ve sezon | Hayır (sonraki sürüm) | 30. gün sonrası içerik olarak planla |
