package com.esnexius.sitemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    public static final String ACTION_COMMAND_RESULT = "com.esnexius.sitemanager.COMMAND_RESULT";
    private static final int REQ_TERMUX = 1001, REQ_ZIP = 1002, REQ_STORAGE = 1003;
    private static final long STATUS_REFRESH_MS = 10_000L;
    private static final long MAX_ZIP_BYTES = 160L * 1024L * 1024L;

    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<String, TextView> metrics = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView termux, website, state, log;
    private Switch autoStart, keepAlive;
    private AppStateStore store;
    private boolean registered;
    private boolean attemptedAutoStart;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handleResult(i); }
    };

    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            if (!store.isBusy() && TermuxBridge.isTermuxInstalled(MainActivity.this)
                    && TermuxBridge.hasRunCommandPermission(MainActivity.this)) {
                runCommand("status", Operations.status(), false);
            }
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new AppStateStore(this);
        NotificationHelper.ensureChannels(this);
        setContentView(buildUi());
        restoreUiState();
        refreshLocalTermux();

        if (TermuxBridge.isTermuxInstalled(this) && !TermuxBridge.hasRunCommandPermission(this))
            TermuxBridge.requestRunCommandPermission(this, REQ_TERMUX);
    }

    @Override protected void onStart() {
        super.onStart();
        if (!registered) {
            IntentFilter f = new IntentFilter(ACTION_COMMAND_RESULT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(resultReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(resultReceiver, f);
            registered = true;
        }
        refreshLocalTermux();
        restoreUiState();
        handler.removeCallbacks(statusTicker);
        handler.post(statusTicker);
        applyKeepAliveService(store.keepAliveEnabled());
    }

    @Override protected void onStop() {
        handler.removeCallbacks(statusTicker);
        if (registered) { unregisterReceiver(resultReceiver); registered = false; }
        super.onStop();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        root.setBackgroundColor(Color.rgb(244, 248, 251));
        scroll.addView(root);

        LinearLayout hero = column();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(bg("#101820", 22));
        root.addView(hero, full());

        hero.addView(label("Esnexius Site Manager", 25, Color.WHITE, true));
        hero.addView(label("Safer deployment, recovery, and server control", 13, Color.rgb(190,220,238), false));

        LinearLayout statusRow = row();
        LinearLayout.LayoutParams srp = full(); srp.topMargin = dp(14);
        hero.addView(statusRow, srp);
        termux = pill("Termux: checking");
        website = pill("Website: unknown");
        LinearLayout.LayoutParams p1 = weight(42); p1.rightMargin = dp(5);
        LinearLayout.LayoutParams p2 = weight(42); p2.leftMargin = dp(5);
        statusRow.addView(termux, p1);
        statusRow.addView(website, p2);

        root.addView(sectionTitle("Dashboard"), spaced(16, 8));
        LinearLayout dashboard = column();
        dashboard.setPadding(dp(14), dp(12), dp(14), dp(12));
        dashboard.setBackground(bg("#FFFFFF", 16));
        addMetricPair(dashboard, "Server", "server", "Version", "version");
        addMetricPair(dashboard, "Node", "node", "PID", "pid");
        addMetricPair(dashboard, "Uptime", "uptime", "Storage", "storage");
        addMetricPair(dashboard, "Backups", "backups", "Free space", "free");
        addMetricPair(dashboard, "Dependencies", "deps", "HTTP", "http");
        root.addView(dashboard, full());

        root.addView(sectionTitle("Automation"), spaced(16, 8));
        LinearLayout automation = column();
        automation.setPadding(dp(14), dp(10), dp(14), dp(10));
        automation.setBackground(bg("#FFFFFF", 16));

        autoStart = new Switch(this);
        autoStart.setText("Auto start server after device/app startup");
        autoStart.setTextSize(14);
        autoStart.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) store.setAutoStartEnabled(checked);
        });
        automation.addView(autoStart, full());

        keepAlive = new Switch(this);
        keepAlive.setText("Keep Server Alive (foreground monitor)");
        keepAlive.setTextSize(14);
        keepAlive.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            store.setKeepAliveEnabled(checked);
            applyKeepAliveService(checked);
        });
        automation.addView(keepAlive, full());
        automation.addView(label("Keep Alive checks the local site about once a minute and restarts it when needed. Android will show a persistent notification while it is enabled.", 11, Color.DKGRAY, false), spaced(6, 0));
        root.addView(automation, full());

        root.addView(sectionTitle("Deployment"), spaced(16, 8));
        root.addView(label("Updates are staged first. The live site is backed up, switched only after validation, and automatically rolled back if its health check fails.", 12, Color.DKGRAY, false), spaced(0, 10));
        addPair(root, action("install", "INSTALL", Operations.install()), action("upload", "UPLOAD ZIP", null));
        addPair(root, action("update", "SAFE UPDATE", Operations.update()), action("backup", "BACKUP NOW", Operations.backup()));
        addPair(root, action("backups", "BACKUP MANAGER", Operations.listBackups()), action("restart", "RESTART SERVER", Operations.restart()));

        root.addView(sectionTitle("Server"), spaced(16, 8));
        addPair(root, action("start", "START", Operations.start()), action("stop", "STOP", Operations.stop()));
        addPair(root, action("status", "REFRESH STATUS", Operations.status()), action("logs", "VIEW LOG", Operations.logs()));
        addPair(root, utility("OPEN WEBSITE", v -> openWebsite()), utility("TERMUX SETUP", v -> setupTermux()));

        state = label("Ready", 14, Color.rgb(25,75,105), true);
        state.setPadding(dp(14), dp(12), dp(14), dp(12));
        state.setBackground(bg("#E4F2FC", 14));
        root.addView(state, spaced(12, 10));

        log = label("", 12, Color.rgb(220,235,244), false);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        log.setPadding(dp(14), dp(14), dp(14), dp(14));
        log.setBackground(bg("#17232C", 15));
        root.addView(log, new LinearLayout.LayoutParams(-1, dp(260)));

        root.addView(label("Install: ~/esnexiusstore-phone-shop\nUpload: Downloads/EsnexiusManager/upload.zip\nBackup safety limit: 5,000 files / 512 MB expanded", 11, Color.GRAY, false), spaced(10, 0));
        return scroll;
    }

    private void addMetricPair(LinearLayout parent, String leftName, String leftKey, String rightName, String rightKey) {
        LinearLayout r = row();
        TextView left = metric(leftName, leftKey);
        TextView right = metric(rightName, rightKey);
        LinearLayout.LayoutParams a = weight(52); a.rightMargin = dp(6);
        LinearLayout.LayoutParams b = weight(52); b.leftMargin = dp(6);
        r.addView(left, a); r.addView(right, b);
        LinearLayout.LayoutParams rp = full(); rp.bottomMargin = dp(8);
        parent.addView(r, rp);
    }

    private TextView metric(String title, String key) {
        TextView t = label(title + "\n—", 12, Color.rgb(38, 55, 66), false);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setBackground(bg("#F2F6F8", 12));
        metrics.put(key, t);
        t.setTag(title);
        return t;
    }

    private TextView sectionTitle(String text) {
        return label(text, 18, Color.rgb(16,24,32), true);
    }

    private Button action(String key, String text, String script) {
        Button b = base(text);
        buttons.put(key, b);
        if ("upload".equals(key)) b.setOnClickListener(v -> beginUpload());
        else b.setOnClickListener(v -> runCommand(key, script, isExclusive(key)));
        return b;
    }

    private boolean isExclusive(String op) {
        return !("status".equals(op) || "logs".equals(op) || "backups".equals(op));
    }

    private Button utility(String text, View.OnClickListener l) {
        Button b = base(text); b.setOnClickListener(l); return b;
    }

    private Button base(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        b.setBackground(bg("#246B9A", 14));
        return b;
    }

    private void addPair(LinearLayout root, Button a, Button b) {
        LinearLayout r = row();
        LinearLayout.LayoutParams rp = full(); rp.bottomMargin = dp(9); root.addView(r, rp);
        LinearLayout.LayoutParams p1 = weight(54); p1.rightMargin = dp(5);
        LinearLayout.LayoutParams p2 = weight(54); p2.leftMargin = dp(5);
        r.addView(a, p1); r.addView(b, p2);
    }

    private void runCommand(String op, String script, boolean exclusive) {
        if (script == null) return;
        if (exclusive && store.isBusy()) {
            Toast.makeText(this, "Another operation is still running: " + store.busyOperation(), Toast.LENGTH_LONG).show();
            return;
        }
        if (!TermuxBridge.isTermuxInstalled(this)) {
            log.setText("Termux is not installed.");
            return;
        }
        if (!TermuxBridge.hasRunCommandPermission(this)) {
            TermuxBridge.requestRunCommandPermission(this, REQ_TERMUX);
            return;
        }

        if (exclusive) {
            store.markRunning(op);
            setBusyUi(true);
            state.setText(pretty(op) + " — Running");
        }
        if (!"status".equals(op)) log.setText("Sending " + pretty(op) + " command to Termux…");

        try {
            TermuxBridge.runBash(this, op, script);
        } catch (SecurityException e) {
            if (exclusive) store.clearBusy();
            setBusyUi(store.isBusy());
            state.setText(pretty(op) + " — Permission required");
            log.setText("Grant Run commands in Termux environment, then retry.");
            TermuxBridge.requestRunCommandPermission(this, REQ_TERMUX);
        } catch (Exception e) {
            if (exclusive) store.clearBusy();
            setBusyUi(store.isBusy());
            state.setText(pretty(op) + " — Failed");
            log.setText(String.valueOf(e.getMessage()));
        }
    }

    private void handleResult(Intent i) {
        String op = safe(i.getStringExtra("operation"));
        int exit = i.getIntExtra("exit_code", -999), err = i.getIntExtra("err", 0);
        String out = safe(i.getStringExtra("stdout"));
        String stderr = safe(i.getStringExtra("stderr"));
        String msg = safe(i.getStringExtra("errmsg"));
        boolean ok = exit == 0 && err == 0;

        if ("status".equals(op)) {
            if (ok) applyStatus(out);
            maybeAutoStart(out);
            return;
        }
        if ("keepalive".equals(op) || "autostart".equals(op)) {
            if (ok) handler.postDelayed(() -> runCommand("status", Operations.status(), false), 700);
            return;
        }

        setBusyUi(store.isBusy());
        state.setText(pretty(op) + (ok ? " — Success" : " — Failed (" + exit + ")"));
        String all = out;
        if (!stderr.isBlank()) all += (all.isBlank() ? "" : "\n\n") + "stderr:\n" + stderr;
        if (!msg.isBlank()) all += (all.isBlank() ? "" : "\n\n") + "Termux:\n" + msg;
        log.setText(all.isBlank() ? "No command output." : all.trim());

        if ("backups".equals(op) && ok) {
            showBackupManager(out);
            return;
        }
        if (ok && !"logs".equals(op)) handler.postDelayed(() -> runCommand("status", Operations.status(), false), 700);
    }

    private void maybeAutoStart(String statusOutput) {
        if (attemptedAutoStart || !store.autoStartEnabled() || store.keepAliveEnabled() || store.isBusy()) return;
        StatusSnapshot s = StatusSnapshot.parse(statusOutput);
        attemptedAutoStart = true;
        if (s.installed() && !s.running()) runCommand("autostart", Operations.ensureRunning(), false);
    }

    private void applyStatus(String out) {
        StatusSnapshot s = StatusSnapshot.parse(out);
        setMetric("server", s.websiteState());
        setMetric("version", s.siteVersion());
        setMetric("node", s.nodeVersion());
        setMetric("pid", s.pid());
        setMetric("uptime", s.uptime());
        setMetric("storage", s.storage());
        setMetric("backups", s.backups());
        setMetric("free", s.freeSpace());
        setMetric("deps", s.dependencyMode());
        setMetric("http", s.online() ? "Online" : "Offline");

        if (s.online()) setPill(website, "Website: online", "#2D6A4F");
        else if (s.running()) setPill(website, "Website: starting", "#A36A18");
        else if (s.installed()) setPill(website, "Website: stopped", "#6B7280");
        else setPill(website, "Website: not installed", "#9B3D3D");
    }

    private void setMetric(String key, String value) {
        TextView t = metrics.get(key);
        if (t != null) t.setText(String.valueOf(t.getTag()) + "\n" + value);
    }

    private void restoreUiState() {
        state.setText(store.lastState());
        log.setText(store.lastLog());
        autoStart.setChecked(store.autoStartEnabled());
        keepAlive.setChecked(store.keepAliveEnabled());
        setBusyUi(store.isBusy());
        if (!store.lastStatus().isBlank()) applyStatus(store.lastStatus());
    }

    private void setBusyUi(boolean busy) {
        for (Map.Entry<String, Button> e : buttons.entrySet()) {
            String op = e.getKey();
            boolean allowedWhileBusy = "logs".equals(op);
            e.getValue().setEnabled(!busy || allowedWhileBusy);
            e.getValue().setAlpha((!busy || allowedWhileBusy) ? 1f : 0.55f);
        }
    }

    private void showBackupManager(String output) {
        List<String> names = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!line.startsWith("BACKUP_ITEM=")) continue;
            String item = line.substring("BACKUP_ITEM=".length());
            String[] parts = item.split("\\|", 2);
            if (parts.length == 0 || parts[0].isBlank()) continue;
            names.add(parts[0]);
            labels.add(parts[0] + (parts.length > 1 ? "   •   " + parts[1] : ""));
        }
        if (names.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Backup Manager")
                    .setMessage("No backups are available yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a backup")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> showBackupActions(names.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showBackupActions(String filename) {
        new AlertDialog.Builder(this)
                .setTitle(filename)
                .setItems(new String[]{"Restore this backup", "Delete this backup"}, (dialog, which) -> {
                    if (which == 0) confirmRestore(filename);
                    else confirmDelete(filename);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRestore(String filename) {
        new AlertDialog.Builder(this)
                .setTitle("Restore backup?")
                .setMessage("The current site will be backed up first. The selected backup will then be validated, activated, and health-checked.")
                .setPositiveButton("RESTORE", (d, w) -> runCommand("restore", Operations.restore(filename), true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(String filename) {
        new AlertDialog.Builder(this)
                .setTitle("Delete backup?")
                .setMessage(filename + "\n\nThis cannot be undone.")
                .setPositiveButton("DELETE", (d, w) -> runCommand("delete-backup", Operations.deleteBackup(filename), true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void beginUpload() {
        if (store.isBusy()) {
            Toast.makeText(this, "Wait for the current operation to finish.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        chooseZip();
    }

    private void chooseZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
        startActivityForResult(i, REQ_ZIP);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_ZIP && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                long size = querySize(data.getData());
                if (size > MAX_ZIP_BYTES) throw new IOException("Selected ZIP is larger than the 160 MB upload limit.");
                saveZip(data.getData());
                log.setText("ZIP copied to Downloads/EsnexiusManager/upload.zip");
                runCommand("upload", Operations.upload(), true);
            } catch (Exception e) {
                state.setText("Upload — Copy failed");
                log.setText(String.valueOf(e.getMessage()));
            }
        }
    }

    private long querySize(Uri source) {
        try (Cursor c = getContentResolver().query(source, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {}
        return -1L;
    }

    private void saveZip(Uri source) throws Exception {
        if (Build.VERSION.SDK_INT <= 28) saveZipLegacy(source);
        else saveZipScoped(source);
    }

    private void saveZipLegacy(Uri source) throws Exception {
        File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloads, "EsnexiusManager");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Downloads/EsnexiusManager");
        File dst = new File(dir, "upload.zip");
        try (InputStream in = getContentResolver().openInputStream(source); OutputStream out = new FileOutputStream(dst, false)) {
            if (in == null) throw new IOException("Cannot open selected ZIP");
            copy(in, out);
        }
    }

    @android.annotation.TargetApi(29)\n    private void saveZipScoped(Uri source) throws Exception {
        ContentResolver r = getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String rel = "Download/EsnexiusManager/", name = "upload.zip";
        String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        try (Cursor c = r.query(collection, new String[]{MediaStore.MediaColumns._ID}, sel, new String[]{name, rel}, null)) {
            while (c != null && c.moveToNext()) r.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null);
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri dst = r.insert(collection, v);
        if (dst == null) throw new IOException("Cannot create upload.zip");
        try (InputStream in = r.openInputStream(source); OutputStream out = r.openOutputStream(dst, "w")) {
            if (in == null || out == null) throw new IOException("Cannot open ZIP");
            copy(in, out);
        } catch (Exception e) {
            r.delete(dst, null, null);
            throw e;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        r.update(dst, done, null, null);
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_ZIP_BYTES) throw new IOException("ZIP exceeds the 160 MB upload limit.");
            out.write(buf, 0, n);
        }
    }

    private void setupTermux() {
        String cmd = Operations.termuxSetupCommand();
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Termux setup", cmd));
        log.setText("Copied setup command:\n\n" + cmd + "\n\nPaste it in Termux and press Enter.");
        Intent i = getPackageManager().getLaunchIntentForPackage(TermuxBridge.TERMUX_PACKAGE);
        if (i != null) startActivity(i);
        else Toast.makeText(this, "Termux is not installed.", Toast.LENGTH_LONG).show();
    }

    private void openWebsite() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:3000"))); }
        catch (Exception e) { Toast.makeText(this, "No browser available.", Toast.LENGTH_LONG).show(); }
    }

    private void applyKeepAliveService(boolean enabled) {
        Intent service = new Intent(this, KeepAliveService.class);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } else stopService(service);
    }

    private void refreshLocalTermux() {
        if (!TermuxBridge.isTermuxInstalled(this)) setPill(termux, "Termux: not installed", "#9B3D3D");
        else if (!TermuxBridge.hasRunCommandPermission(this)) setPill(termux, "Termux: permission needed", "#A36A18");
        else setPill(termux, "Termux: ready", "#2D6A4F");
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == REQ_TERMUX) refreshLocalTermux();
        if (req == REQ_STORAGE && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) chooseZip();
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView label(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); return t; }
    private TextView pill(String s) { TextView t = label(s, 12, Color.WHITE, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(6),0,dp(6),0); t.setBackground(bg("#3A4A56", 12)); return t; }
    private void setPill(TextView t, String s, String color) { t.setText(s); t.setBackground(bg(color, 12)); }
    private GradientDrawable bg(String c, int r) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(c)); g.setCornerRadius(dp(r)); return g; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight(int h) { return new LinearLayout.LayoutParams(0, dp(h), 1f); }
    private LinearLayout.LayoutParams spaced(int top, int bottom) { LinearLayout.LayoutParams p = full(); p.topMargin = dp(top); p.bottomMargin = dp(bottom); return p; }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String pretty(String s) { if (s == null || s.isBlank()) return "Command"; String n = s.replace('-', ' '); return Character.toUpperCase(n.charAt(0)) + n.substring(1); }
}
