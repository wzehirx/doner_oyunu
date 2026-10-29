# v0.6 – TalkBack Eylemleri, Tek Tek Malzeme, Porsiyon Boyu, Gün Gün Soslar

Kaynak: oyuncu isteği. v0.5'in üstüne eklenir; design_v05.md ve engine_api.md geçerlidir, çakışmada bu belge kazanır.

## 1. İstasyon eylem menüleri (iki erişim yolu, aynı seçenekler)
Her istasyon düğmesinin (özellikle DÖNER, EKMEK, SOS, İÇECEK, PAKET, hazırlık düğmeleri) bir **eylem menüsü** vardır: istasyonda o an yapılabilecek seçeneklerin listesi.
- **TalkBack Eylemleri (custom actions):** Düğmeye `AccessibilityDelegate` ile her seçenek için bir `AccessibilityAction` (Türkçe etiket) eklenir; kullanıcı TalkBack "Eylemler" menüsünden (yukarı-aşağı kaydırma / yerel eylem menüsü) seçeneği seçince seçenek doğrudan uygulanır. Etiketler durumu içerir ("Domates ekle", "Duble boy", "Ketçap ekle, stokta 5").
- **Eylem desteği olmayan durum** (TalkBack kapalı veya `AccessibilityManager` eylem menüsünü sunmuyor / kullanıcı normal dokunuyor): düğmeye normal tek dokunuşta aynı seçenekleri gösteren **seçenek paneli** açılır (büyük düğmeler listesi, her biri bir seçenek, tek dokunma ile seçilir, "Kapat" düğmesi, odak başlıkta). Karar kuralı: TalkBack AÇIKSA düğmenin varsayılan çift dokunuşu da paneli açar (eylem menüsü ek yoldur, panel her zaman güvence); TalkBack KAPALIYSA tek dokunuş paneli açar. İki yol aynı motor çağrısını yapar.
- İstasyonda yalnızca bir olası eylem varsa (ör. Paketle) doğrudan uygulanır, menü açılmaz.
- Eylem/panel seçenekleri o anki seçili sipariş + kaleme göre dinamik hesaplanır (motor `getStationOptions(orderId, itemIndex, stationId)` → liste: id, Türkçe ad, açıklama, aktif mi, kilit nedeni).

## 2. Tek tek malzeme ekleme (SOS/SALATA istasyonu)
- v0.5'teki "aç/kapa + Tamam" toplu panel KALKAR. Yerine: SOS istasyonu eylem menüsü her malzeme için ayrı eylem sunar ("Marul ekle", "Domates ekle", "Soğan ekle", "Ketçap ekle"...). Seçilen malzeme **tek başına** eklenir: kısa süre (salata 0,6 sn, sos 0,5 sn), malzeme tüketir, sesi çalınır ("Domates eklendi").
- Aynı malzeme tekrar seçilirse "bol" (2. kez) olur (toplam en çok 2); fazlası reddedilir. "Son ekleneni geri al" eylemi (malzemeyi stoğa iade eder, yalnızca siparişte tamamlanmamışken) ve "Eklenenleri oku" eylemi bulunur.
- Kalem hazır olma: oyuncu **"Malzemeleri bitir"** eylemiyle toppingi onaylar (eski Tamam); doğrulama v0.5'teki gibi hedef küme ile karşılaştırır (eksik/fazla malzeme cezaları aynı). "Hedefi oku" eylemi mevcut.
- Hazır salata/sos tepsisi varsa ilgili malzemelerin eklenme süresi yarıya iner.

## 3. Soslar gün gün açılır
Sos/salata malzemesi `openDay` kazanır (Ingredient'e `unlockDay`). Müşteri istekleri ve ürün varsayılan kümeleri yalnızca açık malzemeleri kullanır; seçenek listesi yalnızca açık malzemeleri sunar; yeni malzeme açılan gün brifingte duyurulur ("Yeni: nar ekşisi").
Önerilen takvim: 1: domates, marul, soğan; 3: ketçap, mayonez; 5: acı sos; 7: turşu; 9: nar ekşisi; 11: sumak/biber; 13: sarımsaklı yoğurt sos; 15: közlenmiş biber/domates. (Mevcut Catalog malzemeleriyle eşleştir, eksikleri ekle; açılış günlerinden önce ürünlerin varsayılan kümelerinde yer almaz.) Market yalnızca açık malzemeleri satar; öneri listesi buna uyar.

## 4. Döner porsiyon boyu (dilimleme)
- Döner içeren ürünlerde (ekmek arası döner, pilav üstü, dürümler...) sipariş kalemi bir **boy** taşır: **Yarım ekmek** (0,5 porsiyon, ürün fiyatının %60'ı), **Tam ekmek** (1 porsiyon, %100), **Duble** (2 porsiyon, %170). 1. günde yalnızca Tam; 2. günden itibaren Yarım; 4. günden itibaren Duble istenebilir (müşteri tipine göre olasılık; sipariş kartında/duyuruda "yarım ekmek döner").
- DONER istasyonu eylem menüsü, kalemin boyunu bilmeden de çalışır: oyuncu dilimleme boyunu SEÇER (Yarım / Tam / Duble). Seçilen boy ≠ istenen boy ise "hatalı sipariş" (boy hatası, v0.5 hata mekaniği: yıldız −0,05, bahşiş yarıya; çok az/çok fazla bir basamak fark). Doğru boy: doğru. Dilimleme süresi ve döner/ekmek tüketimi boya göre ölçeklenir: yarım 0,6x süre ve 0,5 porsiyon et; tam 1x; duble 1,5x süre ve 2 porsiyon et (malzeme miktarları motorda ürün başına boyla çarpılır, `ceil` yok: kesirli birim yerine porsiyon sayacı kullan).
- Hazır dilimlenmiş döner (hazırlık) porsiyon cinsinden tutulur: yarım ekmek 1 porsiyonun yarısını kullanır (iç sayım yarım porsiyon birimi = 0,5 olarak, tamsayı "yarım porsiyon" sayacıyla). Hazırlık eylem menüsü: "4 porsiyon dilimle", "2 porsiyon dilimle".
- Ekmek ısıtma: boya göre aynı (yarım ekmek = daha kısa süre). Fiyat ve ekonomi yeniden dengelenir.

## 5. Erişilebilirlik / Test
- Tüm eylemler Türkçe, her eylemin sonucu Narrator ile duyurulur (kısa). TalkBack kapalıyken panel; açıkken hem eylemler hem panel. `A11yAudit`: istasyon düğmelerinin `AccessibilityNodeInfo.getActionList()` içinde beklenen özel eylemler var mı, etiketler Türkçe/boş değil mi, eylem çalıştırınca (`performAccessibilityAction`) panel olmadan uygulanıyor mu, panel yolu aynı sonucu veriyor mu testleri.
- Motor testleri: tek tek ekleme, bol/azami, geri al, hedef doğrulama, boy hatası, porsiyon sayacı, sos açılış günleri, kayıt uyumluluğu (v0.5 kaydı yüklenir).
- Botlar (TestBot/Campaign) yeni API ile; gün 1–40 geçmeli, hiçbir şey yapmayan bot ≤5 günde başarısız olmalı, öğretici güncellenir (boy seçimi ve tek tek malzeme).
