package com.jarvis.mobile;

import android.Manifest;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
        name = "WakeWord",
        permissions = {
                @Permission(alias = "recordAudio", strings = {Manifest.permission.RECORD_AUDIO})
        }
)
public class WakeWordPlugin extends Plugin {
    @Override
    public void load() {
        super.load();
        WakeWordEvents.bind(this);
    }

    @Override
    protected void handleOnDestroy() {
        WakeWordEvents.unbind(this);
        super.handleOnDestroy();
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (!hasAudioPermission()) {
            call.reject("Microphone permission is required", "NO_MIC_PERMISSION");
            return;
        }
        Context ctx = getContext();
        Intent intent = new Intent(ctx, WakeWordService.class).setAction(WakeWordService.ACTION_START);
        ContextCompat.startForegroundService(ctx, intent);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), WakeWordService.class));
        call.resolve();
    }

    @PluginMethod
    public void configure(PluginCall call) {
        boolean porcupine = call.getBoolean("porcupine", false);
        String accessKey = call.getString("accessKey", "");
        String modelFile = call.getString("modelFile", "jarvis_android.ppn");
        getContext().getSharedPreferences("jarvis_wake", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("porcupine_enabled", porcupine)
                .putString("porcupine_access_key", accessKey)
                .putString("porcupine_model_file", modelFile)
                .apply();
        call.resolve();
    }

    @PluginMethod
    public void isListening(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("listening", WakeWordService.isRunning());
        ret.put("permission", hasAudioPermission());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (hasAudioPermission()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias("recordAudio", call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasAudioPermission());
        call.resolve(ret);
    }

    private boolean hasAudioPermission() {
        return getPermissionState("recordAudio") == PermissionState.GRANTED;
    }
}
