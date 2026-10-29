# Engine API (paket `com.doner.oyunu.engine`, saf Java)

Veri kaynağı: `docs/research.md` (ürün/malzeme/fiyat/yükseltme/müşteri tipleri) + `docs/spec.md` (açılış günleri, kurallar). Çakışmada spec'teki açılış günleri kullanıldı; spec'te olmayan 2 ürün (Kutu içecek 5. gün, Ekmek arası tavuk döner 7. gün) eklendi. Toplam 12 ürün, 15 malzeme, 10 yükseltme, 10 müşteri tipi. Para birimi TL (int).

## Tipik akış (UI)
```java
SaveData save = SaveData.parse(prefsString);            // null/boş => yeni oyun (1. gün stoğu hazır)
// Market: Shop.buyRecommended(save); Shop.buyUpgrade(save, "cirak"); ...
GameEngine engine = new GameEngine(System.nanoTime(), save); // save'i doğrudan günceller
Narrator narrator = new Narrator(engine, (text, urgent) -> speaker.say(text, urgent));
engine.addListener(narrator);          // + kendi GameListener'ın (ses/titreşim/görsel)
// her karede: engine.tick(dtSeconds);
// oyuncu: engine.selectOrder(id) / selectNextOrder(); engine.startStep(id, stationId); engine.deliver(id); engine.emergencyBuy(ingId)
// bitince onDayFinished/onDayFailed -> prefs.putString(save.serialize())
```
Gün bitince motor `save.day`'i artırır, kirayı düşer. Gün BAŞARISIZ olursa para/stok gün başına döner, yıldız en az 1.5 olur, `save.day` aynı kalır (aynı günü tekrar dene).

## Sınıflar
| Sınıf | Açıklama |
|---|---|
| `Station` (enum) | DONER=1, EKMEK=2, SOS=3, IZGARA=4, PAKET=5, ICECEK=6, TATLI=7; `id`, `displayName`, `byId(int)` |
| `Ingredient` | `id,name,unit,price`; `emergencyPrice()` (+%50); `EMERGENCY_BATCH=3` |
| `Step` | `name, station, baseSeconds, needs(Map id->adet)` |
| `Product` | `id,name,openDay,price,steps`; `workSeconds()`, `totalNeeds()`, `ingredientCost()` |
| `CustomerType` | `id,name,minDay,weight,patienceSeconds,tipMult,extraItemChance,preferred,hint` |
| `Customer` | `name,type,patienceMax`; `getPatienceLeft()`, `patienceFraction()`, `getWarnLevel()` |
| `Order` | `id,customer,items,steps`; `nextStep()`, `getNextStepIndex()`, `isRunning()`, `isReady()`, `getPosition()` (1..; stereo pan), `price()`, `itemsText()` |
| `ActiveStep` | çalışan adım: `order, step, total, slot`; `getRemaining()`, `progress()` |
| `StartResult` (enum) | STARTED, NO_ORDER, WRONG_STATION, NO_SLOT, OUT_OF_STOCK, ORDER_BUSY, ORDER_READY, NOT_RUNNING |
| `DaySummary` | `day, failed, revenue, tips, emergencySpend, rent (gerçekten düşülen), rentDue (tam kira), rentUnpaid(), delivered, lost, wrongTaps, startStars, endStars, moneyAfter, newProductsNext`; `profit()` |
| `Catalog` | statik: `ingredients()`, `ingredient(id)`, `products()`, `product(id)`, `productsForDay(day)` (birikimli), `newProductsOnDay(day)`, `upgrades()`, `upgrade(id)`, `customerTypes()`, `typesForDay(day)` |
| `Upgrade` | `id,name,category(MUTFAK/RESTORAN),description,prices[],unlockDays[],effects[]`; `maxLevel()`, `priceForNext(level)`, `unlockDayForNext(level)` |
| `Upgrades` | `new Upgrades(save)`: `speedMult(Station)`, `slots()`, `patienceMult()`, `arrivalMult()`, `tipMult()`, `starGainMult()`, `touristMult()`, `queueBonus()`, `stockCap()` |
| `Inventory` | `get/set/add/has/hasAll/consumeAll/asMap/copy/total` |
| `Shop` | `buyIngredient(save,id,qty)`, `buyUpgrade(save,id)` -> `BuyResult{OK,NOT_ENOUGH_MONEY,MAX_LEVEL,LOCKED,UNKNOWN,STOCK_FULL}`; `recommendedPurchase(save)` (Map), `recommendedCost(save)`, `buyRecommended(save)` (harcanan TL), `ingredientCost(id,qty)` |
| `DayConfig` | `DayConfig.forDay(n)`: `duration` (min(300,120+6n)), `overtime=30`, `arrivalInterval`, `patienceFactor`, `workPatience`, `maxItems` (1→3), `multiItemChance`, `baseQueue`, `rent` (300+30(n-1)); `starMultiplier(stars)` (1★ 0.6 … 5★ 1.4); 40. günden sonra döngüsel/sonsuz |
| `SaveData` | alanlar: `day,bestDay,money,stars,inventory,upgradeLevels`, ayarlar `tempo(0.6-1.6; adım süresi + sabır çarpanı),speechOn,speechRate,vibration,musicVolume,sfxVolume,tutorialDone`; `newGame()`, `serialize()`, `parse(String)` (hataya toleranslı), `copy()`, `level(id)`, `setTempo()` |
| `GameListener` / `GameListenerAdapter` | olay arayüzü / boş uygulama |
| `Narrator` | `implements GameListener`; Türkçe metin üretir ve `Output.say(text, urgent)`'e gönderir |

