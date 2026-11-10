package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.Bank;
import com.doner.oyunu.engine.Bill;
import com.doner.oyunu.engine.BillType;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.Finance;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.ui.Texts;

import org.junit.Test;

import java.util.List;

/** v2.6 metinleri: kelime sınırından kısaltma, kısa fatura/borç metinleri (özet + ayrıntı), iflas ve öğretici cümleleri. */
public class TextsV26Test {

    private static void assertWordBoundary(String full, String cut, int max) {
        assertTrue(cut + " (" + cut.length() + ")", cut.length() <= max);
        assertTrue("önek olmalı: " + cut, full.startsWith(cut));
        if (cut.length() < full.length()) {
            char next = full.charAt(cut.length());
            assertTrue("kelime ortasından kesildi: '" + cut + "' | sonraki '" + next + "'", next == ' ' || next == ',');
            assertFalse("sonda noktalama kalmamalı: " + cut, cut.endsWith(",") || cut.endsWith(" ") || cut.endsWith(":"));
        }
    }

    @Test public void clipCutsAtCommaOrWordBoundaryNeverMidWord() {
        String s = "Karışık döner tabağı: Et ve tavuk dilimle, Döner makinesi, boy seç";
        assertTrue(s.length() > 60);
        String c = Texts.clip(s, 60);
        assertWordBoundary(s, c, 60);
        assertEquals("son virgülden kesilir", "Karışık döner tabağı: Et ve tavuk dilimle, Döner makinesi", c);
        assertEquals("kısa metin değişmez", "Teslim et", Texts.clip("Teslim et", 60));
        // virgül yoksa son boşluktan
        String w = "Bu cümlede hiç virgül yok ama altmış karakterden epeyce uzun bir metin var";
        assertWordBoundary(w, Texts.clip(w, 60), 60);
        // tek uzun kelime: harf sınırı (sonsuz döngü/boş metin yok)
        String one = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        assertEquals(60, Texts.clip(one, 60).length());
        assertEquals("", Texts.clip(null, 60));
        // her uzunlukta kelime sınırı korunur
        for (int max = 10; max < s.length(); max++) assertWordBoundary(s, Texts.clip(s, max), max);
    }

    @Test public void shortLabelsStayWithinSixtyAndKeepWholeWords() {
        SaveData s = SaveData.newGame();
        s.day = 60;
        for (Product p : Catalog.productsForDay(60)) {
            String l = Texts.priceRowLabel(s, p);
            assertTrue(l, l.length() <= 60);
            assertTrue(l, l.startsWith(p.name.length() <= 40 ? p.name : p.name.substring(0, 10)));
        }
        Finance.issue(s, BillType.ELEKTRIK, 123456, 10);
        Bill b = s.bills.get(0);
        String l = Texts.billLabel(b, 60);
        assertTrue(l, l.length() <= 60);
        assertTrue(l, l.startsWith("Elektrik faturası "));
        assertFalse(l, l.endsWith(","));
    }

    private static SaveData fourLateBills() {
        SaveData s = SaveData.newGame();
        s.day = 12;
        s.money = 800;
        Finance.issue(s, BillType.ELEKTRIK, 300, 5);
        Finance.issue(s, BillType.DOGALGAZ, 250, 5);
        Finance.issue(s, BillType.SU, 120, 5);
        Finance.issue(s, BillType.KIRA, 1500, 6);
        s.wageDebt = 260;
        return s;
    }

    /** Denetim §3.2: 4 geciken faturada tek duyuru 600+ karakterdi; özet kısa, ayrıntı istenince. */
    @Test public void spokenBillSummariesAreShortEvenWithFourLateBills() {
        SaveData s = fourLateBills();
        List<String> notices = Finance.notices(s, s.day);
        int full = 0;
        for (String n : notices) full += n.length() + 1;
        assertTrue("uzun metin: " + full, full > 500);

        String brief = Texts.noticesBrief(s, s.day);
        assertTrue(brief, brief.startsWith("5 fatura uyarısı. En acili: "));
        assertTrue(brief, brief.endsWith("Ayrıntılar Faturalar ekranında."));
        assertTrue("kısa: " + brief.length(), brief.length() <= 200);

        String read = Texts.billsRead(s, s.day);
        assertTrue(read, read.startsWith("4 açık fatura, toplam "));
        assertTrue(read, read.contains("4 gecikmiş."));
        assertTrue(read, read.contains("Maaş borcu 260 lira."));
        assertTrue(read, read.endsWith("Kasada 800 lira."));
        assertTrue("kısa: " + read.length(), read.length() <= 200);

        String head = Texts.billsHeader(s, s.day);
        assertTrue(head, head.startsWith("Açık fatura 4, borç "));
        assertTrue(head.length() <= 90);

        // ayrıntı: motorun tam uyarısı
        Bill el = Finance.openBills(s).get(0);
        assertTrue(Texts.billDetail(s, el, s.day), Texts.billDetail(s, el, s.day).contains("gündür ödenmedi"));
    }

