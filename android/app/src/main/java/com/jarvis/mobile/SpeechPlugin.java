package com.jarvis.mobile;

import android.speech.tts.TextToSpeech;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

@CapacitorPlugin(name = "Speech")
public class SpeechPlugin extends Plugin implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private boolean ready = false;
    private final Queue<String> pending = new ArrayDeque<>();

    @Override
    public void load() {
        super.load();
        tts = new TextToSpeech(getContext(), this);
    }

    @Override
    public void onInit(int status) {
        ready = status == TextToSpeech.SUCCESS;
        if (!ready) {
            return;
        }
        if (tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(Locale.UK);
        } else {
            tts.setLanguage(Locale.US);
        }
        tts.setSpeechRate(0.98f);
        tts.setPitch(0.9f);
        while (!pending.isEmpty()) {
            tts.speak(pending.poll(), TextToSpeech.QUEUE_FLUSH, null, "jarvis");
        }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        if (text.isEmpty()) {
            call.resolve();
            return;
        }
        if (ready && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
        } else {
            pending.add(text);
        }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.handleOnDestroy();
    }
}
