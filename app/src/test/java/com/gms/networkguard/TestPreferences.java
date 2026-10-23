package com.gms.networkguard;

import android.content.SharedPreferences;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class TestPreferences {
    int cached = Feature.ALL;
    int persisted = Feature.ALL;
    int commits;
    boolean readFails;
    final ArrayDeque<Boolean> outcomes = new ArrayDeque<>();
    final List<Integer> writes = new ArrayList<>();
    final List<SharedPreferences.OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
            SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getInt")) {
                    if (readFails) throw new IllegalStateException("disconnected");
                    return cached;
                }
                if (method.getName().equals("edit")) return editor();
                if (method.getName().equals("registerOnSharedPreferenceChangeListener")) {
                    listeners.add((SharedPreferences.OnSharedPreferenceChangeListener) args[0]);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });

    void notifyChanged() {
        for (SharedPreferences.OnSharedPreferenceChangeListener listener : listeners) {
            listener.onSharedPreferenceChanged(preferences, Feature.MASK_KEY);
        }
    }

    private SharedPreferences.Editor editor() {
        int[] candidate = {cached};
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("putInt")) {
                        candidate[0] = (Integer) args[1];
                        return proxy;
                    }
                    if (method.getName().equals("commit")) {
                        cached = candidate[0];
                        writes.add(cached);
                        commits++;
                        boolean success = outcomes.isEmpty() || outcomes.removeFirst();
                        if (success) {
                            persisted = cached;
                            notifyChanged();
                        }
                        return success;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
