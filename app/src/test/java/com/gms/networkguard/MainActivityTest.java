package com.gms.networkguard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MainActivityTest {
    private GuardApplication application;
    private XposedService service;
    private TestPreferences prefs;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before
    public void connect() throws Exception {
        application = (GuardApplication) RuntimeEnvironment.getApplication();
        prefs = new TestPreferences();
        service = mock(XposedService.class);
        when(service.getRemotePreferences(Feature.PREFERENCES)).thenReturn(prefs.preferences);
        HookedTarget target = mock(HookedTarget.class);
        when(target.getProcessName()).thenReturn("system");
        when(target.getLoadedVersionCode()).thenReturn(application.getPackageManager()
                .getPackageInfo(application.getPackageName(), 0).getLongVersionCode());
        when(target.getState()).thenReturn(HookedTarget.State.UP_TO_DATE);
        when(service.getRunningTargets()).thenReturn(Collections.singletonList(target));
        application.onServiceBind(service);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void close() {
        if (controller != null) controller.pause().stop().destroy();
        ThreadPoolExecutor worker = ReflectionHelpers.getField(application, "worker");
        worker.shutdownNow();
    }

    private void open() throws Exception {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        await(() -> switches().size() == 4 && switches().values().stream().allMatch(View::isEnabled));
    }

    private EnumMap<Feature, Switch> switches() {
        return ReflectionHelpers.getField(activity, "switches");
    }

    private String message() {
        TextView message = ReflectionHelpers.getField(activity, "message");
        return message.getText().toString();
    }

    private static void await(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 500; attempt++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("UI state did not settle");
    }

    @Test
    public void fourSwitchesAutoSaveWithoutCentralSaveButton() throws Exception {
        open();
        assertTrue(switches().values().stream().allMatch(Switch::isChecked));
        TextView status = ReflectionHelpers.getField(activity, "activationLabel");
        assertEquals(activity.getString(R.string.status_active), status.getText().toString());
        for (Feature feature : Feature.values()) {
            switches().get(feature).performClick();
            await(() -> switches().get(feature).isEnabled());
            assertFalse(feature.enabled(prefs.persisted));
        }
        assertEquals(0, prefs.persisted);
        List<Button> buttons = new ArrayList<>();
        collect(activity.getWindow().getDecorView(), Button.class, buttons);
        buttons.removeIf(button -> button instanceof Switch);
        assertTrue(buttons.isEmpty());
        List<TextView> labels = new ArrayList<>();
        collect(activity.getWindow().getDecorView(), TextView.class, labels);
        assertTrue(labels.stream().noneMatch(label -> label.getText().toString().contains("功能设置")
                || label.getText().toString().contains("重新检查")
                || label.getText().toString().contains("运行时处理")
                || label.getText().toString().contains("不会关闭无障碍")));
    }

    @Test
    public void failedSaveRestoresUiAndDurableValue() throws Exception {
        open();
        prefs.outcomes.add(false);
        prefs.outcomes.add(true);
        Switch control = switches().get(Feature.ANTI_FRAUD);
        control.performClick();
        await(control::isEnabled);
        assertTrue(control.isChecked());
        assertEquals(Feature.ALL, prefs.persisted);
        assertEquals(activity.getString(R.string.save_failed), message());
    }

    @Test
    public void uncertainSaveBlocksEditsAndReopeningRecovers() throws Exception {
        open();
        prefs.outcomes.add(false);
        prefs.outcomes.add(false);
        switches().get(Feature.HIDE_STATE).performClick();
        await(() -> message().equals(activity.getString(R.string.save_uncertain)));
        assertTrue(switches().values().stream().noneMatch(View::isEnabled));
        controller.recreate();
        activity = controller.get();
        await(() -> switches().values().stream().allMatch(View::isEnabled));
        assertTrue(switches().get(Feature.HIDE_STATE).isChecked());
        assertEquals(Feature.ALL, prefs.persisted);
    }

    @Test
    public void concurrentRowsQueueWithoutLosingOtherChanges() throws Exception {
        open();
        CountDownLatch release = new CountDownLatch(1);
        application.execute(() -> {
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        Switch network = switches().get(Feature.GOOGLE_NETWORK);
        Switch entries = switches().get(Feature.HIDE_FRAUD_ENTRIES);
        network.performClick();
        entries.performClick();
        assertFalse(network.isEnabled());
        assertFalse(entries.isEnabled());
        assertTrue(switches().get(Feature.ANTI_FRAUD).isEnabled());
        release.countDown();
        await(() -> network.isEnabled() && entries.isEnabled());
        assertFalse(Feature.GOOGLE_NETWORK.enabled(prefs.persisted));
        assertFalse(Feature.HIDE_FRAUD_ENTRIES.enabled(prefs.persisted));
        assertTrue(Feature.ANTI_FRAUD.enabled(prefs.persisted));
        assertTrue(Feature.HIDE_STATE.enabled(prefs.persisted));
    }

    @Test
    public void serviceDeathDisablesEditingAndNeverClaimsActive() throws Exception {
        open();
        application.onServiceDied(service);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(switches().values().stream().noneMatch(View::isEnabled));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        TextView status = ReflectionHelpers.getField(activity, "activationLabel");
        assertEquals(activity.getString(R.string.status_inactive), status.getText().toString());
    }

    @Test
    public void emptySystemTargetsDoesNotPretendActive() throws Exception {
        when(service.getRunningTargets()).thenReturn(Collections.emptyList());
        open();
        TextView status = ReflectionHelpers.getField(activity, "activationLabel");
        assertEquals(activity.getString(R.string.status_inactive), status.getText().toString());
    }

    @Test
    public void nativeKeyboardAndAccessibilitySemanticsRemainUsable() throws Exception {
        open();
        Switch control = switches().get(Feature.HIDE_STATE);
        control.setFocusableInTouchMode(true);
        assertTrue(control.requestFocus());
        AccessibilityNodeInfo info = control.createAccessibilityNodeInfo();
        assertTrue(info.isCheckable());
        assertTrue(info.isChecked());
        assertTrue(info.getText().toString().contains("ADB"));
        control.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE));
        control.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE));
        await(control::isEnabled);
        assertFalse(Feature.HIDE_STATE.enabled(prefs.persisted));
    }

    @Test
    public void timeoutCannotClaimSuccessWhileWriteIsStillQueued() throws Exception {
        open();
        CountDownLatch release = new CountDownLatch(1);
        application.execute(() -> {
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            switches().get(Feature.ANTI_FRAUD).performClick();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9));
            assertEquals(activity.getString(R.string.operation_timeout), message());
            assertTrue(switches().values().stream().noneMatch(View::isEnabled));
            assertEquals(Feature.ALL, prefs.persisted);
        } finally {
            release.countDown();
        }
        await(() -> switches().get(Feature.ANTI_FRAUD).isEnabled());
        assertFalse(Feature.ANTI_FRAUD.enabled(prefs.persisted));
    }

    @Test
    public void noBackgroundPollingAfterInitialCheck() throws Exception {
        open();
        clearInvocations(service);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));
        verifyNoInteractions(service);
    }

    @Test
    @Config(sdk = 37, qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
    public void renderAndroid17() throws Exception {
        open();
        capture("android17");
    }

    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
    public void renderLight() throws Exception {
        open();
        capture("light");
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-night-xhdpi")
    public void renderNarrowNightLargeText() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        open();
        capture("night-narrow-font200");
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-night-xhdpi")
    public void feedbackDoesNotMoveControlsOrHideRecoveryAtLargeText() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        open();
        TextView feedback = ReflectionHelpers.getField(activity, "message");
        View activation = ReflectionHelpers.getField(activity, "activationCard");
        int originalHeight = feedback.getHeight();
        int originalTop = activation.getTop();
        int[] messages = {R.string.saving, R.string.saved, R.string.save_failed,
                R.string.save_uncertain, R.string.operation_timeout, R.string.load_failed,
                R.string.connection_unavailable, R.string.worker_busy};
        for (int resource : messages) {
            ReflectionHelpers.callInstanceMethod(activity, "showMessage",
                    ReflectionHelpers.ClassParameter.from(int.class, resource),
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(originalHeight, feedback.getHeight());
            assertEquals(originalTop, activation.getTop());
            assertTrue(feedback.getLineCount() <= 2);
            assertEquals(feedback.getText().length(),
                    feedback.getLayout().getLineEnd(feedback.getLineCount() - 1));
            assertTrue(feedback.getLayout().getHeight() <= feedback.getHeight());
        }
        capture("night-narrow-font200-feedback");
    }

    private void capture(String name) throws Exception {
        List<ScrollView> scrolls = new ArrayList<>();
        collect(activity.getWindow().getDecorView(), ScrollView.class, scrolls);
        assertEquals(1, scrolls.size());
        ScrollView scroll = scrolls.get(0);
        assertTrue(scroll.isVerticalScrollBarEnabled());
        View page = scroll.getChildAt(0);
        assertTrue(page.getWidth() > 0);
        assertTrue(page.getHeight() > 0);
        for (Switch control : switches().values()) {
            assertTrue(control.getWidth() <= page.getWidth());
            assertTrue(control.getHeight() >= 64 * activity.getResources().getDisplayMetrics().density);
            assertNotNull(control.getLayout());
            assertEquals(0, control.getLayout().getEllipsisCount(control.getLineCount() - 1));
            assertTrue(control.getLayout().getHeight() <= control.getHeight()
                    - control.getCompoundPaddingTop() - control.getCompoundPaddingBottom());
        }
        File directory = new File("build/reports/ui-tests");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(activity.getColor(R.color.page_background));
        page.draw(canvas);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }

    private static <T extends View> void collect(View root, Class<T> type, List<T> found) {
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                collect(group.getChildAt(index), type, found);
            }
        }
    }
}
