package com.gms.networkguard;

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface.Hooker;

final class TelecomAntiFraudHooks {
    static final String[][] PROFILES = {
            {"com.android.server.telecom.t5", "com.android.server.telecom.oplus.o1",
                    "com.android.server.telecom.c2", "v3"},
            {"com.android.server.telecom.p5", "com.android.server.telecom.oplus.m1",
                    "com.android.server.telecom.z1", "t3"}
    };
    private final MainHook module;

    TelecomAntiFraudHooks(MainHook module) {
        this.module = module;
    }

    void install(ClassLoader loader) {
        String failure = "";
        for (String[] profile : PROFILES) {
            Method[] methods;
            try {
                Class<?> call = Class.forName("com.android.server.telecom.Call", false, loader);
                methods = new Method[]{
                        Class.forName(profile[0], false, loader).getDeclaredMethod("C", call),
                        Class.forName(profile[1], false, loader).getDeclaredMethod("b", Context.class),
                        Class.forName(profile[2], false, loader)
                                .getDeclaredMethod(profile[3], String.class, Bundle.class, Boolean.class)
                };
                if (!matches(methods[0], false, void.class, call)
                        || methods[1].getReturnType() != Boolean.class
                        || (profile == PROFILES[0] && !Modifier.isStatic(methods[1].getModifiers()))
                        || !matches(methods[2], false, void.class, String.class, Bundle.class, Boolean.class)) {
                    throw new NoSuchMethodException("Telecom profile signature " + profile[0]);
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                failure += profile[0] + ": " + error.getMessage() + "; ";
                continue;
            }
            int count = 0;
            try {
                module.hook(methods[0]).setId("disable-telecom-antifraud-center-call-intercept")
                        .intercept(module.guard(Feature.ANTI_FRAUD, chain -> {
                            module.info("prevented Telecom anti-fraud-center call interception");
                            return null;
                        }));
                count++;
                module.hook(methods[1]).setId("disable-telecom-antifraud-sequence-support")
                        .intercept(module.guard(Feature.ANTI_FRAUD, chain -> Boolean.FALSE));
                count++;
                module.hook(methods[2]).setId("disable-telecom-ai-antifraud-events").intercept(eventHook());
                count++;
            } catch (Throwable error) {
                module.err("Telecom anti-fraud profile " + profile[0], error);
            }
            module.info("Telecom anti-fraud hooks=" + count + " profile=" + profile[0]);
            return;
        }
        module.err("Telecom anti-fraud profile not found", new NoSuchMethodException(failure));
        module.info("Telecom anti-fraud hooks=0");
    }

    Hooker eventHook() {
        return module.guard(Feature.ANTI_FRAUD, chain -> {
            Object event = chain.getArg(0);
            if ("android.telecom.event.AI_CALL_ANTI_FRAUD".equals(event)
                    || "android.telecom.event.AI_voice_detect".equals(event)) {
                module.info("blocked Telecom anti-fraud event " + event);
                return null;
            }
            return chain.proceed();
        });
    }

    static boolean matches(Method method, boolean isStatic, Class<?> result, Class<?>... arguments) {
        return Modifier.isStatic(method.getModifiers()) == isStatic
                && method.getReturnType() == result
                && Arrays.equals(method.getParameterTypes(), arguments);
    }
}
