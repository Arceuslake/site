package com.esnexius.sitemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class CommandResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String operation = intent.getStringExtra("operation");
        int requestId = intent.getIntExtra("request_id", -1);
        Bundle result = intent.getBundleExtra("result");

        int exitCode = -999;
        int err = 0;
        String stdout = "";
        String stderr = "";
        String errmsg = "";

        if (result != null) {
            exitCode = result.getInt("exitCode", -999);
            err = result.getInt("err", 0);
            stdout = safe(result.getString("stdout"));
            stderr = safe(result.getString("stderr"));
            errmsg = safe(result.getString("errmsg"));
        } else {
            errmsg = "Termux returned no result bundle. Check Termux version and allow-external-apps.";
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
