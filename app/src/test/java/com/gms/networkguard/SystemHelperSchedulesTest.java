package com.gms.networkguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SystemHelperSchedulesTest {
    private MainHook module;
    private TestPreferences preferences;
    private Context context;
    private AlarmManager alarms;
    private SystemHelperSchedules schedules;
    private static final List<String> restored = new ArrayList<>();

    @Before
    public void prepare() throws Exception {
        module = spy(new MainHook());
        doNothing().when(module).info(anyString());
        doNothing().when(module).err(anyString(), any(Throwable.class));
        preferences = new TestPreferences();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        config.bind(preferences.preferences);
        context = RuntimeEnvironment.getApplication();
        alarms = context.getSystemService(AlarmManager.class);
        restored.clear();
        schedules = new SystemHelperSchedules(module, Alarms.class, Intervals.class);
    }

    @Test
    public void blockedStartupCancelsOnlyKnownSystemHelperAlarms() {
        for (String receiver : SystemHelperSchedules.RECEIVERS) schedule(MainHook.SYSTEM_HELPER,
                SystemHelperSchedules.RECEIVER_PREFIX + receiver);
        schedule(MainHook.SYSTEM_HELPER, "com.mobiletools.systemhelper.OtherReceiver");
        schedule("com.other.application", SystemHelperSchedules.RECEIVER_PREFIX + "GetSignalInfoReceiver");
        assertEquals(8, shadowOf(alarms).getScheduledAlarms().size());
        schedules.attach(context);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, shadowOf(alarms).getScheduledAlarms().size());
        assertTrue(restored.isEmpty());
    }

    @Test
    public void cancellationDoesNotCreatePendingIntentsForAbsentTasks() {
        schedules.attach(context);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(shadowOf(alarms).getScheduledAlarms().isEmpty());
        for (String receiver : SystemHelperSchedules.RECEIVERS) {
            assertNull(PendingIntent.getBroadcast(context, 0, target(MainHook.SYSTEM_HELPER,
                    SystemHelperSchedules.RECEIVER_PREFIX + receiver),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE));
        }
    }

    @Test
    public void offStartupLeavesSchedulingUntouched() {
        preferences.cached = Feature.BLOCK_REPORTING.update(Feature.ALL, false);
        preferences.notifyChanged();
        schedule(MainHook.SYSTEM_HELPER, SystemHelperSchedules.RECEIVER_PREFIX + "GetSignalInfoReceiver");
        schedules.attach(context);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, shadowOf(alarms).getScheduledAlarms().size());
        assertTrue(restored.isEmpty());
    }

    @Test
    public void onOffOnRestoresOriginalIntervalsOnceWithoutInvokingReceivers() {
        schedules.attach(context);
        shadowOf(Looper.getMainLooper()).idle();
        preferences.cached = Feature.BLOCK_REPORTING.update(Feature.ALL, false);
        preferences.notifyChanged();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of("b:240000", "c:1700", "f:1800:2200", "e:700:1000", "a:600:300"), restored);
        preferences.notifyChanged();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(5, restored.size());
        schedule(MainHook.SYSTEM_HELPER, SystemHelperSchedules.RECEIVER_PREFIX + "GetSignalInfoReceiver");
        preferences.cached = Feature.ALL;
        preferences.notifyChanged();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(shadowOf(alarms).getScheduledAlarms().isEmpty());
        assertEquals(5, restored.size());
    }

    @Test
    public void otherFeatureChangesDoNotRestoreOrCancelReportingTimers() {
        schedules.attach(context);
        shadowOf(Looper.getMainLooper()).idle();
        schedule(MainHook.SYSTEM_HELPER, SystemHelperSchedules.RECEIVER_PREFIX + "GetSignalInfoReceiver");
        preferences.cached = Feature.HIDE_STATE.update(Feature.ALL, false);
        preferences.notifyChanged();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, shadowOf(alarms).getScheduledAlarms().size());
        assertTrue(restored.isEmpty());
    }

    @Test
    public void receiverOrSchedulerSignatureMismatchIsRejected() {
        assertThrows(NoSuchMethodException.class,
                () -> new SystemHelperSchedules(module, WrongAlarms.class, Intervals.class));
    }

    private Intent target(String pkg, String receiver) {
        return new Intent().setClassName(pkg, receiver);
    }

    private void schedule(String pkg, String receiver) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 100000,
                PendingIntent.getBroadcast(context, 0, target(pkg, receiver),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    public static final class Alarms {
        public static void a(Context c, long first, long second) { restored.add("a:" + first + ":" + second); }
        public static void b(Context c, long interval) { restored.add("b:" + interval); }
        public static void c(Context c, long interval) { restored.add("c:" + interval); }
        public static void e(Context c, long first, long second) { restored.add("e:" + first + ":" + second); }
        public static void f(Context c, long first, long second) { restored.add("f:" + first + ":" + second); }
    }

    public static final class Intervals {
        public static long m(Context c) { return 1700; }
        public static long h(Context c) { return 1800; }
        public static long l(Context c) { return 2200; }
        public static long g(Context c) { return 700; }
        public static long j(Context c) { return 1000; }
        public static long f(Context c) { return 600; }
        public static long c(Context c) { return 300; }
    }

    public static final class WrongAlarms {
        public static boolean b(Context c, long interval) { return true; }
    }
}
