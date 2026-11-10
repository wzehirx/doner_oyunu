# Engine API v2.6 (paket `com.doner.oyunu.engine`, saf Java, android.* yok, deterministik)

Bu belge UI geliştirmesi için TEK kaynaktır (UI ajanı yalnızca bunu ve `docs/design_v25.md`'yi okur). Şartname: **`docs/design_v25.md` (v2.5: 60 gün, fiyat ve talep, maaş ve faturalar, banka, çırak teslimi, 5 seviyeli yükseltmeler, kayıt dönemi)**, `docs/terminoloji.md` (metin terimleri), `docs/design_v20.md` (eller + makineler, çırak, sıradaki sipariş), `docs/design_v09.md` … `docs/design_v05.md`, `docs/spec.md`. **§C v2.6 motor düzeltmelerini**, **§B v2.5'i eksiksiz anlatır**; §A ve sonrası önceki sürümlerden geçerli kalanlardır (çakışırsa §B kazanır).

## C. v2.5 -> v2.6 (motor düzeltmeleri; `docs/denetim_v25_fable.md`)

Kayıt biçimi aynı (`v=5`, `epoch=25`; dönem ARTMADI, ilerleme korunur). Yeni kayıt anahtarları: `autoPay=0|1` (yoksa 1), `failStreak=N`; `loan.<id>` satırına 11. alan tür (0 normal, 1 kira, 2 acil destek; yoksa normal).

### C.1 Sıfır kasa / boş stok tuzağı (P0)
```java
// Acil destek (esnaf kredisi): limit, engel, kredi sayısı aranmaz; en yüksek faiz, 10 gün; aynı anda en çok 1 açık
boolean Bank.emergencyAidAvailable(save)   // kasa < Shop.recommendedCost(save) ve açık destek yok
int     Bank.emergencyAidAmount(save)      // önerilen stok maliyeti − kasa, 100'e yukarı, en az 500 (yoksa 0)
Bank.Result Bank.takeEmergencyAid(save)    // OK | TOO_MANY_LOANS (açık destek var) | INVALID_AMOUNT
boolean GameEngine.isEmergencyAidAvailable(); Bank.Result GameEngine.takeEmergencyAid()  // gün içinde de
Bank.EMERGENCY_TERM = 10; Loan.kind: Loan.Kind.NORMAL | RENT | EMERGENCY; Bank.openCount(save, kind)

// Süresiz gün kilidi: sipariş var ama hiçbiri teslime hazır değil, çalışan iş yok, hiçbir adım başlatılamıyor ve
// eksik malzemeyi acil almaya para yetmiyor -> GameEngine.STUCK_SECONDS (20 sn) sürerse bekleyenler CEZASIZ gider,
// gün biter (finishDay; maaş/taksit/fatura normal). Olay: GameListener.onDayStuck(int closedOrders) (varsayılan boş).
boolean GameEngine.isStuckNow();  DaySummary.stuckEnded, stuckClosedOrders

// İflas (motor yalnızca bildirir; UI "Dükkân kapandı" + yeniden başla gösterir)
Finance.Bankruptcy Finance.bankruptcy(save)   // açılacak gün (save.day) için: NONE | EVICTED | NO_MEANS | FAIL_LOOP
Finance.Bankruptcy GameEngine.getBankruptcy(); boolean GameEngine.isBankrupt()
DaySummary.bankruptcy (gün sonu ve başarısız gün sonunda dolar), DaySummary.isBankrupt(), DaySummary.failStreak
boolean Finance.canOperate(save)   // stok + kasa (market fiyatı) ile en az bir ürün yapılabilir
boolean Finance.canRescue(save)    // normal kredi (≥ 500) ya da acil destek alınabilir
SaveData.failStreak                // art arda başarısız gün (başarılı günde 0); Finance.FAIL_LOOP_DAYS = 3
SaveData.restartAfterBankruptcy(old) // yeni oyun, ayarlar + en iyi gün rekoru korunur
```
- **EVICTED**: tahliye kararı var, kasa kiraya yetmiyor ve kira kredisi de alınamıyor (`Bank.rentLoanAvailable` false).
- **NO_MEANS**: `!canOperate && !canRescue`.
- **FAIL_LOOP**: `failStreak ≥ 3 && !canRescue && kasa < önerilen stok maliyetinin yarısı`.
- Motor iflasta günü açmayı ENGELLEMEZ (yanlış pozitif kilitlemesin); UI özet/brifingde `summary.isBankrupt()` / `Finance.bankruptcy(save)` ile yeniden başlatmayı önerir. Başarısız gün acil desteği de geri alır (finans gün başına döner).

### C.2 Gün içi maaş ödemesi (P1)
`GameEngine.payWages()` başarılıysa gelen çırağın seviyesi o günün maaşına yazılır (gün sonunda `DaySummary.wagesDue = Finance.dailyWage(seviye, gün)`). Hiç gelmeyen çırak için maaş yazılmaz (değişmedi).

### C.3 Fiyat gün başında donar (P1)
Motor, kurulurken `save.prices`'ı dondurur (gün başı anlık görüntüsü): talep, geliş çarpanı, kota, ürün seçimi ve sipariş fiyat oranı bu fiyatları kullanır; gün içinde `Pricing.setPercent/setAll` bugünü etkilemez, ertesi günün motorunu etkiler. `int GameEngine.getDayPricePercent(productId)`: bugün geçerli yüzde.

### C.4 Kira kredisi sınırı, kredi notu çiftliği (P2)
```java
Bank.MAX_RENT_LOANS = 2                      // limit dışı (en yüksek faizli) açık kira kredisi; aşılınca TOO_MANY_LOANS
boolean Bank.rentLoanAvailable(save, amount) // normal limit içinde ya da kira kredisi sınırı dolmadı
boolean Bank.earnsRepayBonus(save, loan)     // erken kapatma +3 kredi notu yalnızca: takenDay < save.day ve ≥1 taksit vadesi geldi
```
Normal limit içinde verilen kira kredisi `NORMAL` sayılır (sınıra girmez). `Finance.payWithLoan` kira için artık `TOO_MANY_LOANS` dönebilir.

### C.5 Kesinti kalkınca talep/kota (P2)
`payBill`/`payWages` (ve açılıştaki otomatik ödeme) sonrası menüyle birlikte fiyat talebi (`getPriceArrivalMultiplier`) ve süresiz kota (`getCustomerQuota`) yeniden hesaplanır (ör. doğalgaz ödenince Ocak kapalıyken ×0,9 olan kota geri gelir). PrepBox düğmeleri UI işidir.

### C.6 Otomatik fatura ödemesi (P1)
```java
boolean GameEngine.isAutoPayBills(); void GameEngine.setAutoPayBills(boolean)  // SaveData.autoPayBills (varsayılan true; ayar, korunur)
List<Bill> Finance.autoPay(save, day, includeDueToday)  // ödenen faturaların kopyaları (getPaid() = ödenen tutar)
List<Bill> Finance.autoPayAtOpen(save)                  // brifing: ayar açıksa bugün GECİKEN faturaları öder (yoksa boş)
int Finance.autoPayReserve(save, day)                   // kasada bırakılan pay = o günün önerilen stok maliyeti
DaySummary.autoPaid (List<Bill>), DaySummary.autoPaidTotal
```
- Açıkken **gün açılışında** (motor `beginPrep/startDay`; UI brifingte `Finance.autoPayAtOpen` ile önceden de çağırabilir, çift ödeme olmaz) geciken faturalar, **gün sonunda** (maaş + taksitten sonra, haciz ve kredi notundan önce) son günü bugün olan ve geciken faturalar ödenir. Kasada önerilen stok payı kalmayacaksa o fatura atlanır → normal gecikme/kesinti akışı.
- Açılışta ödenen her fatura için `onBillPaid(bill, paid)` verilir. Gün başarısız olursa ödeme geri alınır.
- `DaySummary.expenses()/profit()` otomatik fatura ödemesini İÇERMEZ (faturalar eskisi gibi ayrı); özet "Faturalar otomatik ödendi: N lira" için `autoPaidTotal`.
- Ayar `copySettingsTo`/`newGameKeepingSettings`/dönem sıfırlamasında korunur.

### C.7 UI yapılacaklar (v2.6; UI ajanı)
1. Ayarlar: "Faturaları otomatik öde" anahtarı (`setAutoPayBills` ya da `save.autoPayBills`).
2. Özet: `autoPaid` satırı; `stuckEnded` cümlesi ("Yapılacak iş kalmadı, dükkânı erken kapattın"); `isBankrupt()` ise "Dükkân kapandı" + `SaveData.restartAfterBankruptcy`.
3. Brifing/Banka: `Bank.emergencyAidAvailable` ise "Acil destek al: N lira" düğmesi (öne); iflasta yeniden başla.
4. Banka: kira kredisi sınırı dolunca `TOO_MANY_LOANS` metni; erken kapatmada not ödülü yalnızca `earnsRepayBonus` ise söylenmeli.

## B. v2.0 -> v2.5

### B.0 Özet
| Konu | v2.0 | v2.5 |
|---|---|---|
| Kampanya | 40 gün (+döngü) | **60 gün** (`DayConfig.CAMPAIGN_DAYS`), sonrası sonsuz dalga; 24 ürün, 39 malzeme, 15 müşteri tipi, 16 yükseltme (§B.7, §B.8) |
| Satış fiyatı | sabit (katalog) | oyuncu belirler: **%50–%150, %5 adım** (`Pricing`); talep, yıldız, bahşiş, sabır fiyata bağlı (§B.2) |
| Gün sonu | günlük kira kasadan otomatik | **çırak maaşı + kredi taksiti + (vergi haczi)** otomatik; **faturalar** (elektrik, doğalgaz, su, kira, vergi) kesilir, oyuncu öder/erteler/krediyle öder (§B.3) |
| Ödenmeyen fatura | – | türüne özel zorluk: elektrik/doğalgaz/su kesintisi, kira ihtar→ihtarname→**tahliye** (gün açılamaz), vergi **haczi** (§B.4) |
| Banka | – | kredi limiti, faiz, vade, günlük taksit, gecikme, erken kapatma, kredi notu (§B.5) |
| Çırak | paketler, teslimi oyuncu yapar | **paketlenen siparişi teslim de eder** (seviye 1+, ayar `apprenticeDelivery`); seviye 4 kalfa, 5 dördüncü çırak; **günlük maaş** (§B.6) |
| Yükseltme | 3 seviye | 5 seviye (tabela 6), 5 yeni yükseltme (§B.7) |
| Kayıt | `v=4` | **`v=5`, `epoch=25`**: dönemi eski kayıt = ilerleme sıfır + ayarlar korunur + bir kez bildirim (§B.1) |
| Metin | – | terim birliği (`docs/terminoloji.md`): "sipariş 2", "teslime hazır", "stokta yok", "hazır ayran 3 bardak" |

### B.1 Kayıt dönemi (epoch)
```java
SaveData.EPOCH = 25; SaveData.FORMAT = 5;       // kayıt "v=5\nepoch=25\n…"
SaveData s = SaveData.parse(prefsText);         // epoch yok/küçük -> ilerleme sıfır, ayarlar korunur
if (s.progressResetNotice) { speaker.say(Narrator.progressResetText()); s.clearProgressResetNotice(); save(); }
SaveData.newGameKeepingSettings(old);           // "Yeni oyun" ile aynı kural (MainActivity.newGame bunu kullanır)
old.copySettingsTo(target);                     // yalnızca ayarlar
```
- **Sıfırlanan (ilerleme):** gün, en iyi gün, para, yıldız, stok, yükseltmeler, hazır ayran, fiyatlar, faturalar, krediler, kredi notu, maaş borcu, vergi birikimi.
- **Korunan (ayar):** tempo, konuşma açık/kapalı ve hızı, titreşim, müzik/efekt/ambiyans sesi, öğretici tamamlandı, çırak duyuruları, **çırak teslimi**, oyun akışı ve "seçildi" bayrağı, süre baskısı.
- `progressResetNotice` yalnızca eski kayıtta ilerleme anahtarı (gün/para/yıldız/stok/yükseltme) varsa true olur; kayda `resetNotice=1` yazılır (uygulama bildirimi göstermeden kapanırsa yine gösterilir). Gelecek dönem (epoch > 25) olduğu gibi yüklenir. İleride sıfırlamak için yalnızca `EPOCH` artırılır.
- Yeni anahtarlar: `epoch, apprenticeDelivery, resetNotice, price.<ürün>, bill.<id>=tür,tutar,kesildi,son,ödenen, loan.<id>=…, credit, wageDebt, salesTax, finId`. Bozuk satırlar atlanır.

### B.2 Fiyat ve talep (`Pricing`)
```java
int  Pricing.percent(save, productId)            // 50..150 (yoksa 100)
boolean Pricing.setPercent(save, productId, pct) // v2.7: yalnızca 50..150'ye sınırlar (her TAM yüzde geçerli; eskiden %5'e yuvarlanırdı); 100 = kayıttan çıkar
int  Pricing.raise(save, id) / lower(save, id)   // bir sonraki/önceki %5 basamağı (112 -> 115 / 110), yeni yüzde
void Pricing.setAll(save, pct); boolean Pricing.allDefault(save)
int  Pricing.priceOf(save, product)              // tam ekmek satış fiyatı (TL); referans: product.price
double Pricing.arrivalMultiplier(save)           // tahmini müşteri çarpanı (fiyat ekranı: Narrator.demandText)
double Pricing.demand(ratio, eff); Pricing.sensitivity(type, stars); Pricing.typeDemand(save, type, menu, stars)
Pricing.Feel Pricing.feel(orderRatio, eff)       // teslimde: starMult, tipMult, penalty, reaction
double Pricing.patienceMult(orderRatio, eff)     // ucuzda sabır bonusu
// sabitler: MIN_PERCENT 50, MAX_PERCENT 150, STEP_PERCENT 5, DEFAULT_PERCENT 100, DEMAND_K 1,7, CHOICE_K 1,5, MIN_DEMAND 0,15, MAX_DEMAND 1,8
```
- Fiyat **gün başında** okunur (market/fiyat ekranında değiştirilir; gün içinde değiştirmek o günü etkilemez). Kalem fiyatı sipariş anında sabitlenir: `OrderLine.price()` (satış), `OrderLine.referencePrice()`, `OrderLine.getPriceRatio()`, `Order.price()/referencePrice()/priceRatio()`.
- `CustomerType.priceSensitivity` (öğrenci 1,8 … gurme 0,3). Formüller: design_v25 §2. Teslimde `Order.getPriceReaction()` −1/0/+1 ("uygun buldu"/–/"pahalı buldu"), `DaySummary.priceComplaints/priceCompliments`.
- Süreli günde geliş aralığı `/ getPriceArrivalMultiplier()`; süresizde kota × sınırla(A; 0,5; 1,5) (`DayConfig.quotaFor(save)` aynı değeri verir). `Shop.recommendedPurchase` talebe uyar.
- Tüm fiyatlar %100 iken her şey v2.0 ile birebir aynıdır.

### B.3 Maaş ve faturalar (`Finance`, `Bill`, `BillType`)
```java
// maaş
int Finance.dailyWage(level, day)                 // 250/550/900/1250/1650 × (1 + 0,01·(gün−1))
Finance.PayResult Finance.payWages(save)          // OK, NOT_ENOUGH_MONEY, NOTHING_DUE (UNKNOWN)
// faturalar
List<Bill> Finance.openBills(save)                // son ödeme gününe göre sıralı
Bill Finance.bill(save, id); int Finance.totalDue(save); int Finance.dueWithin(save, days); int Finance.lateCount(save)
List<Bill> Finance.forecast(save, days)           // yaklaşan (tahmini, id=-1) faturalar
int Finance.billAmount(save, type, day); int Finance.load(save, type); int Finance.weeklyRentBase(day)
Finance.PayResult Finance.pay(save, billId)       // tam öder (bugünkü tutar); zamanında ise kredi notu +2
int Finance.payAll(save, keep)                    // kasada en az keep bırakarak öder; ödenen sayısı
Bank.Result Finance.payWithLoan(save, billId, term) // fatura tutarında kredi alır ve öder (kira için limit aranmaz)
List<String> Finance.notices(save, day)           // gün açılırken söylenen uyarılar (brifingde de göster)
Bill Finance.issue(save, type, amount, issuedDay) // senaryo/öğretici/test
// Bill: id, type, amount, issuedDay, dueDay, daysLate(day), daysLeft(day), lateFee(day), amountDue(day), level(day), getPaid()
// BillType: ELEKTRIK, DOGALGAZ, SU, KIRA, VERGI — id, displayName ("Elektrik faturası", "Kira"), shortName, firstDay, period, issuedOn(day), nextIssueDay(day)
// sabitler: DUE_DAYS 2, LATE_FEE_RATE 0,03, EVICTION_DAYS 6, TAX_RATE 0,05, SEIZURE_FINE 0,20, CREDIT_START 60
```
- Takvim: elektrik 3,10,17…; doğalgaz 4,11…; su 5,12…; kira 7,14…; vergi 10,20…; fatura **gün sonunda** kesilir, **son ödeme = kesildiği gün + 2**. `save.day` = açılacak gün; gecikme = `save.day − dueDay`. Gün sonu ekranında (`save.day` artmıştır) ödenen fatura, açılacak gün son günden sonraysa gecikmiş sayılır — UI "son ödeme günü bugün" uyarısını brifingte ve gün sonunda göstermeli.
- Tutarlar: elektrik `7·(30 + 1,5·(g−1) + 10·E)`, doğalgaz `7·(25 + 1,5·(g−1) + 12·G)`, su `7·(15 + 0,8·(g−1) + 6·S)`, kira `7·(200 + 20·(g−1))`, vergi dönem satış+bahşişinin %5'i. E/G/S = `Upgrade.raises(BillType)` olan yükseltme seviyeleri toplamı.
- **Gün sonu sırası** (motor, `finishDay`): vergi birikimi → maaş (önce maaş borcu) → kredi taksitleri → vergi haczi → kredi notu (geciken fatura −2/gün, maaş borcu −1) → yeni faturalar. Sonuçlar `DaySummary` (§B.11).
- **Gün içinde ödeme**: `engine.payBill(id)` (kesinti o an kalkar, menü yenilenir, `onBillPaid`; para yetmezse `onNotEnoughMoney`), `engine.payWages()` (çırak hemen gelir). Gün başarısız olursa para ve faturalar gün başına döner.

### B.4 Kesintiler (`UtilityStatus`)
```java
UtilityStatus u = engine.getUtilityStatus();      // ya da UtilityStatus.of(save, day) (brifing)
u.electric, u.gas, u.water, u.rent, u.tax         // etkin kademe 0..2 (kira 3 = tahliye); electricBill/waterBill: koruma öncesi
u.apprenticeAbsent; u.any(); u.ocakClosed(); u.trayBlocked(); u.waterProductsOff(); u.evicted()
u.speedMult(st); u.capacity(st, cap); u.productAvailable(p); u.arrivalMult(); u.starGainMult(); u.drinkSpoilFraction(); u.meatSpoils(); u.rentStarPenalty()
engine.isStationClosed(Station.IZGARA)           // doğalgaz tam kesik
List<Product> engine.getMenu()                    // kesintide satılamayanlar hariç
GameEngine.LOCK_CLOSED = "Ocak kapalı, doğalgaz kesik"; GameEngine.LOCK_WATER = "Su kesik"
```
| Kademe | Elektrik | Doğalgaz | Su | Kira | Vergi |
|---|---|---|---|---|---|
| 1 (1–2 gün gecikme) | ekmek ısıtıcı −1 yer, ekmek+içecek ×1,3; hazır ayranın yarısı gün başında bozulur | Ocak 1 göz, ×1,3 | tepsi hazırlanamaz (`prep:tray` kilitli "Su kesik"), Sos ve salata tezgâhı ×1,25 | ihtar (uyarı) | gecikme bedeli |
| 2 (3+ gün) | ekmek ısıtıcı 1 yer, ×1,6; döner ×1,3; hazır ayran bozulur; gün sonu et stoklarının %25'i bozulur | **Ocak kapalı**: Ocak isteyen ürünler menüden çıkar, patates yok, müşteri ×0,9; Ocak adımı `UTILITY_OFF`, `ReadyAction.Status.CLOSED` | + çay ve çorba menüden çıkar, teslim yıldızı ×0,6 | 3–5 gün: ihtarname, gün başı −0,15 yıldız, müşteri ×0,9 | 4+ gün: **haciz** (gün sonu borç + %20 kasadan) |
| 3 | – | – | – | **6+ gün: tahliye**: `beginPrep/startDay` false, gün `FAILED` (`summary.failReason = "tahliye"`), para/stok korunur | – |
Jeneratör (1/2) elektrik, su deposu (1/2) su kademesini düşürür. Kesinti gün başında hesaplanır; gün içinde fatura ödenirse yenilenir.

### B.5 Banka (`Bank`, `Loan`, `Bank.Quote`)
```java
int Bank.totalLimit(save); int Bank.available(save); boolean Bank.blocked(save)
double Bank.dailyRate(score)                      // ≥80 0,006 · 60–79 0,008 · 40–59 0,012 · 20–39 0,018 · <20 0,025
Bank.Quote Bank.quote(save, amount, term)         // amount ≥500, 100'ün katı; term ∈ Bank.TERMS {5,10,20}; geçersizse null
Bank.Result Bank.takeLoan(save, amount, term)     // OK, OVER_LIMIT, INVALID_AMOUNT, INVALID_TERM, TOO_MANY_LOANS (3), BLOCKED
Bank.Result Bank.repay(save, loanId)              // erken kapatma: OK, NOT_ENOUGH_MONEY, UNKNOWN (kredi notu +3)
Bank.Result Bank.payOverdue(save, loanId)         // gecikmiş taksiti şimdi öde
List<Loan> Bank.loans(save); Loan Bank.loan(save, id); int Bank.totalDebt(save); int Bank.dueToday(save); int Bank.outstandingPrincipal(save)
// Quote: amount, term, interest, installment, total, dailyRate
// Loan: id, principal, interest, term, takenDay, installment(), getRemaining(), getOverdue(), getFees(), getInstallmentsLeft(),
//       getMissedStreak(), getMissedTotal(), totalOwed(), remainingPrincipal(), remainingInterest(), payoffAmount(), isClosed()
```
- Limit `aşağı100((2000 + 500·gün) × (0,6 + 0,1·yıldız) × sınırla(not/60; 0,25; 1,5)) − kalan anapara`. Taksit gün sonunda otomatik; kaçan taksit +%5 bedel, not −5; art arda 3 kaçan taksitte yeni kredi yok. Kira faturası için `Finance.payWithLoan` limit/engel aramaz (en yüksek faiz).
- Kredi notu `save.creditScore` 0–100 (başlangıç 60).

### B.6 Çırak teslimi ve seviyeler
- Seviye 1+: eli boş çırak **önce teslime hazır siparişleri** (en acilden) teslim eder; seçili siparişi yalnızca `apprenticeMayTouchSelected()` (oyuncunun eli dolu ya da son eyleminden ≥1,5 sn). Teslim 1,0 sn × tempo × yavaşlık (aktif iş, `ActiveStep.isDeliver()`, istasyon PAKET). Ödeme/bahşiş/yıldız oyuncununkiyle aynı.
- `Order.isDelivering()`, `Order.getDeliveredBy()` (0 oyuncu, 1..4 çırak, −1 teslim edilmedi). Çırak teslim ederken `engine.deliver(id)` **false** + `onAlreadyRunning(order, job)`. `getReadyActions`: DELIVER satırı `RUNNING`, `byApprentice=true`, metin "Teslim ediliyor, çırak".
- Olaylar: `onApprenticeAction(a)` (`a.deliver=true`; başlangıç ve bitiş), bitişte `onOrderDelivered` de gelir (Narrator: "Çırak teslim etti: sipariş 2, Ali. 240 lira kazandın, …"; başlangıç sessiz; `apprenticeAnnounce=false` ise "Teslim edildi. …").
- Ayar `SaveData.apprenticeDelivery` (varsayılan açık; kapalıysa v2.0 davranışı). `DaySummary.apprenticeDeliveries`.
- Seviye: 1–3 = çırak sayısı ve yetenek (v2.0 tablosu), **4 = kalfa** (yavaşlık ×1,2 → ×1,05), **5 = dördüncü çırak**. `Upgrades.apprenticeWorkers(lv)`, `apprenticeSkill(lv)`, `apprenticeSlowdown(lv)`; `engine.getApprenticeLevel()` bugün çalışan seviye (maaş borcu varsa 0), `isApprenticeAbsent()`, `getApprenticeWorkers()`.
- Maaş: §B.3. Borç varken çırak gelmez (o gün maaş yazılmaz).

### B.7 Yükseltmeler (1–3. seviyeler v2.0 ile aynı)
| id | Ad | Seviye fiyatları (TL) | Açılış günleri | Etki (seviye 1→son) | Fatura |
|---|---|---|---|---|---|
| `cirak` | Çırak | 3000, 9000, 24000, 38000, 58000 | 3, 8, 16, 30, 44 | 1–3 çırak, 4 kalfa, 5 dördüncü çırak | su |
| `doner_makinesi` | Döner makinesi | 4000, 15000, 48000, 70000, 100000 | 4, 10, 18, 32, 46 | döner ×0,85→0,45 | elektrik, doğalgaz |
| `ekmek_isitici` | Ekmek ısıtıcı | 2500, 8000, 22000, 34000, 52000 | 3, 9, 17, 29, 43 | yer 3,4,4,5,6; ×0,85→0,45 | elektrik |
| `ocak` | Geniş ocak | 2500, 8000, 20000, 32000, 50000 | 3, 9, 17, 29, 43 | göz 3,4,4,5,6; ×0,90→0,55 | doğalgaz |
| `oto_dilimleyici` | Otomatik dilimleyici | 3500, 12000, 34000, 52000, 78000 | 6, 12, 21, 35, 50 | döner ×0,75→0,36 | elektrik |
| `buzdolabi` | Buzdolabı | 2000, 7500, 24000, 36000, 54000 | 2, 8, 16, 28, 42 | stok 50,100,220,300,400; hazır stok +4/seviye | elektrik |
| `masa` | Masa takımı | 3000, 9000, 24000, 36000, 54000 | 11, 22, 28, 38, 52 | sıra +1,2,3,3,4; bahşiş +0→0,10 | su |
| `tabela` | Tabela | 1000, 4000, 12000, 20000, 32000, 48000 | 8, 15, 24, 34, 44, 54 | müşteri +0,10→0,60 | elektrik |
| `klima` | Klima | 2500, 7000, 18000, 28000, 42000 | 13, 21, 26, 37, 49 | sabır +0,10→0,42 | elektrik |
| `muzik` | Müzik sistemi | 1800, 5500, 15000, 24000, 36000 | 4, 14, 23, 33, 47 | müşteri +0,05→0,20, yıldız +0,05→0,25 | elektrik |
| `dekor` | Dekor | 2200, 6500, 20000, 30000, 45000 | 7, 16, 25, 36, 51 | turist +0,10→0,55, bahşiş +0,05→0,22, müşteri +0,05→0,20 | – |
| **`havalandirma`** | Havalandırma | 2500, 8000, 18000, 28000, 42000 | 10, 19, 28, 39, 50 | sabır +0,04→0,18, Ocak ×0,95→0,80 | elektrik |
| **`sogutma`** | Soğutmalı vitrin | 3000, 9000, 20000, 30000, 45000 | 12, 20, 30, 40, 52 | içecek ×0,90→0,60, hazır stok +2→+10 | elektrik |
| **`ikinci_kasa`** | İkinci kasa | 6000, 14000, 28000, 42000, 62000 | 20, 27, 35, 44, 53 | paketleme ×0,80→0,45, sıra +1,1,2,2,2 | elektrik |
| **`jenerator`** | Jeneratör | 8000, 22000 | 9, 30 | elektrik kesintisini 1/2 kademe karşılar | – |
| **`su_deposu`** | Su deposu | 4000, 12000 | 11, 30 | su kesintisini 1/2 kademe karşılar | – |
Yeni `Upgrade.EffectType`: `READY_CAP`, `POWER_BACKUP`, `WATER_BACKUP`. `Upgrade.utilities` / `raises(BillType)`. `Upgrades.powerBackup()/waterBackup()`. `Catalog.newUpgradeLevelsOnDay(day)` (brifing/market "Yeni: …").

### B.8 İçerik takvimi (v2.0 içeriği aynı)
| Gün | Ürün (id, referans fiyat, istasyonlar) | Yeni malzeme | Yeni müşteri tipi (duyarlılık) |
|---|---|---|---|
| 15 | | | Yemek kuryesi `kurye` (1,4) |
| 17 | Tavuk pilav üstü `tavuk_pilav` 280 (Ocak, Döner, Sos) | | |
| 18 | | | Memur `memur` (1,0) |
| 19 | Su `su` 15 (İçecek dolabı) | şişe su | |
| 20 | | maydanoz (sos/salata) | |
| 21 | Mercimek çorbası `corba` 90 (Ocak, Sos; **su gerekir**) | mercimek çorbası | |
| 23 | Kaşarlı dürüm `kasarli_durum` 300 (Ekmek, Döner, Ocak pasif, Sos; patatesli olabilir) | kaşar peyniri | |
| 24 | | | İnşaat işçisi `insaatci` (1,5) |
| 25 | Şalgam `salgam` 40 (İçecek) | şalgam | |
| 26 | | pul biber | |
| 27 | | | Sporcu `sporcu` (0,9) |
| 28 | Fırın sütlaç `sutlac` 85 (Tatlı) | fırın sütlaç | |
| 31 | Çay `cay` 20 (İçecek; **su gerekir**) | çay | |
| 32 | | acılı ezme | |
| 33 | | | Gurme `gurme` (0,3) |
| 34 | Köfte ekmek `kofte_ekmek` 230 (Ekmek, Ocak pasif, Sos; patatesli olabilir) | köfte | |
| 38 | Tavuk iskender `tavuk_iskender` 360 (Ekmek, Döner, Ocak, Sos) | kırmızı lahana | |
| 42 | Fıstıklı baklava `baklava` 160 (Tatlı) | fıstıklı baklava | |
| 46 | Tantuni `tantuni` 260 (Ekmek pasif, Ocak, Sos) | tantuni eti | |
| 50 | Karışık döner tabağı `karisik_tabak` 520 (Ekmek, Döner: et+tavuk, Sos) | | |
Boylu (döner içeren) yeni ürünler: tavuk pilav, kaşarlı dürüm, tavuk iskender, karışık tabak. `Product.needsWater()`. `Catalog.newTypesOnDay(day)`, `Narrator.newTypesText(day)` (brifing; `dayStarted` içerir). Lahmacun havuzuna maydanoz (20. gün). Var olan tiplerin duyarlılıkları: öğrenci 1,8, şikâyetçi 1,3, yaşlı 1,2, taksi 1,1, aile/esnaf 1,0, gece 0,9, acele 0,6, influencer 0,5, turist 0,4.
Zorluk: `DayConfig.forDay(d).intensity` = d (≤40), 40 + 0,5·(d−40) (41–60), 60 sonrası dalgalı artış.

### B.9 Yeni olaylar (`GameListener`, hepsi `default` boş)
```java
void onFinanceNotices(List<String> notices);                 // gün açılırken (hazırlık ya da gün başı), bir kez; boşsa gelmez
void onUtilityBlocked(Order order, Station station, BillType cause); // Ocak kapalı (DOGALGAZ) / tepsi (SU); order hazırlıkta null
void onStockSpoiled(Ingredient ingredient, int amount, BillType cause); // gün başında hazır ayran bozuldu (ELEKTRIK)
void onBillPaid(Bill bill, int paid);                        // gün içinde engine.payBill
```
Değişen: `onApprenticeAction` teslim işini de taşır (`ApprenticeAction.deliver`); `onOrderDelivered` çırak teslimlerinde de gelir (`order.getDeliveredBy() > 0`); `onAlreadyRunning` çırak teslim ederken oyuncu teslimi denerse; `onDayFailed` tahliyede de (`summary.failReason`).
Olay sırası (gün açılışı): `onPrepPhaseStarted` ya da `onDayStarted` → (bozulma) `onStockSpoiled` → (ihtarname) `onStarsChanged` → `onFinanceNotices`. Tahliyede: `onDayFailed` (gün başlamaz).

### B.10 Sonuç tipleri
- `StartResult.UTILITY_OFF` (Ocak kapalı; `onUtilityBlocked`), `PrepResult.UTILITY_OFF` (tepsi su kesik / patates Ocak kapalı). `applyStationOption("prep:*")` artık `HANDS_BUSY`/`STATION_FULL`/`UTILITY_OFF` döndürür (eskiden `NOT_RUNNING`'e düşüyordu).
- `ReadyAction.Status.CLOSED` ("Patates kızart, Ocak kapalı, doğalgaz kesik").
- `Finance.PayResult {OK, NOT_ENOUGH_MONEY, UNKNOWN, NOTHING_DUE}`, `Bank.Result {OK, OVER_LIMIT, INVALID_AMOUNT, INVALID_TERM, TOO_MANY_LOANS, BLOCKED, NOT_ENOUGH_MONEY, UNKNOWN}`.

### B.11 `DaySummary` (yeni)
`wagesDue, wagesPaid, wageDebt, installmentsDue, installmentsPaid, installmentsMissed, seized, newBills (List<Bill>), billsDue, billsLate, loanDebt, creditScore, spoiledDrinks, spoiledStock, spoiledCost, rentStarPenalty, failReason ("tahliye"), priceComplaints, priceCompliments, apprenticeDeliveries`, `expenses()` = maaş + taksit + haciz, **`profit()` = satış + bahşiş − acil alım − expenses()** (faturalar ödendiği gün kasadan düşer). `rent`/`rentDue` @Deprecated, her zaman 0.

### B.12 Narrator (yeni/değişen)
- Kendi konuştukları: `onFinanceNotices` (birleşik, `CAT_BILLS`), `onUtilityBlocked` (`utilityBlocked`), `onStockSpoiled` (`stockSpoiled`), `onBillPaid` (`billPaid`); `dayFinished` sonuna `financeSummary(s)` (maaş borcu, kaçan taksit, bozulan et, pahalı bulan müşteri, yeni faturalar "Yeni fatura: elektrik faturası 315 lira, son ödeme 5. gün.", açık borç); `dayFailed` tahliye metni; `dayStarted` + "Yeni müşteri tipi: …"; `orderReady` "Sipariş 2 teslime hazır: Ali. Teslim et." / "…, çırak teslim edecek."; `orderDelivered` çırak ve fiyat tepkisi; `statusText` + `financeStatusText()` ("Kesinti: elektrik kısmen kesik. Çırak bugün yok, maaş borcu var. Açık fatura 2, borç 640 lira.").
- UI yardımcıları (statik): `billText(bill, day)`, `billsText(save)`, `newBillText(bill)`, `bankText(save)`, `loanText(loan)`, `quoteText(quote)`, `rateText(rate)`, `priceText(save, product)`, `demandText(save)`, `wageText(save)`, `newTypesText(day)`, `progressResetText()`.
- Terim değişiklikleri (`docs/terminoloji.md` §6): "Çırak, sipariş 2: Ayranı doldur.", "Çırak paketledi: sipariş 2.", "Stokta yok: domates. Acil al: 9 lira.", "hazır döner 4 porsiyon / hazır ayran 3 bardak / hazır patates 2 porsiyon", "Ayran doldurma", "Sos ve salata tezgâhı", "Tatlı tezgâhı".

### B.13 Örnek akış (gün sonu → market → gün)
```java
// gün bitti (onDayFinished): save.day artık yarın
DaySummary s = e.getSummary();                     // s.wagesPaid, s.installmentsPaid, s.newBills, s.billsDue
say(narrator.dayFinished(s));                      // Narrator kendisi de söyler
// GÜN SONU / FATURALAR ekranı
for (Bill b : Finance.openBills(save)) {
    row(Narrator.billText(b, save.day));           // "Elektrik faturası 315 lira, son ödeme 5. gün, 2 gün kaldı."
    // [Öde]       Finance.pay(save, b.id)           -> OK | NOT_ENOUGH_MONEY
    // [Ertele]    hiçbir şey (uyarıyı tekrar oku: Finance.notices(save, save.day))
    // [Krediyle]  Bank.quote(save, round100(b.amountDue(save.day)), 10) göster, sonra Finance.payWithLoan(save, b.id, 10)
}
if (save.wageDebt > 0) { /* [Maaşı öde] */ Finance.payWages(save); }
// BANKA ekranı
say(Narrator.bankText(save));                      // not, limit, alınabilir, faiz, borç
Bank.Quote q = Bank.quote(save, 3000, 10);  say(Narrator.quoteText(q));
Bank.takeLoan(save, 3000, 10);  for (Loan l : Bank.loans(save)) row(Narrator.loanText(l));  Bank.repay(save, l.id);
// FİYAT ekranı
for (Product p : Catalog.productsForDay(save.day)) row(Narrator.priceText(save, p));   // [−5] Pricing.lower  [+5] Pricing.raise
say(Narrator.demandText(save));                    // "Tahmini müşteri: yüzde 12 daha az."
// MARKET (değişmedi) + Shop.buyUpgrade; maaş bilgisi: Narrator.wageText(save)
// BRİFİNG: Finance.notices(save, save.day) satırları + DayConfig.quotaFor(save) + Narrator.newTypesText(save.day)
GameEngine e = new GameEngine(seed, save);
if (!e.beginPrep() && e.getState() == GameEngine.State.FAILED) { /* tahliye: faturalar ekranına dön */ }
// gün içinde (Duraklat > Faturalar): e.payBill(id), e.payWages()
```

### B.14 Denge (bot ölçümü; `V25BalanceTest`, rapor satırları "V25")
Akıllı bot (Campaign: fiyat, fatura, maaş borcu, gerekirse kredi): 0,8 ve 1,5 sn botlar ve insan benzeri 1,5 sn bot her akışta 1–60 geçer (0 başarısız deneme); süresiz 1–60 geçer; hiçbir şey yapmayan ≤5 günde; hiç fatura ödemeyen bot 6. günden kesinti yaşar ve **15. günde tahliye** ile takılır. Fiyat stratejileri: ayrıntı `design_v25.md` §10.

### B.15 UI yapılacaklar (v2.5)
1. **Epoch bildirimi**: açılışta `save.progressResetNotice` ise `Narrator.progressResetText()`'i bir kez (TalkBack + ekranda) göster, `clearProgressResetNotice()` + kaydet. Menüdeki "Devam et" 1. günden başlar.
2. **Gün sonu / Faturalar ekranı** (özetten sonra, marketten önce): yeni faturalar, açık faturalar listesi (`Finance.openBills`, `Narrator.billText`), her satırda büyük **Öde / Ertele / Krediyle öde** düğmeleri; maaş borcu satırı (**Maaşı öde**); toplam borç ve kredi notu; "son ödeme günü bugün" uyarısı. Gecikmiş/kesinti satırları önce.
3. **Banka ekranı** (market içinden): `Narrator.bankText`, tutar seçimi (−/+ 500, en az 500, `Bank.available`), vade seçimi 5/10/20 (`Bank.TERMS`), teklif `Narrator.quoteText(Bank.quote(...))`, **Kredi al**; kredi listesi `Narrator.loanText`, **Erken kapat** (`Bank.repay`), **Gecikmiş taksiti öde** (`Bank.payOverdue`). Sonuç sesleri (`Bank.Result`).
4. **Fiyat ekranı** (market içinden): bugünün menüsü, her ürün için satır (`Narrator.priceText`) + **−%5 / +%5** düğmeleri (`Pricing.lower/raise`), "Hepsi %100" (`Pricing.setAll(save, 100)`), tahmini müşteri (`Narrator.demandText`), kısa açıklama ("Ucuz: daha çok müşteri ve yıldız, az kâr. Pahalı: az müşteri, çok kâr, yıldız düşebilir."). Fiyat gün başında geçerli.
5. **Brifing**: `Finance.notices(save, save.day)` satırları (önce), kesinti özeti (`UtilityStatus.of`), yeni müşteri tipi, maaş (`Narrator.wageText`), yeni yükseltme seviyeleri (`Catalog.newUpgradeLevelsOnDay`). Tahliye durumunda "Dükkânı aç" yerine faturalar ekranına yönlendir (motor zaten günü başlatmaz).
6. **Gün içi**: Duraklat menüsüne **Faturalar** (açık faturalar + `engine.payBill`, `engine.payWages`); `onFinanceNotices`, `onUtilityBlocked` (hata sesi + titreşim), `onStockSpoiled`, `onBillPaid` (para sesi) için sesler; durum çubuğu / "Durumu oku" `narrator.statusText()` (kesinti ve açık fatura içerir).
7. **Çırak teslimi**: `onApprenticeAction(a.deliver)` başlangıcında hafif ses (isteğe bağlı), `onOrderDelivered` çırak teslimlerinde de para sesi; "Sıradaki sipariş"/eylem satırlarında DELIVER satırı `RUNNING` "Teslim ediliyor, çırak" (dokunulamaz); `deliver` false dönerse (çırak teslim ediyor) yalnızca kısa uyarı; Ayarlar'a **"Çırak teslim etsin: Açık/Kapalı"** (`save.apprenticeDelivery`, aç/kapa etiketi durumlu); `Texts.apprenticeLevelsText` (seviye 1 teslim de eder, 4 kalfa, 5 dördüncü çırak, günlük maaş).
8. **Yükseltmeler**: market listesi 16 yükseltme, 5–6 seviye ("Seviye 3 / 5"); açıklamalar `Upgrade.description`; faturayı artıran yükseltmede "Elektrik faturasını artırır" notu (`Upgrade.raises`); jeneratör ve su deposu açıklaması.
9. **Özet ekranı** (`Texts.summaryRows`): "Kira" satırı yerine **Çırak maaşı, Kredi taksiti, Haciz, Yeni faturalar, Açık fatura borcu, Kredi notu, Fiyatı pahalı bulan**; `profit()` yeni tanımla; `rentUnpaid` artık 0.
10. **Metinler**: `ui/Texts`'te terimler sözlüğüne uy: "numaralı sipariş" (Texts.java:264-266), "Sipariş hazır" (TUT_READY), "malzeme yok" (Texts.java:388), "Gün sonunda N lira kira ödenecek" (Texts.java:561; artık haftalık kira faturası: `Finance.forecast`), "TL" → "lira".
11. **Yeni ürünler**: istasyon düğmeleri listesi `engine.getMenu()`'yü kullansın (kesintide çıkan ürünler); ürün ikonları/adları (çorba, çay, şalgam, su, sütlaç, baklava, köfte, tantuni, karışık tabak); yeni ses ÜRETİLMEYECEK, var olanlar eşlenecek (ör. çay/şalgam/su = içecek sesleri, köfte/tantuni = sizzle).
12. **Kırılan UI testleri**: aşağıda (§B.16).

### B.16 Bu sürümde kırılan Robolectric UI testleri
Tam çalıştırmada (606 test) yalnızca `FixesUiTest.emergencyButtonIsFixedOutsideTheScrollingListAndTakesFocusWithTalkBack` kırıldı (duyuruda malzeme adı artık küçük harf: "Stokta yok: döner ekmeği."); karşılaştırma büyük/küçük harften bağımsız yapılarak uyarlandı. Ayrıca `MonkeyLifecycleTest.gameMonkeyTalkBackDay3Apprentice1` seyrek kararsız ("istasyon düğmesi yok PAKET", `UiBot.doStation`): motor tohumu `System.nanoTime()` olduğundan her çalıştırma farklı; v2.5'te 3 başarısızlık görüldü (hepsi paralel Gradle yükü altında), ardından 28 art arda geçti; v2.0'da 8/8 geçti. Büyük olasılıkla çırak teslimiyle değişen zamanlama: UI ajanı bu testte `GameScreen.testSeed` sabitlemeli ve PAKET düğmesinin gizlendiği durumu incelemeli. Bunun dışında kırık UI testi yok; ama UI ekranları v2.5 özelliklerini (fatura, banka, fiyat, çırak teslimi, epoch bildirimi) henüz göstermiyor ve `Texts.summaryRows` "Kira 0 lira" satırı üretir — §B.15.

## A. v0.9 -> v2.0 (kaynak modeli, çırak, sıradaki sipariş)

### A.1 Kavram: ortak "yuva" YOK
| | v0.9 | v2.0 |
|---|---|---|
| Eşzamanlılık | tüm işler için tek ortak yuva (`slots()`=1) | **Eller**: oyuncu aynı anda 1 AKTİF iş. **Makineler**: PASİF işler istasyonun kendi kapasitesinde (Ekmek ısıtıcı 2 yer, Ocak 2 göz; yükseltmeyle 4) |
| Pasif iş | – | `Step.isPassive()`: ekmek/lavaş ısıt (EKMEK), "Ocakta mühürle", "Ocakta pişir", "Patates kızart" (IZGARA); hazırlıkta `PrepKind.FRIES`. Eller yalnızca **koyma** süresince dolu: `GameEngine.PASSIVE_LOAD_SECONDS` = 0,6 sn × tempo (çırakta ×1,2, en az 0,3), sonra iş kendi süresince pişer, bitince adım biter (ürün kalemde "hazır" bekler), yer boşalır. Çırak yavaşlığı pişirmeye uygulanmaz |
| Aktif iş | – | diğer her şey (dilimle, malzeme ekle/otomatik, doldur, dolaptan al, paketle, küp doğra, pilav/tereyağı/sos gezdir, tatlı, harç; hazırlıkta döner/ayran/tepsi): iş boyunca eller dolu |
| Red | `NO_SLOT` + `onNoFreeSlot` ("Boş yuva yok") | **`HANDS_BUSY` + `onHandsBusy(order, current)`** ("Elin dolu: Döneri dilimle sürüyor, 2 saniye.") ya da **`STATION_FULL` + `onStationFull(order, station, used, cap)`** ("Ocak dolu (2/2). İlk yer 3 saniye sonra boşalır."). Pasif işte önce yer, sonra eller denetlenir. `NO_SLOT`/`onNoFreeSlot` artık HİÇ gelmez |
| Zaten sürüyor | `ORDER_BUSY` (olaysız) | `ORDER_BUSY` + **`onAlreadyRunning(order, job)`** ("Ekmeği ısıt zaten sürüyor, çırak yapıyor, 2 saniye.") |
| Çırak | seçili siparişe hiç dokunmaz | önce seçili olmayanlar (en acil), başka iş yoksa seçili sipariş (eli doluysa hemen, eli boşsa son eylemden 1,5 sn sonra), sonra hazırlık; her çırağın kendi eli; pasif işi koyup geçer (§A.5) |
| Öneri | ilk açık adım | önce yeri olan pasif adım (koy-geç), sonra eski kural; seçili kalem öncelikli |
| Kayıt | `v=4` | aynı `v=4`; yeni yükseltme `up.ocak` (eski kayıtta 0) |

### A.2 Yeni / değişen API
```java
// ---- eller ve istasyonlar
boolean  engine.isHandsFree()                      // oyuncunun eli boş mu
ActiveStep engine.getHandsJob()                    // eli tutan iş (aktif ya da koyma anındaki pasif); boşsa null
boolean  engine.isPassiveStation(Station st)       // EKMEK, IZGARA (v0.6 dengesinde hiçbiri)
int      engine.getStationCapacity(Station st)     // pasif kapasite (EKMEK/IZGARA 2..4), diğerleri 0
int      engine.getStationUsed(Station st)         // pişen iş (oyuncu + çırak)
boolean  engine.hasStationRoom(Station st)         // yer var mı (pasif değilse true)
List<ActiveStep> engine.getStationJobs(Station st) // istasyondaki tüm işler
String   engine.stationFullText(Station st)        // "Ocak dolu (2/2)" (kilit nedeni metniyle aynı)
// ESKİ (derlenir, kullanma): getFreeSlots() = isHandsFree()?1:0, getSlotCount() = 1, Upgrades.slots() = hands() = 1
// ---- ActiveStep
boolean isPassive(); boolean holdsHands(); float getHandsRemaining(); Station getStation(); int getPlace() /*pasif: 0..cap-1, aktif: -1*/
// ---- Step
boolean isPassive()
// ---- Upgrades
int hands() /*1*/; int stationCapacity(Station st); static int baseCapacity(Station st) /*EKMEK, IZGARA: 2*/
// ---- sıradaki sipariş (UI sadeleştirmesi)
Order engine.getFocusOrder()          // seçili sipariş; seçim yoksa en acil; sipariş yoksa null
Order engine.getMostUrgentOrder()     // en az kalan sabır ORANI (eşitlikte en az süre, sonra en eski); seçimden bağımsız
Order engine.getSuggestedOrder()      // "Sıradaki sipariş": teslime hazır (en acili) > şu an yapılabilir işi olan en acil > en acil
boolean engine.selectSuggestedOrder() // onu seçer (selectOrder: onOrderSelected + öneri duyurusu)
QueueSummary engine.getQueueSummary() // orders, readyToDeliver, workable, focusOrderId, mostUrgentOrderId, mostUrgentSecondsLeft (süresiz: -1), mostUrgentFraction, text
List<ReadyAction> engine.getReadyActions(int orderId)
boolean engine.apprenticeMayTouchSelected()   // çırak şu an seçili siparişe dokunabilir mi (bilgi)
// sabitler
GameEngine.PASSIVE_LOAD_SECONDS = 0.6f; GameEngine.SELECTED_GRACE_SECONDS = 1.5f
```
**`ReadyAction`** (siparişte "şimdi ne yapılabilir"; henüz açılmamış adımlar YOK): `kind` (`STEP`, `PACK`, `DELIVER`), `status` (`NOW`, `HANDS_BUSY`, `STATION_FULL`, `OUT_OF_STOCK`, `RUNNING`), `orderId`, `itemIndex` (PACK/DELIVER: -1), `station` (DELIVER: null), **`optionId`** (doluysa `applyStationOption(orderId, itemIndex, station.id, optionId)` tek dokunuş: "step", "auto", …; menü gerekiyorsa / RUNNING / DELIVER: null), `needsMenu` (boy seçimi, tek tek malzeme → istasyon menüsünü aç), `passive`, `byApprentice` (RUNNING), `seconds` (NOW: tahmini süre, RUNNING: kalan), `stepName`, **`text`**, `isDoable()`.
- Sıra: DELIVER tek başına; yoksa NOW pasif, NOW aktif, bekleyenler (HANDS_BUSY/STATION_FULL/OUT_OF_STOCK), RUNNING. Kalem başına istasyon başına en çok bir açık adım (dokunuşun yapacağı adım).
- `text` örnekleri: "Ekmeği ısıt, Ekmek ısıtıcı" · çok kalemli: "Ekmek arası döner: Patates kızart, Ocak" · "Döneri dilimle, Döner makinesi, boy seç" · "Malzemeleri ekle, Sos ve salata tezgahı, malzemeleri seç" · "…, elin dolu" · "Ekmeği ısıt, Ekmek ısıtıcı dolu (2/2)" · "…, malzeme yok" · "Ekmeği ısıt sürüyor, 2 saniye, Ekmek ısıtıcı, çırak" · "Siparişi paketle, Paketleme" · "Teslim et".

**Yeni `StartResult`**: `HANDS_BUSY`, `STATION_FULL` (+ `NO_SLOT` @Deprecated, dönmez). **Yeni `PrepResult`**: `HANDS_BUSY`, `STATION_FULL` (patates hazırlığı ocak gözü ister). Sonuç listeleri: `startStep`/`applyStationOption("step"|"size:*")`: `STARTED, NO_ORDER, ORDER_READY, NOT_RUNNING, WRONG_STATION, STEP_LOCKED, ORDER_BUSY, HANDS_BUSY, STATION_FULL, OUT_OF_STOCK, LOCKED, INVALID`; `addTopping`/`autoToppings`/`confirmToppings`: `HANDS_BUSY` (malzeme aktif iştir); paketleme: `HANDS_BUSY`; `startPrep*`/`prep:*`: `HANDS_BUSY`, `STATION_FULL` (yalnızca `prep:fries`), `FULL` (hazır stok dolu), `OUT_OF_STOCK`, `INVALID`.

**Yeni olaylar** (`GameListener` içinde `default` boş; `GameListenerAdapter` değişmedi):
```java
void onHandsBusy(Order order /*hazırlıkta null*/, ActiveStep current);
void onStationFull(Order order /*hazırlıkta null*/, Station station, int used, int capacity);
void onAlreadyRunning(Order order, ActiveStep job);      // ORDER_BUSY'nin nedeni (oyuncu ya da çırak yapıyor)
void onHandsFree(ActiveStep job);                        // oyuncunun pasif işinin koyması bitti: eller serbest (ses/titreşim için)
@Deprecated void onNoFreeSlot(Order order);              // artık GELMEZ
```
Olay sırası (pasif adım, oyuncu): `onStepStarted(order, step, slot, cookSeconds)` → (0,6 sn) `onHandsFree(job)` → (pişme) `onStepFinished` → `onItemFinished`/`onOrderReadyToPack` → `onNextStepHint`. Çırak işleri yine yalnızca `onApprenticeAction` verir. `onStepStarted.slot` artık yalnızca oyuncu işlerinin sıra numarasıdır (anahtar/pan için; anlam taşımaz).

**Seçenek menüsü kilit nedenleri** (`StationOption.lockReason`): "Bir işlem sürüyor" KALKTI → **"Elin dolu"** (aktif seçenek, hazırlık, boy, malzeme, paketle) ya da **"Ocak dolu (2/2)" / "Ekmek ısıtıcı dolu (2/2)"** (pasif adım ve `prep:fries`). Diğer nedenler aynı.

### A.3 Narrator (yeni/değişen)
- `stepStarted`: pasif işte sonuna " Elin boş." ("Ekmeği ısıt başladı, 4 saniye. Elin boş.").
- `handsBusy(order, current)`, `stationFull(order, st, used, cap)`, `alreadyRunning(order, job)` — kendi olaylarında konuşur (`CAT_ALERT`, acil). `noFreeSlot` metni "Elin dolu, işin bitmesini bekle." (olay gelmez).
- `handsText()`: "Elin boş." / "Elin dolu: Döneri dilimle, 2 saniye." + "Pişiyor: Ekmek ısıtıcı 1/2, Ocak 2/2." — `statusText()` bunu içerir (gün/hazırlıkta).
- `readyActionsText(orderId)`: "Şimdi: Ekmeği ısıt, Ekmek ısıtıcı; Döneri dilimle, Döner makinesi. Bekliyor: Patates kızart, Ocak dolu (2/2). Ayranı doldur sürüyor, 1 saniye, çırak." / "Teslime hazır."
- `focusText()`: "Sipariş 2: Ali, Ekmek arası döner ve Ayran. İstek: soğansız. Şimdi: …" / "Bekleyen sipariş yok."; `queueText()` = `getQueueSummary().text`.
- `stationLabel(st)`: pasif istasyonda doluluk: "Ekmek ısıtıcı, çalışıyor, 1/2 dolu, sıradaki: Ekmeği ısıt".
- `prepRejected`: `HANDS_BUSY` "Elin dolu, Döner dilimleme için işin bitmesini bekle.", `STATION_FULL` "Ocak dolu (2/2). …"; `startResultText`: `HANDS_BUSY` "Elin dolu.", `STATION_FULL` "İstasyon dolu.".
- Çırak duyuruları: sipariş işi başlangıçları ve paketleme bitişi HER ZAMAN söylenir ("Çırak 2 numaralı siparişte: Ayranı doldur.", "Çırak 2 numaralı siparişi paketledi."); yalnızca hazırlık duyuruları 4 sn kısıtlı; ayar `apprenticeAnnounce=false` hepsini susturur.

### A.4 Yükseltmeler / ekonomi
| id | Ad | Etki (seviye 1/2/3) | Fiyat | Gün |
|---|---|---|---|---|
| `ekmek_isitici` | Ekmek ısıtıcı | kapasite +1/+2/+2 (3/4/4 yer), hız ×0,85/0,70/0,55 | 2 500 / 8 000 / 22 000 | 3/9/17 |
| **`ocak`** (yeni) | Geniş ocak | göz +1/+2/+2 (3/4/4), hız ×0,90/0,80/0,65 (ocak işleri) | 2 500 / 8 000 / 20 000 | 3/9/17 |
| `cirak` | Çırak | seviye 1/2/3 (§A.5); oyuncunun elini artırmaz, kendi eli var | 3 000 / 9 000 / 24 000 | 3/8/16 |
Diğerleri aynı. `Upgrade.EffectType.STATION_CAPACITY` (yeni), `SLOTS` @Deprecated (etkisiz). Sabır: pasif iş süresi sabır tahmininde 0,25 oranında sayılır; Gerçekçi `FlowMode.patienceMult` 1,15 → 1,05. Ölçüm sonuçları: `design_v20.md` §5.

### A.5 Çırak kuralları (öngörülebilir)
- Seviye 1: Siparişi paketle, Ayranı doldur, Dolaptan al; boşta: ayran doldurma, döner dilimleme (hazır stok). Seviye 2: + EKMEK istasyonu (ısıt, küp doğra); boşta + tepsi. Seviye 3: + DONER (istenen boyda), IZGARA/Ocak (patates dahil), TATLI, lahmacun harcı. Malzeme adımı hiçbir zaman.
- Her tick, eli boş her çırak iş varsa alır: 1) seçili olmayan siparişler en acilden; 2) seçili sipariş (`apprenticeMayTouchSelected()`: oyuncunun eli dolu ya da son oyuncu eyleminden ≥1,5 sn); beklerken hazırlığa başlamaz; 3) hazırlık. Sipariş içinde: pasif işler → aktif işler → paketleme. Stok/yer/seviye uymayan iş atlanır (olaysız).
- Pasif işi koyunca (0,72 sn) başka işe geçer; pişme oyuncununkiyle aynı süredir. Çırak işleri istasyon yerlerini oyuncuyla paylaşır (dolu ısıtıcı oyuncuya da "dolu"dur). Oyuncu çırağın sürdürdüğü işe dokunursa `ORDER_BUSY` + `onAlreadyRunning` ("… zaten sürüyor, çırak yapıyor …").

