# Engine API v0.5 (paket `com.doner.oyunu.engine`, saf Java, android.* yok, deterministik)

Bu belge UI geliştirmesi için TEK kaynaktır. Şartname: `docs/design_v05.md` (akış), `docs/spec.md` (genel), `docs/research.md` (veri).
Para birimi TL (int). Tüm olaylar `tick()`/eylemi çağıran iş parçacığında, eylem metodunun içinde (eşzamanlı) gelir.

## 0. v0.4 -> v0.5 özet (UI'nın bilmesi gerekenler)
| Konu | v0.4 | v0.5 |
|---|---|---|
| Sipariş | ürünlerin adımları düz sıra, kalem-içi "Paketle" | **kalem (OrderLine) bazlı, serbest sıra**; kalem-içi paket YOK; tek ortak **"Siparişi paketle"** (PAKET) adımı |
| Sosuz/salata | otomatik | **topping (malzeme) seçimi** adımı: `startStep` → `NEEDS_TOPPINGS` → panel → `confirmToppings` |
| Hazırlık | yok | **hazır döner / hazır içecek / salata-sos tepsisi**, gün öncesi **PREP aşaması** (`beginPrep`, `startDay`) |
| Çırak | +1 yuva | **otomatik işçi** (seviye 1-3), yuva eklemez; olay: `onApprenticeAction` |
| `startStep` | `(orderId, stationId)` | `(orderId, stationId)` (eski, kalem otomatik) **veya** `(orderId, itemIndex, stationId)` |
| `Order.nextStep()` | zorunlu sıradaki adım | **ÖNERİLEN** adım (motor sıra dayatmaz); paketleme önerisi `order.getPackStep()` |
| Özet | – | `DaySummary.wastedPortions/wastedCost/mistakes/perfect/prepBatches/apprenticeTasks` |
| Kayıt | `v=1` | `v=2` (+`readyDrinks`, `apprenticeAnnounce`); v1 kayıtlar yüklenir |

Geriye dönük uyumlu kalanlar (UI ince adaptörle derlenir): `Order.items/steps/nextStep()/isRunning()/isReady()/getNextStepIndex()/getPosition()`,
`startStep(orderId, stationId)`, `getActiveFor(orderId)`, `getFreeSlots()/getSlotCount()`, `missingIngredient(s)`, `emergencyBuy*`.
**Davranış değişiklikleri:** `Order.isRunning()` artık "siparişte herhangi bir iş sürüyor" demektir (kalemler paralel yürüyebilir);
`getNextStepIndex()` biten adım sayısıdır (paketleme dahil), `order.steps` tüm kalem adımları + sondaki paketleme adımıdır.

## 1. Tipik akış (UI)
```java
SaveData save = SaveData.parse(prefsString);             // null/boş => yeni oyun; v0.4 kaydı da yüklenir
// MARKET: Shop.buyRecommended(save); Shop.buyUpgrade(save, "cirak"); Shop.buyIngredient(save, "ketcap", 10);
GameEngine engine = new GameEngine(System.nanoTime(), save);   // save'i doğrudan günceller
Narrator narrator = new Narrator(engine, (text, urgent) -> speaker.say(text, urgent));
engine.addListener(narrator);                                   // + kendi GameListener'ın (ses/titreşim/görsel)

// --- GÜN ÖNCESİ HAZIRLIK (isteğe bağlı; gün süresine sayılmaz, sınırsız süre) ---
engine.beginPrep();                                   // state PREP, onPrepPhaseStarted
engine.startPrepDoner("et_doner");                    // PrepResult.STARTED ...
engine.startPrepDrink();
engine.startPrepTray(Arrays.asList("domates","marul","sogan","ketcap"));
engine.tick(dt);                                      // PREP'te de çağır: hazırlık işleri/çırak ilerler, saat AKMAZ
engine.startDay();                                    // "Dükkânı aç" (onPrepPhaseEnded + onDayStarted). Atlamak için doğrudan startDay()

// --- GÜN (her karede) ---
engine.tick(dt);
engine.selectOrder(id);                // kart dokunuşu; onOrderSelected + onNextStepHint
engine.selectItem(1);                  // (isteğe bağlı) seçili siparişte kalem seç
StartResult r = engine.startStep(orderId, itemIndex /* -1 = otomatik */, stationId);
if (r == StartResult.NEEDS_TOPPINGS) {                // SOS istasyonu: malzeme paneli aç
    List<ToppingOption> opts = engine.getToppingOptions(orderId, engine.openToppingItem(orderId));
    // kullanıcı düğmelerle seçer; "Hedefi oku" -> narrator.toppingTarget(order, itemIndex)
    engine.confirmToppings(orderId, itemIndex, selectedIds);   // "Tamam" -> STARTED veya OUT_OF_STOCK/INVALID/NO_SLOT...
}
engine.deliver(orderId);               // sipariş paketlenince (isReady) teslim
// bitince onDayFinished/onDayFailed -> prefs.putString(save.serialize())
```
Gün bitince motor `save.day`'i artırır, kirayı düşer. Gün BAŞARISIZ olursa para/stok/hazır içecek gün başına döner (hazırlık harcamaları dahil),
yıldız en az 1.5 olur, `save.day` aynı kalır.

