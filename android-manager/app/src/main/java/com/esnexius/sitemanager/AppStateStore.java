package com.esnexius.sitemanager;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppStateStore {
    private static final String PREFS = "esnexius_manager_state";
    private static final long STALE_BUSY_MS = 20L * 60L * 1000L;
    private static final int MAX_LOG_CHARS = 24000;

    private final SharedPreferences prefs;

    public AppStateStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isBusy() {
        boolean busy = prefs.getBoolean("busy", false);
        long started = prefs.getLong("busy_started", 0L);
        if (busy && started > 0 && System.currentTimeMillis() - started > STALE_BUSY_MS) {
            clearBusy();
            return false;
        }
        return busy;
    }

    public String busyOperation() {
        return prefs.getString("busy_operation", "");
    }

    public void markRunning(String operation) {
        prefs.edit()
                .putBoolean("busy", true)
                .putString("busy_operation", safe(operation))
                .putLong("busy_started", System.currentTimeMillis())
                .putString("last_state", pretty(operation) + " — Running")
                .apply();
    }

    public void clearBusy() {
        prefs.edit()
                .putBoolean("busy", false)
                .remove("busy_operation")
                .remove("busy_started")
                .apply();
    }

    public void markResult(String operation, boolean success, int exitCode, String output) {
        String state = pretty(operation) + (success ? " — Success" : " — Failed (" + exitCode + ")");
        prefs.edit()
                .putBoolean("busy", false)
                .remove("busy_operation")
                .remove("busy_started")
                .putString("last_state", state)
                .putString("last_log", trim(output))
                .putLong("last_result_time", System.currentTimeMillis())
                .apply();
    }

    public String lastState() {
        return prefs.getString("last_state", "Ready");
    }

    public String lastLog() {
        return prefs.getString("last_log", "No command has run yet.");
    }

    public long lastResultTime() {
        return prefs.getLong("last_result_time", 0L);
    }

    public void saveStatus(String status) {
        prefs.edit()
                .putString("last_status", trim(status))
                .putLong("last_status_time", System.currentTimeMillis())
                .apply();
    }

    public String lastStatus() {
        return prefs.getString("last_status", "");
    }

    public boolean autoStartEnabled() {
        return prefs.getBoolean("auto_start", false);
    }

    public void setAutoStartEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_start", enabled).apply();
    }

    public boolean keepAliveEnabled() {
        return prefs.getBoolean("keep_alive", false);
    }

    public void setKeepAliveEnabled(boolean enabled) {
        prefs.edit().putBoolean("keep_alive", enabled).apply();
    }

    private static String trim(String text) {
        String value = safe(text);
        if (value.length() <= MAX_LOG_CHARS) return value;
        return "…output truncated…\n" + value.substring(value.length() - MAX_LOG_CHARS);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Command";
        String normalized = value.replace('-', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
