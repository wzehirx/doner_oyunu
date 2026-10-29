# v0.5 Akış Yenilemesi – Gerçek Dönerci Dükkânı Akışı

Kaynak: oyuncu geri bildirimi (v0.4 oynandı). Sorunlar:
1. Çırak 1. seviyede hiçbir iş yapmıyor (yalnızca +1 yuva veriyordu, oyuncu doldurmak zorundaydı).
2. Ayran + döner siparişinde akış garip: ayran al, dök, PAKETLE, sonra ekmek ısıt, dilimle, sos, PAKETLE, teslim. Çift paketleme, sıra sabit.
3. Soslar/salata rastgele ekleniyor; oyuncu NE ekleneceğini seçmeli.
4. Hazırlık yapılamıyor: döner önceden dilimlenemez, içecekler önceden hazırlanamaz.
5. İstasyon ses efektleri adım süresinden kısa kalıyor (ses bitiyor, adım sürüyor).
Hedef: gerçek bir dönerci akışı: gün öncesi/arası HAZIRLIK, sipariş başına SERBEST SIRALI kalemler, seçilebilir malzeme/sos, ortak tek paketleme, işe yarayan çırak.

## 1. Hazırlık (prep) sistemi
Hazır stok kavramı (SaveData'ya kaydedilmez, gün içi durum; gün sonunda atılır, İÇECEK hariç):
| Hazır öğe | Hazırlayan istasyon | Parti | Süre | Tüketir (parti başına) | Siparişte etkisi |
|---|---|---|---|---|---|
| Dilimlenmiş döner (porsiyon) | DONER | 4 porsiyon | ~6 sn | ürünün döner malzemesi x4 | "Döneri dilimle" adımı ~0,8 sn "al" olur (porsiyon düşer) |
| Doldurulmuş içecek (bardak ayran) | ICECEK | 4 bardak | ~4 sn | ayran x4 | "Ayranı doldur" adımı ~0,5 sn olur. Gün sonu stoğu ertesi güne kalır (buzdolabı) |
| Hazır salata/sos tepsisi | SOS | 6 kullanım | ~5 sn | seçilen malzemelerden 6'şar kullanım | topping seçimi hızlanır (seçim yine zorunlu, süre %50) |
Ekmek ısıtma hazırlanamaz (bayatlar). Hazır stok kapasitesi: döner 8, içecek 8, sos tepsisi 12 (yükseltmeyle artar). Hazır olmayan kalem yine normal süreyle yapılır. Hazırlık malzemeyi hazırlama anında tüketir; gün sonunda artan hazır döner/salata israftır (özette "israf" satırı: kaç porsiyon atıldı).
Hazırlık çalışması bir yuvayı (slot) kullanır; sipariş adımı gibi süreyle ilerler. Hazırlık HER AN yapılabilir (sipariş yokken veya yuva boşken).
**Gün öncesi HAZIRLIK AŞAMASI:** Market'ten sonra, müşteri gelmeden (gün süresine sayılmaz) hazırlık ekranı: sınırsız süre, oyuncu "Dükkânı aç" diyene dek hazırlık yapabilir; çırak varsa hazırlığı kendisi de yapar. Atlanabilir ("Hazırlık yapmadan aç"). İlk gün öğreticisi bunu öğretir.

## 2. Sipariş akışı (kalem bazlı, serbest sıra)
- Sipariş = kalemler (ürünler). Her kalemin kendi adımları vardır ve oyuncu **herhangi bir kalemden, herhangi bir sırada** başlayabilir (ör. önce döner, sonra ayran).
- Ürün adımlarından kalem-içi "Paketle" KALKAR. Siparişin sonunda TEK ortak adım: **"Siparişi paketle"** (PAKET istasyonu, süre ~1,5 sn + kalem sayısı x 0,5 sn), tüm kalemler hazır olunca açılır; sonra teslim.
- İçecekler: ayran = "Ayranı doldur" (ICECEK, tek adım; hazır içecek varsa hızlı). Kutu içecek = "Dolaptan al" (ICECEK, ~1 sn). Tatlı/künefe kendi adımları (ızgara/tatlı istasyonu) yine kalem içi.
- Tavuk/et ürünleri: Dilimle/ızgara + ekmek/lavaş ısıt + malzeme ekle (topping seçimi) ; kalem içi sıra: önce hazırlık-türü adımlar (ısıt, dilimle) herhangi sırada, topping adımı ONLARDAN SONRA (sarmadan önce), kalem sonunda otomatik "hazır" (paket yok).
- Narrator duyuruları kalem bazlı: "Sipariş 2: ekmek arası döner, ayran. Önerilen: ..." Oyuncu sırayı seçer, motor sıra DAYATMAZ (yalnızca topping adımı ısıt/dilimle bitince açılır, bağımlılık kuralı).
- Oyuncu seçimi: `selectOrder(id)` sonra `selectItem(index)`; `startStep(orderId, itemIndex, stationId)`; istasyon tek bir uygun adıma çözülür (kalemde o istasyonda açık adım yoksa WRONG_STATION).

## 3. Malzeme/sos seçimi (topping)
- Her ürünün **varsayılan topping kümesi** vardır (ör. ekmek arası döner: domates, marul, soğan, ketçap; Hatay dürüm: soğan, domates, turşu, acı sos, nar ekşisi...). Seçenek havuzu ürüne göre 4–8 malzeme (sos ve salata malzemeleri `Ingredient`, kategori SOS/SALATA).
- Müşteri **istekleri** (Order.requests): "soğansız", "acılı", "acısız", "sosuz", "bol soslu" gibi. İstekler müşteriden gelir (olasılık gün ilerledikçe artar; 1. gün: yok, 2. gün: ~%20, sonra ~%45). İstek sipariş kartında ve duyuruda metin olarak yer alır ("soğansız, acılı").
- Hedef küme = varsayılan ± istekler. Oyuncu SOS istasyonuna dokununca **Malzeme seçimi** paneli açılır: her malzeme bir aç/kapa düğmesi (tek dokunma, durum sesli: "Soğan, eklenmedi"), "Tamam". Seçili malzemeler için süre = temel süre + 0,3 sn/malzeme; stok seçili malzemeler kadar tüketilir (Tamam'da; stokta olmayan malzeme seçilemez ve belirtilir).
- Doğrulama: seçilen küme ≠ hedef küme ise "hatalı sipariş": eksik/fazla malzeme başına −0,05 yıldız (en çok −0,15) ve bahşiş yarıya iner, müşteri yorumu duyurulur ("Soğanı istememiştim"). Doğruysa +küçük bonus (memnuniyet). Oyuncu "Tamam"dan önce hedefi tekrar dinleyebilir ("Hedefi oku").
- 1. günde istek yok ve varsayılan küme önceden seçili gelir (yalnızca Tamam); 2. günden itibaren varsayılan önceden seçili, istekler fark yaratır; 4. günden itibaren seçim BOŞ başlar (oyuncu kendisi seçer). 
- Hazır salata/sos tepsisi varsa seçim süresi yarıya iner.

## 4. Çırak (işe yarayan)
Çırak = otomatik işçi. Her seviye 1 çırak "kapasitesi" ekler (max 3, yükseltmedeki seviye). Çırak yuva sayısını artırmaz, **kendi başına çalışır** (kendi iş kuyruğu):
- Seviye 1: (a) boşta iken HAZIRLIK yapar (içecek doldurur, döner dilimler) ve (b) siparişlerin "Siparişi paketle" ile "Dolaptan al/Ayranı doldur" adımlarını ONLAR başlatılmamışsa/oyuncu elinde değilse üstlenir.
- Seviye 2: + "Ekmek ısıt" adımlarını üstlenir.
- Seviye 3: + "Döneri dilimle" ve ızgara adımlarını üstlenir (topping seçimi HER ZAMAN oyuncuda).
- Çırak hızı oyuncudan %20 yavaş, hatasız. En sabırsız siparişe öncelik verir, oyuncunun seçili siparişine DOKUNMAZ (oyuncu o siparişin adımını başlatabilir; çırak yalnızca seçili olmayan siparişlerin adımlarını alır, hazırlıkta ise boşta).
- Duyurular (kısa, sık değil): "Çırak ayran dolduruyor", "Çırak 3 numaralı siparişi paketledi". Ayarlardan "Çırak duyuruları" kapatılabilir/aç.
- Çırağın yaptığı iş malzeme tüketir, normal stok kuralları geçerli.

## 5. Ses
- İstasyon efektleri adım süresi boyunca sürmeli: döngülenebilen sesler (sizzle, pour, wrap sürtünme) SoundPool döngüsüyle adım süresince çalınır ve adım bitince kısa kesilir/biter; döngülenemeyen darbeli sesler (slice, sauce) adım boyunca 0,35–0,6 sn aralıkla ±%5 perde ve ±%8 ses rastgeleliğiyle tekrarlanır (mekanik tekrar hissini önlemek için). Duraklatmada durur, devam edince sürer. Çırak adımları kısık/ uzak (pan merkez, ses %60).
- Hazırlık adımları da ilgili sesleri kullanır. Hazırlık bitişi `sfx_step_done`.
- (Ek) Gerekirse ElevenLabs ile uzun döngü sesleri üretilir (bkz. tools/ ve CLAUDE-dışı notlar); önce mevcut dosyalar döngü/tekrar ile denenir.

## 6. Ekonomi / denge
- Fiyatlar ve süreler yeni akışa göre yeniden dengelenir: `Campaign` botu gün 1–40'ı geçmeli; hazırlık yapan bot yapmayandan KAZANÇLI olmalı ama yapmayan bot da 1–5. günleri geçebilmeli; yeni bir "hiçbir şey yapma" botu 5 gün içinde başarısız olmalı.
- Hazırlık israfı gerçek maliyet: fazla hazırlık parayı yakar.

## 7. Kayıt uyumluluğu
- `SaveData` yeni alanlar kazanabilir (içecek hazır stoğu, ayar: çırak duyuruları); eski kayıtlar (v0.4) yüklenmeli (eksik alanlar varsayılan).

## 8. Erişilebilirlik (aynen geçerli)
- Topping paneli, hazırlık ekranı, kalem seçimi: Türkçe contentDescription, ≥56dp, tek dokunma, odak yönetimi, Narrator duyuruları; "Durumu oku" hazırlık/kalem/hedef bilgisini de okur.
- Ekrandaki yeni kontroller için Robolectric erişilebilirlik denetimi (A11yAudit) güncellenir.
