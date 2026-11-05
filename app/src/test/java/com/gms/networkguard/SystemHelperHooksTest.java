package com.gms.networkguard;

import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SystemHelperHooksTest {
    private MainHook module;
    private TestPreferences preferences;
    private SystemHelperHooks hooks;
    private static Context scheduledContext;
    private static long scheduledInterval;
    private static int schedules;
    private static boolean schedulingFails;

    @Before
    public void prepare() {
        module = spy(new MainHook());
        doNothing().when(module).info(anyString());
        doNothing().when(module).err(anyString(), any(Throwable.class));
        preferences = new TestPreferences();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        config.bind(preferences.preferences);
        hooks = new SystemHelperHooks(module);
        schedules = 0;
        schedulingFails = false;
        scheduledContext = null;
    }

    @Test
    public void reportingUsesOnlyItsOwnSwitchForAllFeatureCombinations() throws Throwable {
        Hooker protectedCall = hooks.protect("transport", chain -> false);
        Object original = new Object();
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            preferences.cached = mask;
            Chain chain = mock(Chain.class);
            when(chain.proceed()).thenReturn(original);
            assertSame(Feature.BLOCK_REPORTING.enabled(mask) ? Boolean.FALSE : original,
                    protectedCall.intercept(chain));
            verify(chain, times(Feature.BLOCK_REPORTING.enabled(mask) ? 0 : 1)).proceed();
        }
    }

    @Test
    public void blockedCollectionReturnsFreshEmptyObjectsWithoutCallingCollector() throws Throwable {
        Hooker collector = hooks.collectionHook(Information.class.getConstructor());
        Chain chain = mock(Chain.class);
        Information first = (Information) collector.intercept(chain);
        first.value = "changed";
        Information second = (Information) collector.intercept(chain);
        assertNotSame(first, second);
        assertNull(second.value);
        verify(chain, never()).proceed();
        preferences.cached = Feature.BLOCK_REPORTING.update(Feature.ALL, false);
        Object original = new Object();
        when(chain.proceed()).thenReturn(original);
        assertSame(original, collector.intercept(chain));
        verify(chain).proceed();
    }

    @Test
    public void blockedLocationKeepsOriginalAlarmIntervalAndOffRestoresReceiver() throws Throwable {
        Hooker receiver = hooks.locationHook(SystemHelperHooksTest.class.getMethod("interval", Context.class),
                SystemHelperHooksTest.class.getMethod("schedule", Context.class, long.class));
        Context context = RuntimeEnvironment.getApplication();
        Object original = new Object();
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            preferences.cached = mask;
            int before = schedules;
            Chain chain = mock(Chain.class);
            when(chain.getArg(0)).thenReturn(context);
            when(chain.proceed()).thenReturn(original);
            if (Feature.BLOCK_REPORTING.enabled(mask)) {
                assertNull(receiver.intercept(chain));
                assertEquals(before + 1, schedules);
                assertSame(context, scheduledContext);
                assertEquals(1800000L, scheduledInterval);
                verify(chain, never()).proceed();
            } else {
                assertSame(original, receiver.intercept(chain));
                assertEquals(before, schedules);
                verify(chain).proceed();
            }
        }
    }

    @Test
    public void schedulingFailureNeverFallsBackToLocationCollection() throws Throwable {
        schedulingFails = true;
        Hooker receiver = hooks.locationHook(SystemHelperHooksTest.class.getMethod("interval", Context.class),
                SystemHelperHooksTest.class.getMethod("schedule", Context.class, long.class));
        Chain chain = mock(Chain.class);
        when(chain.getArg(0)).thenReturn(RuntimeEnvironment.getApplication());
        assertNull(receiver.intercept(chain));
        verify(chain, never()).proceed();
        verify(module).err(eq("SystemHelper preserve signal schedule"), any(Throwable.class));
    }

    @Test
    public void exactSignaturesRejectOtherOverloadsAndReturnTypes() throws Exception {
        assertTrue(SystemHelperHooks.signature(Receiver.class.getMethod("onReceive", Context.class, Intent.class),
                void.class, Context.class, Intent.class));
        assertFalse(SystemHelperHooks.signature(Receiver.class.getMethod("onReceive", Object.class, Intent.class),
                void.class, Context.class, Intent.class));
        assertFalse(SystemHelperHooks.signature(SystemHelperHooksTest.class.getMethod("interval", Context.class),
                boolean.class, Context.class));
    }

    public static long interval(Context context) {
        return 1800000L;
    }

    public static void schedule(Context context, long interval) {
        if (schedulingFails) throw new IllegalStateException("schedule unavailable");
        scheduledContext = context;
        scheduledInterval = interval;
        schedules++;
    }

    public static final class Information {
        public String value;
        public Information() {}
    }

    public abstract static class Receiver {
        public abstract void onReceive(Context context, Intent intent);
        public abstract void onReceive(Object context, Intent intent);
    }
}
