package com.gms.networkguard;

import android.content.Context;
import android.os.Bundle;

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
public final class TelecomAntiFraudHooksTest {
    @Test
    public void bothKnownProfilesKeepExactClassAndEventMappings() {
        assertArrayEquals(new String[]{"com.android.server.telecom.t5", "com.android.server.telecom.oplus.o1",
                "com.android.server.telecom.c2", "v3"}, TelecomAntiFraudHooks.PROFILES[0]);
        assertArrayEquals(new String[]{"com.android.server.telecom.p5", "com.android.server.telecom.oplus.m1",
                "com.android.server.telecom.z1", "t3"}, TelecomAntiFraudHooks.PROFILES[1]);
    }

    @Test
    public void eventFilteringPreservesEveryUnrelatedEventAndRestoresOriginalWhenOff() throws Throwable {
        MainHook module = spy(new MainHook());
        doNothing().when(module).info(anyString());
        TestPreferences preferences = new TestPreferences();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        config.bind(preferences.preferences);
        Hooker events = new TelecomAntiFraudHooks(module).eventHook();
        Object original = new Object();
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            preferences.cached = mask;
            for (String event : new String[]{"android.telecom.event.AI_CALL_ANTI_FRAUD",
                    "android.telecom.event.AI_voice_detect", "android.telecom.event.MUTE", null}) {
                Chain chain = mock(Chain.class);
                when(chain.getArg(0)).thenReturn(event);
                when(chain.proceed()).thenReturn(original);
                boolean blocked = Feature.ANTI_FRAUD.enabled(mask) && event != null && !event.endsWith("MUTE");
                assertSame(blocked ? null : original, events.intercept(chain));
                verify(chain, times(blocked ? 0 : 1)).proceed();
            }
        }
    }

    @Test
    public void profileSelectionRejectsWrongStaticnessReturnTypeAndArguments() throws Exception {
        assertTrue(TelecomAntiFraudHooks.matches(Fixture.class.getMethod("b", Context.class),
                true, Boolean.class, Context.class));
        assertFalse(TelecomAntiFraudHooks.matches(Fixture.class.getMethod("b", Context.class),
                false, Boolean.class, Context.class));
        assertFalse(TelecomAntiFraudHooks.matches(Fixture.class.getMethod("b", Context.class),
                true, boolean.class, Context.class));
        assertTrue(TelecomAntiFraudHooks.matches(Fixture.class.getMethod("v3", String.class, Bundle.class, Boolean.class),
                false, void.class, String.class, Bundle.class, Boolean.class));
        assertFalse(TelecomAntiFraudHooks.matches(Fixture.class.getMethod("v3", String.class, Bundle.class, boolean.class),
                false, void.class, String.class, Bundle.class, Boolean.class));
    }

    public abstract static class Fixture {
        public static Boolean b(Context context) { return true; }
        public abstract void v3(String event, Bundle extras, Boolean remote);
        public abstract void v3(String event, Bundle extras, boolean remote);
    }
}
