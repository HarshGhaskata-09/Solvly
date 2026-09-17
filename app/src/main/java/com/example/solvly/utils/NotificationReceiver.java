package com.example.solvly.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.solvly.R;
import com.example.solvly.activities.MainActivity;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "solvly_notifications";
    private static final AtomicInteger notificationIdCounter = new AtomicInteger((int) (System.currentTimeMillis() % 100000));

    private final String[] messages = {
        "Stuck on a math problem? Snap a photo and let Solvly help!",
        "Time for a quick study session! Solvly is ready.",
        "Need help with Homework? Solvly's AI has your back.",
        "Master Trigonometry today! Try scanning a new problem.",
        "Don't let complex equations slow you down. Solve them instantly!",
        "Keep up the good work! Consistent practice makes perfect.",
        "Curious about alternative methods? Solvly can show you different ways.",
        "Check out your Problem History to review past concepts.",
        "Is there a math concept you want to learn? Just ask Solvly!",
        "Let's solve some math! Open Solvly and get started."
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("solvly_prefs", Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("pref_notifications", false);

        if (notificationsEnabled) {
            showNotification(context);
            // Schedule the next one to keep the loop going
            NotificationHelper.scheduleNextNotification(context);
        }
    }

    private void showNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Study Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Random study reminders and tips");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                mainIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String randomMessage = messages[new Random().nextInt(messages.length)];

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_status)
                .setContentTitle("Solvly")
                .setContentText(randomMessage)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(notificationIdCounter.incrementAndGet(), builder.build());
        }
    }
}
