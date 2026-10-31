package com.gms.networkguard;

import android.app.job.JobParameters;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import io.github.libxposed.api.XposedInterface.Hooker;

final class NumberRecognitionHooks {
    private final MainHook module;

    NumberRecognitionHooks(MainHook module) {
        this.module = module;
    }

    void install(ClassLoader loader) {
        int moduleCount = 0;
        int jobCount = 0;
        int updaterCount = 0;
        try {
            Class<?> target = Class.forName("com.oplus.nationalantifraud.a", false, loader);
            for (Method method : target.getDeclaredMethods()) {
                if (!isModuleMethod(method)) continue;
                module.hook(method).setId("disable-number-recognition-antifraud-" + method.getName())
                        .intercept(moduleHook(method));
                moduleCount++;
            }
        } catch (Throwable error) {
            module.err("NumberRecognition anti-fraud module", error);
        }
        try {
            Class<?> target = Class.forName(
                    "com.oplus.nationalantifraud.parse.ParseJobService", false, loader);
            for (Method method : target.getDeclaredMethods()) {
                if (!isJobMethod(method)) continue;
                module.hook(method).setId("disable-number-recognition-antifraud-job-" + method.getName())
                        .intercept(module.guard(Feature.ANTI_FRAUD, chain -> {
                            module.info("blocked NumberRecognition anti-fraud job " + method.getName());
                            return false;
                        }));
                jobCount++;
            }
        } catch (Throwable error) {
            module.err("NumberRecognition anti-fraud job", error);
        }
        try {
            Class<?> target = Class.forName("x4.a", false, loader);
            for (Method method : target.getDeclaredMethods()) {
                if (!isUpdaterMethod(method)) continue;
                module.hook(method).setId("disable-number-recognition-antifraud-update")
                        .intercept(module.guard(Feature.ANTI_FRAUD, chain -> null));
                updaterCount++;
            }
        } catch (Throwable error) {
            module.err("NumberRecognition anti-fraud updater", error);
        }
        module.info("NumberRecognition anti-fraud module hooks=" + moduleCount
                + " job hooks=" + jobCount + " updater hooks=" + updaterCount);
    }

    Hooker moduleHook(Method method) {
        if (!isModuleMethod(method)) throw new IllegalArgumentException(method.toGenericString());
        Object result = method.getReturnType() == boolean.class
                ? "h".equals(method.getName()) : null;
        return module.guard(Feature.ANTI_FRAUD, chain -> result);
    }

    static boolean isModuleMethod(Method method) {
        switch (method.getName()) {
            case "a":
            case "i":
                return signature(method, boolean.class, String.class);
            case "b":
                return signature(method, void.class, String.class, boolean.class);
            case "c":
                return signature(method, void.class, Context.class, HashMap.class);
            case "d":
                return signature(method, void.class, Context.class, String.class, Integer.class);
            case "e":
                return signature(method, boolean.class, Context.class, String.class, String.class);
            case "f":
                return signature(method, void.class, Context.class);
            case "g":
                return signature(method, void.class);
            case "h":
                return signature(method, boolean.class, Context.class);
            default:
                return false;
        }
    }

    static boolean isJobMethod(Method method) {
        return ("onStartJob".equals(method.getName()) || "onStopJob".equals(method.getName()))
                && signature(method, boolean.class, JobParameters.class);
    }

    static boolean isUpdaterMethod(Method method) {
        return "l".equals(method.getName()) && signature(method, void.class);
    }

    private static boolean signature(Method method, Class<?> result, Class<?>... arguments) {
        return method.getReturnType() == result
                && Arrays.equals(method.getParameterTypes(), arguments);
    }
}
