package com.jarvis.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

import ai.picovoice.porcupine.PorcupineManager;

public class WakeWordService extends Service implements RecognitionListener {
    private static final String TAG = "JarvisWake";
    private static final String CHANNEL_ID = "jarvis_wake_channel";
    private static final int NOTIF_ID = 1;
    private static final long RESTART_DELAY_MS = 700;
    private static final long COMMAND_WATCHDOG_MS = 9000;
    private static boolean running = false;

    public static final String ACTION_START = "com.jarvis.mobile.WAKE_START";
    public static final String ACTION_STOP = "com.jarvis.mobile.WAKE_STOP";

    private SpeechRecognizer recognizer;
    private PorcupineManager porcupine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean wakeMode = true;
    private boolean porcupineMode = false;
    private String porcupineAccessKey = "";
    private String porcupineModelFile = "jarvis_android.ppn";

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        loadWakeConfig();
    }

    private void loadWakeConfig() {
        SharedPreferences prefs = getSharedPreferences("jarvis_wake", MODE_PRIVATE);
        porcupineMode = prefs.getBoolean("porcupine_enabled", false);
        porcupineAccessKey = prefs.getString("porcupine_access_key", "");
        porcupineModelFile = prefs.getString("porcupine_model_file", "jarvis_android.ppn");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        startForegroundInternal();
        startWakeListening();
        return START_STICKY;
    }

    private void startForegroundInternal() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("J.A.R.V.I.S.")
                .setContentText("Listening for Hey Jarvis")
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Wake word", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Always-on Hey Jarvis listening");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private void startWakeListening() {
        wakeMode = true;
        handler.removeCallbacksAndMessages(null);
        if (porcupineMode && !porcupineAccessKey.isEmpty()) {
            startPorcupine();
        } else {
            startSpeechWake();
        }
    }

    private void startSpeechWake() {
        wakeMode = true;
        startRecognition();
        emitState("wake");
    }

    private void startPorcupine() {
        wakeMode = true;
        try {
            if (porcupine != null) {
                porcupine.stop();
                porcupine.delete();
                porcupine = null;
            }
            String modelPath = copyAssetToCache(porcupineModelFile);
            if (modelPath == null) {
                porcupineMode = false;
                startRecognition();
                emitState("wake");
                return;
            }
            porcupine = new PorcupineManager.Builder()
                    .setAccessKey(porcupineAccessKey)
                    .setKeywordPaths(new String[]{modelPath})
                    .build(getApplicationContext(), keywordIndex -> {
                        if (wakeMode) {
                            onWakeWordDetected("Hey Jarvis");
                        }
                    });
            porcupine.start();
            emitState("wake");
        } catch (Exception e) {
            Log.w(TAG, "porcupine start failed", e);
            porcupineMode = false;
            try {
                if (porcupine != null) {
                    porcupine.delete();
                    porcupine = null;
                }
            } catch (Exception ignored) {}
            startRecognition();
            emitState("wake");
        }
    }

    private String copyAssetToCache(String name) {
        try {
            File out = new File(getCacheDir(), name);
            if (out.exists()) {
                return out.getAbsolutePath();
            }
            try (InputStream is = getAssets().open(name);
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "wake model asset missing: " + name, e);
            return null;
        }
    }

    private void startRecognition() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
            recognizer.startListening(intent);
        } catch (Exception e) {
            Log.w(TAG, "startRecognition failed", e);
            scheduleRestart(RESTART_DELAY_MS);
        }
    }

    @Override
    public void onResults(Bundle results) {
        String text = firstTranscript(results);
        if (wakeMode) {
            if (text != null && isWakeWord(text)) {
                onWakeWordDetected(text);
                return;
            }
            scheduleRestart(RESTART_DELAY_MS);
        } else {
            String clean = stripWakePrefix(text);
            if (!clean.isEmpty()) {
                JSObject data = new JSObject();
                data.put("transcript", clean);
                WakeWordEvents.emit("commandCaptured", data);
            }
            startWakeListening();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (!wakeMode) {
            return;
        }
        String text = firstTranscript(partialResults);
        if (text != null && isWakeWord(text)) {
            onWakeWordDetected(text);
        }
    }

    private void onWakeWordDetected(String full) {
        JSObject data = new JSObject();
        data.put("timestamp", System.currentTimeMillis());
        WakeWordEvents.emit("wakeWordDetected", data);
        wakeMode = false;
        emitState("command");
        String rest = stripWakePrefix(full);
        if (!rest.isEmpty()) {
            JSObject cmd = new JSObject();
            cmd.put("transcript", rest);
            WakeWordEvents.emit("commandCaptured", cmd);
            startWakeListening();
            return;
        }
        stopPorcupineForCommand();
        startRecognition();
        handler.postDelayed(() -> {
            if (!wakeMode) {
                startWakeListening();
            }
        }, COMMAND_WATCHDOG_MS);
    }

    private void stopPorcupineForCommand() {
        if (porcupine != null) {
            try {
                porcupine.stop();
            } catch (Exception ignored) {}
        }
    }

    private boolean isWakeWord(String text) {
        String lower = text.toLowerCase(Locale.US);
        return lower.contains("jarvis") || lower.contains("贾维斯");
    }

    private String stripWakePrefix(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.US);
        int idx = lower.indexOf("jarvis");
        int skip = "jarvis".length();
        if (idx < 0) {
            idx = lower.indexOf("\u8d3e\u7ef4\u65af");
            skip = "\u8d3e\u7ef4\u65af".length();
        }
        if (idx < 0) {
            return text.trim();
        }
        return text.substring(idx + skip).replaceAll("^[\\s,.:;!?-]+", "");
    }

    private String firstTranscript(Bundle bundle) {
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return "";
    }

    @Override
    public void onReadyForSpeech(Bundle params) {}

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        if (!wakeMode) {
            return;
        }
        scheduleRestart(RESTART_DELAY_MS);
    }

    @Override
    public void onError(int error) {
        Log.w(TAG, "recognition error " + error);
        if (!wakeMode) {
            startWakeListening();
        } else {
            scheduleRestart(RESTART_DELAY_MS);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {}

    private void scheduleRestart(long delay) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startWakeListening, delay);
    }

    private void emitState(String state) {
        JSObject data = new JSObject();
        data.put("state", state);
        data.put("listening", true);
        WakeWordEvents.emit("wakeWordState", data);
    }

    @Override
    public void onDestroy() {
        stopEverything();
        super.onDestroy();
    }

    private void stopEverything() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {}
            try {
                recognizer.destroy();
            } catch (Exception ignored) {}
            recognizer = null;
        }
        if (porcupine != null) {
            try {
                porcupine.stop();
            } catch (Exception ignored) {}
            try {
                porcupine.delete();
            } catch (Exception ignored) {}
            porcupine = null;
        }
        JSObject data = new JSObject();
        data.put("state", "stopped");
        data.put("listening", false);
        WakeWordEvents.emit("wakeWordState", data);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
