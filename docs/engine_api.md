# Engine API v0.9 (paket `com.doner.oyunu.engine`, saf Java, android.* yok, deterministik)

Bu belge UI geliştirmesi için TEK kaynaktır. Şartname: `docs/design_v08.md` (v0.8: süre baskısı ayarı, gün içinde hazırlık düğmeleri yok), `docs/design_v07.md` (v0.7: oyun akışı Rahat/Dengeli/Gerçekçi), `docs/design_v06.md` (v0.6: eylem menüleri, tek tek malzeme, boy, sos takvimi), `docs/design_v05.md` (akış), `docs/spec.md` (genel), `docs/research.md` (veri).

## 00a. v0.8 -> v0.9 özet (patates, Ocak, konuşma sırası, ambiyans, kısa etiketler)
| Konu | v0.9 |
|---|---|
| İstasyon adı | `Station.IZGARA.displayName` = **"Ocak"** (enum adı aynı). Adımlar: "Ocakta mühürle", "Ocakta pişir" |
| Patates | Malzeme `Catalog.PATATES` ("patates", 4 TL, `Catalog.FRIES_OPEN_DAY` = 3. gün). Adım `Catalog.FRY_STEP` = "Patates kızart" (Ocak, 4 sn, 1 patates; `Step.isFry()`). Patates alan ürünler: ekmek_arasi, tavuk_ekmek, tavuk_durum, hatay_durum, zurna_durum (`Product.friesOk()`); `Product.friesVariant()` aynı id/adlı çeşit, malzeme adımından hemen önce fry adımı; `Product.isFries()`; `Product.FRIES_PRICE` = +40 TL; `OrderLine.isFries()`, `displayName()` "Ekmek arası döner (duble, patatesli)" |
| Patatesli sipariş | `DayConfig.friesChance(day)`: 3. günden %12, her gün +1,2 puan, en çok %50. Ayrı rastgele kaynak (`friesRng`): ana müşteri akışı kaymaz. `spawnOrder(["ekmek_arasi+patates"], …)` senaryo/test |
| Hazır patates | `PrepKind.FRIES` (Ocak, 4 porsiyon, 5 sn); `engine.startPrepFries()`, seçenek `prep:fries` (IZGARA menüsü, 3. günden); `getReadyFries()`, `menuHasFries()`; hazırsa adım 0,8 sn ve stoktan düşmez; gün sonunda atılır (israf). `Upgrades.readyCapacity(FRIES)` = döner/ayranla aynı |
| Akışlar | Fry adımı her akışta tek dokunuşlu zamanlı normal adımdır (`step`); Rahat/Dengeli/Gerçekçi aynı. Çırak seviye 3 yapar |
| Market | `Shop.recommendedPurchase` patatesi 3. günden ekler |
| Eylem sürümü | `engine.getActionVersion()`: oyuncu eylemlerinde artar (adım/hazırlık/malzeme başlatma, seçim, teslim) |
| Konuşma | `SpeechQueue` (saf Java, `Narrator.Output` uygular): bayat (3,5 sn; kategorisiz 8 sn) duyuru atılır, aynı kategori birleşir, aynı tür ≥1,2 sn arayla, acil olan öne geçer/keser (hata ve sabır kritik başka acille kesilmez), uzun metin `sayLong` bayatlamaz. `Narrator.Output.say(text, urgent, category, Fresh)` (varsayılan düz `say`'e düşer), `Narrator.Fresh`, `CAT_*` sabitleri, `Narrator.freshSinceNow()` (oyuncu eylem yapınca false), ipucu duyurusu `freshHint` ile söylenmeden önce yeniden denetlenir. `Speaker` bu sırayı kullanır |
| Narrator metinleri | `stepFinished` artık sıradakini tekrarlamaz ("X bitti."), `nextStepHint` = "Önerilen: X. İstasyon.", `customerArrived` kısa (tip cümlesi yok), `stationLabel` = "Ad, durum, sıradaki: adım", yeni `statusShort()` (durum çubuğu etiketi) |
| Etiketler | `StationOption.description` yalnızca ad + gerekli durum ("Domates ekle", "…, stokta yok", "…, malzeme yok"); `label()` ≤60 karakter (kilitliyse "ad, neden" ya da "ad, kilitli") |
| Ses | `ToppingSfx.sfxFor/plan`: otomatik eklemede her malzeme kendi sesiyle sırayla; `SoundManager.startSequence`, `Cue`; `sfx_fry` LOOPED; ambiyans: `playAmbience/stopAmbience/setAmbienceVolume/setAmbienceHold/currentAmbience/ambienceShouldPlay/ambienceOutputVolume` (`amb_shop`, MediaPlayer, SoundPool'a yüklenmez) |
| Kayıt | `SaveData.ambienceVolume` (kayıt `ambience=`, varsayılan 0,35, `v=4` aynı; eski kayıt = varsayılan). Stok `stock.patates` |

## 00b. v0.7 -> v0.8 özet (süre baskısı)
| Konu | v0.7 | v0.8 |
|---|---|---|
| Süre | her gün süreli | **`SaveData.timePressure`** (varsayılan `true`; kayıt `timer=1|0`, eski kayıt = `true`, `v=4` aynı). `engine.isTimed()`, **`engine.setTimePressure(boolean)`** (oyun ortasında da; `save.timePressure`'ı yazar, `onTimePressureChanged`) |
| Kota | – | `DayConfig.customerQuota` (temel), `DayConfig.quota(arrivalMult)`, `DayConfig.quotaFor(save)`; `engine.getCustomerQuota()`, `getCustomersArrived()`, **`getRemainingCustomers()`** (= kalan kota + bekleyen sipariş) |
| Yeni olay | – | `GameListener.onTimePressureChanged(boolean timed)` (adapter'da boş; Narrator duyurur) |
| `DaySummary` | – | `timed` (gün sonunda süre baskısı açık mıydı; kapalıysa `lost` her zaman 0) |
| Narrator | – | `untimedDayText(quota)`, `timePressureChanged(timed)`; `dayStarted`, `statusText` ("süre yok, kalan müşteri N"), `dayFinished` süresizde uyumlu; `stationLabel` hazır stok: "hazır 3,5 porsiyon" / "hazır 3 bardak ayran" / "hazır tepsi 6 kullanım" |

**Süresiz gün (`isTimed()==false`) kuralları:** gün saati (`getTimeLeft`, `isOvertime`) işlemez (`isOvertime()` false; `getTimeLeft` anlamsız: UI `getRemainingCustomers()` kullanır); müşteri sabrı `tick`'te azalmaz (`patienceFraction()==1`), `onPatienceWarning` ve `onCustomerLeft` hiç gelmez; müşteri gelişi `getCustomersArrived() < getCustomerQuota()` iken, bekleyen sipariş üst sınırı (`baseQueue + queueBonus`) korunarak, sıra boşken en çok 2,5 sn'de bir; senaryo siparişleri (`spawnOrder`) kotaya sayılmaz; gün **kota doldu ve `getOrders()` boş** olunca `onDayFinished`. `deliver`: hız bonusu yok (yıldız her teslimde +`GameEngine.UNTIMED_STAR_GAIN`=0,08, kusursuz bonusu ve influencer hariç aynen), bahşiş oranı `UNTIMED_TIP_RATE`=%10 x `UNTIMED_TIP_MULT`=0,8, satış geliri/kira normal; yanlış istasyon/hatalı sipariş cezaları aynen; yıldız < 1,0 olursa `onDayFailed`. `setSpawning(false)` ile (öğretici/test) kota dolmaz, gün kendiliğinden bitmez. Gün ortasında `setTimePressure`: kapatınca sayaç ve sabır donar (kota dolmuş + sıra boşsa gün biter); açınca gün saati gelen müşteri oranından (en çok %90) geri kalmaz.

**UI:** Ayarlar > "Süre baskısı: Açık/Kapalı" (büyük düğme, etikette durum), Duraklat menüsünde aynı düğme, brifing satırı, durum çubuğunda süre yerine "Kalan müşteri", sipariş kartında sabır göstergesi yok (süresiz), özet "Süre baskısı: kapalıydı". **Gün içinde hazırlık bölümü (düğmeler, hazır stok göstergeleri, "Hazır stoğu oku") yoktur; yalnızca PREP'te görünür.** Gün içi hazırlık istasyon eylem menüsünden/panelinden (`prep:*`, §6). `Texts.quickHelp(mode, timed)`, `Texts.tutEnd(timed)`, `Texts.orderDescription(o, sel, act, timed)`, `Texts.timePressure*` metinleri.

## 00. v0.6 -> v0.7 özet (oyun akışı)
| Konu | v0.6 | v0.7 |
|---|---|---|
| Akış | tek akış (tek tek malzeme + boy seçimi) | **`FlowMode`**: `RAHAT`, `DENGELI` (varsayılan), `GERCEKCI` (= v0.6 akışı). `engine.getFlowMode()`, **`engine.setFlowMode(m)`** (oyun ortasında da; `save.flowMode`'u yazar, `onFlowModeChanged`) |
| SOS menüsü | `add:*`, `undo`, `finish`, `read:*` | Rahat: `auto` ("Malzemeleri ekle", hedef küme) + `read:target`. Dengeli: önce `auto` ("Varsayılan malzemeleri ekle") + `read:target`; otomatikten sonra (istek farkı varsa) `remove:<id>` ("Soğan çıkar", grup `REMOVE`), `add:<id>`, `finish`, `read:*`. Gerçekçi: v0.6 listesi; çırak seviye 2+ ve kalem boşken başta `auto` ("Standart malzemelerle bitir") |
| DONER menüsü | `size:*` | Rahat: tek `step` (istenen boy, "…, yarım ekmek, boy otomatik, …"). Dengeli: tam ekmekte tek `step`, yarım/duble istenince `size:*`. Gerçekçi: `size:*` |
| Yeni API | – | `autoToppings(orderId,item)`, `removeTopping(orderId,item,ingId)`, `standardSetUnlocked()`, `StationOption.direct(ops)` (tek dokunuş kuralı), `OrderLine.getAddedSet()/isAutoDone()`, `ActiveStep.isAutoToppings()`, `GameEngine(seed, save, v06Balance)` (yalnızca ölçüm) |
| Yeni olaylar | – | `onAutoToppingsStarted(order,item,adding,seconds)`, `onToppingsAutoAdded(order,item,added,removed,finished)`, `onFlowModeChanged(mode)` |
| Denge | – | Gerçekçi: sabra elle dokunuş süresi eklenir, sabır ×1,15, geliş ×0,9, bahşiş ×1,15. Dengeli: yalnızca elle yapılan farklar sabra eklenir. Rahat: v0.6 sabır formülü |
| Kayıt | `v=3` | `v=4`: + `flow=rahat|dengeli|gercekci`, `flowChosen=0|1`; v1–v3 kayıtlar Dengeli/`flowChosen=0` ile yüklenir; bilinmeyen değer yok sayılır |

Geriye uyum: `addTopping/removeLastTopping/finishToppings`, `size:*` ve `startStep` her akışta API olarak çalışır (akış yalnızca MENÜYÜ ve otomatik işleri belirler). Gerçekçi'de çırak 2'den önce `auto` → `INVALID` + `onOptionRejected("auto")`.
Para birimi TL (int). Tüm olaylar `tick()`/eylemi çağıran iş parçacığında, eylem metodunun içinde (eşzamanlı) gelir.

## 0. v0.5 -> v0.6 özet (UI'nın bilmesi gerekenler)
| Konu | v0.5 | v0.6 |
|---|---|---|
| Malzeme ekleme | SOS'ta toplu panel: `NEEDS_TOPPINGS` -> `confirmToppings(ids)` | **Tek tek**: `addTopping(orderId,item,ingId)` (0,5-0,6 sn, tüketir, olay), **bol = 2 kez**, `removeLastTopping`, `finishToppings` ("Malzemeleri bitir"). Eski toplu API geriye uyum için DURUR (bkz. §9) |
| İstasyon menüsü | yok | **`getStationOptions(orderId,item,stationId)`** -> `List<StationOption>` ve **`applyStationOption(orderId,item,stationId,optionId)`** (TEK giriş noktası: TalkBack eylemi de, panel de bunu çağırır) |
| Döner boyu | yok | Her döner içeren kalem **boy** taşır: Yarım / Tam / Duble. DONER istasyonunda boy SEÇİLİR (`size:*` seçenekleri); yanlış boy = boy hatası (teslimde ceza) |
| Hazır döner | porsiyon sayacı | **yarım porsiyon sayacı** (`getReadyMeatHalves`), hazırlık 4 veya 2 porsiyon |
| Sos/salata | hepsi 1. günden | **Gün gün açılır** (`Ingredient.unlockDay`); istek/varsayılan/seçenek/market/öneri yalnızca açık malzemeyle |
| Yeni olaylar | – | `onToppingAddStarted/Added/Removed/Finished`, `onSizeChosen`, `onStationInfo`, `onOptionRejected` |
| `StartResult` | … | + `LOCKED, MAX_REACHED, NOTHING_TO_UNDO, FULL, INFO, UNKNOWN_OPTION` |
| Kayıt | `v=2` | `v=3` (alan değişmedi); `v=1` ve `v=2` kayıtlar yüklenir |

Geriye uyumlu kalanlar: `startStep(...)`, `confirmToppings(...)`, `getToppingOptions/getInitialToppings/getToppingTarget/openToppingItem`, `Order.items/steps/nextStep()/...`, `getReadyMeat(meatId)` (tam porsiyon, yarım aşağı yuvarlanır), `emergencyBuy*`.
**Davranış değişiklikleri:** (1) `Order.price()` ve `Order.itemsText()` boy dahil ("Ekmek arası döner (yarım ekmek)"); (2) malzeme adımı artık ZAMANLI bir iş değildir: tek tek eklemeler kısa işlerdir (`ActiveStep.isToppingAdd()`), adım `finishToppings` ile anında biter (`onStepFinished` gelir, `onStepStarted` gelmez); (3) `startStep` ile DONER dilimleme istenen boyu varsayar (boy hatası olmaz) — boy seçmek için `applyStationOption(..., "size:...")`; (4) `getReadyDoner()` tam porsiyon (yarım aşağı).

## 0c. Oyun akışı ayrıntıları (v0.7)
```java
engine.getFlowMode();                 // save.flowMode (null ise DEFAULT = DENGELI)
engine.setFlowMode(FlowMode.RAHAT);   // save.flowMode + save.flowChosen=true, onFlowModeChanged -> Narrator "Oyun akışı: Rahat. …"
StationOption d = StationOption.direct(engine.getStationOptions(oid, -1, st.id)); // null -> panel aç; değilse doğrudan uygula
engine.applyStationOption(oid, -1, Station.SOS.id, "auto");          // otomatik malzeme işi
engine.applyStationOption(oid, -1, Station.SOS.id, "remove:sogan");  // Dengeli istek farkı: tüm soğan eklemelerini çıkar, stoğa iade
```
- **`auto` işi**: zamanlı (oyuncu yuvası), süre = eklenecek her malzemenin `addTopping` süresi toplamı (tepsiden yarısı, hız/tempo dahil, en az 0,25 sn). Tüketim başta (önce tepsi, sonra stok); bir malzeme bile yoksa hiçbir şey tüketilmez → `OUT_OF_STOCK` + `onOutOfStock(order, ilkEksik)`; `missingIngredients/emergencyCostForMissing/emergencyBuyMissing` bu eksikleri verir/alır. Sonuçlar: `STARTED, OUT_OF_STOCK, NO_SLOT, STEP_LOCKED, ORDER_BUSY, INVALID, NO_ORDER, ORDER_READY, NOT_RUNNING`.
  - Rahat: hedef küme (varsayılan ± istekler) kurulur: eksikler eklenir, hedefte olmayan eklenmişler çıkarılıp iade edilir; iş bitince adım biter.
  - Dengeli: günün varsayılan kümesi eklenir (kalem başına bir kez, `OrderLine.isAutoDone()`); eklenen küme hedefle aynıysa adım biter, değilse açık kalır (`finished=false`).
  - Gerçekçi: yalnızca `standardSetUnlocked()` (çırak ≥ 2) ve kalem boşken; varsayılan küme eklenir ve adım biter (istekli siparişte kullanmak teslimde hata). `GameEngine(seed, save, true)` (v0.6 dengesi) bu kısayolu sunmaz.
- Olay sırası: `onAutoToppingsStarted` → (süre) → `onToppingsAutoAdded(…, finished)` → finished ise `onToppingsFinished` → `onStepFinished(step "Malzemeleri ekle")` → `onItemFinished` → `onOrderReadyToPack`. Otomatik iş `onStepStarted` GÖNDERMEZ (ses döngüsünü `onAutoToppingsStarted`/`onToppingsAutoAdded` ile kur).
- **`removeTopping`**: `STARTED` (çıkarıldı, `onToppingRemoved(…, remaining=0)`), `NOTHING_TO_UNDO` (eklenmemiş), `ORDER_BUSY`, `INVALID` (+ `onOptionRejected("remove:<id>")`).
- **Otomatik boy**: `FlowMode.autoSize(istenenBoy)` doğruysa DONER menüsünde boy seçenekleri yerine tek `step` vardır; dilimleme istenen boyla yapılır (boy hatası olamaz). Narrator: "Döneri dilimle başladı, boy otomatik: yarım ekmek, 2 saniye." (1. gün boy anılmaz).
- **Sabır** (`addOrder`): `(tip.sabır × patienceFactor + işYükü × workPatience + elleİş × 1,0) × yükseltmeler × tempo × akış.patienceMult`; `elleİş = Order.interactionWork(...)`: Gerçekçi'de boylu kalem +3 sn, malzemeli kalem +3 sn + hedef malzeme başına 1 sn + istek farkı başına 1,5 sn; Dengeli'de yalnızca elle olanlar (yarım/duble boy +3; istek farkı olan kalemde +3 + 1,5/fark); Rahat 0. Geliş sıklığı × `arrivalMult` (Gerçekçi 0,9), bahşiş × `tipMult` (Gerçekçi 1,15).
- **Narrator**: `autoToppingsStarted`, `toppingsAutoAdded` ("Varsayılan malzemeler eklendi: domates, marul ve soğan. İstek: soğansız. Farkı sen yap, sonra Malzemeleri bitir." / Rahat "Malzemeler eklendi: … İstek uygulandı: soğansız."), `flowModeChanged`, statik `newSizesText(day, mode)`; `dayStarted` ve `statusText` sonunda "Oyun akışı: Dengeli.".
- **UI** (`ui/`): Ayarlar > Oyun akışı (3 düğme, seçili "…, seçili"), brifing satırı + "Oyun akışı: X" düğmesi (sıradakine geçer; `flowChosen=false` ise ilk brifingte kısa sesli soru), Duraklat menüsünde aynı düğme (`engine.setFlowMode`), `Texts.tutTopping/tutSize/quickHelp(mode)`, yardımda "Oyun akışı" bölümü, `MenuPanel` grup başlığı "Malzeme çıkar".

## 1. Tipik akış (UI)
```java
SaveData save = SaveData.parse(prefsString);             // null/boş => yeni oyun; v0.4/v0.5 kaydı da yüklenir
// MARKET: Shop.availableIngredients(save) (yalnızca açık malzemeler); Shop.buyRecommended(save); Shop.buyUpgrade(save, "cirak");
GameEngine engine = new GameEngine(System.nanoTime(), save);   // save'i doğrudan günceller
Narrator narrator = new Narrator(engine, (text, urgent) -> speaker.say(text, urgent));
engine.addListener(narrator);                                   // + kendi GameListener'ın (ses/titreşim/görsel)

// --- GÜN ÖNCESİ HAZIRLIK (isteğe bağlı; gün süresine sayılmaz) --- (seçenek menüsü sipariş olmadan da çalışır)
engine.beginPrep();
for (StationOption op : engine.getStationOptions(-1, -1, Station.DONER.id)) { /* "4 porsiyon dilimle", "2 porsiyon dilimle" */ }
engine.applyStationOption(-1, -1, Station.DONER.id, "prep:doner:et_doner:4");
engine.applyStationOption(-1, -1, Station.ICECEK.id, "prep:drink");
engine.applyStationOption(-1, -1, Station.SOS.id, "prep:tray");
engine.tick(dt);                                      // PREP'te de çağır
engine.startDay();                                    // "Dükkânı aç"

// --- GÜN (her karede) ---
engine.tick(dt);
engine.selectOrder(id);  engine.selectItem(1);        // kart / kalem dokunuşu (isteğe bağlı)

// İstasyon düğmesi: menüyü OLUŞTUR (TalkBack eylemleri VE panel aynı listeyi kullanır)
List<StationOption> ops = engine.getStationOptions(orderId, -1 /*otomatik kalem*/, stationId);
//  TalkBack:  her op için AccessibilityAction(label = op.label())  -> seçilince applyStationOption(orderId, -1, stationId, op.id)
//  Panel:     her op için büyük düğme (contentDescription = op.label(); !op.enabled ise soluk + lockReason)
StartResult r = engine.applyStationOption(orderId, -1, stationId, op.id);   // iki yol da BUNU çağırır

// SOS örneği (tek tek):
engine.applyStationOption(oid, li, Station.SOS.id, "add:domates");   // 0,6 sn sonra onToppingAdded "Domates eklendi"
engine.applyStationOption(oid, li, Station.SOS.id, "add:domates");   // 2. kez -> bol (onToppingAdded count=2)
engine.applyStationOption(oid, li, Station.SOS.id, "undo");          // son ekleneni geri al (stok iade)
engine.applyStationOption(oid, li, Station.SOS.id, "read:added");    // onStationInfo -> Narrator "Eklenenler: ..."
engine.applyStationOption(oid, li, Station.SOS.id, "finish");        // "Malzemeleri bitir"
// DONER örneği (boy seçimi):
engine.applyStationOption(oid, li, Station.DONER.id, "size:yarim");  // yarim | tam | duble
// Tek eylemli istasyon (PAKET): getStationOptions tek eleman döner -> UI menü açmadan doğrudan uygular
engine.deliver(orderId);               // sipariş paketlenince (isReady) teslim
// bitince onDayFinished/onDayFailed -> prefs.putString(save.serialize())
```
Gün bitince motor `save.day`'i artırır, kirayı düşer. Gün BAŞARISIZ olursa para/stok/hazır içecek gün başına döner (hazırlık harcamaları dahil), yıldız en az 1.5 olur, `save.day` aynı kalır.

## 2. Durum makinesi
`READY → (PREP) → RUNNING → FINISHED | FAILED` (v0.5 ile aynı)
- `READY`: yeni motor. `tick()` veya sipariş eylemi günü kendiliğinden başlatır (`onDayStarted` bir kez); `startPrep*`/`applyStationOption("prep:...")` READY'yi PREP'e geçirir.
- `PREP`: müşteri gelmez, saat akmaz. Yalnızca hazırlık (oyuncu + çırak). Sipariş yok. `getStationOptions(-1,…)` yalnızca hazırlık seçeneklerini verir.
- `RUNNING`: normal gün; hazırlık da yapılabilir (boş yuva varsa).
- `isRunning()` = READY, PREP veya RUNNING. `isPrepPhase()`. `beginPrep()`, `startDay()`.

## 3. Sınıflar (yeni/değişen)
| Sınıf | Açıklama |
|---|---|
| `PortionSize` (enum) | `HALF("yarim","Yarım ekmek")`, `FULL("tam","Tam ekmek")`, `DOUBLE("duble","Duble")`; `id, displayName, halves` (yarım porsiyon birimi 1/2/4), `priceMult` (0,60/1,00/1,70), `timeMult` (0,6/1,0/1,5), `unlockDay` (2/1/4); `portions()`, `isOpen(day)`, `spoken()` ("yarım ekmek"), `byId(id)`, statik `halvesText(halves)` ("3,5"). Sıra HALF<FULL<DOUBLE |
| `StationOption` | menü seçeneği: `id`, `group` (`STEP, SIZE, ADD, CONTROL, READ, PREP`), `name` ("Domates ekle"), `description` (durumlu: "Ketçap ekle, stokta 5"), `enabled`, `lockReason` (kilitliyse; yoksa null), `ingredientId` (ADD/PREP), `outOfStock`; `label()` = enabled ? description : name + ", " + lockReason (TalkBack etiketi) |
| `Ingredient` | + `unlockDay`, `isOpen(day)`, `scalesWithSize()` (ET ve EKMEK kategorisi). 25 malzeme |
| `Product` | + `defaultToppings(day)`, `toppingPool(day)` (yalnızca günün AÇIK malzemeleri), `hasMeat()`, `sizedStepIndex()`, `totalNeeds(day)`. Alanlar `defaultToppings`/`toppingPool` TAM (kilitler dahil) kümelerdir |
| `Step` | + `isSized()` (DONER istasyonunda et tüketen adım: boy seçilir), `isBreadStep()` (EKMEK istasyonunda ekmek/lavaş tüketen adım: istenen boy uygulanır) |
| `OrderLine` | + `getSize()` (istenen boy), `getChosenSize()` (oyuncunun seçtiği; dilimleme başlamadıysa null), `hasSize()`, `sizeMismatch()`, `price()` (boya göre), `displayName()` ("Ekmek arası döner (yarım ekmek)"; tam ekmek eklenmez), `getPool()`/`getDefaults()` (siparişin gününe göre açık küme), `getAddedCounts()` (id -> 1|2), `getAddedList()`, `addedCount(id)`, `addedTotal()`. `getChosen()`: hiç eklenmediyse ve bitirilmediyse null, eklemeler sürerken kısmi küme, bitince son küme |
| `Order` | + `day`; `price()` boy dahil toplam; `itemsText()` boylu; `workSeconds()` boy ve tek tek ekleme dahil |
| `ToppingCheck` | + `getSizeMistakes()` (`SizeMistake{productName, ordered, served, tooMuch()}`), `ingredientMistakes()`, `sizeMistakeCount()`, `getSizedLines()`; **`mistakes()` = malzeme + boy hataları**; `perfect()` hepsi doğru |
| `ActiveStep` | + `isToppingAdd()`, `getAddedIngredient()`, `getSize()` (dilimleme/ekmek adımında kullanılan boy), `getAmount()` (hazırlık miktarı) |
| `StartResult` | STARTED, NO_ORDER, WRONG_STATION, NO_SLOT, OUT_OF_STOCK, ORDER_BUSY, ORDER_READY, NOT_RUNNING, NEEDS_TOPPINGS, STEP_LOCKED, INVALID, **LOCKED, MAX_REACHED, NOTHING_TO_UNDO, FULL, INFO, UNKNOWN_OPTION** |
| `CustomerType` | + `halfChance`, `doubleChance` (boy olasılığı; kalan tam ekmek) |
| `DaySummary` | + `sizeMistakes` (boy hatalı teslim sayısı; `mistakes`'e de dahil), `newIngredientsNext` ("ketçap, mayonez") |
| `Catalog` | + `ingredientsForDay(day)`, **`newIngredientsOnDay(day)`** (sos/salata; 1. günde domates, soğan, marul) |
| `Shop` | + `availableIngredients(save)`; `buyIngredient` kapalı malzemede `LOCKED`; öneri yalnızca açık malzeme + boy talebi |

### Sos/salata takvimi (`Ingredient.unlockDay`)
| Gün | Açılan malzeme |
|---|---|
| 1 | domates, soğan, marul |
| 3 | ketçap, mayonez |
| 5 | acı sos |
| 7 | turşu |
| 8 | süzme yoğurt (zurna dürüm ile) |
| 9 | nar ekşisi |
| 11 | yeşil biber, sumak (yeni) |
| 13 | sarımsaklı yoğurt sos (yeni) |
| 14 | limon (lahmacun ile) |
| 15 | közlenmiş biber ve domates (yeni) |
Müşteri istekleri (`acılı` 5., `turşusuz` 7. günden…), ürün varsayılan kümeleri (`pilav üstü` varsayılanı artık domates, soğan, turşu), seçenek listesi (`getStationOptions`, `getToppingOptions`), hazır tepsi seçimi (`getPrepTrayChoices`), market ve öneri YALNIZCA açık malzemeyi kullanır. Havuz tam açıkken ürün başına ≤ 8 malzeme. Duyuru: `Narrator.newIngredientsText(day)` ("Yeni malzemeler: ketçap ve mayonez." / "Yeni malzeme: acı sos."), `Narrator.dayStarted` bunu içerir, `DaySummary.newIngredientsNext` + `Narrator.dayFinished` ("Yarın yeni malzeme: …"), `Texts.briefingLines` satırı.

### Ürünler (boy, adım, varsayılan topping — tam liste için `Catalog`)
Boylu ürünler (döner/tavuk içeren): ekmek arası döner, pilav üstü, tavuk dürüm, Hatay dürüm, ekmek arası tavuk, zurna, iskender, porsiyon döner. Boysuz: ayran, kutu içecek, lahmacun, künefe.
Açılış günleri/adımlar v0.5 ile aynı. Pilav üstü varsayılan: domates, soğan, turşu.

## 4. Döner porsiyon boyu
| Boy | Porsiyon | Fiyat | Dilimleme/ısıtma süresi | Açılış |
|---|---|---|---|---|
| Yarım ekmek | 0,5 | ürün fiyatının %60'ı | ×0,6 | 2. gün |
| Tam ekmek | 1 | %100 | ×1,0 | 1. gün |
| Duble | 2 | %170 | ×1,5 | 4. gün |
- Müşteri boy ister (tip başına olasılık; `rollSizes`): 1. gün yalnızca tam, 2-3. gün yarım, 4. gün + duble. Sipariş duyurusu/kartı "Ekmek arası döner (yarım ekmek)", "(duble)"; tam ekmek ayrıca söylenmez.
- **DONER istasyonunda oyuncu boyu SEÇER**: `applyStationOption(o, li, DONER, "size:yarim|tam|duble")`. Seçilen boy ≠ istenen = **boy hatası**: teslimde `onOrderMistake` (ceza 0,05 yıldız/hata, tavan 0,15, bahşiş yarıya), `ToppingCheck.getSizeMistakes()`, `DaySummary.sizeMistakes`. Doğru boy kusursuzluğa sayılır. Seçenekler hangisinin doğru olduğunu belli etmez.
- Kilit: boy kendi açılış gününden önce seçilemez (`LOCKED`, `onOptionRejected`), **sipariş edilen boy istisna** (çıkmaz olmasın).
- **Et ve ekmek tüketimi boya göre** (yarım porsiyon sayacı, `ceil` yok): ihtiyaç = adımın miktarı × `halves`. Dilimleme adımında et (DONER, `Step.isSized()`) SEÇİLEN boyla; ekmek/lavaş ısıtmada (`Step.isBreadStep()`) İSTENEN boyla (ısıtma boy seçimi gerektirmez). Karışık malzemeler (harç, tereyağı, pirinç) ölçeklenmez. Stoktan düşen birim tamdır; yarım kalan "açık yarım" olarak motorda tutulur (sonraki yarım kullanır, gün sonunda silinir): 2 yarım ekmek = 1 ekmek.
- **Hazır dilimlenmiş döner yarım-porsiyon sayacıdır**: `getReadyMeatHalves(meatId)` (2 = 1 porsiyon), `getReadyDonerHalves()`; `getReadyMeat/getReadyDoner` = tam porsiyon (aşağı yuvarlanır). Boyla dilimleme hazırdan yeterliyse (`ihtiyaç ≤ hazır`) hazırdan düşer: süre = 0,8 × `timeMult` (en az 0,5 sn); yetmezse hazıra dokunulmaz, stoktan normal süreyle yapılır. `Narrator.readyStockText`: "Hazır: döner 3,5 porsiyon."
- **Hazırlık**: `startPrepDoner(meatId)` = 4 porsiyon (6 sn), `startPrepDoner(meatId, 2)` = 2 porsiyon (3,6 sn); başka miktar `INVALID`. Kapasite 8 porsiyon/malzeme (yarım porsiyon hassasiyetiyle kontrol). Menü seçeneği: "4 porsiyon dilimle", "2 porsiyon dilimle" (menüde birden çok et varsa "4 porsiyon et döner dilimle").
- Gün sonu israf: hazır yarımlar yukarı yuvarlanarak `wastedPortions`, değeri `wastedCost` (yarım için fiyatın yarısı).
- Çırak (seviye 2/3) dilimleme/ısıtmayı İSTENEN boyla yapar (hatasız).
- Ekonomi: ortalama fiyat/talep ~dengeli; market önerisi 4. günden itibaren et/ekmek talebini %12 artırır (`Shop.sizeDemand`).

## 5. Tek tek malzeme ekleme (SOS istasyonu)
```java
StartResult addTopping(int orderId, int itemIndex, String ingredientId)   // itemIndex<0: otomatik kalem
StartResult removeLastTopping(int orderId, int itemIndex)
StartResult finishToppings(int orderId, int itemIndex)
Map<String,Integer> getAddedToppings(int orderId, int itemIndex)         // id -> 1|2
```
- Önkoşul: kalemin diğer adımları (ısıt, dilimle...) bitmiş (`STEP_LOCKED` + `onStepLocked` aksi halde). Ekleme **kısa bir iştir**: salata (SALATA kategorisi) 0,6 sn, sos (SOS) 0,5 sn; hazır tepsiden eklenirse yarısı (0,3 / 0,25); istasyon hızı ve tempo uygulanır (en az 0,25 sn). Oyuncu yuvasını kullanır; ekleme sürerken kalem meşguldür.
- **Tüketim hemen** (tepsi varsa önce tepsiden 1 kullanım, yoksa stoktan 1). Stok yoksa `OUT_OF_STOCK` + `onToppingsRejected(order,item,OUT_OF_STOCK,ing)`; hiçbir şey tüketilmez (UI `emergencyBuy(ingId)` önerir).
- **Bol**: aynı malzeme ikinci kez eklenince `count=2` (bol, +1 birim). Üçüncüsü `MAX_REACHED` + `onToppingsRejected(...,MAX_REACHED,ing)`. Bol, hedef doğrulamasını değiştirmez (yalnızca masraf/zaman).
- Havuz dışı / kapalı (kilitli gün) malzeme `INVALID` + `onToppingsRejected`.
- **Geri al**: son eklenen malzemeyi stoğa (tepsiden geldiyse tepsiye) iade eder; yalnızca malzeme adımı bitmemişken ve ekleme sürmüyorken. `NOTHING_TO_UNDO` / `ORDER_BUSY` + `onOptionRejected("undo", …)`.
- **Malzemeleri bitir**: adımı anında tamamlar (boş küme geçerli = sosuz). `onToppingsFinished` + `onStepFinished` + (kalem hazırsa) `onItemFinished`, `onOrderReadyToPack`, `onNextStepHint`. Ekleme sürerken `ORDER_BUSY`, kilitliyse `STEP_LOCKED`.
- **Doğrulama eski gibi TESLİMDE** (`deliver`): eklenen küme (bol sayılmaz) ≠ hedef küme (günün açık varsayılanı ± istekler) -> malzeme başına −0,05 yıldız (tavan −0,15), bahşiş yarıya; tam doğruysa `onOrderPerfect` + bonus. Boy hataları aynı sayaçta toplanır.
- `getToppingTarget(orderId,item)` ve `Narrator.toppingTarget` = "Hedefi oku"; "Eklenenleri oku" = `Narrator.addedText` ("Eklenenler: domates bol ve marul."; hedefle karşılaştırma YAPMAZ).
- Hazır tepsi varsa ilgili ekleme süresi yarıya iner (v0.6 §2).

## 6. İstasyon eylem menüsü
```java
List<StationOption> getStationOptions(int orderId, int itemIndex, int stationId)
StartResult applyStationOption(int orderId, int itemIndex, int stationId, String optionId)
int resolveStationItem(int orderId, int itemIndex, int stationId)   // menünün baktığı kalem (-1: yok / PAKET)
```
- `orderId < 0` veya sipariş yok/hazır: yalnızca **hazırlık** seçenekleri. `itemIndex < 0`: kalem otomatik (seçili kalem, sonra istasyonda açık adımı olan ilk kalem, sonra kilitli/sürenler). Gün bitti: boş liste.
- Kilitli seçenekler listede kalır (`enabled=false`, `lockReason`): "2. günde açılır", "Çalışıyor", "Bir işlem sürüyor", "Önce kalemin diğer adımları bitmeli", "Önce tüm kalemler hazır olmalı", "Paketleme sürüyor", "Ekleme sürüyor", "En fazla bol, iki kez eklenir", "Henüz malzeme eklenmedi", "Hazır stok dolu", "Malzeme yetmiyor: …", "Yeterli malzeme yok". Kilitli seçeneği yine de uygulamak durumu değiştirmez ve nedeni Narrator ile duyurur.
- **Stok yetersizliği seçeneği KİLİTLEMEZ**: `enabled=true, outOfStock=true` (açıklama "… stokta yok" / "… malzeme yok"); uygulanınca `OUT_OF_STOCK` + olay; UI acil alımı önerir (`emergencyBuy(op.ingredientId)` veya `emergencyBuyMissing(orderId)`). Hazırlık seçenekleri stok yoksa kilitlenir (acil alım yok).
- Birden çok kalemli siparişte ad/açıklama kalem adı önekiyle gelir ("Ekmek arası döner: Ekmeği ısıt").

| İstasyon | Seçenekler (id) | Not |
|---|---|---|
| DONER | `size:yarim`, `size:tam`, `size:duble` (dilimleme adımı açıkken), `prep:doner:<et>:4`, `prep:doner:<et>:2` | açıklama: "Duble, 2 porsiyon, 5 saniye" (+ ", hazır dilimden", ", malzeme yok") |
| EKMEK, IZGARA, TATLI, ICECEK | `step` (istasyondaki ilk açık adım, ad = adım adı); ICECEK + `prep:drink` ("4 bardak ayran doldur", ayran menüdeyse) | `step` = `startStep` ile aynı |
| SOS | `add:<malzeme>` (açık havuz sırasında), `undo`, `finish`, `read:added`, `read:target`, `prep:tray`; lahmacun harcı için `step` | ikinci eklemede ad "Domates bol ekle"; açıklama "Domates bol ekle, 1 kez eklendi, stokta 4"; tepsi: "…hazır tepside 6" |
| PAKET | `step` ("Siparişi paketle") | tek eylem: UI doğrudan uygular |

`applyStationOption` sonuçları: uygulandıysa `STARTED` (zamanlı iş başladı veya anında eylem tamam), `INFO` (read:*; `onStationInfo`), aksi halde neden: `NO_ORDER, ORDER_READY, NOT_RUNNING, WRONG_STATION` (−0,02 yıldız, istasyonda o kalemde iş yok), `STEP_LOCKED, ORDER_BUSY, NO_SLOT, OUT_OF_STOCK, LOCKED` (boy kilitli), `MAX_REACHED, NOTHING_TO_UNDO, FULL` (hazır stok dolu), `INVALID` (havuz dışı), `UNKNOWN_OPTION`. İlgili red olayı her zaman verilir (Narrator duyurur): `onToppingsRejected`, `onOptionRejected`, `onOutOfStock`, `onStepLocked`, `onNoFreeSlot`, `onWrongStation`, `onPrepRejected`.

## 7. GameEngine (yeni/önemli üyeler; v0.5 üyeleri geçerli)
```java
GameEngine(long seed, SaveData save)
// --- v0.6 eylem menüsü ve tek tek ekleme
List<StationOption> getStationOptions(int orderId, int itemIndex, int stationId)
StartResult applyStationOption(int orderId, int itemIndex, int stationId, String optionId)
int resolveStationItem(int orderId, int itemIndex, int stationId)
StartResult addTopping(int orderId, int itemIndex, String ingredientId)
StartResult removeLastTopping(int orderId, int itemIndex)
StartResult finishToppings(int orderId, int itemIndex)
Map<String,Integer> getAddedToppings(int orderId, int itemIndex)
// --- boy ve hazır döner
PrepResult startPrepDoner(String meatId)                 // 4 porsiyon
PrepResult startPrepDoner(String meatId, int portions)   // 4 veya 2
int getReadyMeat(String meatId)  / getReadyMeatHalves(String meatId)   // tam porsiyon / yarım sayacı
int getReadyDoner() / getReadyDonerHalves()
Order spawnOrder(List<String> productIds, List<String> requestTexts)                       // tüm boylar TAM
Order spawnOrder(List<String> productIds, List<String> requestTexts, List<PortionSize> sizes) // senaryo/test (boy açılış gününe bakmaz)
// --- v0.5'ten (değişmeyen): beginPrep/startDay, tick, selectOrder/selectNextOrder/selectItem, startStep(2 ve 3 argümanlı),
//     deliver, startPrep*, getReadyDrinks/getTrayUses/getTrayTotal/getReadyCapacity, getPrepMeats/getPrepTrayChoices (artık günün açık malzemeleri),
//     emergencyBuy*, missingIngredient(s) (boya göre), get* sorgular, setSpawning
```

## 8. GameListener (yeni olaylar; hepsi `GameListenerAdapter`'da boş)
```java
// tek tek ekleme
void onToppingAddStarted(Order order, int itemIndex, Ingredient ing, int count /*2=bol*/, float seconds); // ses/animasyon başlat (kısa)
void onToppingAdded(Order order, int itemIndex, Ingredient ing, int count);       // "Domates eklendi" (Narrator); bol: "bol eklendi"
void onToppingRemoved(Order order, int itemIndex, Ingredient ing, int remaining);  // geri alındı, stok döndü
void onToppingsFinished(Order order, int itemIndex, Set<String> chosen);           // "Malzemeleri bitir"; ardından onStepFinished gelir
// boy
void onSizeChosen(Order order, int itemIndex, PortionSize ordered, PortionSize chosen);  // dilimleme başladı (yalnızca oyuncu); hata ipucu VERMEZ
// menü
void onStationInfo(Order order, int itemIndex, String optionId);   // read:added / read:target -> Narrator metni okur
void onOptionRejected(Order order, String optionId, StartResult reason);  // LOCKED, NOTHING_TO_UNDO, ORDER_BUSY, UNKNOWN_OPTION...
```
Olay sırası (tek tek ekleme): `onToppingAddStarted` -> (süre) -> `onToppingAdded`; bitirme: `onToppingsFinished` -> `onStepFinished(step "Malzemeleri ekle")` -> `onItemFinished` -> `onOrderReadyToPack` -> `onNextStepHint`. Dilimleme: `onSizeChosen` -> `onStepStarted` (süre boya göre). `onToppingsRejected` artık `MAX_REACHED` da taşır. v0.5 olayları (hazırlık, çırak, sabır…) aynıdır; eski `onToppingSelectionNeeded` yalnızca `startStep`→`NEEDS_TOPPINGS` eski akışında gelir.
Ses ipucu: ekleme için `onToppingAddStarted` -> kısa tek atış (`seconds`); `onToppingAdded` -> "eklendi" sesi; çırak/hazırlık sesleri v0.5 gibi. Malzeme adımı artık `onStepStarted` GÖNDERMEZ (yalnızca `onStepFinished` gelir) — süre tabanlı ses döngüsünü `onStepStarted`'a bağlıysa malzeme adımında bitiş olayının başlangıçsız gelebileceğini unutma.

## 9. Eski toplu API (geriye uyum; yeni UI kullanmaz)
- `startStep(SOS)` topping adımı için hâlâ `NEEDS_TOPPINGS` + `onToppingSelectionNeeded` verir; `confirmToppings(orderId,item,ids)` eskisi gibi seçimi tek hamlede yapar (süre 2,0 + 0,3×n, tepsi %50'ye kadar kısaltır, tüketim, `onStepStarted`). Kalemde `addTopping` ile ekleme başlamışsa `INVALID`.
- `getToppingOptions` (günün açık havuzu), `getInitialToppings` (1-3. gün önceden seçili), `ToppingOption` aynen durur.
- `startStep(orderId,item,DONER)` boy seçmez: istenen boyu varsayar (boy hatası yok). Boy seçimi yalnızca `applyStationOption("size:…")` ile yapılır.
- Bunlar test/bot geriye uyumu içindir; UI yeniden tasarımında kaldırılabilir.

## 10. Narrator
`new Narrator(engine)` / `new Narrator(engine, output)`. Yeni metin üreticiler (hepsi herkese açık):
`toppingAdded(ing,count)`, `toppingRemoved(ing,remaining)`, `toppingsFinished(order,item,chosen)`, `addedText(order,item)` ("Eklenenleri oku"), `stationInfo(order,item,optionId)`, `sizeChosen(size)`, `optionRejected(order,optionId,reason)`, `optionLabel(StationOption)`, statik `newIngredientsText(day)`, statik `newSizesText(day)`.
- Sipariş duyurusu boy ve istekleri söyler: "Yeni müşteri: Ahmet, Ekmek arası döner (yarım ekmek) ve Ayran istiyor. 2 kalem. İstek: soğansız. …".
- Öneri: "Önerilen: Döneri dilimle, yarım ekmek. Döner makinesi istasyonuna dokun." Başlama: "Döneri dilimle başladı, duble, 5 saniye." (2. günden itibaren seçilen "tam ekmek" de söylenir; 1. gün boy anılmaz).
- Ekleme: "Domates eklendi." / "Domates bol eklendi." / "Domates zaten bol, en fazla iki kez eklenir." / "Domates geri alındı, stoğa döndü." / "Geri alınacak malzeme yok." / "Bir ekleme sürüyor, bitmesini bekle."
- Boy hatası şikâyeti: "Hatalı sipariş. Yarım ekmek istemiştim, duble yapmışsın. Çok fazla. … Yıldız düştü, bahşiş yarıya indi."
- Brifing: `dayStarted` "… Yeni ürün: …. Yeni malzemeler: ketçap ve mayonez. Yeni boy: yarım ekmek. Döner makinesinde boyu sen seçersin."; `dayFinished` "Yarın yeni malzeme: …" ve "Yanlış boyda dilimlenen sipariş: N."
- `Narrator` kendisi `onToppingAdded/Removed`, `onStationInfo`, `onOptionRejected`'ı konuşur; `onToppingAddStarted`, `onToppingsFinished`, `onSizeChosen` sessizdir (bilgi `onStepStarted/Finished` metinlerinde).
- Hazır döner "döner 3,5 porsiyon" biçimindedir (`readyStockText`, `stationLabel`, `prepFinished`, `statusText`).

## 11. SaveData (`v=4`)
Alanlar v0.5 ile aynı (`day,bestDay,money,stars,inventory,upgradeLevels,tempo,speechOn,speechRate,vibration,musicVolume,sfxVolume,tutorialDone,apprenticeAnnounce,readyDrinks`) + v0.7 `flowMode` (`flow=`), `flowChosen` + v0.8 `timePressure` (`timer=1|0`, eksik = süreli). Yazılan sürüm `v=4`; `parse` v=1 (v0.4), v=2 (v0.5), v=3 (v0.6) ve bilinmeyen sürüm/anahtarları tolere eder (eksik akış = Dengeli). `MainActivity.newGame()` akış ve süre baskısı ayarlarını korur. Yeni malzemelerin (sumak, sarımsaklı yoğurt sos, közlenmiş biber/domates) eski kayıtta stoğu 0'dır (Market alır; gün başında "Önerilen malzemeleri al"). Hazır döner/tepsi/açık yarım KAYDEDİLMEZ.

## 12. Denge (bot simülasyonu; testler `SimulationTest`, `HumanSimTest`, `FlowBalanceTest`, `Campaign`/`TestBot`)
- v0.7: `Campaign(FlowMode)` (varsayılan kurucu Gerçekçi = v0.6 akışı), `Campaign.v06Balance`, `Campaign.humanLike` (insan benzeri: panel açmak ayrı tepki, tepki ±%20, ardışık ekleme 0,7; kalabalık artıran öğeleri müşteri kaçırırken almaz). `TestBot` seçenek menüsüyle oynar (her akışta), `taps` (dokunuş) ve `v04Taps` (aynı siparişlerin v0.4 eşdeğeri) sayar. Sonuçlar `docs/design_v07.md` §3.
- v0.8: `Campaign(FlowMode, boolean timePressure)` süresiz mod kampanyası; `TestBot` süresiz günde 6000 sn sanal koruma kullanır, panelde stok yoksa "Acil al" der. Süresiz sonuçlar `docs/design_v08.md`.
- `TestBot` v0.6 API'siyle oynar: her eylem (boy seçimi, TEK malzeme ekleme, "Malzemeleri bitir", adım) bir tepki süresi alır; ardışık eklemelerde tepki süresi `ADD_REACTION_FACTOR=0,5` ile çarpılır (aynı menüde kas hafızası). `careful=false`: her zaman TAM ekmek ve yalnızca varsayılan malzemeler (boy/malzeme hatası yapar).
- Müşteri sabrı tahmini için malzeme başına iş yükü 1,5 sn (`Product.TOPPING_WORK_EACH`).
- Sonuçlar (hazırlık yapan + yükseltme alan bot): tepki 0,8 sn ve 1,0 sn botları tüm denenen tohumlarda gün 1–40'ı geçer; 1,5 sn (yavaş) bot da geçer. Yükseltmesiz bot ~gün 24–29'da takılır (>10 şart). Hazırlıksız/yükseltmesiz bot 1–5. günleri geçer. Hiç hareket etmeyen bot ≤5 günde başarısız olur. Hazırlık yapan bot yapmayandan kazançlıdır (>%3).
- Yarım ekmek %60 fiyat / %50 et / %60 süre; duble %170 fiyat / %200 et / %150 süre (yarım ve duble birbirini dengeler; ortalama fiyat/talep ~1,0).

## 13. UI'nın yapması gerekenler (özet liste)
1. **Eylem menüsü, her istasyon düğmesi için** (DONER, EKMEK, SOS, IZGARA, PAKET, ICECEK, TATLI + hazırlık düğmeleri): `getStationOptions(selectedOrderId, selectedItem, station.id)`; durum her değiştiğinde (olay/tick) `AccessibilityDelegate`'in eylem listesi YENİDEN kurulmalı.
   - **TalkBack Eylemleri**: her `StationOption` için `AccessibilityAction` (etiket = `op.label()`, Türkçe, durumlu); seçilince `applyStationOption(orderId, item, stationId, op.id)`; etkin olmayanlar da listede kalabilir (etikette neden) — uygulanırsa motor nedenini duyurur.
   - **Seçenek paneli**: TalkBack kapalıyken tek dokunuş, açıkken varsayılan çift dokunuş paneli açar; büyük düğmeler (≥56dp), her düğme tek dokunma = `applyStationOption`, "Kapat", odak başlıkta; panel kapanmadan listeyi tazele (SOS'ta art arda ekleme). Tek eylemli istasyon (PAKET) menü açmadan doğrudan uygulanır (`options.size()==1 && enabled`).
   - **v0.8:** gün (RUNNING) başlayınca ekrandaki ayrı hazırlık düğmeleri ve hazır stok göstergeleri kalkar; gün içi hazırlık YALNIZCA bu istasyon menülerinden (`prep:*`) yapılır; hazır stok "Durumu oku"da ve istasyon düğmesi etiketinde.
   - `OUT_OF_STOCK` sonrası `outOfStock` bayraklı seçenek için acil alım önerisi (`emergencyBuy(op.ingredientId)` / `emergencyBuyMissing(orderId)`).
2. **SOS panelini kaldır** (`SelectionPanel` toplu mod, `NEEDS_TOPPINGS`/`confirmToppings` yolu): yerine tek tek ekleme eylemleri; ekleme sesi `onToppingAddStarted`/`onToppingAdded`, geri al, "Eklenenleri oku", "Hedefi oku", "Malzemeleri bitir". Kalem kartında eklenenleri göster (`OrderLine.getAddedCounts()`, bol işareti).
3. **Boy**: sipariş kartında/kalem satırında boy (`OrderLine.displayName()`, `getSize()`), kalem fiyatı `OrderLine.price()`; DONER menüsünde boy seçimi; boy hatasını teslim sonucunda göster (`onOrderMistake` + `ToppingCheck.getSizeMistakes()`); gün özetinde `sizeMistakes`.
4. **Hazır döner** göstergesi yarım porsiyonla (`Narrator.readyStockText()` / `getReadyDonerHalves()`); hazırlık ekranı: "4 porsiyon dilimle", "2 porsiyon dilimle" (`prep:doner:<et>:4|2` seçenekleri veya `startPrepDoner(id, 2|4)`).
5. **Market**: yalnızca `Shop.availableIngredients(save)` (kapalı malzeme `LOCKED`); brifing/özet ekranlarına yeni malzeme/boy satırı (`Narrator.newIngredientsText/newSizesText`, `DaySummary.newIngredientsNext`).
6. **Öğretici**: boy seçimi ve tek tek malzeme eklemeyi öğret (`spawnOrder(products, requests, sizes)`; 1. günde boy yok, 2. günde yarım).
7. Yeni olayları işle (`GameListenerAdapter`): `onToppingAddStarted/Added/Removed/Finished`, `onSizeChosen`, `onStationInfo`, `onOptionRejected`; malzeme adımı `onStepStarted` göndermez (ses döngüsü kurarken dikkat).
8. `A11yAudit`: istasyon düğmelerinin `getActionList()` içinde beklenen özel eylemler, etiketler Türkçe/boş değil, `performAccessibilityAction` panel olmadan uyguluyor, panel yolu aynı sonucu veriyor.
9. "Durumu oku" (`narrator.statusText()`) hazırlık/kalem/hedef bilgisini içerir; eklenenleri okumak için `Narrator.addedText`.
