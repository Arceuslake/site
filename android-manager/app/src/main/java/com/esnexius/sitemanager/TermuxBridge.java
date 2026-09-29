package com.esnexius.sitemanager;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.concurrent.atomic.AtomicInteger;

public final class TermuxBridge {
    public static final String TERMUX_PACKAGE = "com.termux";
    public static final String TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private TermuxBridge() {}

    public static boolean isTermuxInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean hasRunCommandPermission(Context context) {
        return context.checkSelfPermission(TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestRunCommandPermission(Activity activity, int requestCode) {
        activity.requestPermissions(new String[]{TERMUX_PERMISSION}, requestCode);
    }

    public static void runBash(Context context, String operation, String script) {
        if (!isTermuxInstalled(context)) {
            throw new IllegalStateException("Termux is not installed.");
        }
        if (!hasRunCommandPermission(context)) {
            throw new SecurityException("Run commands in Termux permission is not granted.");
        }

        int requestId = NEXT_ID.incrementAndGet();
        Intent resultIntent = new Intent(context, CommandResultReceiver.class);
        resultIntent.putExtra("operation", operation);
        resultIntent.putExtra("request_id", requestId);

        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestId, resultIntent, flags);

        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE);
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", script});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Esnexius Site Manager: " + operation);
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);
        context.startService(intent);
    }
}
