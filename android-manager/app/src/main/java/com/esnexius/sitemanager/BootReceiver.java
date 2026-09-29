package com.esnexius.sitemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        AppStateStore store = new AppStateStore(context);

        if (store.keepAliveEnabled()) {
            Intent service = new Intent(context, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            return;
        }

        if (store.autoStartEnabled() && !store.isBusy()
                && TermuxBridge.isTermuxInstalled(context)
                && TermuxBridge.hasRunCommandPermission(context)) {
            try {
                TermuxBridge.runBash(context, "autostart", Operations.ensureRunning());
            } catch (Exception ignored) {
            }
        }
    }
}
