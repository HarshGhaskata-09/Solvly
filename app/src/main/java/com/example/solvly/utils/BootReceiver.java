package com.example.solvly.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("solvly_prefs", Context.MODE_PRIVATE);
            boolean notificationsEnabled = prefs.getBoolean("pref_notifications", false);
            
            if (notificationsEnabled) {
                NotificationHelper.scheduleNextNotification(context);
            }
        }
    }
}
