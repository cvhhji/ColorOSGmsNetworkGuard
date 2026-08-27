package com.gms.networkguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    static final String SETTINGS = "com.android.settings";
    static final String PM = "com.coloros.phonemanager";

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
            "com.oplus.phonemanager.common.provider.FraudDetectRuleFilePipeProvider"
    };

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        info("loaded " + param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getClassLoader();

        if (SETTINGS.equals(pkg)) {
            hookSettings(cl);
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
                    disableFraud(ctx);
                }, 4000);
            } else {
                info("system context unavailable");
            }
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                } catch (Throwable ignored) {
                }
                disableFraudShell();
            }, "GmsGuardDisable").start();
        }

        if (PM.equals(pkg)) {
            Context ctx = currentContext();
            if (ctx != null) {
                disableFraud(ctx);
            }
        }
    }

    void hookSettings(ClassLoader cl) {
        int count = 0;
        for (String className : new String[]{
                "com.oplus.settings.feature.homepage.controller.GooglePreferenceController",
                "com.oplus.settings.feature.othersettings.controller.GoogleSettingPreferenceController"
        }) {
            try {
                Class<?> controller = Class.forName(className, false, cl);
                for (Method method : controller.getDeclaredMethods()) {
                    String name = method.getName();
                    if (name.equals("getAvailabilityStatus") && method.getReturnType() == int.class) {
                        hook(method).setId("show-google-" + className + "-" + name)
                                .intercept(chain -> 0);
                        count++;
                    } else if (name.equals("isPreferenceAvailable")
                            && method.getReturnType() == boolean.class) {
                        hook(method).setId("show-google-" + className + "-" + name)
                                .intercept(chain -> true);
                        count++;
                    }
                }
            } catch (Throwable t) {
                err("settings " + className, t);
            }
        }
        info("Google Settings entry hooks=" + count);
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
                                disableFraud(ctx);
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
        try {
            Class<?> c = Class.forName("com.android.server.nwpower.OAppNetControlService", false, cl);
            for (Method m : c.getDeclaredMethods()) {
                String name = m.getName();
                if ((name.equals("setFirewall") || name.equals("legacySetFirewall")) && m.getParameterCount() == 2) {
                    hook(m).setId("allow-gms-fw-" + name).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (args[0] instanceof Integer && googleUid((Integer) args[0])) {
                            args[1] = false;
                            info("prevent firewall uid=" + args[0]);
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    });
                } else if ((name.equals("destroySocket") || name.equals("forceStopNetDisbaleWhitelist")) && m.getParameterCount() == 1) {
                    hook(m).setId("keep-gms-socket-" + name).intercept(chain -> {
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
                            list.addAll(GOOGLE);
                            args[0] = list;
                        }
                        return chain.proceed(args);
                    });
                }
            }
            info("OAppNetControl hooks ready");
        } catch (Throwable t) {
            err("OAppNetControl", t);
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

    void disableFraudShell() {
        try {
            for (String component : FRAUD_COMPONENTS) {
                Process process = new ProcessBuilder("/system/bin/pm", "disable", "--user", "0",
                        PM + "/" + component).redirectErrorStream(true).start();
                int rc = process.waitFor();
                info("disable " + component + " rc=" + rc);
            }
            info("anti-fraud disable commands issued");
        } catch (Throwable t) {
            err("disable shell", t);
        }
    }

    void disableFraud(Context context) {
        int disabled = 0;
        for (String component : FRAUD_COMPONENTS) {
            try {
                context.getPackageManager().setComponentEnabledSetting(
                        new ComponentName(PM, component),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                disabled++;
            } catch (Throwable t) {
                err("disable " + component, t);
            }
        }
        info("anti-fraud components disabled=" + disabled);
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
