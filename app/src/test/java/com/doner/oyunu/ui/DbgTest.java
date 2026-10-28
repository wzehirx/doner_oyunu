package com.doner.oyunu.ui;

import com.doner.oyunu.engine.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class DbgTest {
    @Test public void dbg() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        GameEngine e = h.engine();
        long u0 = android.os.SystemClock.uptimeMillis();
        for (int i = 0; i < 12; i++) {
            h.run(16);
            System.out.println("DBG up=" + (android.os.SystemClock.uptimeMillis()-u0) + " nano=" + System.nanoTime()/1000000 + " t=" + e.getElapsed());
        }
        h.idle(1000);
        System.out.println("DBG after idle1000 up=" + (android.os.SystemClock.uptimeMillis()-u0) + " t=" + e.getElapsed());
    }
}
