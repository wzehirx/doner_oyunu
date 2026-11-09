# v2.5 Bağımsız Denetim Raporu (salt okunur) — Fable

Kapsam: `docs/spec.md`, `docs/engine_api.md`, `docs/design_v05..v25.md`, `docs/terminoloji.md`; `engine/` (GameEngine, Finance, Bank, Pricing, SaveData, SpeechQueue, Narrator, Catalog, UtilityStatus, DayConfig, Order/OrderLine, Shop), `audio/` (Speaker, SoundManager), `ui/` (GameScreen, BillsBox/BillsScreen, BankScreen, PriceScreen, Briefing, Summary, Shop, Settings, Menu, Help, Ui, ActionMenu), `MainActivity`, test ağacı (628 `@Test`). Yöntem: yalnızca kod okuma; hiçbir dosya değiştirilmedi, test çalıştırılmadı. Dal `ccr-7f439f66-f4ms8d`, HEAD `392e828` (UI ajanı çalışma ağacı değişebilir; satır numaraları HEAD'e göredir).

Önem: **P0** = oyuncunun ilerleyemediği / parayı bozan / konuşmayı kesen; **P1** = sık yaşanacak ciddi hata ya da erişilebilirlik kaybı; **P2** = iyileştirme.

---

## 0. En kritik 10 bulgu (özet)

| # | Önem | Bulgu | Yer |
|---|---|---|---|
| 1 | P0 | **Sıfır kasa + boş stok tuzağı**: gün sonu otomatik kesintiler (maaş, taksit, haciz) kasayı tam 0'a indirebilir; stok yoksa süreli günde müşteriler gider → gün başarısız → para gün başına (0) döner → sonsuz tekrar; süresiz günde gün hiç bitmez. Banka engelliyse (3 kaçan taksit / 3 kredi) çıkış yok, yalnızca "Yeni oyun". | `Finance.endOfDay` 196-199, 213-219, 238-240; `GameEngine.failDay` 2886-2890; `checkEnd` 2231-2233 |
| 2 | P1 | **Gün içi maaş ödemesi çırağı bedava çalıştırır**: `workedApprenticeLevel` yalnızca gün açılışında yazılır (0); Duraklat > Faturalar > Maaşı öde ile çırak gelir, gün sonunda o güne maaş yazılmaz. Tekrarlanabilir ekonomi sömürüsü. | `GameEngine.openEffects` 866, `payWages` 317-323, `finishDay` 2849 |
| 3 | P1 | **Fiyat talebi gün başında donar, satış fiyatı canlı okunur**: `typeDemand/priceArrival` kurucuda, kalem oranı ise `addOrder`'da `save.prices`'tan alınır. Bugün UI gün içinde fiyat ekranı açmıyor ama motor "gün içinde değiştirmek o günü etkilemez" sözünü tutmuyor; gelecekteki bir Duraklat>Fiyatlar düğmesi anında "%50 talep + %150 fiyat" sömürüsü yaratır. | `GameEngine` 135-136, 167-173, 2690-2696; `engine_api.md` B.2 |
| 4 | P1 | **Konuşma sırası TalkBack süresini tahminle kilitler**: `announceForAccessibility`'nin bitiş geri çağrısı yok; `estimateMs = 450 + 62·karakter` ile `busyUntil` kurulur. Hızlı TalkBack (2-3×) kullanan kör oyuncuda 250 karakterlik "Durumu oku" sırayı ~16 sn meşgul sayar; açık okuma kesilemez olduğundan bu sürede gelen normal duyurular 3,5 sn'de bayatlayıp **atılır** (müşteri geldi, adım bitti). | `Speaker.playNow` 242-249, `estimateMs` 225; `SpeechQueue.start` 210-221, `STALE_MS` 35 |
| 5 | P1 | **BillsBox iki adımlı "Krediyle öde" onayı odak kaybettirir**: ilk dokunuş `rebuild()` ile tüm satırları yeniden yaratır; TalkBack odağı yok olan düğmede kalır, oyuncu "Onayla" düğmesini yeniden aramak zorunda. "Ertele" de aynı (yeniden kurulur, odak taşınmaz). | `BillsBox.payWithLoan` 219-226, `defer` 206-211, `rebuild` 100-103 |
| 6 | P1 | **Odak taşıma + açık okuma aynı anda**: ödeme/kredi işlemlerinden sonra hem `force(text)` (90 ms gecikmeli duyuru) hem `focusOn(header)` (TalkBack başlığı okur) çağrılıyor; iki konuşma yarışır, hangisinin duyulacağı TalkBack sürümüne bağlı. | `BillsBox.pay` 183-184, `payWages` 198-199, `payWithLoan` 234-236; `BankScreen.takeLoan` 230-231, `repay` 249-250 |
| 7 | P1 | **Bilişsel yük**: 5 fatura türü × 3 kademe zorluk + maaş borcu + 3 krediye kadar taksit + haciz + fiyat; brifingte aynı uyarılar iki kez okunur (BriefingScreen.onEnter + motor `onFinanceNotices`), 4 geciken faturada tek `say` 600+ karakter. Hiçbir "otomatik ödeme" yok. | `BriefingScreen.onEnter` 140-151; `GameEngine.openEffects` 878-881; `Finance.lateText` 307-329 |
| 8 | P2 | **Borç sarmalı için bitiş durumu yok**: kira kredisi limitsiz (`rentLoan`), 20 gün vadede %50 faiz; 3+ kredi sonrası `available()=0` ama kira kredisi yine verilir; taksitler kasadan önce alınır → oyuncu sonsuza dek 0 kasa/kesintiyle oynar, "iflas → yeniden başla" önerisi yok. | `Bank.takeLoan` 115-130, `Finance.payWithLoan` 169-178 |
| 9 | P2 | **Kredi notu çiftliği**: 500 lira / 5 gün kredi al, aynı anda `repay` → maliyet ~4 lira, kredi notu +3; birkaç tekrarla not 100 (faiz binde 6, limit ×1,5). | `Bank.repay` 133-142, `Loan.payoffAmount` 60-63 |
| 10 | P2 | **"Hazır stoğu oku" iki kez**: kök neden tahmini aşağıda (§2.1). Yapılan düzeltme (sayExplicit + U+200B ayırt edici + 90 ms) doğru yönde; kalan riskler: U+200B'nin bazı TTS motorlarında okunması, `isTalkBackOn()` dokunarak keşif kapalıyken false dönüp TTS'e düşmesi (çift konuşma), ve cihazda doğrulanmamış olması. | `Speaker.force` 139-177, `isTalkBackOn` 103-106 |

---

## 1. Gerçek hatalar ve ekonomi sömürüleri

### 1.1 (P0) Sıfır kasa / boş stok ilerleme tuzağı
- **Nerede**: `Finance.endOfDay` (`app/src/main/java/com/doner/oyunu/engine/Finance.java:196-199` maaş, `:213-219` taksit, `:238-240` haciz) her kalemde `min(borç, max(0, money))` alır; üçü art arda kasayı tam 0 bırakabilir. `GameEngine.failDay` (`GameEngine.java:2886-2890`) parayı/stoğu gün başı anlık görüntüsüne döndürür; `checkEnd` (`:2231-2233`) süresiz günde yalnızca "kota doldu ve sipariş kalmadı" ile biter.
- **Senaryo**: 28. gün, 2 kredi taksiti + maaş; gün sonu kasa 0. Markette alım yapılamaz, stok 1-2 porsiyon. Süreli: ilk 3 müşteri stok yok → kaybedilir → yıldız 1,0 altı → gün başarısız → kasa yine 0 → aynı döngü. Süresiz: siparişler teslim edilemez, kota dolmaz, gün hiç bitmez; tek çıkış "Günü bırak" (kayıt yazılmaz). Banka 3 kredi/3 kaçan taksit yüzünden kapalıysa **oyun ilerleyemez**. v0.8 belgesi bunu "bilinen sınır" demişti; v2.5 otomatik kesintilerle olasılığı ciddi biçimde artırdı.
- **Öneri (S/M)**: (a) Motor: süresiz günde "yapılabilir iş yok, stok yok, para acil alıma yetmiyor" 20 sn sürerse `finishDay` (müşteri cezasız gider) ya da `failDay`; (b) `Bank`: "stok kredisi" = kira kredisi gibi limit/engel aranmayan küçük (örn. önerilen stok maliyeti kadar) kredi; (c) UI brifing: kasa < önerilen stok maliyeti ise açık uyarı + Banka düğmesi öne. Test: `V25FinanceTest` yeni senaryo "money=0, stok=0, kredi engelli, gün bitebilmeli".

### 1.2 (P1) Gün içi "Maaşı öde" → bedava çırak günü
- `GameEngine.openEffects` (`:866`) `workedApprenticeLevel = getApprenticeLevel()` yalnızca bir kez; `payWages()` (`:317-323`) `refreshUtilities()` ile çırağı o an işe alır ama `workedApprenticeLevel` 0 kalır; `finishDay` (`:2849`) `Finance.endOfDay(..., 0)` → maaş yazılmaz. `V25FinanceTest.wageIsPaidAtDayEndAndUnpaidWageKeepsTheApprenticeHome` (`:67-74`) gün içi ödemeyi dener ama gün sonu `wagesDue` kontrol etmez.
- **Senaryo**: Gün sonu markette tüm parayı yükseltmeye harca → maaş borcu; ertesi gün hazırlıkta ilk satıştan sonra Duraklat > Faturalar > Maaşı öde → çırak bütün gün çalışır, akşam maaş 0. Her gün tekrarlanabilir (günlük 250-1650 lira kazanç).
- **Öneri (S)**: `payWages()` başarıyla bitince `workedApprenticeLevel = Math.max(workedApprenticeLevel, upgrades.apprenticeLevel())` (ya da kalan süreyle orantılı). Test ekle.

### 1.3 (P1) Fiyatın gün başı/gün içi tutarsızlığı
- `GameEngine` kurucusu `computeDemand()` (`:167-173`) ve `quota` (`:136`) fiyatları dondurur; `addOrder` (`:2690-2696`) ise `Pricing.ratio(save, p.id)` ile **canlı** `save.prices` okur. Ayrıca `Pricing.allDefault(save)` kurucuda true ise `typeDemand` boş kalır, sonradan fiyat değişse de `pickProduct` (`:2780`) duyarlılık 0 kullanır.
- Bugün UI yalnızca brifing/market'ten `PriceScreen` açıyor ve `GameScreen.create()` `a.save.copy()` ile çalışıyor; bu yüzden **şu an** oyuncu tetikleyemez. Ama `engine_api.md` B.2 "gün içinde değiştirmek o günü etkilemez" diyor; motor bunu garanti etmiyor.
- **Öneri (S)**: Kurucuda `Map<String,Integer> dayPrices = new LinkedHashMap<>(save.prices)` dondur; `addOrder`/`pickProduct` bunu kullansın. Test: kurucudan sonra `Pricing.setAll(save,150)` → yeni siparişlerin `priceRatio()==1.0`.

### 1.4 (P2) Borç sarmalı ve bitiş durumu yokluğu
- `Bank.takeLoan(rentLoan=true)` (`Bank.java:115-130`) limit, engel ve `MAX_LOANS` aramaz; `Finance.payWithLoan` (`Finance.java:169-178`) kira için her zaman OK. `BillsBox.LOAN_TERM=10` → günlük %2,5 × 10 = **%25 faiz**; kira haftalık (gün 56'da 9100 lira) → her hafta +25%. Taksitler `endOfDay`'de maaştan sonra, faturalardan önce kasadan alınır; kaçan taksit her gün +%5 (`:224`) birikir.
- **Senaryo**: 30. günden sonra yavaş oyuncu her hafta kirayı krediyle öder; 3 hafta sonra 4-5 açık kredi, günlük taksit > günlük satış; kasa her gün 0; elektrik/gaz/su kesik; kira yine krediyle. Oyun "kilitlenmez" ama oynanamaz; hiçbir ekran "iflas ettin, yeniden başla" demez.
- **Öneri (M)**: (a) kira kredisinde de en çok 2 açık kira kredisi; (b) "İflas" durumu: toplam kredi borcu > son 10 gün satışının 2 katı ve kasa < kira → gün sonunda "Dükkân kapandı: 60 günlük kampanyada N. güne ulaştın" + "Yeni oyun (ayarlar korunur)"; (c) Banka ekranında "günlük taksit toplamı / dünkü satış" oranını söyle.

### 1.5 (P2) Kredi notu çiftliği
- `Bank.repay` (`:133-142`) her erken kapatmada +3; `Loan.payoffAmount` (`Loan.java:60-63`) kalan faizin %25'i → 500 lira / 5 gün / binde 8: faiz 20, erken kapatma 500+5 = 505. 14 tekrar ≈ 70 lira ile not 60→100. Para üretmez ama faizi düşürür, limiti ×1,5 yapar.
- **Öneri (S)**: Erken kapatma bonusu yalnızca `takenDay < save.day` ve en az 1 taksit ödendiyse; ya da günde bir kez.

### 1.6 (P2) Kesinti kalkınca gün içi durum kısmen güncellenir
- `GameEngine.refreshUtilities` (`:325-329`) yalnızca `util` ve `menu`'yü yeniler; `quota` (`:136`), `priceArrival`/`typeDemand` (menüye bağlı, `:167-173`) ve `PrepBox` düğmeleri (`GameScreen.java:2199-2205`, `friesBtn` yalnızca kurulumda `menuHasFries()` true ise) güncellenmez. Hazırlıkta doğalgaz faturası ödenirse Ocak açılır ama "Patates kızart" hazırlık düğmesi belirmez; süresiz kota ×0,9 kalır.
- **Öneri (S)**: `refreshUtilities` → `computeDemand()`; `PrepBox` düğmelerini `update()`'de görünür/gizli yap.

### 1.7 (P2) Kayıt ve dönem (epoch)
- `SaveData.parse` (`SaveData.java:193, 250-255`): `epoch` satırı bozuksa (tek karakter) `epoch=0` → ilerleme sessizce sıfırlanır (bildirimle). Tüm diğer satırlar bozulmaya karşı toleranslı, bu satır tek başına yıkıcı. **Öneri (S)**: `epoch` ayrıştırılamazsa `day/money/stars` varsa EPOCH kabul et (ya da `v=5` varsa). Çift sıfırlama yok: `MainActivity.onCreate` (`:47-51`) bildirimi alıp hemen `saveNow()` yazıyor, `reloadSave()` artık `epoch=25` okur. Ayarlar eksiksiz korunuyor (`copySettingsTo` 111-125). `KEY_STARTED` sıfırlanmadığından menüde "Devam et, gün 1" + "Yeni oyun" birlikte çıkar (kabul edilebilir, belgelenmiş).
- `Finance.billAmount` javadoc "%6" (`Finance.java:59`) kodda %5 (`TAX_RATE`). Belge/yorum tutarsızlığı.

### 1.8 Yarış koşulları / kilitlenme taraması (sorun bulunmadı)
- Tüm motor çağrıları ana iş parçacığında (Choreographer + ana Handler); TTS geri çağrıları `handler.post` ile ana iş parçacığına alınır (`Speaker.finished` 288-296); `SoundPool.OnLoadCompleteListener` ana Looper'da. Gerçek bir yarış yok.
- Tahliye yönlendirmesi UI'da tam: Summary (`SummaryScreen.java:92-106`), Briefing (`:102-106`), Bills (`:78-82`), Shop (`:105-111`) hepsi `UtilityStatus.evicted()` kontrol ediyor; kira kredisi her zaman verildiğinden kilit yok. `failEvicted` (`GameEngine.java:885-889`) günü ilerletmez, gecikme bedeli donar (doğru).
- Döndürme: manifest `screenOrientation=portrait` + geniş `configChanges` → gün ortası Activity yeniden yaratılmaz.
- Süreli gün her zaman biter (`checkEnd` mesai 30 sn sonra siparişleri cezasız kapatır). Süresiz gün: §1.1.

---

## 2. Erişilebilirlik

### 2.1 "Stoğu oku iki kez basınca çalışmıyor" — kök neden tahmini
Kod geçmişine göre (`git diff eb4cddd HEAD -- Speaker.java`) eski `force()` = `queue.say(text, true)`:
1. **Sıra düzeyinde**: açık okuma sıradan bir acil duyuruydu. Konuşan duyuru `CAT_ALERT`/`CAT_PATIENCE` (kesilemez) ise `pending`'e giriyor; `MAX_URGENT_PENDING=2` ile art arda gelen uyarılar onu düşürüyor; 6 sn `URGENT_STALE_MS` sonra atılıyordu. Hazırlıkta "Elin dolu…" (ALERT) sık; ilk basış boşta hemen çıkıyor, ikinci basış konuşan ALERT'in arkasında kaybolup gidiyordu. **Bu, raporlanan belirtiyle birebir uyuşur.**
2. **TalkBack düzeyinde**: aynı metinli art arda `TYPE_ANNOUNCEMENT` TalkBack'in yinelenen sözce bastırmasına takılabilir; UI ajanı U+200B ile ayırıyor (`Speaker.java:160-172`).
3. **Kalan riskler**: (a) `isTalkBackOn()` (`:103-106`) `isTouchExplorationEnabled()` ister; dokunarak keşfi kapalı TalkBack kullanıcısında TTS yoluna düşer → TalkBack odak okuması + kendi TTS'i **çift konuşur** (ayrı bir şikâyet kaynağı olabilir). (b) U+200B'nin cihaz TTS'inde sessiz olduğu doğrulanmalı; daha güvenli ayırt edici: dönüşümlü sondaki nokta ("…" / "… .") ya da U+00A0. (c) Robolectric testleri (`V25UiTest.java:90-108`) yalnızca event gönderildiğini doğrular, TalkBack'in konuştuğunu değil — cihaz testi şart.
4. **Öneri (S)**: `isTalkBackOn()` → `am.isEnabled() && !am.getEnabledAccessibilityServiceList(FEEDBACK_SPOKEN).isEmpty()`; explicit okumalar için TalkBack'te `announceForAccessibility` yerine kendi sabit canlı bölge `TextView`'ını (ASSERTIVE) dönüşümlü metinle güncellemeyi dene.

### 2.2 (P1) Konuşma süresi tahmini ve atılan duyurular
- `Speaker.playNow` (`:242-249`) TalkBack'te `estimateMs` döner; `SpeechQueue.start` (`:220`) `busyUntil` kurar; `finished()` yalnızca TTS'ten gelir. Açık okuma `currentInterruptible=false` (`:216-217`). "Durumu oku" (`Narrator.statusText` `:729-752`, tipik 200-300 karakter) → 13-19 sn meşgul; bu arada `onCustomerArrived` (normal, 3,5 sn bayat) **atılır**, `onPatienceWarning` (acil) en çok 2 bekler.
- **Öneri (S/M)**: TalkBack'te `busyUntil`'ı `min(est, 4000)` ile sınırla ya da açık okumayı `CAT_ALERT`/`CAT_PATIENCE` tarafından kesilebilir yap; Ayarlar'a "Anlatım hızı" zaten var → TalkBack kipinde de `estimateMs / rate` uygula.

### 2.3 (P1) Fatura/banka ekranlarında odak kayıpları
- `BillsBox.rebuild()` (`:100-103`) `list.removeAllViews()`: "Ertele" (`:206-211`) ve "Krediyle öde" ilk adımı (`:219-226`) odaklı düğmeyi yok eder; TalkBack odağı belirsiz yere düşer. Onay akışı kör oyuncu için 2 yerine 4+ hareket.
- `pay`/`payWages`/`payWithLoan` sonrası `host.focus(header)` + `host.say(...)` eş zamanlı (`:183-184, 198-199, 234-236`); `BankScreen` aynı (`:230-231, 249-250, 268-269`). TalkBack odak okuması ile uygulama duyurusu yarışır.
- **Öneri (S)**: satırları yerinde güncelle (metin/etiket değişimi), "Onayla" düğmesinin kendisine odak taşı; işlem sonrası ya odak ya duyuru (başlık metni sonucu zaten içeriyor → yalnızca `focus(header)` yeterli, duyuru 400 ms sonra).

### 2.4 (P2) Diğer TalkBack bulguları
- `MainActivity.show` (`:130`) pane title + `moveFocus` (`:165-175`) başlığa `ACTION_ACCESSIBILITY_FOCUS`: ekran değişince başlık **iki kez** okunur (pencere başlığı + başlık odağı). Öneri: TalkBack açıkken `setAccessibilityPaneTitle` kullan, odağı başlığa değil ilk içerik öğesine taşı.
- `GameScreen.updateEmergency` (`:1472`) eksik malzeme belirince odağı **zorla** Acil al düğmesine taşır (Narrator da acil duyurur). Oyuncu bir eylem satırındayken odak çalınır; sonra geri dönüş yok. Öneri: yalnızca oyuncu 1,5 sn hareketsizse taşı ya da hiç taşıma (duyuru + sabit konum yeterli).
- Brifing uyarıları iki kez: `BriefingScreen.onEnter` (`:140-151`) + motor `onFinanceNotices` (`GameEngine.java:878-881`) → aynı 1-4 cümle hazırlık başında yeniden. Öneri: brifingte okunduysa motorun `onFinanceNotices`'ını UI sessizce (yalnızca `notice`) işlesin.
- `ActionRow` etiketi `Texts.actionLabel` (`Texts.java:424-431`) 60'ı aşınca **kelime ortasından keser** (`substring(0,60)`); "Karışık döner tabağı: Et ve tavuk dilimle, Döner makinesi, boy seç" gibi v2.5 ürünlerinde olası. `priceRowLabel`/`billLabel`/`BillsBox.cap` de ham kesme yapıyor. Öneri: son virgülden kes.
- `Switch` (SettingsScreen `:221-240`) `setShowText(true)` + "Açık/Kapalı" iyi; ancak `addSwitch` etiketleri "Çırak teslimi" gibi; A11yAudit toggle kuralı yalnızca "Süre baskısı" ile başlayanları denetler (`A11yAudit.java:99-103`) → yeni anahtarlar denetim dışı.
- Renk-yalnızca bilgi: `ActionRow`/`QueueRow`/`StationTile` renk kodları her zaman metinle de ("iş var", "dolu", "stokta yok") yineleniyor — iyi. `Ui.dim` (alfa 0,7) ile "alınamaz" durumu `BankScreen.takeBtn` etiketinde ", alınamaz" olarak söyleniyor — iyi. `ShopScreen` satın al düğmesi "paran yetmiyor" söylüyor — iyi.
- ≥56 dp: tüm `Ui.button` 60 dp; `ActionRow` 60, `QueueRow` 56, `StationTile` 108; `BankScreen` vade düğmeleri 60 dp genişlik/yükseklik — uyumlu. `GameScreen` alt çubukta üç düğme 15 sp metinle dar ekranda genişlik <56 dp olabilir (yalnızca uyarı düzeyi; A11yAudit uyarı veriyor).

---

## 3. Oyun tasarımı ve denge

### 3.1 Ekonominin gerçekçiliği (60 gün)
- Formüller tutarlı ve botla doğrulanmış (`V25BalanceTest`: tüm akışlar 1-60, 0 başarısız; hiç ödemeyen 15. günde tahliye). Kira haftalık 7×(200+20·(g−1)) (gün 7: 2240, gün 56: 9100), elektrik 231→~900, vergi %5 — gerçekçi oran. Kredi faizi günlük binde 6-25 (10 günde %6-25) oyun ölçeğinde makul.
- **Zayıf nokta**: tüm denge "bot" ile ölçülmüş; bot her gün fatura ekranına girip önce son günü gelenleri ödüyor (`Campaign.java:157-177`). Kör oyuncunun bunu her gün 3-5 ekran gezerek yapması gerekiyor. Design_v25 §0 ilkesi "her kural tek cümle" karşılanıyor ama **kural sayısı** 10'u aştı.

### 3.2 (P1) Bilişsel yük ve sadeleştirme önerileri
1. **Otomatik ödeme ayarı (varsayılan açık)**: "Faturaları kasada para varsa son ödeme gününde otomatik öde." Gün sonu özeti "Elektrik faturası ödendi: 231 lira" der; oyuncu yalnızca parası yetmediğinde fatura ekranına çağrılır. Kesinti mekaniği, banka ve "Ertele" korunur ama tercih olur. Botun yaptığını oyuncu adına yapar; "sıkıcı iş" kalkar, zorluk kalır. (M; `Finance.endOfDay`'e bir adım + ayar + Settings satırı)
2. **Elektrik/doğalgaz/su → tek "Dükkân faturası"** (haftalık, 3 kesinti kademesi tek merdivende) ya da en azından **aynı gün kesilsin** (3-4-5 yerine hepsi 3): oyuncunun üç ayrı son ödeme günü takip etmesi gerekmesin. (M)
3. **Krediyle öde** onayında sayıyı öne al: "Kredi: 1500 lira, 10 gün, günlük taksit 169 lira" (şimdiki `quoteText` toplam/faiz/taksit sıralı, uzun).
4. **İlk fatura öğreticisi**: 3. gün sonu ilk elektrik faturası kesildiğinde tek cümlelik öğretici ("Faturan var. Faturalar düğmesinden Öde; iki gün içinde ödersen sorun yok."). Şu an yalnızca Yardım bölümünde (`Texts.helpSections` 955-963).
5. **Brifing 12-15 satır** (`Texts.briefingLines` 590-640): TalkBack kullanıcısı için uzun; "Ayrıntıları oku" düğmesi altında katlanabilir yap, ilk 4 satır (uyarı, kasa, hedef, yeni ürün) kalsın.

### 3.3 "İlk 5 dakika"
- Gün 1 öğretici kısa ve iyi (TUT_* tek cümle). Ancak menüde "TalkBack açıkken sağa sola kaydırarak gez" ipucu var, oyun akışı sorusu brifingte sesli, sonra hazırlık, sonra müşteri — 4 ekran. Kabul edilebilir.
- Risk: 2. gün ayran, 3. gün patates+ilk fatura+çırak teklifi (3000 lira, kasada ~6-7k) aynı anda → karar yükü 3. günde zirve yapar. Çırağı 4. güne, patatesi 5. güne kaydırmak öğrenme eğrisini yumuşatır (S; `Catalog` sabitleri; denge testleri yeniden koşulmalı).

### 3.4 Gereksiz karmaşık / kaldırılabilir
- **Vergi haczi** (gün sonu kasadan + %20): oyuncunun göremeyeceği ani para kaybı; vergi 10 günde bir zaten otomatik kesilebilir ("Vergi 540 lira kesildi") → haciz mekaniği tamamen kaldırılabilir (S).
- **Jeneratör / Su deposu**: yalnızca fatura ödemeyen oyuncuya yarar; otomatik ödeme gelirse işlevsiz. Kaldır ya da "kesintiyi 1 gün geciktirir"e çevir.
- **Seviye 4-5 yükseltmeler**: açılış günleri 28-54 arası ve fiyatlar 30-100k; yavaş oyuncunun serveti 400k (tablo §10) → ulaşılabilir ama "azalan getiri" (döner ×0,50→0,45) fark edilmez. Ya etkileri belirginleştir ya 4-5'i birleştir (tek "Usta seviye").
- `v06Balance` modu motorda (`GameEngine.java:117-141`): yalnızca eski ölçüm için; üretim kodunda dallanma yaratıyor (10+ `if (v06Balance)`). Test tarafına taşınabilir.

---

## 4. Kod kalitesi, bakım, testler

- **Boyut**: `GameEngine.java` 2896 satır (durum sorguları + kaynak modeli + malzeme + hazırlık + çırak + spawn + gün sonu tek sınıfta), `GameScreen.java` 2471 (ekran + 6 iç sınıf + geri bildirim), `Texts.java` 1032, `Narrator.java` 1083. Riskli bölgeler: `startStepImpl` (961-1046, 4 iç içe durum), `getReadyActions` (406-459), `Finance.endOfDay` (190-271; 5 sorumluluk, yan etkiler `s`'ye yazılır), `GameScreen.refresh` (1258-1315 her 100 ms tüm etiketleri yeniden hesaplar: `Texts.stationShort` × 7, `getReadyActions`, `getQueueSummary` → `hasDoable` → her sipariş için `getReadyActions` tekrar; O(sipariş²) ama küçük).
- **Ayrıştırma önerileri (M/L)**: `ApprenticeBrain` (runApprentices/assign/prep), `ToppingFlow` (addTopping/auto/finish/remove), `DayLedger` (finishDay/accountWaste/Finance), `StationOptions` (getStationOptions ailesi). `GameScreen.Feedback` ayrı dosya; `PrepBox`/`WorkersBox` ayrı.
- **Üretim kodunda test kancaları**: `GameScreen.testSeed` statik (paylaşılan durum), `FocusHeaderAccess`, `v06Balance`. Kabul edilebilir ama belgelenmeli.
- **Kararsız test**: `MonkeyLifecycleTest` artık `GameScreen.testSeed` sabitliyor (`:29-38`), `UiHarness.DEFAULT_SEED` var → nanoTime kaynaklı kararsızlık giderilmiş. Kalan rastgelelik: `GameScreen.fx = new Random()` (yalnızca ses perdesi), `SoundManager.rnd` (varyant seçimi) — testleri etkilemez. `FixesUiTest:160` TTS yolunda "konuşma hiç bitmeyebilir" üst sınıra güveniyor (zamanlamaya duyarlı).
- **Test boşlukları** (hiç test edilmeyen):
  1. Gün içi `payWages` sonrası gün sonu maaş (§1.2).
  2. Motor kurulduktan sonra fiyat değişiminin siparişe etkisi (§1.3; `orderPriceUsesThePlayersPriceFixedAtOrderTime` yalnızca sipariş anını sınar).
  3. Sıfır kasa + boş stok + banka engelli günün bitebilirliği (süreli ve süresiz) (§1.1).
  4. Kira kredisi ile 4+ kredi ve `Bank.available()==0` sonrası davranış; kredi notu çiftliği.
  5. Speaker: TalkBack kipinde uzun açık okumanın normal duyuruları bayatlatması (SpeechQueue sahte saatle sınanabilir).
  6. BillsBox: "Ertele"/"Krediyle öde" sonrası erişilebilirlik odağının nerede olduğu (Robolectric `isAccessibilityFocused` ile).
  7. `refreshUtilities` sonrası `PrepBox` patates düğmesi / kota.
  8. `SaveData.parse` yalnızca `epoch` satırı bozuk kayıt.
  9. v2.5 Yardım/Brifing metinlerinin terim sözlüğüne uyumu (`TermConsistencyTest` 3 test var; `helpSections` kapsamı?).
  10. `Finance.notices` ile `UtilityStatus` kademe metinlerinin aynı gün aynı kademeyi söylediği (lv/late hesapları iki yerde).
- Dokümantasyon: `engine_api.md` 640 satır, §B ve §A ayrımı iyi; `Finance.billAmount` javadoc %6 hatası; `design_v25` §4 "iki gün sonra" ile `DUE_DAYS=2` uyumlu.

---

## 5. Unity'ye taşıma: kısa ve dürüst değerlendirme

**Önerilmez (bu kod tabanı ve bu hedef kitle için).**

- **Erişilebilirlik en büyük risk**: Unity UI, Android `AccessibilityNodeInfo` ağacı üretmez; TalkBack dokunarak keşif, Eylemler menüsü (`ActionMenu`), başlık gezintisi, canlı bölge, pane title, odak yönetimi — bunların hepsi burada saf Android View ile ücretsiz geliyor. Unity'de ya ekranı tamamen kendi TTS'inizle "ses arayüzü" olarak yeniden tasarlarsınız (TalkBack jestleriyle çakışır; kullanıcılar TalkBack'i kapatmak zorunda kalır) ya da üçüncü parti eklentilere (Unity Accessibility Plugin vb.) güvenirsiniz; hiçbiri özel eylem menüsü + canlı bölge + odak kurtarma düzeyini vermez. Spec'in "%100 görme engelli kullanılabilirlik ZORUNLU" maddesi tek başına karar verdirir.
- **Motor portu (C#)**: `engine/` saf Java, android bağımsız, ~9k satır; C#'a mekanik çeviri 2-4 hafta (Random uyumu: `java.util.Random` → deterministik tohum testleri kırılır; `SplitMix64` kopyalanabilir). 628 testin ~350'si motor (JUnit → NUnit, çevrilebilir), ~280'i Robolectric UI testi (**tamamen kaybolur**; A11yAudit kuralları Unity'de denetlenemez).
- **Kazanç**: iOS/masaüstü için tek kod, görsel efektler, ses motoru (3B pan, mixer). Bu oyunun görseli ikincil; stereo pan ve varyant havuzu SoundPool ile zaten var.
- **Alternatifler**: iOS isteniyorsa Kotlin Multiplatform ile motoru paylaşıp UI'yi her platformda yerel (UIKit VoiceOver) yazmak, erişilebilirliği korur. Masaüstü/web isteniyorsa motor C#/TS portu + yerel erişilebilir UI. Unity yalnızca "görsel bir 3D dükkân simülasyonu" hedefi varsa ve TalkBack desteği gözden çıkarılırsa anlamlı.

---

## 6. v2.6 için önerilen iş listesi

Çakışma sütunu: UI ajanının şu an dokunduğu dosyalar `ui/`, `audio/`, `MainActivity`, `Texts` → bu dosyalara dokunan maddeler ajanla çakışır; `engine/` maddeleri bağımsız.

| Önem | İş | Büyüklük | Dosyalar | UI ajanıyla çakışma |
|---|---|---|---|---|
| P0 | Sıfır kasa/boş stok tuzağı: süresiz günde otomatik bitiş + "stok kredisi" (limit aranmayan küçük kredi) + brifing uyarısı | M | `GameEngine.checkEnd`, `Bank`, `Finance`, brifing satırı | motor kısmı hayır; brifing satırı (`Texts`) **evet** |
| P0 | TalkBack konuşma süresi: `busyUntil` üst sınırı / açık okumayı ALERT'le kesilebilir yapma / TalkBack kipinde hız | S | `Speaker`, `SpeechQueue` | `Speaker` **evet** (audio/), `SpeechQueue` hayır |
| P1 | Gün içi maaş ödemesinde `workedApprenticeLevel` güncelle + test | S | `GameEngine.payWages`, `V25FinanceTest` | hayır |
| P1 | Fiyatları motor kurucusunda dondur + test | S | `GameEngine`, `V25BankPricingTest` | hayır |
| P1 | BillsBox: yerinde güncelleme, Onayla düğmesine odak, odak+duyuru yarışını kaldır (BankScreen dahil) | M | `BillsBox`, `BankScreen` | **evet** |
| P1 | "Faturaları otomatik öde" ayarı (varsayılan açık) + gün sonu özet cümlesi | M | `Finance.endOfDay`, `SaveData` (ayar + epoch korunan listesi), `SettingsScreen`, `Texts` | motor hayır; ayar ekranı/Texts **evet** |
| P1 | Brifing çift uyarı: motorun `onFinanceNotices`'ı GameScreen'de yalnızca görsel not | S | `GameScreen.Feedback`/`Narrator` | **evet** |
| P1 | `isTalkBackOn()` tanımını konuşan servis varlığına çevir; U+200B'yi cihazda doğrula / alternatif ayırt edici | S | `Speaker` | **evet** |
| P1 | İlk fatura öğretici cümlesi (3. gün sonu) | S | `SummaryScreen`/`Texts` | **evet** |
| P2 | Borç sarmalı: kira kredisi sınırı + iflas/yeniden başla durumu | M | `Bank`, `Finance`, `SummaryScreen` | motor hayır; özet ekranı **evet** |
| P2 | Kredi notu çiftliği: erken kapatma bonusu koşulu | S | `Bank.repay` | hayır |
| P2 | `refreshUtilities` → `computeDemand`; PrepBox düğmeleri dinamik | S | `GameEngine`, `GameScreen.PrepBox` | PrepBox **evet** |
| P2 | Etiket kesmelerini kelime sınırından yap (`actionLabel`, `priceRowLabel`, `billLabel`, `BillsBox.cap`) | S | `Texts`, `BillsBox` | **evet** |
| P2 | Ekran değişiminde çift başlık okuması (pane title vs. odak) | S | `MainActivity.moveFocus` | **evet** |
| P2 | Acil al odak çalmasını yumuşat | S | `GameScreen.updateEmergency` | **evet** |
| P2 | `epoch` satırı bozuk kayıtta ilerlemeyi koru; `billAmount` javadoc %5 | S | `SaveData`, `Finance` | hayır |
| P2 | Vergi haczini kaldır / vergiyi otomatik kes; jeneratör-su deposunu sadeleştir (tasarım kararı) | M | `Finance`, `Catalog`, `Texts`, belgeler | kısmen |
| P2 | Test boşlukları (§4 listesi 1-10) | M | `app/src/test` | UI testleri **evet** |
| P2 | `GameEngine` ayrıştırma (ApprenticeBrain, ToppingFlow, DayLedger); `v06Balance`'ı test tarafına taşı | L | `engine/` | hayır (ama UI ajanı bitmeden başlanmamalı: API imzaları) |

Önerilen sıra: önce motor tarafındaki P0/P1 (çakışmasız: tuzak, maaş, fiyat dondurma, SpeechQueue sınırı), UI ajanı teslim ettikten sonra `Speaker`/`BillsBox`/`Texts` maddeleri tek seferde.
