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
    int version = Feature.VERSION;
    int persistedVersion = Feature.VERSION;
    boolean readFails;
    final ArrayDeque<Boolean> outcomes = new ArrayDeque<>();
    final List<Integer> writes = new ArrayList<>();
    final List<SharedPreferences.OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
            SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getInt")) {
                    if (readFails) throw new IllegalStateException("disconnected");
                    return Feature.VERSION_KEY.equals(args[0]) ? version : cached;
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
        int[] nextVersion = {version};
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("putInt")) {
                        if (Feature.VERSION_KEY.equals(args[0])) nextVersion[0] = (Integer) args[1];
                        else candidate[0] = (Integer) args[1];
                        return proxy;
                    }
                    if (method.getName().equals("commit")) {
                        cached = candidate[0];
                        version = nextVersion[0];
                        writes.add(cached);
                        commits++;
                        boolean success = outcomes.isEmpty() || outcomes.removeFirst();
                        if (success) {
                            persisted = cached;
                            persistedVersion = version;
                            notifyChanged();
                        }
                        return success;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
