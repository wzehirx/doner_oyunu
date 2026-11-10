# v2.5 – 60 günlük kampanya, fiyat ve talep, maaş ve faturalar, banka, çırak teslimi, 5 seviyeli yükseltmeler, kayıt dönemi

Kaynak: görme engelli oyuncunun v2.0 istekleri (a–i). v2.0 ve öncesi belgeler geçerlidir; çakışmada bu belge kazanır. API: `docs/engine_api.md` §B (v2.5). Terimler: `docs/terminoloji.md`.

## 0. İlkeler
- **Gerçekçi ama sade**: her yeni kural tek cümleyle söylenebilir ("Faturayı 3 gün içinde öde; ödemezsen kesinti başlar"). Görme engelli oyuncu kafasında zamanlayıcı tutmasın diye her şey **gün başında** ve **gün sonunda** sesli, kısa, net duyurulur; gün içinde ek zamanlayıcı yok.
- **Ceza hafif başlar, birikir**: uyarı → kısmi kesinti → tam kesinti. Ödenince hemen (gün içinde ödenirse o an) normale döner.
- **Kilitlenme yok**: kira için banka limit yetmese de "kira kredisi" verir; maaş borcu, taksit ve vergi kasaya para girdikçe kendiliğinden kapanır.
- **Varsayılan fiyatlar v2.0 ile birebir**: tüm fiyatlar %100 iken talep, rastgelelik tüketimi ve gelir eskisiyle aynıdır (ilk 16 günün denge testleri kaymaz).

## 1. Kampanya takvimi (60 gün + sonsuz)
Zorluk yoğunluğu: gün ≤ 40 `i = gün`; 41–60 `i = 40 + 0,5·(gün−40)` (60. gün 50); 60 sonrası 10 günlük dalgalar `i = 50 + (fazla mod 10)·0,4 + (fazla div 10)·2`. Geliş aralığı `max(4,2; 18 − 0,31·i)`, sabır çarpanı `max(0,5; 1 − 0,01·i)` (v2.0 formülleri).

| Gün | Ürün (ref. fiyat) | Malzeme | Müşteri tipi | Yükseltme (yeni / seviye) |
|---|---|---|---|---|
| 1–16 | v2.0 ile aynı (10 ürün) | v2.0 takvimi | v2.0 (10 tip) | v2.0 seviye 1–3 |
| 15 | | | Yemek kuryesi | |
| 17 | Tavuk pilav üstü (280) | | | |
| 18 | | | Memur | |
| 19 | Su (15) | su | | |
| 20 | | maydanoz | | İkinci kasa 1 |
| 21 | Mercimek çorbası (90) | mercimek çorbası | | |
| 23 | Kaşarlı dürüm (300) | kaşar | | |
| 24 | | | İnşaat işçisi | |
| 25 | Şalgam (40) | şalgam | | |
| 26 | | pul biber | | |
| 27 | | | Sporcu | |
| 28 | Fırın sütlaç (85) | sütlaç | | |
| 29–36 | | | | seviye 4'ler |
| 31 | Çay (20) | çay | | |
| 32 | | acılı ezme | | |
| 33 | | | Gurme | |
| 34 | Köfte ekmek (230) | köfte | | |
| 38 | Tavuk iskender (360) | kırmızı lahana | | |
| 42 | Fıstıklı baklava (160) | baklava | | |
| 42–54 | | | | seviye 5'ler, Tabela 6 |
| 46 | Tantuni (260) | tantuni eti | | |
| 50 | Karışık döner tabağı (520) | | | |