### A.6 Örnek akış (patatesli ekmek arası + ayran, Dengeli, çırak 1)
```java
Order o = engine.getSuggestedOrder();  engine.selectOrder(o.id);          // "Önerilen: Ekmek arası döner: Ekmeği ısıt. Ekmek ısıtıcı."
for (ReadyAction a : engine.getReadyActions(o.id)) { /* kartta/sesli: a.text; a.isDoable() */ }
// "Ekmeği ısıt, Ekmek ısıtıcı" | "Patates kızart, Ocak" | "Döneri dilimle, Döner makinesi" | "Ayranı doldur, İçecek dolabı"
ReadyAction a = engine.getReadyActions(o.id).get(0);
engine.applyStationOption(o.id, a.itemIndex, a.station.id, a.optionId);   // STARTED: "Ekmeği ısıt başladı, 4 saniye. Elin boş."
engine.startStep(o.id, 0, Station.IZGARA.id);   // 0,6 sn dolmadan: HANDS_BUSY ("Elin dolu: Ekmeği ısıt sürüyor, 1 saniye.")
// tick 0,6 sn -> onHandsFree
engine.startStep(o.id, 0, Station.IZGARA.id);   // STARTED (patates ocakta, ekmek ısıtıcıda; eller boş)
engine.startStep(o.id, 0, Station.DONER.id);    // STARTED (aktif: eller 3,5 sn dolu); bu arada çırak eli dolu oyuncunun ayranını doldurur
// üçüncü bir ekmek koymaya çalışılırsa ve 2 yer doluysa: STATION_FULL ("Ekmek ısıtıcı dolu (2/2). …")
// hepsi bitince: SOS "auto" (aktif), sonra paketleme (oyuncu ya da 1,5 sn sonra çırak), engine.deliver(o.id)
```

