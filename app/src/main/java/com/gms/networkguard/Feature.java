package com.gms.networkguard;

import android.content.SharedPreferences;

enum Feature {
    GOOGLE_NETWORK(1),
    ANTI_FRAUD(1 << 1),
    HIDE_FRAUD_ENTRIES(1 << 2),
    HIDE_STATE(1 << 3),
    BLOCK_REPORTING(1 << 4);

    static final String PREFERENCES = "guard_features";
    static final String MASK_KEY = "feature_mask";
    static final String VERSION_KEY = "feature_version";
    static final int VERSION = 2;
    static final int ALL = (1 << 5) - 1;
    final int bit;

    Feature(int bit) {
        this.bit = bit;
    }

    static int readMask(SharedPreferences preferences) {
        int mask = preferences.getInt(MASK_KEY, ALL);
        return preferences.getInt(VERSION_KEY, 0) < VERSION
                ? mask | BLOCK_REPORTING.bit : mask;
    }

    boolean enabled(int mask) {
        return (mask & bit) != 0;
    }

    int update(int mask, boolean enabled) {
        return enabled ? mask | bit : mask & ~bit;
    }
}
