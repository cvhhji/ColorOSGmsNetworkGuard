package com.gms.networkguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
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

public final class MainHook extends XposedModule {
    static final String TAG = "GmsAntiFraudGuard";
    static final String ANDROID = "android";
    static final String SYSTEM = "system";
    static final String PM = "com.coloros.phonemanager";
    static final String BLACKLIST = "com.oplus.blacklistapp";

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

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        info("loaded " + param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getClassLoader();

        if (BLACKLIST.equals(pkg)) {
            hookNationalAntiFraud(cl);
        }

        if (ANDROID.equals(pkg) || SYSTEM.equals(pkg) || "com.oplus.battery".equals(pkg)) {
            hookNetworking(cl);
            if (ANDROID.equals(pkg) || SYSTEM.equals(pkg)) {
                hookSystemServer(cl);
            }
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

    void hookFraudComponentCallbacks(ClassLoader cl) {
        Set<String> targets = new HashSet<>(Arrays.asList(FRAUD_COMPONENTS));
        Set<String> covered = new HashSet<>();
        Set<Method> hooked = new HashSet<>();
        int count = 0;
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, cl);
            for (Method method : activity.getDeclaredMethods()) {
                if (method.getName().equals("performCreate")
                        && method.getReturnType() == void.class) {
                    hook(method).setId("block-antifraud-activity-" + method.getParameterCount())
                            .intercept(chain -> {
                                Object target = chain.getThisObject();
                                if (isFraudComponent(target, targets)) {
                                    ((android.app.Activity) target).finish();
                                    info("blocked anti-fraud component "
                                            + target.getClass().getName() + "#performCreate");
                                    return null;
                                }
                                return chain.proceed();
                            });
                    hooked.add(method);
                    count++;
                }
            }

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
                hook(method).setId(id).intercept(chain -> {
                    Object target = chain.getThisObject();
                    if (isFraudComponent(target, targets)) {
                        info("blocked anti-fraud component " + target.getClass().getName()
                                + "#" + method.getName());
                        return defaultValue(method.getReturnType());
                    }
                    return chain.proceed();
                });
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
                            .intercept(chain -> {
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
                            });
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
                            .intercept(chain -> false);
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
                            .intercept(chain -> false);
                    count++;
                } else if (name.equals("b")
                        && method.getParameterCount() == 2
                        && method.getReturnType() == void.class) {
                    hook(method).setId("disable-national-antifraud-write")
                            .intercept(chain -> null);
                    count++;
                }
            }
        } catch (Throwable t) {
            err("National Anti-Fraud Center", t);
        }
        info("National Anti-Fraud Center hooks=" + count);
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
                    hook(m).setId("allow-gms-fw-" + className + "-" + name).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof Integer && googleUid((Integer) args[0])) {
                            args[1] = allowValue;
                            info("prevent firewall uid=" + args[0]);
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    });
                } else if ((name.equals("destroySocket") || name.equals("destroySocketForProc")
                        || name.equals("forceStopNetDisbaleWhitelist")) && m.getParameterCount() >= 1) {
                    hook(m).setId("keep-gms-socket-" + className + "-" + name).intercept(chain -> {
                        Object arg = chain.getArg(0);
                        if (arg instanceof Integer && googleUid((Integer) arg)) {
                            info("prevent socket destroy uid=" + arg);
                            return null;
                        }
                        return chain.proceed();
                    });
                } else if (name.equals("networkDisableWhiteList")) {
                    hook(m).setId("gms-net-whitelist").intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof List) {
                            List<Object> list = new ArrayList<>((List<?>) args[0]);
                            addGoogleIdentifiers(list);
                            args[0] = list;
                        }
                        return chain.proceed(args);
                    });
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
                        hook(m).setId("allow-google-mobile-" + className + "-" + name).intercept(chain -> {
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
                        });
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
                    hook(m).setId("allow-google-policy-" + className).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof Integer && googleUid((Integer) args[0])) {
                            args[1] = 0;
                        }
                        return chain.proceed(args);
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    void repair(Context context) {
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