Toplam 24 ürün (12 + 12 yeni), 39 malzeme, 15 müşteri tipi (10 + 5), 16 yükseltme (11 + 5). Yeni ürünler var olan istasyonları kullanır (çorba ve tantuni Ocak'ta, sütlaç ve baklava Tatlı tezgâhında, çay/şalgam/su İçecek dolabında). Brifing yeni ürün, malzeme ve müşteri tipini duyurur.

## 2. Fiyat belirleme ve talep (istek d, e)
- Her ürünün **referans fiyatı** = katalog fiyatı. Oyuncu satış fiyatını **%50–%150** arasında **%5 adımla** belirler (`Pricing`, kayıtta `price.<ürün>=yüzde`). Kalem fiyatı = referans × boy çarpanı (+patates 40) × oran; fiyat **sipariş anında** sabitlenir.
- **Duyarlılık** `e` (müşteri tipine göre): öğrenci 1,8 · inşaat işçisi 1,5 · kurye 1,4 · şikâyetçi 1,3 · yaşlı 1,2 · taksi 1,1 · aile, esnaf, memur 1,0 · sporcu, gece 0,9 · acele 0,6 · influencer 0,5 · turist 0,4 · gurme 0,3. Yıldız etkisi: `e' = e × (1,15 − 0,05·yıldız)` (5★'da %10 daha az duyarlı, 1★'da %10 daha çok).
- **Tipin sepet oranı** `r_t` = tipin seçim ağırlıklarıyla (tercih 3, diğer 1) ve referans fiyatla ağırlıklı ortalama fiyat oranı.
- **Talep** `D_t = sınırla(exp(−1,7 · e' · x · (1 + 2·max(0, x) + max(0, −x))), 0,15, 1,8)`, `x = r_t − 1`: pahalıda giderek hızlanan düşüş (çok pahalı menüye müşteri gelmez), indirimde hafif artan yükseliş.
  - Müşteri tipi seçimi ağırlığı × `D_t` (pahalı menüde turist/gurme oranı artar, öğrenci azalır).
  - Genel geliş çarpanı `A = Σ W_t·D_t / Σ W_t`; süreli günde geliş aralığı `/A`; süresiz günde kota `× sınırla(A, 0,5, 1,5)`.
  - Sipariş içinde ürün seçimi ağırlığı × `exp(−1,5 · e' · (oran_p − 1))` (pahalı ürün daha az seçilir).
- **Teslimde fiyat memnuniyeti** `v = (sipariş oranı − 1) × e'`: yıldız kazancı × `sınırla(1 − 1,5·v, 0, 1,3)`, bahşiş × `sınırla(1 − 1,2·v, 0,3, 1,3)`; `v > 0,2` ise ek yıldız cezası `min(0,15; 0,3·(v − 0,2))` ve "Müşteri fiyatı pahalı buldu."; `v < −0,15` ise "Müşteri fiyatı uygun buldu."
- **Ucuzda sabır**: müşteri sabrı × `1 + 0,6·(1 − sipariş oranı)·e'` (yalnızca oran < 1).
- **Denge gerekçesi**: `K·ē ≈ 1/(1−c)` (c ≈ %40 malzeme payı) seçildi; kapasite yetiyorsa **referans** fiyat kârı en yüksek yere yakındır. **Düşük** fiyat daha çok müşteri, daha hızlı yıldız, sabır ve bahşiş oranı verir ama marj erir (%50'de zarar). **Yüksek** fiyat az müşteri ve yüksek marj verir; sıra zaten doluysa (güçlü oyuncu) kârlıdır ama yıldız kazancı azalır; %150'de talep çöker ve yıldız düşer. İlk denemede (doğrusal talep) %150 güçlü oyuncu için baskındı (sıra dolu olduğundan kaybolan talep bedavaydı); bu yüzden talep pahalı tarafta dışbükey ve şikâyet cezası sert yapıldı. Doğru fiyat oyuncunun hızına, moduna ve gününe bağlıdır: tek baskın strateji yok (ölçüm §10).

## 3. Çırak maaşı (istek c)
Günlük maaş (sigorta dahil) gün sonunda kasadan otomatik düşer: `maaş = round(T[seviye] × (1 + 0,01·(gün−1)))`, `T = 0, 250, 550, 900, 1250, 1650` (seviye 1 3. günde 255 lira; seviye 3 30. günde 1161 lira). Para yetmezse kalan **maaş borcu** olur; borç varken çırak ertesi gün **gelmez** (çırak seviyesi o gün 0; "Çırak maaşını alamadı, bugün gelmedi.") ve o gün maaş yazılmaz. Borç gün sonunda kasadan önce kapanır ya da `Finance.payWages` ile ödenir.

## 4. Faturalar (istek c, g)
Fatura **gün sonunda kesilir**, **son ödeme günü = kesildiği gün + 2** (o günlerin gün sonu/market ekranında ödenebilir). Gecikme her gün tutarın **%3'ü** kadar gecikme bedeli ekler.

| Fatura | Kesildiği günler | Tutar (g = kesildiği gün) |
|---|---|---|
| Elektrik | 3, 10, 17, … (7 günde bir) | `7 × (30 + 1,5·(g−1) + 10·E)`; E = elektrikli yükseltme seviyeleri toplamı (ekmek ısıtıcı, döner makinesi, otomatik dilimleyici, buzdolabı, klima, müzik, tabela, soğutmalı vitrin, havalandırma, ikinci kasa) |
| Doğalgaz | 4, 11, 18, … | `7 × (25 + 1,5·(g−1) + 12·G)`; G = geniş ocak + döner makinesi seviyeleri |
| Su | 5, 12, 19, … | `7 × (15 + 0,8·(g−1) + 6·S)`; S = çırak + masa takımı seviyeleri |
| Kira | 7, 14, 21, … | `7 × (200 + 20·(g−1))` (günlük kira kalktı; v2.0'ın 300 + 30·(g−1) günlük kirasının ~%67'si, çünkü elektrik/su/doğalgaz artık ayrı) |
| Vergi | 10, 20, 30, … | son 10 günün satış + bahşişinin **%5**'i |

**Tutar ayarının gerekçesi**: ilk denemede (kira = v2.0 kirası, vergi %6, maaş 300/650/…) en yavaş bot (insan benzeri, Gerçekçi) 20–40. günlerde para biriktiremedi, 40. günde yalnızca 2 yükseltmesi vardı ve yıldızı çöktü: sabit giderler takvimle büyüyor, yavaş oyuncunun geliri o hızla büyümüyordu. Ayarlanan tutarlarla 30. günde yavaş oyuncunun sabit gideri günde ~2 000 lira (v2.0'da ~1 100 lira kira) ve hepsi geçer; hızlı oyuncuda giderler satışın ~%15–20'si.

**Ödenmeyen faturanın zorluğu** (gün başında, gecikme günü = gün − son ödeme günü):

| Fatura | 1–2 gün gecikme | 3+ gün gecikme |
|---|---|---|
| Elektrik | kısmi kesinti: ekmek ısıtıcı 1 yer az, ekmek ısıtıcı ve içecek dolabı ×1,3 yavaş; hazır ayranın yarısı bozulur | tam kesinti: ekmek ısıtıcı tek yer, ekmek/içecek ×1,6, döner makinesi ×1,3 (motor durdu); hazır ayran bozulur; gün sonunda et döner ve tavuk döner stoğunun %25'i bozulur. Jeneratör 1 kademeyi (2. seviye 2 kademeyi) karşılar |
| Doğalgaz | Ocak tek göz, Ocak işleri ×1,3 | Ocak kapalı: Ocak isteyen ürünler menüden çıkar, patates yok, müşteri ×0,9 |
| Su | salata/sos tepsisi hazırlanamaz, Sos ve salata tezgâhı ×1,25 | + çay ve çorba menüden çıkar; teslimde yıldız kazancı ×0,6 (hijyen). Su deposu 1 (2) kademe karşılar |
| Kira | ihtar (yalnızca uyarı) | 3–5 gün: ihtarname, her gün başında −0,15 yıldız, müşteri ×0,9; **6+ gün: tahliye**, dükkân açılamaz (gün başarısız) |
| Vergi | gecikme bedeli, kredi notu düşer | 4+ gün: **haciz**: gün sonunda borç + %20 ceza kasadan kendiliğinden alınır |

Uyarılar gün başında söylenir: "Elektrik faturası 315 lira, son ödeme günü bugün. Ödemezsen yarın elektrik kesilir." / "Elektrik kesik: ekmek ısıtıcı ve içecek dolabı yavaş. Faturayı ödersen hemen düzelir." Gün sonunda yeni faturalar ve toplam borç okunur. Gün sonu akışı (UI): her açık fatura için **Öde / Ertele / Krediyle öde**.

## 5. Banka (istek g)
- **Kredi notu** 0–100 (başlangıç 60): zamanında ödenen fatura +2, geciken her fatura her gün −2, ödenen taksit +1/gün, kaçan taksit −5, erken kapatma +3, maaş borcu −1/gün, haciz −10.
- **Limit** `= aşağı100((2000 + 500·gün) × (0,6 + 0,1·yıldız) × sınırla(not/60; 0,25; 1,5)) − kalan anapara`. 3 ya da daha çok kaçan taksit varsa limit 0.
- **Faiz** (günlük, toplam faiz = anapara × oran × vade): not ≥80 %0,6 · 60–79 %0,8 · 40–59 %1,2 · 20–39 %1,8 · <20 %2,5. **Vade** 5, 10 ya da 20 gün; **taksit** = (anapara + faiz) / vade, gün sonunda kasadan otomatik.
- Kaçan taksit gecikmiş borca eklenir (+%5) ve ilk fırsatta otomatik alınır. **Erken kapatma** = kalan anapara + kalan faizin %25'i + gecikmiş borç.
- **Kira kredisi**: kira faturası için limit yetmese de kredi verilir (en yüksek faiz): tahliye kilitlenmesi olmaz. En az kredi 500, adım 100, aynı anda en çok 3 kredi.

## 6. Çırak teslimi (istek b)
- Seviye 1'den itibaren çırak **paketlenmiş (teslime hazır) siparişi teslim eder** (ayar `apprenticeDelivery`, varsayılan açık).
- Sıra: eli boş çırak önce teslime hazır siparişleri (en acilden), sonra v2.0 kuralı (seçili olmayan siparişler, seçili sipariş, hazırlık). **Seçili sipariş** v2.0 kuralıyla: oyuncunun eli doluysa ya da son eyleminden 1,5 sn geçtiyse.
- Teslim 1,0 sn'lik aktif iştir (çırağın eli). Bu sırada oyuncunun `deliver` çağrısı `false` + `onAlreadyRunning`. Ödeme, bahşiş ve yıldız kuralları oyuncununkiyle aynıdır.
- Duyuru: "Çırak teslim etti: sipariş 2, Ali. 240 lira, 20 lira bahşiş." (başlangıç sessiz).

## 7. Yükseltmeler (istek f)
Mevcut 11 yükseltmenin 1–3. seviyeleri v2.0 ile aynı (erken oyun ve eski testler kaymaz); 4. ve 5. seviye eklendi (Tabela 6 seviye). Fiyat ≈ önceki × 1,45–1,6, açılış 28–54. gün; etkiler azalan getiriyle (ör. döner ×0,55 → 0,50 → 0,45). Yeni: **İkinci kasa** (paketleme hızı + sıra), **Soğutmalı vitrin** (içecek hızı + hazır stok kapasitesi), **Havalandırma** (sabır + Ocak hızı), **Jeneratör** (elektrik kesintisini 1/2 kademe karşılar), **Su deposu** (su kesintisini 1/2 kademe karşılar). Çırak seviye 4: kalfa (yavaşlık ×1,2 → ×1,05), seviye 5: dördüncü çırak. Tablo: `engine_api.md` §B.7.

## 8. Kayıt dönemi (istek h)
`SaveData.EPOCH = 25`, kayıtta `epoch=25` (`v=5`).
Süresiz kota (v2.5): `temel kota × sınırla(fiyat talebi × kesinti × (1 + 0,5·(yıldız çarpanı − 1)); 0,5; 1,5)`; yıldızın yarı etkisi süresizde itibarı anlamlı kılar ve süresiz oyuncuyu sabit giderler karşısında korur (3★'da etkisiz). Yüklenen kaydın `epoch`'u yoksa ya da küçükse **ilerleme** (gün, en iyi gün, para, yıldız, stok, yükseltmeler, hazır içecek, fiyatlar, faturalar, krediler, kredi notu, maaş borcu) sıfırlanır, **ayarlar** (tempo, konuşma ve hızı, titreşim, müzik/efekt/ambiyans sesi, öğretici, çırak duyuruları, çırak teslimi, oyun akışı, süre baskısı) korunur; ilerleme varsa `progressResetNotice = true` olur. UI bir kez okuyup `clearProgressResetNotice()` çağırır. İleride `EPOCH` artırılarak aynı mekanizma yeniden kullanılır.

## 9. Denge ve ölçüm
Akıllı bot (`Campaign`): her gün önce maaş borcunu, sonra faturaları öder (önce son günü gelenler; stok parası kalmayacaksa son günü gelmeyeni erteler, son günündekini **krediyle** öder), stok parası yetmezse kredi alır, zenginse krediyi erken kapatır, yükseltme alırken stok + 3 günlük fatura/maaş payı ayırır, çırağın 4–5. seviyesi için biriktirmez. Fiyat: sabit (`pricePercent`) ya da akıllı (yıldız < 4,3 → −%5; müşteri kaçırdı ya da yıldız ≥ 4,9 → +%5; %85–125). `TestBot` çırak teslim ediyorsa beklemeyi bilir ve teslimleri kimin yaptığından bağımsız sayar.

## 10. Ölçülen sonuçlar (V25BalanceTest, "V25" satırları)
| Bot (60 gün) | Akış | Sonuç | Başarısız deneme | Kayıp/gün | Servet* | Dokunuş/sipariş |
|---|---|---|---|---|---|---|
| 0,8 sn hazırlık+yükseltme | Rahat / Dengeli / Gerçekçi | 1–60 geçer | 0 / 0 / 0 | 0,00 | 1 068 033 / 871 776 / 904 520 | 4,44 / 6,42 / 8,12 |
| 1,5 sn | Rahat / Dengeli / Gerçekçi | 1–60 geçer | 0 / 0 / 0 | 0,00 | 1 021 004 / 773 880 / 785 992 | 3,84 / 5,64 / 7,37 |
| İnsan benzeri 1,5 sn | Rahat / Dengeli / Gerçekçi | 1–60 geçer | 0 / 0 / 0 | 0,00 / 0,92 / 0,32 | 943 627 / 506 074 / 409 455 | 3,78 / 5,59 / 6,98 |
| Süresiz 0,8 sn | Rahat / Dengeli / Gerçekçi | 1–60 geçer | 0 | – | – / 692 996 / 701 930 | – / 7,72 / 9,61 |
| Hiçbir şey yapmayan | – | ≤ 5. günde başarısız | | | | |
| Hiç fatura ödemeyen | Dengeli / Gerçekçi | **15. günde tahliye** (6. günden elektrik, 7'den doğalgaz, 8'den su kesintisi; 12. günde Ocak kapalı, çay/çorba yok, ihtarname) | | | 28 759 / 24 023 | |
| Yükseltmesiz (v2.0 testi, 40 gün) | Rahat/Dengeli 28, Gerçekçi 37–39. günde takılır | | | | | |
*Servet = para + alınan yükseltmelerin fiyatı − kredi borcu.

**Fiyat stratejileri** (Dengeli, 0,8 sn, 3 tohum ortalaması, 60 gün):
| Fiyat | Süreli servet | Süreli yıldız | Süreli teslim | Süresiz servet | Süresiz teslim |
|---|---|---|---|---|---|
| %50 | −5 322 (takılır) | 1,79 | 538 | 581 (takılır) | 166 |
| %80 (düşük) | 570 400 | 5,00 | **2 844** | 478 174 | **2 713** |
| %100 (referans) | 865 328 | 5,00 | 2 808 | **696 258** | 2 504 |
| %125 (yüksek) | **1 065 874** | 4,99 | 2 317 | 629 168 | 1 590 |
| %150 | 328 709 (yıldız çöker) | 2,94 | 603 | 52 712 (takılır) | 142 |
| akıllı (%85–125) | 1 095 223 (tohum 1; referans 845 886) | 4,96 | 2 438 | – | – |
Sonuç: güçlü süreli oyuncuda yüksek fiyat en kârlı ama en az müşteriye hizmet eder; süresizde referans en kârlı; düşük fiyat en çok müşteriye hizmet eder ve yıldızı korur ama kârı en düşük makul seçenektir; uçlar kaybettirir; ayarlanan (akıllı) fiyat en iyisidir — tek baskın sabit strateji yok. Ön ölçümde yavaş insan benzeri botta %80 müşteri yığılmasıyla 1/3 tohumda takıldı, %125 en çok servet ama yıldız 4,28 (referans 4,81): yavaş oyuncu için yüksek fiyat riskli değil, düşük fiyat risklidir.

**Süresiz / süreli** (15 gün, Dengeli): gelir oranı %85, servet oranı %72 (v0.8 ilkesi "en az %70" gelirle ölçülür; sabit giderler iki modda aynı).

## 11. UI kararları (v2.5 arayüz)
Kaynak: oyuncu geri bildirimi *"Konuşmada tutarsızlıklar var. 'Stoğu oku' bazen çalışmıyor, iki kez basılınca çalışmıyor."* ve v2.5 motor özellikleri.

- **"Stoğu oku" kök nedeni ve düzeltme.** Açık okumalar (`Speaker.force`: Durumu oku, Hazır stoğu oku, Siparişi oku, Hedefi/Eklenenleri oku, fatura/banka/fiyat okumaları) sıradan bir acil duyuru gibi `SpeechQueue`'ya giriyordu: konuşan kesilmeyen bir uyarı (Elin dolu, sabır kritik) varken bekliyor, 6 sn'de bayat sayılıp atılıyor, art arda gelen uyarılarla sıra sınırından düşüyordu ("bazen çalışmıyor"); TalkBack açıkken de art arda gelen AYNI metinli duyuruyu yutuyordu ("iki kez basılınca çalışmıyor"). Düzeltme: `SpeechQueue.sayExplicit` (tek eklenen kuyruk yöntemi) her çağrıda konuşanı keserek hemen söyler, bayatlık/birleştirme/aralık/sıra sınırı uygulanmaz, oyun duyuruları onu kesmez (sonra söylenir); TalkBack açıkken metin aynıysa görünmez ayırt edici (U+200B) eklenir ve duyuru dokunma olayından sonra (90 ms) gönderilir; TTS'de `QUEUE_FLUSH`. Panel okuma seçenekleri (`CAT_INFO`) de bu yoldan geçer.
- **Yeni ekranlar** (hepsi tek dokunuş, ≥56 dp, kısa etiket, durum metinle, okuma düğmesi): *Fiyatlar* (brifing ve market; ürün satırı "Ayran, 25 lira, yüzde 100", −/+ yüzde 5 düğmeleri ve satırda TalkBack Eylemleri "Fiyatı artır/azalt", Tahmini müşteri etkisi, Referans fiyata dön), *Banka* (brifing, market, faturalar; tutar −/+ 500, vade 5/10/20 seçili durumlu, teklif, Kredi al, kredi satırında Erken kapat / Gecikmiş taksiti öde), *Faturalar* (gün sonu özeti, brifing, market; Öde / Ertele / Krediyle öde — krediyle öde önce teklifi okur, ikinci dokunuş "Emin misin? Krediyle öde" onaylar; Maaşı öde; gecikmiş ve son günü gelen önce). Gün içinde Duraklat > *Faturalar* paneli yalnızca Öde / Maaşı öde sunar (`engine.payBill/payWages`, kesinti o an kalkar).
- **Akış:** özet ekranı açık fatura ya da maaş borcu varsa en üstte "Faturalar"; "Markete git" yeri değişmedi (eski akış ve testler korunur). Tahliyede brifing ve faturalar ekranında "Güne başla" yok ("Faturalar: kirayı öde"), marketin "Güne başla"sı faturalara yönlendirir; özet "Faturalar: kirayı öde" der.
- **Brifing:** önce `Finance.notices` satırları (kırmızı çerçeve, sesli), kesinti özeti, açık fatura + yaklaşan fatura ("Kira yaklaşık 1680 lira, 7. gün sonunda kesilir"), çırak maaşı, yeni müşteri tipi, yeni yükseltme seviyeleri, kesinti yüzünden menüden çıkanlar, fiyat ortalaması. Eski "Gün sonunda N lira kira" satırı kalktı; özet ekranında sıfır "Kira" satırı yerine çırak maaşı, maaş borcu, kredi taksiti, haciz, bozulan et, yeni faturalar, açık fatura borcu, kredi borcu/notu, fiyatı pahalı/uygun bulan, çırağın teslim ettiği.
- **Kesinti etiketleri:** kapalı Ocak düğmesi "Ocak kapalı, doğalgaz kesik"; elektrikte Ekmek ısıtıcı/İçecek dolabı "…, elektrik kesik", suda tezgâh "su kesik"; `ReadyAction.CLOSED` satırı "…, Ocak kapalı, doğalgaz kesik". Olaylar: `onUtilityBlocked` hata sesi + titreşim, `onStockSpoiled`/`onFinanceNotices` uyarı sesi, `onBillPaid` para sesi (anlatım Narrator'da, ekranda kısa not).
- **Çırak:** teslim satırı "Teslim ediliyor, çırak" (dokununca okunur, teslim denenmez), Teslim et düğmesi çırağın teslim ettiği siparişi atlar; çalışanlar kutusu çalışan sayısını `getApprenticeWorkers` ile gösterir, maaş borcunda "Çırak bugün yok: maaş borcu var"; Ayarlar'da "Çırak teslimi" anahtarı; market açıklamasında günlük maaş ve sonraki seviyenin maaşı.
- **Terimler:** `ui/` metinleri sözlüğe çekildi ("lira", "yüzde N", "teslime hazır" sipariş için, "hazır döner 4 porsiyon / hazır ayran 3 bardak / hazır tepsi 18 kullanım", "stokta yok", "tezgâh", "dükkân", "sipariş 2"). `TermConsistencyTest` arayüz kaynağının tüm metin sabitlerini ve Texts çıktılarını, `A11yAudit` her ekranın görünür etiketlerini yasak terimlere karşı tarar.
- **Yeni ürün sesleri** (yeni ses yok): çay/şalgam/çorba dökme, su dolap + şişe/kutu açma, sütlaç dolap, baklava sarma, çorbanın limonu sebze sesi; köfte/tantuni/kaşar Ocak cızırtısı.
- **Sıfırlama bildirimi:** eski dönem kaydı açılışta menüde bir kez görsel kart ve sesli "Yeni sürüm: ilerlemen sıfırlandı, ayarların korundu." (ayrıntı `Narrator.progressResetText`); bayrak hemen temizlenip kaydedilir; "Yeni oyun" bildirimi düşürür.
- **Kararsız test:** `MonkeyLifecycleTest.gameMonkeyTalkBackDay3Apprentice1` hatası UI hatası değil test botu hatasıydı: rastgele dokunuş sipariş listesini (sonra da Duraklat > Faturalar'ı) açıyor, liste açıkken ana ekran TalkBack'ten gizli olduğundan istasyon düğmesi bulunamıyordu. `UiBot` önce paneli kapatır; tüm arayüz testlerinde motor tohumu sabit (`UiHarness.DEFAULT_SEED`, maymunlarda kendi tohumları; eskiden hata veren tohumlar gerileme testi).

## 12. v2.6 motor düzeltmeleri (denetim: `docs/denetim_v25_fable.md`; API: `engine_api.md` §C)

İlke aynı: her kural tek cümle. Kayıt dönemi artmadı (ilerleme korunur).

1. **Acil destek**: kasa önerilen stoğa yetmiyorsa banka, limit/engel/kredi sayısı aramadan "önerilen stok − kasa" kadar (100'e yuvarlı, en az 500) 10 günlük, en yüksek faizli bir destek kredisi verir; aynı anda yalnızca bir tane. Sıfır kasa + boş stok tuzağından her zaman bir çıkış vardır.
2. **Süresiz gün kilidi**: hiçbir sipariş ilerletilemiyor (hazır yok, iş yok, adım yok) ve eksik malzemeyi acil almaya para yetmiyorsa 20 saniye sonra bekleyen müşteriler cezasız gider ve gün biter; gün asla sonsuza dek sürmez.
3. **İflas**: (a) tahliye kararı + kasa kiraya yetmiyor + kira kredisi alınamıyor; (b) stok ve kasa tek ürüne yetmiyor + kredi ve acil destek yok; (c) art arda 3 başarısız gün + kredi/destek yok + kasa önerilen stoğun yarısından az. Motor yalnızca bildirir; oyuncuya "Dükkân kapandı, N. güne ulaştın" ve "Yeniden başla (ayarlar ve rekor korunur)" sunulur.
4. **Gün içi maaş**: maaş borcu gün içinde ödenip çırak gelirse, o günün maaşı akşam yazılır.
5. **Fiyat gün başında donar**: gün içinde fiyat değiştirmek yalnızca ertesi günü etkiler (talep, geliş, sipariş fiyatı).
6. **Kira kredisi sınırı**: limit dışı kira kredisi en çok 2 tane açık olabilir; üçüncüsü verilmez (borç sarmalı iflasla biter).
7. **Kredi notu çiftliği kapandı**: erken kapatmada +3 not yalnızca kredi önceki bir gün alındıysa ve en az bir taksiti vadesine geldiyse.
8. **Kesinti kalkınca**: gün içinde fatura ödenince menüyle birlikte talep ve süresiz kota da düzelir.
9. **Otomatik fatura ödemesi (ayar, varsayılan açık)**: gün açılışında geciken, gün sonunda son günü gelen faturalar kasa yetiyorsa (önerilen stok parası kasada kalacak biçimde) otomatik ödenir; yetmezse normal gecikme ve kesinti akışı sürer. Kapatan oyuncu v2.5 gibi elle öder.

Testler: `V26EngineTest` (20 test). Bilinçli uyarlanan v2.5 testleri: elle ödeme/ödememe senaryoları otomatik ödemeyi kapatır (`V25FinanceTest.day`, `V25BalanceTest.neverPayingBot…`, `V25UiTest` üç kesinti testi); aynı gün al-kapat kredi notu beklentisi 63 → 60 (`V25BankPricingTest`, `V25UiTest.bankScreen…`).

