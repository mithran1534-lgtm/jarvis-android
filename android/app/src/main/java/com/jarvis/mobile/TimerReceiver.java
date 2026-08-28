package com.jarvis.mobile;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class TimerReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "jarvis_timer_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String label = intent.getStringExtra("label");
        if (label == null || label.isEmpty()) {
            label = "Timer done";
        }
        createChannel(context);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Notification notification = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentTitle("J.A.R.V.I.S.")
                .setContentText(label)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify((int) (System.currentTimeMillis() % 100000), notification);
        }
    }

    private void createChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Timers", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Jarvis timer alerts");
        nm.createNotificationChannel(channel);
    }
}