## 2. Durum makinesi
`READY → (PREP) → RUNNING → FINISHED | FAILED`
- `READY`: yeni motor. `tick()` veya sipariş eylemi (`startStep/confirmToppings/spawnOrder`) günü kendiliğinden başlatır (`onDayStarted` tam bir kez); **`startPrep` READY'yi PREP'e geçirir**.
- `PREP`: müşteri gelmez, saat akmaz (`getElapsed()==0`, `getTimeLeft()==duration`). Yalnızca hazırlık (oyuncu + çırak). Sipariş yok → sipariş eylemleri `NO_ORDER`.
- `RUNNING`: normal gün. Hazırlık de yapılabilir (boş yuva varsa).
- `isRunning()` = READY, PREP veya RUNNING (gün bitmedi). `isPrepPhase()`.
- `beginPrep()` : READY → PREP (false: zaten başladı). `startDay()`: READY/PREP → RUNNING (false: zaten başladı).
- Hazırlık işi PREP'te başlayıp gün başladığında bitmediyse gün içinde devam eder.

## 3. Sınıflar
| Sınıf | Açıklama |
|---|---|
| `Station` (enum) | DONER=1, EKMEK=2, SOS=3, IZGARA=4, PAKET=5, ICECEK=6, TATLI=7; `id`, `displayName`, `byId(int)` |
| `Ingredient` | `id,name,unit,price,category`; `Category{ET,EKMEK,SOS,SALATA,ICECEK,TATLI,DIGER}`; `isTopping()` (SOS/SALATA); `emergencyPrice()`; `EMERGENCY_BATCH=3`. 22 malzeme (yeni: `ketcap,mayonez,aci_sos,nar_eksisi` SOS; `tursu,biber,limon` SALATA; `yogurt` SOS; `domates,sogan,marul` SALATA) |
| `Step` | `name,station,baseSeconds,needs,kind,sequential`; `Kind{NORMAL,TOPPING}`; `isTopping()`, `meatId()` (hazır döner ile hızlanan adım), `isDrinkFill()` |
| `Product` | `id,name,openDay,price,steps`, **`defaultToppings`** (sıralı küme), **`toppingPool`** (4-8 malzeme); `hasToppings()`, `toppingStepIndex()`, `workSeconds()`, `totalNeeds()` (varsayılan topping dahil), `ingredientCost()` |
| `OrderLine` | siparişin kalemi: `product,index`; `stepCount(), step(i), isStepDone(i), isStepRunning(i), isStepUnlocked(i), isStepOpen(i), firstOpenStep(), isDone(), isRunning(), doneCount()`, `getTarget()` (hedef malzeme kümesi), `getChosen()` (oyuncunun seçimi; topping başlamadıysa null) |
| `Order` | `id,customer,items(List<Product>),requests(List<Request>),steps`; `lineCount(), getLine(i), getLines()`, `getPackStep()`, `allLinesDone()`, `canPack()`, `isPacking()`, `isReady()` (paketlendi), `isRunning()`, `nextStep()` (önerilen), `getNextStepIndex()`, `getPosition()` (1..; stereo pan), `hasRequests()`, `requestsText()` ("soğansız, acılı"), `targetToppings(lineIndex)`, `price()`, `itemsText()`, `workSeconds()` |
| `Request` | müşteri isteği; `text` ("soğansız","domatessiz","marulsuz","turşusuz","acılı","acısız","sosuz","bol soslu"); `Request.all()` |
| `ToppingOption` | panel düğmesi: `ingredient, available, selected (başlangıç durumu), isDefault, stock, trayUses` |
| `ToppingCheck` | teslim doğrulaması: `getMistakes()` (`Mistake{ingredientId, extra, requested, productName}`), `mistakes()`, `getToppingLines()`, `perfect()` |
| `ActiveStep` | çalışan iş: `order` (hazırlıkta null), `step`, `total`, `slot` (çırakta -1), `worker` (0 oyuncu, 1-3 çırak), `itemIndex` (-1: paketleme/hazırlık), `prep` (PrepKind veya null); `getRemaining(), progress(), isApprentice(), isPrep(), isPack()` |
| `PrepKind` (enum) | `DONER` (4 porsiyon, 6 sn, DONER ist.), `DRINK` (4 bardak, 4 sn, ICECEK), `TRAY` (malzeme başına 6 kullanım, 5 sn, SOS); `displayName, station, batch, baseSeconds` |
| `PrepResult` (enum) | STARTED, NOT_ALLOWED, NO_SLOT, OUT_OF_STOCK, FULL, INVALID |
| `StartResult` (enum) | STARTED, NO_ORDER, WRONG_STATION, NO_SLOT, OUT_OF_STOCK, ORDER_BUSY, ORDER_READY, NOT_RUNNING, **NEEDS_TOPPINGS, STEP_LOCKED, INVALID** |
| `ApprenticeAction` | `worker, started, order (hazırlıkta null), itemIndex, step, prep, pack, seconds`; `isPrep()` |
| `DaySummary` | `day, failed, revenue, tips, emergencySpend, rent, rentDue, rentUnpaid(), delivered, lost, wrongTaps, startStars, endStars, moneyAfter, newProductsNext`, **`wastedPortions, wastedCost, mistakes, perfect, prepBatches, apprenticeTasks`**; `profit()` (israf ayrıca düşülmez: malzeme zaten harcanmıştı) |
| `Catalog` | statik: `ingredients(), ingredient(id), products(), product(id), productsForDay(day), newProductsOnDay(day), upgrades(), upgrade(id), customerTypes(), typesForDay(day)`; `Catalog.AYRAN="ayran"` |
| `Upgrade` / `Upgrades` | `Upgrades.slots()` (hep 1, çırak yuva eklemez), **`apprenticeLevel()`** (0..3), **`readyCapacity(PrepKind)`**, `speedMult(Station)`, `patienceMult(), arrivalMult(), tipMult(), starGainMult(), touristMult(), queueBonus(), stockCap()` |
| `Shop` | `buyIngredient, buyUpgrade, recommendedPurchase/Cost, buyRecommended` (artık seçilebilir topping'ler için küçük pay içerir) |
| `DayConfig` | aynı + `requestChance(day)` (1:0, 2:%20, 3+:%45), `toppingsPreselected(day)` (1-3 true) |
| `SaveData` | + `apprenticeAnnounce` (ayar, varsayılan true), `readyDrinks` (gün sonunda atılmaz) |

### Ürünler ve adımlar (paket adımı kalem içinde YOK)
| Ürün (açılış) | Adımlar (istasyon, sn) | Varsayılan topping → havuz |
|---|---|---|
| Ekmek arası döner (1) | Ekmeği ısıt (EKMEK 3,5), Döneri dilimle (DONER 3,5), **Malzemeleri ekle (SOS, topping 2,0)** | domates, marul, soğan, ketçap → +mayonez, acı sos, turşu |
| Ayran (2) | Ayranı doldur (ICECEK 1,8) | – |
| Pilav üstü döner (3) | Pilavı tabağa al (IZGARA 3), Tereyağını gezdir (IZGARA 2), Döneri dilimle (DONER 4,5), Garnitürü ekle (topping 2) | domates, biber, turşu → +soğan, acı sos |
| Tavuk dürüm (4) | Lavaşı ısıt, Tavuğu dilimle, topping | marul, domates, mayonez → +soğan, ketçap, acı sos, turşu |
| Kutu içecek (5) | Dolaptan al (ICECEK 1) | – |
| Hatay usulü dürüm (6) | Lavaşı ısıt, Harç ve döner ekle (DONER, 2 malzeme), Izgarada mühürle, topping | soğan, domates, turşu, acı sos, nar ekşisi → +marul, biber |
| Ekmek arası tavuk (7) | Ekmeği ısıt, Tavuğu dilimle, topping | marul, domates, mayonez → …|
| Zurna dürüm (8) | Lavaşı ısıt+tereyağı, Döneri dilimle, topping | yoğurt, domates, soğan → +marul, acı sos, turşu, biber |
| İskender (10) | Ekmeği küp doğra, Döneri dilimle ve ser, Tereyağı ve domates sosu (IZGARA), topping | yoğurt, domates, biber → +acı sos, turşu |
| Porsiyon döner (12) | Ekmeği ısıt, Döneri dilimle, topping | domates, soğan, biber → +marul, turşu, acı sos, ketçap |
| Lahmacun (14) | Harcı lavaşa yay (SOS), Fırına ver (IZGARA, sıralı), topping | soğan, domates, limon → +marul, turşu, acı sos |
| Künefe (16) | Kadayıfı yoğur, Peyniri ser, Fırında pişir, Çevir ve servis et (hepsi TATLI/IZGARA, sıralı) | – |

**Bağımlılık kuralları:** `Step.sequential` olan adım, kalemin önceki TÜM adımları bitince açılır (künefe, lahmacun fırın). **Topping adımı** kalemin diğer tüm adımları bitince açılır. Diğer adımlar herhangi sırada ve (çırak varsa) paralel yapılabilir. **Paketleme** tüm kalemler bitince açılır; süre = 1,5 + 0,5 × kalem sayısı.

## 4. GameEngine
```java
GameEngine(long seed, SaveData save)
void addListener(GameListener) / removeListener(GameListener)
void tick(float dt)                                 // büyük dt içeride 0,1 sn alt adımlara bölünür
// --- aşamalar
boolean beginPrep();  boolean startDay();           // bkz. §2
State getState(); boolean isRunning(); boolean isPrepPhase()
// --- seçim
boolean selectOrder(int orderId)                    // onOrderSelected + onNextStepHint
boolean selectNextOrder()                           // döngüsel
boolean selectItem(int itemIndex)                   // seçili siparişte kalem (-1 otomatik); onItemSelected
int getSelectedOrderId(); int getSelectedItem(); Order getSelectedOrder()
// --- sipariş adımları
StartResult startStep(int orderId, int stationId)                  // kalem otomatik (seçili kalem öncelikli)
StartResult startStep(int orderId, int itemIndex, int stationId)   // itemIndex<0: otomatik; PAKET istasyonu itemIndex'ten bağımsız ortak paketlemedir
StartResult confirmToppings(int orderId, int itemIndex, Collection<String> ingredientIds)
List<ToppingOption> getToppingOptions(int orderId, int itemIndex)  // havuz sırasında
Set<String> getInitialToppings(int orderId, int itemIndex)         // panel açılışında seçili (1-3. gün varsayılan, sonra boş)
Set<String> getToppingTarget(int orderId, int itemIndex)           // hedef küme (varsayılan ± istekler)
int openToppingItem(int orderId)                                   // topping bekleyen ilk kalem, yoksa -1
boolean deliver(int orderId)                                       // paketlenmediyse false
// --- hazırlık
PrepResult startPrep(PrepKind kind, Collection<String> ids)
PrepResult startPrepDoner(String meatId); PrepResult startPrepDrink(); PrepResult startPrepTray(Collection<String> ingredientIds)
int getReadyMeat(String meatId); int getReadyDoner(); int getReadyDrinks(); int getTrayUses(String ingId); int getTrayTotal()
int getReadyCapacity(PrepKind kind)
List<Ingredient> getPrepMeats()          // menüde kullanılan döner malzemeleri (hazırlanabilir)
List<Ingredient> getPrepTrayChoices()    // tepsiye konabilecekler (menü topping havuzlarının birleşimi)
boolean menuHasDrinkPrep()               // menüde ayran var mı
// --- stok / acil alım
boolean emergencyBuy(String ingredientId)           // 3 birim x (+%50)
Ingredient missingIngredient(int orderId); List<Ingredient> missingIngredients(int orderId)
int emergencyCostForMissing(int orderId); boolean emergencyBuyMissing(int orderId)
// --- sorgular
int getDay(); DayConfig getConfig(); SaveData getSave(); int getMoney(); double getStars()
float getElapsed(); float getTimeLeft(); boolean isOvertime()
int getSlotCount()      // oyuncu yuvası: hep 1
int getFreeSlots()      // oyuncunun boş yuvası (çırak işleri yuva kullanmaz; hazırlık kullanır)
int getApprenticeLevel()
DaySummary getSummary(); Upgrades getUpgrades()
List<Order> getOrders(); Order getOrder(int); 
List<ActiveStep> getActiveSteps()                   // TÜM çalışan işler (oyuncu + çırak + hazırlık)
ActiveStep getActiveFor(int orderId)                // siparişin ilk işi (oyuncu işi öncelikli)
ActiveStep getActiveForItem(int orderId, int itemIndex)   // kalem adımı; paketleme için itemIndex=-1
// --- senaryo/öğretici/test yardımcıları
Order spawnOrder(List<String> productIds, List<String> requestTexts)  // anında sipariş (gün başlamadıysa başlatır); bilinmeyen ürün/istek -> null
void setSpawning(boolean on)                        // otomatik müşteri gelişini kapat/aç (varsayılan açık)
```

### startStep sonuçları
| Sonuç | Anlamı | Yan etki / olay |
|---|---|---|
| `STARTED` | adım yuvada başladı | `onStepStarted` |
| `NEEDS_TOPPINGS` | SOS'taki topping adımı: zamanlayıcı BAŞLAMADI | `onToppingSelectionNeeded(order,itemIndex)` → UI paneli açar → `confirmToppings` |
| `STEP_LOCKED` | o istasyonda adım var ama kilitli (topping için önce ısıt/dilimle, paketleme için tüm kalemler) | `onStepLocked`; yıldız cezası YOK |
| `WRONG_STATION` | o istasyonda hiçbir kalemin açık adımı yok | `onWrongStation`, −0,02 yıldız, `wrongTaps++` |
| `ORDER_BUSY` | uygun adım(lar) zaten sürüyor | – |
| `NO_SLOT` | oyuncu yuvası dolu | `onNoFreeSlot` |
| `OUT_OF_STOCK` | malzeme yok | `onOutOfStock(order, ing)`; sonra `emergencyBuyMissing(orderId)` |
| `ORDER_READY` / `NO_ORDER` / `NOT_RUNNING` / `INVALID` | sipariş zaten hazır / yok / gün bitti / geçersiz kalem | – |
İstasyon, kalemde o istasyonda birden çok açık adım varsa İLK açık adıma çözülür. Seçili kalem (`selectItem`) önceliklidir.

### confirmToppings
- Önkoşul: kalemin topping adımı açık (diğer adımlar bitmiş). Seçilenler havuzda olmalı (aksi `INVALID`, `onToppingsRejected`).
- Seçilen her malzeme stokta veya hazır tepside olmalı; yoksa `OUT_OF_STOCK` + `onToppingsRejected(order,itemIndex,OUT_OF_STOCK,ingredient)`, **hiçbir şey tüketilmez**.
- Boş seçim geçerlidir (sosuz). Tüketim: seçilen her malzemeden 1 birim (tepsi varsa önce tepsiden 1 kullanım).
- Süre = (adım tabanı + 0,3 sn × seçili sayısı) × (1 − 0,5 × tepsiyle karşılanan oran) × istasyon hız × tempo, en az 0,8 sn. `onStepStarted` verilir.
- Seçim `OrderLine.getChosen()`'e yazılır; **doğrulama TESLİMDE** (`deliver`) yapılır, hedefle kıyaslanır.
- Teslim doğrulaması: hatalı malzeme sayısı M (eksik + fazla). M>0: `onOrderMistake(order, check, penalty)`, ceza = min(0,15, 0,05·M) yıldız, bahşiş yarıya iner, `onOrderDelivered(... happy=false)`. M=0 ve topping kalemi var: `onOrderPerfect`, +0,02 yıldız bonusu (×starGain). Topping'siz sipariş (sadece içecek vb.) etkilenmez.

### Müşteri istekleri
`Order.requests`. Olasılık: 1. gün 0, 2. gün %20, 3+ gün %45 (1 veya %30 olasılıkla 2 istek; yalnızca hedef kümeyi gerçekten değiştirenler seçilir). `Order.requestsText()` kartta ve duyuruda gösterilir.
Etkiler: soğansız/domatessiz/marulsuz/turşusuz (malzemeyi çıkarır), acılı (+acı sos, havuzda varsa), acısız (−acı sos), sosuz (acı hariç tüm SOS kategorisini çıkarır), bol soslu (havuzdaki acı-olmayan SOS'ları ekler).
`OrderLine.getTarget()` = varsayılan ± istekler (havuz sırasında).

### Hazırlık (PrepKind)
| Tür | Parti | Süre | Tüketir | Siparişte etkisi | Kapasite (buzdolabı sv0; +4 / +6 her seviye) |
|---|---|---|---|---|---|
| DONER (`ids={"et_doner"}` veya `"tavuk_doner"`) | 4 porsiyon | 6 sn × DONER hız × tempo | o malzemeden 4 | aynı malzemeyi isteyen "dilimle" adımı **0,8 sn**, porsiyon düşer, stoktan düşmez | 8 (malzeme başına) |
| DRINK (ids yok sayılır) | 4 bardak | 4 sn | ayran × 4 | "Ayranı doldur" **0,5 sn**; hazır içecek `SaveData.readyDrinks`'te, **gün sonunda ve kayıtta KALIR** | 8 |
| TRAY (`ids` = SOS/SALATA malzemeleri) | malzeme başına 6 kullanım | 5 sn | seçilen her malzemeden 6 | topping adımı kısalır (yukarıdaki formül) | malzeme başına 12 |
- Malzeme başlarken tüketilir; bitişte hazır stok artar (`onPrepFinished`). Yuva (oyuncu) kullanılır; boş yuva yoksa `NO_SLOT`. Kapasite devam eden hazırlıkları da sayar (`FULL`). Yetersiz stok: `OUT_OF_STOCK`. Geçersiz malzeme: `INVALID`. Reddedilen her hazırlık için `onPrepRejected`.
- **Gün sonunda hazır döner ve tepsi atılır = israf**: `DaySummary.wastedPortions` (porsiyon + tepsi kullanımı), `wastedCost` (TL). Yarım kalan hazırlık da israftır. Başarısız günde hazırlık harcamaları geri döner.
- Hazırlık gün içinde de yapılabilir (boş yuva varsa).

### Çırak (otomatik işçi)
`Upgrades.apprenticeLevel()` = `cirak` yükseltmesinin seviyesi (0-3) = eşzamanlı çırak sayısı ve yetenek seviyesi. Fiyat/gün: 3000@3, 9000@8, 24000@16. Yuva EKLEMEZ (oyuncu yuvası hep 1).
| Seviye | Üstlendiği iş |
|---|---|
| 1 | "Siparişi paketle", ICECEK adımları (ayran doldur, dolaptan al); boştayken hazırlık (içecek, döner) |
| 2 | + EKMEK adımları (ekmek/lavaş ısıt); boştayken tepsi hazırlığı |
| 3 | + DONER, IZGARA, TATLI ve SOS-harç adımları |
| – | **Topping seçimi HER ZAMAN oyuncuda** |
Kurallar: oyuncudan %20 yavaş (süre ×1,2), hatasız, stok normal tüketilir (stok yoksa sessizce atlar, acil alım yapmaz). Önce **en sabırsız** (kalan sabır en az) sipariş; **oyuncunun SEÇİLİ siparişine dokunmaz** (seçili sipariş yoksa tümü uygun). Sipariş işi yoksa hazırlık: içecek (hazır ≤4 iken parti), döner (porsiyon ≤3 iken; gün sonuna 25 sn kala yapmaz), tepsi (seviye 2+, menünün en sık varsayılan 5 malzemesi). Hazırlık aşamasında (PREP) da aynı şekilde çalışır. Çırak işleri `getActiveSteps()`'te `isApprentice()` ile görünür; `onStepStarted/onStepFinished` YALNIZCA oyuncu işleri içindir, çırak işleri `onApprenticeAction` ile gelir (UI sesi: kısık/uzak, pan merkez, %60).
`SaveData.apprenticeAnnounce` kapalıysa Narrator çırak duyurusu yapmaz (UI olayı yine alır). Duyurular kısıtlanır (≥4 sn aralık; paketleme bitişi her zaman).

## 5. GameListener (hepsi `GameListenerAdapter`'da boş)
```java
// gün
void onPrepPhaseStarted(int day);     // beginPrep
void onPrepPhaseEnded(int day);       // startDay (PREP'ten); hemen ardından onDayStarted
void onDayStarted(int day, DayConfig config);
void onDayFinished(DaySummary summary);  void onDayFailed(DaySummary summary);
// sipariş / müşteri
void onCustomerArrived(Order order);
void onOrderSelected(Order order);       void onItemSelected(Order order, int itemIndex);
void onNextStepHint(Order order, Step step);          // ÖNERİLEN adım (sıra dayatma yok); paketleme: step == order.getPackStep()
void onPatienceWarning(Order order, int level);       // 1 azalıyor, 2 kritik
void onCustomerLeft(Order order);
// adımlar (yalnızca OYUNCU işleri)
void onStepStarted(Order order, Step step, int slot, float seconds);
void onStepFinished(Order order, Step step, boolean orderComplete);  // orderComplete: paketleme bitti
void onWrongStation(Order order, Station tapped /*null olabilir*/, Station expected);
void onStepLocked(Order order, Step step);
void onNoFreeSlot(Order order);
// kalem / paketleme / teslim (oyuncu ya da çırak fark etmez)
void onItemFinished(Order order, int itemIndex);      // kalem hazır
void onOrderReadyToPack(Order order);                 // tüm kalemler hazır -> "Siparişi paketle" açıldı
void onOrderReady(Order order);                       // paketlendi -> teslim et
void onOrderDelivered(Order order, int money, int tip, boolean happy);
// malzeme seçimi
void onToppingSelectionNeeded(Order order, int itemIndex);      // paneli aç
void onToppingsRejected(Order order, int itemIndex, StartResult reason, Ingredient ingredient);
void onOrderMistake(Order order, ToppingCheck check, double penalty);   // teslimde; onOrderDelivered'dan ÖNCE
void onOrderPerfect(Order order);
// hazırlık
void onPrepStarted(PrepKind kind, boolean byApprentice, float seconds);
void onPrepFinished(PrepKind kind, boolean byApprentice, int amount);
void onPrepRejected(PrepKind kind, PrepResult reason, Ingredient ingredient);
// çırak
void onApprenticeAction(ApprenticeAction action);     // started=true/false; order==null -> hazırlık
// diğer
void onStarsChanged(double oldStars, double newStars);
void onOutOfStock(Order order, Ingredient ingredient);
void onEmergencyPurchase(Ingredient ingredient, int qty, int cost);
void onNotEnoughMoney(int needed);
```
Ses için ipucu (design_v05 §5): `onStepStarted` → istasyon efekti adım süresince (`seconds`); `onStepFinished` → keser/`sfx_step_done`; çırak: `onApprenticeAction(started)` kısık/pan merkez; hazırlık `onPrepStarted/Finished` ilgili istasyon sesi + `sfx_step_done`.

## 6. Narrator
`new Narrator(engine)` / `new Narrator(engine, output)`; `setOutput`. `Output.say(String text, boolean urgent)`. GameListener olarak tüm olayları Türkçe konuşur. Metin üreticiler herkese açık (UI isterse doğrudan kullanır):
`prepPhaseStarted, prepPhaseEnded, dayStarted, customerArrived, orderSelected, itemSelected, nextStepHint, stepStarted, stepFinished, wrongStation, stepLocked, noFreeSlot, itemFinished, orderReadyToPack, orderReady, orderDelivered, toppingSelectionNeeded, toppingTarget, toppingState, toppingSelectionSummary, toppingsRejected, orderMistake, orderPerfect, prepStarted, prepFinished, prepRejected, readyStockText, apprenticeAction, patienceWarning, customerLeft, starsChanged, outOfStock, emergencyPurchase, notEnoughMoney, dayFinished, dayFailed, statusText, stationLabel, startResultText`.
- `toppingTarget(order, itemIndex)` — **"Hedefi oku"**: "Hedef, Ekmek arası döner: domates, marul, ketçap ve acı sos. Müşteri isteği: soğansız, acılı."
- `toppingState(ingredient, selected, available)` — düğme durumu: "Soğan, eklenmedi" / "Soğan, eklendi" / "Soğan, stokta yok".
- `toppingSelectionSummary(ids)` — "Seçili: domates ve marul. Toplam 2 malzeme."
- `customerArrived`: "Yeni müşteri: Ahmet, Ekmek arası döner ve Ayran istiyor. 2 kalem. İstek: soğansız. Acelesi var. Sipariş 2."
- `nextStepHint`: "Önerilen: Ekmeği ısıt. Ekmek ısıtıcı istasyonuna dokun." (çok kalemli siparişte ürün adı öneki: "Ayran: Ayranı doldur.")
- `stepFinished`: "Ekmeği ısıt bitti. Önerilen: Döneri dilimle, Döner makinesi."
- `orderMistake`: "Hatalı sipariş. Soğan istememiştim. Ketçap eksik olmuş. Yıldız düştü, bahşiş yarıya indi."
- `apprenticeAction`: "Çırak 3 numaralı siparişi paketledi." / "Çırak ayran dolduruyor." / "Çırak 2 numaralı siparişte: Ekmeği ısıt."
- `readyStockText()`: "Hazır: döner 4 porsiyon, ayran 3 bardak, salata ve sos tepsisi 24 kullanım." (hiç yoksa "")
- `statusText()`: para, yıldız, kalan süre (veya "hazırlık aşaması, süre akmıyor"), bekleyen sipariş, seçili siparişin kalemleri/önerisi/istekleri, hazır stok.
- `stationLabel(st)`: "Döner makinesi istasyonu, boş, Ekmek arası döner Döneri dilimle için gerekli, hazır döner 4" (kalem adı yalnızca çok kalemli siparişte).
- `startResultText(StartResult)`: tüm sonuçlar için kısa metin (NEEDS_TOPPINGS, STEP_LOCKED, INVALID dahil).
- Statik: `fmtStars(double)`, `fmtSeconds(float)`, `fmtMoney(int)`, `joinNames(Collection<String> ids)`.
- Çırak duyuruları `engine.getSave().apprenticeAnnounce` ayarına bakar.

## 7. SaveData (`v=2`)
Alanlar: `day,bestDay,money,stars,inventory,upgradeLevels`, ayarlar `tempo,speechOn,speechRate,vibration,musicVolume,sfxVolume,tutorialDone`, **`apprenticeAnnounce`** (kayıt anahtarı `apprenticeAnnounce=1/0`), **`readyDrinks`** (0-40). `parse` hataya toleranslıdır; **v0.4 (`v=1`) kayıtları** yüklenir, yeni alanlar varsayılan (çırak duyuruları açık, hazır içecek 0). Eski kayıtta yeni malzeme stoğu 0'dır (Market'ten alınır; gün başında "Önerilen malzemeleri al").
Hazır döner ve tepsi KAYDEDİLMEZ (gün içi durum).

## 8. Denge (bot simülasyonu; testler `SimulationTest`, `HumanSimTest`)
- Başlangıç parası 5000. Fiyatlar/süreler v0.4 ile aynı; yeni süre kalemleri: topping +0,3 sn/malzeme, paketleme 1,5+0,5/kalem, çırak ×1,2.
- Hazırlık yapan + yükseltme alan bot (0,8 sn tepki): gün 1–40 geçer (≥32 gün ilk denemede). Hazırlıksız bot da yükseltmeyle 1–40'ı geçer. Yavaş (1,5 sn) bot yükseltmeyle 1–40'ı geçer.
- Yükseltmesiz bot gün ~23–26'da takılır; hazırlıksız/yükseltmesiz bot gün 1–5'i geçer.
- 15 gün yükseltmesiz: hazırlık yapan bot hazırlıksızdan ≈%7–11 daha çok kazanır. Fazla hazırlık israf yaratır (gün 1'de bot ~170 TL).
- Hiç hareket etmeyen bot ≤5 günde başarısız olur.
- Çırak 3. seviyeye (24000 TL, 16. gün) çıkınca oyun belirgin kolaylaşır; yükseltme sırası: önce çırak.

## 9. UI'nın yapması gerekenler (özet)
1. Hazırlık ekranı: market sonrası `beginPrep()` → hazırlık kontrolleri (döner/içecek/tepsi `startPrep*`, hazır stok göstergeleri, "Dükkânı aç" = `startDay()`, "Hazırlık yapmadan aç" = doğrudan `startDay()`); PREP'te de `tick(dt)` çağır.
2. Sipariş kartı: kalem listesi (`order.getLine(i)`, kalem adım durumu), istekler (`requestsText()`), "Siparişi paketle" durumu; kalem seçimi `selectItem`.
3. SOS istasyonu: `NEEDS_TOPPINGS` → malzeme paneli (`getToppingOptions`, aç/kapa düğmeleri, `Narrator.toppingState`, "Hedefi oku" = `Narrator.toppingTarget`, "Tamam" = `confirmToppings`; `OUT_OF_STOCK` için `emergencyBuy`).
4. `startStep(orderId, itemIndex, stationId)` kullan; yeni sonuçlar `STEP_LOCKED`, `NEEDS_TOPPINGS`.
5. Yeni olayları işle (özellikle `onApprenticeAction`, `onPrepStarted/Finished`, `onToppingsRejected`, `onOrderMistake`, `onOrderPerfect`); GameListenerAdapter'dan türet.
6. Gün özeti: israf satırı (`wastedPortions`, `wastedCost`), hatalı/kusursuz sipariş sayıları.
7. Ayarlar: "Çırak duyuruları" anahtarı → `save.apprenticeAnnounce`.
8. Ses: istasyon sesi adım süresince (`seconds`); çırak/hazırlık sesleri.
9. "Durumu oku": `narrator.statusText()` hazırlık/kalem/hedef bilgisini içerir.
