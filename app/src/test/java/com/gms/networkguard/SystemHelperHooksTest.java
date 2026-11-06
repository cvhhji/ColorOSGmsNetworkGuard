package com.gms.networkguard;

import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
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

    @Before
    public void prepare() {
        module = spy(new MainHook());
        doNothing().when(module).info(anyString());
        doNothing().when(module).err(anyString(), any(Throwable.class));
        preferences = new TestPreferences();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        config.bind(preferences.preferences);
        hooks = new SystemHelperHooks(module);
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
    public void scheduledReceiversDoNotRunOrRenewWhileBlockedAndOffRestoresOriginal() throws Throwable {
        for (String name : SystemHelperSchedules.RECEIVERS) {
            Hooker receiver = hooks.scheduledReceiverHook(name);
            Object original = new Object();
            for (int mask = 0; mask <= Feature.ALL; mask++) {
                preferences.cached = mask;
                Chain chain = mock(Chain.class);
                when(chain.proceed()).thenReturn(original);
                assertSame(Feature.BLOCK_REPORTING.enabled(mask) ? null : original,
                        receiver.intercept(chain));
                verify(chain, times(Feature.BLOCK_REPORTING.enabled(mask) ? 0 : 1)).proceed();
            }
        }
    }

    @Test
    public void exactSignaturesRejectOtherOverloadsAndReturnTypes() throws Exception {
        assertTrue(SystemHelperHooks.signature(Receiver.class.getMethod("onReceive", Context.class, Intent.class),
                void.class, Context.class, Intent.class));
        assertFalse(SystemHelperHooks.signature(Receiver.class.getMethod("onReceive", Object.class, Intent.class),
                void.class, Context.class, Intent.class));
        assertFalse(SystemHelperHooks.signature(Information.class.getMethod("interval", Context.class),
                boolean.class, Context.class));
    }

    public static final class Information {
        public String value;
        public Information() {}
        public static long interval(Context context) { return 1800000L; }
    }

    public abstract static class Receiver {
        public abstract void onReceive(Context context, Intent intent);
        public abstract void onReceive(Object context, Intent intent);
    }
}
