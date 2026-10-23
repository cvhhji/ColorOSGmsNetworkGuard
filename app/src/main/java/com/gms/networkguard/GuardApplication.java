package com.gms.networkguard;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class GuardApplication extends Application implements XposedServiceHelper.OnServiceListener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(Feature.values().length + 2));
    private final RemoteSettings settings = new RemoteSettings();
    private volatile XposedService service;
    private WeakReference<MainActivity> activity = new WeakReference<>(null);

    @Override
    public void onCreate() {
        super.onCreate();
        worker.allowCoreThreadTimeOut(true);
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        this.service = service;
        notifyActivity();
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (this.service == service) this.service = null;
        notifyActivity();
    }

    XposedService service() {
        return service;
    }

    RemoteSettings settings() {
        return settings;
    }

    void execute(Runnable operation) {
        worker.execute(operation);
    }

    void attach(MainActivity target) {
        activity = new WeakReference<>(target);
    }

    void detach(MainActivity target) {
        if (activity.get() == target) activity.clear();
    }

    private void notifyActivity() {
        main.post(() -> {
            MainActivity target = activity.get();
            if (target != null) target.onConnectionChanged();
        });
    }
}
