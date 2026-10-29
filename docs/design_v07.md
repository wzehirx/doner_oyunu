# v0.7 – Oyun Akışı: Rahat / Dengeli / Gerçekçi

Kaynak: görme engelli oyuncunun (TalkBack) geri bildirimi: "v0.6 daha da zorlaştı. v0.4'te malzemeleri kendisi koyuyordu; v0.6 gerçekçi ama her malzeme için ekstra çaba (tek tek ekleme, boy seçimi) gerekiyor." v0.6 ve v0.5 belgeleri geçerlidir; çakışmada bu belge kazanır.

## 1. Karar
Ayarlarda **"Oyun akışı"**: üç seçenek, varsayılan **Dengeli**. İlke: *"Standart olan otomatik, farklı olan elle."*

| Akış | Döner boyu | Malzemeler | Dokunuş (DONER / SOS) |
|---|---|---|---|
| **Rahat** | her zaman otomatik (istenen boy) | "Malzemeleri ekle": hedef küme (müşteri istekleri dahil) otomatik | 1 / 1 (v0.4 gibi) |
| **Dengeli** (varsayılan) | tam ekmek otomatik; yarım/duble istenince boy paneli | "Varsayılan malzemeleri ekle" tek dokunuş; istek yoksa adım biter; istek varsa adım açık kalır, oyuncu farkı yapar ("Soğan çıkar", "Acı sos ekle") ve "Malzemeleri bitir" | 1 / 1, yalnızca fark olunca + panel |
| **Gerçekçi** | her dilimlemede elle | v0.6: tek tek ekleme, bol, geri al, bitir | v0.6 gibi |

- **Süre ve stok tutarlı:** otomatik malzeme bir ZAMANLI iştir (oyuncu yuvası), süresi tek tek eklemelerin toplamıdır (salata 0,6 sn, sos 0,5 sn; tepsiden yarısı). Stok başta düşer (önce tepsi). Bir malzeme bile yoksa hiçbir şey tüketilmez, `onOutOfStock` → mevcut "Acil al" (eksiklerin hepsi tek dokunuş) yolu çalışır. Otomatik iş ödül/ceza kurallarını değiştirmez: doğrulama teslimde.
- **Duyurular (kısa):** "Varsayılan malzemeler ekleniyor, 2 saniye." → "Varsayılan malzemeler eklendi: domates, marul, soğan ve ketçap. İstek: soğansız. Farkı sen yap, sonra Malzemeleri bitir." / Rahat: "Malzemeler eklendi: domates ve marul. İstek uygulandı: soğansız." / boy: "Döneri dilimle başladı, boy otomatik: yarım ekmek, 2 saniye."
- **Değişim her an:** Ayarlar (3 büyük seçenek düğmesi, seçili olan "seçili" okunur), gün brifingi ("Oyun akışı: Dengeli" düğmesi sıradakine geçer; akış hiç seçilmediyse ilk brifingte kısa sesli soru) ve oyun içinde Duraklat menüsü (aynı düğme). Kayıt: `SaveData.flowMode`, `flowChosen` (`v=4`; v0.4/v0.5/v0.6 kayıtları Dengeli ile yüklenir). Yeni oyun ayarı korur.
- **TalkBack Eylemleri + seçenek paneli aynı:** her iki yol da `applyStationOption` çağırır; yeni seçenekler `auto`, `remove:<id>`. Okuma seçenekleri ("Hedefi oku") tek işlemi panele zorlamaz (`StationOption.direct`): Rahat/Dengeli'de dokunuş doğrudan uygular, Eylemler menüsünde yine tüm seçenekler (Hedefi oku dahil) bulunur.

## 2. Gerçekçi akışın adilleştirilmesi (v0.6 = Gerçekçi'nin eski hali)
Ölçüm (insan benzeri bot: 1,5 sn tepki ±%20, panel açmak ayrı tepki, hazırlık + yükseltme): v0.6 4/4 tohumda gün 22–25'te takılıyordu; dar boğaz sabır değil **oyuncu dikkati** (sipariş başına ~13–14 dokunuş). Yapılanlar:
1. **Sabır, elle dokunuşu sayar:** v0.6 tahmini yalnızca makine süresini ve malzeme başına 1,5 sn'yi sayıyordu. Artık boylu kalemde boy seçimi (+3 sn), malzemeli kalemde menü+bitir (+3 sn), elle eklenen her malzeme (+1 sn) ve her istek farkı (+1,5 sn) sabra eklenir; bu insan süresi günle azalan `workPatience` ile küçülmez. Dengeli'de yalnızca elle yapılanlar sayılır; Rahat'ta 0.
2. **Gerçekçi çarpanları:** sabır ×1,15, geliş ×0,9 ("el emeğine saygılı, biraz daha seyrek müşteri"), ödül olarak bahşiş ×1,15.
3. **İlerleme bağlantısı (c):** Gerçekçi'de çırak seviye 2 olunca SOS menüsüne **"Standart malzemelerle bitir (çırak hazırladı)"** kısayolu gelir: varsayılan küme eklenir ve adım biter. İsteği olan siparişte kullanmak HATADIR (isteği hatırlamak oyuncuda kalır, kısayol isteği ele vermez). Çırak yine hiçbir malzeme adımını kendisi yapmaz.

