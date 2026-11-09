# Terimler sözlüğü (v2.5)

Motorun ürettiği bütün metinler (`Narrator`, `statusText`, `StationOption`, `ReadyAction.text`, `QueueSummary.text`, `Finance.notices`) bu sözlüğe uyar. UI metinleri (`ui/Texts`) de aynı terimleri kullanmalıdır. Aynı kavram her yerde aynı kelimeyle söylenir.

## 1. Yazım kuralları
| Kural | Doğru | Yanlış (eski) |
|---|---|---|
| Malzeme adı cümle içinde küçük harf, cümle başında büyük | "Stokta yok: döner ekmeği." · "Domates eklendi." | "Malzeme bitti: Döner ekmeği." |
| Ürün adı her yerde menüdeki gibi büyük harfle başlar | "Yeni müşteri: Ali, Ekmek arası döner." | |
| İstasyon adı özel addır, büyük harfle başlar | "Ocak dolu (2/2)", "Ekmek ısıtıcı" | |
| Şapkalı yazım (TDK) | dükkân, tezgâh, şikâyet | dükkan, tezgah, şikayet |
| Para: rakam + "lira" (binlik ayırıcı yok) | "2500 lira" | "2.500 TL", "2500 TL" |
| Süre: tam sayı + "saniye" | "2 saniye" | "2 sn", "1,5 saniye" |
| Ondalık: virgül | "3,5 porsiyon", "yıldız 4,2" | "3.5" |
| Yüzde: "yüzde N" (TalkBack "%" okumaz) | "yüzde 110" | "%110" |
| Faiz: "günlük binde N" | "günlük binde 8" | "%0,8" |
| Doluluk: "N/M" | "Ocak dolu (2/2)", "Ekmek ısıtıcı 1/2" | |
| Gün: "N. gün" | "son ödeme 5. gün" | "5 gün" (tarih anlamında) |

## 2. Sipariş ve iş
| Kavram | Terim | Not |
|---|---|---|
| Müşterinin isteği | **sipariş** ("Sipariş 2", cümle içinde "sipariş 2") | "2 numaralı sipariş" kullanılmaz |
| Siparişteki bir ürün | **kalem** | |
| Kalemin bütün adımları bitti | **hazır** ("Ayran hazır.") | yalnızca kalem için |
| Sipariş paketlendi, teslim edilebilir | **teslime hazır** ("Sipariş 2 teslime hazır: Ali.") | "Sipariş hazır" kullanılmaz |
| Kaynak yüzünden yapılamayan açık adım | **Bekliyor:** ("Bekliyor: Patates kızart, Ocak dolu (2/2).") | |
| Süren iş | **sürüyor** ("Ekmeği ısıt sürüyor, 2 saniye") | |
| Oyuncunun eli | **Elin boş / Elin dolu** | "yuva" kelimesi YOK |
| Pasif istasyon dolu | **"<İstasyon> dolu (N/M)"** | |
| Kesinti yüzünden kapalı istasyon | **"Ocak kapalı, doğalgaz kesik"** | |

## 3. Stok ve hazırlık
| Kavram | Terim | Birim |
|---|---|---|
| Malzeme sıfır ya da bu iş için yetersiz | **stokta yok** ("Domates ekle, stokta yok", "Stokta yok: domates. Acil al: 9 lira.") | — |
| Hazırlık partisi için yetersiz | **Malzeme yetmiyor: <malzeme>** | — |
| Acil alım düğmesi ve cümlesi | **Acil al** | — |
| Hazır stok (hazırlanmış) | **hazır <şey> <sayı> <birim>**: "hazır döner 3,5 porsiyon", "hazır ayran 3 bardak", "hazır tepsi 6 kullanım", "hazır patates 2 porsiyon"; özet: "Hazır: döner 4 porsiyon, ayran 3 bardak, tepsi 12 kullanım." | döner/patates **porsiyon**, ayran **bardak**, tepsi **kullanım** |
| Salata ve sos tepsisi | uzun ad "Salata ve sos tepsisi", kısa ad **tepsi** | |
| Hazırlanan içecek | **ayran** ("Ayran doldurma", "4 bardak ayran doldur") | "İçecek doldurma", "hazır içecek" kullanılmaz |
| Hazır stok kapasitesi dolu | **Hazır stok dolu** | |
| Malzeme birimleri | malzemenin kendi birimi (`Ingredient.unit`): porsiyon, adet, bardak, kase | "3 porsiyon domates acil alındı" |

