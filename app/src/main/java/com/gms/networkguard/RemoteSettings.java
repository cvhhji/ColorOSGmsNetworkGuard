package com.gms.networkguard;

import android.content.SharedPreferences;

final class RemoteSettings {
    private Object owner;
    private SharedPreferences preferences;
    private int confirmed = Feature.ALL;
    private boolean uncertain;

    int read(Object identity, SharedPreferences next) {
        if (owner != identity) {
            int mask = next.getInt(Feature.MASK_KEY, Feature.ALL);
            preferences = next;
            confirmed = mask;
            uncertain = false;
            owner = identity;
        }
        if (uncertain) throw new UnconfirmedWriteException();
        return confirmed;
    }

    boolean change(Object identity, SharedPreferences next, Feature feature, boolean enabled) {
        read(identity, next);
        int mask = feature.update(confirmed, enabled);
        if (mask == confirmed) return true;
        uncertain = true;
        if (commit(mask)) {
            confirmed = mask;
            uncertain = false;
            return true;
        }
        recover();
        return false;
    }

    void recover() {
        if (uncertain) {
            if (!commit(confirmed)) throw new UnconfirmedWriteException();
            uncertain = false;
        }
    }

    void recover(Object identity, SharedPreferences next) {
        if (owner != identity) read(identity, next);
        else recover();
    }

    private boolean commit(int mask) {
        try {
            return preferences.edit().putInt(Feature.MASK_KEY, mask).commit();
        } catch (RuntimeException error) {
            return false;
        }
    }

    static final class UnconfirmedWriteException extends IllegalStateException {
        UnconfirmedWriteException() {
            super("Configuration write could not be confirmed");
        }
    }
}
