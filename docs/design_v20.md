# v2.0 – Kaynak modeli (eller + makineler), güvenilir çırak, sıradaki sipariş

Kaynak: görme engelli oyuncunun geri bildirimi: *"Ekmek ısıtıyorum mesela, ocağı kullandırmıyor, boş yuva yok diyor. Bir müşterinin patatesini kızartırken birinin ekmeğini ısıtıyorum, sonra bir daha ekmek ısıtıyorum. Çırak birinci seviye bazen çalışıyor bazen çalışmıyor."* v0.9 ve öncesi belgeler geçerlidir; çakışmada bu belge kazanır. API ayrıntısı: `docs/engine_api.md` §A.

## 1. Kök neden
v0.9'da tüm istasyonlar TEK ortak "işlem yuvası" kullanıyordu ve yuvayı artıran hiçbir yükseltme yoktu (`Upgrades.slots()` hep 1). Ekmek ısıtıcıya ekmek koymak, ocakta patates kızartmak, döner dilimlemek aynı yuvayı tutuyordu: patates kızarırken ekmek ısıtılamıyordu ("boş yuva yok"). Gerçek dükkânda ocak, ekmek ısıtıcı ve döner bıçağı ayrı kaynaklardır; makineye koyup başka işe geçilir.

## 2. Karar: iki tür iş
| Tür | İşler | Ne tutar |
|---|---|---|
| **AKTİF** (eller gerekir) | döner dilimle, harç+döner ekle, malzeme ekle (tek tek / otomatik), ayran doldur, dolaptan al, paketle, ekmeği küp doğra, pilavı tabağa al, tereyağı/sos gezdir, tatlı tezgâhı işleri, harcı lavaşa yay; hazırlıkta döner dilimleme, ayran doldurma, tepsi | iş boyunca işi yapanın ELLERİNİ |
| **PASİF** (makine kendi işler) | ekmek/lavaş ısıt (Ekmek ısıtıcı), ocakta mühürle, ocakta pişir (lahmacun, künefe), patates kızart (Ocak); hazırlıkta 4 porsiyon patates | yalnızca **koyma** süresince (0,6 sn × tempo; çırakta ×1,2) elleri, iş boyunca istasyonun **bir yerini** |

- **Kapasite:** Ekmek ısıtıcı 2 yer, Ocak 2 göz (temel). Yükseltme: "Ekmek ısıtıcı" 3 → 4 yer (+hız), yeni **"Geniş ocak"** 3 → 4 göz (+hız). Çırağın pasif işi de aynı yeri kullanır (fiziksel kaynak ortaktır).
- **Eller:** oyuncu aynı anda 1 aktif iş (ya da 1 koyma). **Çırak = ek el**: her çırağın kendi eli vardır, oyuncunun elini artırmaz.
- Pasif iş bitince adım biter, ürün kalemin üstünde "hazır" bekler; yer boşalır. Oyuncunun "al" demesi gerekmez (fazladan dokunuş olurdu).
- **Mesajlar ayrı ve net:** "Elin dolu: Döneri dilimle sürüyor, 2 saniye." (`HANDS_BUSY`) / "Ocak dolu (2/2). İlk yer 3 saniye sonra boşalır." (`STATION_FULL`) / "Ekmeği ısıt zaten sürüyor, çırak yapıyor, 2 saniye." (`ORDER_BUSY`). Pasif iş başlayınca: "Ekmeği ısıt başladı, 4 saniye. Elin boş." "Yuva" kelimesi oyundan kalktı.
- **Öneri (Önerilen: …) koy-geç önce:** yeri olan pasif istasyonda açık adım varsa önce o önerilir (önce ekmeği ısıtıcıya koy / patatesi ocağa at, sonra dilimle). Oyuncu kalemi kendisi seçtiyse o kalem önceliklidir.
- **Soğuma/yanma YOK (bilinçli):** ekmeğin 40 sn'de soğuması ya da patatesin yanması, görme engelli oyuncunun kafasında ayrı ayrı zamanlayıcılar tutmasını ister; ekranda göremediği için ancak ek duyurularla takip edebilir (daha çok konuşma, daha çok dokunuş). Siparişi geciktirmenin bedeli zaten müşteri sabrıdır. Bu sürümün hedefi sadeleşmedir; v2.x'te istenirse tek sabitle eklenebilir.