### A.7 UI yapılacaklar (v2.0)
1. **"Boş yuva" göstergesini kaldır** (`GameScreen` `slotsTv`, `Texts.slotsText`, yardım metni "Boş yuva yoksa…", `Texts` kısa yardım): yerine "Elin boş / Elin dolu: X" (`narrator.handsText()` ya da `isHandsFree()/getHandsJob()`) ve pasif istasyon doluluğu ("Ekmek ısıtıcı 1/2", "Ocak 2/2": `getStationUsed/getStationCapacity`).
2. **Yeni olaylar**: `onHandsBusy` ve `onStationFull` için hata sesi + titreşim (eski `onNoFreeSlot` işleyicisi artık hiç çağrılmaz; aynı işi bu ikisine taşı), `onAlreadyRunning` için kısa uyarı sesi, `onHandsFree` için hafif "serbest" ipucu (isteğe bağlı tık/titreşim). Narrator metinleri kendisi konuşur.
3. **İstasyon düğmesi etiketi/görseli**: pasif istasyonda doluluk ("1/2 dolu", `narrator.stationLabel` hazır); çalışan işler satırında pasif işler "ısıtıcıda / ocakta" ve yer numarası (`ActiveStep.isPassive()/getPlace()`); ses döngüsü pasif işte iş boyunca (oyuncu başka işe geçse de) sürmeli — aynı anda birden çok döngü olabilir (`playerKey(o, step)` zaten iş başına).
4. **Sıradaki sipariş**: tek büyük "Sıradaki sipariş" düğmesi `engine.selectSuggestedOrder()` + `narrator.focusText()`; otomatik seçim (`updateAuto`) `getSuggestedOrder()` kullansın; durum çubuğuna `getQueueSummary().text`.
5. **Sipariş kartı sadeleştirme**: kalem/adım listesini elle kurmak yerine `getReadyActions(orderId)`: NOW satırları büyük düğme (dokununca `optionId` doluysa `applyStationOption`, `needsMenu` ise o istasyonun menüsünü aç), bekleyen/süren satırlar bilgi; DELIVER "Teslim et". "Siparişi oku" = `narrator.readyActionsText(id)`.
6. **Seçenek menüsü** kilit nedenleri değişti ("Elin dolu", "Ocak dolu (2/2)"); menü/eylem listesi eller boşaldığında ve istasyon yeri açıldığında (`onHandsFree`, `onStepFinished`) yeniden kurulmalı.
7. **Market**: yeni yükseltme `ocak` ("Geniş ocak") listede görünmeli (genel liste ise otomatik), açıklamalar güncellendi (`Upgrade.description`); `Texts.apprenticeLevelsText` çırak metni design_v20 §3 tablosuna göre (seviye 1 seçili siparişe de yardım eder).
8. **Öğretici/yardım**: "makineye koy, başka işe geç" (ekmek ısıtıcı/ocak), "Elin dolu" ve "Ocak dolu" farkı, çırağın kuralı; "yuva" kelimesi her yerden kalkmalı.
9. **Testler**: Robolectric UI testlerinden bu sürümde kırılan yok (533/533 geçti), ama `A11yAudit`/UI testleri "Boş yuva" göstergesine dayanıyorsa yeni göstergeyle güncellenmeli; `UiBot` `getFreeSlots()` ile çalışmaya devam eder (eller boşsa 1).

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
- **`auto` işi**: zamanlı AKTİF iş (oyuncunun eli; v2.0), süre = eklenecek her malzemenin `addTopping` süresi toplamı (tepsiden yarısı, hız/tempo dahil, en az 0,25 sn). Tüketim başta (önce tepsi, sonra stok); bir malzeme bile yoksa hiçbir şey tüketilmez → `OUT_OF_STOCK` + `onOutOfStock(order, ilkEksik)`; `missingIngredients/emergencyCostForMissing/emergencyBuyMissing` bu eksikleri verir/alır. Sonuçlar: `STARTED, OUT_OF_STOCK, HANDS_BUSY, STEP_LOCKED, ORDER_BUSY, INVALID, NO_ORDER, ORDER_READY, NOT_RUNNING` (v2.0: `NO_SLOT` yerine `HANDS_BUSY`; otomatik malzeme aktif iştir).
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
Gün bitince motor `save.day`'i artırır; v2.5: günlük kira yerine maaş/taksit düşer ve faturalar kesilir (§B.3). Gün BAŞARISIZ olursa para/stok/hazır içecek gün başına döner (hazırlık harcamaları dahil), yıldız en az 1.5 olur, `save.day` aynı kalır.

