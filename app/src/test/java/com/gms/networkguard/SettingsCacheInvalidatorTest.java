package com.gms.networkguard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class SettingsCacheInvalidatorTest {
    @Test
    public void perKeyInvalidationDeduplicatesAndLeavesUnrelatedSettingsAlone() throws Exception {
        PerKeyRegistry registry = new PerKeyRegistry();
        SettingsCacheInvalidator invalidator = perKeyInvalidator();
        invalidator.record(registry, 10L, "adb_enabled");
        invalidator.record(registry, 10L, "adb_enabled");
        invalidator.record(registry, 10L, "airplane_mode_on");
        invalidator.record(registry, 20L, "enabled_accessibility_services");
        assertEquals(2, invalidator.invalidate());
        assertTrue(registry.calls.contains("10:adb_enabled"));
        assertTrue(registry.calls.contains("20:enabled_accessibility_services"));
        assertEquals(2, registry.calls.size());
    }

    @Test
    public void longKeysKeepUserAndDeviceBitsAndRegistryIdentity() throws Exception {
        PerKeyRegistry first = new PerKeyRegistry();
        PerKeyRegistry second = new PerKeyRegistry();
        SettingsCacheInvalidator invalidator = perKeyInvalidator();
        long namespace = 0x123456789L;
        invalidator.record(first, namespace, "development_settings_enabled");
        invalidator.record(second, namespace, "development_settings_enabled");
        assertEquals(2, invalidator.invalidate());
        assertEquals(namespace + ":development_settings_enabled", first.calls.get(0));
        assertEquals(first.calls, second.calls);
    }

    @Test
    public void legacyNamespaceTrackersInvalidateOnceWithoutInventingKeys() throws Exception {
        LegacyRegistry registry = new LegacyRegistry();
        SettingsCacheInvalidator invalidator = new SettingsCacheInvalidator(
                LegacyRegistry.class.getDeclaredMethod("incrementGeneration", int.class));
        invalidator.record(registry, 7, null);
        invalidator.record(registry, 7, null);
        assertEquals(1, invalidator.invalidate());
        assertEquals(7, registry.lastKey);
        assertEquals(1, registry.calls);
    }

    @Test
    public void perKeyTrackersIgnoreMissingNamesAndTheUnrelatedUnsetBucket() throws Exception {
        PerKeyRegistry registry = new PerKeyRegistry();
        SettingsCacheInvalidator invalidator = perKeyInvalidator();
        invalidator.record(registry, 0L, null);
        invalidator.record(registry, 0L, "");
        assertEquals(0, invalidator.invalidate());
        assertTrue(registry.calls.isEmpty());
    }

    @Test
    public void aBrokenTargetDoesNotPreventOtherTargetsFromRefreshing() throws Exception {
        PerKeyRegistry good = new PerKeyRegistry();
        PerKeyRegistry broken = new PerKeyRegistry();
        broken.fail = true;
        SettingsCacheInvalidator invalidator = perKeyInvalidator();
        invalidator.record(good, 0L, "adb_enabled");
        invalidator.record(broken, 0L, "adb_enabled");
        assertThrows(ReflectiveOperationException.class, invalidator::invalidate);
        assertEquals(1, good.calls.size());
    }

    @Test
    public void noTrackedClientsNeedsNoRefresh() throws Exception {
        SettingsCacheInvalidator invalidator = new SettingsCacheInvalidator(
                LegacyRegistry.class.getDeclaredMethod("incrementGeneration", int.class));
        assertEquals(0, invalidator.invalidate());
    }

    private static SettingsCacheInvalidator perKeyInvalidator() throws Exception {
        return new SettingsCacheInvalidator(PerKeyRegistry.class.getDeclaredMethod(
                "incrementGeneration", long.class, String.class));
    }

    public static final class PerKeyRegistry {
        final List<String> calls = new ArrayList<>();
        boolean fail;

        public void incrementGeneration(long key, String name) {
            if (fail) throw new IllegalStateException("unavailable");
            calls.add(key + ":" + name);
        }
    }

    public static final class LegacyRegistry {
        int calls;
        int lastKey;

        public void incrementGeneration(int key) {
            lastKey = key;
            calls++;
        }
    }
}
