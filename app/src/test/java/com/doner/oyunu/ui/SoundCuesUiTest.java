package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import com.doner.oyunu.audio.SoundManager;
import com.doner.oyunu.engine.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v0.9: madde 1 (otomatik eklemede her malzemenin kendi sesi), madde 2 (sfx_fry döngüsü), madde 3 (ambiyans).
 * Köken neden: Rahat/Dengeli "auto" yolu yalnızca tek bir sos döngüsü (sfx_sauce) çalıyordu; tek tek ekleme yolundaki
 * (onToppingAddStarted) domates/marul sesleri bu yolda hiç tetiklenmiyordu.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class SoundCuesUiTest {
    static final class Rec implements SoundManager.Sink {
        final List<String> names = new ArrayList<>();
        final List<float[]> args = new ArrayList<>();
        final List<Long> at = new ArrayList<>();
        long clock;

        @Override public int start(String name, float l, float r, float rate, int loop) {
            names.add(name);
            args.add(new float[]{l, r, rate, loop});
            at.add(clock);
            return names.size();
        }

        @Override public void stop(int stream) { }
    }

    private UiHarness day(FlowMode m, int d) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = d;
        for (Ingredient i : Catalog.ingredients()) h.act.save.inventory.set(i.id, 30);
        h.startDay();
        h.engine().setSpawning(false);
        return h;
    }

    private void autoToppingsPlayOwnSounds(FlowMode m) {
        UiHarness h = day(m, 3);
        GameEngine e = h.engine();
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        h.run(300);
        e.applyStationOption(o.id, -1, Station.EKMEK.id, "step"); h.run(5000);
        e.applyStationOption(o.id, -1, Station.DONER.id, "step"); h.run(5000);
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        float total = 0;
        for (ActiveStep a : e.getActiveSteps()) total = a.total;
        h.run(3000);
        // 3. günde varsayılan: domates, marul, soğan, ketçap, mayonez? -> yalnızca açık olanlar
        List<String> want = new ArrayList<>();
        for (String id : o.getLine(0).getTarget()) want.add(ToppingSfx.sfxFor(Catalog.ingredient(id)));
        assertTrue("domates + marul + soğan + sos var: " + want, want.contains("sfx_veg_tomato") && want.contains("sfx_veg_lettuce") && want.contains("sfx_sauce"));
        List<String> got = new ArrayList<>();
        for (String n : rec.names) if (n.startsWith("sfx_veg") || n.equals("sfx_sauce")) got.add(n);
        assertEquals("her malzeme kendi sesiyle, sırayla (" + m + ")", want, got.subList(0, want.size()));
        for (float[] a : rec.args) assertEquals("sıralı vuruşlar döngü değil", 0f, a[3], 0f);
        assertEquals("sos döngüsü kurulmaz", 0, h.act.sound.activeLoopCount());
    }

    @Test public void rahatAutoToppingsPlayTomatoLettuceAndSauceInOrder() { autoToppingsPlayOwnSounds(FlowMode.RAHAT); }

    @Test public void dengeliAutoToppingsPlayTomatoLettuceAndSauceInOrder() { autoToppingsPlayOwnSounds(FlowMode.DENGELI); }

    @Test public void autoSequenceSpreadsOverTheJobAndPausesWithTheGame() {
        UiHarness h = day(FlowMode.RAHAT, 3);
        GameEngine e = h.engine();
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        h.run(300);
        e.applyStationOption(o.id, -1, Station.EKMEK.id, "step"); h.run(5000);
        e.applyStationOption(o.id, -1, Station.DONER.id, "step"); h.run(5000);
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        e.applyStationOption(o.id, -1, Station.SOS.id, "auto");
        h.run(100);
        int first = rec.names.size();
        assertTrue("ilk malzeme hemen çalar", first >= 1);
        assertEquals("sfx_veg_tomato", rec.names.get(0));
        assertEquals("iş sürerken sıralı ses etkin", 1, h.act.sound.activeLoopCount());
        h.click("Duraklat"); h.run(100);
        int paused = rec.names.size();
        h.run(4000);
        assertEquals("duraklatınca yeni vuruş yok", paused, rec.names.size());
    }

    @Test public void toppingSfxPlanIsProportionalAndMapsIngredients() {
        List<Ingredient> l = Arrays.asList(Catalog.ingredient("domates"), Catalog.ingredient("marul"), Catalog.ingredient("sogan"), Catalog.ingredient("ketcap"));
        List<ToppingSfx.Cue> plan = ToppingSfx.plan(l, 2.2f);
        assertEquals(4, plan.size());
        assertEquals(0, plan.get(0).offsetMs);
        assertEquals("sfx_veg_tomato", plan.get(0).sfx);
        assertEquals("sfx_veg_lettuce", plan.get(1).sfx);
        assertEquals("soğan marul sesiyle", "sfx_veg_lettuce", plan.get(2).sfx);
        assertEquals("sfx_sauce", plan.get(3).sfx);
        for (int i = 1; i < plan.size(); i++) assertTrue(plan.get(i).offsetMs > plan.get(i - 1).offsetMs);
        assertTrue(plan.get(3).offsetMs < 2200);
        assertTrue(ToppingSfx.plan(l, 0f).isEmpty());
        assertTrue(ToppingSfx.plan(Collections.<Ingredient>emptyList(), 2f).isEmpty());
        assertTrue("sfx_veg_* SFX listesinde", Arrays.asList(SoundManager.SFX).containsAll(Arrays.asList("sfx_veg_tomato", "sfx_veg_lettuce", "sfx_sauce")));
    }

    @Test public void fryStepLoopsSfxFryForItsDuration() {
        assertTrue(Arrays.asList(SoundManager.LOOPED).contains("sfx_fry"));
        assertTrue(Arrays.asList(SoundManager.SFX).contains("sfx_fry"));
        UiHarness h = day(FlowMode.DENGELI, 3);
        GameEngine e = h.engine();
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi+patates"), Collections.<String>emptyList());
        h.run(300);
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.IZGARA.id, "step"));
        h.run(100);
        assertEquals("sfx_fry", rec.names.get(0));
        assertEquals("SoundPool döngüsü", -1f, rec.args.get(0)[3], 0f);
        assertEquals(1, h.act.sound.activeLoopCount());
        h.run(4500);
        assertEquals("süre bitince durur", 0, h.act.sound.activeLoopCount());
    }

    @Test public void friesPrepAlsoPlaysTheFrySound() {
        UiHarness h = new UiHarness(false, FlowMode.DENGELI);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 3;
        h.act.save.inventory.set("patates", 20);
        h.click("Güne başla"); h.settle();
        h.run(800);
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        assertEquals(StartResult.STARTED, h.engine().applyStationOption(-1, -1, Station.IZGARA.id, "prep:fries"));
        h.run(100);
        assertEquals("sfx_fry", rec.names.get(0));
        assertNotNull("hazır patates göstergesi", h.findText("Hazır patates"));
    }
}
