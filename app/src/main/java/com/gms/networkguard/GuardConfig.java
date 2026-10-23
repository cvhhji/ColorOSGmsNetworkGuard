package com.gms.networkguard;

import android.content.SharedPreferences;

final class GuardConfig {
    private volatile SharedPreferences preferences;

    void bind(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    boolean isBound() {
        return preferences != null;
    }

    int mask() {
        SharedPreferences current = preferences;
        if (current == null) return Feature.ALL;
        try {
            return current.getInt(Feature.MASK_KEY, Feature.ALL);
        } catch (RuntimeException error) {
            return Feature.ALL;
        }
    }

    boolean enabled(Feature feature) {
        return feature.enabled(mask());
    }

    int visibilityMask() {
        return enabled(Feature.HIDE_STATE) ? StateVisibility.ALL : 0;
    }
}
