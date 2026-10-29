package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;
import com.doner.oyunu.ui.Texts;

import org.junit.Test;

import java.util.List;

/** v0.7 oyun akışı metinleri: brifing, düğme etiketleri, öğretici ve yardım (Türkçe, akışa göre). */
public class FlowTextsTest {

    @Test public void briefingHasFlowLineThatAsksUntilChosen() {
        SaveData s = SaveData.newGame();
        String all = String.join(" | ", Texts.briefingLines(s));
        assertTrue(all, all.contains("Seçim: Oyun akışı: Dengeli."));
        assertTrue(all, all.contains("Rahat, Dengeli ya da Gerçekçi"));
        s.flowChosen = true;
        s.flowMode = FlowMode.RAHAT;
        all = String.join(" | ", Texts.briefingLines(s));
        assertFalse(all.contains("Seçim:"));
        assertTrue(all, all.contains("Oyun akışı: Rahat. Malzemeler ve döner boyu otomatik."));
        assertTrue(all, all.contains("Duraklat menüsünden"));
        assertTrue("öğretici satırı akışa göre", all.contains("malzemelerin ve boyun otomatik eklendiğini"));
        s.day = 2;
        assertTrue(String.join(" ", Texts.briefingLines(s)).contains("Yeni boy: yarım ekmek. Rahat akışta boy otomatik seçilir."));
        s.flowMode = FlowMode.GERCEKCI;
        assertTrue(String.join(" ", Texts.briefingLines(s)).contains("Yeni boy: yarım ekmek. Döner makinesinde boyu sen seçersin."));
    }

    @Test public void flowButtonAndOptionDescriptionsSayStateAndNext() {
        assertEquals("Oyun akışı: Dengeli", Texts.flowButtonLabel(FlowMode.DENGELI));
        assertTrue(Texts.flowButtonDescription(FlowMode.DENGELI).startsWith("Oyun akışı: Dengeli. Dokununca Gerçekçi akışına geçer."));
        assertTrue(Texts.flowOptionDescription(FlowMode.DENGELI, true).startsWith("Dengeli, seçili."));
        assertTrue(Texts.flowOptionDescription(FlowMode.DENGELI, true).endsWith("Önerilen."));
        assertTrue(Texts.flowOptionDescription(FlowMode.RAHAT, false).startsWith("Rahat, seçmek için dokun."));
        for (FlowMode m : FlowMode.values()) {
            assertFalse(m.description.isEmpty());
            assertTrue(m.spoken().startsWith("Oyun akışı: " + m.displayName + ". "));
        }
    }

    @Test public void tutorialAndQuickHelpFollowFlow() {
        assertEquals(Texts.TUT_TOPPING, Texts.tutTopping(FlowMode.GERCEKCI));
        assertEquals(Texts.TUT_SIZE, Texts.tutSize(FlowMode.GERCEKCI));
        assertEquals(Texts.quickHelp(), Texts.quickHelp(FlowMode.GERCEKCI));
        assertTrue(Texts.tutTopping(FlowMode.RAHAT).contains("otomatik"));
        assertTrue(Texts.tutTopping(FlowMode.DENGELI).contains("Soğan çıkar"));
        assertTrue(Texts.tutTopping(FlowMode.DENGELI).contains("Malzemeleri bitir"));
        assertTrue(Texts.tutSize(FlowMode.RAHAT).contains("sistem seçer"));
        assertTrue(Texts.tutSize(FlowMode.DENGELI).contains("yarım ekmek ya da duble"));
        assertTrue(Texts.quickHelp(FlowMode.RAHAT).contains("Rahat akışta"));
        assertTrue(Texts.quickHelp(FlowMode.DENGELI).contains("Hedefi oku"));
        assertTrue(Texts.quickHelp(FlowMode.DENGELI).contains("Eylemler menüsünden"));
    }

    @Test public void helpHasFlowSectionAndApprenticeMentionsStandardSet() {
        boolean flow = false;
        for (String[] sec : Texts.helpSections()) {
            if (sec[0].equals("Oyun akışı")) {
                flow = true;
                for (String k : new String[]{"Rahat", "Dengeli", "Gerçekçi", "Ayarlardan", "Duraklat", "Acil al", "Standart malzemelerle bitir"})
                    assertTrue("akış yardımı: " + k, sec[1].contains(k));
            }
        }
        assertTrue(flow);
        assertTrue(Texts.apprenticeLevelsText(0).contains("Standart malzemelerle bitir"));
        List<String[]> h = Texts.helpSections();
        assertTrue(h.size() >= 7);
    }
}
