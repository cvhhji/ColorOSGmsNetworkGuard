package com.gms.networkguard;

import android.content.Context;
import android.content.Intent;
import android.telephony.PreciseDataConnectionState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface.Hooker;

final class SystemHelperHooks {
    private final MainHook module;
    private int count;

    SystemHelperHooks(MainHook module) {
        this.module = module;
    }

    void install(ClassLoader loader) {
        try {
            Class<?> info = Class.forName("e1.b", false, loader);
            Constructor<?> constructor = info.getDeclaredConstructor();
            constructor.setAccessible(true);
            install(loader, "f1.p", "d", info, collectionHook(constructor), Context.class, String.class);
            for (String reader : new String[]{"f1.a", "f1.f"}) {
                install(loader, reader, "b", void.class, nullResult("device collector"), Context.class, info);
            }
        } catch (Throwable error) {
            module.err("SystemHelper device collector", error);
        }
        install(loader, "f1.a", "o", void.class, nullResult("telephony registration"), Context.class);
        install(loader, "f1.a", "c", boolean.class, falseResult("collection gate"), Context.class);
        install(loader, "f1.f", "u", boolean.class, falseResult("collection gate"), Context.class);
        for (String worker : new String[]{"e1.c", "e1.e", "e1.f", "e1.g"}) {
            install(loader, worker, "run", void.class, nullResult("worker " + worker));
        }
        install(loader, "g1.e", "b", void.class, nullResult("history cache"), String.class);
        install(loader, "g1.m", "c", void.class, nullResult("signal cache"), String.class);
        install(loader, "g1.f", "f", boolean.class, falseResult("HTTP upload"), String.class, byte[].class);
        install(loader, "g1.f", "g", boolean.class, falseResult("HTTPS upload"),
                String.class, byte[].class, byte[].class);
        install(loader, "e1.a", "onCallDisconnectCauseChanged", void.class,
                nullResult("call diagnostics"), int.class, int.class);
        install(loader, "e1.a", "onPreciseDataConnectionStateChanged", void.class,
                nullResult("data diagnostics"), PreciseDataConnectionState.class);
        String receiver = "com.mobiletools.systemhelper.control.GetSignalInfoReceiver";
        for (String gate : new String[]{"c", "d"}) {
            install(loader, receiver, gate, boolean.class, falseResult("location gate"), Context.class);
        }
        try {
            Method interval = Class.forName("g1.l", false, loader).getDeclaredMethod("m", Context.class);
            Method schedule = Class.forName("g1.a", false, loader)
                    .getDeclaredMethod("c", Context.class, long.class);
            if (!signature(interval, long.class, Context.class)
                    || !signature(schedule, void.class, Context.class, long.class)) {
                throw new NoSuchMethodException("SystemHelper signal scheduling signature");
            }
            interval.setAccessible(true);
            schedule.setAccessible(true);
            install(loader, receiver, "onReceive", void.class, locationHook(interval, schedule),
                    Context.class, Intent.class);
        } catch (Throwable error) {
            module.err("SystemHelper location scheduling", error);
        }
        module.info("SystemHelper reporting hooks=" + count);
    }

    private void install(ClassLoader loader, String owner, String name, Class<?> result,
                         Hooker hooker, Class<?>... arguments) {
        try {
            Method method = Class.forName(owner, false, loader).getDeclaredMethod(name, arguments);
            if (!signature(method, result, arguments)) throw new NoSuchMethodException(method.toGenericString());
            module.hook(method).setId("block-systemhelper-" + owner + "-" + name).intercept(hooker);
            count++;
        } catch (Throwable error) {
            module.err("SystemHelper " + owner + "#" + name, error);
        }
    }

    Hooker protect(String operation, Hooker blocked) {
        return module.guard(Feature.BLOCK_REPORTING, chain -> {
            module.info("blocked SystemHelper reporting " + operation);
            return blocked.intercept(chain);
        });
    }

    Hooker collectionHook(Constructor<?> constructor) {
        return protect("device information", chain -> constructor.newInstance());
    }

    Hooker locationHook(Method interval, Method schedule) {
        return protect("location collection", chain -> {
            Object context = chain.getArg(0);
            try {
                schedule.invoke(null, context, interval.invoke(null, context));
            } catch (Throwable error) {
                module.err("SystemHelper preserve signal schedule", error);
            }
            return null;
        });
    }

    private Hooker nullResult(String operation) {
        return protect(operation, chain -> null);
    }

    private Hooker falseResult(String operation) {
        return protect(operation, chain -> false);
    }

    static boolean signature(Method method, Class<?> result, Class<?>... arguments) {
        return method.getReturnType() == result && Arrays.equals(method.getParameterTypes(), arguments);
    }
}
