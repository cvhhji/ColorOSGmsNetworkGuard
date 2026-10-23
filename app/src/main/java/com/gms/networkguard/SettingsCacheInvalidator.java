package com.gms.networkguard;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SettingsCacheInvalidator {
    private final Set<Target> targets = ConcurrentHashMap.newKeySet();
    private final Method increment;

    SettingsCacheInvalidator(Method increment) {
        this.increment = increment;
        increment.setAccessible(true);
    }

    void record(Object registry, Number namespace, String name) {
        if (increment.getParameterCount() == 2
                && StateVisibility.hiddenValue(name, StateVisibility.ALL) == null) return;
        targets.add(new Target(registry, namespace, name));
    }

    int invalidate() throws ReflectiveOperationException {
        int count = 0;
        ReflectiveOperationException failure = null;
        for (Target target : targets) {
            try {
                if (increment.getParameterCount() == 1) {
                    increment.invoke(target.registry, target.namespace);
                } else {
                    increment.invoke(target.registry, target.namespace, target.name);
                }
                count++;
            } catch (ReflectiveOperationException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
        return count;
    }

    private static final class Target {
        final Object registry;
        final Number namespace;
        final String name;

        Target(Object registry, Number namespace, String name) {
            this.registry = registry;
            this.namespace = namespace;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Target)) return false;
            Target target = (Target) other;
            return registry == target.registry && namespace.equals(target.namespace)
                    && Objects.equals(name, target.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(registry), namespace, name);
        }
    }
}
