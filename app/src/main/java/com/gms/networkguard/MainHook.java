package com.gms.networkguard;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

@SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
public final class MainHook extends XposedModule {
    private final GuardConfig config = new GuardConfig();
    static final String TAG = "GmsAntiFraudGuard";
    static final String PM = "com.coloros.phonemanager";
    static final String BLACKLIST = "com.oplus.blacklistapp";
    static final String SECURITY_GUARD = "com.coloros.securityguard";
    static final String SAFE_CENTER = "com.oplus.safecenter";
    static final String PERMISSION_CONTROLLER = "com.android.permissioncontroller";
    static final String PACKAGE_INSTALLER = "com.android.packageinstaller";

    static final Set<String> ANDROID_ANTI_FRAUD_FLAGS = new HashSet<>(Arrays.asList(
            "enhancedConfirmationInCallApisEnabled",
            "unknownCallPackageInstallBlockingEnabled"
    ));

    static final String[] ANDROID_ANTI_FRAUD_FLAG_CLASSES = {
            "android.permission.flags.Flags",
            "android.permission.jarjar.android.permission.flags.Flags",
            "com.android.internal.hidden_from_bootclasspath.android.permission.flags.Flags"
    };

    static final Set<String> ANDROID_ANTI_FRAUD_DEVICE_CONFIG_FLAGS = new HashSet<>(Arrays.asList(
            "android.permission.flags.enhanced_confirmation_in_call_apis_enabled",
            "android.permission.flags.unknown_call_package_install_blocking_enabled"
    ));

    static final Set<String> ANTI_FRAUD_SERVICE_COMPONENTS = new HashSet<>(Arrays.asList(
            "com.google.android.gms/com.google.android.gms.bankscamwarning.services.BankScamCallDetectionService",
            "com.google.android.gms/com.google.android.gms.fraudprotect.service.CallDetectionService",
            "android/com.android.ecm.EnhancedConfirmationCallTrackerService"
    ));

    static final Set<String> IN_CALL_ANTI_FRAUD_SERVICES = new HashSet<>(Arrays.asList(
            "com.google.android.gms/com.google.android.gms.bankscamwarning.services.BankScamCallDetectionService",
            "android/com.android.ecm.EnhancedConfirmationCallTrackerService"
    ));

    static final Set<String> GOOGLE = new HashSet<>(Arrays.asList(
            "com.google.android.gms",
            "com.android.vending",
            "com.google.android.gsf"
    ));

    static final String[] FRAUD_COMPONENTS = {
            "com.oplus.phonemanager.aivoicecalldetect.antifraudhome.SecurityHomeActivity",
            "com.oplus.phonemanager.aivoicecalldetect.settings.AiVoiceCallDetectSettingsActivity",
            "com.oplus.phonemanager.aivoicecalldetect.settings.CrossSceneFraudDetectSettingsActivity",
            "com.oplus.phonemanager.aivoicecalldetect.frauddetailpage.FraudDetailActivity",
            "com.oplus.phonemanager.aivoicecalldetect.records.FraudDetectRecordsActivity",
            "com.oplus.phonemanager.aivoicecalldetect.riskdetail.ui.AntiFraudSeqDetailsActivity",
            "com.oplus.phonemanager.aivoicecalldetect.antifraudrecords.view.SecurityEventStatisticsActivity",
            "com.oplus.phonemanager.aivoicecalldetect.feedback.FalseReportFeedbackActivity",
            "com.oplus.phonemanager.aivoicecalldetect.dialog.FraudRiskDialogActivity",
            "com.oplus.phonemanager.aivoicecalldetect.receiver.InCallRiskDialogReceiver",
            "com.oplus.phonemanager.aivoicecalldetect.trigger.VoipCallDetectTriggerReceiver",
            "com.oplus.phonemanager.aivoicecalldetect.trigger.SimCallDetectTriggerService",
            "com.oplus.phonemanager.aivoicecalldetect.service.AiVoiceDetectForegroundService",
            "com.oplus.phonemanager.aivoicecalldetect.provider.AiVoiceDetectProvider",
            "com.oplus.phonemanager.aivoicecalldetect.provider.FeedbackFileLogProvider",
            "com.oplus.phonemanager.common.provider.FraudDetectRuleFilePipeProvider",
            "com.oplus.phonemanager.aivoicecalldetect.receiver.TriggerGuideActionReceiver",
            "com.oplus.phonemanager.deepfakedetect.ui.DeepfakeFaceDetectSettingsActivity",
            "com.oplus.phonemanager.deepfakedetect.ui.DeepfakeDetailActivity",
            "com.oplus.phonemanager.deepfakedetect.ui.DeepfakeImagePreviewActivity",
            "com.oplus.phonemanager.deepfakedetect.service.DeepfakeForegroundService",
            "com.oplus.phonemanager.deepfakedetect.receiver.DeepfakeFaceGuideActionReceiver"
    };

