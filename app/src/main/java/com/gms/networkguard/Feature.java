package com.gms.networkguard;

enum Feature {
    GOOGLE_NETWORK(1),
    ANTI_FRAUD(1 << 1),
    HIDE_FRAUD_ENTRIES(1 << 2),
    HIDE_STATE(1 << 3);

    static final String PREFERENCES = "guard_features";
    static final String MASK_KEY = "feature_mask";
    static final int ALL = (1 << 4) - 1;
    final int bit;

    Feature(int bit) {
        this.bit = bit;
    }

    boolean enabled(int mask) {
        return (mask & bit) != 0;
    }

    int update(int mask, boolean enabled) {
        return enabled ? mask | bit : mask & ~bit;
    }
}
