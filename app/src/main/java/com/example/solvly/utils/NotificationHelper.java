package com.example.solvly.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class NotificationHelper {

    public static void scheduleNextNotification(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        
        // Use FLAG_IMMUTABLE as required by modern Android
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule next notification in roughly 2 hours (with some randomness)
        // 2 hours = 7200000 ms
        // Randomness: +/- 30 minutes (1800000 ms)
        long baseInterval = 2 * 60 * 60 * 1000L;
        long randomness = (long) ((Math.random() - 0.5) * 60 * 60 * 1000L); // +/- 30 mins
        long nextTime = SystemClock.elapsedRealtime() + baseInterval + randomness;

        if (alarmManager != null) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextTime,
                    pendingIntent
            );
        }
    }

    public static void cancelNotifications(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
