package com.jarvis.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Actions")
public class ActionsPlugin extends Plugin {

    @PluginMethod
    public void openApp(PluginCall call) {
        String pkg = call.getString("packageId", "");
        if (pkg.isEmpty()) {
            call.reject("packageId is required", "BAD_INPUT");
            return;
        }
        try {
            Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                call.reject("App not installed", "APP_NOT_FOUND");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(launch);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? e.toString() : e.getMessage(), "OPEN_FAILED");
        }
    }

    @PluginMethod
    public void vibrate(PluginCall call) {
        long ms = Math.max(50, Math.min(5000, call.getDouble("duration", 300.0).longValue()));
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void setTimer(PluginCall call) {
        long seconds = Math.max(1, Math.min(86400, call.getDouble("seconds", 60.0).longValue()));
        String label = call.getString("label", "Timer done");
        Intent intent = new Intent(getContext(), TimerReceiver.class)
                .setAction("com.jarvis.mobile.TIMER_" + System.currentTimeMillis())
                .putExtra("label", label);
        PendingIntent pending = PendingIntent.getBroadcast(
                getContext(),
                (int) (System.currentTimeMillis() % 100000),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        long triggerAt = System.currentTimeMillis() + seconds * 1000L;
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10_000L, pending);
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        }
        call.resolve();
    }
}
