package com.gms.networkguard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.concurrent.RejectedExecutionException;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

public final class MainActivity extends Activity {
    private static final long TIMEOUT_MS = 8000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnumMap<Feature, Switch> switches = new EnumMap<>(Feature.class);
    private final EnumMap<Feature, Boolean> pending = new EnumMap<>(Feature.class);
    private GuardApplication application;
    private LinearLayout activationCard;
    private FrameLayout activationIconHolder;
    private ImageView activationIcon;
    private ProgressBar activationProgress;
    private TextView activationLabel;
    private TextView message;
    private String versionLabel;
    private XposedService session;
    private int confirmedMask = Feature.ALL;
    private volatile int generation;
    private boolean updating;
    private boolean loaded;
    private boolean refreshing;
    private boolean blocked;
    private boolean saveError;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        application = (GuardApplication) getApplication();
        buildPage();
        application.attach(this);
        onConnectionChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!refreshing && pending.isEmpty()) refresh();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        generation++;
        application.detach(this);
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    void onConnectionChanged() {
        if (destroyed) return;
        generation++;
        session = application.service();
        refreshing = false;
        loaded = false;
        blocked = false;
        pending.clear();
        refresh();
    }

    private void buildPage() {
        getWindow().setDecorFitsSystemWindows(false);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        getWindow().getDecorView();
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) controller.setSystemBarsAppearance(dark ? 0 : lightBars, lightBars);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int inset = getResources().getDimensionPixelSize(R.dimen.page_inset);
        page.setPadding(inset, dp(24), inset, dp(24));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("G", 22, true, R.color.primary);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(R.color.primary_container, dp(16)));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.VERTICAL);
        headings.addView(text(getString(R.string.app_name), 28, true, R.color.text_primary));
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception error) {
            version = "—";
        }
        versionLabel = version;
        message = text(versionLabel, 13, false, R.color.text_tertiary);
        message.setMinLines(2);
        message.setMaxLines(2);
        message.setIncludeFontPadding(false);
        message.setFallbackLineSpacing(false);
        int feedbackLineHeight = Math.round(message.getTextSize() * 1.5f);
        message.setLineHeight(feedbackLineHeight);
        message.setHeight(feedbackLineHeight * 2);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        headings.addView(message);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.setMarginStart(dp(14));
        header.addView(headings, headingParams);
        page.addView(header);

        activationCard = new LinearLayout(this);
        activationCard.setGravity(Gravity.CENTER_VERTICAL);
        activationCard.setPadding(inset, dp(18), inset, dp(18));
        activationCard.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.status_min_height));
        activationCard.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        activationIconHolder = new FrameLayout(this);
        activationIconHolder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        activationIcon = new ImageView(this);
        activationIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        activationIconHolder.addView(activationIcon,
                new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        activationProgress = new ProgressBar(this);
        activationIconHolder.addView(activationProgress,
                new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        activationCard.addView(activationIconHolder, new LinearLayout.LayoutParams(dp(52), dp(52)));
        activationLabel = text(getString(R.string.status_checking), 22, true, R.color.text_primary);
        LinearLayout.LayoutParams statusLabelParams = new LinearLayout.LayoutParams(0, -2, 1);
        statusLabelParams.setMarginStart(dp(16));
        activationCard.addView(activationLabel, statusLabelParams);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(20), 0, dp(14));
        page.addView(activationCard, statusParams);
        setActivation(ActivationStatus.CHECKING);

        LinearLayout network = section(page, R.string.section_network);
        addFeature(network, Feature.GOOGLE_NETWORK, R.string.network_title, R.string.network_summary);
        LinearLayout system = section(page, R.string.section_system);
        addFeature(system, Feature.ANTI_FRAUD, R.string.fraud_title, R.string.fraud_summary);
        addFeature(system, Feature.HIDE_FRAUD_ENTRIES, R.string.entries_title, R.string.entries_summary);
        addFeature(system, Feature.HIDE_STATE, R.string.visibility_title, R.string.visibility_summary);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.page_background));
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        scroll.addView(page);
        setContentView(scroll);
        scroll.requestApplyInsets();
        renderSwitches();
    }

    private LinearLayout section(LinearLayout page, int label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int inset = getResources().getDimensionPixelSize(R.dimen.card_inset);
        card.setPadding(inset, dp(18), inset, dp(16));
        card.setBackground(round(R.color.card_background,
                getResources().getDimensionPixelSize(R.dimen.card_radius)));
        TextView title = text(getString(label), 18, true, R.color.text_primary);
        title.setPadding(0, 0, 0, dp(10));
        title.setAccessibilityHeading(true);
        card.addView(title);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        page.addView(card, params);
        return card;
    }

    private void addFeature(LinearLayout card, Feature feature, int titleId, int summaryId) {
        String title = getString(titleId);
        String summary = getString(summaryId);
        SpannableString label = new SpannableString(title + "\n" + summary);
        int summarySize = Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics()));
        label.setSpan(new AbsoluteSizeSpan(summarySize), title.length() + 1, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(color(R.color.text_secondary)), title.length() + 1,
                label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextSize(16);
        control.setTextColor(color(R.color.text_primary));
        control.setTypeface(Typeface.DEFAULT);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setSwitchPadding(dp(14));
        control.setPadding(0, dp(9), 0, dp(9));
        control.setMinHeight(dp(64));
        control.setLineSpacing(dp(2), 1f);
        control.setShowText(false);
        control.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(R.color.control_disabled), color(R.color.primary), color(R.color.switch_thumb)}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(R.color.primary_container), color(R.color.switch_track)}));
        control.setOnCheckedChangeListener((button, checked) -> {
            if (!updating) change(feature, checked);
        });
        switches.put(feature, control);
        card.addView(control, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refresh() {
        if (destroyed || refreshing || !pending.isEmpty()) return;
        XposedService expected = application.service();
        session = expected;
        int ticket = ++generation;
        loaded = false;
        blocked = false;
        renderSwitches();
        if (expected == null) {
            setActivation(ActivationStatus.CHECKING);
            showMessage(R.string.connection_waiting, false);
            main.postDelayed(() -> {
                if (valid(ticket, null) && application.service() == null) {
                    setActivation(ActivationStatus.INACTIVE);
                    showMessage(R.string.connection_unavailable, true);
                }
            }, 2500);
            return;
        }
        refreshing = true;
        renderSwitches();
        Runnable timeout = () -> {
            if (valid(ticket, expected) && refreshing) {
                refreshing = false;
                blocked = true;
                setActivation(ActivationStatus.UNKNOWN);
                showMessage(R.string.operation_timeout, true);
                renderSwitches();
            }
        };
        main.postDelayed(timeout, TIMEOUT_MS);
        try {
            application.execute(() -> {
                try {
                    if (application.service() != expected || generation != ticket) return;
                    SharedPreferences prefs = expected.getRemotePreferences(Feature.PREFERENCES);
                    application.settings().recover(expected, prefs);
                    int mask = application.settings().read(expected, prefs);
                    ActivationStatus activation = inspectActivation(expected);
                    main.post(() -> {
                        if (!valid(ticket, expected)) return;
                        main.removeCallbacks(timeout);
                        confirmedMask = mask;
                        loaded = true;
                        blocked = false;
                        refreshing = false;
                        saveError = false;
                        setActivation(activation);
                        message.setText(versionLabel);
                        message.setTextColor(color(R.color.text_tertiary));
                        renderSwitches();
                    });
                } catch (RuntimeException error) {
                    main.post(() -> {
                        if (!valid(ticket, expected)) return;
                        main.removeCallbacks(timeout);
                        refreshing = false;
                        blocked = true;
                        setActivation(ActivationStatus.UNKNOWN);
                        showMessage(error instanceof RemoteSettings.UnconfirmedWriteException
                                ? R.string.save_uncertain : R.string.load_failed, true);
                        renderSwitches();
                    });
                }
            });
        } catch (RejectedExecutionException error) {
            main.removeCallbacks(timeout);
            refreshing = false;
            showMessage(R.string.worker_busy, true);
            renderSwitches();
        }
    }

    private void change(Feature feature, boolean enabled) {
        if (!loaded || blocked || refreshing || pending.containsKey(feature)
                || session == null || application.service() != session) {
            renderSwitches();
            return;
        }
        if (pending.isEmpty()) saveError = false;
        XposedService expected = session;
        int ticket = generation;
        pending.put(feature, enabled);
        showMessage(R.string.saving, false);
        renderSwitches();
        Runnable timeout = () -> {
            if (valid(ticket, expected) && pending.containsKey(feature)) {
                blocked = true;
                showMessage(R.string.operation_timeout, true);
                renderSwitches();
            }
        };
        main.postDelayed(timeout, TIMEOUT_MS);
        try {
            application.execute(() -> {
                boolean success = false;
                int mask = confirmedMask;
                RuntimeException failure = null;
                try {
                    if (application.service() != expected || generation != ticket) return;
                    SharedPreferences prefs = expected.getRemotePreferences(Feature.PREFERENCES);
                    success = application.settings().change(expected, prefs, feature, enabled);
                    mask = application.settings().read(expected, prefs);
                } catch (RuntimeException error) {
                    failure = error;
                }
                boolean saved = success;
                int resultMask = mask;
                RuntimeException resultError = failure;
                main.post(() -> {
                    if (!valid(ticket, expected)) return;
                    main.removeCallbacks(timeout);
                    pending.remove(feature);
                    if (resultError == null) {
                        confirmedMask = resultMask;
                        if (pending.isEmpty()) blocked = false;
                    } else {
                        blocked = true;
                    }
                    if (!saved || resultError != null) {
                        saveError = true;
                        showMessage(resultError == null ? R.string.save_failed : R.string.save_uncertain, true);
                    } else if (!saveError) {
                        showMessage(pending.isEmpty() ? R.string.saved : R.string.saving, false);
                    }
                    renderSwitches();
                });
            });
        } catch (RejectedExecutionException error) {
            main.removeCallbacks(timeout);
            pending.remove(feature);
            showMessage(R.string.worker_busy, true);
            renderSwitches();
        }
    }

    private ActivationStatus inspectActivation(XposedService service) {
        try {
            long installed = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            for (HookedTarget target : service.getRunningTargets()) {
                if (!"system".equals(target.getProcessName())
                        && !"system_server".equals(target.getProcessName())) continue;
                return ActivationStatus.resolve(true, installed, target.getLoadedVersionCode(),
                        target.getState() == HookedTarget.State.UP_TO_DATE,
                        target.getState() == HookedTarget.State.STALE);
            }
            return ActivationStatus.INACTIVE;
        } catch (Exception error) {
            return ActivationStatus.UNKNOWN;
        }
    }

    private boolean valid(int ticket, XposedService expected) {
        return !destroyed && generation == ticket && session == expected
                && application.service() == expected;
    }

    private void renderSwitches() {
        updating = true;
        for (Feature feature : switches.keySet()) {
            Switch control = switches.get(feature);
            boolean saving = pending.containsKey(feature);
            control.setChecked(saving ? pending.get(feature) : feature.enabled(confirmedMask));
            control.setEnabled(loaded && !blocked && !refreshing && !saving && session != null);
            control.setAlpha(control.isEnabled() ? 1f : 0.52f);
            control.setStateDescription(saving ? getString(R.string.state_saving) : null);
        }
        updating = false;
    }

    private void setActivation(ActivationStatus state) {
        int background = R.color.card_background;
        int foreground = R.color.text_secondary;
        int iconBackground = R.color.switch_track;
        int iconForeground = R.color.text_secondary;
        int label = R.string.status_checking;
        int icon = R.drawable.ic_status_attention;
        if (state == ActivationStatus.ACTIVE) {
            background = R.color.primary_container;
            foreground = R.color.on_primary_container;
            label = R.string.status_active;
            iconBackground = R.color.primary;
            iconForeground = R.color.on_primary;
            icon = R.drawable.ic_status_check;
        } else if (state == ActivationStatus.RESTART_REQUIRED) {
            background = R.color.warning_container;
            foreground = R.color.on_warning_container;
            label = R.string.status_restart;
            iconBackground = R.color.warning;
            iconForeground = R.color.on_warning;
        } else if (state == ActivationStatus.INACTIVE) {
            background = R.color.error_container;
            foreground = R.color.on_error_container;
            label = R.string.status_inactive;
            iconBackground = R.color.error;
            iconForeground = R.color.on_error;
        } else if (state == ActivationStatus.UNKNOWN) {
            label = R.string.status_unknown;
        }
        activationCard.setBackground(round(background,
                getResources().getDimensionPixelSize(R.dimen.status_radius)));
        activationIconHolder.setBackground(round(iconBackground, dp(26)));
        activationIcon.setImageResource(icon);
        activationIcon.setImageTintList(ColorStateList.valueOf(color(iconForeground)));
        activationIcon.setVisibility(state == ActivationStatus.CHECKING ? View.GONE : View.VISIBLE);
        activationProgress.setIndeterminateTintList(ColorStateList.valueOf(color(iconForeground)));
        activationProgress.setVisibility(state == ActivationStatus.CHECKING ? View.VISIBLE : View.GONE);
        activationLabel.setText(label);
        activationLabel.setTextColor(color(foreground));
    }

    private void showMessage(int resource, boolean error) {
        boolean changed = !getString(resource).contentEquals(message.getText());
        message.setText(resource);
        message.setTextColor(color(error ? R.color.error : R.color.text_secondary));
        if (error && changed) Toast.makeText(this, resource, Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        view.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(color));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int color(int resource) {
        return getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
