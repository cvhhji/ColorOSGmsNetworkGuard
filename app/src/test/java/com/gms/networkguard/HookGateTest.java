package com.gms.networkguard;

import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class HookGateTest {
    @Test
    public void disabledFeaturesProceedToOriginalForEveryCombination() throws Throwable {
        MainHook module = new MainHook();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        TestPreferences prefs = new TestPreferences();
        config.bind(prefs.preferences);
        Method guard = MainHook.class.getDeclaredMethod("guard", Feature.class, Hooker.class);
        guard.setAccessible(true);
        Object original = new Object();
        Object intercepted = new Object();
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            prefs.cached = mask;
            for (Feature feature : Feature.values()) {
                Chain chain = mock(Chain.class);
                when(chain.proceed()).thenReturn(original);
                Hooker hooker = (Hooker) guard.invoke(module, feature, (Hooker) call -> intercepted);
                assertSame(feature.enabled(mask) ? intercepted : original, hooker.intercept(chain));
                verify(chain, times(feature.enabled(mask) ? 0 : 1)).proceed();
            }
        }
    }

    @Test
    public void serviceFilteringOnlyFollowsServiceSwitchAndPreservesOtherServices() {
        MainHook module = spy(new MainHook());
        doNothing().when(module).info(anyString());
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        TestPreferences prefs = new TestPreferences();
        config.bind(prefs.preferences);
        List<ResolveInfo> services = new ArrayList<>();
        ResolveInfo regular = service("com.android.dialer/.InCallService");
        services.add(regular);
        for (String component : MainHook.IN_CALL_ANTI_FRAUD_SERVICES) services.add(service(component));
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            prefs.cached = mask;
            Object filtered = module.filterAntiFraudInCallServices(services, "test");
            if (Feature.ANTI_FRAUD.enabled(mask)) {
                assertEquals(List.of(regular), filtered);
            } else {
                assertSame(services, filtered);
            }
            assertEquals(MainHook.IN_CALL_ANTI_FRAUD_SERVICES.size() + 1, services.size());
        }
    }

    @Test
    public void stateMaskFollowsOnlyCombinedStateSwitch() {
        MainHook module = new MainHook();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        TestPreferences prefs = new TestPreferences();
        config.bind(prefs.preferences);
        for (int mask = 0; mask <= Feature.ALL; mask++) {
            prefs.cached = mask;
            assertEquals(Feature.HIDE_STATE.enabled(mask) ? StateVisibility.ALL : 0,
                    module.visibilityMask());
        }
    }

    @Test
    public void fullComponentNamesMatchAndroidShortAndFullIntentForms() {
        for (String name : MainHook.ANTI_FRAUD_SERVICE_COMPONENTS) {
            ComponentName full = ComponentName.unflattenFromString(name);
            assertNotNull(full);
            ComponentName shortened = ComponentName.unflattenFromString(full.flattenToShortString());
            assertTrue(MainHook.matchesComponent(MainHook.ANTI_FRAUD_SERVICE_COMPONENTS, full));
            assertTrue(MainHook.matchesComponent(MainHook.ANTI_FRAUD_SERVICE_COMPONENTS, shortened));
        }
        assertFalse(MainHook.matchesComponent(MainHook.ANTI_FRAUD_SERVICE_COMPONENTS, null));
        assertFalse(MainHook.matchesComponent(MainHook.ANTI_FRAUD_SERVICE_COMPONENTS,
                new ComponentName("com.google.android.gms", "com.google.android.gms.OtherService")));
    }

    private static ResolveInfo service(String flattened) {
        ComponentName component = ComponentName.unflattenFromString(flattened);
        assertNotNull(component);
        ResolveInfo result = new ResolveInfo();
        result.serviceInfo = new ServiceInfo();
        result.serviceInfo.packageName = component.getPackageName();
        result.serviceInfo.name = component.getClassName();
        return result;
    }
}
