package com.gms.networkguard;

import android.content.SharedPreferences;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class GuardConfig {
    private volatile SharedPreferences preferences;
    private final CopyOnWriteArrayList<Runnable> visibilityListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean previousVisibility = new AtomicBoolean(true);
    private final CopyOnWriteArrayList<Runnable> reportingListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean previousReporting = new AtomicBoolean(true);
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (changed, key) -> {
        if (changed == preferences && (key == null || Feature.MASK_KEY.equals(key))) {
            updateVisibility();
            updateReporting();
        }
    };

    void bind(SharedPreferences preferences) {
        this.preferences = preferences;
        previousVisibility.set(enabled(Feature.HIDE_STATE));
        previousReporting.set(enabled(Feature.BLOCK_REPORTING));
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        updateVisibility();
        updateReporting();
    }

    boolean isBound() {
        return preferences != null;
    }

    int mask() {
        SharedPreferences current = preferences;
        if (current == null) return Feature.ALL;
        try {
            return Feature.readMask(current);
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

    void onVisibilityChanged(Runnable listener) {
        visibilityListeners.add(listener);
    }

    void onReportingChanged(Runnable listener) {
        reportingListeners.add(listener);
    }

    private void updateReporting() {
        boolean enabled = enabled(Feature.BLOCK_REPORTING);
        if (previousReporting.getAndSet(enabled) != enabled) {
            for (Runnable listener : reportingListeners) listener.run();
        }
    }

    private void updateVisibility() {
        boolean enabled = enabled(Feature.HIDE_STATE);
        if (previousVisibility.getAndSet(enabled) != enabled) {
            for (Runnable listener : visibilityListeners) listener.run();
        }
    }
}
