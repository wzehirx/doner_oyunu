package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Upgrade;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v2.5 terim birliği (docs/terminoloji.md): arayüz kaynağındaki TÜM metin sabitlerinde ve Texts çıktılarında (yardım,
 * öğretici, brifing, özet, market) eski/yasak terim yok: "yuva", "numaralı", "malzeme yok", "TL", "tezgah", "dükkan",
 * "Sipariş hazır", "hazır içecek"… Ekranlardaki görünür etiketler ayrıca her A11yAudit denetiminde taranır.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TermConsistencyTest {

    @Test public void uiSourceStringLiteralsUseTerminology() throws Exception {
        List<String> bad = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File f : new File("src/main/java/com/doner/oyunu/ui").listFiles()) if (f.getName().endsWith(".java")) files.add(f);
        files.add(new File("src/main/java/com/doner/oyunu/MainActivity.java"));
        Pattern lit = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        int n = 0;
        for (File f : files) {
            String src = new String(Files.readAllBytes(f.toPath()), "UTF-8");
            Matcher m = lit.matcher(src);
            while (m.find()) {
                n++;
                String t = m.group(1);
                String term = A11yAudit.forbiddenTerm(t);
                if (term != null) bad.add(f.getName() + ": '" + term + "' in \"" + t + "\"");
            }
        }
        assertTrue("taranan metin sabiti çok az: " + n, n > 300);
        assertTrue("eski terimler:\n" + String.join("\n", bad), bad.isEmpty());
    }

    private static void check(String where, String text, List<String> bad) {
        String term = A11yAudit.forbiddenTerm(text);
        if (term != null) bad.add(where + ": '" + term + "' in " + text);
    }

    @Test public void textsOutputsUseTerminology() {
        List<String> bad = new ArrayList<>();
        for (String[] s : Texts.helpSections()) { check("yardım " + s[0], s[0], bad); check("yardım " + s[0], s[1], bad); }
        for (FlowMode m : FlowMode.values()) {
            check("hızlı yardım", Texts.quickHelp(m, true), bad);
            check("hızlı yardım", Texts.quickHelp(m, false), bad);
            check("öğretici boy", Texts.tutSize(m), bad);
            check("öğretici malzeme", Texts.tutTopping(m), bad);
        }
        for (String t : new String[]{Texts.TUT_PREP, Texts.TUT_CUSTOMER, Texts.TUT_SELECTED, Texts.TUT_PASSIVE, Texts.TUT_SIZE,
                Texts.TUT_TOPPING, Texts.TUT_READY, Texts.TUT_END, Texts.TUT_END_UNTIMED, Texts.PRICE_NOTE, Texts.FLOW_NOTE,
                Texts.TIME_PRESSURE_NOTE}) check("sabit", t, bad);
        assertEquals("Sipariş teslime hazır. Teslim et düğmesine dokun.", Texts.TUT_READY);
        for (int day : new int[]{1, 3, 6, 7, 10, 17, 21, 31, 50, 60}) {
            SaveData s = SaveData.newGame();
            s.day = day;
            for (String l : Texts.briefingLines(s)) check("brifing " + day, l, bad);
            for (Upgrade u : com.doner.oyunu.engine.Catalog.upgrades()) {
                check("market " + u.id, Texts.upgradeDescription(u, s), bad);
                check("market " + u.id, Texts.upgradeStatus(u, s), bad);
                check("market " + u.id, Texts.upgradeButton(u, s), bad);
            }
        }
        DaySummary d = new DaySummary();
        d.day = 3; d.revenue = 100; d.wagesPaid = 50; d.wagesDue = 50;
        for (String[] r : Texts.summaryRows(d)) { check("özet", r[0], bad); check("özet", r[1], bad); }
        assertTrue("eski terimler:\n" + String.join("\n", bad), bad.isEmpty());
        // biçim: para "lira", yüzde "yüzde N"
        assertEquals("2500 lira", Texts.money(2500));
        assertEquals("yüzde 70", Texts.percent(0.7));
    }

    @Test public void forbiddenTermDetectorWorks() {
        assertEquals("numaralı", A11yAudit.forbiddenTerm("2 numaralı sipariş"));
        assertEquals("malzeme yok", A11yAudit.forbiddenTerm("Domates ekle, malzeme yok"));
        assertNull(A11yAudit.forbiddenTerm("Geri alınacak malzeme yok."));
        assertEquals("TL", A11yAudit.forbiddenTerm("Satın al: 300 TL"));
        assertNull(A11yAudit.forbiddenTerm("Satın al: 300 lira"));
        assertEquals("tezgah", A11yAudit.forbiddenTerm("Sos tezgahına dokun"));
        assertNull(A11yAudit.forbiddenTerm("Sos ve salata tezgâhına dokun"));
        assertEquals("sipariş hazır", A11yAudit.forbiddenTerm("Sipariş hazır. Teslim et."));
        assertNull(A11yAudit.forbiddenTerm("Sipariş teslime hazır."));
        assertNull(A11yAudit.forbiddenTerm("Döner Dükkânı"));
    }
}
