package dev.tossfront.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Started once through the device's root ADB. No shell execution or public network listener. */
public final class AudioBridge {
    private final AudioManager audio;
    private final Class<?> strategyClass, attributesClass;
    private final Object mediaStrategy;
    private final AudioAttributes media = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final String token;

    private AudioBridge(Context context, String token) throws Exception {
        this.token = token;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy");
        attributesClass = Class.forName("android.media.AudioDeviceAttributes");
        Object found = null;
        for (Object strategy : (List<?>) strategyClass.getMethod("getAudioProductStrategies").invoke(null)) {
            if ((Boolean) strategyClass.getMethod("supportsAudioAttributes", AudioAttributes.class).invoke(strategy, media)) {
                found = strategy; break;
            }
        }
        if (found == null) throw new IllegalStateException("Media strategy unavailable");
        mediaStrategy = found;
    }

    static boolean supported(int type) {
        return type == 2 || type == 3 || type == 4 || type == 5 || type == 6 || type == 8
                || type == 9 || type == 10 || type == 11 || type == 12 || type == 13
                || type == 22 || type == 26 || type == 27 || type == 30;
    }

    private List<?> current() throws Exception {
        return (List<?>) AudioManager.class.getMethod("getDevicesForAttributes", AudioAttributes.class).invoke(audio, media);
    }

    private JSONObject state() throws Exception {
        JSONArray outputs = new JSONArray(), active = new JSONArray();
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (!supported(device.getType())) continue;
            outputs.put(new JSONObject().put("id", device.getId()).put("type", device.getType())
                    .put("name", device.getProductName().toString()).put("address", device.getAddress()));
        }
        for (Object device : current()) {
            active.put(new JSONObject().put("type", attributesClass.getMethod("getType").invoke(device))
                    .put("address", attributesClass.getMethod("getAddress").invoke(device)));
        }
        List<?> preferred = (List<?>) AudioManager.class.getMethod("getPreferredDevicesForStrategy", strategyClass)
                .invoke(audio, mediaStrategy);
        return new JSONObject().put("ok", true).put("devices", outputs).put("active", active)
                .put("automatic", preferred.isEmpty()).put("strategy", strategyClass.getMethod("getId").invoke(mediaStrategy))
                .put("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                .put("volumeMax", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    private JSONObject handle(JSONObject request) throws Exception {
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                request.optString("token").getBytes(StandardCharsets.UTF_8))) {
            return new JSONObject().put("ok", false).put("error", "Unauthorized");
        }
        String action = request.optString("action");
        if (action.equals("select")) {
            int id = request.getInt("id");
            AudioDeviceInfo chosen = null;
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getId() == id && supported(device.getType())) chosen = device;
            }
            if (chosen == null) throw new IllegalArgumentException("출력 장치의 연결이 해제됐습니다.");
            Object attributes = attributesClass.getConstructor(AudioDeviceInfo.class).newInstance(chosen);
            boolean ok = (Boolean) AudioManager.class.getMethod("setPreferredDeviceForStrategy", strategyClass, attributesClass)
                    .invoke(audio, mediaStrategy, attributes);
            if (!ok) throw new IllegalStateException("시스템에서 출력 변경을 거절했습니다.");
        } else if (action.equals("auto")) {
            boolean ok = (Boolean) AudioManager.class.getMethod("removePreferredDeviceForStrategy", strategyClass)
                    .invoke(audio, mediaStrategy);
            // Removing an already empty preference can return false. Read back the actual state.
            if (!ok && !state().getBoolean("automatic")) throw new IllegalStateException("자동 선택으로 변경하지 못했습니다.");
        } else if (!action.equals("status")) {
            throw new IllegalArgumentException("Unknown action");
        }
        return state();
    }

    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 0 || args.length != 1) throw new SecurityException("Root bootstrap required");
        Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        String configPath = args[0];
        String raw = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(configPath)), StandardCharsets.UTF_8);
        JSONObject config = new JSONObject(raw);
        String token = config.getString("token");
        if (token.length() < 32) throw new SecurityException("Short token");
        AudioBridge bridge = new AudioBridge(context, token);
        try (ServerSocket server = new ServerSocket(config.getInt("port"), 8, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("READY FrontAudio");
            while (true) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2500);
                    ByteArrayOutputStream line = new ByteArrayOutputStream();
                    int value;
                    while ((value = socket.getInputStream().read()) != -1 && value != '\n') {
                        if (line.size() >= 8192) throw new IOException("Request too large");
                        line.write(value);
                    }
                    JSONObject reply;
                    try { reply = bridge.handle(new JSONObject(line.toString("UTF-8"))); }
                    catch (Exception e) {
                        Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                        reply = new JSONObject().put("ok", false).put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    }
                    socket.getOutputStream().write((reply.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) { /* A failed client cannot stop the bridge. */ }
            }
        }
    }
}
