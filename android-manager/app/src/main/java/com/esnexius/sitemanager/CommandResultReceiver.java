package com.esnexius.sitemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class CommandResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String operation = safe(intent.getStringExtra("operation"));
        int requestId = intent.getIntExtra("request_id", -1);
        Bundle result = intent.getBundleExtra("result");
        int exitCode = -999, err = 0;
        String stdout = "", stderr = "", errmsg = "";

        if (result != null) {
            exitCode = result.getInt("exitCode", -999);
            err = result.getInt("err", 0);
            stdout = safe(result.getString("stdout"));
            stderr = safe(result.getString("stderr"));
            errmsg = safe(result.getString("errmsg"));
        } else {
            errmsg = "Termux returned no result bundle. Check the Termux version and allow-external-apps setting.";
        }

        boolean ok = exitCode == 0 && err == 0;
        String all = stdout;
        if (!stderr.isBlank()) all += (all.isBlank() ? "" : "\n\n") + "stderr:\n" + stderr;
        if (!errmsg.isBlank()) all += (all.isBlank() ? "" : "\n\n") + "Termux:\n" + errmsg;

        AppStateStore store = new AppStateStore(context);
        if ("status".equals(operation)) {
            if (ok) store.saveStatus(stdout);
        } else if ("keepalive".equals(operation) || "autostart".equals(operation)) {
            // Background health operations must not overwrite the user's latest visible command result.
        } else {
            store.markResult(operation, ok, exitCode, all);
        }

        if (!"status".equals(operation) && !"keepalive".equals(operation) && !"autostart".equals(operation)) {
            NotificationHelper.notifyResult(context, operation, ok);
        }

        Intent event = new Intent(MainActivity.ACTION_COMMAND_RESULT);
        event.setPackage(context.getPackageName());
        event.putExtra("operation", operation);
        event.putExtra("request_id", requestId);
        event.putExtra("exit_code", exitCode);
        event.putExtra("err", err);
        event.putExtra("stdout", stdout);
        event.putExtra("stderr", stderr);
        event.putExtra("errmsg", errmsg);
        context.sendBroadcast(event);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