## GameEngine
```java
GameEngine(long seed, SaveData save)
void addListener(GameListener) / removeListener(GameListener)
void tick(float dtSeconds)                 // büyük dt içeride 0.1 sn'lik alt adımlara bölünür; ilk tick onDayStarted verir
boolean selectOrder(int orderId)           // onOrderSelected + onNextStepHint
boolean selectNextOrder()                  // döngüsel
StartResult startStep(int orderId, int stationId)
boolean deliver(int orderId)               // hazır değilse false
boolean emergencyBuy(String ingredientId)  // 3 birim x (+%50); para yetmezse onNotEnoughMoney
// sorgular
State getState()  // READY, RUNNING, FINISHED, FAILED
boolean isRunning(); int getDay(); DayConfig getConfig(); SaveData getSave()
int getMoney(); double getStars(); float getElapsed(); float getTimeLeft(); boolean isOvertime()
int getSlotCount(); int getFreeSlots(); DaySummary getSummary(); Upgrades getUpgrades()
List<Order> getOrders(); Order getOrder(int); Order getSelectedOrder(); int getSelectedOrderId()
List<ActiveStep> getActiveSteps(); ActiveStep getActiveFor(int orderId)
Ingredient missingIngredient(int orderId)  // sıradaki adım için eksik ilk malzeme
List<Ingredient> missingIngredients(int orderId) // eksik TÜM malzemeler
int emergencyCostForMissing(int orderId); boolean emergencyBuyMissing(int orderId) // hepsini tek hamlede (hepsine para yetmezse hiçbiri) acil al
```
Kurallar: yanlış istasyon -0.02 yıldız; müşteri gidince -0.30; teslimde sabrın kalanı >=%50 hızlı (+0.12, %20 bahşiş), >=%20 memnun (+0.08, %8 bahşiş), aksi +0.02 (yıldız kazanımları `starGainMult` ile artar); yıldız <1.0 => gün başarısız. Sabır uyarıları: seviye 1 (<=%50), seviye 2 (<=%20). Mesai bitince (+30 sn) kalan siparişler cezasız kapanır. Malzeme adım BAŞLARKEN tüketilir. Stok kapasitesi malzeme başına 30 (buzdolabı: 50/100/220). Müşteri gelişi bekleyen sipariş üst sınırı (`baseQueue + masa`) dolunca ertelenir.

## GameListener
```java
void onDayStarted(int day, DayConfig config);
void onCustomerArrived(Order order);
void onOrderSelected(Order order);
void onNextStepHint(Order order, Step step);
void onStepStarted(Order order, Step step, int slot, float seconds);
void onStepFinished(Order order, Step step, boolean orderComplete);
void onWrongStation(Order order, Station tapped /*null olabilir*/, Station expected);
void onNoFreeSlot(Order order);
void onOrderReady(Order order);
void onOrderDelivered(Order order, int money, int tip, boolean happy);
void onPatienceWarning(Order order, int level);   // 1 azalıyor, 2 kritik (order.id mevcut)
void onCustomerLeft(Order order);
void onStarsChanged(double oldStars, double newStars);
void onOutOfStock(Order order, Ingredient ingredient);
void onEmergencyPurchase(Ingredient ingredient, int qty, int cost);
void onNotEnoughMoney(int needed);
void onDayFinished(DaySummary summary);
void onDayFailed(DaySummary summary);
```

## Narrator
`new Narrator(engine)` veya `new Narrator(engine, output)`; `setOutput(Output)`.
- `Output.say(String text, boolean urgent)` (urgent = assertive/kesici).
- Her olay için metin üreticiler: `dayStarted, customerArrived, orderSelected, nextStepHint, stepStarted, stepFinished, wrongStation, noFreeSlot, orderReady, orderDelivered, patienceWarning, customerLeft, starsChanged, outOfStock, emergencyPurchase, notEnoughMoney, dayFinished, dayFailed`.
- `String statusText()` — "Para 5000 lira, yıldız 3,0, kalan süre 126 saniye, 2 bekleyen sipariş. Seçili: Ahmet, sıradaki Ekmeği ısıt."
- `String stationLabel(Station)` — "Ekmek ısıtıcı istasyonu, boş, Ekmeği ısıt için gerekli" (contentDescription için).
- `String startResultText(StartResult)`.
- Statik: `fmtStars(double)` ("3,3"), `fmtSeconds`, `fmtMoney`.

## Yükseltmeler (fiyat TL; seviye k+1 açılış günü)
cirak (3000@3, 9000@8; +1 yuva her seviye, toplam 3), doner_makinesi (4000@4, 15000@10, 48000@18), ekmek_isitici (2500@3, 8000@9, 22000@17), oto_dilimleyici (3500@6, 12000@12, 34000@21), buzdolabi (2000@2, 7500@8, 24000@16), masa (3000@11, 9000@22, 24000@28), tabela (1000@8, 4000@15, 12000@24), klima (2500@13, 7000@21, 18000@26), muzik (1800@4, 5500@14, 15000@23), dekor (2200@7, 6500@16, 20000@25).

## Denge (bot simülasyonuyla)
Başlangıç parası 5000. Yükseltmeli bot (0.8 sn tepki) 1–40 günü geçer; yükseltmesiz bot ~20-25. günlerde takılır; hiç hareket etmeyen bot ilk günlerde kaybeder. Çok yavaş oyuncu (2.5 sn tepki) 1. günü kaybetmez.

## Testler (`app/src/test/java/com/doner/oyunu/`)
`CatalogTest, EngineTest, ShopSaveTest, SimulationTest, NarratorTest` (+ yardımcılar `TestBot`, `Campaign`).
