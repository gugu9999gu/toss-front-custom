package dev.tossfront.deck;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public final class DeckActivity extends Activity {
    private WebView web;
    private SharedPreferences preferences;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed = false;
    private final BluetoothTransport bluetooth = new BluetoothTransport();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("connection", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
    @Override public void onBackPressed() { web.evaluateJavascript("window.FrontDeck && window.FrontDeck.back()", null); }
    @Override protected void onDestroy() {
        destroyed = true; bluetooth.destroy(); network.shutdownNow();
        web.removeJavascriptInterface("NativeDeck"); web.destroy();
        super.onDestroy();
    }

    private void refresh() {
        web.evaluateJavascript("window.FrontDeck && window.FrontDeck.refresh()", null);
    }
    private void consumePairingIntent(Intent intent) {
        String pin = intent.getStringExtra("pairing_code");
        String host = intent.getStringExtra("wireless_host");
        String fingerprint = intent.getStringExtra("wireless_fingerprint");
        int port = intent.getIntExtra("wireless_port", 38766);
        String bluetoothAddress = intent.getStringExtra("bluetooth_address"); intent.removeExtra("bluetooth_address");
        intent.removeExtra("pairing_code");
        intent.removeExtra("wireless_host"); intent.removeExtra("wireless_fingerprint"); intent.removeExtra("wireless_port");
        if (pin != null && pin.matches("[0-9]{8}")) {
            if (bluetoothAddress != null) { pairBluetooth(pin, bluetoothAddress); return; }
            try { pair(pin, host == null ? ConnectionTarget.usb() : ConnectionTarget.wifi(host, port, fingerprint)); }
            catch (IllegalArgumentException invalid) { Toast.makeText(this, "무선 연결 정보를 확인하세요", Toast.LENGTH_SHORT).show(); }
        }
    }
    private ConnectionTarget target() {
        if (!preferences.getString("mode", "usb").equals("wifi")) return ConnectionTarget.usb();
        return ConnectionTarget.wifi(preferences.getString("host", ""), preferences.getInt("port", 38766), preferences.getString("fingerprint", ""));
    }
    private void pair(final String pin, final ConnectionTarget target) {
        network.execute(() -> {
            JSONObject result;
            try {
                result = call("pair", new JSONObject().put("pin", pin), target, "");
                if (result.has("token")) {
                    bluetooth.close();
                    preferences.edit().putString("token", result.getString("token"))
                        .putString("mode", target.wireless ? "wifi" : "usb").putString("host", target.host)
                        .putInt("port", target.port).putString("fingerprint", target.fingerprint).commit();
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
        if (preferences.getString("mode", "usb").equals("bluetooth")) {
            return bluetooth.call(preferences.getString("bluetooth_address", ""), route, body, preferences.getString("token", ""));
        }
        return call(route, body, target(), preferences.getString("token", ""));
    }
    private JSONObject call(String route, JSONObject body, ConnectionTarget target, String token) throws Exception {
        boolean read = route.equals("config") || route.equals("windows") || route.equals("apps") || route.equals("editor");
        if (!read && !route.equals("action") && !route.equals("pair") && !route.equals("focus") && !route.equals("save") && !route.equals("delete") && !route.equals("input")) return error("지원하지 않는 요청입니다.");
        if (!route.equals("pair") && token.isEmpty()) return error("설정에서 PC 연결 코드를 입력하세요.");
        HttpURLConnection connection = (HttpURLConnection) new URL(target.baseUrl() + "/api/" + route).openConnection();
        if (target.wireless) target.secure((HttpsURLConnection) connection);
        connection.setConnectTimeout(1800); connection.setReadTimeout(3500);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        if (!route.equals("pair")) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (!read) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > (route.equals("save") || route.equals("input") ? 8192 : 2048)) return error("요청이 너무 큽니다.");
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
                while ((count = in.read(buffer)) != -1) { if (out.size() + count > 4194304) return error("잘못된 PC 응답입니다."); out.write(buffer, 0, count); }
                JSONObject response = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                if (route.equals("config") && status == 200) response.put("transport", target.wireless ? "wifi" : "usb");
                return response;
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
        String mode = preferences.getString("mode", "usb");
        new AlertDialog.Builder(this).setTitle("FrontDeck 설정 · " + (mode.equals("wifi") ? "Wi-Fi" : mode.equals("bluetooth") ? "Bluetooth" : "USB"))
            .setItems(new String[] {"Wi-Fi 연결", "USB 연결 코드 입력", "뮤직플레이어 열기", "Android 설정", "기본 홈 선택", "Bluetooth 연결"}, (dialog, which) -> {
                if (which == 0) {
                    showWireless();
                } else if (which == 1) {
                    EditText input = new EditText(this);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setHint("8자리 연결 코드"); input.setSingleLine(true);
                    new AlertDialog.Builder(this).setTitle("PC 연결").setMessage("PC 연결 프로그램에 표시된 코드를 입력하세요.")
                        .setView(input).setPositiveButton("연결", (d, w) -> {
                            String pin = input.getText().toString().trim();
                            if (pin.matches("[0-9]{8}")) pair(pin, ConnectionTarget.usb());
                            else Toast.makeText(this, "8자리 코드를 입력하세요", Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("취소", null).show();
                } else if (which == 2) { openMusic(); }
                else if (which == 5) { showBluetooth(); }
                else {
                    try { startActivity(new Intent(which == 3 ? Settings.ACTION_SETTINGS : Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception unavailable) { Toast.makeText(this, "설정을 열 수 없습니다", Toast.LENGTH_SHORT).show(); }
                }
            }).setNegativeButton("닫기", null).show();
    }
    private boolean bluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 40) {
            if (bluetoothPermission()) showBluetooth();
            else Toast.makeText(this, "Bluetooth 연결에 주변 기기 권한이 필요합니다", Toast.LENGTH_LONG).show();
        }
    }
    private void pairBluetooth(String pin, String address) {
        if (!bluetoothPermission()) { Toast.makeText(this, "설정 → Bluetooth 연결에서 주변 기기 권한을 허용하세요", Toast.LENGTH_LONG).show(); return; }
        if (address == null || !address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) return;
        network.execute(() -> {
            android.util.Log.i("FrontDeckConnection", "Bluetooth pairing started");
            JSONObject result;
            try {
                result = bluetooth.call(address, "pair", new JSONObject().put("pin", pin), "");
                if (result.has("token")) preferences.edit().putString("token", result.getString("token"))
                    .putString("mode", "bluetooth").putString("bluetooth_address", address).commit();
            } catch (Exception unavailable) {
                android.util.Log.w("FrontDeckConnection", "Bluetooth pairing failed: " + unavailable.getClass().getSimpleName());
                result = error("PC의 Bluetooth 연결 프로그램·페어링·연결 코드를 확인하세요");
            }
            android.util.Log.i("FrontDeckConnection", result.has("token") ? "Bluetooth pairing complete" : "Bluetooth pairing rejected");
            final String message = result.has("token") ? "Bluetooth로 PC에 연결됐습니다" : result.optString("error", "연결 실패");
            runOnUiThread(() -> { if (!destroyed) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); refresh(); } });
        });
    }
    private void showBluetooth() {
        if (!bluetoothPermission()) { requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 40); return; }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { Toast.makeText(this, "Bluetooth를 지원하지 않는 기기입니다", Toast.LENGTH_LONG).show(); return; }
        if (!adapter.isEnabled()) { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return; }
        ArrayList<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        devices.sort((a, b) -> String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
        String[] names = new String[devices.size() + 1];
        for (int n = 0; n < devices.size(); n++) names[n] = devices.get(n).getName() + " · " + devices.get(n).getAddress();
        names[devices.size()] = "새 PC 페어링 · Android Bluetooth 설정";
        new AlertDialog.Builder(this).setTitle("연결할 PC 선택").setItems(names, (dialog, selected) -> {
            if (selected == devices.size()) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return; }
            EditText pin = new EditText(this); pin.setSingleLine(true); pin.setInputType(InputType.TYPE_CLASS_NUMBER); pin.setHint("8자리 연결 코드");
            new AlertDialog.Builder(this).setTitle("Bluetooth로 PC 연결")
                .setMessage("PC 프로그램을 -Bluetooth 옵션으로 실행하고, PC 연결 창의 8자리 코드를 입력하세요.")
                .setView(pin).setPositiveButton("연결", (d, w) -> {
                    String code = pin.getText().toString().trim();
                    if (code.matches("[0-9]{8}")) pairBluetooth(code, devices.get(selected).getAddress());
                    else Toast.makeText(this, "8자리 연결 코드를 입력하세요", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("취소", null).show();
        }).setNegativeButton("닫기", null).show();
    }
    private void showWireless() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density); form.setPadding(pad, 0, pad, 0);
        TextView help = new TextView(this); help.setText("같은 공유기의 Wi-Fi에 연결한 뒤 PC의 무선 연결 창에 표시되는 주소와 코드를 입력하세요."); form.addView(help);
        EditText address = new EditText(this); address.setSingleLine(true); address.setHint("PC 주소 · 예: 192.168.1.10");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        String oldHost = preferences.getString("host", ""); int oldPort = preferences.getInt("port", 38766);
        if (!oldHost.equals("127.0.0.1")) address.setText(oldHost + (oldPort == 38766 ? "" : ":" + oldPort));
        form.addView(address);
        EditText pin = new EditText(this); pin.setSingleLine(true); pin.setHint("8자리 연결 코드"); pin.setInputType(InputType.TYPE_CLASS_NUMBER); form.addView(pin);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Wi-Fi로 PC 연결").setView(form)
            .setPositiveButton("PC 확인", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String raw = address.getText().toString().trim(); String[] parts = raw.split(":", -1);
            int port = 38766;
            try { if (parts.length == 2) port = Integer.parseInt(parts[1]); else if (parts.length != 1) throw new IllegalArgumentException(); }
            catch (Exception invalid) { address.setError("PC 주소를 확인하세요"); return; }
            String host = parts[0], code = pin.getText().toString().trim();
            if (!ConnectionTarget.validAddress(host, port)) { address.setError("PC의 사설 IPv4 주소를 입력하세요"); return; }
            if (!code.matches("[0-9]{8}")) { pin.setError("8자리 코드를 입력하세요"); return; }
            final int selectedPort = port;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            network.execute(() -> {
                try {
                    String fingerprint = ConnectionTarget.probeFingerprint(host, selectedPort);
                    ConnectionTarget candidate = ConnectionTarget.wifi(host, selectedPort, fingerprint);
                    runOnUiThread(() -> {
                        if (destroyed) return; dialog.dismiss();
                        new AlertDialog.Builder(this).setTitle("PC 확인 코드")
                            .setMessage(ConnectionTarget.confirmationCode(fingerprint) + "\n\nPC 무선 연결 창의 기기 확인 코드와 같으면 연결하세요.")
                            .setPositiveButton("코드가 같아요 · 연결", (d, w) -> pair(code, candidate)).setNegativeButton("취소", null).show();
                    });
                } catch (Exception unavailable) {
                    runOnUiThread(() -> { if (!destroyed) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        address.setError("PC 프로그램·같은 네트워크·주소를 확인하세요"); } });
                }
            });
        }));
        dialog.show();
    }
    public final class Bridge {
        @JavascriptInterface public void request(final String id, final String route, final String raw) {
            if (id == null || !id.matches("[0-9]{1,10}") || raw == null || raw.length() > 8192 ||
                (!"config".equals(route) && !"action".equals(route) && !"windows".equals(route) && !"apps".equals(route) &&
                 !"editor".equals(route) && !"focus".equals(route) && !"save".equals(route) && !"delete".equals(route) && !"input".equals(route))) return;
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
