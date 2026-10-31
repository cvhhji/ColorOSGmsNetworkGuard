package com.gms.networkguard;

import android.app.job.JobParameters;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Method;
import java.util.HashMap;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class NumberRecognitionHooksTest {
    @Test
    public void exactModuleSignaturesCoverEveryAntiFraudOperation() {
        int count = 0;
        for (Method method : AntiFraudModule.class.getDeclaredMethods()) {
            assertTrue(method.toGenericString(), NumberRecognitionHooks.isModuleMethod(method));
            count++;
        }
        assertEquals(9, count);
        for (Method method : OtherMethods.class.getDeclaredMethods()) {
            assertFalse(method.toGenericString(), NumberRecognitionHooks.isModuleMethod(method));
        }
    }

    @Test
    public void moduleSwitchPreservesOriginalResultsWhenOffForEveryCombination() throws Throwable {
        MainHook module = new MainHook();
        GuardConfig config = ReflectionHelpers.getField(module, "config");
        TestPreferences preferences = new TestPreferences();
        config.bind(preferences.preferences);
        NumberRecognitionHooks hooks = new NumberRecognitionHooks(module);
        Object original = new Object();
        for (Method method : AntiFraudModule.class.getDeclaredMethods()) {
            Hooker hooker = hooks.moduleHook(method);
            for (int mask = 0; mask <= Feature.ALL; mask++) {
                preferences.cached = mask;
                Chain chain = mock(Chain.class);
                when(chain.proceed()).thenReturn(original);
                Object result = hooker.intercept(chain);
                if (Feature.ANTI_FRAUD.enabled(mask)) {
                    if (method.getReturnType() == boolean.class) {
                        assertEquals("h".equals(method.getName()), result);
                    } else {
                        assertNull(result);
                    }
                    verify(chain, never()).proceed();
                } else {
                    assertSame(original, result);
                    verify(chain).proceed();
                }
            }
        }
    }

    @Test
    public void jobAndUpdaterFiltersRejectWrongOverloads() throws Exception {
        assertTrue(NumberRecognitionHooks.isJobMethod(
                AntiFraudJob.class.getDeclaredMethod("onStartJob", JobParameters.class)));
        assertTrue(NumberRecognitionHooks.isJobMethod(
                AntiFraudJob.class.getDeclaredMethod("onStopJob", JobParameters.class)));
        assertFalse(NumberRecognitionHooks.isJobMethod(
                OtherMethods.class.getDeclaredMethod("onStartJob", Object.class)));
        assertTrue(NumberRecognitionHooks.isUpdaterMethod(
                AntiFraudUpdater.class.getDeclaredMethod("l")));
        assertFalse(NumberRecognitionHooks.isUpdaterMethod(
                OtherMethods.class.getDeclaredMethod("l", String.class)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unrelatedMethodCannotBecomeAnAntiFraudHook() throws Exception {
        new NumberRecognitionHooks(new MainHook()).moduleHook(
                OtherMethods.class.getDeclaredMethod("a", Object.class));
    }

    public abstract static class AntiFraudModule {
        public abstract boolean a(String number);
        public abstract void b(String number, boolean fraud);
        public abstract void c(Context context, HashMap<String, String> numbers);
        public abstract void d(Context context, String number, Integer type);
        public abstract boolean e(Context context, String original, String formatted);
        public abstract void f(Context context);
        public abstract void g();
        public abstract boolean h(Context context);
        public abstract boolean i(String number);
    }

    public abstract static class OtherMethods {
        public abstract boolean a(Object object);
        public abstract void b(String number, Boolean fraud);
        public abstract boolean h();
        public abstract boolean onStartJob(Object parameters);
        public abstract void l(String value);
    }

    public abstract static class AntiFraudJob {
        public abstract boolean onStartJob(JobParameters parameters);
        public abstract boolean onStopJob(JobParameters parameters);
    }

    public abstract static class AntiFraudUpdater {
        public abstract void l();
    }
}