    static final String[] FRAUD_SERVICES = {
            "com.oplus.phonemanager.aivoicecalldetect.trigger.SimCallDetectTriggerService",
            "com.oplus.phonemanager.aivoicecalldetect.service.AiVoiceDetectForegroundService",
            "com.oplus.phonemanager.deepfakedetect.service.DeepfakeForegroundService"
    };

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        loadConfig();
        info("loaded " + param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        loadConfig();
        String pkg = param.getPackageName();
        ClassLoader cl = param.getClassLoader();

        if ("com.android.providers.settings".equals(pkg)) {
            new SystemStateVisibilityHooks(this).installSettingsProvider(cl);
        }

        if (BLACKLIST.equals(pkg)) {
            hookNationalAntiFraud(cl);
            hookFraudNumberIntercept(cl);
        }

        if ("com.android.phone".equals(pkg)) {
            hookPhoneAntiFraudSettings(cl);
        }

        if ("com.android.server.telecom".equals(pkg)) {
            hookTelecomAntiFraud(cl);
            hookAntiFraudInCallServiceDiscovery(cl);
        }

        if (PERMISSION_CONTROLLER.equals(pkg) || PACKAGE_INSTALLER.equals(pkg)) {
            hookAndroidAntiFraudFlags(cl);
        }

        if (SECURITY_GUARD.equals(pkg)) {
            hookSecurityGuardAntiFraud(cl);
        }

        if (SAFE_CENTER.equals(pkg)) {
            hookSafeCenterAntiFraud(cl);
        }

        if ("com.oplus.securitypermission".equals(pkg)) {
            hookAntiFraudSettingsEntry(cl);
        }

        if ("com.oplus.battery".equals(pkg)) {
            hookNetworking(cl);
            Context ctx = currentContext();
            if (ctx != null) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    repair(ctx);
                }, 4000);
            } else {
                info("system context unavailable");
            }
        }

        if (PM.equals(pkg)) {
            hookFraudComponentStateWrites(cl);
            hookPhoneManagerAntiFraud(cl);
            hookFraudComponentCallbacks(cl);
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        loadConfig();
        ClassLoader cl = param.getClassLoader();
        hookNetworking(cl);
        hookSystemServer(cl);
        hookFraudActivityStarts(cl);
        hookFraudServiceStarts(cl);
        hookAndroidAntiFraudFlags(cl);
        new SystemStateVisibilityHooks(this).installAccessibility(cl);
    }

    private void loadConfig() {
        if (config.isBound()) return;
        try {
            config.bind(getRemotePreferences(Feature.PREFERENCES));
        } catch (Throwable error) {
            err("feature preferences unavailable; keeping existing defaults", error);
        }
    }

    private Hooker guard(Feature feature, Hooker hooker) {
        return chain -> config.enabled(feature) ? hooker.intercept(chain) : chain.proceed();
    }

    int visibilityMask() {
        return config.visibilityMask();
    }

    void onVisibilityChanged(Runnable listener) {
        config.onVisibilityChanged(listener);
    }

    void hookAndroidAntiFraudFlags(ClassLoader cl) {
        int flagHooks = 0;
        Set<Method> hooked = new HashSet<>();
        for (String className : ANDROID_ANTI_FRAUD_FLAG_CLASSES) {
            try {
                Class<?> flags = Class.forName(className, false, cl);
                int classHooks = 0;
                for (Method method : flags.getDeclaredMethods()) {
                    if (!ANDROID_ANTI_FRAUD_FLAGS.contains(method.getName())
                            || method.getParameterCount() != 0
                            || method.getReturnType() != boolean.class
                            || !Modifier.isStatic(method.getModifiers())
                            || !hooked.add(method)) {
                        continue;
                    }
                    hook(method).setId("disable-android-antifraud-flag-" + method.getName())
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                                info("disabled Android call-protection flag " + method.getName());
                                return false;
                            }));
                    classHooks++;
                }
                flagHooks += classHooks;
                if (classHooks > 0) {
                    info("Android anti-fraud flag class=" + className + " hooks=" + classHooks);
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                err("Android anti-fraud feature flags " + className, t);
            }
        }

        int deviceConfigHooks = 0;
        try {
            Class<?> deviceConfig = Class.forName("android.provider.DeviceConfig", false, cl);
            Method method = deviceConfig.getDeclaredMethod(
                    "getBoolean", String.class, String.class, boolean.class);
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class) {
                hook(method).setId("disable-android-antifraud-device-config-flags")
                        .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                            Object namespace = chain.getArg(0);
                            Object name = chain.getArg(1);
                            if ("permissions".equals(namespace)
                                    && name instanceof String
                                    && ANDROID_ANTI_FRAUD_DEVICE_CONFIG_FLAGS.contains(name)) {
                                info("disabled Android call-protection flag " + name);
                                return false;
                            }
                            return chain.proceed();
                        }));
                deviceConfigHooks++;
            }
        } catch (Throwable t) {
            err("Android anti-fraud DeviceConfig flags", t);
        }
        info("Android anti-fraud feature flag hooks=" + flagHooks
                + " DeviceConfig hooks=" + deviceConfigHooks);
    }

    void hookAntiFraudInCallServiceDiscovery(ClassLoader cl) {
        int queryHooks = 0;
        try {
            Class<?> packageManager = Class.forName(
                    "android.app.ApplicationPackageManager", false, cl);
            for (Method method : packageManager.getDeclaredMethods()) {
                if ((!method.getName().equals("queryIntentServices")
                        && !method.getName().equals("queryIntentServicesAsUser"))
                        || !List.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                if (!hasIntentParameter(method)) continue;
                hook(method).setId("exclude-antifraud-incall-service-query-"
                        + Integer.toHexString(method.toGenericString().hashCode()))
                        .intercept(chain -> filterAntiFraudInCallServices(
                                chain.proceed(), method.getName()));
                queryHooks++;
            }
        } catch (Throwable t) {
            err("Telecom anti-fraud service queries", t);
        }

        int controllerHooks = 0;
        try {
            Class<?> controller = Class.forName(
                    "com.android.server.telecom.InCallController", false, cl);
            for (Method method : controller.getDeclaredMethods()) {
                if (!method.getName().equals("getInCallServiceComponents")
                        || !List.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                hook(method).setId("exclude-antifraud-incall-service-controller-"
                        + Integer.toHexString(method.toGenericString().hashCode()))
                        .intercept(chain -> filterAntiFraudInCallServices(
                                chain.proceed(), method.getName()));
                controllerHooks++;
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            err("Telecom anti-fraud InCallController", t);
        }
        info("Telecom anti-fraud service query hooks=" + queryHooks
                + " InCallController hooks=" + controllerHooks);
    }

    Object filterAntiFraudInCallServices(Object result, String source) {
        if (!config.enabled(Feature.ANTI_FRAUD)) return result;
        if (!(result instanceof List)) return result;
        List<?> services = (List<?>) result;
        ArrayList<Object> filtered = new ArrayList<>(services.size());
        boolean removed = false;
        for (Object service : services) {
            ComponentName component = resolvedInCallServiceComponent(service);
            if (matchesComponent(IN_CALL_ANTI_FRAUD_SERVICES, component)) {
                info("excluded anti-fraud InCallService "
                        + component.flattenToShortString() + " via " + source);
                removed = true;
            } else {
                filtered.add(service);
            }
        }
        return removed ? filtered : result;
    }

    static boolean matchesComponent(Set<String> targets, ComponentName component) {
        return component != null && targets.contains(component.flattenToString());
    }

    ComponentName resolvedInCallServiceComponent(Object service) {
        if (service instanceof ResolveInfo) {
            android.content.pm.ServiceInfo serviceInfo = ((ResolveInfo) service).serviceInfo;
            if (serviceInfo != null && serviceInfo.packageName != null && serviceInfo.name != null) {
                return new ComponentName(serviceInfo.packageName, serviceInfo.name);
            }
        }
        return inCallServiceComponent(service);
    }

    ComponentName inCallServiceComponent(Object service) {
        if (service == null) return null;
        for (Class<?> current = service.getClass(); current != null;
             current = current.getSuperclass()) {
            try {
                Method getter = current.getDeclaredMethod("getComponentName");
                getter.setAccessible(true);
                Object value = getter.invoke(service);
                if (value instanceof ComponentName) {
                    return (ComponentName) value;
                }
            } catch (Throwable ignored) {
            }
            try {
                Field field = current.getDeclaredField("mComponentName");
                if (field.getType() == ComponentName.class) {
                    field.setAccessible(true);
                    Object value = field.get(service);
                    if (value instanceof ComponentName) {
                        return (ComponentName) value;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    void hookFraudComponentCallbacks(ClassLoader cl) {
        Set<String> targets = new HashSet<>(Arrays.asList(FRAUD_COMPONENTS));
        Set<String> covered = new HashSet<>();
        Set<Method> hooked = new HashSet<>();
        int count = 0;
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, cl);
            Set<String> receiverCallbacks = new HashSet<>(Arrays.asList("onReceive"));
            Set<String> serviceCallbacks = new HashSet<>(Arrays.asList(
                    "onCreate", "onStartCommand", "onBind", "onRebind", "onUnbind",
                    "onTaskRemoved", "onCallAdded", "onCallRemoved"));
            Set<String> providerCallbacks = new HashSet<>(Arrays.asList(
                    "onCreate", "query", "insert", "update", "delete", "bulkInsert",
                    "call", "openFile", "openAssetFile", "openTypedAssetFile", "getType",
                    "canonicalize", "uncanonicalize", "refresh"));

            Class<?> receiver = Class.forName("android.content.BroadcastReceiver", false, cl);
            Class<?> service = Class.forName("android.app.Service", false, cl);
            Class<?> provider = Class.forName("android.content.ContentProvider", false, cl);

            for (String className : FRAUD_COMPONENTS) {
                Class<?> component = Class.forName(className, false, cl);
                if (activity.isAssignableFrom(component)) {
                    covered.add(className);
                } else if (receiver.isAssignableFrom(component)) {
                    count += hookComponentHierarchy(component, receiver, receiverCallbacks,
                            targets, hooked);
                    covered.add(className);
                } else if (service.isAssignableFrom(component)) {
                    count += hookComponentHierarchy(component, service, serviceCallbacks,
                            targets, hooked);
                    covered.add(className);
                } else if (provider.isAssignableFrom(component)) {
                    count += hookComponentHierarchy(component, provider, providerCallbacks,
                            targets, hooked);
                    covered.add(className);
                }
            }
        } catch (Throwable t) {
            err("PhoneManager component callbacks", t);
        }
        info("PhoneManager component callback hooks=" + count);
        Set<String> missing = new HashSet<>(targets);
        missing.removeAll(covered);
        info("PhoneManager anti-fraud component coverage=" + covered.size() + "/"
                + targets.size() + (missing.isEmpty() ? "" : " missing=" + missing));
    }

    void hookFraudActivityStarts(ClassLoader cl) {
        Set<String> targets = new HashSet<>(Arrays.asList(FRAUD_COMPONENTS));
        int count = 0;
        try {
            Class<?> starter = Class.forName("com.android.server.wm.ActivityStarter", false, cl);
            Class<?> activityManager = Class.forName("android.app.ActivityManager", false, cl);
            Field abortedField = activityManager.getDeclaredField("START_ABORTED");
            abortedField.setAccessible(true);
            int aborted = abortedField.getInt(null);
            for (Method method : starter.getDeclaredMethods()) {
                if (!method.getName().equals("executeRequest")
                        || method.getReturnType() != int.class
                        || method.getParameterCount() != 1) {
                    continue;
                }
                hook(method).setId("block-phone-manager-antifraud-activity-start")
                        .intercept(guard(Feature.HIDE_FRAUD_ENTRIES, chain -> {
                            Object request = chain.getArg(0);
                            android.content.Intent intent = requestIntent(request);
                            if (isFraudActivityIntent(intent, targets)) {
                                info("blocked anti-fraud activity start "
                                        + intent.getComponent().flattenToShortString());
                                return aborted;
                            }
                            return chain.proceed();
                        }));
                count++;
            }
        } catch (Throwable t) {
            err("PhoneManager anti-fraud activity starts", t);
        }
        info("PhoneManager anti-fraud activity start hooks=" + count);
    }

    android.content.Intent requestIntent(Object request) {
        if (request == null) return null;
        for (Class<?> current = request.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != android.content.Intent.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(request);
                    if (value instanceof android.content.Intent) {
                        return (android.content.Intent) value;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    boolean isFraudActivityIntent(android.content.Intent intent, Set<String> targets) {
        if (intent == null) return false;
        ComponentName component = intent.getComponent();
        if (component != null && PM.equals(component.getPackageName())
                && targets.contains(component.getClassName())) {
            return true;
        }
        android.content.Intent selector = intent.getSelector();
        if (selector != null && selector != intent) {
            ComponentName selected = selector.getComponent();
            return selected != null && PM.equals(selected.getPackageName())
                    && targets.contains(selected.getClassName());
        }
        return false;
    }

    void hookFraudServiceStarts(ClassLoader cl) {
        Set<String> targets = new HashSet<>(Arrays.asList(FRAUD_SERVICES));
        int count = 0;
        try {
            Class<?> activeServices = Class.forName(
                    "com.android.server.am.ActiveServices", false, cl);
            for (Method method : activeServices.getDeclaredMethods()) {
                boolean start = method.getName().equals("startServiceLocked")
                        && ComponentName.class.isAssignableFrom(method.getReturnType());
                boolean bind = method.getName().equals("bindServiceLocked")
                        && method.getReturnType() == int.class;
                if ((!start && !bind) || !hasIntentParameter(method)) continue;
                hook(method).setId("block-antifraud-service-"
                        + method.getName() + "-"
                        + Integer.toHexString(method.toGenericString().hashCode()))
                        .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                            android.content.Intent intent = firstIntent(chain.getArgs().toArray());
                            ComponentName component = intent == null ? null : intent.getComponent();
                            if (component != null && ((PM.equals(component.getPackageName())
                                    && targets.contains(component.getClassName()))
                                    || matchesComponent(ANTI_FRAUD_SERVICE_COMPONENTS, component))) {
                                info("blocked anti-fraud service "
                                        + component.flattenToShortString());
                                return defaultValue(method.getReturnType());
                            }
                            return chain.proceed();
                        }));
                count++;
            }
        } catch (Throwable t) {
            err("anti-fraud service starts", t);
        }
        info("anti-fraud service targets="
                + (targets.size() + ANTI_FRAUD_SERVICE_COMPONENTS.size())
                + " start/bind hooks=" + count);
    }

    boolean hasIntentParameter(Method method) {
        for (Class<?> type : method.getParameterTypes()) {
            if (type == android.content.Intent.class) return true;
        }
        return false;
    }

    android.content.Intent firstIntent(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof android.content.Intent) {
                return (android.content.Intent) arg;
            }
        }
        return null;
    }

    int hookComponentHierarchy(Class<?> component, Class<?> boundary, Set<String> callbacks,
                               Set<String> targets, Set<Method> hooked) {
        int count = 0;
        for (Class<?> current = component;
             current != null && boundary.isAssignableFrom(current);
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (!callbacks.contains(method.getName())
                        || Modifier.isAbstract(modifiers)
                        || Modifier.isStatic(modifiers)
                        || hooked.contains(method)) {
                    continue;
                }
                String id = "block-antifraud-callback-" + current.getName() + "-"
                        + method.getName() + "-" + method.getParameterCount();
                hook(method).setId(id).intercept(guard(Feature.ANTI_FRAUD, chain -> {
                    Object target = chain.getThisObject();
                    if (isFraudComponent(target, targets)) {
                        info("blocked anti-fraud component " + target.getClass().getName()
                                + "#" + method.getName());
                        return defaultValue(method.getReturnType());
                    }
                    return chain.proceed();
                }));
                hooked.add(method);
                count++;
            }
        }
        return count;
    }

    boolean isFraudComponent(Object target, Set<String> targets) {
        return target != null && targets.contains(target.getClass().getName());
    }

    Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return (char) 0;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    void hookFraudComponentStateWrites(ClassLoader cl) {
        Set<String> targets = new HashSet<>(Arrays.asList(FRAUD_COMPONENTS));
        int count = 0;
        try {
            Class<?> manager = Class.forName("android.app.ApplicationPackageManager", false, cl);
            for (Method method : manager.getDeclaredMethods()) {
                if (method.getName().equals("setComponentEnabledSetting")
                        && method.getParameterCount() == 3
                        && method.getParameterTypes()[0] == ComponentName.class
                        && method.getParameterTypes()[1] == int.class
                        && method.getReturnType() == void.class) {
                    hook(method).setId("keep-phone-manager-antifraud-component-default")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                                Object componentArg = chain.getArg(0);
                                Object stateArg = chain.getArg(1);
                                if (componentArg instanceof ComponentName
                                        && stateArg instanceof Integer) {
                                    ComponentName component = (ComponentName) componentArg;
                                    int state = (Integer) stateArg;
                                    if (PM.equals(component.getPackageName())
                                            && targets.contains(component.getClassName())
                                            && state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                                        info("prevent component state " + component.getClassName()
                                                + "=" + state);
                                        return null;
                                    }
                                }
                                return chain.proceed();
                            }));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("PhoneManager component state", t);
        }
        info("PhoneManager component state hooks=" + count);
    }

    void hookPhoneManagerAntiFraud(ClassLoader cl) {
        String[] names = {
                "isAIVoiceDetectSupport",
                "isAiFraudDetectSupport",
                "isDeepfakeFaceDetectSupport",
                "isIntelligentAntiFraudFullySupported",
                "isSupportAntiFraudSequence",
                "isSupportCrossSceneFraud",
                "isSupportSecurePayFraudSeq",
                "isAiVoiceSwitchOn",
                "isAntiFraudSeqSwitchOn",
                "isSmartAntiFraudSwitchOn"
        };
        Set<String> targets = new HashSet<>(Arrays.asList(names));
        int count = 0;
        try {
            Class<?> feature = Class.forName(
                    "com.oplus.phonemanager.common.feature.FeatureOption", false, cl);
            for (Method method : feature.getDeclaredMethods()) {
                if (targets.contains(method.getName())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == boolean.class) {
                    hook(method).setId("disable-phone-manager-antifraud-" + method.getName())
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> false));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("PhoneManager anti-fraud", t);
        }
        info("PhoneManager anti-fraud hooks=" + count);
    }

    void hookNationalAntiFraud(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> util = Class.forName("com.oplus.utils.I", false, cl);
            for (Method method : util.getDeclaredMethods()) {
                String name = method.getName();
                if ((name.equals("a") || name.equals("c") || name.equals("d"))
                        && method.getParameterCount() == 1
                        && method.getReturnType() == boolean.class) {
                    hook(method).setId("disable-national-antifraud-" + name)
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> false));
                    count++;
                } else if (name.equals("b")
                        && method.getParameterCount() == 2
                        && method.getReturnType() == void.class) {
                    hook(method).setId("disable-national-antifraud-write")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> null));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("National Anti-Fraud Center", t);
        }
        info("National Anti-Fraud Center hooks=" + count);
    }

    void hookFraudNumberIntercept(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> feature = Class.forName("com.oplus.feature.LazyLoadFeatureOption", false, cl);
            for (Method method : feature.getDeclaredMethods()) {
                if (method.getName().equals("C0")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == boolean.class) {
                    hook(method).setId("disable-blacklist-antifraud-sequence-support")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> false));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("BlackListApp anti-fraud support", t);
        }
        try {
            Class<?> policy = Class.forName("P4.f", false, cl);
            for (Method method : policy.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals("N")
                        && method.getReturnType() == boolean.class
                        && params.length == 2
                        && params[0] == Context.class
                        && params[1] == int.class) {
                    hook(method).setId("disable-suspected-fraud-number-intercept")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> false));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("BlackListApp suspected-fraud number intercept", t);
        }
        info("BlackListApp fraud number hooks=" + count);
    }

    void hookPhoneAntiFraudSettings(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> fragment = Class.forName(
                    "com.android.phone.OplusCallFeaturesSetting$OplusCallFeaturesSettingFragment",
                    false, cl);
            for (Method method : fragment.getDeclaredMethods()) {
                if (method.getName().equals("showAIAntiFraudPreference")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == void.class) {
                    hook(method).setId("hide-phone-ai-antifraud-setting")
                            .intercept(guard(Feature.HIDE_FRAUD_ENTRIES, chain -> null));
                    count++;
                } else if (method.getName().equals("isShowAIVoiceDetectionToggle")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == Boolean.class) {
                    hook(method).setId("hide-phone-ai-voice-detection-setting")
                            .intercept(guard(Feature.HIDE_FRAUD_ENTRIES, chain -> Boolean.FALSE));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("Phone anti-fraud settings", t);
        }
        info("Phone anti-fraud settings hooks=" + count);
    }

    void hookTelecomAntiFraud(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> telecomCall = Class.forName("com.android.server.telecom.Call", false, cl);
            Class<?> manager = Class.forName("com.android.server.telecom.p5", false, cl);
            for (Method method : manager.getDeclaredMethods()) {
                if (method.getName().equals("C")
                        && method.getReturnType() == void.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == telecomCall) {
                    hook(method).setId("disable-telecom-antifraud-center-call-intercept")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                                info("prevented Telecom anti-fraud-center call interception");
                                return null;
                            }));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("Telecom anti-fraud-center call intercept", t);
        }
        try {
            Class<?> support = Class.forName("com.android.server.telecom.oplus.m1", false, cl);
            for (Method method : support.getDeclaredMethods()) {
                if (method.getName().equals("b")
                        && method.getReturnType() == Boolean.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Context.class) {
                    hook(method).setId("disable-telecom-antifraud-sequence-support")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> Boolean.FALSE));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("Telecom anti-fraud support", t);
        }
        try {
            Class<?> events = Class.forName("com.android.server.telecom.z1", false, cl);
            for (Method method : events.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals("t3")
                        && method.getReturnType() == void.class
                        && params.length == 3
                        && params[0] == String.class
                        && params[1] == Bundle.class
                        && params[2] == Boolean.class) {
                    hook(method).setId("disable-telecom-ai-antifraud-events")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                                Object event = chain.getArg(0);
                                if ("android.telecom.event.AI_CALL_ANTI_FRAUD".equals(event)
                                        || "android.telecom.event.AI_voice_detect".equals(event)) {
                                    info("blocked Telecom anti-fraud event " + event);
                                    return null;
                                }
                                return chain.proceed();
                            }));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("Telecom AI anti-fraud events", t);
        }
        info("Telecom anti-fraud hooks=" + count);
    }

    void hookSecurityGuardAntiFraud(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> switches = Class.forName(
                    "com.oplus.securityguard.antifraud.AntiFraudSeqSwitchHelper", false, cl);
            for (Method method : switches.getDeclaredMethods()) {
                if ((method.getName().equals("d") || method.getName().equals("e"))
                        && method.getParameterCount() == 0
                        && method.getReturnType() == boolean.class) {
                    hook(method).setId("disable-securityguard-antifraud-switch-" + method.getName())
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> false));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("SecurityGuard anti-fraud switch", t);
        }
        try {
            Class<?> engine = Class.forName(
                    "com.oplus.securityguard.antifraud.detect.engine.FraudDetectEngine",
                    false, cl);
            for (Method method : engine.getDeclaredMethods()) {
                if (method.getName().equals("c")
                        && method.getParameterCount() == 1
                        && method.getReturnType() == void.class) {
                    hook(method).setId("disable-securityguard-fraud-sequence-detection")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> null));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("SecurityGuard fraud sequence engine", t);
        }
        try {
            Class<?> provider = Class.forName(
                    "com.oplus.securityguard.antifraud.data.collection.AppSwitchNotifyProvider",
                    false, cl);
            for (Method method : provider.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals("call")
                        && method.getReturnType() == Bundle.class
                        && params.length == 3
                        && params[0] == String.class
                        && params[1] == String.class
                        && params[2] == Bundle.class) {
                    hook(method).setId("disable-securityguard-antifraud-app-events")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> new Bundle()));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("SecurityGuard anti-fraud app events", t);
        }
        info("SecurityGuard anti-fraud hooks=" + count);
    }

    void hookSafeCenterAntiFraud(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> provider = Class.forName(
                    "com.oplus.safecenter.accessibility.provider.FraudRiskProvider", false, cl);
            for (Method method : provider.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals("call")
                        && method.getReturnType() == Bundle.class
                        && params.length == 3
                        && params[0] == String.class
                        && params[1] == String.class
                        && params[2] == Bundle.class) {
                    hook(method).setId("disable-safecenter-antifraud-actions")
                            .intercept(guard(Feature.ANTI_FRAUD, chain -> {
                                Object action = chain.getArg(0);
                                if (action instanceof String
                                        && ((String) action).startsWith("trigger_")) {
                                    info("blocked SafeCenter anti-fraud action " + action);
                                    return new Bundle();
                                }
                                return chain.proceed();
                            }));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("SafeCenter anti-fraud actions", t);
        }
        info("SafeCenter anti-fraud hooks=" + count);
    }

    void hookAntiFraudSettingsEntry(ClassLoader cl) {
        int count = 0;
        try {
            Class<?> controller = Class.forName(
                    "com.oplusos.securitypermission.privacy.controller.AntifraudProtectionController",
                    false, cl);
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getName().equals("isPreferenceAvailable")
                        && method.getReturnType() == boolean.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Context.class) {
                    hook(method).setId("hide-securitypermission-antifraud-entry")
                            .intercept(guard(Feature.HIDE_FRAUD_ENTRIES, chain -> false));
                    count++;
                }
            }
        } catch (Throwable t) {
            err("SecurityPermission anti-fraud setting", t);
        }
        info("SecurityPermission anti-fraud setting hooks=" + count);
    }

    static Context currentContext() {
        try {
            Class<?> c = Class.forName("android.app.ActivityThread");
            Context ctx = (Context) c.getMethod("currentApplication").invoke(null);
            if (ctx != null) return ctx;
            Object at = c.getMethod("currentActivityThread").invoke(null);
            Method m = c.getDeclaredMethod("getSystemContext");
            m.setAccessible(true);
            return (Context) m.invoke(at);
        } catch (Throwable ignored) {
            return null;
        }
    }

    void hookSystemServer(ClassLoader cl) {
        try {
            Class<?> c = Class.forName("com.android.server.SystemServer", false, cl);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("startOtherServices")) {
                    hook(m).setId("gms-repair-start").intercept(chain -> {
                        Object result = chain.proceed();
                        Context ctx = context(chain.getThisObject());
                        if (ctx != null) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                repair(ctx);
                            }, 5000);
                        }
                        return result;
                    });
                    break;
                }
            }
        } catch (Throwable t) {
            err("systemserver", t);
        }
    }

    static Context context(Object target) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("mSystemContext");
                f.setAccessible(true);
                return (Context) f.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    void hookNetworking(ClassLoader cl) {
        hookNms(cl);
        hookPolicy(cl, "android.net.OplusNetworkingControlManager");
        hookPolicy(cl, "android.net.IOplusNetworkingControlManager$Stub$Proxy");
        hookOAppNetControl(cl, "com.android.server.nwpower.OAppNetControlService", false);
        hookOAppNetControl(cl, "android.nwpower.OAppNetControlManager", true);
    }

    void hookOAppNetControl(ClassLoader cl, String className, boolean allowValue) {
        try {
            Class<?> c = Class.forName(className, false, cl);
            for (Method m : c.getDeclaredMethods()) {
                String name = m.getName();
                if ((name.equals("setFirewall") || name.equals("legacySetFirewall")
                        || name.equals("setFirewallWithArgs")) && m.getParameterCount() >= 2) {
                    hook(m).setId("allow-gms-fw-" + className + "-" + name).intercept(guard(Feature.GOOGLE_NETWORK, chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof Integer && googleUid((Integer) args[0])) {
                            args[1] = allowValue;
                            info("prevent firewall uid=" + args[0]);
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    }));
                } else if ((name.equals("destroySocket") || name.equals("destroySocketForProc")
                        || name.equals("forceStopNetDisbaleWhitelist")) && m.getParameterCount() >= 1) {
                    hook(m).setId("keep-gms-socket-" + className + "-" + name).intercept(guard(Feature.GOOGLE_NETWORK, chain -> {
                        Object arg = chain.getArg(0);
                        if (arg instanceof Integer && googleUid((Integer) arg)) {
                            info("prevent socket destroy uid=" + arg);
                            return null;
                        }
                        return chain.proceed();
                    }));
                } else if (name.equals("networkDisableWhiteList")) {
                    hook(m).setId("gms-net-whitelist").intercept(guard(Feature.GOOGLE_NETWORK, chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof List) {
                            List<Object> list = new ArrayList<>((List<?>) args[0]);
                            addGoogleIdentifiers(list);
                            args[0] = list;
                        }
                        return chain.proceed(args);
                    }));
                }
            }
            info("OAppNetControl hooks ready " + className);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            err("OAppNetControl " + className, t);
        }
    }

    void addGoogleIdentifiers(List<Object> list) {
        for (String packageName : GOOGLE) {
            if (!list.contains(packageName)) {
                list.add(packageName);
            }
        }
        Context context = currentContext();
        if (context == null) {
            return;
        }
        for (String packageName : GOOGLE) {
            try {
                String uid = String.valueOf(context.getPackageManager().getPackageUid(packageName, 0));
                if (!list.contains(uid)) {
                    list.add(uid);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    void hookNms(ClassLoader cl) {
        String[] classes = {
                "com.android.server.net.NetworkPolicyManagerService",
                "com.android.server.net.NetworkPolicyManagerService$NetworkPolicyManagerInternalImpl"
        };
        for (String className : classes) {
            try {
                Class<?> c = Class.forName(className, false, cl);
                for (Method m : c.getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.equals("setUidPolicy") || name.equals("addUidPolicy")
                            || name.equals("setUidFirewallRule") || name.equals("setUidFirewallRuleUL"))
                            && m.getParameterCount() >= 2) {
                        hook(m).setId("allow-google-mobile-" + className + "-" + name).intercept(guard(Feature.GOOGLE_NETWORK, chain -> {
                            Object[] args = chain.getArgs().toArray();
                            int pos = -1;
                            for (int i = 0; i < args.length; i++) {
                                if (args[i] instanceof Integer && googleUid((Integer) args[i])) {
                                    pos = i;
                                    break;
                                }
                            }
                            if (pos >= 0) {
                                if ((name.equals("setUidPolicy") || name.equals("addUidPolicy"))
                                        && pos + 1 < args.length && args[pos + 1] instanceof Integer) {
                                    args[pos + 1] = 4;
                                } else if (args.length > 0 && args[args.length - 1] instanceof Integer) {
                                    args[args.length - 1] = 1;
                                }
                                info("prevent metered deny uid=" + args[pos] + " via " + name);
                                return chain.proceed(args);
                            }
                            return chain.proceed();
                        }));
                    }
                }
                info("metered policy hooks ready " + className);
            } catch (Throwable ignored) {
            }
        }
    }

    void hookPolicy(ClassLoader cl, String className) {
        try {
            Class<?> c = Class.forName(className, false, cl);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("setUidPolicy") && m.getParameterCount() >= 2) {
                    hook(m).setId("allow-google-policy-" + className).intercept(guard(Feature.GOOGLE_NETWORK, chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof Integer && googleUid((Integer) args[0])) {
                            args[1] = 0;
                        }
                        return chain.proceed(args);
                    }));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressLint("WrongConstant")
    void repair(Context context) {
        if (!config.enabled(Feature.GOOGLE_NETWORK)) return;
        try {
            PackageManager pm = context.getPackageManager();
            for (String packageName : GOOGLE) {
                int uid = pm.getPackageUid(packageName, 0);
                try {
                    Class<?> c = Class.forName("android.net.OplusNetworkingControlManager");
                    Object manager = c.getMethod("getOplusNetworkingControlManager").invoke(null);
                    c.getMethod("setUidPolicy", int.class, int.class).invoke(manager, uid, 0);
                    int policy = (Integer) c.getMethod("getUidPolicy", int.class).invoke(manager, uid);
                    info("oplus policy " + packageName + "=" + policy);
                } catch (Throwable t) {
                    err("policy " + packageName, t);
                }
                try {
                    Object npm = context.getSystemService("netpolicy");
                    Class<?> nc = Class.forName("android.net.NetworkPolicyManager");
                    nc.getMethod("setUidPolicy", int.class, int.class).invoke(npm, uid, 4);
                    info("metered policy " + packageName + "=4");
                } catch (Throwable t) {
                    err("metered " + packageName, t);
                }
                try {
                    android.app.usage.UsageStatsManager us =
                            (android.app.usage.UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                    us.getClass()
                            .getMethod("setAppStandbyBucket", String.class, int.class)
                            .invoke(us, packageName, android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE);
                } catch (Throwable ignored) {
                }
                info("repaired " + packageName + " uid=" + uid);
            }
        } catch (Throwable t) {
            err("repair", t);
        }
    }

    boolean googleUid(int uid) {
        try {
            Object pm = Class.forName("android.app.AppGlobals")
                    .getMethod("getPackageManager")
                    .invoke(null);
            String[] packages = (String[]) pm.getClass()
                    .getMethod("getPackagesForUid", int.class)
                    .invoke(pm, uid);
            if (packages != null) {
                for (String pkg : packages) {
                    if (GOOGLE.contains(pkg)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    void info(String message) {
        log(Log.INFO, TAG, message);
    }

    void err(String message, Throwable throwable) {
        log(Log.ERROR, TAG, message, throwable);
    }
}