## 3. Ölçülen sonuçlar (FlowBalanceTest; "BAL" satırları)
- Her akışta 0,8 sn ve 1,5 sn hazırlıklı+yükseltmeli bot 1–40 geçer (0 başarısız deneme).
- İnsan benzeri 1,5 sn bot: **v0.6** 4/4 tohumda takılır (gün 22–25, 3–7 başarısız deneme, ~2,1–2,6 kayıp müşteri/gün); **Gerçekçi v0.7** 4/4 tohumda 1–40 geçer (0 başarısız deneme, 0,05–0,65 kayıp/gün); gün 1–20'de kayıp/gün 1,7–1,9 → 0–0,25, dokunuş/sipariş 11,6–12,9 → 10,0–10,9. Rahat ve Dengeli de geçer.
- Sipariş başına dokunuş (insan benzeri bot, 40 gün): Rahat ~5,1, Dengeli ~7,5, Gerçekçi ~9,6 (çırak 2'den önce gün 1–7: ~8,9 ≈ v0.6 ~9,1); aynı siparişlerin **v0.4 eşdeğeri** ~9,4 (gün 1–7: ~6,0; Rahat 5,2, Dengeli 6,5). Rahat ve Dengeli 40 günde v0.4'ün altında, ilk günlerde ona eşit düzeyde: "v0.4 hissi". Dikkatsiz (isteği yok sayan) bot Rahat'ta hata yapmaz, Dengeli/Gerçekçi'de yapar: istekler Dengeli'de hâlâ oyunun kararıdır.
- Yükseltmesiz bot her akışta takılır (Rahat/Dengeli ~27–28, Gerçekçi ~34–36): ilerleme yükseltme ister. Hiçbir şey yapmayan bot her akışta ≤5 günde başarısız.

## 4. Alternatifler ve neden seçilmedi
- **(a) Yalnızca iki mod (Kolay/Gerçekçi):** v0.4 ile v0.6 arasındaki uçurumu bırakır; çoğu oyuncu için doğru nokta ortadadır (istekleri dinleyip uygulamak oyunun keyifli kararıdır, her domatese dokunmak değil). Üç mod, iki modun tüm yararını içerir.
- **(b) Ayrı ayrı anahtarlar (malzeme otomasyonu × boy otomasyonu × istek otomasyonu):** esnek ama TalkBack'te ayar ekranı ve öğretici karmaşıklaşır; anlamlı birleşimler zaten üç akışa düşüyor.
- **(c) Yalnızca ilerlemeyle otomasyon (çırak/alet malzeme koysun):** oyuncunun şikâyetini ilk günlerde çözmez ve zorluğu oyuncu seçemez. Seçilen tasarımda (c) yalnızca Gerçekçi'nin geç oyununu adilleştiren tamamlayıcıdır.
- **(d) Yalnızca Gerçekçi'yi yumuşatmak:** dokunuş yükü (asıl şikâyet) kalır; yine de (d) Gerçekçi içinde uygulandı.
- **Rahat'ta bile istasyona dokunmadan tam otomatik malzeme:** v0.4 hissi istasyon başına bir dokunuştur; tamamen otomatik olursa oyuncu sıradaki işi kaybeder ve ses/olay sırası belirsizleşir.

## 5. Erişilebilirlik
- Tüm akışlarda v0.6 kuralları geçerli: Türkçe etiketler, ≥56dp, tek dokunma, odak yönetimi, TalkBack Eylemleri = panel. Seçili akış düğmesi "Dengeli, seçili" der (renge bağımlı değil; `A11yAudit` yeni kural: seçili görünümün etiketi "seçili" demeli).
- Öğretici, kısa yardım, yardım ekranı ve brifing akışa göre konuşur (`Texts.tutTopping/tutSize/quickHelp(mode)`, yardımda "Oyun akışı" bölümü).
