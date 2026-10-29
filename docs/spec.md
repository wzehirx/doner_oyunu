# Döner Dükkanı – Teknik Şartname

Hedef: Android (minSdk 24, Java 17, saf Android framework, AndroidX YOK, dış kütüphane YOK). Tek Activity (`MainActivity`), ekranlar programatik View'larla (XML layout şart değil).
Paket: `com.doner.oyunu`. Alt paketler: `engine` (SAF Java, android import YASAK, JUnit ile test edilir), `audio`, `ui`.

## Oyun döngüsü
Menü → Gün Özeti/Brifing (menü, hedef) → Market (malzeme/yükseltme alışverişi) → GÜN (kasada servis) → Gün Sonu (kazanç, yıldız, ödemeler) → Market → sonraki gün. Kayıt: SharedPreferences (engine `SaveData` düz "key=value" metne serileştirir).

## Gün (GameEngine)
- (v0.8) Süre baskısı ayarı: kapalıyken gün süresiz geçer (sayaç yok, sabır sabit, müşteri gitmez; kota kadar müşteri gelip hepsi teslim edilince gün biter; hız bonusu yok, bahşiş x0,8). Ayrıntı: docs/design_v08.md.
- Gün N, süre = 120s + 6s*N (üst sınır 300s). Müşteri geliş aralığı gün ilerledikçe kısalır; geliş sıklığı yıldız çarpanıyla ölçeklenir (1★ ×0.6 … 5★ ×1.4). Gün sonunda bekleyen sipariş kalmayana kadar (max +30s) devam.
- Menü birikimlidir: gün N'de açık TÜM ürünler (önceki günlerinki dahil) sipariş edilir; yeni ürün açılan gün brifingde duyurulur. Ürün açılış günleri: 1 Ekmek arası döner, 2 İçecek(ayran), 3 Pilav üstü döner, 4 Tavuk dürüm, 6 Hatay usulü dürüm, 8 Zurna dürüm, 10 İskender, 12 Porsiyon döner, 14 Lahmacun, 16 Künefe (+ araştırma dosyası docs/research.md varsa oradaki veriyi esas al).
- Siparişte birden fazla ürün olabilir (gün ilerledikçe 1→3). Müşteri tipleri (acele, sabırlı, öğrenci, turist, vb.) farklı sabır süresi/bahşiş verir.
- Sabır süresi dolan müşteri gider: yıldız düşer, gelir yok. Teslim süresine göre memnuniyet (hızlı = bahşiş + yıldız artışı).
- Yıldız: 1.0–5.0 ondalıklı itibar. Başlangıç 3.0. Memnun +0.08 (hızlı +0.12), mutsuz −0.30, hatalı sipariş −0.15. 1.0 altına düşerse gün BAŞARISIZ: aynı günü tekrar dene (para kaybı yok).
- Sipariş hazırlama: her ürün 3–5 adımdan oluşur (ör. ekmek arası: Ekmeği ısıt → Döneri dilimle → Sos/salata ekle → Paketle). Her adım bir İSTASYON'da yapılır (Döner makinesi, Ekmek ısıtıcı, Sos/Salata tezgahı, Izgara/Fırın, Paketleme, İçecek dolabı, Tatlı). Oyuncu bir siparişi seçer, sistem sıradaki adımı söyler, oyuncu doğru istasyona dokunur → adım X saniyede (ses/ilerleme) biter. Yanlış istasyon = hata sesi, ufak ceza. Tüm adımlar bitince "Teslim et" butonu/eylemi.
- Eş zamanlı işlem yuvası (slot) sayısı ekipmanla artar (başlangıç 1, yükseltmeyle 3'e kadar). Süreler ekipman yükseltmeleriyle kısalır; kalıcı "tempo" ayarı (0.6×–1.6×) süreleri ve sabırları ölçekler.
- Malzeme stoğu: her adım/ürün malzeme tüketir. Malzeme biterse o ürün hazırlanamaz (sesli uyarı), oyuncu "Marketten acil al" ile (pahalı, +%50) tamamlayabilir. Gün öncesi Market'te toplu alım; "Önerilen malzemeleri al" hızlı butonu olsun.
- Para: teslimde ürün satış fiyatı + bahşiş. Gün sonunda kira/gider (kademeli). Para ile: malzeme, mutfak aletleri (döner makinesi, ekmek ısıtıcı, otomatik dilimleyici, buzdolabı, ızgara...), restoran öğeleri (masa, tabela, klima, müzik sistemi, dekor) – etkileri sabır süresi, müşteri sıklığı, bahşiş, hız.
- Zorluk eğrisi: gün N, öncekilerin tüm zorluklarını içerir; sipariş büyüklüğü, geliş sıklığı artar, sabır azalır. En az 30 gün oynanabilir; sonrası döngüsel zorluk artışıyla sonsuz.

## Erişilebilirlik (ZORUNLU, %100 görme engelli kullanılabilirlik)
- Her etkileşimli View: `setContentDescription` (Türkçe, durum dahil: örn. "Ekmek ısıtıcı istasyonu, boş, ekmek arası döner için gerekli"), minimum 56dp dokunma hedefi, mantıklı `accessibilityTraversalBefore/After`/ekleme sırası, `setImportantForAccessibility`. Dekoratif View'lar importantForAccessibility=no.
- Dinamik olaylar (yeni müşteri, adım bitti, sabır azaldı, yıldız değişti, gün bitti) `announceForAccessibility` + TTS konuşmasıyla duyurulur. TalkBack AÇIKSA çift konuşmayı önlemek için `Speaker` yalnızca announce kullanır; KAPALIYSA kendi `TextToSpeech`'ini kullanır (ayarlardan "Sesli anlatım" açık/kapalı + hız).
- Canlı bölgeler: Durum çubuğu (para, yıldız, kalan süre, bekleyen sipariş) `ACCESSIBILITY_LIVE_REGION_POLITE`; "Durumu oku" büyük butonu her yerde mevcut.
- Dokunmatik hareket gerektiren (sürükle, çimdik, uzun basma) eylem YOK; sadece tek dokunma/çift dokunma.
- Ses işaretleri: her olay ayırt edilebilir ses + titreşim (Vibrator, ayarlanabilir). Siparişler stereo pan ile (sipariş yuvası 1 sol … 3 sağ) çalınabilir (SoundPool setVolume sol/sağ).
- Yazı tipi büyük, yüksek kontrast (koyu zemin, krem/altın yazı), renge bağımlı bilgi yok; sistem font ölçeğine saygı.
- Menü ve her ekranda "Nasıl oynanır" sesli yardım; gün başında 1. günde öğretici (tutorial) adım adım sesli.
- Duraklat/Devam tuşu, ekranda her an erişilebilir; arka plana gidince oyun otomatik durur.
- Odak yönetimi: ekran değişince başlığa odak (`sendAccessibilityEvent(TYPE_VIEW_FOCUSED)` / `requestFocus` + `setAccessibilityPaneTitle`).

## Ses
`audio/SoundManager`: SoundPool (sfx), müzik için MediaPlayer döngü; ayarlardan müzik/efekt ses seviyeleri; res/raw dosyaları (adlar: sfx_customer_arrive, sfx_order_new, sfx_slice, sfx_bread_warm, sfx_sauce, sfx_wrap, sfx_grill_sizzle, sfx_pour, sfx_step_done, sfx_deliver, sfx_coin, sfx_error, sfx_warning, sfx_customer_happy, sfx_customer_angry, sfx_star_up, sfx_star_down, sfx_day_start, sfx_day_end, sfx_buy, sfx_tap, sfx_tick; music_menu, music_day_calm, music_day_busy, music_shop). Dosyalar tools/gen_audio.py ile başka ajan tarafından üretiliyor; kodda `getIdentifier` ile yükle, dosya yoksa sessizce atla (çökme olmasın). Görsel: şık canlı renkli (sıcak turuncu/kırmızı/altın) özel çizim View'lar (Canvas) – ama tüm bilgi sesli/metinsel de erişilebilir olmalı.

## Test edilebilirlik
Engine deterministik: `new GameEngine(long seed, ...)`, `tick(float dtSeconds)`, olaylar `GameListener` ile. JUnit testleri `app/src/test/java/com/doner/oyunu/` (günün tamamlanabilirliği, sabır/yıldız, stok, kayıt/yükleme, tüm günlerin 1–40 simülasyonu bot ile).
