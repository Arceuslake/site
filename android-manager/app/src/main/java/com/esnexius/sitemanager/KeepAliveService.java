package com.esnexius.sitemanager;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class KeepAliveService extends Service {
    private static final long CHECK_INTERVAL_MS = 60_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppStateStore store;

    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (!store.keepAliveEnabled()) {
                stopSelf();
                return;
            }
            if (!store.isBusy() && TermuxBridge.isTermuxInstalled(KeepAliveService.this)
                    && TermuxBridge.hasRunCommandPermission(KeepAliveService.this)) {
                try {
                    TermuxBridge.runBash(KeepAliveService.this, "keepalive", Operations.ensureRunning());
                } catch (Exception ignored) {
                }
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        store = new AppStateStore(this);
        NotificationHelper.ensureChannels(this);
        startForeground(NotificationHelper.KEEP_ALIVE_ID,
                NotificationHelper.keepAliveNotification(this));
        handler.post(check);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(check);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