## 3. Çırak: kural tablosu ve kök neden
| Seviye | Sipariş işleri | Boşta hazırlık |
|---|---|---|
| 1 | Siparişi paketle, Ayranı doldur, Dolaptan al | ayran doldur, döner dilimle (hazır stok) |
| 2 | + Ekmek istasyonu (ekmek/lavaş ısıt, küp doğra) | + salata/sos tepsisi |
| 3 | + Döner makinesi (istenen boyda, hatasız), Ocak (pişir, mühürle, patates, pilav, tereyağı), Tatlı, lahmacun harcı | aynı |
Malzeme (topping) adımı hiçbir seviyede çırağa verilmez (Gerçekçi'de seviye 2'nin "Standart malzemelerle bitir" kısayolu aynen durur).

**İş seçimi (her tick, eli boş her çırak, iş varsa MUTLAKA alır):** 1) seçili OLMAYAN siparişler, en acilden (en az kalan sabır oranı); 2) başka iş yoksa **seçili sipariş** — oyuncunun eli doluysa hemen, eli boşsa oyuncunun son eyleminden 1,5 sn sonra (`SELECTED_GRACE_SECONDS`; oyuncunun tam dokunmak üzere olduğu işi kapmasın). Bu bekleme sırasında uzun bir hazırlığa başlamaz; 3) hiç sipariş işi yoksa hazırlık. Sipariş içinde önce pasif işler (koy-geç), sonra aktif işler, sonra paketleme. Stok, istasyon yeri ya da seviye uymayan iş sessizce atlanır.

**"Bazen çalışıyor bazen çalışmıyor" kök nedeni:** (a) çırak seçili siparişe HİÇ dokunmuyordu; arayüz seçim yoksa 1,5 sn içinde bir siparişi kendiliğinden seçtiği için 1–2 siparişli günlerde çırağın yapabileceği iş çoğunlukla seçili siparişteydi → ancak ikinci bir sipariş varken çalışıyordu; (b) gün içi hazırlık koşulları (ayran ≤4, döner ≤3 porsiyon, kalan süre >25 sn) dolunca boş kalıyordu; (c) çırağın sipariş işi duyuruları 4 sn kısıtıyla düşüyordu (yaptığı iş duyulmuyordu). Düzeltme: yukarıdaki kural; sipariş işi başlangıçları ve paketleme bitişi her zaman duyurulur (konuşma sırası aynı kategoriyi birleştirir), yalnızca hazırlık duyuruları 4 sn kısıtlıdır.

## 4. Sıradaki sipariş (UI sadeleştirmesi için motorda)
- `getFocusOrder()`: seçili sipariş; seçim yoksa en acil (`getMostUrgentOrder()`: en az kalan sabır oranı, eşitlikte en az süre, sonra en eski; süresizde en eski).
- `getSuggestedOrder()` / `selectSuggestedOrder()`: "Sıradaki sipariş" düğmesi: teslime hazır olan, yoksa şu an yapılabilir işi olan en acil sipariş.
- `getQueueSummary()`: sipariş sayısı, teslime hazır, yapılabilir, en acilin kalan süresi ve oranı, kısa metin ("3 sipariş, 1 teslime hazır. En acil: sipariş 2, Ali, 12 saniye.").
- `getReadyActions(orderId)`: siparişte şimdi yapılabilecekler (kalem bazlı, kısa metin, tek dokunuş `optionId` ya da `needsMenu`), kaynak yüzünden bekleyenler (elin dolu / "Ocak dolu (2/2)" / malzeme yok) ve sürenler; teslime hazırsa yalnızca "Teslim et". UI tüm kalemleri ve adımları listelemek zorunda kalmaz. Narrator: `readyActionsText`, `focusText`, `queueText`, `handsText`.

