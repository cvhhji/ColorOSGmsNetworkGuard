package com.gms.networkguard;

enum ActivationStatus {
    CHECKING, ACTIVE, INACTIVE, RESTART_REQUIRED, UNKNOWN;

    static ActivationStatus resolve(boolean systemFound, long installed, long loaded,
                                    boolean upToDate, boolean stale) {
        if (!systemFound) return INACTIVE;
        if (stale || (loaded > 0 && installed != loaded)) return RESTART_REQUIRED;
        if (upToDate && installed == loaded && loaded > 0) return ACTIVE;
        return UNKNOWN;
    }
}
