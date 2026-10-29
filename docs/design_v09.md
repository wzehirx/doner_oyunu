# v0.9 – Patates, Ocak, ambiyans, konuşma sırası, kısa etiketler

Kaynak: görme engelli oyuncunun geri bildirimi. v0.8 ve öncesi geçerlidir; çakışmada bu belge kazanır.

## 1. Domates/marul sesi duyulmuyordu (kök neden)
Varsayılan Dengeli (ve Rahat) akışta malzemeler "auto" ile otomatik eklenir. `onAutoToppingsStarted` yalnızca TEK bir sos döngüsü (`sfx_sauce`) çalıyordu; domates/marul sesleri (`sfx_veg_*`) yalnızca tek tek ekleme yolunda (`onToppingAddStarted`, Gerçekçi) tetikleniyordu. Dosyalar ve SFX listesi doğruydu. Çözüm: `ToppingSfx.plan` her malzeme için bir vuruş üretir (domates=`sfx_veg_tomato`, marul/soğan/diğer salata=`sfx_veg_lettuce`, sos=`sfx_sauce`), ekleme süresine malzeme paylarıyla (salata 0,6, sos 0,5) yayılır; `SoundManager.startSequence` çalar, duraklatmada durur.

## 2. Patates ve Ocak
- İstasyonun görünen adı **Ocak** (`Station.IZGARA`); ızgara/fırın adımları "Ocakta mühürle/pişir".
- `patates` (4 TL, 3. günden açık). "Patates kızart": Ocak, 4 sn. **Karar:** ürün varsayılanı değil, müşteri isteği ("patatesli"): her müşteri için ek iş olmasın, seçim oyuncuya hissettirilsin. Ürünler: ekmek arası döner, tavuk döner ekmek arası, tavuk dürüm, Hatay usulü dürüm, zurna dürüm. Olasılık 3. günde %12, +1,2 puan/gün, tavan %50. Kalem fiyatı +40 TL (hammadde 4 TL, 4 sn ek iş: kâr var ama zamanı çalar). Sabır tahmini ek adımı otomatik sayar.
- Hazırlıkta "4 porsiyon patates kızart" (`prep:fries`, Ocak menüsü/hazırlık düğmesi): hazırsa adım 0,8 sn; gün sonu atılır (israf). Ses `sfx_fry` döngüsü (adım ve hazırlık).
- Akışlar: Rahat/Dengeli/Gerçekçi'de aynı tek dokunuşlu adım (ekmek ısıtma gibi); otomasyon yalnızca malzeme/boy için vardı. Çırak seviye 3 kızartır.

## 3. Ambiyans
`amb_shop` (MediaPlayer, döngü, SoundPool'a yüklenmez). Yalnızca gün ekranında (hazırlık + gün). Ayar `ambienceVolume` (Ayarlar > "Ambiyans sesi", 0 = kapalı, varsayılan 0,35, müzikten bağımsız). Duraklat paneli, arka plan, ekran değişimi, odak kaybında susar/devam eder; TalkBack/TTS konuşurken `DUCK_FACTOR` ile kısılır. Dosya yoksa sessizce atlanır.

## 4. Kısa etiketler
Düğme etiketi = ad + gerekli durum: "Domates ekle", "Döner makinesi, boş, sıradaki: Döneri dilimle", "Sipariş 2, Ali, ayran ve döner, sabır iyi, seçili". İpucu cümleleri ("…için dokun"), süreler, stok sayıları ve tekrar eden bilgi kalktı. Ayrıntı "Durumu oku" / "Hedefi oku" / "Hazır stoğu oku"da. `A11yAudit` yeni kural: tıklanabilir etiket ≤ 60 karakter (sipariş etiketleri ≤ 110), "dokun" ipucu yok ("Emin misin" onayı hariç), özel eylem etiketleri de aynı.

## 5. Öğretici ve yardım
Öğretici 6 kısa konuşan aşama (hazırlık, müşteri geldi, önerilen istasyon, malzeme/boy, sipariş hazır, bitiş); diğer aşamalar (2, 3, 6, 8) sessiz ilerler. Yardım ve brifing sadeleştirildi (Ocak ve patates bölümü eklendi).

## 6. Konuşma sırası
`SpeechQueue` (bkz. engine_api §00a). Örnek: ekmek bitince oyuncu dilimlemeye başlarsa "Önerilen: Döneri dilimle" söylenmez; öğretici ipucu oyuncu eyleminden sonra susar (`getActionVersion`). Uzun cümleler kısaldı (müşteri tipi cümlesi, "istasyonuna dokun" ve tekrar eden öneri kalktı).

## 7. Testler
`SpeechQueueTest`, `SpeechScenarioTest`, `FriesTest`, `SoundCuesUiTest`, `AmbienceUiTest`; eski testlerde kısalan metin beklentileri bilinçli güncellendi (silme/gevşetme yok).
