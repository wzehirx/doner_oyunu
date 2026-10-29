package com.doner.oyunu.ui;

import com.doner.oyunu.engine.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.os.SystemClock;
import org.robolectric.shadows.ShadowLooper;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class DbgTest {
    @Test public void dbg() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        GameEngine e = h.engine();
        for (int i = 0; i < 6; i++) {
            long u0 = SystemClock.uptimeMillis(); float t0 = e.getElapsed();
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
            System.out.println("DBG pureIdle16: dUp=" + (SystemClock.uptimeMillis()-u0) + " dEngine=" + (e.getElapsed()-t0));
        }
        for (int i = 0; i < 3; i++) {
            long u0 = SystemClock.uptimeMillis(); float t0 = e.getElapsed();
            h.run(1000);
            System.out.println("DBG run1000: dUp=" + (SystemClock.uptimeMillis()-u0) + " dEngine=" + (e.getElapsed()-t0));
        }
    }
}
