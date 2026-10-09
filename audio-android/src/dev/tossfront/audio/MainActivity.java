package dev.tossfront.audio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 22, 24), PANEL = Color.rgb(27, 37, 40),
            INK = Color.rgb(240, 247, 246), MUTED = Color.rgb(154, 174, 178), MINT = Color.rgb(142, 240, 204);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout devices;
    private TextView current, subtitle, message, autoButton;
    private boolean busy, resumed;
    private AudioTrack testTrack;
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) { if (resumed) refresh(); }
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { if (resumed) refresh(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(24), dp(24), dp(24)); scroll.addView(body);
        body.addView(label("FRONT AUDIO", 12, MINT));
        TextView title = label("소리가 나오는 곳", 27, INK); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(body, title, 8);
        add(body, label("음악·영상의 출력 장치를 선택하세요.", 14, MUTED), 8);
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18)); hero.setBackground(shape(PANEL, 0));
        hero.addView(label("현재 출력", 12, MINT));
        current = label("확인 중…", 21, INK); current.setTypeface(Typeface.DEFAULT, Typeface.BOLD); add(hero, current, 8);
        subtitle = label("연결된 장치를 불러오고 있습니다.", 13, MUTED); add(hero, subtitle, 8); add(body, hero, 24);
        add(body, label("사용 가능한 장치", 13, MUTED), 24);
        devices = new LinearLayout(this); devices.setOrientation(LinearLayout.VERTICAL); add(body, devices, 12);
        autoButton = button("자동 선택으로 되돌리기", () -> command("auto", -1)); add(body, autoButton, 12);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView test = button("테스트 소리", this::testSound), bluetooth = button("Bluetooth 설정", () -> {
            try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
            catch (Exception e) { setMessage("Bluetooth 설정을 열 수 없습니다.", true); }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(56), 1); half.setMarginEnd(dp(8)); actions.addView(test, half);
        actions.addView(bluetooth, new LinearLayout.LayoutParams(0, dp(56), 1)); add(body, actions, 22);
        add(body, button("장치 새로고침", this::refresh), 10);
        message = label("", 13, MUTED); message.setLineSpacing(dp(4), 1); add(body, message, 18);
        add(body, label("PC 케이블을 빼도 동작합니다. 기기를 재부팅한 경우에는 PC에서 오디오 제어를 다시 시작해 주세요.", 12, MUTED), 18);
        setContentView(scroll);
        ((AudioManager)getSystemService(AUDIO_SERVICE)).registerAudioDeviceCallback(deviceCallback, main);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable shape(int color, int border) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(16));
        if (border != 0) bg.setStroke(dp(1), border); return bg;
    }
    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(margin); parent.addView(child, params);
    }
    private TextView button(String text, Runnable action) {
        TextView view = label(text, 14, INK); view.setGravity(Gravity.CENTER); view.setMinHeight(dp(56));
        view.setPadding(dp(14), dp(12), dp(14), dp(12)); view.setBackground(shape(PANEL, 0));
        view.setClickable(true); view.setFocusable(true); view.setOnClickListener(v -> action.run());
        view.setOnTouchListener((v, event) -> {
            float scale = event.getAction() == MotionEvent.ACTION_DOWN ? .97f : 1f;
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(scale).scaleY(scale).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 100 : 0).start();
            }
            return false;
        });
        return view;
    }

    private String deviceName(JSONObject device) {
        int type = device.optInt("type");
        if (type == 2) return "내장 스피커";
        if (type == 8 || type == 26 || type == 27 || type == 30) return device.optString("name", "Bluetooth 오디오");
        if (type == 11 || type == 12 || type == 22) return "USB 오디오 · " + device.optString("name");
        if (type == 3 || type == 4) return "유선 이어폰";
        if (type == 9 || type == 10) return "HDMI 오디오";
        return device.optString("name", "오디오 출력");
    }
    private boolean matches(JSONObject device, JSONArray active) {
        for (int i = 0; i < active.length(); i++) {
            JSONObject route = active.optJSONObject(i);
            if (device.optInt("type") == route.optInt("type") && device.optString("address").equals(route.optString("address"))) return true;
        }
        return false;
    }
    private void render(JSONObject state) {
        devices.removeAllViews(); JSONArray available = state.optJSONArray("devices"), active = state.optJSONArray("active");
        String name = "출력 장치 없음";
        for (int i = 0; i < available.length(); i++) {
            JSONObject device = available.optJSONObject(i); boolean selected = matches(device, active);
            if (selected) name = deviceName(device);
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(16), dp(18), dp(16)); row.setMinimumHeight(dp(82)); row.setBackground(shape(PANEL, selected ? MINT : 0));
            TextView mark = label(selected ? "●" : "○", 22, selected ? MINT : MUTED); row.addView(mark, new LinearLayout.LayoutParams(dp(36), -2));
            LinearLayout lines = new LinearLayout(this); lines.setOrientation(LinearLayout.VERTICAL);
            TextView routeName = label(deviceName(device), 17, INK); routeName.setTypeface(Typeface.DEFAULT, Typeface.BOLD); lines.addView(routeName);
            add(lines, label(selected ? "현재 사용 중" : (device.optInt("type") == 2 ? "기기의 스피커로 재생" : "연결됨 · 눌러서 변경"), 12, selected ? MINT : MUTED), 4);
            row.addView(lines, new LinearLayout.LayoutParams(0, -2, 1)); row.setClickable(true); row.setFocusable(true);
            row.setContentDescription(deviceName(device) + (selected ? ", 현재 출력" : ", 출력으로 선택"));
            row.setOnClickListener(v -> command("select", device.optInt("id"))); add(devices, row, i == 0 ? 0 : 10);
        }
        if (available.length() == 0) devices.addView(label("연결된 출력 장치가 없습니다.", 15, MUTED));
        current.setText(name); subtitle.setText((state.optBoolean("automatic") ? "시스템 자동 선택" : "직접 선택한 출력") + " · 미디어 볼륨 " + state.optInt("volume") + "/" + state.optInt("volumeMax"));
        autoButton.setEnabled(!busy); autoButton.setAlpha(state.optBoolean("automatic") ? .6f : 1f);
    }

    private JSONObject request(String action, int id) throws Exception {
        File config = new File(getFilesDir(), "bridge.json");
        String raw = new String(java.nio.file.Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8); JSONObject connection = new JSONObject(raw);
        JSONObject request = new JSONObject().put("token", connection.getString("token")).put("action", action);
        if (id >= 0) request.put("id", id);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", connection.getInt("port")), 2000); socket.setSoTimeout(3000);
            socket.getOutputStream().write((request.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream line = new ByteArrayOutputStream(); int value;
            while ((value = socket.getInputStream().read()) != -1 && value != '\n') { if (line.size() > 65536) throw new IOException("Reply too large"); line.write(value); }
            JSONObject reply = new JSONObject(line.toString("UTF-8"));
            if (!reply.optBoolean("ok")) throw new IOException(reply.optString("error")); return reply;
        }
    }
    private void refresh() { command("status", -1); }
    private void command(String action, int id) {
        if (busy) return; busy = true;
        if (!action.equals("status")) setMessage("출력을 변경하고 있습니다…", false);
        worker.execute(() -> {
            try {
                JSONObject reply = request(action, id);
                if (!action.equals("status")) { Thread.sleep(350); reply = request("status", -1); }
                final JSONObject result = reply;
                main.post(() -> { if (isDestroyed()) return; busy = false; render(result); setMessage(action.equals("status") ? "장치를 누르면 음악·영상 출력이 변경됩니다." : "출력 설정을 적용했습니다.", false); });
            } catch (Exception e) {
                main.post(() -> { if (isDestroyed()) return; busy = false;
                    if (action.equals("status")) { current.setText("오디오 제어 연결 필요"); subtitle.setText("PC에서 FrontAudio 제어를 시작해 주세요."); devices.removeAllViews(); }
                    setMessage(action.equals("status") ? "오디오 제어에 연결하지 못했습니다. USB를 PC에 연결한 뒤 제어 시작 명령을 실행해 주세요." : "출력을 변경하지 못했습니다. 장치 연결을 확인하고 새로고침해 주세요.", true);
                });
            }
        });
    }
    private void setMessage(String value, boolean error) { message.setText(value); message.setTextColor(error ? Color.rgb(255, 183, 164) : MUTED); }

    private void testSound() {
        if (testTrack != null) return;
        setMessage("짧은 테스트 소리를 재생합니다. 볼륨은 변경하지 않습니다.", false);
        worker.execute(() -> {
            AudioTrack track = null;
            try {
                int rate = 48000, count = rate / 2; short[] samples = new short[count];
                for (int i = 0; i < count; i++) { double envelope = Math.min(1, i / 1200.0) * Math.min(1, (count - i) / 2400.0);
                    samples[i] = (short)(Math.sin(2 * Math.PI * 523.25 * i / rate) * 4000 * envelope); }
                track = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(count * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                testTrack = track; track.write(samples, 0, count); track.play(); Thread.sleep(650);
                AudioDeviceInfo routed = track.getRoutedDevice(); final String route = routed == null ? "현재 출력" : routed.getType() == 2 ? "내장 스피커" : routed.getProductName().toString();
                android.util.Log.i("FrontAudio", "Test routed type=" + (routed == null ? -1 : routed.getType()));
                main.post(() -> { if (!isDestroyed()) setMessage("테스트 소리 재생: " + route, false); });
            } catch (Exception e) { main.post(() -> { if (!isDestroyed()) setMessage("테스트 소리를 재생하지 못했습니다.", true); }); }
            finally { if (track != null) track.release(); testTrack = null; }
        });
    }
    @Override protected void onResume() { super.onResume(); resumed = true; refresh(); }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override protected void onDestroy() {
        ((AudioManager)getSystemService(AUDIO_SERVICE)).unregisterAudioDeviceCallback(deviceCallback); worker.shutdown(); super.onDestroy();
    }
}
