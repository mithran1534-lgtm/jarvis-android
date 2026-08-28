package com.jarvis.mobile;

import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "AiEngine")
public class AiEnginePlugin extends Plugin {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @PluginMethod
    public void chat(PluginCall call) {
        String baseUrl = call.getString("baseUrl", "https://api.openai.com/v1");
        String apiKey = call.getString("apiKey", "");
        String model = call.getString("model", "gpt-4o-mini");
        String system = call.getString("system",
                "You are J.A.R.V.I.S., a calm, precise personal AI assistant. "
                        + "Answer in a short, confident tone. The user is called Sir. "
                        + "Be genuinely helpful and concise.");
        JSArray messages = call.getArray("messages");
        double temperature = call.getDouble("temperature", 0.7);

        if (apiKey.isEmpty()) {
            call.reject("API key is required", "NO_API_KEY");
            return;
        }

        executor.execute(() -> {
            try {
                String result = request(baseUrl, apiKey, model, system, messages, temperature);
                JSObject ret = new JSObject();
                ret.put("content", result);
                main.post(() -> call.resolve(ret));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                main.post(() -> call.reject(msg, "AI_REQUEST_FAILED"));
            }
        });
    }

    private String request(String baseUrl, String apiKey, String model, String system,
                           JSArray messages, double temperature) throws Exception {
        String endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", temperature);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system));
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.getJSONObject(i);
            msgs.put(new JSONObject()
                    .put("role", m.optString("role", "user"))
                    .put("content", m.optString("content", "")));
        }
        body.put("messages", msgs);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(is);
        if (code >= 200 && code < 300) {
            JSONObject obj = new JSONObject(text);
            JSONArray choices = obj.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                if (message != null) {
                    return message.optString("content", "").trim();
                }
            }
            throw new Exception("No choices in response");
        }
        throw new Exception("HTTP " + code + ": " + text);
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