## 4. Para, fatura, banka (v2.5)
| Kavram | Terim |
|---|---|
| Fatura türleri | **Elektrik faturası, Doğalgaz faturası, Su faturası, Kira, Vergi** (`BillType.displayName`; cümle içinde küçük harf) |
| Son ödeme | **son ödeme N. gün** / **son ödeme günü bugün** / **son ödeme yarın** |
| Gecikme | **N gündür ödenmedi** (uyarı), **N gün gecikti** (fatura satırı), **gecikme bedeli** |
| Ödenmeyen faturanın etkisi | elektrik **kısmen kesik / tamamen kesik**, doğalgaz **kısık / kesik (Ocak kapalı)**, su **kesik / tamamen kesik**, kira **ihtar → ihtarname → tahliye**, vergi **haciz** |
| Toplam açık borç | **Açık fatura borcu** |
| Çırak ödemesi | **çırak maaşı** (günlük), ödenemeyen kısmı **maaş borcu** |
| Banka | **kredi**, **kredi notu**, **limit**, **alınabilir**, **taksit** (günlük), **vade** (gün), **faiz**, **erken kapatma**, **gecikmiş taksit**, **kira kredisi** |
| Fiyat | **referans fiyat** (katalog), **satış fiyatı**, "yüzde N"; müşteri tepkisi "Müşteri fiyatı pahalı buldu." / "Müşteri fiyatı uygun buldu." |
| Talep | **Tahmini müşteri: yüzde N daha çok / daha az / değişmez.** |
| Gün sonu | **Satış, bahşiş, acil alım, çırak maaşı, kredi taksiti, haciz, Net, Kasada** |

## 5. Çırak
| Kavram | Terim |
|---|---|
| Sipariş işi başladı | "Çırak, sipariş 2: Ayranı doldur." |
| Paketleme | "Çırak paketliyor: sipariş 2." / "Çırak paketledi: sipariş 2." |
| Teslim | "Çırak teslim etti: sipariş 2, Ali. 240 lira kazandın, …" (başlangıç sessiz) |
| Hazırlık | "Çırak döner dilimliyor." / "Çırak ayranları doldurdu." |
| Yokluk | "Çırak maaşını alamadı, bugün gelmedi." |
| Seviyeler | çırak (1–3), **kalfa** (4), dördüncü çırak (5) |

## 6. Bu sürümde düzeltilen tutarsızlıklar
1. "2 numaralı sipariş" / "Sipariş 2" karışıklığı → her yerde "sipariş 2".
2. "Sipariş hazır" (sipariş) ile "… hazır" (kalem) aynı kelimeydi → sipariş için "teslime hazır".
3. "Malzeme bitti" / "malzeme yok" / "stokta yok" → "stokta yok" (yalnızca hazırlık partisi için "Malzeme yetmiyor").
4. "hazır 3 bardak ayran" / "ayran 3 bardak" / "Hazır içecek 3 bardak" / "hazır 2 porsiyon patates" → "hazır ayran 3 bardak", "hazır patates 2 porsiyon"; "İçecek doldurma" → "Ayran doldurma".
5. "Marketten acil al, 9 lira" → "Acil al: 9 lira." (düğme adıyla aynı).
6. Cümle içinde büyük harfli malzeme adları ("Malzeme yetmiyor: Patates") → küçük harf.
7. "tezgahı", "Şikayetçi" → "tezgâhı", "Şikâyetçi" ("dükkân" ile aynı yazım).
8. Eski panel cümlesi "…stokta yok, seçilemez. … ya da seçimden çıkar." (v0.5 aç/kapa paneli) → "Domates stokta yok. Acil al: 9 lira."
