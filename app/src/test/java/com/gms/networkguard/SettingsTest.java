package com.gms.networkguard;

import org.junit.Test;

import static org.junit.Assert.*;

public final class SettingsTest {
    @Test
    public void fourIndependentBitsCoverEveryCombination() {
        assertEquals(4, Feature.values().length);
        assertEquals(15, Feature.ALL);
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            for (Feature feature : Feature.values()) {
                assertEquals((mask & feature.bit) != 0, feature.enabled(mask));
                assertTrue(feature.enabled(feature.update(mask, true)));
                assertFalse(feature.enabled(feature.update(mask, false)));
                assertEquals(mask & ~feature.bit, feature.update(mask, true) & ~feature.bit);
                assertEquals(mask & ~feature.bit, feature.update(mask, false) & ~feature.bit);
                assertEquals(1 << 20, feature.update(mask | (1 << 20), false) & (1 << 20));
            }
        }
    }

    @Test
    public void defaultsPreserveAllExistingFeatures() {
        GuardConfig config = new GuardConfig();
        assertFalse(config.isBound());
        for (Feature feature : Feature.values()) assertTrue(config.enabled(feature));
        assertEquals(StateVisibility.ALL, config.visibilityMask());
        TestPreferences prefs = new TestPreferences();
        config.bind(prefs.preferences);
        assertTrue(config.isBound());
        prefs.cached = Feature.HIDE_STATE.update(Feature.ALL, false);
        assertEquals(0, config.visibilityMask());
        assertTrue(config.enabled(Feature.ANTI_FRAUD));
        prefs.readFails = true;
        assertEquals(Feature.ALL, config.mask());
    }

    @Test
    public void savesMergeAgainstConfirmedValuesAndPersistAllFour() {
        RemoteSettings settings = new RemoteSettings();
        TestPreferences prefs = new TestPreferences();
        Object owner = new Object();
        assertEquals(Feature.ALL, settings.read(owner, prefs.preferences));
        int expected = Feature.ALL;
        for (Feature feature : Feature.values()) {
            assertTrue(settings.change(owner, prefs.preferences, feature, false));
            expected = feature.update(expected, false);
            assertEquals(expected, prefs.persisted);
            assertEquals(expected, settings.read(owner, prefs.preferences));
        }
        assertEquals(0, prefs.persisted);
        for (Feature feature : Feature.values()) {
            assertTrue(settings.change(owner, prefs.preferences, feature, true));
        }
        assertEquals(Feature.ALL, prefs.persisted);
        assertEquals(8, prefs.commits);
    }

    @Test
    public void visibilityChangesRefreshCachesInBothDirectionsOnlyOnce() {
        GuardConfig config = new GuardConfig();
        TestPreferences prefs = new TestPreferences();
        config.bind(prefs.preferences);
        int[] refreshes = {0};
        config.onVisibilityChanged(() -> refreshes[0]++);
        prefs.cached = Feature.HIDE_STATE.update(Feature.ALL, false);
        prefs.notifyChanged();
        prefs.notifyChanged();
        assertEquals(1, refreshes[0]);
        prefs.cached = Feature.ALL;
        prefs.notifyChanged();
        assertEquals(2, refreshes[0]);
    }

    @Test
    public void otherFeatureChangesDoNotInvalidateVisibilityCaches() {
        GuardConfig config = new GuardConfig();
        TestPreferences prefs = new TestPreferences();
        config.bind(prefs.preferences);
        int[] refreshes = {0};
        config.onVisibilityChanged(() -> refreshes[0]++);
        for (Feature feature : Feature.values()) {
            if (feature == Feature.HIDE_STATE) continue;
            prefs.cached = feature.update(prefs.cached, false);
            prefs.notifyChanged();
        }
        assertEquals(0, refreshes[0]);
    }

    @Test
    public void unchangedValueDoesNotWrite() {
        RemoteSettings settings = new RemoteSettings();
        TestPreferences prefs = new TestPreferences();
        assertTrue(settings.change(new Object(), prefs.preferences, Feature.ANTI_FRAUD, true));
        assertEquals(0, prefs.commits);
    }

    @Test
    public void failedCommitRollsBackOptimisticRemoteCache() {
        RemoteSettings settings = new RemoteSettings();
        TestPreferences prefs = new TestPreferences();
        Object owner = new Object();
        prefs.outcomes.add(false);
        prefs.outcomes.add(true);
        assertFalse(settings.change(owner, prefs.preferences, Feature.ANTI_FRAUD, false));
        assertEquals(Feature.ALL, settings.read(owner, prefs.preferences));
        assertEquals(Feature.ALL, prefs.cached);
        assertEquals(Feature.ALL, prefs.persisted);
        assertEquals(2, prefs.commits);
    }

    @Test
    public void unconfirmedRollbackBlocksUntilRecovery() {
        RemoteSettings settings = new RemoteSettings();
        TestPreferences prefs = new TestPreferences();
        Object owner = new Object();
        prefs.outcomes.add(false);
        prefs.outcomes.add(false);
        assertThrows(RemoteSettings.UnconfirmedWriteException.class,
                () -> settings.change(owner, prefs.preferences, Feature.HIDE_STATE, false));
        assertThrows(RemoteSettings.UnconfirmedWriteException.class,
                () -> settings.read(owner, prefs.preferences));
        assertEquals(2, prefs.commits);
        settings.recover(owner, prefs.preferences);
        assertEquals(Feature.ALL, settings.read(owner, prefs.preferences));
        assertEquals(Feature.ALL, prefs.persisted);
        assertEquals(3, prefs.commits);
    }

    @Test
    public void reconnectReadsNewServiceInsteadOfOldOptimisticCache() {
        RemoteSettings settings = new RemoteSettings();
        TestPreferences old = new TestPreferences();
        old.outcomes.add(false);
        old.outcomes.add(false);
        assertThrows(RemoteSettings.UnconfirmedWriteException.class,
                () -> settings.change(new Object(), old.preferences, Feature.HIDE_STATE, false));
        TestPreferences next = new TestPreferences();
        next.cached = Feature.GOOGLE_NETWORK.bit;
        assertEquals(Feature.GOOGLE_NETWORK.bit, settings.read(new Object(), next.preferences));
        assertEquals(0, next.commits);
    }

    @Test
    public void activationRequiresActualMatchingSystemTarget() {
        assertEquals(ActivationStatus.INACTIVE, ActivationStatus.resolve(false, 58, 58, true, false));
        assertEquals(ActivationStatus.ACTIVE, ActivationStatus.resolve(true, 58, 58, true, false));
        assertEquals(ActivationStatus.RESTART_REQUIRED, ActivationStatus.resolve(true, 58, 57, true, false));
        assertEquals(ActivationStatus.RESTART_REQUIRED, ActivationStatus.resolve(true, 58, 58, false, true));
        assertEquals(ActivationStatus.UNKNOWN, ActivationStatus.resolve(true, 58, 0, true, false));
        assertEquals(ActivationStatus.UNKNOWN, ActivationStatus.resolve(true, 58, 58, false, false));
    }
}
