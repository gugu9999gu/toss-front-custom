package dev.tossfront.deck;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DeckActivity extends Activity {
    private WebView web;
    private SharedPreferences preferences;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("connection", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prepareDashboardWindow();
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layout);
        }
        WebView.setWebContentsDebuggingEnabled(false);
        web = new WebView(this);
        web.setBackgroundColor(0xff101217);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
        });
        web.addJavascriptInterface(new Bridge(), "NativeDeck");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        immersive();
        consumePairingIntent(getIntent());
    }

    private void prepareDashboardWindow() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard == null || keyguard.isDeviceSecure()) return;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    private void dismissUnsecuredKeyguard() {
        if (Build.VERSION.SDK_INT < 26) return;
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard != null && !keyguard.isDeviceSecure() && keyguard.isKeyguardLocked()) {
            keyguard.requestDismissKeyguard(this, null);
        }
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) immersive(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); consumePairingIntent(intent); }
    @Override protected void onResume() { super.onResume(); dismissUnsecuredKeyguard(); if (web != null) refresh(); }
    @Override public void onBackPressed() { showSettings(); }
    @Override protected void onDestroy() {
        destroyed = true; network.shutdownNow();
        web.removeJavascriptInterface("NativeDeck"); web.destroy();
        super.onDestroy();
    }

    private void refresh() {
        web.evaluateJavascript("window.FrontDeck && window.FrontDeck.refresh()", null);
    }
    private void consumePairingIntent(Intent intent) {
        String pin = intent.getStringExtra("pairing_code");
        intent.removeExtra("pairing_code");
        if (pin != null && pin.matches("[0-9]{8}")) pair(pin);
    }
    private void pair(final String pin) {
        network.execute(() -> {
            JSONObject result;
            try {
                result = call("pair", new JSONObject().put("pin", pin));
                if (result.has("token")) {
                    preferences.edit().putString("token", result.getString("token")).commit();
                    result = new JSONObject().put("ok", true);
                }
            } catch (Exception failure) { result = error("PC 연결을 확인하세요."); }
            final boolean success = result.optBoolean("ok");
            final String message = success ? "PC에 연결됐습니다" : result.optString("error", "연결 코드를 확인하세요");
            runOnUiThread(() -> { if (!destroyed) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); refresh(); } });
        });
    }
    private static JSONObject error(String message) {
        JSONObject result = new JSONObject();
        try { result.put("error", message); } catch (Exception ignored) {}
        return result;
    }
    private JSONObject call(String route, JSONObject body) throws Exception {
        if (!route.equals("config") && !route.equals("action") && !route.equals("pair")) return error("지원하지 않는 요청입니다.");
        String token = preferences.getString("token", "");
        if (!route.equals("pair") && token.isEmpty()) return error("설정에서 PC 연결 코드를 입력하세요.");
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:38765/api/" + route).openConnection();
        connection.setConnectTimeout(1800); connection.setReadTimeout(1800);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        if (!route.equals("pair")) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (!route.equals("config")) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 2048) return error("요청이 너무 큽니다.");
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) return error("PC 연결을 확인하세요.");
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) { if (out.size() + count > 65536) return error("잘못된 PC 응답입니다."); out.write(buffer, 0, count); }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    private void openMusic() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("dev.tossfront.record");
        if (launch == null) {
            Toast.makeText(this, "레코드 플레이어를 먼저 설치해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(launch); }
        catch (Exception unavailable) { Toast.makeText(this, "레코드 플레이어를 열 수 없습니다", Toast.LENGTH_SHORT).show(); }
    }
    private void showSettings() {
        new AlertDialog.Builder(this).setTitle("FrontDeck 설정")
            .setItems(new String[] {"PC 연결 코드 입력", "뮤직플레이어 열기", "Android 설정", "기본 홈 선택"}, (dialog, which) -> {
                if (which == 0) {
                    EditText input = new EditText(this);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setHint("8자리 연결 코드"); input.setSingleLine(true);
                    new AlertDialog.Builder(this).setTitle("PC 연결").setMessage("PC 연결 프로그램에 표시된 코드를 입력하세요.")
                        .setView(input).setPositiveButton("연결", (d, w) -> {
                            String pin = input.getText().toString().trim();
                            if (pin.matches("[0-9]{8}")) pair(pin);
                            else Toast.makeText(this, "8자리 코드를 입력하세요", Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("취소", null).show();
                } else if (which == 1) { openMusic(); }
                else {
                    try { startActivity(new Intent(which == 2 ? Settings.ACTION_SETTINGS : Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception unavailable) { Toast.makeText(this, "설정을 열 수 없습니다", Toast.LENGTH_SHORT).show(); }
                }
            }).setNegativeButton("닫기", null).show();
    }
    public final class Bridge {
        @JavascriptInterface public void request(final String id, final String route, final String raw) {
            if (id == null || !id.matches("[0-9]{1,10}") || raw == null || raw.length() > 2048 ||
                (!"config".equals(route) && !"action".equals(route))) return;
            network.execute(() -> {
                JSONObject result;
                try { result = call(route, new JSONObject(raw)); }
                catch (Exception unavailable) { result = error("PC 연결을 확인하세요."); }
                final String script = "window.FrontDeck && window.FrontDeck.reply(" + JSONObject.quote(id) + "," + result.toString() + ")";
                runOnUiThread(() -> { if (!destroyed) web.evaluateJavascript(script, null); });
            });
        }
        @JavascriptInterface public void openSettings() { runOnUiThread(() -> { if (!destroyed) showSettings(); }); }
        @JavascriptInterface public void openMusic() { runOnUiThread(() -> { if (!destroyed) DeckActivity.this.openMusic(); }); }
    }
}
