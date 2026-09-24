package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static com.liskovsoft.smartyoutubetv2.common.misc.StartupPlaybackMonitor.Result.*;

public class StartupPlaybackMonitorTest {
    @Test public void repeatedSourceErrorsSurviveReloadAndSeekRestoration() {
        StartupPlaybackMonitor m = monitor();
        assertEquals(NONE, m.onSourceError(1000, 2000));
        m.begin("video");
        m.onReload();
        m.resetObservation();
        assertEquals(RECOVER, m.onSourceError(5000, 2000));
    }

    @Test public void unrelatedSourceErrorsDoNotRecover() {
        StartupPlaybackMonitor m = monitor();
        assertEquals(NONE, m.onSourceError(1000, 2000));
        assertEquals(NONE, m.onSourceError(12000, 2000));
        m.begin("other");
        assertEquals(NONE, m.onSourceError(13000, 2000));
        assertEquals(NONE, m.onSourceError(14000, 30000));
    }

    @Test public void sourceErrorsRespectSharedCooldownAndAttemptLimit() {
        StartupPlaybackMonitor m = monitor();
        for (int i = 0; i < 3; i++) {
            long now = i * 20000L;
            assertEquals(NONE, m.onSourceError(now, 2000));
            assertEquals(RECOVER, m.onSourceError(now + 1000, 2000));
            assertEquals(NONE, m.onSourceError(now + 2000, 2000));
        }
        assertEquals(NONE, m.onSourceError(80000, 2000));
        assertEquals(NONE, m.onSourceError(81000, 2000));
    }

    private StartupPlaybackMonitor monitor() {
        StartupPlaybackMonitor monitor = new StartupPlaybackMonitor();
        monitor.begin("video");
        return monitor;
    }

    @Test public void twoRollbacksRecoverBeforeTenSeconds() {
        StartupPlaybackMonitor m = monitor();
        assertEquals(NONE, m.sample(0, 0, true));
        assertEquals(NONE, m.sample(2000, 2000, true));
        assertEquals(NONE, m.sample(2500, 0, true));
        assertEquals(NONE, m.sample(4500, 2000, true));
        assertEquals(RECOVER, m.sample(5000, 0, true));
    }

    @Test public void frozenStartupRecoversAfterTenSeconds() {
        StartupPlaybackMonitor m = monitor();
        m.sample(0, 2000, true);
        assertEquals(NONE, m.sample(9999, 2000, false));
        assertEquals(RECOVER, m.sample(10000, 2000, false));
    }

    @Test public void onlyContinuousProgressConfirmsClient() {
        StartupPlaybackMonitor m = monitor();
        for (int i = 0; i <= 10; i++) assertEquals(NONE, m.sample(i * 1000, i * 1000, true));
        assertEquals(HEALTHY, m.sample(11000, 11000, true));
        assertEquals(NONE, m.sample(12000, 12000, true));
    }

    @Test public void pausesDoNotConfirmClient() {
        StartupPlaybackMonitor m = monitor();
        m.sample(0, 15000, false);
        assertEquals(NONE, m.sample(20000, 15000, false));
    }

    @Test public void explicitSeeksClearRollbackEvidence() {
        StartupPlaybackMonitor m = monitor();
        m.sample(0, 3000, true);
        m.sample(1000, 0, true);
        m.resetObservation();
        m.sample(2000, 3000, true);
        assertEquals(NONE, m.sample(3000, 0, true));
    }

    @Test public void distantRollbacksAreNotAStartupLoop() {
        StartupPlaybackMonitor m = monitor();
        m.sample(0, 3000, true);
        m.sample(1000, 0, true);
        m.sample(11000, 4000, true);
        assertEquals(NONE, m.sample(12000, 0, true));
    }

    @Test public void laterPlaybackIsNotTreatedAsStartup() {
        StartupPlaybackMonitor m = monitor();
        m.sample(0, 60000, true);
        m.sample(1000, 50000, true);
        m.sample(2000, 60000, true);
        assertEquals(NONE, m.sample(3000, 50000, true));
    }

    @Test public void cooldownAndLimitSurviveSameVideoReloads() {
        StartupPlaybackMonitor m = monitor();
        for (int i = 0; i < 3; i++) {
            long base = i * 30000L;
            m.begin("video");
            m.sample(base, 0, false);
            assertEquals(RECOVER, m.sample(base + 10000, 0, false));
            assertEquals(NONE, m.sample(base + 11000, 0, false));
        }
        m.begin("video");
        m.sample(90000, 0, false);
        assertEquals(NONE, m.sample(100000, 0, false));
        m.begin("other");
        m.sample(110000, 0, false);
        assertEquals(RECOVER, m.sample(120000, 0, false));
    }
}
