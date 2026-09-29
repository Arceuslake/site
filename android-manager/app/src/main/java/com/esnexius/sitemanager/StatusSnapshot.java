package com.esnexius.sitemanager;

import java.util.HashMap;
import java.util.Map;

public final class StatusSnapshot {
    private final Map<String, String> values;

    private StatusSnapshot(Map<String, String> values) {
        this.values = values;
    }

    public static StatusSnapshot parse(String output) {
        Map<String, String> values = new HashMap<>();
        if (output != null) {
            for (String line : output.split("\\R")) {
                int at = line.indexOf('=');
                if (at <= 0) continue;
                values.put(line.substring(0, at).trim(), line.substring(at + 1).trim());
            }
        }
        return new StatusSnapshot(values);
    }

    public String get(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public boolean yes(String key) {
        return "yes".equalsIgnoreCase(get(key, ""));
    }

    public String websiteState() {
        if ("online".equalsIgnoreCase(get("HTTP", ""))) return "Online";
        if ("running".equalsIgnoreCase(get("SERVER", ""))) return "Starting";
        if (yes("INSTALLED")) return "Stopped";
        return "Not installed";
    }

    public String nodeVersion() {
        return get("NODE", "Missing");
    }

    public String siteVersion() {
        return get("VERSION", "Unknown");
    }

    public String pid() {
        return get("PID", "—");
    }

    public String uptime() {
        return get("UPTIME", "—");
    }

    public String storage() {
        return "ready".equalsIgnoreCase(get("STORAGE", "")) ? "Ready" : "Needs setup";
    }

    public String backups() {
        return get("BACKUPS", "0");
    }

    public String freeSpace() {
        String mb = get("FREE_MB", "");
        return mb.isBlank() ? "Unknown" : mb + " MB";
    }

    public String dependencyMode() {
        return yes("LOCKFILE") ? "npm ci" : "npm install";
    }

    public boolean online() {
        return "online".equalsIgnoreCase(get("HTTP", ""));
    }

    public boolean running() {
        return "running".equalsIgnoreCase(get("SERVER", ""));
    }

    public boolean installed() {
        return yes("INSTALLED");
    }
}
