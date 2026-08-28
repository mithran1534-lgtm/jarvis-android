package com.jarvis.mobile;

import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.JSObject;

final class WakeWordEvents {
    private static WakeWordPlugin plugin;

    private WakeWordEvents() {}

    static void bind(WakeWordPlugin p) {
        plugin = p;
    }

    static void unbind(WakeWordPlugin p) {
        if (plugin == p) {
            plugin = null;
        }
    }

    static void emit(final String event, final JSObject data) {
        final WakeWordPlugin target = plugin;
        if (target == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> target.notifyListeners(event, data));
    }
}