## 2. Durum makinesi
`READY → (PREP) → RUNNING → FINISHED | FAILED` (v0.5 ile aynı)
- `READY`: yeni motor. `tick()` veya sipariş eylemi günü kendiliğinden başlatır (`onDayStarted` bir kez); `startPrep*`/`applyStationOption("prep:...")` READY'yi PREP'e geçirir.
- `PREP`: müşteri gelmez, saat akmaz. Yalnızca hazırlık (oyuncu + çırak). Sipariş yok. `getStationOptions(-1,…)` yalnızca hazırlık seçeneklerini verir.
- `RUNNING`: normal gün; hazırlık da yapılabilir (v2.0: eller boşsa; patates hazırlığı ocakta yer ister).
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
| `StartResult` | STARTED, NO_ORDER, WRONG_STATION, ~~NO_SLOT~~ (v2.0: dönmez), OUT_OF_STOCK, ORDER_BUSY, ORDER_READY, NOT_RUNNING, NEEDS_TOPPINGS, STEP_LOCKED, INVALID, **LOCKED, MAX_REACHED, NOTHING_TO_UNDO, FULL, INFO, UNKNOWN_OPTION**, v2.0: **HANDS_BUSY, STATION_FULL** |
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
- Önkoşul: kalemin diğer adımları (ısıt, dilimle...) bitmiş (`STEP_LOCKED` + `onStepLocked` aksi halde). Ekleme **kısa bir iştir**: salata (SALATA kategorisi) 0,6 sn, sos (SOS) 0,5 sn; hazır tepsiden eklenirse yarısı (0,3 / 0,25); istasyon hızı ve tempo uygulanır (en az 0,25 sn). AKTİF iştir (v2.0: oyuncunun eli; v0.9'da "oyuncu yuvası"); ekleme sürerken kalem meşguldür.
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
- Kilitli seçenekler listede kalır (`enabled=false`, `lockReason`): "2. günde açılır", "Çalışıyor", "Önce kalemin diğer adımları bitmeli", "Önce tüm kalemler hazır olmalı", "Paketleme sürüyor", "Ekleme sürüyor", v2.0: "Elin dolu", "Ocak dolu (2/2)", "Ekmek ısıtıcı dolu (2/2)" (eski "Bir işlem sürüyor" kalktı), "En fazla bol, iki kez eklenir", "Henüz malzeme eklenmedi", "Hazır stok dolu", "Malzeme yetmiyor: …", "Yeterli malzeme yok". Kilitli seçeneği yine de uygulamak durumu değiştirmez ve nedeni Narrator ile duyurur.
- **Stok yetersizliği seçeneği KİLİTLEMEZ**: `enabled=true, outOfStock=true` (açıklama "… stokta yok" / "… malzeme yok"); uygulanınca `OUT_OF_STOCK` + olay; UI acil alımı önerir (`emergencyBuy(op.ingredientId)` veya `emergencyBuyMissing(orderId)`). Hazırlık seçenekleri stok yoksa kilitlenir (acil alım yok).
- Birden çok kalemli siparişte ad/açıklama kalem adı önekiyle gelir ("Ekmek arası döner: Ekmeği ısıt").

| İstasyon | Seçenekler (id) | Not |
|---|---|---|
| DONER | `size:yarim`, `size:tam`, `size:duble` (dilimleme adımı açıkken), `prep:doner:<et>:4`, `prep:doner:<et>:2` | açıklama: "Duble, 2 porsiyon, 5 saniye" (+ ", hazır dilimden", ", malzeme yok") |
| EKMEK, IZGARA, TATLI, ICECEK | `step` (istasyondaki ilk açık adım, ad = adım adı); ICECEK + `prep:drink` ("4 bardak ayran doldur", ayran menüdeyse) | `step` = `startStep` ile aynı |
| SOS | `add:<malzeme>` (açık havuz sırasında), `undo`, `finish`, `read:added`, `read:target`, `prep:tray`; lahmacun harcı için `step` | ikinci eklemede ad "Domates bol ekle"; açıklama "Domates bol ekle, 1 kez eklendi, stokta 4"; tepsi: "…hazır tepside 6" |
| PAKET | `step` ("Siparişi paketle") | tek eylem: UI doğrudan uygular |

`applyStationOption` sonuçları: uygulandıysa `STARTED` (zamanlı iş başladı veya anında eylem tamam), `INFO` (read:*; `onStationInfo`), aksi halde neden: `NO_ORDER, ORDER_READY, NOT_RUNNING, WRONG_STATION` (−0,02 yıldız, istasyonda o kalemde iş yok), `STEP_LOCKED, ORDER_BUSY` (+ `onAlreadyRunning`), `HANDS_BUSY, STATION_FULL` (v2.0; eski `NO_SLOT` dönmez), `OUT_OF_STOCK, LOCKED` (boy kilitli), `MAX_REACHED, NOTHING_TO_UNDO, FULL` (hazır stok dolu), `INVALID` (havuz dışı), `UNKNOWN_OPTION`. İlgili red olayı her zaman verilir (Narrator duyurur): `onToppingsRejected`, `onOptionRejected`, `onOutOfStock`, `onStepLocked`, `onHandsBusy`, `onStationFull`, `onWrongStation`, `onPrepRejected` (v2.0: `onNoFreeSlot` gelmez).

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
- **v2.0:** pasif işler (ısıtıcı/ocak) paralel; sabır tahmininde pasif süre ×0,25, Gerçekçi sabır ×1,05; sonuçlar `docs/design_v20.md` §5 (yükseltmeli/insan benzeri botlar her akışta 1–40, yükseltmesiz bot Rahat/Dengeli ~28–30, Gerçekçi ~35–36. günde takılır, hiçbir şey yapmayan ≤5 gün). `TestBot`/`Campaign` değişmedi (eski `getFreeSlots()` = eller boş).
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


## D. v2.6 -> v2.7 (motor tarafındaki TEK değişiklikler; ayrıntı `docs/design_v25.md` §14)

```java
enum Importance { CRITICAL, NORMAL, VERBOSE }             // duyuru önem sınıfı (yeni)
enum NarrationLevel { LOW("Az"), MEDIUM("Orta"), HIGH("Çok") }  // ayar; allows(Importance), effective(setting, screenReaderOn), fromId, DEFAULT = LOW
SaveData.narrationLevel                                    // kayıt anahtarı narration=0|1|2 (yoksa/bozuksa LOW); ayar: yeni oyunda korunur (copySettingsTo)
Narrator.Output.say(text, urgent, category, fresh, Importance)  // varsayılan yöntem: eski Output'lar bozulmaz (dört bağımsız değişkenliye düşer)
Narrator.defaultImportance(category, urgent)               // önemi verilmeyen çağrılar için kategori tablosu
Pricing.clamp(pct)                                          // yalnızca 50..150 (5'in katı kısıtı kalktı); STEP_PERCENT yalnızca −/+ basamağı
```
Kayıt biçimi/dönem değişmedi (`v=5`, `epoch=25`); eski kayıtlar olduğu gibi yüklenir (`price.<ürün>` 5'in katı olmayan değeri artık korunur). `GameListener` değişmedi; `Narrator` her olayı bir önem sınıfıyla söyler.
