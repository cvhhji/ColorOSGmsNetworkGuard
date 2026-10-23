package com.gms.networkguard;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressLint("PrivateApi")
final class SystemStateVisibilityHooks {
    private static final Set<Method> SETTINGS_HOOKS = ConcurrentHashMap.newKeySet();
    private final MainHook module;

    SystemStateVisibilityHooks(MainHook module) {
        this.module = module;
    }

    void installAccessibility(ClassLoader loader) {
        int count = 0;
        try {
            Class<?> service = Class.forName(
                    "com.android.server.accessibility.AccessibilityManagerService", false, loader);
            for (Method method : service.getDeclaredMethods()) {
                if (!method.getName().equals("getEnabledAccessibilityServiceList")
                        && !method.getName().equals("getInstalledAccessibilityServiceList")) continue;
                boolean list = List.class.isAssignableFrom(method.getReturnType());
                boolean slice = method.getReturnType().getName()
                        .equals("android.content.pm.ParceledListSlice");
                if (!list && !slice) continue;
                module.hook(method).setId("hide-system-accessibility-list-"
                                + method.toGenericString().hashCode())
                        .intercept(chain -> {
                            boolean hide = (maskForCaller(Binder.getCallingUid())
                                    & StateVisibility.ACCESSIBILITY) != 0;
                            Object result = chain.proceed();
                            if (!hide) return result;
                            return list ? Collections.emptyList()
                                    : method.getReturnType().getConstructor(List.class)
                                            .newInstance(Collections.emptyList());
                        });
                count++;
            }
        } catch (Throwable error) {
            module.err("system state visibility accessibility", error);
        }
        module.info("system state visibility accessibility list hooks=" + count);
    }

    void installSettingsProvider(ClassLoader loader) {
        int count = 0;
        try {
            Class<?> provider = Class.forName(
                    "com.android.providers.settings.SettingsProvider", false, loader);
            for (Method method : provider.getDeclaredMethods()) {
                if (method.getName().equals("call") && method.getParameterCount() == 3
                        && method.getReturnType() == Bundle.class) {
                    if (!SETTINGS_HOOKS.add(method)) continue;
                    module.hook(method).setId("hide-system-settings-call")
                            .intercept(chain -> {
                                String operation = (String) chain.getArg(0);
                                String key = (String) chain.getArg(1);
                                boolean read = "GET_global".equals(operation)
                                        || "GET_secure".equals(operation) || "GET_system".equals(operation)
                                        || "LIST_global".equals(operation) || "LIST_secure".equals(operation)
                                        || "LIST_system".equals(operation);
                                boolean relevant = read && (operation.startsWith("LIST_")
                                        || StateVisibility.hiddenValue(key, StateVisibility.ALL) != null);
                                int mask = relevant ? maskForCaller(Binder.getCallingUid()) : 0;
                                Object result = chain.proceed();
                                return mask != 0 && result instanceof Bundle
                                        ? StateVisibility.hideCallResult(operation, key, (Bundle) result, mask)
                                        : result;
                            });
                    count++;
                } else if (method.getName().equals("query") && method.getParameterCount() == 5
                        && method.getParameterTypes()[0] == Uri.class
                        && Cursor.class.isAssignableFrom(method.getReturnType())) {
                    if (!SETTINGS_HOOKS.add(method)) continue;
                    module.hook(method).setId("hide-system-settings-query")
                            .intercept(chain -> {
                                Uri uri = (Uri) chain.getArg(0);
                                List<String> path = uri.getPathSegments();
                                if (path.isEmpty() || (!path.get(0).equals("global")
                                        && !path.get(0).equals("secure") && !path.get(0).equals("system"))) {
                                    return chain.proceed();
                                }
                                int mask = maskForCaller(Binder.getCallingUid());
                                if (mask == 0) return chain.proceed();
                                Object[] args = chain.getArgs().toArray();
                                String[] projection = (String[]) args[1];
                                boolean addName = projection != null
                                        && !Arrays.asList(projection).contains("name");
                                if (addName) {
                                    String[] expanded = Arrays.copyOf(projection, projection.length + 1);
                                    expanded[projection.length] = "name";
                                    args[1] = expanded;
                                }
                                Cursor result = (Cursor) chain.proceed(args);
                                return result == null ? null : StateVisibility.hideCursor(result, addName, mask);
                            });
                    count++;
                }
            }
        } catch (Throwable error) {
            module.err("system state visibility settings", error);
        }
        if (count > 0) module.info("system state visibility settings hooks=" + count);
    }

    @SuppressLint("QueryPermissionsNeeded")
    private int maskForCaller(int uid) {
        int configured = module.visibilityMask();
        if (configured == 0) return 0;
        if (uid % 100000 < 10000) return 0;
        Context context = MainHook.currentContext();
        if (context == null) return 0;
        long identity = Binder.clearCallingIdentity();
        try {
            PackageManager manager = context.getPackageManager();
            String[] packages = manager.getPackagesForUid(uid);
            if (packages == null || packages.length == 0) return configured;
            for (String name : packages) {
                if (MainHook.GOOGLE.contains(name)) return 0;
                ApplicationInfo info = manager.getApplicationInfo(name, 0);
                if ((info.flags & (ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return 0;
            }
            for (String name : packages) {
                Intent intent = new Intent("android.accessibilityservice.AccessibilityService")
                        .setPackage(name);
                List<ResolveInfo> services = manager.queryIntentServices(intent,
                        PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
                for (ResolveInfo service : services) {
                    if (service.serviceInfo != null && "android.permission.BIND_ACCESSIBILITY_SERVICE"
                            .equals(service.serviceInfo.permission)) return configured & StateVisibility.DEBUG;
                }
            }
            Bundle args = new Bundle();
            args.putInt("_user", uid / 100000);
            Bundle result = context.getContentResolver().call(Uri.parse("content://settings"),
                    "GET_secure", "enabled_accessibility_services", args);
            String enabled = result == null ? null : result.getString("value");
            if (enabled != null) {
                for (String value : enabled.split(":")) {
                    ComponentName service = ComponentName.unflattenFromString(value);
                    if (service != null && Arrays.asList(packages).contains(service.getPackageName())) {
                        return configured & StateVisibility.DEBUG;
                    }
                }
            }
            return configured;
        } catch (Throwable error) {
            return 0;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}
