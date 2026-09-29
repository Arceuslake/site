package com.esnexius.sitemanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class NotificationHelper {
    public static final String CHANNEL_RESULTS = "manager_results";
    public static final String CHANNEL_KEEP_ALIVE = "manager_keep_alive";
    public static final int KEEP_ALIVE_ID = 4101;

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel results = new NotificationChannel(
                CHANNEL_RESULTS, "Manager results", NotificationManager.IMPORTANCE_DEFAULT);
        results.setDescription("Deployment, backup, restore, and server operation results.");
        NotificationChannel keepAlive = new NotificationChannel(
                CHANNEL_KEEP_ALIVE, "Server keep alive", NotificationManager.IMPORTANCE_LOW);
        keepAlive.setDescription("Keeps the local EsnexiusStore server monitored.");
        manager.createNotificationChannel(results);
        manager.createNotificationChannel(keepAlive);
    }

    public static void notifyResult(Context context, String operation, boolean success) {
        ensureChannels(context);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 0, open, immutableFlags());
        String title = pretty(operation) + (success ? " completed" : " failed");
        String text = success
                ? "Esnexius Site Manager finished the operation successfully."
                : "Open Esnexius Site Manager to view the error details.";

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_RESULTS)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(4200 + Math.abs(operation == null ? 0 : operation.hashCode() % 500), notification);
    }

    public static Notification keepAliveNotification(Context context) {
        ensureChannels(context);
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, 1, open, immutableFlags());
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_KEEP_ALIVE)
                : new Notification.Builder(context);
        return builder
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Esnexius server monitor")
                .setContentText("Keep Alive is monitoring the local storefront.")
                .setOngoing(true)
                .setContentIntent(content)
                .build();
    }

    private static int immutableFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Operation";
        String normalized = value.replace('-', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