    @Test public void noticesBriefForSingleAndNoWarning() {
        SaveData s = SaveData.newGame();
        s.day = 9;
        assertEquals("", Texts.noticesBrief(s, s.day));
        Finance.issue(s, BillType.KIRA, 1500, 7); // son ödeme 9: bugün
        assertEquals("Kira 1500 lira, son ödeme bugün. Ödemezsen yarın ev sahibi ihtar çeker.", Texts.noticesBrief(s, s.day));
        // süresi olan fatura uyarı değildir
        SaveData t = SaveData.newGame();
        t.day = 8;
        Finance.issue(t, BillType.SU, 120, 8); // son ödeme 10: 2 gün var
        assertEquals("", Texts.noticesBrief(t, t.day));
    }

    @Test public void billEffectIsOneShortSentencePerLevel() {
        SaveData s = SaveData.newGame();
        s.day = 12;
        Finance.issue(s, BillType.ELEKTRIK, 300, 9);  // son 11: 1 gün gecikti
        Finance.issue(s, BillType.DOGALGAZ, 250, 5);  // son 7: 5 gün -> Ocak kapalı
        Finance.issue(s, BillType.KIRA, 1500, 3);     // son 5: 7 gün -> tahliye
        Finance.issue(s, BillType.SU, 120, 12);       // son 14: süresi var
        for (Bill b : s.bills) {
            String e = Texts.billEffect(s, b, s.day);
            assertTrue(b.type + ": " + e, e.length() <= 70);
            switch (b.type) {
                case ELEKTRIK: assertEquals("Elektrik kısmen kesik. Ödersen hemen düzelir.", e); break;
                case DOGALGAZ: assertEquals("Ocak kapalı, doğalgaz kesik. Ödersen hemen düzelir.", e); break;
                case KIRA: assertEquals("Tahliye kararı: ödemeden dükkân açılamaz.", e); break;
                default: assertEquals("", e);
            }
        }
    }

    @Test public void quoteTextsPutInstallmentFirstAndStayShort() {
        SaveData s = SaveData.newGame();
        s.day = 10;
        Bank.Quote q = Bank.quote(s, 1500, 10);
        assertNotNull(q);
        String sh = Texts.quoteShort(q);
        assertEquals("1500 lira, 10 gün, günlük taksit " + q.installment + " lira.", sh);
        assertTrue(Texts.quoteLine(q), Texts.quoteLine(q).startsWith("Teklif: 1500 lira, 10 gün: günlük taksit "));
        String head = Texts.bankHeader(s);
        assertTrue(head, head.startsWith("Kredi notu " + s.creditScore + ". Alınabilir "));
        assertTrue(head, head.endsWith("Kredi borcun yok. Kasada " + s.money + " lira."));
        assertTrue(head.length() <= 100);
    }

    @Test public void bankruptcyAndTutorialTexts() {
        for (Finance.Bankruptcy b : Finance.Bankruptcy.values()) {
            String t = Texts.bankruptcyText(b, 23);
            if (b == Finance.Bankruptcy.NONE) { assertEquals("", t); continue; }
            assertTrue(t, t.startsWith("Dükkân kapandı. "));
            assertTrue(t, t.contains("23. güne ulaştın."));
            assertTrue(t, t.endsWith("Yeni oyun düğmesiyle baştan başla; ayarların korunur."));
            assertTrue("kısa: " + t.length(), t.length() <= 200);
        }
        SaveData s = SaveData.newGame();
        s.day = 4;
        Finance.issue(s, BillType.ELEKTRIK, 231, 3);
        Bill b = s.bills.get(0);
        assertTrue(Texts.firstBillTip(b, true).contains("otomatik ödenir"));
        assertTrue(Texts.firstBillTip(b, false).contains("Faturalar düğmesinden Öde"));
        assertTrue(Texts.firstBillTip(b, true).length() <= 160);
    }

    @Test public void summaryRowsListAutoPaidBills() {
        com.doner.oyunu.engine.DaySummary d = new com.doner.oyunu.engine.DaySummary();
        SaveData s = SaveData.newGame();
        s.day = 9;
        s.money = 50_000;
        Finance.issue(s, BillType.ELEKTRIK, 300, 7);
        d.autoPaid.addAll(Finance.autoPay(s, 9, true));
        assertEquals(1, d.autoPaid.size());
        boolean found = false;
        for (String[] r : Texts.summaryRows(d)) {
            if (r[0].equals("Otomatik ödenen fatura")) { found = true; assertEquals("elektrik faturası 300 lira", r[1]); }
        }
        assertTrue(found);
    }
}