## 5. Ekonomi ve denge
- Yükseltmeler: "Ekmek ısıtıcı" artık önce kapasite (+1/+2/+2 yer) ve hız ×0,85/0,70/0,55 (eskiden yalnızca hız 0,70/0,50/0,40; pasif işte hız daha az önemli). Yeni "Geniş ocak" (`ocak`, 3/9/17. gün, 2 500 / 8 000 / 20 000 TL): +1/+2/+2 göz, hız ×0,90/0,80/0,65. Çırak fiyatı aynı (3 000 / 9 000 / 24 000; seviye 1 artık her gün işe yaradığı için daha değerli).
- Pasif işler paralel yürüdüğü için oyun belirgin kolaylaştı. Denge: müşteri sabrı tahmininde pasif işin süresi **0,25** oranında sayılır (`Order.PASSIVE_WORK_WEIGHT`; oyuncu o sırada başka işe geçebilir); Gerçekçi sabır çarpanı 1,15 → **1,05** (v0.7 yumuşatması el yükü içindi, pasif işlerle el yükü azaldı). Bahşiş ve geliş çarpanları aynı.
- Patatesli sipariş rastgeleliği düzeltildi: ardışık tohumların ilk sayıları ilişkiliydi (3. günde %12 yerine ~%50 ölçülüyordu); tohum SplitMix64 ile karıştırılır.
- Ölçülen (BAL satırları, `FlowBalanceTest`/`SimulationTest`): hazırlıklı + yükseltmeli bot (0,8 ve 1,5 sn) her akışta 1–40 geçer, 0 başarısız deneme. İnsan benzeri 1,5 sn bot: Gerçekçi 4/4 tohumda 1–40 (0 başarısız deneme, 0,10–0,18 kayıp müşteri/gün), Rahat ve Dengeli 2/2 tohumda geçer (Dengeli 0,20–0,33 kayıp/gün). v0.6 tabanı (eski tek yuva) aynı botla gün 21–25'te takılır. Yükseltmesiz bot takılır: Rahat 28–30, Dengeli 28, Gerçekçi 35–36. gün (ilerleme yükseltme ister). Hiçbir şey yapmayan bot ≤5 günde başarısız. Hazırlık yapan bot yapmayandan kazançlı (~%4). Sipariş başına dokunuş (insan benzeri, 40 gün): Rahat 4,9, Dengeli 6,7, Gerçekçi 9,0. Süresiz mod testleri aynen geçer.

## 6. Uyum
- Kayıt biçimi değişmedi (`v=4`); yeni yükseltme `up.ocak` (eski kayıtta 0). v0.9 kayıtları yüklenir.
- `GameEngine(seed, save, true)` (v0.6 ölçüm dengesi) eski tek yuva modelini korur: her iş aktiftir, pasif kapasite 0.
- Eskiyen (derleme uyumu için duran) API: `StartResult.NO_SLOT`, `PrepResult.NO_SLOT`, `onNoFreeSlot` (motor artık göndermez), `getFreeSlots()` (eller boşsa 1), `getSlotCount()` (1), `Upgrades.slots()`, `Upgrade.EffectType.SLOTS`.

## 7. Testler
`ResourceModelTest` (oyuncunun senaryosu: patates kızarırken iki ekmek; "Ekmek ısıtıcı dolu (2/2)", "Ocak dolu (2/2)" + geniş ocak; eller doluyken ikinci aktif işin reddi ve ayrı duyuru; koyma süresi ve yerler; öneri koy-geç önce; v0.6 dengesi; yükseltme/ekonomi; v0.9 kaydı; focus/queue/readyActions), `ApprenticeV20Test` (tek seçili siparişe 5/5 tohumda yardım, seviye kuralları, her tick'te boşta çırak varken alınabilir iş kalmaması, istasyon kapasitesi, tutarlı duyurular), `ApprenticeTest` yeni seçili sipariş kuralına bilinçli uyarlandı (önce seçili olmayan, bekleme süresi). Eski testlerdeki `NO_SLOT`/"Bir işlem sürüyor" beklentileri `HANDS_BUSY`/"Elin dolu"ya çevrildi; silinen ya da gevşetilen test yok.
