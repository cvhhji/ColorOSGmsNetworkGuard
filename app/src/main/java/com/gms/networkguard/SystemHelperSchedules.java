package com.gms.networkguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class SystemHelperSchedules {
    static final String RECEIVER_PREFIX = "com.mobiletools.systemhelper.control.";
    static final String[] RECEIVERS = {
            "OnBootReceiver", "SIMStateReceiver", "ScheduleRegisterReceiver",
            "AppInfoRegisterReceiver", "SignalInfoRegisterReceiver", "GetSignalInfoReceiver"
    };

    private final MainHook module;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Method[] schedules;
    private final Method[] intervals;
    private Context context;
    private boolean suspended;

    SystemHelperSchedules(MainHook module, Class<?> alarms, Class<?> preferences)
            throws NoSuchMethodException {
        this.module = module;
        schedules = new Method[]{
                method(alarms, "b", void.class, Context.class, long.class),
                method(alarms, "c", void.class, Context.class, long.class),
                method(alarms, "f", void.class, Context.class, long.class, long.class),
                method(alarms, "e", void.class, Context.class, long.class, long.class),
                method(alarms, "a", void.class, Context.class, long.class, long.class)
        };
        intervals = new Method[]{
                method(preferences, "m", long.class, Context.class),
                method(preferences, "h", long.class, Context.class),
                method(preferences, "l", long.class, Context.class),
                method(preferences, "g", long.class, Context.class),
                method(preferences, "j", long.class, Context.class),
                method(preferences, "f", long.class, Context.class),
                method(preferences, "c", long.class, Context.class)
        };
        module.onReportingChanged(() -> handler.post(this::update));
    }

    void attach(Context context) {
        this.context = context.getApplicationContext();
        if (Looper.myLooper() == handler.getLooper()) update();
        else handler.post(this::update);
    }

    void update() {
        if (context == null) return;
        if (module.reportingBlocked()) {
            suspended = true;
            cancel();
        } else if (suspended) {
            suspended = false;
            restore();
        }
    }

    private void cancel() {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            module.err("SystemHelper alarm service unavailable", new IllegalStateException());
            return;
        }
        int count = 0;
        for (String receiver : RECEIVERS) {
            try {
                Intent intent = new Intent().setClassName(MainHook.SYSTEM_HELPER, RECEIVER_PREFIX + receiver);
                PendingIntent operation = PendingIntent.getBroadcast(context, 0, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (operation != null) {
                    alarms.cancel(operation);
                    count++;
                }
            } catch (RuntimeException error) {
                module.err("SystemHelper cancel alarm " + receiver, error);
            }
        }
        module.info("SystemHelper alarm cancellation requests=" + count);
    }

    private void restore() {
        int index = 0;
        for (int i = 0; i < schedules.length; i++) {
            try {
                if (i == 0) schedules[i].invoke(null, context, 240000L);
                else if (i == 1) schedules[i].invoke(null, context, intervals[index++].invoke(null, context));
                else {
                    Method first = intervals[index++];
                    Method second = intervals[index++];
                    schedules[i].invoke(null, context, first.invoke(null, context), second.invoke(null, context));
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                module.err("SystemHelper restore alarm " + schedules[i].getName(), error);
            }
        }
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        if (!Modifier.isStatic(method.getModifiers()) || !SystemHelperHooks.signature(method, result, parameters)) {
            throw new NoSuchMethodException(owner.getName() + "#" + name);
        }
        method.setAccessible(true);
        return method;
    }
}
