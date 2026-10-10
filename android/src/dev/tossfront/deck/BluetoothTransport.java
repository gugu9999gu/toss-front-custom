package dev.tossfront.deck;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Secure RFCOMM; one bounded exchange, no automatic retry of an ambiguous action. */
final class BluetoothTransport {
    static final UUID SERVICE = UUID.fromString("d6c83020-6e4d-4fc5-a632-0419fef81ca3");
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private volatile BluetoothSocket socket;
    private String address = "";
    private long sequence;

    synchronized JSONObject call(String selected, String route, JSONObject body, String token) throws Exception {
        if (selected == null || !selected.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) throw new IllegalArgumentException();
        // Pairing is an explicit transport switch; never reuse an idle socket from another mode.
        if (route.equals("pair")) close();
        BluetoothSocket current = socket;
        if (current == null || !current.isConnected() || !address.equals(selected)) {
            close();
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) throw new IllegalStateException();
            BluetoothDevice pc = adapter.getRemoteDevice(selected);
            if (pc.getBondState() != BluetoothDevice.BOND_BONDED) throw new IllegalStateException();
            adapter.cancelDiscovery();
            current = pc.createRfcommSocketToServiceRecord(SERVICE);
            socket = current; address = selected;
            final BluetoothSocket connecting = current;
            ScheduledFuture<?> timeout = watchdog.schedule(() -> closeSocket(connecting), 4, TimeUnit.SECONDS);
            try { current.connect(); } catch (Exception failed) {
                android.util.Log.w("FrontDeckConnection", "RFCOMM connect failed: " + failed.getClass().getSimpleName());
                close(); throw failed;
            }
            finally { timeout.cancel(false); }
        }
        final BluetoothSocket connection = current;
        ScheduledFuture<?> timeout = watchdog.schedule(() -> closeSocket(connection), 5, TimeUnit.SECONDS);
        try {
            String id = Long.toString(++sequence);
            byte[] request = new JSONObject().put("version", 1).put("id", id).put("route", route)
                .put("body", body).put("token", token).toString().getBytes(StandardCharsets.UTF_8);
            if (request.length > 16384) throw new IllegalArgumentException();
            DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            output.writeInt(request.length); output.write(request); output.flush();
            DataInputStream input = new DataInputStream(connection.getInputStream());
            int size = input.readInt();
            if (size <= 0 || size > 4194304) throw new IllegalStateException();
            byte[] bytes = new byte[size]; input.readFully(bytes);
            JSONObject response = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!id.equals(response.getString("id"))) throw new IllegalStateException();
            JSONObject result = response.getJSONObject("result");
            if (route.equals("config") && response.getInt("status") == 200) result.put("transport", "bluetooth");
            return result;
        } catch (Exception failure) {
            android.util.Log.w("FrontDeckConnection", "RFCOMM exchange failed: " + failure.getClass().getSimpleName());
            close(); throw failure;
        }
        finally { timeout.cancel(false); }
    }

    private static void closeSocket(BluetoothSocket socket) { try { socket.close(); } catch (Exception ignored) {} }
    // Unsynchronized so the Activity can cancel an in-flight blocking connection.
    void close() { BluetoothSocket old = socket; socket = null; if (old != null) closeSocket(old); }
    void destroy() { close(); watchdog.shutdownNow(); }
}
